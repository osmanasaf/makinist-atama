package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Siddet;

/** MK-96 (saf, DB'siz): her çapraz kural iki yönden ve sınır değerlerinde; K-14 KAPALI iken haftalık üst sınır ≥ normal aranmaz. */
class CaprazDenetimTest {

    /** Varsayılan değerler: ayırma 4 s, dış merkez 11 s, normal 45, üst sınır 45, aktarma 20 dk, gece 7,5 s, günlük 11 s. */
    private static String hata(String anahtar, double yeni, Siddet k14, Map<String, Double> degisen) {
        return hata(anahtar, yeni, k14, Siddet.ENGEL, degisen);
    }

    private static String hata(String anahtar, double yeni, Siddet k14, Siddet k23, Map<String, Double> degisen) {
        Map<String, Double> d = new HashMap<>(Map.of("nobet.ayirma_saat", 4.0, "dinlenme.dis_merkez_saat", 11.0, "haftalik.normal_saat", 45.0, "haftalik.max_saat", 45.0,
                "nobet.asgari_aktarma_dk", 20.0, "gece.max_saat", 7.5, "gunluk.max_saat", 11.0));
        d.putAll(degisen);
        return CaprazDenetim.parametre(anahtar, yeni, d::get, kod -> kod.equals("K-14") ? k14 : k23);
    }

    private static String hata(String anahtar, double yeni) {
        return hata(anahtar, yeni, Siddet.ENGEL, Map.of());
    }

    @Test
    void ayirma_disMerkezi_asamaz_iki_yon_sinir() {
        assertThat(hata("nobet.ayirma_saat", 11)).isNull();
        assertThat(hata("nobet.ayirma_saat", 11.5)).contains("dış merkez dinlenme süresini (11 s) aşamaz");
        assertThat(hata("dinlenme.dis_merkez_saat", 4)).isNull();
        assertThat(hata("dinlenme.dis_merkez_saat", 3.5)).contains("nöbet ayırma süresinden (4 s) kısa olamaz");
    }

    @Test
    void haftalikUstSinir_normalSureyiAsamaz_K14Kapaliyken_aranmaz() {
        assertThat(hata("haftalik.max_saat", 45)).isNull();
        assertThat(hata("haftalik.max_saat", 44)).contains("normal haftalık süreden (45 s) kısa olamaz");
        assertThat(hata("haftalik.normal_saat", 46)).contains("haftalık üst sınırı (45 s) aşamaz");
        assertThat(hata("haftalik.max_saat", 40, Siddet.UYARI, Map.of())).as("UYARI da kural devrede").isNotNull();
        assertThat(hata("haftalik.max_saat", 40, Siddet.KAPALI, Map.of())).as("KAPALI: aranmaz").isNull();
        assertThat(hata("haftalik.normal_saat", 48, Siddet.KAPALI, Map.of())).as("normal 48 > üst sınır 45, K-14 kapalı").isNull();
    }

    @Test
    void aktarma_ayirmaSaatinin_dakikasindanKucuk_kesin_iki_yon() {
        assertThat(hata("nobet.asgari_aktarma_dk", 239)).isNull();
        assertThat(hata("nobet.asgari_aktarma_dk", 240)).contains("nöbet ayırma süresinin (240 dk) altında olmalı");
        assertThat(hata("nobet.asgari_aktarma_dk", 241)).isNotNull();
        assertThat(hata("nobet.ayirma_saat", 1)).as("20 dk < 60 dk").isNull();
        assertThat(hata("nobet.ayirma_saat", 1, Siddet.ENGEL, Map.of("nobet.asgari_aktarma_dk", 60.0))).as("60 < 60 değil").contains("aktarma süresinin (60 dk) üstünde olmalı");
        assertThat(hata("nobet.ayirma_saat", 1.01, Siddet.ENGEL, Map.of("nobet.asgari_aktarma_dk", 60.0))).isNull();
        assertThat(hata("nobet.ayirma_saat", 0)).as("20 dk < 0 değil").isNotNull();
    }

    @Test
    void aktarmaSifir_kural_aranmaz() {
        assertThat(hata("nobet.ayirma_saat", 0, Siddet.ENGEL, Map.of("nobet.asgari_aktarma_dk", 0.0, "dinlenme.dis_merkez_saat", 0.0))).isNull();
        assertThat(hata("nobet.asgari_aktarma_dk", 0)).isNull();
    }

    /** Review INFO: K-23 KAPALIyken aktarma süresi kullanılmaz; ayırma ile çapraz denetim aranmaz (iki yönde), açılırken denetlenir. */
    @Test
    void aktarma_K23Kapaliyken_aranmaz_acilirkenDenetlenir() {
        assertThat(hata("nobet.asgari_aktarma_dk", 240, Siddet.KAPALI, Siddet.KAPALI, Map.of())).isNull();
        assertThat(hata("nobet.ayirma_saat", 1, Siddet.KAPALI, Siddet.KAPALI, Map.of("nobet.asgari_aktarma_dk", 90.0))).isNull();
        assertThat(hata("nobet.ayirma_saat", 1, Siddet.KAPALI, Siddet.UYARI, Map.of("nobet.asgari_aktarma_dk", 90.0))).contains("aktarma süresinin (90 dk) üstünde");
        Map<String, Double> bozuk = Map.of("nobet.asgari_aktarma_dk", 240.0, "nobet.ayirma_saat", 4.0);
        assertThat(CaprazDenetim.acilis("K-23", bozuk::get)).contains("altında olmalı").contains("Önce aktarma süresini düşürün");
        assertThat(CaprazDenetim.acilis("K-23", Map.of("nobet.asgari_aktarma_dk", 20.0, "nobet.ayirma_saat", 4.0)::get)).isNull();
        assertThat(CaprazDenetim.acilis("K-23", Map.of("nobet.asgari_aktarma_dk", 0.0, "nobet.ayirma_saat", 0.0)::get)).as("0 = aranmaz").isNull();
    }

    @Test
    void geceAzami_gunlukSiniriAsamaz_iki_yon() {
        assertThat(hata("gece.max_saat", 11)).isNull();
        assertThat(hata("gece.max_saat", 11.5)).contains("günlük üst sınırı (11 s) aşamaz");
        assertThat(hata("gunluk.max_saat", 7.5)).isNull();
        assertThat(hata("gunluk.max_saat", 7)).contains("gece görevi azami süresinden (7.5 s) kısa olamaz");
    }

    @Test
    void ilgisizAnahtar_denetlenmez() {
        assertThat(hata("belge.uyari_gun", 1)).isNull();
    }

    @Test
    void k14Aciliyor_ustSinirNormalinAltindaysaReddedilir() {
        assertThat(CaprazDenetim.acilis("K-14", Map.of("haftalik.normal_saat", 45.0, "haftalik.max_saat", 45.0)::get)).isNull();
        assertThat(CaprazDenetim.acilis("K-14", Map.of("haftalik.normal_saat", 48.0, "haftalik.max_saat", 45.0)::get)).contains("normal haftalık süreden (48 s) kısa olamaz");
    }
}
