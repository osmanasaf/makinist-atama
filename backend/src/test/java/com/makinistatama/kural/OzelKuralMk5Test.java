package com.makinistatama.kural;

import static com.makinistatama.kural.OzelKuralYap.kural;
import static com.makinistatama.kural.OzelKuralYap.vardiya;
import static com.makinistatama.kural.TestVerisi.baglam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * MK-5 (NFR-06), DB'siz rastgele senaryo: bir makinistin rastgele görev kümesi ve rastgele özel kurallar için, {@code degerlendirYazma} ENGEL vermediği
 * (kabul edilen) bir aday eklendiğinde (A) aday kendisi özel kuralla kırmızı olmaz ve (B) daha önce özel kuralla kırmızı OLMAYAN hiçbir başka görev
 * sonradan kırmızı olmaz. Kırmızı = {@code KirmiziAtamaHesabi}'nın tanımı: yalnız kendisinden önceki + aynı nöbetteki sonraki görevlerle değerlendirme.
 */
class OzelKuralMk5Test {

    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    static List<OzelKural> havuz() {
        long id = 1;
        List<OzelKural> l = new ArrayList<>();
        l.add(kural(id++, "Gün 8", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "8"));
        l.add(kural(id++, "Hafta 24", Olcu.TOPLAM_GOREV_SURESI, Pencere.HAFTA, "24"));
        l.add(kural(id++, "Ay 70", Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "70"));
        l.add(kural(id++, "Nöbet 7", Olcu.TOPLAM_GOREV_SURESI, Pencere.NOBET, "7"));
        l.add(kural(id++, "Gece gün 4", Olcu.GECE_SURESI, Pencere.GUN, "4"));
        l.add(kural(id++, "Gece hafta 12", Olcu.GECE_SURESI, Pencere.HAFTA, "12"));
        l.add(kural(id++, "Gece nöbet 5", Olcu.GECE_SURESI, Pencere.NOBET, "5"));
        l.add(kural(id++, "Görev gün 2", Olcu.GOREV_SAYISI, Pencere.GUN, "2"));
        l.add(kural(id++, "Görev hafta 6", Olcu.GOREV_SAYISI, Pencere.HAFTA, "6"));
        l.add(kural(id++, "Gece nöbeti hafta 2", Olcu.GECE_NOBETI_SAYISI, Pencere.HAFTA, "2"));
        l.add(kural(id++, "Gece nöbeti ay 5", Olcu.GECE_NOBETI_SAYISI, Pencere.AY, "5"));
        l.add(kural(id++, "Gece vardiyası gün 5", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "5", vardiya(Vardiya.GECE), Siddet.ENGEL));
        l.add(kural(id++, "Gündüz vardiyası gün 6", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "6", vardiya(Vardiya.GUNDUZ), Siddet.ENGEL));
        l.add(kural(id++, "Gece vardiyası hafta 14", Olcu.TOPLAM_GOREV_SURESI, Pencere.HAFTA, "14", vardiya(Vardiya.GECE), Siddet.ENGEL));
        l.add(kural(id++, "Gece vardiyası görev hafta 3", Olcu.GOREV_SAYISI, Pencere.HAFTA, "3", vardiya(Vardiya.GECE), Siddet.ENGEL));
        l.add(kural(id++, "Gündüz vardiyası ay 40", Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "40", vardiya(Vardiya.GUNDUZ), Siddet.ENGEL));
        l.add(kural(id++, "Yıl 90", Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "90"));
        l.add(kural(id++, "Tek sefer 6", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "6"));
        return l;
    }

    private static Gorev rastgele(Random r, long id, boolean bekleme) {
        LocalDate gun = LocalDate.of(2026, 10, 5).plusDays(r.nextInt(25));
        LocalTime k = LocalTime.parse(SAATLER[r.nextInt(SAATLER.length)]);
        var kalkis = gun.atTime(k).atZone(ZamanHesabi.ISTANBUL).toInstant();
        var varis = kalkis.plusSeconds(60L * SURELER[r.nextInt(SURELER.length)]);
        return new Gorev(id, TestVerisi.ANKARA, TestVerisi.ANKARA, TestVerisi.HAT, TestVerisi.ARAC, kalkis, varis, kalkis, varis, 100);
    }

    /** KirmiziAtamaHesabi tanımı: yalnız kendisinden önceki + aynı nöbetteki sonraki görevlerle; yalnız ENGEL düzeyindeki özel kural ihlali sayılır. */
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
            int adet = 1 + r.nextInt(4);
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
                mevcut.add(rastgele(r, i + 1, bekleme));
            }
            Gorev aday = rastgele(r, 100, bekleme);

            List<Ihlal> yazma = KuralMotoru.degerlendirYazma(baglam(aday, mevcut, p));
            if (KuralMotoru.engelVarMi(yazma)) {
                red++;
                continue;
            }
            kabul++;
            List<Gorev> sonra = new ArrayList<>(mevcut);
            sonra.add(aday);
            String bilgi = "tohum " + tohum + " senaryo " + s + " kurallar " + secilen.stream().map(OzelKural::ad).toList();
            assertFalse(ozelKirmizi(sonra, aday, p), "(A) kabul edilen aday kırmızı: " + bilgi);
            for (Gorev x : mevcut) {
                if (!ozelKirmizi(mevcut, x, p)) {
                    assertFalse(ozelKirmizi(sonra, x, p), "(B) kabul edilen aday başka görevi kırmızı yaptı (görev " + x.seferId() + "): " + bilgi);
                }
            }
        }
        assertTrue(kabul > senaryo / 10, "kabul edilen senaryo sayısı anlamlı olmalı: " + kabul);
        assertTrue(red > senaryo / 20, "reddedilen senaryo sayısı anlamlı olmalı: " + red);
        assertEquals(senaryo, kabul + red);
    }

    @Test
    void kabulEdilenAdayVeKomsulariKirmiziOlmaz_bekleme0() {
        kos(11, false, 4000);
    }

    @Test
    void kabulEdilenAdayVeKomsulariKirmiziOlmaz_bekleme1() {
        kos(23, true, 4000);
    }

    @Test
    void kabulEdilenAdayVeKomsulariKirmiziOlmaz_baskaTohum() {
        kos(777, false, 4000);
    }
}
