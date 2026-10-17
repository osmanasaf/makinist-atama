package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.gorev;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.util.List;
import org.junit.jupiter.api.Test;

class NobetHesabiTest {

    private static final long AYIRMA = 240; // nobet.ayirma_saat = 4

    @Test
    void bos_liste_nobet_uretmez() {
        assertTrue(NobetHesabi.grupla(List.of(), AYIRMA).isEmpty());
    }

    @Test
    void gidis_donus_ayni_nobet_bosluk_2_saat() {
        List<Nobet> n = NobetHesabi.grupla(List.of(
                gorev(2, "2026-10-12T14:00", "2026-10-12T18:00"),
                gorev(1, "2026-10-12T08:00", "2026-10-12T12:00")), AYIRMA);
        assertEquals(1, n.size());
        assertEquals(an("2026-10-12T08:00"), n.get(0).baslangic());
        assertEquals(an("2026-10-12T18:00"), n.get(0).bitis());
        assertEquals(2, n.get(0).gorevler().size());
    }

    @Test
    void sekiz_saat_bosluk_ayri_nobet() {
        List<Nobet> n = NobetHesabi.grupla(List.of(
                gorev(1, "2026-10-12T14:00", "2026-10-12T22:00"),
                gorev(2, "2026-10-13T06:00", "2026-10-13T10:00")), AYIRMA);
        assertEquals(2, n.size());
    }

    @Test
    void bosluk_tam_4_saat_ayri_nobet_3_59_ayni() {
        assertEquals(2, NobetHesabi.grupla(List.of(
                gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"),
                gorev(2, "2026-10-12T16:00", "2026-10-12T18:00")), AYIRMA).size());
        assertEquals(1, NobetHesabi.grupla(List.of(
                gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"),
                gorev(2, "2026-10-12T15:59", "2026-10-12T18:00")), AYIRMA).size());
    }

    @Test
    void ic_ice_gorev_nobet_bitisi_en_gec_bitis_ve_son_varis_gari() {
        Gorev uzun = gorev(1, TestVerisi.ANKARA, TestVerisi.ESKISEHIR, "2026-10-12T08:00", "2026-10-12T20:00");
        Gorev kisa = gorev(2, TestVerisi.ESKISEHIR, TestVerisi.ANKARA, "2026-10-12T09:00", "2026-10-12T10:00");
        Gorev sonra = gorev(3, "2026-10-12T22:00", "2026-10-12T23:00"); // bosluk 2 s (en gec bitise gore)
        List<Nobet> n = NobetHesabi.grupla(List.of(uzun, kisa, sonra), AYIRMA);
        assertEquals(1, n.size());
        assertEquals(an("2026-10-12T23:00"), n.get(0).bitis());
        assertEquals(TestVerisi.ANKARA, n.get(0).ilkKalkisGarId());
        assertEquals(TestVerisi.ANKARA, n.get(0).sonVarisGarId());
    }

    @Test
    void nobetBul_kimlikle() {
        Gorev a = gorev(1, "2026-10-12T08:00", "2026-10-12T12:00");
        Gorev b = gorev(2, "2026-10-13T08:00", "2026-10-13T12:00");
        List<Nobet> n = NobetHesabi.grupla(List.of(a, b), AYIRMA);
        assertSame(n.get(1), NobetHesabi.nobetiBul(n, b));
    }

    @Test
    void calismaAraliklari_bekleme_sayilmaz_gorev_araliklari() {
        List<Gorev> g = List.of(
                gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"),
                gorev(2, "2026-10-12T14:00", "2026-10-12T18:00"));
        List<Aralik> a = NobetHesabi.calismaAraliklari(g, AYIRMA, false);
        assertEquals(2, a.size());
        assertEquals(480, a.stream().mapToLong(Aralik::dakika).sum());
    }

    @Test
    void calismaAraliklari_bekleme_sayilir_nobet_araligi() {
        List<Gorev> g = List.of(
                gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"),
                gorev(2, "2026-10-12T14:00", "2026-10-12T18:00"));
        List<Aralik> a = NobetHesabi.calismaAraliklari(g, AYIRMA, true);
        assertEquals(1, a.size());
        assertEquals(600, a.get(0).dakika()); // 08:00-18:00 arada 2 saat bekleme dahil
    }

    @Test
    void sorumluKomsular_oncekilerVeAyniNobettekiSonrakiler_ayirilanSonrakiHaric() {
        Gorev onceki = gorev(1, "2026-10-11T22:00", "2026-10-12T03:00");
        Gorev ayniNobet = gorev(2, "2026-10-12T07:55", "2026-10-12T13:10"); // adaydan 68 dk sonra: aynı nöbet
        Gorev ayrilan = gorev(3, "2026-10-12T20:00", "2026-10-12T23:00"); // aralarında > 4 s: farklı nöbet, sorumlu değil
        Gorev aday = gorev(4, "2026-10-12T05:00", "2026-10-12T06:47");

        List<Gorev> sorumlu = NobetHesabi.sorumluKomsular(List.of(onceki, ayniNobet, ayrilan), aday, AYIRMA);

        assertEquals(List.of(onceki, ayniNobet), sorumlu);
    }
}
