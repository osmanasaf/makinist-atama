package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.ZamanHesabi.Aralik;

/** OK-1/OK-2: çalışma aralığının tek tanımı (kural, adalet ve puantaj {@link Gorev#calismaAraligi}'ni kullanır). */
class GorevTest {

    @Test
    void kalkisGecikmesiCalismaSuresindenSayilir_planlananKalkistanGuncelVarisa() {
        Aralik a = Gorev.calismaAraligi(an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T13:00"), param());

        assertEquals(an("2026-10-12T08:00"), a.bas());
        assertEquals(an("2026-10-12T13:00"), a.bit());
        assertEquals(300, a.dakika());
    }

    @Test
    void hazirlikVeAlikoymaEklenir_gecikmeyleBirlikte() {
        Parametreler p = param("gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "30");

        Aralik a = Gorev.calismaAraligi(an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T13:00"), p);

        assertEquals(an("2026-10-12T07:30"), a.bas());
        assertEquals(an("2026-10-12T13:30"), a.bit());
    }

    @Test
    void olustur_ayniAraligiKullanir() {
        Parametreler p = param("gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "15");

        Gorev g = Gorev.olustur(1, 1, 1, 10, 20, an("2026-10-12T08:00"), an("2026-10-12T09:00"), an("2026-10-12T13:00"), 100, p);

        assertEquals(an("2026-10-12T07:30"), g.baslangic());
        assertEquals(an("2026-10-12T13:15"), g.bitis());
        assertEquals(an("2026-10-12T09:00"), g.kalkis()); // belge/yetki/K-21 için sefer saatleri güncel kalır
    }
}
