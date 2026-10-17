package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.p;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tur 3, reviewer karşı örnekleri: W1 (geçerlilik aralığı dışındaki görevler o kuralın ölçümüne girmez, MK-5) ve W4 (gece nöbeti günü = gecenin başladığı gün).
 * Assertler yalnız Ö- kodlarına bakar.
 */
class OzelKuralTur3Test {

    private static List<Ihlal> ozel(Gorev aday, List<Gorev> diger, Parametreler p) {
        return KuralMotoru.degerlendir(baglam(aday, diger, p)).stream().filter(i -> OzelKural.ozelMi(i.kod())).toList();
    }

    /** Kırmızı hesabı: yalnız sorumlu (önceki ve aynı nöbetteki) komşular, K-22 yok. */
    private static boolean kirmizi(Gorev g, List<Gorev> digerleri, Parametreler p) {
        List<Gorev> onceki = NobetHesabi.sorumluKomsular(digerleri.stream().filter(x -> x != g).toList(), g, p.dakika("nobet.ayirma_saat"));
        return ozel(g, onceki, p).stream().anyMatch(i -> i.siddet() == Siddet.ENGEL);
    }

    private static boolean yazmaEngeli(Gorev aday, List<Gorev> diger, Parametreler p) {
        return KuralMotoru.engelVarMi(KuralMotoru.degerlendirYazma(baglam(aday, diger, p)).stream().filter(i -> OzelKural.ozelMi(i.kod())).toList());
    }

    private static OzelKural gecerli(long id, Olcu olcu, Pencere pencere, Integer kayan, Karsilastirma kars, String esik, LocalDate bas, LocalDate bit) {
        return new OzelKural(id, "Kural " + id, olcu, pencere, kayan, kars, new BigDecimal(esik), Kapsam.TUMU, Siddet.ENGEL, bas, bit, "g", "k", 0);
    }

    // ---------------------------------------------------------------- W1: geçerlilik aralığı

    private static final Parametreler KAYAN_10_GUN_10_S = p(
            gecerli(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 10, Karsilastirma.EN_FAZLA, "10", LocalDate.of(2026, 11, 15), null));

    @Test
    void w1_baslangictanOncekiAtama_baslangicSonrasiGorevi_kirmiziYapmaz() {
        Gorev sonra = gorev(1, "2026-11-16T08:00", "2026-11-16T14:00"); // 6 s, kural yürürlükte
        Gorev aday = gorev(2, "2026-11-10T08:00", "2026-11-10T14:00"); // 6 s, kural henüz yürürlükte değil
        assertTrue(!yazmaEngeli(aday, List.of(sonra), KAYAN_10_GUN_10_S), "yürürlükten önceki aday kuralla engellenmez");
        assertTrue(!kirmizi(sonra, List.of(aday, sonra), KAYAN_10_GUN_10_S), "yürürlük öncesi görev başlangıç sonrası görevin toplamına girmez");
        // geçerlilik içinde aynı iki görev yine ihlal üretir
        Gorev icerde = gorev(3, "2026-11-15T08:00", "2026-11-15T14:00");
        assertEquals(1, ozel(icerde, List.of(sonra), KAYAN_10_GUN_10_S).size());
    }

    @Test
    void w1_bitistenSonrakiGorev_adayinToplamina_girmez() {
        Parametreler p = p(gecerli(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 10, Karsilastirma.EN_FAZLA, "10", LocalDate.of(2000, 1, 1), LocalDate.of(2026, 11, 15)));
        Gorev sonra = gorev(1, "2026-11-16T08:00", "2026-11-16T14:00"); // bitişten sonra
        Gorev aday = gorev(2, "2026-11-14T08:00", "2026-11-14T14:00");
        assertTrue(ozel(aday, List.of(sonra), p).isEmpty(), "yürürlük sonrası görev sayılmaz (aşırı kısıtlama yok)");
        assertEquals(1, ozel(aday, List.of(gorev(3, "2026-11-15T08:00", "2026-11-15T14:00")), p).size()); // 15 Kas hâlâ içeride
    }

    @Test
    void w1_dinlenme_yururlukDisiKomsuyuGormez() {
        Parametreler p = p(gecerli(1, Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, null, Karsilastirma.EN_AZ, "12", LocalDate.of(2026, 11, 15), null));
        Gorev aday = gorev(2, "2026-11-14T22:00", "2026-11-15T02:00"); // sefer günü 14 Kas: kural dışı
        Gorev sonra = gorev(1, "2026-11-15T08:00", "2026-11-15T12:00"); // aradaki boşluk 6 s < 12 s ama ilk görev yürürlük dışı
        assertTrue(!yazmaEngeli(aday, List.of(sonra), p));
        assertTrue(!kirmizi(sonra, List.of(aday, sonra), p));
    }

    // ---------------------------------------------------------------- W4: gece nöbeti günü

    private static final Parametreler GECE_SERISI_3 = p(
            OzelKuralYap.kural(1, "Gece üst üste 3", Olcu.ARDISIK_GECE_GUNU, Pencere.SERI, Karsilastirma.EN_FAZLA, "3", Kapsam.TUMU, null));

    /** Pzt 12, Sal 13, Çar 14 Ekim 2026 geceleri 22:00–05:00. */
    private static final List<Gorev> UC_GECE = List.of(gorev(1, "2026-10-12T22:00", "2026-10-13T05:00"), gorev(2, "2026-10-13T22:00", "2026-10-14T05:00"),
            gorev(3, "2026-10-14T22:00", "2026-10-15T05:00"));

    @Test
    void w4_persembeGecesi_cumaSaat01deBaslayanNobet_dorduncuGeceSayilir() {
        List<Ihlal> i = ozel(gorev(9, "2026-10-16T01:00", "2026-10-16T05:00"), UC_GECE, GECE_SERISI_3); // Perşembe gecesi (15 Eki)
        assertEquals(1, i.size());
        assertEquals("Ardışık gece günü (üst üste 12 Eki 2026 – 15 Eki 2026): 4 gün > 3 gün (Kural: Gece üst üste 3)", i.get(0).metin());
    }

    @Test
    void w4_cumartesiGecesi_pazarSaat01deBaslayanNobet_pazartesiGeceleriyleBirlesmez() {
        assertTrue(ozel(gorev(9, "2026-10-11T01:00", "2026-10-11T05:00"), UC_GECE, GECE_SERISI_3).isEmpty()); // 10 Eki gecesi; 11 Eki gecesi boş
    }

    @Test
    void w4_aksamBaslayanNobet_ayniGunuSayar_ceseriyleGunduzNobetiKatilmaz() {
        assertTrue(ozel(gorev(9, "2026-10-16T22:00", "2026-10-17T05:00"), UC_GECE, GECE_SERISI_3).isEmpty()); // Cuma gecesi: Perşembe boş
        assertEquals(1, ozel(gorev(9, "2026-10-15T22:00", "2026-10-16T05:00"), UC_GECE, GECE_SERISI_3).size()); // Perşembe gecesi akşamdan: 4. gece
        assertTrue(ozel(gorev(9, "2026-10-15T10:00", "2026-10-15T14:00"), UC_GECE, GECE_SERISI_3).isEmpty());
    }
}
