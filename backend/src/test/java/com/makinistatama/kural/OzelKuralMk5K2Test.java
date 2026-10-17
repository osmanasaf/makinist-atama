package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.kural;
import static com.makinistatama.kural.TestVerisi.baglam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * MK-5 (NFR-06), K2 ölçüleri, DB'siz rastgele senaryo ({@link OzelKuralMk5Test} ile aynı kontrat): seri (çalışma/gece günü), kayan pencere (süre, görev, gece nöbeti),
 * izin günü, görevsiz aralık, nöbetler arası dinlenme ve hat/tren türü kapsamları. Görevler iki hat ve üç tren türü (YUK/YOLCU/belirsiz) arasında dağılır.
 * Kabul edilen aday (a) kendisi özel kuralla kırmızı olmaz, (b) daha önce kırmızı olmayan hiçbir görevi kırmızı yapmaz.
 */
class OzelKuralMk5K2Test {

    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };
    static final long HAT_B = 99;
    static final String[] TURLER = { "YUK", "YOLCU", null };

    static Kapsam hatA() {
        return OzelKuralYap.hat(TestVerisi.HAT);
    }

    static Kapsam yuk() {
        return new Kapsam(null, null, null, null, Set.of("YUK"), null);
    }

    static List<OzelKural> havuz() {
        Karsilastirma f = Karsilastirma.EN_FAZLA;
        Karsilastirma a = Karsilastirma.EN_AZ;
        List<OzelKural> l = new ArrayList<>();
        l.add(kural(1, "Çalışma serisi 3", Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, f, "3", Kapsam.TUMU, null));
        l.add(kural(2, "Gece serisi 2", Olcu.ARDISIK_GECE_GUNU, Pencere.SERI, f, "2", Kapsam.TUMU, null));
        l.add(kural(3, "Hat A çalışma serisi 2", Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, f, "2", hatA(), null));
        l.add(kural(4, "Ayda 12 gün izin", Olcu.IZIN_GUNU, Pencere.AY, a, "12", Kapsam.TUMU, null));
        l.add(kural(5, "Haftada 2 gün izin", Olcu.IZIN_GUNU, Pencere.HAFTA, a, "2", Kapsam.TUMU, null));
        l.add(kural(6, "Kayan 5 gün 2 gün izin", Olcu.IZIN_GUNU, Pencere.KAYAN, a, "2", Kapsam.TUMU, 5));
        l.add(kural(7, "Günde 8 s boşluk", Olcu.GOREVSIZ_ARALIK, Pencere.GUN, a, "8", Kapsam.TUMU, null));
        l.add(kural(8, "Haftada 30 s boşluk", Olcu.GOREVSIZ_ARALIK, Pencere.HAFTA, a, "30", Kapsam.TUMU, null));
        l.add(kural(9, "Nöbetler arası 12 s", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, a, "12", Kapsam.TUMU, null));
        l.add(kural(10, "Hat A nöbetler arası 14 s", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, a, "14", hatA(), null));
        l.add(kural(11, "Gece nöbetleri arası 16 s", Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, a, "16", OzelKuralYap.vardiya(Vardiya.GECE), null));
        l.add(kural(12, "Kayan 7 gün 14 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, f, "14", Kapsam.TUMU, 7));
        l.add(kural(13, "Yük kayan 4 gün 8 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, f, "8", yuk(), 4));
        l.add(kural(14, "Kayan 3 gün 2 görev", Olcu.GOREV_SAYISI, Pencere.KAYAN, f, "2", Kapsam.TUMU, 3));
        l.add(kural(15, "Kayan 6 gün 2 gece nöbeti", Olcu.GECE_NOBETI_SAYISI, Pencere.KAYAN, f, "2", Kapsam.TUMU, 6));
        l.add(kural(16, "Kayan 5 gün gece 6 s", Olcu.GECE_SURESI, Pencere.KAYAN, f, "6", Kapsam.TUMU, 5));
        l.add(kural(17, "Gece vardiyası kayan 3 gün 9 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, f, "9", OzelKuralYap.vardiya(Vardiya.GECE), 3));
        l.add(kural(18, "Hat B kayan 4 gün 3 görev", Olcu.GOREV_SAYISI, Pencere.KAYAN, f, "1", new Kapsam(Set.of(HAT_B), null, null, null, null, null), 4));
        return l;
    }

    private static Gorev rastgele(Random r, long id) {
        LocalDate gun = LocalDate.of(2026, 10, 5).plusDays(r.nextInt(25));
        LocalTime k = LocalTime.parse(SAATLER[r.nextInt(SAATLER.length)]);
        var kalkis = gun.atTime(k).atZone(ZamanHesabi.ISTANBUL).toInstant();
        var varis = kalkis.plusSeconds(60L * SURELER[r.nextInt(SURELER.length)]);
        long hat = r.nextInt(3) == 0 ? HAT_B : TestVerisi.HAT;
        return new Gorev(id, TestVerisi.ANKARA, TestVerisi.ANKARA, hat, TestVerisi.ARAC, kalkis, varis, kalkis, varis, 100, TURLER[r.nextInt(TURLER.length)]);
    }

    static boolean ozelKirmizi(List<Gorev> hepsi, Gorev g, Parametreler p) {
        List<Gorev> digerler = hepsi.stream().filter(x -> x != g).toList();
        List<Gorev> onceki = NobetHesabi.sorumluKomsular(digerler, g, p.dakika("nobet.ayirma_saat"));
        return KuralMotoru.degerlendir(baglam(g, onceki, p)).stream().anyMatch(i -> OzelKural.ozelMi(i.kod()) && i.siddet() == Siddet.ENGEL);
    }

    private void kos(long tohum, boolean bekleme, int senaryo) {
        Random r = new Random(tohum);
        List<OzelKural> havuz = havuz();
        int kabul = 0;
        int red = 0;
        for (int s = 0; s < senaryo; s++) {
            List<OzelKural> secilen = new ArrayList<>();
            int adet = 1 + r.nextInt(3);
            for (int i = 0; i < adet; i++) {
                OzelKural k = havuz.get(r.nextInt(havuz.size()));
                if (secilen.stream().noneMatch(x -> x.id() == k.id())) {
                    secilen.add(k);
                }
            }
            Parametreler p = OzelKuralYap.p(java.util.Map.of(), secilen, "nobet.bekleme_sayilir", bekleme ? "1" : "0");
            List<Gorev> mevcut = new ArrayList<>();
            int n = 3 + r.nextInt(8);
            for (int i = 0; i < n; i++) {
                mevcut.add(rastgele(r, i + 1));
            }
            Gorev aday = rastgele(r, 100);

            List<Ihlal> yazma = KuralMotoru.degerlendirYazma(baglam(aday, mevcut, p));
            if (KuralMotoru.engelVarMi(yazma)) {
                red++;
                continue;
            }
            kabul++;
            List<Gorev> sonra = new ArrayList<>(mevcut);
            sonra.add(aday);
            String bilgi = "tohum " + tohum + " senaryo " + s + " kurallar " + secilen.stream().map(OzelKural::ad).toList() + " aday " + aday.kalkis() + " " + aday.hatId();
            assertFalse(ozelKirmizi(sonra, aday, p), "(A) kabul edilen aday kırmızı: " + bilgi);
            for (Gorev x : mevcut) {
                if (!ozelKirmizi(mevcut, x, p)) {
                    assertFalse(ozelKirmizi(sonra, x, p), "(B) kabul edilen aday başka görevi kırmızı yaptı (görev " + x.seferId() + " " + x.kalkis() + "): " + bilgi);
                }
            }
        }
        assertTrue(kabul > senaryo / 10, "kabul edilen senaryo sayısı anlamlı olmalı: " + kabul);
        assertTrue(red > senaryo / 20, "reddedilen senaryo sayısı anlamlı olmalı: " + red);
        assertEquals(senaryo, kabul + red);
    }

    @Test
    void k2_kabulEdilenAdayVeKomsulariKirmiziOlmaz_bekleme0() {
        kos(31, false, 4000);
    }

    @Test
    void k2_kabulEdilenAdayVeKomsulariKirmiziOlmaz_bekleme1() {
        kos(47, true, 4000);
    }

    @Test
    void k2_kabulEdilenAdayVeKomsulariKirmiziOlmaz_baskaTohum() {
        kos(909, false, 4000);
    }
}
