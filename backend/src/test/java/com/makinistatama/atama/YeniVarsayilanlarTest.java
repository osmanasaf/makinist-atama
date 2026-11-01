package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * OK-24 kabul testleri, taze veritabanında YENİ varsayılanlarla (V30): AC-1 sayılar ve değerler, AC-2 K-23 19/20 dk, AC-3 haftalık 45 s üstü engel değil
 * (fazla mesai), yıllık 270 s (K-15) ve gece nöbetli haftada fazla mesai yasağı (K-17) hâlâ ENGEL.
 */
class YeniVarsayilanlarTest extends TestVeritabani {

    static final LocalDate PZT = LocalDate.of(2026, 10, 5);

    private String sayi(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }

    @Test
    void ac1_taze_veritabani_yeni_varsayilanlar() {
        assertThat(sayi("SELECT count(*) FROM kural_parametre")).isEqualTo("32");
        assertThat(sayi("SELECT count(*) FROM kural_siddet")).isEqualTo("22");
        assertThat(sayi("SELECT count(*) FROM uygulama_ayar")).isEqualTo("34");
        assertThat(sayi("SELECT siddet FROM kural_siddet WHERE kod = 'K-14'")).isEqualTo("KAPALI");
        assertThat(sayi("SELECT siddet FROM kural_siddet WHERE kod = 'K-23'")).isEqualTo("ENGEL");
        assertThat(sayi("SELECT deger FROM kural_parametre WHERE anahtar = 'nobet.asgari_aktarma_dk'")).isEqualTo("20");
        assertThat(sayi("SELECT deger FROM kural_parametre WHERE anahtar = 'dinlenme.ev_saat'")).isEqualTo("11");
        assertThat(sayi("SELECT deger FROM kural_parametre WHERE anahtar = 'dinlenme.dis_merkez_saat'")).isEqualTo("11");
        assertThat(sayi("SELECT deger FROM kural_parametre WHERE anahtar = 'haftalik.normal_saat'")).isEqualTo("45");
        assertThat(sayi("SELECT deger FROM kural_parametre WHERE anahtar = 'haftalik.max_saat'")).isEqualTo("45");
        assertThat(sayi("SELECT siddet FROM kural_siddet WHERE kod = 'K-17'")).isEqualTo("ENGEL");
        assertThat(sayi("SELECT siddet FROM kural_siddet WHERE kod = 'K-15'")).isEqualTo("ENGEL");
    }

    /** AC-2: aynı nöbette ardışık iki görev: 10:00 varış / 10:19 kalkış ihlal, 10:20 uygun; farklı nöbette (4 s+ boşluk) aranmaz. (Aynı tren parçası ve yolculuk muafiyeti: AsgariAktarmaTest.) */
    @Test
    void ac2_k23_19dk_ihlal_20dk_uygun_farkliNobette_aranmaz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        Tarayici t = atamaci();
        LocalDate gun = LocalDate.of(2026, 10, 12);
        t.post("/api/atama/sefer/" + v.seferEv("A", gun, "08:00", "10:00"), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        long b19 = v.seferEv("B19", gun, "10:19", "11:00");
        t.post("/api/atama/sefer/" + b19, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-23")));
        long b20 = v.seferEv("B20", gun, "10:20", "11:00");
        t.post("/api/atama/sefer/" + b20, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        long uzak = v.seferEv("C", gun, "15:05", "16:00"); // B20 11:00'da biter: 4 s 5 dk boşluk = ayrı nöbet; K-23 aranmaz (dinlenme K-11 ayrı kural)
        t.post("/api/atama/sefer/" + uzak, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", not(hasItem("K-23"))));
    }

    /** AC-3: 51 saatlik hafta (6 x 8,5 s) K-14 KAPALI iken ihlal değildir; puantajda 6 saat fazla mesai; K-14 ENGEL'e alınırsa aynı hafta kırmızıdır. */
    @Test
    void ac3_51saatlikHafta_ihlalDegil_fazlaMesai6saat() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        for (int g = 0; g < 6; g++) {
            v.atama(v.seferEv("H" + g, PZT.plusDays(g), "08:00", "16:30"), m, "ASIL");
        }
        Tarayici t = atamaci();
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-05&bit=2026-10-11").andExpect(jsonPath("$.kirmiziAtama").value(0));
        yonetici().get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].fazlaMesaiSaat").value(6.0));

        yonetici().put("/api/kural/K-14/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"eski davranış\"}").andExpect(status().isOk());
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-05&bit=2026-10-11").andExpect(jsonPath("$.kirmiziAtama").value(not(0)));
    }

    /** AC-3: yıllık fazla mesai üst sınırı (K-15, 270 s) ve gece nöbetli haftada normal sürenin aşılması (K-17) varsayılanda ENGEL kalır (S-1). */
    @Test
    void ac3_k15_k17_engel_kalir_gecenobetiliHaftaFazlaMesaiYok() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        // 5 gün gündüz 9 s = 45 s; 6. gün gece nöbeti (22:00-23:30) haftayı 46,5 s yapar -> K-17 (gece görevi normal süreyi aşırtamaz)
        for (int g = 0; g < 5; g++) {
            v.atama(v.seferEv("G" + g, PZT.plusDays(g), "08:00", "17:00"), m, "ASIL");
        }
        long gece = v.seferEv("GECE", PZT.plusDays(5), "22:00", "23:30");
        atamaci().post("/api/atama/sefer/" + gece, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-17")));
        // gündüz görev 45 s üstüne eklenebilir (K-14 kapalı, K-17 yalnız gece görevi için)
        long gunduz = v.seferEv("GUNDUZ", PZT.plusDays(5), "08:00", "10:00");
        atamaci().post("/api/atama/sefer/" + gunduz, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
    }
}
