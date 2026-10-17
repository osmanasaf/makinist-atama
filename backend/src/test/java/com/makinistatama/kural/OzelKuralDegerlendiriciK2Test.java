package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.gorevHat;
import static com.makinistatama.kural.OzelKuralYap.hat;
import static com.makinistatama.kural.OzelKuralYap.kural;
import static com.makinistatama.kural.OzelKuralYap.p;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** K2 (F6): seri, kayan pencere, izin günü, görevsiz aralık, nöbetler arası dinlenme, tren türü kapsamı. Assertler yalnız Ö- kodlarına bakar. */
class OzelKuralDegerlendiriciK2Test {

    private static List<Ihlal> ozel(Gorev aday, List<Gorev> diger, Parametreler p) {
        return KuralMotoru.degerlendir(baglam(aday, diger, p)).stream().filter(i -> OzelKural.ozelMi(i.kod())).toList();
    }

    private static List<String> kodlar(List<Ihlal> l) {
        return l.stream().map(Ihlal::kod).toList();
    }

    private static String g(int gun) {
        return String.format("2026-10-%02d", gun);
    }

    private static Gorev gunduz(long id, int gun, String bas, String bit) {
        return gorev(id, g(gun) + "T" + bas, g(gun) + "T" + bit);
    }

    /** Gece nöbeti: {@code gun} 22:00 – ertesi 05:00 (7 s, tamamı gece). */
    private static Gorev gece(long id, int gun) {
        return gorev(id, g(gun) + "T22:00", g(gun + 1) + "T05:00");
    }

    private static final Parametreler GECE_SERISI_3 = p(kural(1, "Gece üst üste 3", Olcu.ARDISIK_GECE_GUNU, Pencere.SERI, Karsilastirma.EN_FAZLA, "3", Kapsam.TUMU, null));

    // ---------------------------------------------------------------- seri (örnek 1)

    @Test
    void geceSerisi_5_6_7_varken_8_Ekim_seri4_ENGEL_9_Ekim_uygun_4_Ekim_iki_yonlu_ENGEL() {
        List<Gorev> mevcut = List.of(gece(1, 5), gece(2, 6), gece(3, 7));
        List<Ihlal> i = ozel(gece(9, 8), mevcut, GECE_SERISI_3);
        assertEquals(1, i.size());
        assertEquals(4.0, i.get(0).olculen(), 1e-9);
        assertEquals("gün", i.get(0).birim());
        assertEquals("Ardışık gece günü (üst üste 5 Eki 2026 – 8 Eki 2026): 4 gün > 3 gün (Kural: Gece üst üste 3)", i.get(0).metin());
        assertTrue(ozel(gece(9, 9), mevcut, GECE_SERISI_3).isEmpty()); // 8 Ekim boş: seri 1
        assertEquals(1, ozel(gece(9, 4), mevcut, GECE_SERISI_3).size()); // 4,5,6,7
        assertTrue(ozel(gece(9, 2), mevcut, GECE_SERISI_3).isEmpty()); // 2: seri 1
    }

    @Test
    void geceSerisi_gunduzGorevi_seriyiBolmezAmaAdaySayilmaz() {
        List<Gorev> mevcut = new ArrayList<>(List.of(gece(1, 5), gece(2, 6), gece(3, 7), gunduz(4, 8, "10:00", "14:00"))); // 8 Ekim gündüz görevi gece günü değil
        assertTrue(ozel(gece(9, 9), mevcut, GECE_SERISI_3).isEmpty()); // 9 Ekim yine seri 1
        assertTrue(ozel(gunduz(9, 8, "16:00", "18:00"), mevcut, GECE_SERISI_3).isEmpty()); // gündüz aday seriye katılmaz
    }

    @Test
    void calismaSerisi_gorevli_gunler_aralıksız() {
        Parametreler p = p(kural(1, "Çalışma 5", Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, Karsilastirma.EN_FAZLA, "5", Kapsam.TUMU, null));
        List<Gorev> mevcut = new ArrayList<>();
        for (int d = 1; d <= 5; d++) {
            mevcut.add(gunduz(d, d, "10:00", "12:00"));
        }
        assertEquals(1, ozel(gunduz(9, 6, "10:00", "12:00"), mevcut, p).size()); // 6 gün > 5
        assertTrue(ozel(gunduz(9, 7, "10:00", "12:00"), mevcut, p).isEmpty()); // 6 Ekim boş
    }

    // ---------------------------------------------------------------- izin günü (örnek 2)

    private static final Parametreler IZIN_8 = p(kural(1, "Ayda 8 gün izin", Olcu.IZIN_GUNU, Pencere.AY, Karsilastirma.EN_AZ, "8", Kapsam.TUMU, null));

    private static List<Gorev> calisilan(int gunSayisi) {
        List<Gorev> l = new ArrayList<>();
        for (int d = 1; d <= gunSayisi; d++) {
            l.add(gunduz(d, d, "10:00", "12:00"));
        }
        return l;
    }

    @Test
    void izinGunu_23_gun_calisan_24_gun_ENGEL_ikinci_gorev_ayni_gune_uygun() {
        List<Gorev> mevcut = calisilan(23);
        List<Ihlal> i = ozel(gunduz(99, 24, "10:00", "12:00"), mevcut, IZIN_8);
        assertEquals(1, i.size());
        assertEquals(7.0, i.get(0).olculen(), 1e-9);
        assertEquals(8.0, i.get(0).esik(), 1e-9);
        assertEquals("İzin günü sayısı (Ekim 2026): 7 gün < 8 gün (Kural: Ayda 8 gün izin)", i.get(0).metin());
        assertTrue(ozel(gunduz(99, 23, "14:00", "16:00"), mevcut, IZIN_8).isEmpty()); // aynı güne ikinci görev: 8 izin
        assertTrue(ozel(gunduz(99, 23, "10:00", "12:00"), calisilan(22), IZIN_8).isEmpty()); // 23. çalışma günü: izin 8
    }

    @Test
    void izinGunu_geceGorevi_iki_gunu_de_gorevli_sayar() {
        List<Gorev> mevcut = calisilan(22);
        assertEquals(1, ozel(gece(99, 23), mevcut, IZIN_8).size()); // 23 ve 24 Ekim görevli: 24 gün çalışılan => izin 7
        assertTrue(ozel(gece(99, 22), mevcut, IZIN_8).isEmpty()); // 22 zaten çalışılan, 23 yeni: 23 gün => izin 8
    }

    @Test
    void izinGunu_hafta_en_az_1_gun() {
        Parametreler p = p(kural(1, "Haftada 1 gün izin", Olcu.IZIN_GUNU, Pencere.HAFTA, Karsilastirma.EN_AZ, "1", Kapsam.TUMU, null));
        List<Gorev> mevcut = new ArrayList<>(); // 5 Ekim Pazartesi .. 11 Ekim Pazar; 5..10 çalışılan
        for (int d = 5; d <= 10; d++) {
            mevcut.add(gunduz(d, d, "10:00", "12:00"));
        }
        assertTrue(ozel(gunduz(99, 10, "14:00", "16:00"), mevcut, p).isEmpty()); // 11 Ekim boş
        List<Ihlal> i = ozel(gunduz(99, 11, "10:00", "12:00"), mevcut, p);
        assertEquals(1, i.size());
        assertEquals(0.0, i.get(0).olculen(), 1e-9);
    }

    // ---------------------------------------------------------------- kayan pencere (ekran taslağı)

    @Test
    void kayan7gun_toplamSure_50s_sinir_ve_ustu() {
        Parametreler p = p(kural(1, "Kayan 7 gün 50 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, Karsilastirma.EN_FAZLA, "50", Kapsam.TUMU, 7));
        List<Gorev> mevcut = new ArrayList<>();
        for (int d = 6; d <= 11; d++) {
            mevcut.add(gunduz(d, d, "08:00", "16:00")); // 6 x 8 s = 48 s
        }
        mevcut.add(gunduz(50, 4, "08:00", "20:00")); // 4 Ekim: 12 s; 12 Ekim'i kapsayan hiçbir 7 günlük pencerede değil
        assertTrue(ozel(gunduz(99, 12, "08:00", "10:00"), mevcut, p).isEmpty()); // 50 s eşit: uygun
        List<Ihlal> i = ozel(gunduz(99, 12, "08:00", "10:01"), mevcut, p);
        assertEquals(1, i.size());
        assertEquals("Toplam görev süresi (kayan 7 gün 6 Eki 2026 – 12 Eki 2026): 50 s 1 dk > 50 s (Kural: Kayan 7 gün 50 s)", i.get(0).metin());
    }

    @Test
    void kayan_gorevSayisi_ve_geceNobeti() {
        Parametreler p = p(kural(1, "Kayan 3 gün 2 görev", Olcu.GOREV_SAYISI, Pencere.KAYAN, Karsilastirma.EN_FAZLA, "2", Kapsam.TUMU, 3),
                kural(2, "Kayan 4 gün 2 gece", Olcu.GECE_NOBETI_SAYISI, Pencere.KAYAN, Karsilastirma.EN_FAZLA, "2", Kapsam.TUMU, 4));
        List<Gorev> mevcut = List.of(gunduz(1, 10, "10:00", "11:00"), gunduz(2, 12, "10:00", "11:00"));
        assertEquals(List.of("Ö-001"), kodlar(ozel(gunduz(9, 11, "10:00", "11:00"), mevcut, p))); // 10,11,12 => 3 > 2
        assertTrue(ozel(gunduz(9, 13, "10:00", "11:00"), mevcut, p).isEmpty()); // en çok 2
        List<Gorev> geceler = List.of(gece(1, 10), gece(2, 12));
        assertEquals(List.of("Ö-001", "Ö-002"), kodlar(ozel(gece(9, 11), geceler, p))); // 10,11,12: 3 görev (3 günlük pencere) ve 3 gece nöbeti (4 günlük pencere)
        assertTrue(ozel(gece(9, 15), geceler, p).isEmpty()); // 12 ve 15 aynı pencerede ama yalnız 2
    }

    // ---------------------------------------------------------------- görevsiz aralık, nöbetler arası dinlenme

    @Test
    void gorevsizAralik_gun_en_az() {
        Parametreler p8 = p(kural(1, "Günde 8 s boşluk", Olcu.GOREVSIZ_ARALIK, Pencere.GUN, Karsilastirma.EN_AZ, "8", Kapsam.TUMU, null));
        Parametreler p9 = p(kural(1, "Günde 9 s boşluk", Olcu.GOREVSIZ_ARALIK, Pencere.GUN, Karsilastirma.EN_AZ, "9", Kapsam.TUMU, null));
        List<Gorev> mevcut = List.of(gunduz(1, 12, "08:00", "12:00"));
        Gorev aday = gunduz(2, 12, "18:00", "20:00"); // boşluklar: 0-8 (8 s), 12-18 (6 s), 20-24 (4 s)
        assertTrue(ozel(aday, mevcut, p8).isEmpty());
        List<Ihlal> i = ozel(aday, mevcut, p9);
        assertEquals(1, i.size());
        assertEquals("En uzun görevsiz aralık (12 Eki 2026): 8 s < 9 s (Kural: Günde 9 s boşluk)", i.get(0).metin());
    }

    private static final Parametreler DINLENME_12 = p(kural(1, "Nöbetler arası 12 s", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, Karsilastirma.EN_AZ, "12", Kapsam.TUMU, null));

    @Test
    void nobetlerArasiDinlenme_11s59_ENGEL_12s_uygun_oncekiVeSonrakiNobetle() {
        List<Gorev> mevcut = List.of(gunduz(1, 12, "08:00", "10:00"));
        List<Ihlal> i = ozel(gunduz(9, 12, "21:59", "23:00"), mevcut, DINLENME_12);
        assertEquals(1, i.size());
        assertTrue(i.get(0).metin().contains("11 s 59 dk < 12 s"), i.get(0).metin());
        assertTrue(ozel(gunduz(9, 12, "22:00", "23:00"), mevcut, DINLENME_12).isEmpty());
        // aday önce: aday bitişi 20:01 (önceki gün), sonraki nöbet 08:00 => 11 s 59 dk
        assertEquals(1, ozel(gorev(9, g(11) + "T18:00", g(11) + "T20:01"), mevcut, DINLENME_12).size());
        assertTrue(ozel(gorev(9, g(11) + "T18:00", g(11) + "T20:00"), mevcut, DINLENME_12).isEmpty());
    }

    @Test
    void nobetlerArasiDinlenme_hatKapsami_boslugun_iki_ucundan_biri_kapsamdaysa_denetlenir() {
        Parametreler p = p(kural(1, "Hat 10: 12 s", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, Karsilastirma.EN_AZ, "12", hat(TestVerisi.HAT), null));
        List<Gorev> mevcutKapsamda = List.of(gunduz(1, 12, "08:00", "10:00")); // hat 10
        Gorev disAday = gorevHat(9, 99, g(12) + "T16:00", g(12) + "T17:00"); // 6 s sonra, hat 99: komşu kapsamda => denetlenir
        assertEquals(1, ozel(disAday, mevcutKapsamda, p).size());
        List<Gorev> mevcutDis = List.of(gorevHat(1, 99, g(12) + "T08:00", g(12) + "T10:00"));
        assertTrue(ozel(disAday, mevcutDis, p).isEmpty()); // iki uç da kapsam dışı
        assertEquals(1, ozel(gunduz(9, 12, "16:00", "17:00"), mevcutDis, p).size()); // aday kapsamda
    }

    // ---------------------------------------------------------------- tren türü kapsamı

    private static Gorev trenli(String tur) {
        var b = TestVerisi.an(g(12) + "T08:00");
        var e = TestVerisi.an(g(12) + "T16:00");
        return new Gorev(1, TestVerisi.ANKARA, TestVerisi.ANKARA, TestVerisi.HAT, TestVerisi.ARAC, b, e, b, e, 100, tur);
    }

    @Test
    void trenTuru_bos_sefer_hicbir_tur_kapsamina_girmez() {
        Kapsam yuk = new Kapsam(null, null, null, null, Set.of("YUK"), null);
        Parametreler p = p(kural(1, "Yük 5 s", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "5", yuk, Siddet.ENGEL));
        assertEquals(1, ozel(trenli("YUK"), List.of(), p).size());
        assertTrue(ozel(trenli("YOLCU"), List.of(), p).isEmpty());
        assertTrue(ozel(trenli(null), List.of(), p).isEmpty()); // belirsiz: kapsama girmez [V7]
    }
}
