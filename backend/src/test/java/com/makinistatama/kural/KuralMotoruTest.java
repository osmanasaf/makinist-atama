package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kod;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KuralMotoruTest {

    private static final Gorev ONCEKI = gorev(1, "2026-10-12T14:00", "2026-10-12T22:00");
    private static final Gorev ADAY = gorev(2, "2026-10-13T06:00", "2026-10-13T10:00");

    @Test
    void uygun_aday_ihlal_donmez() {
        assertTrue(KuralMotoru.degerlendir(baglam(ADAY, List.of())).isEmpty());
        assertFalse(KuralMotoru.engelVarMi(List.of()));
    }

    @Test
    void birden_cok_ihlal_birlikte_doner_hat_yetkisi_yok_ve_dinlenme() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new HashMap<>();
        List<Ihlal> i = KuralMotoru.degerlendir(baglam(ADAY, List.of(ONCEKI), m.yap(), param()));
        assertEquals(List.of("K-06", "K-11"), kodlar(i));
        assertTrue(KuralMotoru.engelVarMi(i));
        assertEquals("Hat yetkisi yok: Ankara–Eskişehir", i.get(0).metin());
        assertEquals("Dinlenme 8 s < 11 s; önceki görev 12 Eki 22:00'de bitti", i.get(1).metin());
    }

    @Test
    void KAPALI_kural_cikti_vermez_ve_kontrol_listesinden_duser() {
        Parametreler p = param(Map.of("K-11", Siddet.KAPALI));
        assertTrue(KuralMotoru.degerlendir(baglam(ADAY, List.of(ONCEKI), p)).isEmpty());
        assertFalse(KuralMotoru.kontrolEdilenKodlar(p).contains("K-11"));
    }

    @Test
    void siddet_parametreden_gelir_UYARI_engel_degildir() {
        // K-11 UYARI yapildiginda ihlal UYARI siddetinde doner, engelVarMi false
        Parametreler p = param(Map.of("K-11", Siddet.UYARI));
        List<Ihlal> i = KuralMotoru.degerlendir(baglam(ADAY, List.of(ONCEKI), p));
        assertEquals(List.of("K-11"), kodlar(i));
        assertEquals(Siddet.UYARI, i.get(0).siddet());
        assertFalse(KuralMotoru.engelVarMi(i));
    }

    @Test
    void K20_yalniz_UYARI_engel_yok() {
        Gorev onceki = gorev(1, 1, 2, "2026-10-12T08:00", "2026-10-12T12:00");
        List<Ihlal> i = KuralMotoru.degerlendir(baglam(gorev(2, 1, 2, "2026-10-12T14:00", "2026-10-12T18:00"),
                List.of(onceki)));
        assertEquals(List.of("K-20"), kodlar(i));
        assertEquals(Siddet.UYARI, i.get(0).siddet());
        assertFalse(KuralMotoru.engelVarMi(i));
    }

    @Test
    void K10_siddet_haritasinda_olmasa_da_sabit_ENGEL() {
        Gorev diger = gorev(1, "2026-10-12T09:00", "2026-10-12T11:00");
        List<Ihlal> i = kod(KuralMotoru.degerlendir(baglam(gorev(2, "2026-10-12T10:00", "2026-10-12T12:00"),
                List.of(diger))), "K-10");
        assertEquals(1, i.size());
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
    }

    @Test
    void kontrolEdilenKodlar_varsayilan_K01_K22_hepsi_ama_K21_kapali() {
        List<String> k = KuralMotoru.kontrolEdilenKodlar(VarsayilanParametreler.varsayilan());
        assertEquals(21, k.size()); // K-01..K-22, K-21 kapalı
        assertTrue(k.contains("K-10"));
        assertTrue(k.contains("K-20"));
        assertFalse(k.contains("K-21"));
        assertEquals("K-01", k.get(0));
    }

    @Test
    void metin_bicimi_olculen_esik_birim_dolu() {
        Ihlal i = KuralMotoru.degerlendir(baglam(ADAY, List.of(ONCEKI))).get(0);
        assertEquals("K-11", i.kod());
        assertEquals(8.0, i.olculen());
        assertEquals(11.0, i.esik());
        assertEquals("s", i.birim());
        assertEquals(Siddet.ENGEL, i.siddet());
    }

    @Test
    void sonuc_kod_sirasina_gore_belirleyici() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new HashMap<>();
        m.durum = MakinistBilgisi.Durum.ASKIDA;
        List<String> k = kodlar(KuralMotoru.degerlendir(baglam(ADAY, List.of(ONCEKI), m.yap(), param())));
        assertEquals(List.of("K-01", "K-06", "K-11"), k);
    }

    // ---------- K-22: bitmiş sefere atama (yalnız bağlamda "şimdi" varsa) ----------

    private static KuralBaglami simdiyle(java.time.Instant simdi, Parametreler p) {
        return new KuralBaglami(ADAY, List.of(), TestVerisi.makinist(), Map.of(), null, TestVerisi.ADLAR, p, simdi);
    }

    @Test
    void k22_bitmisSefer_uyari_sinirdaDegil_simdiYoksaDenetlenmez() {
        java.time.Instant bitis = ADAY.varis();

        List<Ihlal> sonra = KuralMotoru.degerlendir(simdiyle(bitis.plusSeconds(60), param()));
        assertEquals(List.of("K-22"), kodlar(sonra));
        assertEquals(Siddet.UYARI, sonra.get(0).siddet());
        assertTrue(sonra.get(0).metin().contains("bitti"), sonra.get(0).metin());
        assertTrue(KuralMotoru.degerlendir(simdiyle(bitis, param())).isEmpty()); // tam bitiş anı: henüz geçmişte değil
        assertTrue(KuralMotoru.degerlendir(simdiyle(null, param())).isEmpty());
        assertTrue(KuralMotoru.degerlendir(baglam(ADAY, List.of())).isEmpty()); // eski kurucu: denetlenmez
    }

    @Test
    void k22_siddetParametreden_engel_ya_da_kapali() {
        java.time.Instant sonra = ADAY.varis().plusSeconds(3600);

        assertEquals(Siddet.ENGEL, KuralMotoru.degerlendir(simdiyle(sonra, param(Map.of("K-22", Siddet.ENGEL)))).get(0).siddet());
        assertTrue(KuralMotoru.degerlendir(simdiyle(sonra, param(Map.of("K-22", Siddet.KAPALI)))).isEmpty());
    }
}
