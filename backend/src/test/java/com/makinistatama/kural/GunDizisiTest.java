package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.an;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T1.3: gün dizisi ve takvim yardımcıları. */
class GunDizisiTest {

    private static final LocalTime G20 = LocalTime.of(20, 0);
    private static final LocalTime G06 = LocalTime.of(6, 0);

    private static Aralik ar(String bas, String bit) {
        return new Aralik(an(bas), an(bit));
    }

    @Test
    void gunDizisi_22_06_iki_gune_120_ve_360_dakika() {
        GunDizisi d = GunDizisi.of(List.of(ar("2026-10-12T22:00", "2026-10-13T06:00")));
        assertEquals(120, d.toplam(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13)));
        assertEquals(360, d.toplam(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14)));
        assertEquals(480, d.toplam(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertEquals(0, d.toplam(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 20)));
        assertEquals(0, GunDizisi.of(List.of()).toplam(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void gunDizisi_ay_siniri_31_Eki_1_Kas() {
        GunDizisi d = GunDizisi.of(List.of(ar("2026-10-31T20:00", "2026-11-01T04:00")));
        assertEquals(240, d.toplam(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)));
        assertEquals(240, d.toplam(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)));
    }

    @Test
    void gunDizisi_pazar_pazartesi_hafta_siniri() {
        GunDizisi d = GunDizisi.of(List.of(ar("2026-10-11T22:00", "2026-10-12T02:00"))); // Pazar 22:00 - Pazartesi 02:00
        assertEquals(120, d.toplam(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)));
        assertEquals(120, d.toplam(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)));
    }

    @Test
    void gece_dizisi_gece_dakikasi_ZamanHesabi_ile_ayni_toplami_verir() {
        List<Aralik> a = List.of(ar("2026-10-12T22:00", "2026-10-13T06:00"), ar("2026-10-14T05:00", "2026-10-14T21:00"), ar("2026-10-20T08:00", "2026-10-20T12:00"));
        GunDizisi d = GunDizisi.of(a);
        long beklenen = 0;
        for (Aralik x : a) {
            beklenen += ZamanHesabi.geceDakika(x.bas(), x.bit(), G20, G06);
        }
        assertEquals(beklenen, d.gece(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), G20, G06));
        assertEquals(120, d.gece(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), G20, G06)); // 20:00 sonrası 22-24
        assertEquals(360, d.gece(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), G20, G06));
        assertEquals(60 + 60, d.gece(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), G20, G06)); // 05-06 ve 20-21
        assertEquals(0, d.gece(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), G20, G06));
    }

    @Test
    void gece_dizisi_gece_yarisini_asmayan_pencere() {
        List<Aralik> a = List.of(ar("2026-10-12T22:00", "2026-10-13T06:00"));
        GunDizisi d = GunDizisi.of(a);
        LocalTime sifir = LocalTime.of(0, 0);
        LocalTime bes = LocalTime.of(5, 0);
        assertEquals(ZamanHesabi.geceDakika(a.get(0).bas(), a.get(0).bit(), sifir, bes), d.gece(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), sifir, bes));
        assertEquals(300, d.gece(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), sifir, bes));
    }

    @Test
    void ay_yardimcilari_ay_uzunluklari_ve_etiket() {
        assertEquals(LocalDate.of(2026, 2, 1), ZamanHesabi.ayBasi(LocalDate.of(2026, 2, 28)));
        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), ZamanHesabi.aylar(an("2026-10-31T20:00"), an("2026-11-01T04:00")));
        assertEquals(List.of(LocalDate.of(2026, 10, 1)), ZamanHesabi.aylar(an("2026-10-30T20:00"), an("2026-11-01T00:00"))); // 00:00'da biten kasıma dokunmaz
        assertEquals("Ekim 2026", ZamanHesabi.ay(LocalDate.of(2026, 10, 1)));
        assertEquals(LocalDate.of(2026, 3, 1), OzelKuralDegerlendirici.pencereSon(Pencere.AY, LocalDate.of(2026, 2, 1)));
        assertEquals(LocalDate.of(2027, 1, 1), OzelKuralDegerlendirici.pencereSon(Pencere.YIL, LocalDate.of(2026, 1, 1)));
        assertEquals(LocalDate.of(2026, 10, 12), OzelKuralDegerlendirici.pencereBasi(Pencere.HAFTA, LocalDate.of(2026, 10, 18))); // Pazar -> Pazartesi
        assertEquals(28, OzelKuralDegerlendirici.pencereSon(Pencere.AY, LocalDate.of(2026, 2, 1)).toEpochDay() - LocalDate.of(2026, 2, 1).toEpochDay());
    }
}
