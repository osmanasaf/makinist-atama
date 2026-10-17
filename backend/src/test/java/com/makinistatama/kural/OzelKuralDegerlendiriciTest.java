package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.gorevHat;
import static com.makinistatama.kural.OzelKuralYap.hat;
import static com.makinistatama.kural.OzelKuralYap.kural;
import static com.makinistatama.kural.OzelKuralYap.p;
import static com.makinistatama.kural.OzelKuralYap.vardiya;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kod;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T1.4: özel kural değerlendirici (K1 ölçüleri). Yerleşik kurallar varsayılan parametrelerle açıktır; assert'ler yalnız Ö- kodlarına bakar. */
class OzelKuralDegerlendiriciTest {

    private static List<Ihlal> ozel(Gorev aday, List<Gorev> diger, Parametreler p) {
        return KuralMotoru.degerlendir(baglam(aday, diger, p)).stream().filter(i -> OzelKural.ozelMi(i.kod())).toList();
    }

    // ---------------------------------------------------------------- tek sefer (FR-02: örnek 3)

    @Test
    void tekSefer_8s00_uygun_8s01_ENGEL_metin_kural_adini_tasir() {
        Parametreler p = p(kural(1, "Dizel hatlar 8 s", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "8", hat(TestVerisi.HAT), Siddet.ENGEL));
        assertTrue(ozel(gorev(1, "2026-10-12T08:00", "2026-10-12T16:00"), List.of(), p).isEmpty());
        List<Ihlal> i = ozel(gorev(1, "2026-10-12T08:00", "2026-10-12T16:01"), List.of(), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
        assertEquals("s", i.get(0).birim());
        assertEquals(8.0, i.get(0).esik(), 1e-9);
        assertEquals(481 / 60.0, i.get(0).olculen(), 1e-9);
        assertEquals("Tek sefer süresi: 8 s 1 dk > 8 s (Kural: Dizel hatlar 8 s)", i.get(0).metin());
    }

    @Test
    void tekSefer_kapsam_disi_hatta_uygulanmaz() {
        Parametreler p = p(kural(1, "Hat A", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "8", hat(TestVerisi.HAT), Siddet.ENGEL));
        assertTrue(ozel(gorevHat(1, 99, "2026-10-12T08:00", "2026-10-12T17:00"), List.of(), p).isEmpty());
        assertEquals(1, ozel(gorevHat(1, TestVerisi.HAT, "2026-10-12T08:00", "2026-10-12T17:00"), List.of(), p).size());
    }

    @Test
    void kapali_hic_ihlal_uretmez_ve_gecerlilik_sefer_gunune_gore() {
        OzelKural acik = kural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1");
        Gorev aday = gorev(1, "2026-10-12T08:00", "2026-10-12T10:00");
        assertEquals(1, ozel(aday, List.of(), p(acik)).size());
        assertTrue(ozel(aday, List.of(), p(kural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1", Kapsam.TUMU, Siddet.KAPALI))).isEmpty());
        OzelKural gelecek = new OzelKural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, Karsilastirma.EN_FAZLA, BigDecimal.ONE, Kapsam.TUMU, Siddet.ENGEL,
                LocalDate.of(2026, 11, 1), null, "g", "k", 0);
        assertTrue(ozel(aday, List.of(), p(gelecek)).isEmpty()); // 1 Kas başlangıç: 12 Eki'de yok
        OzelKural bitmis = new OzelKural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, Karsilastirma.EN_FAZLA, BigDecimal.ONE, Kapsam.TUMU, Siddet.ENGEL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 11), "g", "k", 0);
        assertTrue(ozel(aday, List.of(), p(bitmis)).isEmpty()); // 11 Eki bitiş
        OzelKural bitisGunu = new OzelKural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, Karsilastirma.EN_FAZLA, BigDecimal.ONE, Kapsam.TUMU, Siddet.ENGEL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 12), "g", "k", 0);
        assertEquals(1, ozel(aday, List.of(), p(bitisGunu)).size()); // iki uç dahil
    }

    // ---------------------------------------------------------------- toplam süre: gün (FR-03)

    @Test
    void gunluk_4_4_3_son_gorev_ENGEL_ve_K13_ile_ayni_olculen() {
        Gorev g1 = gorev(1, "2026-10-12T06:00", "2026-10-12T10:00");
        Gorev g2 = gorev(2, "2026-10-12T11:00", "2026-10-12T15:00");
        Gorev g3 = gorev(3, "2026-10-12T16:00", "2026-10-12T19:00");
        Parametreler p = p(kural(7, "Günlük 8 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8"));
        // yalniz oncekilerle (kirmizi hesabin gorusu): ilk iki yesil
        assertTrue(ozel(g1, List.of(), p).isEmpty());
        assertTrue(ozel(g2, List.of(g1), p).isEmpty());
        List<Ihlal> i = ozel(g3, List.of(g1, g2), p);
        assertEquals(List.of("Ö-007"), kodlar(i));
        assertEquals("Toplam görev süresi (12 Eki 2026): 11 s > 8 s (Kural: Günlük 8 s)", i.get(0).metin());
        assertEquals(11.0, i.get(0).olculen(), 1e-9);
    }

    @Test
    void gunluk_olculen_K13_ile_birebir_ayni_tek_kaynak() {
        Gorev g1 = gorev(1, "2026-10-12T04:00", "2026-10-12T09:00");
        Gorev g2 = gorev(2, "2026-10-12T10:00", "2026-10-12T15:00");
        Gorev aday = gorev(3, "2026-10-12T16:00", "2026-10-12T19:00");
        Parametreler p = p(kural(1, "G", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8"));
        List<Ihlal> hepsi = KuralMotoru.degerlendir(baglam(aday, List.of(g1, g2), p));
        Ihlal k13 = kod(hepsi, "K-13").get(0);
        Ihlal o = kod(hepsi, "Ö-001").get(0);
        assertEquals(k13.olculen(), o.olculen(), 1e-9);
        assertEquals(13.0, o.olculen(), 1e-9);
    }

    @Test
    void gece_yarisini_asan_gorev_iki_gunun_icinde_en_kotu_gun_raporlanir() {
        // 22:00-06:00: 12 Eki 2 s, 13 Eki 6 s; 13 Eki'ne 3 s daha => 13 Eki 9 s > 8 s
        Gorev gece = gorev(1, "2026-10-12T22:00", "2026-10-13T06:00");
        Gorev aday = gorev(2, "2026-10-13T12:00", "2026-10-13T15:00");
        Parametreler p = p(kural(1, "G8", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8"));
        List<Ihlal> i = ozel(aday, List.of(gece), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertTrue(i.get(0).metin().contains("13 Eki 2026"));
        assertEquals(9.0, i.get(0).olculen(), 1e-9);
    }

    // ---------------------------------------------------------------- çoklu kural (FR-05)

    @Test
    void coklu_genel_8s_ENGEL_ve_hatA_10s_UYARI_sayilari() {
        OzelKural genel = kural(1, "Genel 8", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8");
        OzelKural hatA = kural(2, "Hat A 10", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "10", hat(TestVerisi.HAT), Siddet.UYARI);
        Parametreler p = p(genel, hatA);
        Gorev ilk = gorevHat(1, TestVerisi.HAT, "2026-10-12T06:00", "2026-10-12T10:00");
        // 9 s: yalniz genel (9 > 8); hat A 10 s'in altinda
        List<Ihlal> dokuz = ozel(gorevHat(2, TestVerisi.HAT, "2026-10-12T11:00", "2026-10-12T16:00"), List.of(ilk), p);
        assertEquals(List.of("Ö-001"), kodlar(dokuz));
        assertEquals(Siddet.ENGEL, dokuz.get(0).siddet());
        // 10,5 s: ikisi birlikte, kod sirasi Ö-001 < Ö-002; sonuc ENGEL
        List<Ihlal> onbucuk = ozel(gorevHat(2, TestVerisi.HAT, "2026-10-12T11:00", "2026-10-12T17:30"), List.of(ilk), p);
        assertEquals(List.of("Ö-001", "Ö-002"), kodlar(onbucuk));
        assertEquals(Siddet.UYARI, onbucuk.get(1).siddet());
        assertTrue(KuralMotoru.engelVarMi(onbucuk));
    }

    @Test
    void yerlesik_kodlar_ozel_kodlardan_once_siralanir() {
        Gorev onceki = gorev(1, "2026-10-12T14:00", "2026-10-12T22:00");
        Gorev aday = gorev(2, "2026-10-13T06:00", "2026-10-13T10:00"); // K-11 dinlenme 8 s < 11 s
        List<Ihlal> i = KuralMotoru.degerlendir(baglam(aday, List.of(onceki), p(kural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1"))));
        assertEquals(List.of("K-11", "Ö-001"), kodlar(i));
    }

    // ---------------------------------------------------------------- ay penceresi (Ekim/Kasım sınırı)

    @Test
    void ay_penceresi_ekim_kasim_siniri_ayri_hesaplanir() {
        List<Gorev> ekim = new ArrayList<>();
        for (int g = 20; g <= 31; g += 2) { // 20, 22, ... 30 Eki: 6 gorev x 4 s = 24 s
            ekim.add(gorev(g, "2026-10-" + g + "T06:00", "2026-10-" + g + "T10:00"));
        }
        Parametreler p = p(kural(1, "Aylık 25", Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "25"));
        Gorev kasim = gorev(100, "2026-11-01T06:00", "2026-11-01T10:00");
        assertTrue(ozel(kasim, ekim, p).isEmpty()); // Kasim: 4 s
        Gorev eki31 = gorev(101, "2026-10-31T06:00", "2026-10-31T10:00");
        List<Ihlal> i = ozel(eki31, ekim, p); // Ekim: 24 + 4 = 28 > 25
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals("Toplam görev süresi (Ekim 2026): 28 s > 25 s (Kural: Aylık 25)", i.get(0).metin());
    }

    // ---------------------------------------------------------------- hafta, yıl, nöbet

    @Test
    void hafta_penceresi_pazar_pazartesi_ayrilir() {
        // Pzt 12 Eki haftasi: 11 Eki Pazar (onceki hafta) 8 s, Pazartesi 4 s
        Gorev pazar = gorev(1, "2026-10-11T06:00", "2026-10-11T14:00");
        Gorev aday = gorev(2, "2026-10-12T06:00", "2026-10-12T10:00");
        Parametreler p = p(kural(1, "H6", Olcu.TOPLAM_GOREV_SURESI, Pencere.HAFTA, "6"));
        assertTrue(ozel(aday, List.of(), p).isEmpty()); // 4 s <= 6: yok
        // yukaridaki 4 s uygun; ayni haftaya 4 s daha eklenince 8 > 6
        Gorev bir = gorev(3, "2026-10-13T06:00", "2026-10-13T10:00");
        List<Ihlal> i = ozel(bir, List.of(pazar, aday), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals(8.0, i.get(0).olculen(), 1e-9); // Pazar (onceki hafta) sayilmaz
    }

    @Test
    void yil_penceresi_butun_yilin_toplamini_sayar() {
        List<Gorev> diger = new ArrayList<>();
        for (int ay = 1; ay <= 9; ay++) {
            diger.add(gorev(ay, "2026-0" + ay + "-10T06:00", "2026-0" + ay + "-10T16:00")); // 9 x 10 s = 90 s
        }
        Parametreler p = p(kural(1, "Yıl 95", Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "95"));
        Gorev aday = gorev(50, "2026-10-12T06:00", "2026-10-12T16:00"); // 100 s
        List<Ihlal> i = ozel(aday, diger, p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals("Toplam görev süresi (2026 yılı): 100 s > 95 s (Kural: Yıl 95)", i.get(0).metin());
        assertTrue(ozel(gorev(51, "2027-01-04T06:00", "2027-01-04T10:00"), diger, p).isEmpty()); // baska yil
    }

    @Test
    void nobet_penceresi_adayin_nobetinin_toplami() {
        Gorev g1 = gorev(1, "2026-10-12T06:00", "2026-10-12T10:00");
        Gorev aday = gorev(2, "2026-10-12T11:00", "2026-10-12T15:00"); // 1 s ara: ayni nobet, 8 s
        Parametreler p = p(kural(1, "Nöbet 6", Olcu.TOPLAM_GOREV_SURESI, Pencere.NOBET, "6"));
        List<Ihlal> i = ozel(aday, List.of(g1), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals(8.0, i.get(0).olculen(), 1e-9);
        assertTrue(i.get(0).metin().startsWith("Toplam görev süresi (nöbet 12 Eki 06:00): 8 s > 6 s"));
        // ara 5 s: ayri nobetler, 4 s <= 6
        assertTrue(ozel(gorev(3, "2026-10-12T15:00", "2026-10-12T19:00"), List.of(g1), p).isEmpty());
    }

    // ---------------------------------------------------------------- gece süresi, görev sayısı, gece nöbeti sayısı

    @Test
    void gece_suresi_gun_penceresi_gece_yarisini_boler() {
        // gece 20:00-06:00; 22:00-06:00 gorevi: 12 Eki 2 s, 13 Eki 6 s
        Gorev aday = gorev(1, "2026-10-12T22:00", "2026-10-13T06:00");
        Parametreler p = p(kural(1, "Gece 5", Olcu.GECE_SURESI, Pencere.GUN, "5"));
        List<Ihlal> i = ozel(aday, List.of(), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals("Gece süresi (13 Eki 2026): 6 s > 5 s (Kural: Gece 5)", i.get(0).metin());
        // tum gece = 8 s (20:00-06:00 icinde 22-06)
        assertEquals(480, ZamanHesabi.geceDakika(aday.baslangic(), aday.bitis(), java.time.LocalTime.of(20, 0), java.time.LocalTime.of(6, 0)));
        assertTrue(ozel(aday, List.of(), p(kural(1, "Gece 6", Olcu.GECE_SURESI, Pencere.GUN, "6"))).isEmpty());
    }

    @Test
    void gece_suresi_nobet_penceresi() {
        Gorev aday = gorev(1, "2026-10-12T22:00", "2026-10-13T05:00");
        List<Ihlal> i = ozel(aday, List.of(), p(kural(1, "N", Olcu.GECE_SURESI, Pencere.NOBET, "6")));
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals(7.0, i.get(0).olculen(), 1e-9);
    }

    @Test
    void gorev_sayisi_gun_penceresi_kalkis_gunune_yazilir() {
        Gorev a = gorev(1, "2026-10-12T06:00", "2026-10-12T08:00");
        Gorev b = gorev(2, "2026-10-12T09:00", "2026-10-12T11:00");
        Gorev aday = gorev(3, "2026-10-12T12:00", "2026-10-12T14:00");
        Parametreler p = p(kural(1, "Gün 2 görev", Olcu.GOREV_SAYISI, Pencere.GUN, "2"));
        assertTrue(ozel(b, List.of(a), p).isEmpty());
        List<Ihlal> i = ozel(aday, List.of(a, b), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals("adet", i.get(0).birim());
        assertEquals(3.0, i.get(0).olculen(), 1e-9);
        assertEquals("Görev sayısı (12 Eki 2026): 3 > 2 (Kural: Gün 2 görev)", i.get(0).metin());
        // ertesi gunun gorevi sayilmaz
        assertTrue(ozel(gorev(4, "2026-10-13T06:00", "2026-10-13T08:00"), List.of(a, b), p).isEmpty());
    }

    @Test
    void gece_nobeti_sayisi_hafta_penceresi() {
        Gorev pzt = gorev(1, "2026-10-12T22:00", "2026-10-13T04:00");
        Gorev sal = gorev(2, "2026-10-13T22:00", "2026-10-14T04:00");
        Gorev aday = gorev(3, "2026-10-14T22:00", "2026-10-15T04:00");
        Gorev gunduz = gorev(4, "2026-10-14T09:00", "2026-10-14T12:00");
        Parametreler p = p(kural(1, "Haftada 2 gece", Olcu.GECE_NOBETI_SAYISI, Pencere.HAFTA, "2"));
        assertTrue(ozel(sal, List.of(pzt), p).isEmpty());
        List<Ihlal> i = ozel(aday, List.of(pzt, sal, gunduz), p);
        assertEquals(List.of("Ö-001"), kodlar(i));
        assertEquals(3.0, i.get(0).olculen(), 1e-9); // gunduz nobeti sayilmaz
        assertTrue(ozel(gunduz, List.of(pzt, sal), p).isEmpty()); // gunduz aday sayiyi artirmaz
    }

    // ---------------------------------------------------------------- kapsam

    @Test
    void kapsam_hat_yalniz_kapsama_giren_gorevler_sayilir() {
        Gorev baska = gorevHat(1, 99, "2026-10-12T06:00", "2026-10-12T14:00"); // 8 s baska hat
        Gorev aday = gorevHat(2, TestVerisi.HAT, "2026-10-12T15:00", "2026-10-12T19:00");
        Parametreler p = p(kural(1, "Hat A gün 6", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "6", hat(TestVerisi.HAT), Siddet.ENGEL));
        assertTrue(ozel(aday, List.of(baska), p).isEmpty()); // hat A: 4 s
        Gorev hatA = gorevHat(3, TestVerisi.HAT, "2026-10-12T08:00", "2026-10-12T12:00");
        assertEquals(List.of("Ö-001"), kodlar(ozel(aday, List.of(baska, hatA), p))); // hat A: 8 s > 6
    }

    @Test
    void kapsam_arac_tipi_ve_gar_ve_vardiya() {
        Gorev aday = gorev(1, "2026-10-12T08:00", "2026-10-12T18:00");
        OzelKural arac = kural(1, "a", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", new Kapsam(null, java.util.Set.of(TestVerisi.ARAC), null, null, null, null), Siddet.ENGEL);
        OzelKural baskaArac = kural(2, "b", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", new Kapsam(null, java.util.Set.of(777L), null, null, null, null), Siddet.ENGEL);
        OzelKural kalkis = kural(3, "c", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", new Kapsam(null, null, java.util.Set.of(TestVerisi.ESKISEHIR), null, null, null), Siddet.ENGEL);
        OzelKural varis = kural(4, "d", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", new Kapsam(null, null, null, java.util.Set.of(TestVerisi.ANKARA), null, null), Siddet.ENGEL);
        OzelKural gece = kural(5, "e", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", vardiya(Vardiya.GECE), Siddet.ENGEL);
        OzelKural gunduz = kural(6, "f", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", vardiya(Vardiya.GUNDUZ), Siddet.ENGEL);
        assertEquals(List.of("Ö-001", "Ö-004", "Ö-006"), kodlar(ozel(aday, List.of(), p(arac, baskaArac, kalkis, varis, gece, gunduz))));
        Gorev geceAday = gorev(2, "2026-10-12T22:00", "2026-10-13T05:00");
        assertEquals(List.of("Ö-005"), kodlar(ozel(geceAday, List.of(), p(gece, gunduz))));
    }

    @Test
    void vardiya_gece_kapsami_yalniz_gece_nobetinin_gorevlerini_sayar() {
        // gunduz gorevi 06:00-12:00 (6 s) + gece nobeti 22:00-04:00: GECE kapsamli gunluk toplam yalniz gece nobetini sayar
        Gorev gunduz = gorev(1, "2026-10-13T09:00", "2026-10-13T15:00");
        Gorev gece = gorev(2, "2026-10-12T22:00", "2026-10-13T04:00");
        Parametreler p = p(kural(1, "Gece vardiyası gün 5", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "5", vardiya(Vardiya.GECE), Siddet.ENGEL));
        List<Ihlal> i = ozel(gece, List.of(gunduz), p);
        assertEquals(List.of(), kodlar(i)); // 13 Eki: 4 s (gece nobeti) <= 5, 12 Eki: 2 s
        Gorev gece2 = gorev(3, "2026-10-13T20:30", "2026-10-13T23:00");
        List<Ihlal> j = ozel(gece2, List.of(gunduz, gece), p);
        // 13 Eki: gece nobeti 4 s + 2,5 s = 6,5 s > 5; gunduz (6 s) sayilmaz
        assertEquals(List.of("Ö-001"), kodlar(j));
        assertEquals(6.5, j.get(0).olculen(), 1e-9);
    }

    // ---------------------------------------------------------------- bağlam / diğer

    @Test
    void ozel_kural_yokken_ve_KAPALI_iken_kontrol_edilen_kodlar_ayni() {
        Parametreler bos = TestVerisi.param();
        Parametreler kapali = p(kural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1", Kapsam.TUMU, Siddet.KAPALI));
        assertEquals(KuralMotoru.kontrolEdilenKodlar(bos), KuralMotoru.kontrolEdilenKodlar(kapali));
        Parametreler acik = p(kural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1"), kural(12, "k2", Olcu.GOREV_SAYISI, Pencere.GUN, "1", Kapsam.TUMU, Siddet.UYARI));
        List<String> k = KuralMotoru.kontrolEdilenKodlar(acik);
        assertTrue(k.contains("Ö-001") && k.contains("Ö-012"));
        assertEquals(KuralMotoru.kontrolEdilenKodlar(bos).size() + 2, k.size());
    }

    @Test
    void degerlendirYazma_ozel_kural_iki_kosuda_da_calisir() {
        Gorev ilk = gorev(1, "2026-10-12T06:00", "2026-10-12T10:00");
        Gorev aday = gorev(2, "2026-10-12T11:00", "2026-10-12T15:00");
        Parametreler p = p(kural(1, "G7", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "7"));
        List<Ihlal> i = KuralMotoru.degerlendirYazma(baglam(aday, List.of(ilk), p));
        assertEquals(1, kod(i, "Ö-001").size()); // tekrarlanmaz
        assertFalse(i.isEmpty());
        // sonradan eklenen (adaydan sonra baslayan) gorev: canli kirmizi, aday tum komsulari gorur
        Gorev sonra = gorev(3, "2026-10-12T16:00", "2026-10-12T20:00");
        assertEquals(1, kod(KuralMotoru.degerlendirYazma(baglam(aday, List.of(ilk, sonra), p)), "Ö-001").size());
    }

    @Test
    void parametreler_ozel_kural_arama_ve_degistirme() {
        OzelKural k = kural(3, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "8", Kapsam.TUMU, Siddet.UYARI);
        Parametreler p = p(k);
        assertEquals(Siddet.UYARI, p.siddet("Ö-003"));
        assertEquals("k", p.ozelKural("Ö-003").ad());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> p.siddet("Ö-004"));
        OzelKural yeni = kural(3, "k2", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "7");
        Parametreler q = p.ozelDegistir(yeni);
        assertEquals(1, q.ozelKurallar().size());
        assertEquals("k2", q.ozelKural("Ö-003").ad());
        assertEquals(2, q.ozelDegistir(kural(0, "taslak", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "7")).ozelKurallar().size());
        assertEquals("Ö-000", OzelKural.kod(0));
        assertEquals(1, p.ozelKurallar().size()); // orijinal degismez
        // pencere ihtiyaci
        assertEquals(0, p.ozelPencereGun());
        assertEquals(31, p(kural(1, "a", Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "100")).ozelPencereGun());
        assertEquals(0, p(kural(1, "a", Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "100", Kapsam.TUMU, Siddet.KAPALI)).ozelPencereGun());
        assertTrue(p(kural(1, "a", Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "100")).ozelYillik());
        assertFalse(p.ozelYillik());
    }

    @Test
    void siddet_UYARI_engel_degil() {
        Parametreler p = p(Map.of(), kural(1, "u", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1", Kapsam.TUMU, Siddet.UYARI));
        List<Ihlal> i = KuralMotoru.degerlendir(baglam(gorev(1, "2026-10-12T08:00", "2026-10-12T10:00"), List.of(), p));
        assertEquals(Siddet.UYARI, kod(i, "Ö-001").get(0).siddet());
        assertFalse(KuralMotoru.engelVarMi(i));
    }
}
