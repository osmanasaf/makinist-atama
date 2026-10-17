package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.parametre.ParametreServisi;

/**
 * QA (M1 kapısı): P2'nin migration tohumu (gerçek PostgreSQL) -> {@link ParametreServisi#yukle()} -> P6'nın saf motoru zinciri.
 * Birim testler katalog sabitiyle ({@link VarsayilanParametreler}) çalışır; bu test aynı analiz örneklerini DB'den okunan
 * parametrelerle koşar, yani tohum ile motor birbirine bağlı doğrulanır.
 */
class KuralMotoruTohumTest extends TestVeritabani {

    @Autowired
    ParametreServisi parametreler;

    private List<Ihlal> degerlendir(Gorev aday, List<Gorev> diger, TestVerisi.M m) {
        return KuralMotoru.degerlendir(baglam(aday, diger, m.yap(), parametreler.yukle()));
    }

    @Test
    void K11_dinlenme_12Eki2200_sonrasi_13Eki0600_8s_engel_0900_uygun_tohumdan() {
        Gorev onceki = gorev(1, "2026-10-12T14:00", "2026-10-12T22:00");

        List<Ihlal> ihlal = degerlendir(gorev(2, "2026-10-13T06:00", "2026-10-13T10:00"), List.of(onceki), new TestVerisi.M());
        assertEquals(List.of("K-11"), kodlar(ihlal));
        assertEquals("Dinlenme 8 s < 11 s; önceki görev 12 Eki 22:00'de bitti", ihlal.get(0).metin());
        assertTrue(degerlendir(gorev(2, "2026-10-13T09:00", "2026-10-13T12:00"), List.of(onceki), new TestVerisi.M()).isEmpty());
    }

    @Test
    void K16_gece_21_05_8s_engel_22_0530_uygun_tohumdan() {
        assertEquals(List.of("K-16"), kodlar(degerlendir(gorev(1, "2026-10-12T21:00", "2026-10-13T05:00"), List.of(), new TestVerisi.M())));
        assertTrue(degerlendir(gorev(1, "2026-10-12T22:00", "2026-10-13T05:30"), List.of(), new TestVerisi.M()).isEmpty());
    }

    @Test
    void K05_K06_sinav_2021_engel_2022_uygun_tohumdan_60ay() {
        TestVerisi.M eski = new TestVerisi.M();
        eski.arac = Map.of(TestVerisi.ARAC, LocalDate.of(2021, 3, 10));
        eski.hat = Map.of(TestVerisi.HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2021, 3, 10), LocalDate.of(2026, 9, 1)));
        TestVerisi.M yeni = new TestVerisi.M();
        yeni.arac = Map.of(TestVerisi.ARAC, LocalDate.of(2022, 3, 10));
        yeni.hat = Map.of(TestVerisi.HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2022, 3, 10), LocalDate.of(2026, 9, 1)));
        Gorev sefer = gorev(1, "2026-10-12T08:00", "2026-10-12T12:00");

        assertEquals(List.of("K-05", "K-06"), kodlar(degerlendir(sefer, List.of(), eski)));
        assertTrue(degerlendir(sefer, List.of(), yeni).isEmpty());
    }

    @Test
    void K20_konum_uyusmazligi_yalniz_UYARI_tohumdaK20_UYARI_K21_KAPALI() {
        Gorev onceki = gorev(1, TestVerisi.ANKARA, TestVerisi.ESKISEHIR, "2026-10-12T06:00", "2026-10-12T08:00");
        List<Ihlal> ihlal = degerlendir(gorev(2, TestVerisi.ANKARA, TestVerisi.ANKARA, "2026-10-12T20:00", "2026-10-12T23:00"), List.of(onceki),
                new TestVerisi.M());

        assertEquals(List.of("K-20"), kodlar(ihlal));
        assertEquals(Siddet.UYARI, ihlal.get(0).siddet());
        assertTrue(!KuralMotoru.engelVarMi(ihlal));
        assertTrue(!KuralMotoru.kontrolEdilenKodlar(parametreler.yukle()).contains("K-21"));
    }
}
