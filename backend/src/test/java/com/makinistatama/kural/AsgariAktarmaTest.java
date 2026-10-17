package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.degerlendir;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kod;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** K-23 (Q-1): aynı nöbette ardışık iki görev arasında asgari aktarma/dönüş süresi; tohumda KAPALI/0 (davranış değişmez). */
class AsgariAktarmaTest {

    private static final Gorev ILK = gorev(1, "2026-10-12T06:00", "2026-10-12T08:00");
    private static final Parametreler ACIK = param(Map.of("K-23", Siddet.ENGEL), "nobet.asgari_aktarma_dk", "20");

    private static Gorev sonra(long id, String kalkis) {
        return gorev(id, kalkis, "2026-10-12T11:00");
    }

    @Test
    void kapali_ve_sifir_bir_dakikalik_aktarma_ihlal_degil() {
        Parametreler eski = param(Map.of("K-23", Siddet.KAPALI), "nobet.asgari_aktarma_dk", "0"); // OK-24: varsayılan artık ENGEL/20; eski varsayılan açık ön koşul
        assertTrue(kod(degerlendir(sonra(2, "2026-10-12T08:01"), List.of(ILK), eski), "K-23").isEmpty());
    }

    @Test
    void sifir_deger_siddet_ENGEL_olsa_da_kural_calismaz() {
        Parametreler p = param(Map.of("K-23", Siddet.ENGEL), "nobet.asgari_aktarma_dk", "0");
        assertTrue(kod(degerlendir(sonra(2, "2026-10-12T08:01"), List.of(ILK), p), "K-23").isEmpty());
    }

    @Test
    void deger_var_ama_siddet_KAPALI_ise_calismaz() {
        Parametreler p = param(Map.of("K-23", Siddet.KAPALI), "nobet.asgari_aktarma_dk", "20");
        assertTrue(kod(degerlendir(sonra(2, "2026-10-12T08:05"), List.of(ILK), p), "K-23").isEmpty());
    }

    @Test
    void aday_sonra_gelirse_10_dk_aktarma_ENGEL() {
        List<Ihlal> i = kod(degerlendir(sonra(2, "2026-10-12T08:10"), List.of(ILK), ACIK), "K-23");
        assertEquals(1, i.size());
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
        assertEquals(10, i.get(0).olculen());
        assertEquals(20, i.get(0).esik());
        assertEquals("dk", i.get(0).birim());
    }

    @Test
    void aday_once_gelirse_sonraki_gorevle_de_denetlenir() {
        Gorev aday = gorev(2, "2026-10-12T05:00", "2026-10-12T05:55");
        assertEquals(1, kod(degerlendir(aday, List.of(ILK), ACIK), "K-23").size());
    }

    @Test
    void ortusen_seferler_K10_konusudur_K23_uretmez() {
        List<Ihlal> i = degerlendir(sonra(2, "2026-10-12T07:30"), List.of(ILK), ACIK);
        assertTrue(kod(i, "K-23").isEmpty());
        assertEquals(1, kod(i, "K-10").size());
    }

    @Test
    void tam_esik_ve_ustu_ihlal_degil() {
        assertTrue(kod(degerlendir(sonra(2, "2026-10-12T08:20"), List.of(ILK), ACIK), "K-23").isEmpty());
        assertTrue(kod(degerlendir(sonra(3, "2026-10-12T09:00"), List.of(ILK), ACIK), "K-23").isEmpty());
    }

    @Test
    void ayni_trenin_ardisik_parcasi_muaf_OK17() {
        Parametreler p = param(Map.of("K-23", Siddet.ENGEL), "nobet.asgari_aktarma_dk", "20");
        Gorev devam = Gorev.olustur(2, 1, 1, TestVerisi.HAT, TestVerisi.ARAC, an("2026-10-12T08:05"), an("2026-10-12T08:05"), an("2026-10-12T11:00"), 100, null, p, false, true);
        assertTrue(kod(degerlendir(devam, List.of(ILK), p), "K-23").isEmpty());
        Gorev ayriGorev = Gorev.olustur(2, 1, 1, TestVerisi.HAT, TestVerisi.ARAC, an("2026-10-12T08:05"), an("2026-10-12T08:05"), an("2026-10-12T11:00"), 100, null, p, true, true);
        assertEquals(1, kod(degerlendir(ayriGorev, List.of(ILK), p), "K-23").size());
    }

    @Test
    void yolculuk_gecisleri_yolculuk_aktarma_ayarina_birakilir() {
        Gorev yolculuk = Gorev.yolculukOlustur(2, 1, 1, TestVerisi.HAT, TestVerisi.ARAC, an("2026-10-12T05:00"), an("2026-10-12T05:00"), an("2026-10-12T06:00"), true);
        Gorev surus = gorev(3, "2026-10-12T06:05", "2026-10-12T09:00");
        assertTrue(kod(degerlendir(surus, List.of(yolculuk), ACIK), "K-23").isEmpty());
    }

    @Test
    void farkli_nobette_olan_gorevler_bu_kurala_girmez() {
        Parametreler p = param(Map.of("K-23", Siddet.ENGEL), "nobet.asgari_aktarma_dk", "600"); // 10 saat > nöbet ayırma (4 saat)
        assertTrue(kod(degerlendir(gorev(2, "2026-10-12T14:00", "2026-10-12T16:00"), List.of(ILK), p), "K-23").isEmpty());
    }
}
