package com.makinistatama.atama;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * QA (kural kurucu K1, FR-05): aynı ölçüde birden çok özel kural (genel + gevşek kapsamlı + sıkı kapsamlı) HTTP düzeyinde birlikte uygulanır;
 * en sıkısı belirleyicidir, gevşek kural hiçbir şeyi gevşetmez, kapatılan kural sırayla etkisini kaybeder.
 */
class OzelKuralCokluKuralTest extends TestVeritabani {

    static final LocalDate D12 = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long sefA; // hat A, 6 s 30 dk
    long sefZ; // hat Z, 6 s 30 dk

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        for (long m : new long[] { m1, m2 }) {
            jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-09-01')", m, v.hat2);
        }
        sefA = v.sefer("A1", D12, "08:00", "14:30");
        sefZ = v.sefer("Z1", D12, "08:00", "14:30");
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, sefZ);
        v.atama(sefA, m1, "ASIL");
        v.atama(sefZ, m2, "ASIL");
    }

    private static String kural(String ad, String esik, long hat) {
        return "{\"ad\":\"" + ad + "\",\"olcu\":\"TEK_SEFER_SURESI\",\"pencere\":\"SEFER\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":" + esik
                + (hat == 0 ? "" : ",\"kapsam\":{\"hatlar\":[" + hat + "]}") + ",\"siddet\":\"ENGEL\",\"gerekce\":\"QA\",\"gecerlilikBas\":\"2026-01-01\"";
    }

    private static String kapali(String ad, String esik, long hat, int versiyon) {
        return kural(ad, esik, hat).replace("\"ENGEL\"", "\"KAPALI\"") + ",\"versiyon\":" + versiyon + ",\"neden\":\"kapat\"}";
    }

    @Test
    void enSikiBelirleyici_gevsekKuralGevsetmez_kapatilanKuralSirayla_etkisiniKaybeder() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", kural("Genel 6 s", "6", 0) + "}").andExpect(status().isCreated()); // Ö-001: her iki sefer 6,5 s > 6 s
        y.post("/api/ozel-kural", kural("Hat A 7 s", "7", v.hat) + "}").andExpect(status().isCreated()); // Ö-002: gevşek, 6,5 s uygun
        y.post("/api/ozel-kural", kural("Hat Z 5 s", "5", v.hat2) + "}").andExpect(status().isCreated()); // Ö-003: sıkı, 6,5 s > 5 s
        Tarayici t = atamaci();

        // hat A: yalnız genel kural ihlal (gevşek kapsamlı kural ihlal etmez, genel kuralı da gevşetmez)
        t.get("/api/atama/sefer/" + sefA + "/adaylar").andExpect(jsonPath("$[0].ihlaller[*].kod", hasItem("Ö-001")))
                .andExpect(jsonPath("$[*].ihlaller[?(@.kod=='Ö-002')]", empty())).andExpect(jsonPath("$[*].ihlaller[?(@.kod=='Ö-003')]", empty()));
        // hat Z: genel + sıkı kapsamlı birlikte listelenir
        t.get("/api/atama/sefer/" + sefZ + "/adaylar").andExpect(jsonPath("$[0].ihlaller[*].kod", hasItem("Ö-001")))
                .andExpect(jsonPath("$[0].ihlaller[*].kod", hasItem("Ö-003"))).andExpect(jsonPath("$[*].ihlaller[?(@.kod=='Ö-002')]", empty()));
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D12).andExpect(jsonPath("$.kirmiziAtama").value(2));

        // genel kural kapanınca hat A yeşile döner (gevşek 7 s uygun); hat Z sıkı kuralla kırmızı kalır
        y.put("/api/ozel-kural/1", kapali("Genel 6 s", "6", 0, 0)).andExpect(status().isOk());
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D12).andExpect(jsonPath("$.kirmiziAtama").value(1));
        // sıkı kural da kapanınca hiçbiri kırmızı değil
        y.put("/api/ozel-kural/3", kapali("Hat Z 5 s", "5", v.hat2, 0)).andExpect(status().isOk());
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D12).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }
}
