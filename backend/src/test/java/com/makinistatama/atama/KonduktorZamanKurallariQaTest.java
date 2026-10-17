package com.makinistatama.atama;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * F3b QA (FR-02): zaman kuralları (K-01, K-11, K-14) kondüktöre makinistle AYNI sayılarla uygulanır; analiz örnekleri
 * (haftada 40 s + 6 s = 46 s > 45; 22:00 biten görevden 8 s sonra 06:00) ve pasif/askıda kondüktör.
 */
class KonduktorZamanKurallariQaTest extends TestVeritabani {

    static final LocalDate PZT = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        t = atamaci();
    }

    private static String govde(long id, String rol) {
        return "{\"makinistId\":" + id + ",\"rol\":\"" + rol + "\"}";
    }

    @Test
    void haftada40SaatVar_6SaatDahaEklenirse_K14_ENGEL_kondukturdeDe_makinisteOlduguGibi() throws Exception {
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-14'"); // OK-24: varsayılan K-14 KAPALI; kuralın kendisi sınanıyor (TestVeritabani tohuma döndürür)
        long c = v.konduktor("K001");
        long m = v.makinist("D001");
        for (int g = 0; g < 4; g++) { // Pzt-Per 08:00-18:00 = 4 x 10 s = 40 s (Ankara-Ankara: konum uyarısı çıkmaz)
            v.atama(v.seferEv("H" + g, PZT.plusDays(g), "08:00", "18:00"), c, "KONDUKTOR");
            v.atama(v.seferEv("M" + g, PZT.plusDays(g), "08:00", "18:00"), m, "ASIL");
        }
        long cuma1 = v.seferEv("C1", PZT.plusDays(4), "08:00", "14:00");
        long cuma2 = v.seferEv("C2", PZT.plusDays(4), "08:00", "14:00");
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU' WHERE id IN (?, ?)", cuma1, cuma2);

        t.post("/api/atama/sefer/" + cuma1, govde(c, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-14")));
        t.post("/api/atama/sefer/" + cuma2, govde(m, "ASIL")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-14")));
    }

    @Test
    void gece22deBitenGorevdenSonra8SaatteBaslayanGorev_K11_ENGEL_kondukturdeDe() throws Exception {
        long c = v.konduktor("K001");
        long m = v.makinist("D001");
        v.atama(v.seferEv("O1", PZT, "16:00", "22:00"), c, "KONDUKTOR");
        v.atama(v.seferEv("O2", PZT, "16:00", "22:00"), m, "ASIL");
        long ertesi = v.seferEv("O3", PZT.plusDays(1), "06:00", "10:00");

        t.post("/api/atama/sefer/" + ertesi, govde(c, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-11")));
        t.post("/api/atama/sefer/" + ertesi, govde(m, "ASIL")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-11")));
    }

    @Test
    void askidaVePasifKonduktor_K01_ENGEL() throws Exception {
        long c1 = v.konduktor("K001");
        long c2 = v.konduktor("K002");
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", c1);
        jdbc.update("UPDATE makinist SET durum = 'PASIF' WHERE id = ?", c2);
        long sefer = v.seferEv("P1", PZT, "08:00", "10:00");

        t.post("/api/atama/sefer/" + sefer, govde(c1, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-01")));
        t.post("/api/atama/sefer/" + sefer, govde(c2, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-01")));
    }
}
