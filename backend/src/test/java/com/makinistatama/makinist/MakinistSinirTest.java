package com.makinistatama.makinist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;

/** QA (M1): makinist kartı uçlarının negatif / sınır / hata davranışı; arayüzün dayandığı hata sözleşmesi dahil. */
class MakinistSinirTest extends TestVeritabani {

    long ankara;
    long hat;

    @BeforeEach
    void tanimlar() {
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        hat = TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
    }

    String govde(String sicil, String tarihler) {
        return """
                {"sicil":"%s","adSoyad":"Demo Makinist 99","bagliGarId":%d,"durum":"AKTIF",%s}""".formatted(sicil, ankara, tarihler);
    }

    static final String TARIHLER = "\"ehliyetBitis\":\"2030-01-01\",\"saglikBitis\":\"2027-06-01\",\"psikoteknikBitis\":\"2028-01-01\"";

    int makinistSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class);
    }

    @Test
    void eksikBelge_ucTarihBirlikteHataVerir_arayuzHerAlanIcinMesajGosterebilsin() throws Exception {
        atamaci().post("/api/makinist", govde("D090", "\"sonSurus\":null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("DOGRULAMA"))
                .andExpect(jsonPath("$.alanlar.ehliyetBitis").exists())
                .andExpect(jsonPath("$.alanlar.saglikBitis").exists())
                .andExpect(jsonPath("$.alanlar.psikoteknikBitis").exists());
        assertThat(makinistSayisi()).isZero();
    }

    @Test
    void olmayanTakvimGunu_500DegilAlanHatasiyla400() throws Exception {
        atamaci().post("/api/makinist", govde("D091", TARIHLER.replace("2030-01-01", "2026-02-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.ehliyetBitis").exists());
        atamaci().post("/api/makinist", govde("D091", TARIHLER.replace("2027-06-01", "01.06.2027")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.saglikBitis").exists());
        assertThat(makinistSayisi()).isZero();
    }

    @Test
    void sicilUzunlukSiniri_20Kabul_21Red_kenarBosluklarKirpilirVeBuyutulur() throws Exception {
        Tarayici t = atamaci();

        t.post("/api/makinist", govde("A".repeat(21), TARIHLER)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.sicil").exists());
        t.post("/api/makinist", govde("A".repeat(20), TARIHLER)).andExpect(status().isCreated());
        t.post("/api/makinist", govde("  d092  ", TARIHLER)).andExpect(status().isCreated()).andExpect(jsonPath("$.sicil").value("D092"));
        t.post("/api/makinist", govde("D092", TARIHLER)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SICIL_TEKRARI"));
        t.post("/api/makinist", govde("   ", TARIHLER)).andExpect(status().isBadRequest());
        assertThat(makinistSayisi()).isEqualTo(2);
    }

    @Test
    void bagliGarOlarakHatKimligi_400_kayitOlusmaz() throws Exception {
        atamaci().post("/api/makinist", govde("D093", TARIHLER).replace("\"bagliGarId\":" + ankara, "\"bagliGarId\":" + hat))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.bagliGarId").exists());
        assertThat(makinistSayisi()).isZero();
    }

    @Test
    void oturumsuz_okumaVeYazma401_kayitOlusmaz() throws Exception {
        Tarayici anonim = new Tarayici(mvc);

        anonim.get("/api/makinist").andExpect(status().isUnauthorized());
        anonim.post("/api/makinist", govde("D094", TARIHLER)).andExpect(status().isUnauthorized());
        anonim.post("/api/makinist/1/izin", "{\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-24\",\"tur\":\"YILLIK\"}").andExpect(status().isUnauthorized());
        assertThat(makinistSayisi()).isZero();
    }

    @Test
    void izin_tekGunlukSinirKabul_gecersizTurVeOlmayanMakinist_hata() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("D095", TARIHLER)).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'D095'", Long.class);

        t.post("/api/makinist/" + id + "/izin", "{\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-20\",\"tur\":\"RAPOR\"}").andExpect(status().isCreated());
        t.post("/api/makinist/" + id + "/izin", "{\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-21\",\"tur\":\"YOK\"}").andExpect(status().isBadRequest());
        t.post("/api/makinist/" + id + "/izin", "{\"baslangic\":\"2026-10-20\",\"tur\":\"YILLIK\"}").andExpect(status().isBadRequest());
        t.post("/api/makinist/99999/izin", "{\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-21\",\"tur\":\"YILLIK\"}").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'IZIN_EKLE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void okuma_olmayanKimlik404_sayisalOlmayanKimlikVeGecersizDurum400() throws Exception {
        Tarayici t = atamaci();

        t.get("/api/makinist/99999").andExpect(status().isNotFound()).andExpect(jsonPath("$.kod").value("BULUNAMADI"));
        t.get("/api/makinist/abc").andExpect(status().isBadRequest());
        t.get("/api/makinist?durum=XYZ").andExpect(status().isBadRequest());
    }

    @Test
    void guncelle_versiyonEksik400_olmayanMakinist404_bozukJson400() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("D096", TARIHLER)).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'D096'", Long.class);

        t.put("/api/makinist/" + id, govde("D096", TARIHLER)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.versiyon").exists());
        t.put("/api/makinist/99999", govde("D096", TARIHLER + ",\"versiyon\":0")).andExpect(status().isNotFound());
        t.post("/api/makinist", "{\"sicil\":").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'MAKINIST_GUNCELLE'", Integer.class)).isZero();
    }
}
