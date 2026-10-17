package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.kural;
import static com.makinistatama.kural.OzelKuralYap.p;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** F3b MK-85 (FR-03): özel kural kapsamında personel türü; kapı kişi düzeyindedir (ölçüden bağımsız), boş küme = tümü. */
class OzelKuralTurKapsamiTest {

    private static MakinistBilgisi kisi(MakinistBilgisi.Tur tur) {
        boolean makinist = tur == MakinistBilgisi.Tur.MAKINIST;
        return new MakinistBilgisi(1, TestVerisi.ANKARA, MakinistBilgisi.Durum.AKTIF, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1),
                makinist ? Map.of(TestVerisi.ARAC, LocalDate.of(2024, 1, 1)) : Map.of(),
                makinist ? Map.of(TestVerisi.HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1))) : Map.of(),
                null, List.of(), tur);
    }

    private static Kapsam turler(MakinistBilgisi.Tur... t) {
        return new Kapsam(null, null, null, null, null, null, Set.of(t));
    }

    private static List<String> ozel(Gorev aday, List<Gorev> diger, MakinistBilgisi m, Parametreler p) {
        return kodlar(KuralMotoru.degerlendir(baglam(aday, diger, m, p)).stream().filter(i -> OzelKural.ozelMi(i.kod())).toList());
    }

    @Test
    void gunlukSinir9s_yalnizKonduktor_konduktore10sIhlal_makinistte_yok() {
        Parametreler p = p(kural(1, "Kondüktör günlük 9 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "9", turler(MakinistBilgisi.Tur.KONDUKTOR), Siddet.ENGEL));
        Gorev g = gorev(1, "2026-10-12T06:00", "2026-10-12T16:00"); // 10 s
        assertEquals(List.of("Ö-001"), ozel(g, List.of(), kisi(MakinistBilgisi.Tur.KONDUKTOR), p));
        assertTrue(ozel(g, List.of(), kisi(MakinistBilgisi.Tur.MAKINIST), p).isEmpty());
    }

    @Test
    void bosKume_tumTurler_veAlansizKapsamAyniDavranis() {
        Gorev g = gorev(1, "2026-10-12T06:00", "2026-10-12T16:00");
        Parametreler bos = p(kural(1, "Herkes 9 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "9", new Kapsam(null, null, null, null, null, null, Set.of()), Siddet.ENGEL));
        Parametreler eski = p(kural(1, "Herkes 9 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "9", new Kapsam(null, null, null, null, null, null), Siddet.ENGEL));
        for (MakinistBilgisi.Tur t : MakinistBilgisi.Tur.values()) {
            assertEquals(List.of("Ö-001"), ozel(g, List.of(), kisi(t), bos));
            assertEquals(ozel(g, List.of(), kisi(t), eski), ozel(g, List.of(), kisi(t), bos));
        }
        assertTrue(Kapsam.TUMU.tumu());
        assertFalse(turler(MakinistBilgisi.Tur.KONDUKTOR).tumu());
    }

    @Test
    void nobetlerArasiDinlenmeOlcusundeDeTurKapisiCalisir() {
        Parametreler kon = p(kural(1, "Kondüktör nöbet arası", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, Karsilastirma.EN_AZ, "12", turler(MakinistBilgisi.Tur.KONDUKTOR), null));
        Parametreler tum = p(kural(1, "Herkes nöbet arası", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, Karsilastirma.EN_AZ, "12", Kapsam.TUMU, null));
        Gorev onceki = gorev(10, "2026-10-12T06:00", "2026-10-12T12:00");
        Gorev aday = gorev(1, "2026-10-12T18:00", "2026-10-12T23:00"); // 6 s ara < 12 s
        List<String> konTum = ozel(aday, List.of(onceki), kisi(MakinistBilgisi.Tur.KONDUKTOR), tum);
        assertEquals(1, konTum.size());
        assertEquals(konTum, ozel(aday, List.of(onceki), kisi(MakinistBilgisi.Tur.MAKINIST), tum));
        assertEquals(konTum, ozel(aday, List.of(onceki), kisi(MakinistBilgisi.Tur.KONDUKTOR), kon));
        assertTrue(ozel(aday, List.of(onceki), kisi(MakinistBilgisi.Tur.MAKINIST), kon).isEmpty());
    }

    @Test
    void kapsamTurleri_siraliVeNullGuvenli() {
        Kapsam k = new Kapsam(null, null, null, null, null, null, Set.of(MakinistBilgisi.Tur.MAKINIST, MakinistBilgisi.Tur.KONDUKTOR));
        assertEquals(List.of(MakinistBilgisi.Tur.MAKINIST, MakinistBilgisi.Tur.KONDUKTOR), List.copyOf(k.personelTurleri()));
        assertTrue(new Kapsam(null, null, null, null, null, null, null).personelTurleri().isEmpty());
    }
}
