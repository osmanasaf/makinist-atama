package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;

/** QA (M1): sefer listesi ve Excel yükleme uçlarının sınır / negatif / hata davranışı (HTTP düzeyi). */
class SeferSinirTest extends TestVeritabani {

    long ankara;
    long eskisehir;
    long hat;
    long yht;

    @BeforeEach
    void tanimlar() {
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        eskisehir = TestTanimlari.gar(jdbc, "Eskişehir");
        hat = TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        yht = TestTanimlari.aracTipi(jdbc, "YHT");
    }

    String govde(String tarih, String tren, String kalkis, String varis) {
        return """
                {"trenNo":"%s","tarih":"%s","kalkisGarId":%d,"kalkisSaat":"%s","varisGarId":%d,"varisSaat":"%s","aracTipiId":%d,"hatId":%d}"""
                .formatted(tren, tarih, ankara, kalkis, eskisehir, varis, yht, hat);
    }

    ResultActions yukle(Tarayici t, String ad, byte[] dosya, boolean onizle) throws Exception {
        return t.istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle")
                .file(new MockMultipartFile("dosya", ad, "application/octet-stream", dosya)).param("onizle", Boolean.toString(onizle)));
    }

    int seferSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class);
    }

    @Test
    void sureSiniri_kalkisEsitVaris_tam24Saat_kabul_ve_23_59ile00_00_bir_dakika() throws Exception {
        Tarayici t = atamaci();

        t.post("/api/sefer", govde("2026-12-25", "S1", "08:00", "08:00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.planlananVaris").value("2026-12-26T08:00:00+03:00"));
        t.post("/api/sefer", govde("2026-12-25", "S2", "23:59", "00:00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.planlananVaris").value("2026-12-26T00:00:00+03:00"));
        t.post("/api/sefer", govde("2026-12-25", "S3", "08:00", "08:00").replace("\"hatId\"", "\"varisTarihi\":\"2026-12-27\",\"hatId\""))
                .andExpect(status().isBadRequest()); // 48 saat
        assertThat(seferSayisi()).isEqualTo(2);
    }

    @Test
    void gunListesi_geceSeferiKalkisGundeGorunur_ertesiGundeGorunmez_sinirGunuHicSeferYoksaBosListe() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer", govde("2026-10-31", "G1", "23:30", "01:00")).andExpect(status().isCreated());

        t.get("/api/sefer?gun=2026-10-31").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].trenNo").value("G1"));
        t.get("/api/sefer?gun=2026-11-01").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        t.get("/api/sefer?gun=2026-02-31").andExpect(status().isBadRequest());
        t.get("/api/sefer").andExpect(status().isBadRequest());
    }

    @Test
    void sefer_oturumsuz401_olmayanKimlik404() throws Exception {
        new Tarayici(mvc).get("/api/sefer?gun=2026-10-12").andExpect(status().isUnauthorized());
        new Tarayici(mvc).post("/api/sefer", govde("2026-10-12", "X", "08:00", "09:00")).andExpect(status().isUnauthorized());
        atamaci().get("/api/sefer/99999").andExpect(status().isNotFound());
        assertThat(seferSayisi()).isZero();
    }

    @Test
    void yukle_bosDosyaVeEksikParametreVeOturumsuz_hataKodlari_veriYazilmaz() throws Exception {
        Tarayici t = atamaci();

        yukle(t, "bos.xlsx", new byte[0], true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        yukle(t, "sahte.xlsx", "bu xlsx degil".getBytes(), false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        t.istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle")).andExpect(status().isBadRequest());
        yukle(new Tarayici(mvc), "x.xlsx", TestExcel.sefer(List.of()), true).andExpect(status().isUnauthorized());
        assertThat(seferSayisi()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    @Test
    void yukle_5MBUstuDosya_okuyucuReddeder_GECERSIZ_DOSYA() throws Exception {
        Tarayici t = atamaci();

        yukle(t, "dev.xlsx", new byte[5 * 1024 * 1024 + 1], true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        assertThat(seferSayisi()).isZero();
    }

    @Test
    void yukle_yalnizBaslikliDosya_sifirSatir_onayDaSifirEkler_hataYok() throws Exception {
        Tarayici t = atamaci();
        byte[] dosya = TestExcel.sefer(List.of());

        yukle(t, "baslik.xlsx", dosya, true).andExpect(status().isOk()).andExpect(jsonPath("$.toplamSatir").value(0))
                .andExpect(jsonPath("$.eklenen").value(0)).andExpect(jsonPath("$.hataSayisi").value(0));
        yukle(t, "baslik.xlsx", dosya, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(0));
        assertThat(seferSayisi()).isZero();
    }

    @Test
    void yukle_olmayanTakvimGunu_31Subat_satirHatasi_sessizce28SubatYazilmaz() throws Exception {
        Tarayici t = atamaci();
        List<String[]> satirlar = new ArrayList<>();
        satirlar.add(new String[] { "31.02.2026", "T1", "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245" });
        satirlar.add(new String[] { "29.02.2028", "T2", "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245" });

        yukle(t, "tarih.xlsx", TestExcel.sefer(satirlar), false).andExpect(status().isOk())
                .andExpect(jsonPath("$.eklenen").value(1)).andExpect(jsonPath("$.hataSayisi").value(1))
                .andExpect(jsonPath("$.hatalar[0].satirNo").value(2));
        assertThat(jdbc.queryForList("SELECT tarih::text FROM sefer")).extracting(r -> r.get("tarih")).containsExactly("2028-02-29");
    }

    @Test
    void yukle_ayniDosyadaAyniTarihTrenNo_ikinciHataVeIlkiEklenir_kucukBuyukHarfDuyarsizGarEslesmesi() throws Exception {
        Tarayici t = atamaci();
        List<String[]> satirlar = List.of(
                new String[] { "12.10.2026", "T1", "ANKARA", "08:00", "eskişehir", "09:30", "yht", "ankara–eskişehir", "245" },
                new String[] { "12.10.2026", "T1", "Ankara", "10:00", "Eskişehir", "11:30", "YHT", "Ankara–Eskişehir", "245" });

        yukle(t, "tekrar.xlsx", TestExcel.sefer(satirlar), false).andExpect(status().isOk())
                .andExpect(jsonPath("$.eklenen").value(1)).andExpect(jsonPath("$.hataSayisi").value(1));
        assertThat(seferSayisi()).isEqualTo(1);
    }
}
