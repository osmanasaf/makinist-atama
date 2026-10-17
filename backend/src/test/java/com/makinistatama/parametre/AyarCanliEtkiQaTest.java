package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** QA (dinamik ayarlar): ayar değişimi atama denetimine ANINDA yansır, varsayılana dönünce kırmızı kalkar; kullanıcının üç cevabı varsayılanda (48 s hafta, 11 s dinlenme sınırı). */
class AyarCanliEtkiQaTest extends TestVeritabani {

    private static String param(String deger, String neden) {
        return "{\"deger\":\"" + deger + "\",\"neden\":\"" + neden + "\"}";
    }

    @Test
    void aktarmaEsigi_30_mevcutAtamaKirmizi_varsayilanaDonunce_kirmiziKalkar_ikiDenetimKaydiAnahtarli() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("Q001");
        LocalDate gun = LocalDate.of(2027, 1, 12);
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + v.seferEv("A", gun, "08:00", "10:00"), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + v.seferEv("B", gun, "10:20", "11:00"), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()); // 20 dk = esik: uygun
        String kirmizi = "/api/atama/kirmizi-sayisi?bas=2027-01-10&bit=2027-01-14";
        t.get(kirmizi).andExpect(jsonPath("$.kirmiziAtama").value(0));

        Tarayici y = yonetici();
        y.put("/api/parametre/nobet.asgari_aktarma_dk", param("241", "sinir disi")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("0 ile 240")));
        y.put("/api/parametre/nobet.asgari_aktarma_dk", param("240", "capraz")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("altında olmalı")));
        y.put("/api/parametre/nobet.asgari_aktarma_dk", param("30", "deneme")).andExpect(status().isOk());
        t.get(kirmizi).andExpect(jsonPath("$.kirmiziAtama").value(2)); // anında, yeniden başlatma yok

        y.put("/api/parametre/nobet.asgari_aktarma_dk", param("20", "Varsayılana döndürüldü")).andExpect(status().isOk());
        t.get(kirmizi).andExpect(jsonPath("$.kirmiziAtama").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND ayrinti LIKE '%nobet.asgari_aktarma_dk%'", Integer.class))
                .as("reddedilen iki istek kayıt üretmez; kabul edilen iki yazma iki kayıt").isEqualTo(2);
    }

    @Test
    void makinist48saat_ihlalDegil_puantajda3saatFazlaMesai_k14EngelOlunca_kirmizi() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("Q002");
        Tarayici t = atamaci();
        for (int g = 0; g < 6; g++) { // Pzt-Cmt 08:00-16:00 = 48 s
            t.post("/api/atama/sefer/" + v.seferEv("H" + g, LocalDate.of(2027, 1, 4).plusDays(g), "08:00", "16:00"), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        }
        t.get("/api/atama/kirmizi-sayisi?bas=2027-01-04&bit=2027-01-10").andExpect(jsonPath("$.kirmiziAtama").value(0));
        yonetici().get("/api/rapor/puantaj?ay=2027-01&makinist=" + m).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(48.0))
                .andExpect(jsonPath("$.satirlar[0].fazlaMesaiSaat").value(3.0));
        yonetici().put("/api/kural/K-14/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/atama/kirmizi-sayisi?bas=2027-01-04&bit=2027-01-10").andExpect(jsonPath("$.kirmiziAtama").value(1));
    }

    @Test
    void dinlenme11saat_10saat30dkReddedilir_tam11saatKabul() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("Q003");
        LocalDate gun = LocalDate.of(2027, 1, 19);
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + v.seferEv("R1", gun, "07:00", "09:00"), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + v.seferEv("R2", gun, "19:30", "20:30"), AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-11")));
        t.post("/api/atama/sefer/" + v.seferEv("R3", gun, "20:00", "21:00"), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
    }
}
