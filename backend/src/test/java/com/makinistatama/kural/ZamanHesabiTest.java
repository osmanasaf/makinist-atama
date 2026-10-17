package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.an;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZamanHesabiTest {

    private static final LocalTime G20 = LocalTime.of(20, 0);
    private static final LocalTime G06 = LocalTime.of(6, 0);

    private static Aralik ar(String bas, String bit) {
        return new Aralik(an(bas), an(bit));
    }

    private static long gece(String bas, String bit) {
        return ZamanHesabi.geceDakika(an(bas), an(bit), G20, G06);
    }

    @Test
    void sabitSaatDilimiIstanbul() {
        assertEquals(ZoneId.of("Europe/Istanbul"), ZamanHesabi.ISTANBUL);
    }

    @Test
    void gunBolme_22_06_iki_gune_yazilir() {
        Aralik a = ar("2026-10-12T22:00", "2026-10-13T06:00");
        assertEquals(120, ZamanHesabi.gunDakika(a, LocalDate.of(2026, 10, 12)));
        assertEquals(360, ZamanHesabi.gunDakika(a, LocalDate.of(2026, 10, 13)));
    }

    @Test
    void gunBolme_gece_yarisinda_bitis_ertesi_gune_0_yazar() {
        Aralik a = ar("2026-10-12T22:00", "2026-10-13T00:00");
        assertEquals(120, ZamanHesabi.gunDakika(a, LocalDate.of(2026, 10, 12)));
        assertEquals(0, ZamanHesabi.gunDakika(a, LocalDate.of(2026, 10, 13)));
    }

    @Test
    void gunBolme_ay_siniri_31Eki_1Kas() {
        Aralik a = ar("2026-10-31T22:00", "2026-11-01T06:00");
        assertEquals(120, ZamanHesabi.gunDakika(a, LocalDate.of(2026, 10, 31)));
        assertEquals(360, ZamanHesabi.gunDakika(a, LocalDate.of(2026, 11, 1)));
        assertEquals(2, ZamanHesabi.gunler(a.bas(), a.bit()).size());
    }

    @Test
    void hafta_Pazartesi_baslar_Pazar_Pazartesi_siniri() {
        assertEquals(LocalDate.of(2026, 10, 12), ZamanHesabi.haftaBasi(LocalDate.of(2026, 10, 12)));
        assertEquals(LocalDate.of(2026, 10, 12), ZamanHesabi.haftaBasi(LocalDate.of(2026, 10, 18)));
        assertEquals(LocalDate.of(2026, 10, 19), ZamanHesabi.haftaBasi(LocalDate.of(2026, 10, 19)));
        assertEquals(LocalDate.of(2026, 10, 5), ZamanHesabi.haftaBasi(LocalDate.of(2026, 10, 11)));

        Aralik a = ar("2026-10-18T22:00", "2026-10-19T06:00"); // Pazar gecesi
        assertEquals(120, ZamanHesabi.haftaDakika(a, LocalDate.of(2026, 10, 12)));
        assertEquals(360, ZamanHesabi.haftaDakika(a, LocalDate.of(2026, 10, 19)));
        assertEquals(List.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)),
                ZamanHesabi.haftalar(a.bas(), a.bit()));
    }

    @Test
    void gece_payi_18_02_ve_04_11() {
        assertEquals(360, gece("2026-10-12T18:00", "2026-10-13T02:00")); // 6/8 = 0,75
        assertEquals(120, gece("2026-10-12T04:00", "2026-10-12T11:00")); // 2/7
    }

    @Test
    void gece_penceresi_iki_yonlu_23_05() {
        // gece yarisini asan pencere: hem onceki gunun aksami hem o gunun sabahi sayilir
        assertEquals(60, gece("2026-10-13T02:00", "2026-10-13T03:00")); // onceki gunden gelen parca
        assertEquals(60, gece("2026-10-13T21:00", "2026-10-13T22:00")); // ertesi gune giden parca
        assertEquals(60, gece("2026-10-13T19:00", "2026-10-13T21:00")); // yalniz 20:00 sonrasi
        assertEquals(360, gece("2026-10-12T23:00", "2026-10-13T05:00"));
    }

    @Test
    void gece_22_00_ile_00_00_arasi() {
        assertEquals(120, gece("2026-10-12T22:00", "2026-10-13T00:00"));
    }

    @Test
    void gece_cok_gunluk_aralik() {
        // 12 Eki 12:00 -> 14 Eki 12:00: 12 Eki 20-24 (240) + 13 Eki 00-06 (360) + 13 Eki 20-24 (240) + 14 Eki 00-06 (360)
        assertEquals(1200, gece("2026-10-12T12:00", "2026-10-14T12:00"));
    }

    @Test
    void gece_penceresi_baslangic_kucuk_bitis_de_desteklenir() {
        LocalTime b = LocalTime.of(8, 0);
        LocalTime e = LocalTime.of(12, 0);
        assertEquals(240, ZamanHesabi.geceDakika(an("2026-10-12T07:00"), an("2026-10-12T13:00"), b, e));
        // 12 Eki 10:00 -> 14 Eki 10:00: 120 + 240 + 120
        assertEquals(480, ZamanHesabi.geceDakika(an("2026-10-12T10:00"), an("2026-10-14T10:00"), b, e));
        assertEquals(0, ZamanHesabi.geceDakika(an("2026-10-12T12:00"), an("2026-10-12T20:00"), b, e));
    }

    @Test
    void gunler_izin_ve_belge_gunu() {
        assertEquals(5, ZamanHesabi.gunler(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 24)).size());
        // 22:00-00:00 yalniz tek gunu etkiler
        assertEquals(1, ZamanHesabi.gunler(an("2026-10-12T22:00"), an("2026-10-13T00:00")).size());
        assertEquals(2, ZamanHesabi.gunler(an("2026-10-12T22:00"), an("2026-10-13T06:00")).size());
    }

    @Test
    void kesisim_yari_acik() {
        assertEquals(0, ZamanHesabi.kesisim(an("2026-10-12T08:00"), an("2026-10-12T12:00"),
                an("2026-10-12T12:00"), an("2026-10-12T16:00")));
        assertEquals(60, ZamanHesabi.kesisim(an("2026-10-12T08:00"), an("2026-10-12T12:00"),
                an("2026-10-12T11:00"), an("2026-10-12T16:00")));
    }

    @Test
    void bicim_turkce() {
        assertEquals("8 s", ZamanHesabi.sure(480));
        assertEquals("9 s 30 dk", ZamanHesabi.sure(570));
        assertEquals("4 s 45 dk", ZamanHesabi.sure(285));
        assertEquals("7 s 30 dk", ZamanHesabi.sure(450)); // tek biçim: saat ondalık gösterilmez (M2 QA BUG-10)
        assertEquals("10 s 59 dk", ZamanHesabi.sure(659));
        assertEquals("30 dk", ZamanHesabi.sure(30));
        assertEquals("12 Eki 22:00", ZamanHesabi.an(an("2026-10-12T22:00")));
        assertEquals("12 Eki 22:00'de", ZamanHesabi.deEk(an("2026-10-12T22:00")));
        assertEquals("13 Eki 06:00'da", ZamanHesabi.deEk(an("2026-10-13T06:00")));
        assertEquals("13 Eki 09:30'da", ZamanHesabi.deEk(an("2026-10-13T09:30")));
        assertEquals("1 Kas 2026", ZamanHesabi.tarih(LocalDate.of(2026, 11, 1)));
    }
}
