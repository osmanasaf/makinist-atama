package com.makinistatama.makinist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;

class MakinistTest extends TestVeritabani {

    long ankara;
    long eskisehir;
    long hatAe;
    long yht;

    @BeforeEach
    void tanimlar() {
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        eskisehir = TestTanimlari.gar(jdbc, "Eskişehir");
        hatAe = TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        yht = TestTanimlari.aracTipi(jdbc, "YHT");
    }

    String govde(String sicil, long versiyon, String brove) {
        return """
                {"sicil":"%s","adSoyad":"Demo Makinist 17","bagliGarId":%d,"durum":"AKTIF",
                 "ehliyetBitis":"2030-01-01","saglikBitis":"2027-06-01","psikoteknikBitis":"2028-01-01","sonSurus":"2026-09-01",
                 "versiyon":%d,%s}""".formatted(sicil, ankara, versiyon, brove);
    }

    String brove() {
        return """
                "broveAracTipleri":[{"aracTipiId":%d,"sinavTarihi":"2022-03-10"}],
                "broveHatlar":[{"hatId":%d,"sinavTarihi":"2022-03-10","sonSurus":"2025-02-01"}]""".formatted(yht, hatAe);
    }

    @Test
    void olustur_gecerlilikBitisTuretilir_sinav20220310_20270310() throws Exception {
        Tarayici t = atamaci();

        t.post("/api/makinist", govde("D017", 0, brove()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sicil").value("D017"))
                .andExpect(jsonPath("$.bagliGar").value("Ankara"))
                .andExpect(jsonPath("$.broveHatlar[0].hat").value("Ankara–Eskişehir"))
                .andExpect(jsonPath("$.broveHatlar[0].gecerlilikBitis").value("2027-03-10"))
                .andExpect(jsonPath("$.broveAracTipleri[0].gecerlilikBitis").value("2027-03-10"))
                .andExpect(jsonPath("$.versiyon").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'MAKINIST_EKLE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void sicilTekrari_409_kucukHarfSicilBuyutulur() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("D017", 0, brove())).andExpect(status().isCreated());

        t.post("/api/makinist", govde("d017", 0, brove()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SICIL_TEKRARI"));
    }

    @Test
    void guncelle_broveButunOlarakDegisir_versiyonArtar_eskiVersiyon409() throws Exception {
        Tarayici t = atamaci();
        long id = idOlustur(t);

        t.put("/api/makinist/" + id, govde("D017", 0, "\"broveAracTipleri\":[],\"broveHatlar\":[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.broveHatlar", hasSize(0)))
                .andExpect(jsonPath("$.versiyon").value(1));

        t.put("/api/makinist/" + id, govde("D017", 0, brove()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'MAKINIST_GUNCELLE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void guncelle_ayniBroveYenidenYazilabilir_benzersizlikCakismaz() throws Exception {
        Tarayici t = atamaci();
        long id = idOlustur(t);

        t.put("/api/makinist/" + id, govde("D017", 0, brove())).andExpect(status().isOk()).andExpect(jsonPath("$.broveAracTipleri", hasSize(1)));
    }

    @Test
    void sicilDegistirilemez_ve_bilinmeyenTanim400() throws Exception {
        Tarayici t = atamaci();
        long id = idOlustur(t);

        t.put("/api/makinist/" + id, govde("D018", 0, brove())).andExpect(status().isBadRequest());
        t.post("/api/makinist", govde("D020", 0, "\"broveHatlar\":[{\"hatId\":" + yht + ",\"sinavTarihi\":\"2022-03-10\"}]"))
                .andExpect(status().isBadRequest()); // araç tipi kimliği hat olarak
        t.post("/api/makinist", govde("D021", 0, "\"broveAracTipleri\":[{\"aracTipiId\":" + yht + ",\"sinavTarihi\":\"2022-03-10\"},{\"aracTipiId\":" + yht
                + ",\"sinavTarihi\":\"2023-03-10\"}]")).andExpect(status().isBadRequest());
    }

    @Test
    void dogrulama_zorunluAlanlar400() throws Exception {
        atamaci().post("/api/makinist", "{\"sicil\":\"\",\"adSoyad\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.sicil").exists()).andExpect(jsonPath("$.alanlar.bagliGarId").exists());
    }

    @Test
    void izinEkle_20_24Ekim_kayitSatiriOlusur_kartaGelir() throws Exception {
        Tarayici t = atamaci();
        long id = idOlustur(t);

        t.post("/api/makinist/" + id + "/izin", "{\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-24\",\"tur\":\"YILLIK\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.durum").value("ONAY"));
        t.post("/api/makinist/" + id + "/izin", "{\"baslangic\":\"2026-10-24\",\"bitis\":\"2026-10-20\",\"tur\":\"YILLIK\"}")
                .andExpect(status().isBadRequest());

        t.get("/api/makinist/" + id).andExpect(jsonPath("$.izinler", hasSize(1))).andExpect(jsonPath("$.izinler[0].baslangic").value("2026-10-20"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'IZIN_EKLE' AND makinist_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void liste_aramaTurkceDuyarsiz_durumSuzgeci_gizliAlanYok() throws Exception {
        Tarayici t = atamaci();
        idOlustur(t);
        t.post("/api/makinist", govde("D018", 0, brove()).replace("Demo Makinist 17", "Çağlar Demo").replace("\"AKTIF\"", "\"ASKIDA\""))
                .andExpect(status().isCreated());

        t.get("/api/makinist?arama=caglar").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].sicil").value("D018"));
        t.get("/api/makinist?durum=AKTIF").andExpect(jsonPath("$", hasSize(1)));
        t.get("/api/makinist").andExpect(jsonPath("$", hasSize(2)));
        String govde = t.get("/api/makinist/1").andReturn().getResponse().getContentAsString();
        assertThat(govde).doesNotContainIgnoringCase("tckn").doesNotContainIgnoringCase("telefon").doesNotContainIgnoringCase("tani");
    }

    private long idOlustur(Tarayici t) throws Exception {
        t.post("/api/makinist", govde("D017", 0, brove())).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'D017'", Long.class);
    }
}
