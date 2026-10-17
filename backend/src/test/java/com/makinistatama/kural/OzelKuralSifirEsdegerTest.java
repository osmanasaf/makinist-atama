package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.kural;
import static com.makinistatama.kural.OzelKuralYap.p;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T1.5 (NFR-06): özel kural yokken, yalnız KAPALI kural varken ve hiçbir şey tetiklemeyen kural varken motor çıktısı AYNI; degerlendirYazma da. */
class OzelKuralSifirEsdegerTest {

    private record Senaryo(Gorev aday, List<Gorev> diger) {
    }

    private static List<Senaryo> senaryolar() {
        List<Senaryo> l = new ArrayList<>();
        l.add(new Senaryo(gorev(2, "2026-10-13T06:00", "2026-10-13T10:00"), List.of(gorev(1, "2026-10-12T14:00", "2026-10-12T22:00")))); // K-11
        l.add(new Senaryo(gorev(3, "2026-10-12T16:00", "2026-10-12T20:00"), List.of(gorev(1, "2026-10-12T04:00", "2026-10-12T09:00"), gorev(2, "2026-10-12T10:00", "2026-10-12T15:00")))); // K-13
        l.add(new Senaryo(gorev(2, "2026-10-12T22:00", "2026-10-13T07:00"), List.of(gorev(1, "2026-10-11T22:00", "2026-10-12T04:00")))); // gece
        l.add(new Senaryo(gorev(2, "2026-10-12T08:00", "2026-10-12T10:00"), List.of())); // temiz
        l.add(new Senaryo(gorev(9, "2026-10-12T10:00", "2026-10-12T12:00"), List.of(gorev(1, "2026-10-12T09:00", "2026-10-12T11:00"), gorev(5, "2026-10-12T13:00", "2026-10-12T20:00")))); // K-10
        return l;
    }

    @Test
    void bos_KAPALI_ve_tetiklemeyen_kural_ayni_cikti() {
        Parametreler bos = TestVerisi.param();
        Parametreler kapali = p(kural(1, "k", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1", Kapsam.TUMU, Siddet.KAPALI),
                kural(2, "g", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "1", Kapsam.TUMU, Siddet.KAPALI));
        Parametreler tetiklemez = p(kural(1, "uzak", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "24"),
                kural(2, "hat", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "1", OzelKuralYap.hat(999), Siddet.ENGEL));
        for (Senaryo s : senaryolar()) {
            List<Ihlal> beklenen = KuralMotoru.degerlendir(baglam(s.aday(), s.diger(), bos));
            assertEquals(beklenen, KuralMotoru.degerlendir(baglam(s.aday(), s.diger(), kapali)));
            assertEquals(beklenen, KuralMotoru.degerlendir(baglam(s.aday(), s.diger(), tetiklemez)));
            List<Ihlal> yazma = KuralMotoru.degerlendirYazma(baglam(s.aday(), s.diger(), bos));
            assertEquals(yazma, KuralMotoru.degerlendirYazma(baglam(s.aday(), s.diger(), kapali)));
            assertEquals(yazma, KuralMotoru.degerlendirYazma(baglam(s.aday(), s.diger(), tetiklemez)));
            assertEquals(KuralMotoru.kontrolEdilenKodlar(bos), KuralMotoru.kontrolEdilenKodlar(kapali));
        }
    }

    @Test
    void olustur_iki_bilesenli_bos_liste_verir() {
        assertEquals(List.of(), Parametreler.olustur(Map.of("a", "1"), Map.of()).ozelKurallar());
    }
}
