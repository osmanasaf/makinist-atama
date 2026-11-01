package com.makinistatama.tanim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * F5 / MK-178 / AK-5.3: resmi tatil listesi (yönetici elle girer, YALNIZ GÖSTERİM). Okuma personel, yazma yönetici; tekrar eden tarih 409; doğrulama 400; her yazma denetim kaydı;
 * tatil var/yok iken adalet ve kırmızı sayısı çıktısı AYNI (kurallar, puantaj ve toplamlar tatilden etkilenmez).
 */
class ResmiTatilTest extends TestVeritabani {

    static final JsonMapper JSON = new JsonMapper();

    Tarayici yonetici;

    @BeforeEach
    void kur() throws Exception {
        yonetici = yonetici();
    }

    private static String govde(String tarih, String ad) {
        return "{\"tarih\":\"" + tarih + "\",\"ad\":\"" + ad + "\"}";
    }

    /** OK-50 (D-14): tatil adı da kurum adı gibi kontrol ve yön denetim karakteri içeremez; mevcut kayıtlar etkilenmez. */
    @Test
    void ad_kontrolVeYonDenetimKarakteri_400_normalTurkceKabul() throws Exception {
        for (String kotu : new String[] { "Bayram\u0007", "Bayram\u202Egnirts", "Ba\u200Bram" }) {
            yonetici.post("/api/resmi-tatil", govde("2026-10-29", kotu)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resmi_tatil", Integer.class)).isZero();
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı (öğleden sonra)")).andExpect(status().isCreated());
    }

    @Test
    void tohumsuz_personelOkur_yoneticiYazar_atamaciYazamaz_anonim401() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resmi_tatil", Integer.class)).as("migration tohumlamaz (bayram tarihi iddiası yok)").isZero();
        atamaci().get("/api/resmi-tatil?yil=2026").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        atamaci().post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı")).andExpect(status().isForbidden());
        atamaci().delete("/api/resmi-tatil/2026-10-29").andExpect(status().isForbidden());
        new Tarayici(mvc).get("/api/resmi-tatil?yil=2026").andExpect(status().isUnauthorized());
        new Tarayici(mvc).post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı")).andExpect(status().isUnauthorized());
    }

    @Test
    void makinist403_okumaDa() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        makinistGirisli(m, "D001").get("/api/resmi-tatil?yil=2026").andExpect(status().isForbidden());
    }

    @Test
    void ekle_listele_yilSuzgeci_tarihSirali_sil() throws Exception {
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı")).andExpect(status().isCreated()).andExpect(jsonPath("$.tarih").value("2026-10-29"))
                .andExpect(jsonPath("$.ad").value("Cumhuriyet Bayramı"));
        yonetici.post("/api/resmi-tatil", govde("2026-01-01", "Yılbaşı")).andExpect(status().isCreated());
        yonetici.post("/api/resmi-tatil", govde("2027-01-01", "Yılbaşı")).andExpect(status().isCreated());
        atamaci().get("/api/resmi-tatil?yil=2026").andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].tarih").value("2026-01-01")).andExpect(jsonPath("$[1].tarih").value("2026-10-29"));
        atamaci().get("/api/resmi-tatil").andExpect(jsonPath("$", hasSize(3)));
        atamaci().get("/api/resmi-tatil?yil=1999").andExpect(status().isBadRequest());

        yonetici.delete("/api/resmi-tatil/2026-10-29").andExpect(status().isNoContent());
        yonetici.delete("/api/resmi-tatil/2026-10-29").andExpect(status().isNotFound());
        atamaci().get("/api/resmi-tatil?yil=2026").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void tekrarEdenTarih409_dogrulama400() throws Exception {
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı")).andExpect(status().isCreated());
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Başka ad")).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TATIL_VAR"));
        yonetici.post("/api/resmi-tatil", govde("2026-10-30", "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.ad").exists());
        yonetici.post("/api/resmi-tatil", govde("2026-10-30", "   ")).andExpect(status().isBadRequest());
        yonetici.post("/api/resmi-tatil", govde("2026-10-30", "x".repeat(81))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.ad").exists());
        yonetici.post("/api/resmi-tatil", govde("2026-10-30", "x".repeat(80))).andExpect(status().isCreated());
        yonetici.post("/api/resmi-tatil", "{\"ad\":\"Tarihsiz\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.tarih").exists());
        yonetici.post("/api/resmi-tatil", govde("29.10.2026", "Biçim")).andExpect(status().isBadRequest());
        yonetici.post("/api/resmi-tatil", govde("1999-12-31", "Çok eski")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.tarih").exists());
        yonetici.post("/api/resmi-tatil", govde("2101-01-01", "Çok ileri")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resmi_tatil", Integer.class)).isEqualTo(2);
    }

    @Test
    void her_yazma_denetimKaydi_TATIL_EKLE_TATIL_SIL() throws Exception {
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı")).andExpect(status().isCreated());
        yonetici.delete("/api/resmi-tatil/2026-10-29").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'TATIL_EKLE'", String.class)).isEqualTo("2026-10-29 Cumhuriyet Bayramı");
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'TATIL_SIL'", String.class)).isEqualTo("2026-10-29 Cumhuriyet Bayramı");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur IN ('TATIL_EKLE', 'TATIL_SIL')", Integer.class)).isEqualTo(2);
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Çift")).andExpect(status().isCreated());
        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Çift")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TATIL_EKLE'", Integer.class)).as("başarısız yazma kayıt bırakmaz").isEqualTo(2);
    }

    private static JsonNode saf(String json) throws Exception {
        JsonNode d = JSON.readTree(json);
        ((ObjectNode) d).remove("uretimZamani"); // her çağrıda değişen zaman damgası
        return d;
    }

    @Test
    void hesaplariEtkilemez_adaletVeKirmiziTatilVarkenAyni() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        LocalDate gun = LocalDate.of(2026, 10, 29);
        v.atama(v.seferEv("T1", gun, "08:00", "12:00"), m, "ASIL");
        v.atama(v.seferEv("T2", gun, "10:00", "13:00"), m, "ASIL"); // çakışma: kırmızı
        Tarayici t = atamaci();
        String adaletOnce = t.get("/api/rapor/adalet?ay=2026-10").andReturn().getResponse().getContentAsString();
        String kirmiziOnce = t.get("/api/atama/kirmizi-sayisi?bas=" + gun + "&bit=" + gun).andReturn().getResponse().getContentAsString();
        String gunOnce = t.get("/api/atama/gun?tarih=" + gun).andReturn().getResponse().getContentAsString();
        assertThat(kirmiziOnce).as("duyarlılık: kırmızı atama var").contains("\"kirmiziAtama\":2");

        yonetici.post("/api/resmi-tatil", govde("2026-10-29", "Cumhuriyet Bayramı")).andExpect(status().isCreated());

        assertThat(saf(t.get("/api/rapor/adalet?ay=2026-10").andReturn().getResponse().getContentAsString())).isEqualTo(saf(adaletOnce));
        assertThat(t.get("/api/atama/kirmizi-sayisi?bas=" + gun + "&bit=" + gun).andReturn().getResponse().getContentAsString()).isEqualTo(kirmiziOnce);
        assertThat(saf(t.get("/api/atama/gun?tarih=" + gun).andReturn().getResponse().getContentAsString())).isEqualTo(saf(gunOnce));
    }
}
