package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;

class SeferTest extends TestVeritabani {

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

    String govde(String tarih, String tren, String kalkis, String varis, String varisTarihi, long versiyon) {
        return """
                {"trenNo":"%s","tarih":"%s","kalkisGarId":%d,"kalkisSaat":"%s","varisGarId":%d,"varisSaat":"%s",%s"aracTipiId":%d,"hatId":%d,"km":245,"versiyon":%d}"""
                .formatted(tren, tarih, ankara, kalkis, eskisehir, varis, varisTarihi == null ? "" : "\"varisTarihi\":\"" + varisTarihi + "\",", yht, hat, versiyon);
    }

    @Test
    void gece2200_0600_varisErtesiGun_tarihKalkisGunu_ofsetli() throws Exception {
        Tarayici t = atamaci();

        t.post("/api/sefer", govde("2026-10-12", "T1", "22:00", "06:00", null, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tarih").value("2026-10-12"))
                .andExpect(jsonPath("$.planlananKalkis").value("2026-10-12T22:00:00+03:00"))
                .andExpect(jsonPath("$.planlananVaris").value("2026-10-13T06:00:00+03:00"))
                .andExpect(jsonPath("$.kalkisGar").value("Ankara"))
                .andExpect(jsonPath("$.durum").value("PLANLI"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_EKLE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void suredenFazla24Saat_400() throws Exception {
        // 08:00 kalkış, ertesi gün 09:00 varış = 25 saat
        atamaci().post("/api/sefer", govde("2026-10-12", "T1", "08:00", "09:00", "2026-10-13", 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.varisSaat").exists());
        // tam 24 saat serbest
        atamaci().post("/api/sefer", govde("2026-10-12", "T2", "08:00", "08:00", null, 0)).andExpect(status().isCreated());
    }

    @Test
    void gecikme_guncelSaat_planlananSakli_tarihKaymaz() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer", govde("2026-10-12", "T1", "23:30", "06:00", null, 0)).andExpect(status().isCreated());

        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 90, varis_gecikme_dk = 90");

        t.get("/api/sefer?gun=2026-10-12")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].planlananKalkis").value("2026-10-12T23:30:00+03:00"))
                .andExpect(jsonPath("$[0].guncelKalkis").value("2026-10-13T01:00:00+03:00")) // ertesi güne taştı
                .andExpect(jsonPath("$[0].tarih").value("2026-10-12")); // ama liste günü planlanan gün
        t.get("/api/sefer?gun=2026-10-13").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sekizOnBeş_90dkGecikme_guncel0930() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer", govde("2026-10-12", "T1", "08:00", "10:00", null, 0)).andExpect(status().isCreated());
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 90");

        t.get("/api/sefer/1").andExpect(jsonPath("$.guncelKalkis").value("2026-10-12T09:30:00+03:00"))
                .andExpect(jsonPath("$.planlananKalkis").value("2026-10-12T08:00:00+03:00"));
    }

    @Test
    void tarihTrenNoTekrari409_ayniNoFarkliGunSerbest() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer", govde("2026-10-12", "T1", "08:00", "10:00", null, 0)).andExpect(status().isCreated());

        t.post("/api/sefer", govde("2026-10-12", "T1", "12:00", "14:00", null, 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_TEKRARI"))
                .andExpect(jsonPath("$.detail").value("12.10.2026 tarihli T1 numaralı sefer zaten var.")); // BUG-3: ISO değil gg.aa.yyyy
        t.post("/api/sefer", govde("2026-10-13", "T1", "08:00", "10:00", null, 0)).andExpect(status().isCreated());
    }

    @Test
    void gunListesi_saatSirali() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer", govde("2026-10-12", "T3", "14:00", "16:00", null, 0));
        t.post("/api/sefer", govde("2026-10-12", "T1", "06:00", "08:00", null, 0));
        t.post("/api/sefer", govde("2026-10-12", "T2", "09:00", "11:00", null, 0));

        t.get("/api/sefer?gun=2026-10-12").andExpect(jsonPath("$[0].trenNo").value("T1")).andExpect(jsonPath("$[1].trenNo").value("T2"))
                .andExpect(jsonPath("$[2].trenNo").value("T3"));
        t.get("/api/sefer").andExpect(status().isBadRequest());
    }

    @Test
    void guncelle_versiyonVeTekrarKontrolu_kayitSatiri() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer", govde("2026-10-12", "T1", "08:00", "10:00", null, 0));
        t.post("/api/sefer", govde("2026-10-12", "T2", "11:00", "12:00", null, 0));

        t.put("/api/sefer/1", govde("2026-10-12", "T1", "08:15", "10:00", null, 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planlananKalkis").value("2026-10-12T08:15:00+03:00")).andExpect(jsonPath("$.versiyon").value(1));
        t.put("/api/sefer/1", govde("2026-10-12", "T1", "08:30", "10:00", null, 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        t.put("/api/sefer/1", govde("2026-10-12", "T2", "08:15", "10:00", null, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("SEFER_TEKRARI"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_GUNCELLE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void dbCheck_tarihPlanlananKalkistenFarkliOlamaz() {
        long id = jdbc.queryForObject("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id)
                VALUES ('X', DATE '2026-10-12', ?, ?, TIMESTAMPTZ '2026-10-12 08:00+03', TIMESTAMPTZ '2026-10-12 09:00+03', ?, ?) RETURNING id""",
                Long.class, ankara, eskisehir, yht, hat);
        assertThat(id).isPositive();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id)
                VALUES ('Y', DATE '2026-10-13', ?, ?, TIMESTAMPTZ '2026-10-12 23:30+03', TIMESTAMPTZ '2026-10-13 01:00+03', ?, ?)""",
                ankara, eskisehir, yht, hat)).hasMessageContaining("sefer_tarih_ck");
    }

    @Test
    void bilinmeyenTanim400() throws Exception {
        atamaci().post("/api/sefer", govde("2026-10-12", "T1", "08:00", "10:00", null, 0).replace("\"hatId\":" + hat, "\"hatId\":" + yht))
                .andExpect(status().isBadRequest());
    }
}
