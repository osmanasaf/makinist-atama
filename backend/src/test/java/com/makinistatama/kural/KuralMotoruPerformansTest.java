package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NFR-02 olcumu: otomatik atama onizlemesi = 3.100 sefer x 40 makinist = 124.000 degerlendirme, <= 30 sn.
 * Kural motoru tek is parcacigi; baglam yukleme/kayit haric. Olcum cikti satirina yazilir.
 */
class KuralMotoruPerformansTest {

    private static final long NFR02_DEGERLENDIRME = 3_100L * 40;
    private static final double NFR02_SANIYE = 30.0;

    /** Adaydan geriye gunluk sabah gorevi (06-12) + her 3 gunde bir 22:00-03:00 gece nobeti, toplam {@code adet} gorev. */
    private static List<Gorev> yogunBaglam(int adet) {
        List<Gorev> l = new ArrayList<>();
        LocalDate gun = LocalDate.of(2026, 10, 19);
        long id = 1;
        while (l.size() < adet) {
            l.add(gorev(id++, gun + "T06:00", gun + "T12:00"));
            if (l.size() < adet && gun.getDayOfMonth() % 3 == 0) {
                l.add(gorev(id++, gun + "T22:00", gun.plusDays(1) + "T03:00"));
            }
            gun = gun.minusDays(1);
        }
        return l;
    }

    private static double olc(int baglamGorev, int tekrar) {
        Gorev aday = gorev(100_000, "2026-10-20T08:00", "2026-10-20T10:00");
        KuralBaglami b = baglam(aday, yogunBaglam(baglamGorev), param());
        for (int i = 0; i < 3_000; i++) { // JIT isinmasi
            KuralMotoru.degerlendir(b);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < tekrar; i++) {
            KuralMotoru.degerlendir(b);
        }
        double mikro = (System.nanoTime() - t0) / 1e3 / tekrar;
        double tahminSn = mikro * NFR02_DEGERLENDIRME / 1e6;
        System.out.printf("NFR-02 olcum: baglam=%d gorev -> %.0f us/degerlendirme, %d degerlendirme tahmini %.1f sn%n",
                baglamGorev, mikro, NFR02_DEGERLENDIRME, tahminSn);
        return tahminSn;
    }

    @Test
    void nfr02_tipik_baglam_60_gorev_124bin_degerlendirme_30_sn_altinda() {
        assertTrue(olc(60, 20_000) <= NFR02_SANIYE);
    }

    @Test
    void nfr02_en_kotu_baglam_250_gorev_K15_yil_basi_penceresi_30_sn_altinda() {
        assertTrue(olc(250, 10_000) <= NFR02_SANIYE);
    }
}
