package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.ARAC;
import static com.makinistatama.kural.TestVerisi.ESKISEHIR;
import static com.makinistatama.kural.TestVerisi.HAT;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.ZamanHesabi.Aralik;

/** F2b MK-39: yolculuk {@code Gorev}'i: hazırlık/alıkoyma yok, km 0, sayılmayan yolculukta süre 0; sefer görevi ve eski kurucular aynı. */
class GorevYolculukTest {

    private static final Parametreler HAZIRLIKLI = param("gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "30");

    @Test
    void yolculukta_hazirlikVeAlikoymaYok_seferdeVar() {
        Gorev sefer = Gorev.olustur(1, ANKARA, ESKISEHIR, HAT, ARAC, an("2026-10-12T08:00"), an("2026-10-12T08:00"), an("2026-10-12T09:30"), 245, HAZIRLIKLI);
        Gorev yolculuk = Gorev.yolculukOlustur(2, ANKARA, ESKISEHIR, HAT, ARAC, an("2026-10-12T08:00"), an("2026-10-12T08:00"), an("2026-10-12T09:30"), true);

        assertEquals(an("2026-10-12T07:30"), sefer.baslangic());
        assertEquals(an("2026-10-12T10:00"), sefer.bitis());
        assertEquals(an("2026-10-12T08:00"), yolculuk.baslangic());
        assertEquals(an("2026-10-12T09:30"), yolculuk.bitis());
        assertEquals(90, yolculuk.sureDakika());
        assertEquals(0, yolculuk.km());
        assertTrue(yolculuk.yolculuk());
        assertFalse(sefer.yolculuk());
        assertTrue(sefer.sureSayilir());
    }

    @Test
    void yolculukBaslangici_gecikmedenOnceAtanmisIse_planlananKalkistir() {
        Gorev g = Gorev.yolculukOlustur(1, ANKARA, ESKISEHIR, HAT, ARAC, an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T11:00"), true);

        assertEquals(an("2026-10-12T08:00"), g.baslangic()); // OK-6 ile aynı: bekleme çalışmadır
        assertEquals(an("2026-10-12T09:00"), g.kalkis());
    }

    @Test
    void sureSayilmiyorsa_sureDakika0_aralikYineDolu() {
        Gorev g = Gorev.yolculukOlustur(1, ANKARA, ESKISEHIR, HAT, ARAC, an("2026-10-12T08:00"), an("2026-10-12T08:00"), an("2026-10-12T09:30"), false);

        assertEquals(0, g.sureDakika());
        assertEquals(an("2026-10-12T08:00"), g.baslangic());
        assertEquals(an("2026-10-12T09:30"), g.bitis());
    }

    @Test
    void calismaAraligi_yolculukDali_tekFonksiyon_parametreOkumaz() {
        Aralik y = Gorev.calismaAraligi(true, an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T11:00"), null);
        Aralik s = Gorev.calismaAraligi(false, an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T11:00"), HAZIRLIKLI);

        assertEquals(new Aralik(an("2026-10-12T08:00"), an("2026-10-12T11:00")), y);
        assertEquals(new Aralik(an("2026-10-12T07:30"), an("2026-10-12T11:30")), s);
        assertEquals(Gorev.calismaAraligi(an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T11:00"), HAZIRLIKLI), s);
    }

    @Test
    void eskiKurucular_surusGorevi_varsayilanlarla() {
        Gorev g = new Gorev(1, ANKARA, ESKISEHIR, HAT, ARAC, an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T08:00"), an("2026-10-12T09:00"), 100);

        assertFalse(g.yolculuk());
        assertTrue(g.sureSayilir());
        assertEquals(60, g.sureDakika());
    }
}
