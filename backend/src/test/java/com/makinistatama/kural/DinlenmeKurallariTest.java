package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.ESKISEHIR;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DinlenmeKurallariTest {

    /** Dinlenme/çakışma testleri K-23'ten bağımsızdır: OK-24 ile varsayılan K-23 ENGEL/20 dk; eski davranış (KAPALI) açıkça kurulur. */
    private static final Parametreler K23_KAPALI = param(java.util.Map.of("K-23", Siddet.KAPALI));

    private static List<Ihlal> kontrol(Gorev aday, List<Gorev> diger) {
        return DinlenmeKurallari.kontrol(baglam(aday, diger, K23_KAPALI));
    }

    private static List<Ihlal> kontrol(Gorev aday, List<Gorev> diger, Parametreler p) {
        return DinlenmeKurallari.kontrol(baglam(aday, diger, p));
    }

    @Test
    void K11_dinlenme_8s_lt_11s_olculen_esik_ve_metin() {
        Gorev onceki = gorev(1, "2026-10-12T14:00", "2026-10-12T22:00");
        List<Ihlal> i = kontrol(gorev(2, "2026-10-13T06:00", "2026-10-13T10:00"), List.of(onceki));
        assertEquals(List.of("K-11"), kodlar(i));
        Ihlal k = i.get(0);
        assertEquals(Siddet.ENGEL, k.siddet());
        assertEquals(8.0, k.olculen());
        assertEquals(11.0, k.esik());
        assertEquals("Dinlenme 8 s < 11 s; önceki görev 12 Eki 22:00'de bitti", k.metin());
    }

    @Test
    void K11_sinir_09_00_uygun_09_01_uygun_08_59_engel() {
        Gorev onceki = gorev(1, "2026-10-12T14:00", "2026-10-12T22:00");
        assertTrue(kontrol(gorev(2, "2026-10-13T09:00", "2026-10-13T12:00"), List.of(onceki)).isEmpty());
        assertTrue(kontrol(gorev(2, "2026-10-13T09:01", "2026-10-13T12:00"), List.of(onceki)).isEmpty());
        List<Ihlal> i = kontrol(gorev(2, "2026-10-13T08:59", "2026-10-13T12:00"), List.of(onceki));
        assertEquals(List.of("K-11"), kodlar(i));
        assertTrue(i.get(0).metin().startsWith("Dinlenme 10 s 59 dk < 11 s"), i.get(0).metin());
    }

    @Test
    void K11_sonraki_gorevle_de_kontrol_edilir() {
        Gorev sonraki = gorev(1, "2026-10-13T06:00", "2026-10-13T10:00");
        List<Ihlal> i = kontrol(gorev(2, "2026-10-12T14:00", "2026-10-12T22:00"), List.of(sonraki));
        assertEquals(List.of("K-11"), kodlar(i));
        assertTrue(i.get(0).metin().startsWith("Dinlenme 8 s < 11 s (sonraki görev)"), i.get(0).metin());
        assertEquals(8.0, i.get(0).olculen());
    }

    @Test
    void ayni_gun_gidis_donus_08_12_ve_14_18_uygun() {
        assertTrue(kontrol(gorev(2, "2026-10-12T14:00", "2026-10-12T18:00"),
                List.of(gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"))).isEmpty());
        // ters sirayla da
        assertTrue(kontrol(gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"),
                List.of(gorev(2, "2026-10-12T14:00", "2026-10-12T18:00"))).isEmpty());
    }

    @Test
    void nobet_ici_arama_degil_nobetler_arasi_bosluk_olculur() {
        // nobet: 08-12 + 14-18 (bitis 18:00); ertesi gun 04:00 -> bosluk 10 s < 11
        List<Gorev> diger = List.of(gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"),
                gorev(2, "2026-10-12T14:00", "2026-10-12T18:00"));
        List<Ihlal> i = kontrol(gorev(3, "2026-10-13T04:00", "2026-10-13T06:00"), diger);
        assertEquals(List.of("K-11"), kodlar(i));
        assertEquals(10.0, i.get(0).olculen());
    }

    @Test
    void K12_dis_merkez_varsayilan_11_8s_engel_ve_dis_merkez_8_ise_uygun() {
        // onceki gorev Eskisehir'de bitti, makinist Ankara'ya bagli; aday Eskisehir'den kalkiyor (K-20 yok)
        Gorev onceki = gorev(1, ANKARA, ESKISEHIR, "2026-10-12T10:00", "2026-10-12T22:00");
        Gorev aday = gorev(2, ESKISEHIR, ANKARA, "2026-10-13T06:00", "2026-10-13T10:00");
        List<Ihlal> i = kontrol(aday, List.of(onceki));
        assertEquals(List.of("K-12"), kodlar(i));
        assertTrue(i.get(0).metin().startsWith("Dinlenme 8 s < 11 s; önceki görev 12 Eki 22:00'de bitti"));

        assertTrue(kontrol(aday, List.of(onceki), param("dinlenme.dis_merkez_saat", "8")).isEmpty());
        // 8 s - 1 dk -> yine ihlal
        Gorev erken = gorev(2, ESKISEHIR, ANKARA, "2026-10-13T05:59", "2026-10-13T10:00");
        assertEquals(List.of("K-12"),
                kodlar(kontrol(erken, List.of(onceki), param("dinlenme.dis_merkez_saat", "8"))));
    }

    @Test
    void K12_ev_merkezinde_dis_merkez_esigi_gecerli_degil() {
        Gorev onceki = gorev(1, "2026-10-12T14:00", "2026-10-12T22:00"); // Ankara'da bitti (ev)
        Gorev aday = gorev(2, "2026-10-13T06:00", "2026-10-13T10:00");
        assertEquals(List.of("K-11"),
                kodlar(kontrol(aday, List.of(onceki), param("dinlenme.dis_merkez_saat", "8"))));
        assertTrue(kontrol(aday, List.of(onceki), param("dinlenme.ev_saat", "8")).isEmpty());
    }

    @Test
    void K10_cakisma_engel_bitis_esit_baslangic_uygun() {
        Gorev diger = gorev(1, "2026-10-12T08:00", "2026-10-12T10:00");
        List<Ihlal> i = kontrol(gorev(2, "2026-10-12T09:00", "2026-10-12T11:00"), List.of(diger));
        assertEquals(List.of("K-10"), kodlar(i));
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
        assertEquals("Çakışma: bu görev, 12 Eki 08:00 – 12 Eki 10:00 arasındaki başka bir görevle 1 s örtüşüyor", i.get(0).metin()); // UX-12: sefer kimliği yok
        assertTrue(kontrol(gorev(2, "2026-10-12T10:00", "2026-10-12T12:00"), List.of(diger)).isEmpty());
        assertTrue(kontrol(gorev(2, "2026-10-12T06:00", "2026-10-12T08:00"), List.of(diger)).isEmpty());
        // 1 dk cakisma
        assertEquals(List.of("K-10"),
                kodlar(kontrol(gorev(2, "2026-10-12T09:59", "2026-10-12T12:00"), List.of(diger))));
    }

    @Test
    void K20_konum_uyusmazligi_UYARI() {
        Gorev onceki = gorev(1, ANKARA, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T12:00");
        List<Ihlal> i = kontrol(gorev(2, ANKARA, ESKISEHIR, "2026-10-12T14:00", "2026-10-12T18:00"), List.of(onceki));
        assertEquals(List.of("K-20"), kodlar(i));
        assertEquals(Siddet.UYARI, i.get(0).siddet());
        assertEquals("Konum uyuşmazlığı: Eskişehir ≠ Ankara", i.get(0).metin());
        // uyumlu zincir
        assertTrue(kontrol(gorev(2, ESKISEHIR, ANKARA, "2026-10-12T14:00", "2026-10-12T18:00"), List.of(onceki))
                .isEmpty());
    }

    @Test
    void K20_sonraki_gorev_de_denetlenir() {
        Gorev sonraki = gorev(1, ANKARA, ANKARA, "2026-10-12T14:00", "2026-10-12T18:00");
        List<Ihlal> i = kontrol(gorev(2, ANKARA, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T12:00"), List.of(sonraki));
        assertEquals(List.of("K-20"), kodlar(i));
        assertTrue(i.get(0).metin().contains("sonraki görev"));
    }

    @Test
    void K20_ilk_gorevde_konum_bagli_gardir_OK5() {
        // makinist Ankara'ya bağlı: ilk görev Ankara'dan başlıyorsa uyarı yok, Eskişehir'den başlıyorsa K-20
        assertTrue(kontrol(gorev(2, ANKARA, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T12:00"), List.of()).isEmpty());
        List<Ihlal> i = kontrol(gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T12:00"), List.of());
        assertEquals(List.of("K-20"), kodlar(i));
        assertEquals(Siddet.UYARI, i.get(0).siddet());
        assertEquals("Konum uyuşmazlığı (ilk görev, bağlı gar): Ankara ≠ Eskişehir", i.get(0).metin());
        // önceki görev varsa bağlı gara bakılmaz, önceki görevin varış garı belirleyicidir
        Gorev onceki = gorev(1, ESKISEHIR, ESKISEHIR, "2026-10-12T06:00", "2026-10-12T07:00");
        assertTrue(kontrol(gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T11:00", "2026-10-12T12:00"), List.of(onceki)).stream().noneMatch(x -> x.kod().equals("K-20")));
    }

    @Test
    void hazirlik_30_alikoyma_15_gorev_araligi_07_30_12_15_dinlenmede_sayilir() {
        Parametreler p = param("gorev.hazirlik_dk", "30", "gorev.alikoyma_dk", "15");
        Gorev g = Gorev.olustur(1, ANKARA, ANKARA, 10, 20, an("2026-10-12T08:00"), an("2026-10-12T12:00"), 100, p);
        assertEquals(an("2026-10-12T07:30"), g.baslangic());
        assertEquals(an("2026-10-12T12:15"), g.bitis());
        assertEquals(an("2026-10-12T08:00"), g.kalkis());
        assertEquals(285, g.sureDakika()); // 4,75 s

        // onceki gorev bitisi 12:15; aday kalkis 23:00 -> baslangic 22:30 -> dinlenme 10 s 15 dk < 11 s
        Gorev aday = Gorev.olustur(2, ANKARA, ANKARA, 10, 20, an("2026-10-12T23:00"), an("2026-10-13T01:00"), 100, p);
        List<Ihlal> i = kontrol(aday, List.of(g), p);
        assertEquals(List.of("K-11"), kodlar(i));
        assertTrue(i.get(0).metin().startsWith("Dinlenme 10 s 15 dk < 11 s"), i.get(0).metin());

        // hazirlik/alikoyma 0 iken ayni sefer saatleri 10 s 15 dk degil 11 s -> uygun
        Parametreler sifir = param();
        Gorev g0 = Gorev.olustur(1, ANKARA, ANKARA, 10, 20, an("2026-10-12T08:00"), an("2026-10-12T12:00"), 100, sifir);
        Gorev a0 = Gorev.olustur(2, ANKARA, ANKARA, 10, 20, an("2026-10-12T23:00"), an("2026-10-13T01:00"), 100, sifir);
        assertTrue(kontrol(a0, List.of(g0), sifir).isEmpty());
    }
}
