package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T3.4: özel kural uçtan uca (K1): aday listesi, atama, otomatik atama, canlı kırmızı, UYARI gerekçesi, KAPALI, geçerlilik (FR-02, FR-03, FR-05). */
class OzelKuralUcTanUcTest extends TestVeritabani {

    static final LocalDate D12 = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long m1;
    long m2;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m1 = v.makinist("D001");
        m2 = v.makinist("D002");
        // ikinci hat (kapsam dışı sınaması) için iki makinistin de hat yetkisi olsun
        for (long m : new long[] { m1, m2 }) {
            jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-09-01')", m, v.hat2);
        }
    }

    private static String govde(String ad, String olcu, String pencere, String esik, String kapsam, String siddet, String bas, String ek) {
        return "{\"ad\":\"" + ad + "\",\"olcu\":\"" + olcu + "\",\"pencere\":\"" + pencere + "\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":" + esik
                + (kapsam == null ? "" : ",\"kapsam\":" + kapsam) + ",\"siddet\":\"" + siddet + "\",\"gerekce\":\"TİS ek protokol md. 4\",\"gecerlilikBas\":\"" + bas + "\"" + ek + "}";
    }

    private String dizelKurali(String siddet) {
        return govde("Dizel hatlar 8 s", "TEK_SEFER_SURESI", "SEFER", "8", "{\"hatlar\":[" + v.hat + "]}", siddet, "2026-01-01", "");
    }

    // ------------------------------------------------------------------ örnek 3: dizel hatlarda sefer en fazla 8 saat

    @Test
    void ornek3_aday_ata_otomatik_hepsi_ayni_kurali_uygular() throws Exception {
        long uzun = v.sefer("U1", D12, "08:00", "16:01"); // 8 s 1 dk, hat A
        long tam = v.sefer("U2", D12, "08:00", "16:00"); // 8 s 0 dk, hat A
        long baska = v.sefer("U3", D12, "08:00", "17:30"); // 9,5 s ama hat Z (kapsam dışı)
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, baska);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", dizelKurali("ENGEL")).andExpect(status().isCreated());
        Tarayici t = atamaci();

        // aday listesi
        t.get("/api/atama/sefer/" + uzun + "/adaylar").andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[*].uygun", everyItem(is(false))))
                .andExpect(jsonPath("$[0].ihlaller[*].kod", hasItem("Ö-001")))
                .andExpect(jsonPath("$[0].ihlaller[?(@.kod=='Ö-001')].metin", hasItem("Tek sefer süresi: 8 s 1 dk > 8 s (Kural: Dizel hatlar 8 s)")))
                .andExpect(jsonPath("$[0].ihlaller[?(@.kod=='Ö-001')].siddet", hasItem("ENGEL")));
        t.get("/api/atama/sefer/" + tam + "/adaylar").andExpect(jsonPath("$[*].uygun", everyItem(is(true))));
        t.get("/api/atama/sefer/" + baska + "/adaylar").andExpect(jsonPath("$[*].uygun", everyItem(is(true))));

        // atama
        t.post("/api/atama/sefer/" + uzun, AtamaVeri.ataGovdesi(m1)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"))
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("Ö-001")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
        // yerine ata önerisi aynı etiketi kullanır
        t.get("/api/atama/sefer/" + uzun + "/oneri").andExpect(jsonPath("$.oneriler", hasSize(0))).andExpect(jsonPath("$.mesaj", containsString("0/2 uygun: Dizel hatlar 8 s 2")));

        // otomatik atama (önizleme ve uygula): uzun sefer boş, neden kural adını taşır
        for (boolean uygula : new boolean[] { false, true }) {
            t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-12\",\"uygula\":" + uygula + "}").andExpect(status().isOk())
                    .andExpect(jsonPath("$.bosKalanlar[*].seferId", hasItem((int) uzun)))
                    .andExpect(jsonPath("$.bosKalanlar[?(@.seferId==" + uzun + ")].neden", hasItem(containsString("Dizel hatlar 8 s 2"))))
                    .andExpect(jsonPath("$.atananlar[*].seferId", hasItem((int) baska)));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, uzun)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id IN (?, ?)", Integer.class, tam, baska)).isEqualTo(2);
    }

    @Test
    void uyari_gerekceZorunlu_anlikGoruntuKuraliVeKontrolEdilenKodlariTasir() throws Exception {
        long uzun = v.sefer("U1", D12, "08:00", "16:01");
        yonetici().post("/api/ozel-kural", dizelKurali("UYARI")).andExpect(status().isCreated());
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + uzun, AtamaVeri.ataGovdesi(m1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        t.post("/api/atama/sefer/" + uzun, "{\"makinistId\":" + m1 + ",\"gerekce\":\"Tek uygun makinist\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.uyarilar[0].kod").value("Ö-001")).andExpect(jsonPath("$.uyarilar[0].siddet").value("UYARI"));

        String ayrinti = jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class);
        assertThat(ayrinti).contains("\"sonuc\":\"UYARI\"").contains("Ö-001").contains("Kural: Dizel hatlar 8 s").contains("\"olculen\":8.02").contains("\"esik\":8.0");
        assertThat(ayrinti.substring(0, ayrinti.indexOf("\"sonuc\""))).contains("\"Ö-001\"");

        // BUG-1: denetim raporunda uyarılı atama satırı uyaran kuralın kodunu ve adını gösterir
        yonetici().get("/api/degisiklik?bas=2026-01-01&bit=2026-12-31&tur=ATAMA").andExpect(status().isOk())
                .andExpect(jsonPath("$.bolumler[?(@.kod=='UYARILI_ATAMALAR')].satirlar[0].uyarilar[0]")
                        .value(hasItem("Ö-001: Tek sefer süresi: 8 s 1 dk > 8 s (Kural: Dizel hatlar 8 s)")));

        // kural sonradan gevşetilince eski atamanın kaydı değişmez (o günkü eşik ihlalde)
        yonetici().put("/api/ozel-kural/1", govde("Dizel hatlar 8 s", "TEK_SEFER_SURESI", "SEFER", "9", "{\"hatlar\":[" + v.hat + "]}", "UYARI", "2026-01-01",
                ",\"versiyon\":0,\"neden\":\"gevşetildi\"")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class)).isEqualTo(ayrinti);
        // geçmiş tanım kural geçmişinden okunur
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'KURAL_GUNCELLE'", String.class)).contains("\"esik\":8");
    }

    @Test
    void kapali_hicDegerlendirilmez_kontrolEdilenKodlardaYok() throws Exception {
        long uzun = v.sefer("U1", D12, "08:00", "16:01");
        yonetici().post("/api/ozel-kural", dizelKurali("KAPALI")).andExpect(status().isCreated());
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + uzun + "/adaylar").andExpect(jsonPath("$[*].uygun", everyItem(is(true))));
        t.post("/api/atama/sefer/" + uzun, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated());
        String ayrinti = jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class);
        assertThat(ayrinti).doesNotContain("Ö-001");
    }

    // ------------------------------------------------------------------ FR-03: canlı kırmızı

    /** 4 s + 4 s + 3 s, araları 4 s (ayrı nöbetler; aynı nöbette olsalar sorumlu komşu kuralı üçünü de kırmızı yapardı); K-11/K-12 kapalı. */
    private void ucGorev() {
        jdbc.update("UPDATE kural_siddet SET siddet = 'KAPALI' WHERE kod IN ('K-11', 'K-12')");
        v.atama(v.seferEv("G1", D12, "04:00", "08:00"), m1, "ASIL");
        v.atama(v.seferEv("G2", D12, "12:00", "16:00"), m1, "ASIL");
        v.atama(v.seferEv("G3", D12, "20:00", "23:00"), m1, "ASIL");
    }

    @Test
    void canliKirmizi_4_4_3_yalnizUcuncuKirmizi_KAPALIiseHepsiYesil_gecerlilikBaslangici() throws Exception {
        ucGorev();
        Tarayici t = atamaci();
        t.get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[*].kirmizi", everyItem(is(false)))); // K-13 11 s: hepsi yeşil

        Tarayici y = yonetici();
        y.post("/api/ozel-kural", govde("Günlük 8 s", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "ENGEL", "2026-01-01", "")).andExpect(status().isCreated());
        t.get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false))
                .andExpect(jsonPath("$.satirlar[1].makinistler[0].kirmizi").value(false)).andExpect(jsonPath("$.satirlar[2].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[2].makinistler[0].kirmiziNedenler[0]").value(containsString("11 s > 8 s (Kural: Günlük 8 s)")));
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D12).andExpect(jsonPath("$.kirmiziAtama").value(1));

        // geçerlilik başlangıcı 1 Kas: 12 Eki'nde uygulanmaz
        y.put("/api/ozel-kural/1", govde("Günlük 8 s", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "ENGEL", "2026-11-01", ",\"versiyon\":0,\"neden\":\"yürürlük\"")).andExpect(status().isOk());
        t.get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[*].makinistler[0].kirmizi", everyItem(is(false))));

        // yürürlük geri alınır, sonra KAPALI
        y.put("/api/ozel-kural/1", govde("Günlük 8 s", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "ENGEL", "2026-01-01", ",\"versiyon\":1,\"neden\":\"geri\"")).andExpect(status().isOk());
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D12).andExpect(jsonPath("$.kirmiziAtama").value(1));
        y.put("/api/ozel-kural/1", govde("Günlük 8 s", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "KAPALI", "2026-01-01", ",\"versiyon\":2,\"neden\":\"kapat\"")).andExpect(status().isOk());
        t.get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[*].makinistler[0].kirmizi", everyItem(is(false))));
    }

    @Test
    void ayPenceresi_yerlesikKuralYokkenOzelKuralUyarisi_AYsinirindaAyri() throws Exception {
        // Ekim'de 4 gün x 8 s = 32 s; 5. gün 8 s daha: 40 s. "Ayda en fazla 36 s" ENGEL => 5. gün kırmızı
        for (int g = 1; g <= 4; g++) {
            v.atama(v.seferEv("A" + g, LocalDate.of(2026, 10, g + 4), "06:00", "14:00"), m1, "ASIL");
        }
        long bes = v.seferEv("A5", LocalDate.of(2026, 10, 12), "06:00", "14:00");
        v.atama(bes, m1, "ASIL");
        yonetici().post("/api/ozel-kural", govde("Aylık 36 s", "TOPLAM_GOREV_SURESI", "AY", "36", null, "ENGEL", "2026-01-01", "")).andExpect(status().isCreated());

        atamaci().get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value(containsString("(Ekim 2026): 40 s > 36 s")));
        // Kasım'daki görev ayrı pencere
        long kasim = v.seferEv("K1", LocalDate.of(2026, 11, 2), "06:00", "14:00");
        v.atama(kasim, m2, "ASIL");
        atamaci().get("/api/atama/gun?tarih=2026-11-02").andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
    }

    // ------------------------------------------------------------------ MK-5: yıl penceresinde ileri yükleme

    @Test
    void yilKurali_16GunOtesindekiMevcutAtamayiGorur_yazmaAninda422() throws Exception {
        // yıl toplamı 20 s (10 + 10 Kasım); 20 Ekim'e 1 s daha eklenirse 11 Kasım görevi kırmızı olurdu (MK-5): atama daha yazılmadan reddedilmeli
        v.atama(v.seferEv("N1", LocalDate.of(2026, 11, 10), "08:00", "18:00"), m1, "ASIL");
        v.atama(v.seferEv("N2", LocalDate.of(2026, 11, 11), "08:00", "18:00"), m1, "ASIL");
        long ekim = v.seferEv("E1", LocalDate.of(2026, 10, 20), "08:00", "09:00");
        yonetici().post("/api/ozel-kural", govde("Yıl 20 s", "TOPLAM_GOREV_SURESI", "YIL", "20", null, "ENGEL", "2026-01-01", "")).andExpect(status().isCreated());
        Tarayici t = atamaci();

        t.get("/api/atama/kirmizi-sayisi?bas=2026-11-10&bit=2026-11-11").andExpect(jsonPath("$.kirmiziAtama").value(0));
        t.post("/api/atama/sefer/" + ekim, AtamaVeri.ataGovdesi(m1)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("Ö-001")));
        t.post("/api/atama/sefer/" + ekim, AtamaVeri.ataGovdesi(m2)).andExpect(status().isCreated());
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-20&bit=2026-11-11").andExpect(jsonPath("$.kirmiziAtama").value(0));
    }
}
