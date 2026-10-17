package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.ARAC;
import static com.makinistatama.kural.TestVerisi.ESKISEHIR;
import static com.makinistatama.kural.TestVerisi.HAT;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.gorev;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** F2b MK-40: hat/araç/tren türü kapsamlı özel kural yolculuğa uygulanmaz; kapsamsız süre kuralı sayar; görev sayısı yolculuğu saymaz. */
class YolculukKapsamTest {

    private static Gorev yolculuk(long id, String bas, String bit) {
        return Gorev.yolculukOlustur(id, ANKARA, ESKISEHIR, HAT, ARAC, an(bas), an(bas), an(bit), true);
    }

    private static List<Ihlal> ozel(OzelKural k, Gorev aday, List<Gorev> diger) {
        KuralBaglami b = TestVerisi.baglam(aday, diger, TestVerisi.makinist(), OzelKuralYap.p(k));
        return OzelKuralDegerlendirici.kontrol(b, Onhesap.of(b));
    }

    @Test
    void gorevKapsar_hatKapsamliKuralYolculugaGirmez_kapsamsizGirer() {
        Gorev y = yolculuk(1, "2026-10-12T08:00", "2026-10-12T09:30");
        Gorev s = gorev(2, "2026-10-12T08:00", "2026-10-12T09:30");
        Kapsam hat = new Kapsam(Set.of(HAT), null, null, null, null, null);
        Kapsam arac = new Kapsam(null, Set.of(ARAC), null, null, null, null);
        Kapsam tur = new Kapsam(null, null, null, null, Set.of("YOLCU"), null);
        Kapsam gar = new Kapsam(null, null, Set.of(ANKARA), null, null, null);

        assertFalse(hat.gorevKapsar(y));
        assertFalse(arac.gorevKapsar(y));
        assertFalse(tur.gorevKapsar(y));
        assertTrue(gar.gorevKapsar(y)); // gar yolculuğun gerçek garıdır
        assertTrue(hat.gorevKapsar(s));
        assertTrue(arac.gorevKapsar(s));
        assertTrue(Kapsam.TUMU.gorevKapsar(y));
    }

    @Test
    void kapsamsizGunlukSure_yolculuguSayar_hatKapsamliSaymaz() {
        // yolculuk 5 s + sefer 4 s aynı gün; eşik 8 s: kapsamsız toplam 9 s > 8 s ihlal, hat kapsamlı yalnız sefer: 4 s, ihlal yok
        Gorev y = yolculuk(1, "2026-10-12T06:00", "2026-10-12T11:00");
        Gorev s = gorev(2, "2026-10-12T11:30", "2026-10-12T15:30");

        assertEquals(1, ozel(OzelKuralYap.kural(1, "K1", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8"), s, List.of(y)).size());
        assertTrue(ozel(OzelKuralYap.kural(2, "K2", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8", OzelKuralYap.hat(HAT), Siddet.ENGEL), s, List.of(y)).isEmpty());
    }

    @Test
    void gorevSayisi_yolculuguSaymaz_adayYolculuksaSayiArtmaz() {
        Gorev y1 = yolculuk(1, "2026-10-12T06:00", "2026-10-12T07:00");
        Gorev y2 = yolculuk(2, "2026-10-12T07:30", "2026-10-12T08:30");
        Gorev s = gorev(3, "2026-10-12T09:00", "2026-10-12T10:00");
        OzelKural enFazla1 = OzelKuralYap.kural(1, "K1", Olcu.GOREV_SAYISI, Pencere.GUN, "1");

        assertTrue(ozel(enFazla1, s, List.of(y1, y2)).isEmpty(), "iki yolculuk + bir sefer = 1 görev");
        assertTrue(ozel(enFazla1, y2, List.of(s, y1)).isEmpty(), "aday yolculuk sayıyı artırmaz");
        assertEquals(1, ozel(enFazla1, gorev(4, "2026-10-12T11:00", "2026-10-12T12:00"), List.of(s)).size(), "iki sefer = 2 görev > 1");
    }
}
