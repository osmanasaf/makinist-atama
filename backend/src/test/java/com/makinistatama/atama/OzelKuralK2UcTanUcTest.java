package com.makinistatama.atama;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * K2 (F6) uçtan uca: kullanıcının üç örneğinin üçü (gece nöbeti üst üste 3 gün, ayda en az 8 gün izin, dizel hatlarda sefer en fazla 8 s: bu üçüncüsü
 * {@code OzelKuralUcTanUcTest}'te), ekran taslağı örneği (kayan 7 gün, 50 s, hat, tren türü Yük) ve "Sivas hattında nöbetler arası dinlenme en az 12 s".
 * Testlerde şimdi = 10 Eki 2026.
 */
class OzelKuralK2UcTanUcTest extends TestVeritabani {

    static final LocalDate D12 = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long m1;
    long m2;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m1 = v.makinist("D001");
        m2 = v.makinist("D002");
        for (long m : new long[] { m1, m2 }) {
            jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-09-01')", m, v.hat2);
        }
    }

    private static String govde(String ad, String olcu, String pencere, String kars, String esik, String kapsam, String ek) {
        return "{\"ad\":\"" + ad + "\",\"olcu\":\"" + olcu + "\",\"pencere\":\"" + pencere + "\",\"karsilastirma\":\"" + kars + "\",\"esik\":" + esik
                + (kapsam == null ? "" : ",\"kapsam\":" + kapsam) + ",\"siddet\":\"ENGEL\",\"gerekce\":\"TİS\",\"gecerlilikBas\":\"2026-01-01\"" + ek + "}";
    }

    private String ozelIhlal(long makinist, String alan) {
        return "$[?(@.makinistId==" + makinist + ")].ihlaller[?(@.kod=='Ö-001')]" + alan;
    }

    @Test
    void ornek1_geceNobeti_ustUsteEnFazla3Gun_aday_ata_otomatik() throws Exception {
        for (int g = 15; g <= 17; g++) { // 15, 16, 17 Ekim gece nöbetleri (22:00-05:00, 7 s: yerleşik K-16 7,5 s'i aşmaz)
            v.atama(v.seferEv("N" + g, LocalDate.of(2026, 10, g), "22:00", "05:00"), m1, "ASIL");
        }
        long sekiz = v.seferEv("N18", LocalDate.of(2026, 10, 18), "22:00", "05:00");
        long yirmi = v.seferEv("N20", LocalDate.of(2026, 10, 20), "22:00", "05:00"); // 19 Ekim boş: seri 1
        yonetici().post("/api/ozel-kural", govde("Gece üst üste 3", "ARDISIK_GECE_GUNU", "SERI", "EN_FAZLA", "3", null, "")).andExpect(status().isCreated());
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + sekiz + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ".metin"), hasItem(containsString("Ardışık gece günü (üst üste 15 Eki 2026 – 18 Eki 2026): 4 gün > 3 gün"))))
                .andExpect(jsonPath("$[?(@.makinistId==" + m2 + ")].uygun", hasItem(true)));
        t.post("/api/atama/sefer/" + sekiz, AtamaVeri.ataGovdesi(m1)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("Ö-001")));
        t.get("/api/atama/sefer/" + yirmi + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ""), empty()));
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-18\",\"bit\":\"2026-10-18\",\"uygula\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.atananlar[?(@.seferId==" + sekiz + ")].makinistId", hasItem((int) m2)));
    }

    @Test
    void ornek2_aydaEnAz8GunIzin_24Gun_422_otomatikKacinir_onizlemeKirmiziSayar() throws Exception {
        for (int g = 1; g <= 23; g++) { // Ekim'de 23 farklı gün, günde 2 s
            v.atama(v.seferEv("I" + g, LocalDate.of(2026, 10, g), "10:00", "12:00"), m1, "ASIL");
        }
        long yirmiDort = v.seferEv("I24", LocalDate.of(2026, 10, 24), "10:00", "12:00");
        long ikinciAyniGun = v.seferEv("I23b", LocalDate.of(2026, 10, 23), "15:00", "16:00");
        Tarayici y = yonetici();
        // önizleme: eşik 9 olsaydı 23. gün atama (izin 8 < 9) kırmızı olurdu
        y.post("/api/ozel-kural/dene", govde("Ayda 9 gün izin", "IZIN_GUNU", "AY", "EN_AZ", "9", null, "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(1));
        y.post("/api/ozel-kural", govde("Ayda 8 gün izin", "IZIN_GUNU", "AY", "EN_AZ", "8", null, "")).andExpect(status().isCreated());
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + yirmiDort, AtamaVeri.ataGovdesi(m1)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[?(@.kod=='Ö-001')].metin", hasItem("İzin günü sayısı (Ekim 2026): 7 gün < 8 gün (Kural: Ayda 8 gün izin)")));
        t.post("/api/atama/sefer/" + ikinciAyniGun, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated()); // aynı güne ikinci görev: izin sayısı değişmez
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-24\",\"bit\":\"2026-10-24\",\"uygula\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.atananlar[?(@.seferId==" + yirmiDort + ")].makinistId", hasItem((int) m2)));
    }

    @Test
    void taslakOrnek_kayan7Gun_50s_hat_trenTuruYuk_tamUctanUca() throws Exception {
        // 6 gün x 8 s = 48 s (hat A, Yük); 12 Ekim'e 2 s 1 dk => 50 s 1 dk > 50 s. Yolcu ve tür belirsiz sefer kapsam dışı.
        for (int g = 6; g <= 11; g++) {
            long s = v.seferEv("Y" + g, LocalDate.of(2026, 10, g), "08:00", "16:00");
            jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", s);
            v.atama(s, m1, "ASIL");
        }
        long yuk = v.seferEv("Y12", D12, "08:00", "10:01");
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", yuk);
        long yolcu = v.seferEv("Y12y", D12, "12:00", "14:01");
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU' WHERE id = ?", yolcu);
        long belirsiz = v.seferEv("Y12b", D12, "15:00", "17:01");
        String kapsam = "{\"hatlar\":[" + v.hat + "],\"trenTurleri\":[\"YUK\"]}";
        yonetici().post("/api/ozel-kural", govde("Kayan 7 gün 50 s", "TOPLAM_GOREV_SURESI", "KAYAN", "EN_FAZLA", "50", kapsam, ",\"kayanGun\":7"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kayanGun").value(7))
                .andExpect(jsonPath("$.ozet").value("Toplam görev süresi (kayan 7 gün) en fazla 50 s · Hat: Ankara–Eskişehir · Tren: Yük"));
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + yuk + "/adaylar")
                .andExpect(jsonPath(ozelIhlal(m1, ".metin"), hasItem(containsString("kayan 7 gün 6 Eki 2026 – 12 Eki 2026): 50 s 1 dk > 50 s"))));
        t.get("/api/atama/sefer/" + yolcu + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ""), empty()));
        t.get("/api/atama/sefer/" + belirsiz + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ""), empty()));
        // kayan pencere gün sayısı olmadan 400
        yonetici().post("/api/ozel-kural", govde("Kayansız", "TOPLAM_GOREV_SURESI", "KAYAN", "EN_FAZLA", "50", null, "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.kayanGun").exists());
    }

    @Test
    void sivasHattindaNobetlerArasiDinlenme_12s_sikilastirma() throws Exception {
        v.atama(v.seferEv("D1", D12, "08:00", "10:00"), m1, "ASIL"); // hat A, 10:00'da biter
        long aday = v.seferEv("D2", D12, "21:00", "22:00"); // 11 s sonra: yerleşik K-11 (11 s) geçer, özel 12 s tutmaz
        long hatZ = v.seferEv("D3", D12, "21:00", "22:00");
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, hatZ);
        Tarayici t = atamaci();
        t.get("/api/atama/sefer/" + aday + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ""), empty()));

        yonetici().post("/api/ozel-kural", govde("Hat A dinlenme 12 s", "NOBETLER_ARASI_DINLENME", "NOBET", "EN_AZ", "12", "{\"hatlar\":[" + v.hat + "]}", ""))
                .andExpect(status().isCreated());
        t.get("/api/atama/sefer/" + aday + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ".metin"), hasItem(containsString("11 s < 12 s"))))
                .andExpect(jsonPath("$[?(@.makinistId==" + m1 + ")].ihlaller[?(@.kod=='K-11')]", empty()));
        // boşluğun bir ucu hat A nöbeti olduğundan hat Z adayı da denetlenir
        t.get("/api/atama/sefer/" + hatZ + "/adaylar").andExpect(jsonPath(ozelIhlal(m1, ""), not(empty())));
        t.get("/api/atama/sefer/" + aday + "/adaylar").andExpect(jsonPath("$[?(@.makinistId==" + m2 + ")].uygun", hasItem(true)));
    }
}
