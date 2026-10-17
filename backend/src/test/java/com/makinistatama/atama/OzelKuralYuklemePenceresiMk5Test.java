package com.makinistatama.atama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.NobetHesabi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.kural.ZamanHesabi;

/**
 * MK-5 + YÜKLEME PENCERESİ (DB'siz rastgele): {@code OzelKuralMk5Test} motora bütün görevleri verir; gerçekte {@code BaglamYukleyici}/{@code OtomatikAtama} yalnız
 * sefer gününün etrafındaki pencereyi yükler. Burada hem yazma anı denetimi hem kırmızı hesabı, o görevin KENDİ penceresindeki görevleri görür (görevler 5 ay
 * boyunca dağılır; yıl/ay/hafta kuralları). Değişmez: kabul edilen aday, daha önce kırmızı olmayan hiçbir görevi (kendi penceresiyle) kırmızı yapmaz.
 * Yıl penceresinin ileri yüklemesi yıl sonuna kadar uzamıyorsa bu test kırılır.
 */
class OzelKuralYuklemePenceresiMk5Test {

    static final Parametreler TABAN = VarsayilanParametreler.varsayilan();
    static final String[] SAATLER = { "04:00", "07:55", "10:00", "13:00", "18:00", "21:30" };
    static final int[] SURELER = { 60, 120, 240, 360, 600 };

    private static OzelKural kural(long id, Olcu olcu, Pencere pencere, String esik) {
        return new OzelKural(id, "K" + id, olcu, pencere, Karsilastirma.EN_FAZLA, new BigDecimal(esik), Kapsam.TUMU, Siddet.ENGEL, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    private static OzelKural kayan(long id, Olcu olcu, String esik, int gun) {
        return new OzelKural(id, "K" + id, olcu, Pencere.KAYAN, gun, Karsilastirma.EN_FAZLA, new BigDecimal(esik), Kapsam.TUMU, Siddet.ENGEL, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    private static List<OzelKural> havuz() {
        return List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "25"), kural(2, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "40"),
                kural(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "20"), kural(4, Olcu.TOPLAM_GOREV_SURESI, Pencere.HAFTA, "16"),
                kural(5, Olcu.GOREV_SAYISI, Pencere.AY, "4"), kural(6, Olcu.GECE_SURESI, Pencere.AY, "10"),
                kayan(7, Olcu.TOPLAM_GOREV_SURESI, "30", 60), kayan(8, Olcu.GOREV_SAYISI, "3", 30),
                new OzelKural(9, "K9", Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, Karsilastirma.EN_FAZLA, new BigDecimal("1"), Kapsam.TUMU, Siddet.ENGEL, LocalDate.of(2000, 1, 1), null, "g", "k", 0));
    }

    private static Gorev rastgele(Random r, long id) {
        LocalDate gun = LocalDate.of(2026, 9, 1).plusDays(r.nextInt(140));
        var kalkis = gun.atTime(LocalTime.parse(SAATLER[r.nextInt(SAATLER.length)])).atZone(ZamanHesabi.ISTANBUL).toInstant();
        var varis = kalkis.plusSeconds(60L * SURELER[r.nextInt(SURELER.length)]);
        return new Gorev(id, OtomatikAtamaTest.ANKARA, OtomatikAtamaTest.ANKARA, OtomatikAtamaTest.HAT, OtomatikAtamaTest.ARAC, kalkis, varis, kalkis, varis, 100);
    }

    /** BaglamYukleyici.komsular ile aynı pencere: gün − pencere (K-15/yıl penceresinde yıl başı) … yük sonu. */
    private static List<Gorev> gorunen(List<Gorev> hepsi, Gorev merkez, Parametreler p) {
        LocalDate gun = ZamanHesabi.gun(merkez.kalkis());
        int pencere = BaglamYukleyici.pencereGun(p);
        LocalDate bas = ZamanHesabi.pencereBasi(gun, pencere, BaglamYukleyici.yillik(p));
        LocalDate bit = ZamanHesabi.pencereSonu(gun, pencere, p.ozelYillik());
        return hepsi.stream().filter(x -> x != merkez).filter(x -> {
            LocalDate g = ZamanHesabi.gun(x.kalkis());
            return !g.isBefore(bas) && !g.isAfter(bit);
        }).toList();
    }

    private static KuralBaglami baglam(Gorev aday, List<Gorev> diger, Parametreler p) {
        return new KuralBaglami(aday, diger, OtomatikAtamaTest.bilgi(1, true, List.of()), null, null, OtomatikAtamaTest.ADLAR, p);
    }

    private static boolean ozelKirmizi(List<Gorev> hepsi, Gorev g, Parametreler p) {
        List<Gorev> onceki = NobetHesabi.sorumluKomsular(gorunen(hepsi, g, p), g, p.dakika("nobet.ayirma_saat"));
        return KuralMotoru.degerlendir(baglam(g, onceki, p)).stream().anyMatch(i -> OzelKural.ozelMi(i.kod()) && i.siddet() == Siddet.ENGEL);
    }

    @Test
    void kabulEdilenAday_kendiPenceresiyleBakanBaskaGorevleriKirmiziYapmaz() {
        kos(2026, 1500, havuz(), r -> rastgele(r, 0), 150, 75);
    }

    /** Tur 3 (W1): geçerlilik aralığı olan kurallar (başlangıç ve/veya bitiş görev dağılımının ortasında) + K2 ölçüleri. */
    @Test
    void gecerlilikAraligiOlanKurallar_ve_K2Olculeri_MK5Korunur() {
        LocalDate bas = LocalDate.of(2026, 10, 20);
        LocalDate bit = LocalDate.of(2026, 11, 20);
        Karsilastirma f = Karsilastirma.EN_FAZLA;
        Karsilastirma a = Karsilastirma.EN_AZ;
        List<OzelKural> havuz = List.of(
                gecerli(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 10, f, "14", bas, null), gecerli(2, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 10, f, "14", bas, bit),
                gecerli(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, null, f, "20", bas, bit), gecerli(4, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, null, f, "30", bas, null),
                gecerli(5, Olcu.GOREV_SAYISI, Pencere.HAFTA, null, f, "3", bas, bit), gecerli(6, Olcu.IZIN_GUNU, Pencere.AY, null, a, "24", bas, null),
                gecerli(7, Olcu.IZIN_GUNU, Pencere.KAYAN, 5, a, "2", bas, bit), gecerli(8, Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, null, a, "14", bas, bit),
                gecerli(9, Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, null, f, "3", bas, bit), gecerli(10, Olcu.ARDISIK_GECE_GUNU, Pencere.SERI, null, f, "1", bas, null),
                gecerli(11, Olcu.GOREVSIZ_ARALIK, Pencere.AY, null, a, "60", LocalDate.of(2000, 1, 1), bit));
        kos(777, 2500, havuz, r -> rastgele(r, 0), 150, 50);
    }

    /** Tur 3 (W2): gece yarısını aşan görevler ay sonu / yıl sonu çevresinde yoğunlaşır; ay ve yıl penceresi kuralları. */
    @Test
    void ayVeYilSonundaGeceYarisiniAsanGorevler_MK5Korunur() {
        Karsilastirma a = Karsilastirma.EN_AZ;
        List<OzelKural> havuz = List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "14"), kural(2, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "14"),
                gecerli(3, Olcu.IZIN_GUNU, Pencere.AY, null, a, "26", LocalDate.of(2000, 1, 1), null), gecerli(4, Olcu.IZIN_GUNU, Pencere.AY, null, a, "27", LocalDate.of(2000, 1, 1), null),
                gecerli(5, Olcu.GOREVSIZ_ARALIK, Pencere.AY, null, a, "700", LocalDate.of(2000, 1, 1), null), kural(6, Olcu.GOREV_SAYISI, Pencere.AY, "3"),
                kural(7, Olcu.GECE_SURESI, Pencere.AY, "8"));
        kos(31, 3000, havuz, r -> {
            boolean yilSonu = r.nextBoolean();
            LocalDate gun = yilSonu ? LocalDate.of(2026, 12, 27).plusDays(r.nextInt(12)) : LocalDate.of(2026, 11, 26).plusDays(r.nextInt(10));
            String[] saat = { "10:00", "20:00", "22:00", "23:30", "00:30" };
            var kalkis = gun.atTime(LocalTime.parse(saat[r.nextInt(saat.length)])).atZone(ZamanHesabi.ISTANBUL).toInstant();
            var varis = kalkis.plusSeconds(60L * (120 + 60 * r.nextInt(8)));
            return new Gorev(0, OtomatikAtamaTest.ANKARA, OtomatikAtamaTest.ANKARA, OtomatikAtamaTest.HAT, OtomatikAtamaTest.ARAC, kalkis, varis, kalkis, varis, 100);
        }, 150, 50);
    }

    private static OzelKural gecerli(long id, Olcu olcu, Pencere pencere, Integer kayan, Karsilastirma kars, String esik, LocalDate bas, LocalDate bit) {
        return new OzelKural(id, "K" + id, olcu, pencere, kayan, kars, new BigDecimal(esik), Kapsam.TUMU, Siddet.ENGEL, bas, bit, "g", "k", 0);
    }

    private static void kos(long tohum, int senaryo, List<OzelKural> havuz, java.util.function.Function<Random, Gorev> uretici, int enAzKabul, int enAzRed) {
        Random r = new Random(tohum);
        int kabul = 0;
        int red = 0;
        for (int s = 0; s < senaryo; s++) {
            List<OzelKural> secilen = new ArrayList<>();
            for (int i = 1 + r.nextInt(3); i > 0; i--) {
                OzelKural k = havuz.get(r.nextInt(havuz.size()));
                if (secilen.stream().noneMatch(x -> x.id() == k.id())) {
                    secilen.add(k);
                }
            }
            Parametreler p = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), secilen);
            List<Gorev> mevcut = new ArrayList<>();
            for (int i = 0, n = 4 + r.nextInt(9); i < n; i++) {
                mevcut.add(yeniKimlik(uretici.apply(r), i + 1));
            }
            Gorev aday = yeniKimlik(uretici.apply(r), 100);
            if (KuralMotoru.engelVarMi(KuralMotoru.degerlendirYazma(baglam(aday, gorunen(mevcut, aday, p), p)))) {
                red++;
                continue;
            }
            kabul++;
            List<Gorev> sonra = new ArrayList<>(mevcut);
            sonra.add(aday);
            String bilgi = "senaryo " + s + " kurallar " + secilen.stream().map(OzelKural::ad).toList() + " aday " + aday.kalkis();
            assertFalse(ozelKirmizi(sonra, aday, p), "(A) kabul edilen aday kırmızı: " + bilgi);
            for (Gorev x : mevcut) {
                if (!ozelKirmizi(mevcut, x, p)) {
                    assertFalse(ozelKirmizi(sonra, x, p), "(B) kabul edilen aday başka görevi kırmızı yaptı (görev " + x.seferId() + " " + x.kalkis() + "): " + bilgi);
                }
            }
        }
        assertTrue(kabul > enAzKabul, "kabul: " + kabul);
        assertTrue(red > enAzRed, "red: " + red);
        assertEquals(senaryo, kabul + red);
    }

    private static Gorev yeniKimlik(Gorev g, long id) {
        return new Gorev(id, g.kalkisGarId(), g.varisGarId(), g.hatId(), g.aracTipiId(), g.kalkis(), g.varis(), g.baslangic(), g.bitis(), g.km());
    }

    /** W2 (reviewer senaryo-1): yıl sonu gecesi. 31 Ara 22:00 – 1 Oca 04:00 aday, 10 Şub 2027'deki 8 s görevin yıl toplamını (4 s + 8 s) 10 s'in üstüne çıkarır. */
    @Test
    void yilSonuGecesi_adayYeniYilaTasar_ilerideSubatGorevi_kirmiziOlmaz() {
        Parametreler p = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "10")));
        Gorev subat = yeniKimlik(OtomatikAtamaTest.gorev(0, "2027-02-10T10:00", "2027-02-10T18:00"), 1);
        Gorev aday = yeniKimlik(OtomatikAtamaTest.gorev(0, "2026-12-31T22:00", "2027-01-01T04:00"), 2);
        List<Gorev> mevcut = List.of(subat);
        if (!KuralMotoru.engelVarMi(KuralMotoru.degerlendirYazma(baglam(aday, gorunen(mevcut, aday, p), p)))) {
            assertFalse(ozelKirmizi(List.of(subat, aday), subat, p), "kabul edilen yılbaşı gecesi görevi Şubat görevini kırmızı yaptı");
        }
    }

    /** W2 (reviewer senaryo-2): ay sonu gecesi. Aralık'ta 15/20/25/31'de görev; 30 Kas 22:00 – 1 Ara 02:00 aday Aralık'ta 5. görevli günü yaratır (izin 26 &lt; 27). */
    @Test
    void aySonuGecesi_adayYeniAyaTasar_ilerideAyinSonGorevi_kirmiziOlmaz() {
        Parametreler p = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(),
                List.of(gecerli(1, Olcu.IZIN_GUNU, Pencere.AY, null, Karsilastirma.EN_AZ, "27", LocalDate.of(2000, 1, 1), null)));
        List<Gorev> mevcut = new ArrayList<>();
        int[] gunler = { 15, 20, 25, 31 };
        for (int i = 0; i < gunler.length; i++) {
            mevcut.add(yeniKimlik(OtomatikAtamaTest.gorev(0, "2026-12-" + gunler[i] + "T10:00", "2026-12-" + gunler[i] + "T12:00"), i + 1));
        }
        Gorev aday = yeniKimlik(OtomatikAtamaTest.gorev(0, "2026-11-30T22:00", "2026-12-01T02:00"), 9);
        if (!KuralMotoru.engelVarMi(KuralMotoru.degerlendirYazma(baglam(aday, gorunen(mevcut, aday, p), p)))) {
            List<Gorev> sonra = new ArrayList<>(mevcut);
            sonra.add(aday);
            assertFalse(ozelKirmizi(sonra, mevcut.get(3), p), "kabul edilen ay sonu gecesi görevi 31 Aralık görevini kırmızı yaptı");
        }
    }

    private static Gorev hazirlikli(String kalkis, String varis, int hazirlikDk, long id) {
        Gorev g = OtomatikAtamaTest.gorev(0, kalkis, varis);
        return new Gorev(id, g.kalkisGarId(), g.varisGarId(), g.hatId(), g.aracTipiId(), g.kalkis(), g.varis(), g.kalkis().minusSeconds(60L * hazirlikDk), g.bitis(), g.km());
    }

    /**
     * Kural kurucu Tur 3 (W, geri yön): hazırlık süresi adayın çalışma aralığını önceki güne uzatır (1 Oca 00:30 seferi 31 Ara 23:30'da başlar, 31 Ara 22:00 görevle aynı nöbet).
     * Geri yükleme ileri yükleme gibi gün - 1 / yıl için (gün - 2)'nin yılı olmalı; yoksa Mart görevi görünmez, aday kabul edilir ve 31 Ara görevi kırmızı olur.
     */
    @Test
    void yilBasiGecesi_hazirlikAdayiOncekiGuneUzatir_gerideYilGorevleriGorunur_adayReddedilir() {
        Parametreler p = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "10")));
        Gorev mart = yeniKimlik(OtomatikAtamaTest.gorev(0, "2026-03-10T08:00", "2026-03-10T17:00"), 1);
        Gorev y = yeniKimlik(OtomatikAtamaTest.gorev(0, "2026-12-31T22:00", "2026-12-31T23:00"), 2);
        Gorev x = hazirlikli("2027-01-01T00:30", "2027-01-01T03:00", 60, 9);
        List<Gorev> mevcut = List.of(mart, y);
        assertFalse(ozelKirmizi(mevcut, y, p), "ön koşul: aday yokken 31 Ara görevi kırmızı değil (yıl toplamı tam 10 s)");
        assertTrue(KuralMotoru.engelVarMi(KuralMotoru.degerlendirYazma(baglam(x, gorunen(mevcut, x, p), p))), "yılbaşı gecesi aday, geride kalan Mart görevini görüp reddedilmeli");
    }

    /** Aynısı ay için (K-15 KAPALI: yıl başı geri yüklemesi örtmez): 28 Şub 23:00 – 1 Mar 03:00 görevinin Mart payı 3 s, 10 Mar'da 6 s, 31 Mar'da 1 s; aday 1 Nis 00:30 (hazırlıkla 31 Mar 23:30). */
    @Test
    void ayBasiGecesi_k15Kapaliyken_hazirlikAdayiOncekiGuneUzatir_adayReddedilir() {
        java.util.Map<String, Siddet> siddet = new java.util.HashMap<>(TABAN.siddetler());
        siddet.put("K-15", Siddet.KAPALI);
        Parametreler p = Parametreler.olustur(TABAN.degerler(), siddet, List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "10")));
        Gorev subat = yeniKimlik(OtomatikAtamaTest.gorev(0, "2027-02-28T23:00", "2027-03-01T03:00"), 1);
        Gorev mart = yeniKimlik(OtomatikAtamaTest.gorev(0, "2027-03-10T08:00", "2027-03-10T14:00"), 2);
        Gorev y = yeniKimlik(OtomatikAtamaTest.gorev(0, "2027-03-31T22:00", "2027-03-31T23:00"), 3);
        Gorev x = hazirlikli("2027-04-01T00:30", "2027-04-01T02:00", 60, 9);
        List<Gorev> mevcut = List.of(subat, mart, y);
        assertFalse(ozelKirmizi(mevcut, y, p), "ön koşul: aday yokken 31 Mart görevi kırmızı değil (Mart toplamı tam 10 s)");
        assertTrue(KuralMotoru.engelVarMi(KuralMotoru.degerlendirYazma(baglam(x, gorunen(mevcut, x, p), p))), "ay başı gecesi aday, bir gün geride kalan 28 Şubat görevini görüp reddedilmeli");
    }

    @Test
    void yuklemePenceresi_ozelKuralYokkenAynen_kuraldanGenisler() {
        Parametreler bos = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of());
        assertEquals(15, BaglamYukleyici.pencereGun(bos)); // Faz 1 penceresi
        assertEquals(LocalDate.of(2026, 10, 27), ZamanHesabi.pencereSonu(LocalDate.of(2026, 10, 12), 15, bos.ozelYillik()));
        Parametreler kayan = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of(kayan(1, Olcu.TOPLAM_GOREV_SURESI, "30", 60)));
        assertEquals(60, BaglamYukleyici.pencereGun(kayan));
        Parametreler seri = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of(new OzelKural(2, "s", Olcu.ARDISIK_GECE_GUNU, Pencere.SERI,
                Karsilastirma.EN_FAZLA, new BigDecimal("20"), Kapsam.TUMU, Siddet.ENGEL, LocalDate.of(2000, 1, 1), null, "g", "k", 0)));
        assertEquals(21, BaglamYukleyici.pencereGun(seri)); // eşik + 1
        Parametreler yil = Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of(kural(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "20")));
        assertEquals(LocalDate.of(2026, 12, 31), ZamanHesabi.pencereSonu(LocalDate.of(2026, 10, 12), 15, yil.ozelYillik())); // yıl sonuna uzar
        assertEquals(LocalDate.of(2027, 12, 31), ZamanHesabi.pencereSonu(LocalDate.of(2026, 12, 30), 15, yil.ozelYillik())); // gün + 2 ertesi yıla düşer (gece yarısı taşması): o yılın sonu
        assertEquals(LocalDate.of(2027, 1, 4), ZamanHesabi.pencereSonu(LocalDate.of(2026, 12, 20), 15, yil.ozelYillik())); // gün + pencere yıl sonunu aşıyorsa o korunur
        assertEquals(31, BaglamYukleyici.pencereGun(Parametreler.olustur(TABAN.degerler(), TABAN.siddetler(), List.of(kural(4, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "20"))))); // ay sonu gece taşması: 30 değil 31
    }
}
