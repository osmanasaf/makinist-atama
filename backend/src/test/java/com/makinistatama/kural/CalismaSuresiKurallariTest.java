package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.degerlendir;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kod;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** K-13..K-19, K-21 (K-16/K-17/K-18 GeceKurallari'nda); hepsi KuralMotoru uzerinden, hafta 12-18 Eki 2026. */
class CalismaSuresiKurallariTest {

    /** K-14 testleri kuralın KENDİSİNİ sınar: OK-24 ile varsayılan KAPALI olduğundan eski varsayılan (ENGEL) açıkça kurulur. */
    private static final Map<String, Siddet> K14_ENGEL = Map.of("K-14", Siddet.ENGEL);

    // ---------- K-13 gunluk ----------

    @Test
    void K13_4s_ve_8s_12s_engel() {
        List<Ihlal> i = degerlendir(gorev(2, "2026-10-12T12:00", "2026-10-12T20:00"),
                List.of(gorev(1, "2026-10-12T06:00", "2026-10-12T10:00")));
        assertEquals(List.of("K-13"), kodlar(i));
        assertEquals(12.0, i.get(0).olculen());
        assertEquals(11.0, i.get(0).esik());
        assertEquals("Günlük çalışma 12 Eki 2026: 12 s > 11 s", i.get(0).metin());
    }

    @Test
    void K13_3s_ve_8s_11s_uygun() {
        assertTrue(degerlendir(gorev(2, "2026-10-12T12:00", "2026-10-12T20:00"),
                List.of(gorev(1, "2026-10-12T06:00", "2026-10-12T09:00"))).isEmpty());
    }

    @Test
    void K13_gece_yarisini_asan_gorev_gun_sinirinda_bolunur() {
        // 22:00-06:00: 12 Eki'ne 2 s, 13 Eki'ne 6 s; gunluk.max 5 s -> yalniz 13 Eki ihlal
        List<Ihlal> i = kod(degerlendir(gorev(1, "2026-10-12T22:00", "2026-10-13T06:00"), List.of(),
                param("gunluk.max_saat", "5")), "K-13");
        assertEquals(1, i.size());
        assertEquals(6.0, i.get(0).olculen());
        assertTrue(i.get(0).metin().contains("13 Eki"));
        // 11 s: 3 s + 8 s ayni gunde baska gunun toplamini etkilemez; 31 Eki -> 1 Kas
        assertEquals(1, kod(degerlendir(gorev(1, "2026-10-31T22:00", "2026-11-01T06:00"), List.of(),
                param("gunluk.max_saat", "5")), "K-13").size());
    }

    @Test
    void K13_hazirlik_alikoyma_dahil_4_75s_siniri() {
        Parametreler p = param("gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "15", "gunluk.max_saat", "4.75");
        Gorev g = Gorev.olustur(1, 1, 1, 10, 20, an("2026-10-12T08:00"), an("2026-10-12T12:00"), 100, p);
        assertTrue(kod(KuralMotoru.degerlendir(baglam(g, List.of(), p)), "K-13").isEmpty());
        Parametreler dar = param("gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "15", "gunluk.max_saat", "4.7");
        assertEquals(1, kod(KuralMotoru.degerlendir(baglam(g, List.of(), dar)), "K-13").size());
    }

    @Test
    void K13_bekleme_sayilir_iken_nobet_araligi_toplanir() {
        // 08-12 + 14-18: bekleme sayilmazsa 8 s (uygun), sayilirsa 10 s; gunluk.max 9
        Gorev aday = gorev(2, "2026-10-12T14:00", "2026-10-12T18:00");
        List<Gorev> diger = List.of(gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"));
        assertTrue(kod(degerlendir(aday, diger, param("gunluk.max_saat", "9")), "K-13").isEmpty());
        assertEquals(1, kod(degerlendir(aday, diger, param("gunluk.max_saat", "9", "nobet.bekleme_sayilir", "1")),
                "K-13").size());
    }

    // ---------- K-14 haftalik, fazla mesai ----------

    private static List<Gorev> pztPerCum10s() {
        List<Gorev> g = new ArrayList<>();
        for (int d = 12; d <= 15; d++) { // Pzt-Per 06:00-16:00 = 4 x 10 s = 40 s
            g.add(gorev(d, "2026-10-" + d + "T06:00", "2026-10-" + d + "T16:00"));
        }
        return g;
    }

    @Test
    void K14_40s_arti_5s_45_uygun_6s_46_engel() {
        Parametreler p = param(K14_ENGEL);
        assertTrue(degerlendir(gorev(20, "2026-10-16T08:00", "2026-10-16T13:00"), pztPerCum10s(), p).isEmpty());
        List<Ihlal> i = degerlendir(gorev(20, "2026-10-16T08:00", "2026-10-16T14:00"), pztPerCum10s(), p);
        assertEquals(List.of("K-14"), kodlar(i));
        assertEquals(46.0, i.get(0).olculen());
        assertEquals(45.0, i.get(0).esik());
    }

    @Test
    void K14_max_50_iken_46s_uygun_ve_1s_fazla_mesai() {
        Parametreler p = param(K14_ENGEL, "haftalik.max_saat", "50");
        assertTrue(degerlendir(gorev(20, "2026-10-16T08:00", "2026-10-16T14:00"), pztPerCum10s(), p).isEmpty());
        assertEquals(60, CalismaSuresiKurallari.fazlaMesaiDakika(46 * 60, p));
        assertEquals(0, CalismaSuresiKurallari.fazlaMesaiDakika(45 * 60, p));
        assertEquals(0, CalismaSuresiKurallari.fazlaMesaiDakika(10 * 60, p));
    }

    @Test
    void K14_hafta_siniri_pazar_gecesi_iki_haftaya_bolunur() {
        // onceki haftada 40 s; Pazar 22:00-Pzt 06:00 gorevi onceki haftaya yalniz 2 s yazar
        List<Gorev> diger = new ArrayList<>();
        for (int d = 5; d <= 8; d++) {
            diger.add(gorev(d, "2026-10-0" + d + "T06:00", "2026-10-0" + d + "T16:00")); // hafta 41: 40 s
        }
        assertTrue(kod(degerlendir(gorev(30, "2026-10-11T22:00", "2026-10-12T06:00"), diger,
                param(K14_ENGEL, "haftalik.max_saat", "50")), "K-14").isEmpty());
        assertEquals(1, kod(degerlendir(gorev(30, "2026-10-11T22:00", "2026-10-12T06:00"), diger,
                param(K14_ENGEL, "haftalik.max_saat", "41")), "K-14").size()); // 42 s > 41 (yalniz hafta 41)
    }

    // ---------- K-15 yillik fazla mesai ----------

    /** 269 s fazla mesai: 6 hafta x 39 s (84 s/hafta) + 1 hafta 35 s (80 s). */
    private static List<Gorev> yirmiDokuzYuzAltmisDokuz() {
        List<Gorev> g = new ArrayList<>();
        long id = 100;
        LocalDate hafta = LocalDate.of(2026, 8, 24);
        for (int h = 0; h < 7; h++) {
            for (int d = 0; d < 7; d++) {
                LocalDate gun = hafta.plusDays(d);
                boolean kisa = h == 0 && d == 6; // ilk hafta Pazar 8 s -> 80 s
                g.add(gorev(id++, gun + "T06:00", gun + (kisa ? "T14:00" : "T18:00")));
            }
            hafta = hafta.plusWeeks(1);
        }
        // hafta 42 (12-18 Eki): Pzt-Cum 9 s = 45 s, fazla mesai 0
        for (int d = 12; d <= 16; d++) {
            g.add(gorev(id++, "2026-10-" + d + "T06:00", "2026-10-" + d + "T15:00"));
        }
        return g;
    }

    @Test
    void K15_yil_269_s_fazla_mesai_arti_2s_271_engel_1s_270_uygun() {
        Parametreler p = param("haftalik.max_saat", "168");
        List<Ihlal> i = kod(degerlendir(gorev(900, "2026-10-17T10:00", "2026-10-17T12:00"),
                yirmiDokuzYuzAltmisDokuz(), p), "K-15");
        assertEquals(1, i.size());
        assertEquals(271.0, i.get(0).olculen());
        assertEquals(270.0, i.get(0).esik());
        assertTrue(kod(degerlendir(gorev(900, "2026-10-17T10:00", "2026-10-17T11:00"),
                yirmiDokuzYuzAltmisDokuz(), p), "K-15").isEmpty());
    }

    @Test
    void K15_fazla_mesai_dogurmayan_gorev_ve_kapali_kural_ihlal_vermez() {
        Parametreler p = param("haftalik.max_saat", "168");
        // aday hafta 43'te 5 s: haftalik toplam 5 < 45 -> fazla mesai dogurmaz, yillik zaten dolu olsa da
        assertTrue(kod(degerlendir(gorev(900, "2026-10-20T10:00", "2026-10-20T15:00"),
                yirmiDokuzYuzAltmisDokuz(), p), "K-15").isEmpty());
        Parametreler kapali = param(Map.of("K-15", Siddet.KAPALI), "haftalik.max_saat", "168");
        assertTrue(kod(degerlendir(gorev(900, "2026-10-17T10:00", "2026-10-17T12:00"),
                yirmiDokuzYuzAltmisDokuz(), kapali), "K-15").isEmpty());
    }

    // ---------- K-19 haftalik dinlenme ----------

    private static List<Gorev> pztCtsOnDort(int sonGun) {
        List<Gorev> g = new ArrayList<>();
        for (int d = 12; d <= sonGun; d++) {
            g.add(gorev(d, "2026-10-" + d + "T06:00", "2026-10-" + d + "T14:00"));
        }
        return g;
    }

    @Test
    void haftaTatili_Pzt_Cmt_ve_Pazar_06_14_en_uzun_bosluk_16s_engel() {
        Parametreler p = param("haftalik.max_saat", "60");
        List<Ihlal> i = degerlendir(gorev(40, "2026-10-18T06:00", "2026-10-18T14:00"), pztCtsOnDort(17), p);
        assertEquals(List.of("K-19"), kodlar(i));
        assertEquals(16.0, i.get(0).olculen());
        assertEquals(24.0, i.get(0).esik());
    }

    @Test
    void haftaTatili_pazar_verilmezse_cumartesi_14den_pazar_24e_34s_uygun() {
        Parametreler p = param("haftalik.max_saat", "60");
        assertTrue(degerlendir(gorev(40, "2026-10-17T06:00", "2026-10-17T14:00"), pztCtsOnDort(16), p).isEmpty());
    }

    @Test
    void haftaTatili_sinir_tam_24s_uygun_1439_dk_engel() {
        // Pzt tam bos (edge bosluk 24 s); Sal 00:00-12:00, aday Car 00:00-06:00, Per..Paz 00:00-06:00
        List<Gorev> diger = new ArrayList<>(List.of(
                gorev(1, "2026-10-13T00:00", "2026-10-13T12:00"),
                gorev(3, "2026-10-15T00:00", "2026-10-15T06:00"),
                gorev(4, "2026-10-16T00:00", "2026-10-16T06:00"),
                gorev(5, "2026-10-17T00:00", "2026-10-17T06:00"),
                gorev(6, "2026-10-18T00:00", "2026-10-18T06:00")));
        Gorev aday = gorev(2, "2026-10-14T00:00", "2026-10-14T06:00");
        assertTrue(degerlendir(aday, diger).isEmpty());

        diger.set(0, gorev(1, "2026-10-12T23:59", "2026-10-13T12:00")); // en uzun bosluk 23 s 59 dk
        List<Ihlal> i = degerlendir(aday, diger);
        assertEquals(List.of("K-19"), kodlar(i));
        assertTrue(i.get(0).metin().contains("23 s 59 dk < 24 s"), i.get(0).metin());
    }

    // ---------- K-21 tek sefer ----------

    @Test
    void K21_kapali_iken_10s_sefer_ihlal_uretmez() {
        assertTrue(degerlendir(gorev(1, "2026-10-12T06:00", "2026-10-12T16:00"), List.of()).isEmpty());
    }

    @Test
    void K21_acikken_9s_uygun_9s_1dk_engel() {
        Parametreler p = param(Map.of("K-21", Siddet.ENGEL));
        assertTrue(degerlendir(gorev(1, "2026-10-12T06:00", "2026-10-12T15:00"), List.of(), p).isEmpty());
        List<Ihlal> i = degerlendir(gorev(1, "2026-10-12T06:00", "2026-10-12T15:01"), List.of(), p);
        assertEquals(List.of("K-21"), kodlar(i));
        assertEquals("Tek sefer süresi 9 s 1 dk > 9 s", i.get(0).metin());
    }

    @Test
    void K21_sefer_saatleri_kullanilir_hazirlik_dahil_degil() {
        Parametreler p = param(Map.of("K-21", Siddet.ENGEL), "gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "30");
        Gorev g = Gorev.olustur(1, 1, 1, 10, 20, an("2026-10-12T06:00"), an("2026-10-12T15:00"), 100, p);
        assertTrue(kod(KuralMotoru.degerlendir(baglam(g, List.of(), p)), "K-21").isEmpty());
    }

    // ---------- gece kurallari (K-16, K-17, K-18) ----------

    @Test
    void gece_K16_21_05_8s_engel_22_0530_7_5s_uygun() {
        List<Ihlal> i = degerlendir(gorev(1, "2026-10-12T21:00", "2026-10-13T05:00"), List.of());
        assertEquals(List.of("K-16"), kodlar(i));
        assertEquals(8.0, i.get(0).olculen());
        assertEquals(7.5, i.get(0).esik());
        assertTrue(degerlendir(gorev(1, "2026-10-12T22:00", "2026-10-13T05:30"), List.of()).isEmpty());
        assertEquals(List.of("K-16"), kodlar(degerlendir(gorev(1, "2026-10-12T22:00", "2026-10-13T05:31"), List.of())));
    }

    @Test
    void gece_K16_18_02_gece_gorevi_engel() {
        assertEquals(List.of("K-16"), kodlar(degerlendir(gorev(1, "2026-10-12T18:00", "2026-10-13T02:00"), List.of())));
    }

    @Test
    void gece_K16_04_11_gunduz_sayilir() {
        // 7 s; gece.max_saat 6 olsa bile gece payi 2/7 < 0,5 -> K-16 uygulanmaz
        Gorev g = gorev(1, "2026-10-12T04:00", "2026-10-12T11:00");
        assertTrue(kod(degerlendir(g, List.of(), param("gece.max_saat", "6")), "K-16").isEmpty());
        // oran 0,2'ye inerse gece gorevi sayilir ve 7 > 6
        assertEquals(1, kod(degerlendir(g, List.of(), param("gece.max_saat", "6", "gece.gorev_orani", "0.2")),
                "K-16").size());
    }

    @Test
    void gece_K16_oran_tam_yari_gece_gorevi_degil() {
        // 17:00-23:00: gece 3/6 = 0,5 -> "fazlasi" degil
        Gorev g = gorev(1, "2026-10-12T17:00", "2026-10-12T23:00");
        assertTrue(kod(degerlendir(g, List.of(), param("gece.max_saat", "5")), "K-16").isEmpty());
    }

    @Test
    void gece_K16_iki_ardisik_5s_gece_seferi_nobet_gecesi_10s_engel() {
        Gorev ilk = gorev(1, "2026-10-12T21:00", "2026-10-13T02:00");
        List<Ihlal> i = degerlendir(gorev(2, "2026-10-13T04:00", "2026-10-13T09:00"), List.of(ilk));
        assertEquals(List.of("K-16"), kodlar(i));
        assertEquals(10.0, i.get(0).olculen());
        assertTrue(i.get(0).metin().contains("10 s > 7 s 30 dk"), i.get(0).metin());
        // her biri tek basina uygun
        assertTrue(degerlendir(ilk, List.of()).isEmpty());
    }

    private static List<Gorev> haftaTohum(String gun41, String gun41Bit) {
        return List.of(gorev(1, gun41, gun41Bit));
    }

    @Test
    void gece_K18_hafta41_gecesi_varken_hafta42_gecesi_engel_ardisik_2() {
        List<Gorev> diger = haftaTohum("2026-10-06T22:00", "2026-10-07T05:00");
        List<Ihlal> i = degerlendir(gorev(2, "2026-10-14T23:00", "2026-10-15T05:00"), diger);
        assertEquals(List.of("K-18"), kodlar(i));
        assertEquals("2. ardışık gece haftası > 1", i.get(0).metin());
        assertEquals(2.0, i.get(0).olculen());
        assertEquals(1.0, i.get(0).esik());
    }

    @Test
    void gece_K18_ayni_hafta_gunduz_gorevi_uygun() {
        List<Gorev> diger = haftaTohum("2026-10-06T22:00", "2026-10-07T05:00");
        assertTrue(degerlendir(gorev(2, "2026-10-14T08:00", "2026-10-14T14:00"), diger).isEmpty());
    }

    @Test
    void gece_K18_hafta43te_hafta42_gecesizse_uygun() {
        List<Gorev> diger = haftaTohum("2026-10-06T22:00", "2026-10-07T05:00");
        assertTrue(degerlendir(gorev(2, "2026-10-21T22:00", "2026-10-22T05:00"), diger).isEmpty());
    }

    @Test
    void gece_K18_sonraki_hafta_gece_haftasiysa_da_engel_iki_yonlu() {
        List<Gorev> diger = haftaTohum("2026-10-21T22:00", "2026-10-22T05:00"); // hafta 43
        List<Ihlal> i = degerlendir(gorev(2, "2026-10-14T23:00", "2026-10-15T05:00"), diger);
        assertEquals(List.of("K-18"), kodlar(i));
    }

    @Test
    void gece_K18_hafta_zaten_gece_haftasiysa_ikinci_gece_gorevi_yeni_ihlal_uretmez() {
        List<Gorev> diger = List.of(gorev(1, "2026-10-06T22:00", "2026-10-07T05:00"),
                gorev(3, "2026-10-13T22:00", "2026-10-14T05:00")); // hafta 41 ve 42 gece
        assertTrue(kod(degerlendir(gorev(2, "2026-10-16T22:00", "2026-10-17T05:00"), diger), "K-18").isEmpty());
    }

    @Test
    void gece_K18_ard_hafta_max_2_iken_ucuncu_hafta_engel() {
        Parametreler p = param("gece.ard_hafta_max", "2");
        List<Gorev> diger = List.of(gorev(1, "2026-10-06T22:00", "2026-10-07T05:00"),
                gorev(3, "2026-10-13T22:00", "2026-10-14T05:00"));
        List<Ihlal> i = kod(degerlendir(gorev(2, "2026-10-20T22:00", "2026-10-21T05:00"), diger, p), "K-18");
        assertEquals(1, i.size());
        assertEquals("3. ardışık gece haftası > 2", i.get(0).metin());
        assertTrue(kod(degerlendir(gorev(2, "2026-10-13T22:00", "2026-10-14T05:00"),
                List.of(gorev(1, "2026-10-06T22:00", "2026-10-07T05:00")), p), "K-18").isEmpty());
    }

    private static List<Gorev> pztPerCum44(int sonGun) {
        List<Gorev> g = new ArrayList<>();
        for (int d = 12; d <= sonGun; d++) { // 4 x 11 s = 44 s
            g.add(gorev(d, "2026-10-" + d + "T06:00", "2026-10-" + d + "T17:00"));
        }
        return g;
    }

    @Test
    void gece_K17_44s_arti_gece_6s_50s_engel_K14_max_52_olsa_bile() {
        Parametreler p = param("haftalik.max_saat", "52");
        List<Ihlal> i = degerlendir(gorev(40, "2026-10-16T22:00", "2026-10-17T04:00"), pztPerCum44(15), p);
        assertEquals(List.of("K-17"), kodlar(i));
        assertEquals(50.0, i.get(0).olculen());
        assertEquals(45.0, i.get(0).esik());
    }

    @Test
    void gece_K17_gunduz_gorevi_haftalik_normal_ustu_gece_nobeti_yoksa_K17_degil_yalniz_K14() {
        Parametreler p = param("haftalik.max_saat", "52");
        assertTrue(degerlendir(gorev(40, "2026-10-16T08:00", "2026-10-16T14:00"), pztPerCum44(15), p).isEmpty());
    }

    // ---------- review Tur 1: W1 (K-18 min_gorev), W2 (K-17 gunduz aday, MK-5) ----------

    @Test
    void gece_K18_min_gorev_2_iken_tek_gece_nobetli_hafta_gece_haftasi_degil() {
        Parametreler p = param("gece.hafta_min_gorev", "2");
        List<Gorev> diger = List.of(gorev(1, "2026-10-06T22:00", "2026-10-07T05:00"),
                gorev(3, "2026-10-08T22:00", "2026-10-09T05:00")); // hafta 41: 2 gece nobeti = gece haftasi
        assertTrue(kod(degerlendir(gorev(2, "2026-10-14T23:00", "2026-10-15T05:00"), diger, p), "K-18").isEmpty());
    }

    @Test
    void gece_K18_min_gorev_2_iken_ikinci_gece_nobeti_haftayi_gece_haftasi_yapar() {
        Parametreler p = param("gece.hafta_min_gorev", "2");
        List<Gorev> diger = List.of(gorev(1, "2026-10-06T22:00", "2026-10-07T05:00"), // hafta 41: 1 (aday ikincisi olur)
                gorev(3, "2026-10-13T22:00", "2026-10-14T05:00"), gorev(4, "2026-10-15T22:00", "2026-10-16T05:00")); // hafta 42: gece
        List<Ihlal> i = kod(degerlendir(gorev(2, "2026-10-08T22:00", "2026-10-09T05:00"), diger, p), "K-18");
        assertEquals(1, i.size());
        assertEquals(2.0, i.get(0).olculen());
    }

    private static List<Gorev> pztPerCum9sVeGece() {
        List<Gorev> g = new ArrayList<>();
        for (int d = 12; d <= 15; d++) { // 4 x 9 s = 36 s
            g.add(gorev(d, "2026-10-" + d + "T06:00", "2026-10-" + d + "T15:00"));
        }
        g.add(gorev(40, "2026-10-17T22:00", "2026-10-18T04:00")); // kayitli gece nobeti 6 s -> 42 s
        return g;
    }

    @Test
    void gece_K17_gunduz_aday_kayitli_gece_nobetini_normal_ustune_cikariyorsa_engel() {
        Parametreler p = param("haftalik.max_saat", "52");
        List<Ihlal> i = degerlendir(gorev(41, "2026-10-16T06:00", "2026-10-16T14:00"), pztPerCum9sVeGece(), p); // 42 + 8 = 50
        assertEquals(List.of("K-17"), kodlar(i));
        assertEquals(50.0, i.get(0).olculen());
        assertEquals(45.0, i.get(0).esik());
    }

    @Test
    void gece_K17_gunduz_aday_gece_nobetinden_SONRA_ise_gece_nobetini_etkilemez() {
        Parametreler p = param("haftalik.max_saat", "52");
        assertEquals(List.of(), degerlendir(gorev(41, "2026-10-18T16:00", "2026-10-18T20:00"), pztPerCum9sVeGece(), p)); // 46 s, MK-5: gece nobetinden sonra
    }

    @Test
    void gece_K17_gunduz_aday_normal_altinda_kaliyorsa_uygun() {
        Parametreler p = param("haftalik.max_saat", "52");
        assertTrue(degerlendir(gorev(41, "2026-10-16T06:00", "2026-10-16T09:00"), pztPerCum9sVeGece(), p).isEmpty()); // 45 s = esik
    }
}
