package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T4.1-T4.2: "kuralı dene" (FR-07): kaydetmeden etki sayısı + örnekler, yazmaz, aynı kırmızı hesabını kullanır. Testlerde şimdi = 10 Eki. */
class OzelKuralOnizlemeTest extends TestVeritabani {

    static final LocalDate D12 = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long m1;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m1 = v.makinist("D001");
        // FR-03 verisi: 12 Eki 4 s + 4 s + 3 s (toplam 11 s; K-13 sınırı 11). Araları 4 s: AYRI nöbetler (nobet.ayirma_saat); aynı nöbette olsalardı sorumlu komşu
        // kuralı (MK-5, BUG-14) yüzünden üçü de kırmızı sayılırdı (bkz. dene_ayniNobetteki_gorevlerin_hepsi_kirmizi). K-11/K-12 dinlenme bu veriyi ayrıca kırmızı yapmasın diye kapalı.
        jdbc.update("UPDATE kural_siddet SET siddet = 'KAPALI' WHERE kod IN ('K-11', 'K-12')");
        v.atama(v.seferEv("G1", D12, "04:00", "08:00"), m1, "ASIL");
        v.atama(v.seferEv("G2", D12, "12:00", "16:00"), m1, "ASIL");
        v.atama(v.seferEv("G3", D12, "20:00", "23:00"), m1, "ASIL");
    }

    private static String govde(String esik, String siddet, String ek) {
        return "{\"ad\":\"Günlük 8 s\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":" + esik + ",\"siddet\":\"" + siddet
                + "\",\"gerekce\":\"deneme\",\"gecerlilikBas\":\"2026-01-01\"" + ek + "}";
    }

    @Test
    void dene_1makinist_1atama_kirmizi_olur_ornekli_hicbirSeyYazilmaz() throws Exception {
        long kayitOnce = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Long.class);

        yonetici().post("/api/ozel-kural/dene", govde("8", "ENGEL", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.bas").value("2026-10-10")).andExpect(jsonPath("$.bit").value("2026-11-09")).andExpect(jsonPath("$.toplamAtama").value(3))
                .andExpect(jsonPath("$.kirmiziAtama").value(1)).andExpect(jsonPath("$.kirmiziMakinist").value(1)).andExpect(jsonPath("$.uyariAtama").value(0))
                .andExpect(jsonPath("$.ornekler", hasSize(1))).andExpect(jsonPath("$.ornekler[0].sicil").value("D001")).andExpect(jsonPath("$.ornekler[0].tarih").value("2026-10-12"))
                .andExpect(jsonPath("$.ornekler[0].trenNo").value("G3")).andExpect(jsonPath("$.ornekler[0].durum").value("KIRMIZI"))
                .andExpect(jsonPath("$.ornekler[0].metin").value(containsString("11 s > 8 s (Kural: Günlük 8 s)")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Long.class)).isEqualTo(kayitOnce);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(3);
    }

    /** Aynı nöbetteki (arası 4 s'ten kısa) görevler birlikte sorumludur: nöbet birleşince toplam değişir (MK-5); üçü de kırmızı olur. */
    @Test
    void dene_ayniNobetteki_gorevlerin_hepsi_kirmizi() throws Exception {
        jdbc.update("DELETE FROM atama");
        v.atama(v.seferEv("H1", D12, "06:00", "10:00"), m1, "ASIL");
        v.atama(v.seferEv("H2", D12, "11:00", "15:00"), m1, "ASIL");
        v.atama(v.seferEv("H3", D12, "16:00", "19:00"), m1, "ASIL");
        yonetici().post("/api/ozel-kural/dene", govde("8", "ENGEL", "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(3))
                .andExpect(jsonPath("$.kirmiziMakinist").value(1))
                .andExpect(jsonPath("$.ornekler", hasSize(1))); // BUG-6: aynı makinist, gün ve metin üç atama için tek örnek satırı
    }

    @Test
    void dene_UYARI_uyariSayilir_kirmiziDegil() throws Exception {
        yonetici().post("/api/ozel-kural/dene", govde("8", "UYARI", "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(0))
                .andExpect(jsonPath("$.uyariAtama").value(1)).andExpect(jsonPath("$.uyariMakinist").value(1)).andExpect(jsonPath("$.ornekler[0].durum").value("UYARI"));
    }

    @Test
    void dene_etkisizKural_veKapali_sifirFark() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural/dene", govde("24", "ENGEL", "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(0))
                .andExpect(jsonPath("$.ornekler", hasSize(0)));
        y.post("/api/ozel-kural/dene", govde("8", "KAPALI", "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void dene_gecersizTaslak_400_yetkisizAtamaci403() throws Exception {
        yonetici().post("/api/ozel-kural/dene", govde("25", "ENGEL", "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.esik").exists());
        yonetici().post("/api/ozel-kural/dene?id=99", govde("8", "ENGEL", "")).andExpect(status().isNotFound());
        atamaci().post("/api/ozel-kural/dene", govde("8", "ENGEL", "")).andExpect(status().isForbidden());
    }

    @Test
    void dene_mevcutKuraliDuzenlerken_taslakOnunYerineGecer() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", govde("8", "ENGEL", "")).andExpect(status().isCreated()); // 3. atama zaten kırmızı
        // eşik 12 s'e çekilince kırmızı olacak yeni atama yok (taslak eski sürümün yerine geçer, ikisi birden uygulanmaz)
        y.post("/api/ozel-kural/dene?id=1", govde("12", "ENGEL", "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(0));
        // eşik 5 s: 2. atama da (8 s > 5) ve 3. atama (zaten kırmızı) -> yeni kırmızı yalnız 2. ve 1.? (4 s > 5 değil): 2. atama
        y.post("/api/ozel-kural/dene?id=1", govde("5", "ENGEL", "")).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(1))
                .andExpect(jsonPath("$.ornekler[0].trenNo").value("G2"));
        assertThat(jdbc.queryForObject("SELECT versiyon FROM ozel_kural", Long.class)).isZero(); // dene yazmadı
    }

    @Test
    void dene_pencereKapsami_31gun_gecmisTarihliAtamalarDisarida() throws Exception {
        v.atama(v.seferEv("E1", LocalDate.of(2026, 9, 1), "06:00", "16:00"), m1, "ASIL"); // 10 s: bugünden önce
        yonetici().post("/api/ozel-kural/dene", govde("8", "ENGEL", "")).andExpect(status().isOk()).andExpect(jsonPath("$.toplamAtama").value(3))
                .andExpect(jsonPath("$.kirmiziAtama").value(1));
    }
}
