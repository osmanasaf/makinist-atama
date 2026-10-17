package com.makinistatama.atama;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

import com.makinistatama.atama.OtomatikAtama.Bos;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.MakinistBilgisi;

/**
 * Kadro doluluk eğrisi (F3a, MK-75/MK-76; OK-22 / MK-82 ile yeniden kuruldu): SAF (Spring/JPA/IO yok), DB'siz, BELİRLEYİCİ. Sanal makinistler (kimlik < 0, sicil
 * {@code SANAL-nnn}) bellekte mevcut kadroya eklenir; planlayıcı ({@link OtomatikAtama#planla}) fonksiyon olarak verilir. Sanal makinist varsayılan GENELdir (tarifedeki tüm
 * araç tipi ve hat bröveleri: kişi sayısı ölçüsü).
 * <p>
 * Eğri: tur 0 (mevcut kadro) boş seferlerinin kalkış garı dağılımından SABİT bir ekleme zinciri kurulur (en büyük bölüntü: ek makinist, boş talep dakikası en çok olan gara);
 * zincirin ön ekleri (ilk n kişi) birbirinden BAĞIMSIZ koşulardır. Önce basamak ölçeği (2, 4, 8, ...) paralel koşulur, sonra her hedef (kabul edilen boş sayısı) için
 * aralık k-bölmeyle daraltılır (en çok {@link #INCE_TUR} tur). Hangi n'lerin koşulacağı yalnız önceki sonuçlara bağlıdır (süreye ve iş parçacığı sayısına değil), bu yüzden
 * sonuç belirleyicidir. "En az makinist" kanıtlanmaz (K-18/K-19 tekdüze değil): raporlanan sayı yalnız KOŞULMUŞ ve hedefi sağlayan en küçük sayıdır.
 */
final class KadroArama {

    /** Sanal makinist grubu: bağlı gar (= boş seferin kalkış garı), araç tipi, hat (genel makinistte araç tipi ve hat 0). */
    record Grup(long garId, long aracTipiId, long hatId) implements Comparable<Grup> {
        @Override
        public int compareTo(Grup o) {
            return Comparator.comparingLong(Grup::garId).thenComparingLong(Grup::hatId).thenComparingLong(Grup::aracTipiId).compare(this, o);
        }
    }

    /** Aralık daraltma tur sınırı (her tur aralığı ~4'e böler: 64 -> 16 -> 4 -> 1). */
    static final int INCE_TUR = 4;
    /** Bir turda hedef başına en çok iç nokta (k-bölme). */
    static final int IC_NOKTA = 3;
    /**
     * Daraltma toleransı: (lo, hi] aralığı {@code max(1, ⌈%12 × hi⌉)} kişiye inince durulur (küçük sayılarda tam; +29 gibi sayılarda ±3-4). Eğri tekdüze değildir (başlangıç
     * tarihi kaymasında ±%30-50 oynar): tek kişi hassasiyeti sahte kesinliktir, koşu (dolayısıyla süre) sayısını ise yarıya indirir.
     */
    static final double TOLERANS = 0.12;

    /**
     * Aramanın değişmeyen bağlamı. {@code gercekSayisi}: sentetik izin sırası sanal makinistler için buradan devam eder. {@code genel} doluysa sanal makinist bu bröveleri taşır
     * (grup yalnız bağlı gar), {@code null} ise tek (araç tipi, hat) bröveli.
     */
    record Baglam(Map<Long, SeferGirdisi> seferler, LocalDate bas, LocalDate bit, int devamsizlikYuzde, int gercekSayisi, Broveler genel) {
    }

    /** OK-22: GENEL sanal makinist, tarifedeki TÜM araç tipi ve hat bröveleri (kişi sayısı ölçüsü). */
    record Broveler(Set<Long> aracTipleri, Set<Long> hatlar) {
    }

    private KadroArama() {
    }

    // ---------------------------------------------------------------- sanal makinist

    /**
     * Sanal makinist (MK-75): AKTIF, tek bağlı gar; genel ({@link Baglam#genel}) ise tarifedeki TÜM araç tipi ve hat bröveleri, değilse tek (araç tipi, hat) bröveli; belgeler
     * aralık sonrası. Bröve SINAV tarihi {@code bit}'tir: {@code sinav + sinav_gecerlilik_ay} ve K-07/K-08 "sürüş ara" (kayıt yoksa sınav tarihine düşer) parametre değeri ne
     * olursa olsun ihlal üretmez. {@code ekleSira} 0'dan; kimlik {@code −(ekleSira+1)}.
     */
    static MakinistGirdisi sanal(int ekleSira, Grup g, Baglam b) {
        long id = -(ekleSira + 1L);
        LocalDate belge = b.bit().plusYears(1);
        Map<Long, LocalDate> arac = new HashMap<>();
        Map<Long, MakinistBilgisi.HatBrove> hat = new HashMap<>();
        for (long a : b.genel() == null ? Set.of(g.aracTipiId()) : b.genel().aracTipleri()) {
            arac.put(a, b.bit());
        }
        for (long h : b.genel() == null ? Set.of(g.hatId()) : b.genel().hatlar()) {
            hat.put(h, new MakinistBilgisi.HatBrove(b.bit(), null));
        }
        MakinistBilgisi bilgi = new MakinistBilgisi(id, g.garId(), MakinistBilgisi.Durum.AKTIF, belge, belge, belge, arac, hat, null, List.of());
        return new MakinistGirdisi(id, String.format("SANAL-%03d", ekleSira + 1), izinli(bilgi, b.gercekSayisi() + ekleSira, b.bas(), b.bit(), b.devamsizlikYuzde()), List.of(),
                Map.of(), null, Map.of());
    }

    /**
     * Sentetik izin (FR-06): kişi başı {@code ceil(p·N) − aralıktaki ONAY izin günü} (alt sınır 0) ardışık gün, tek blok (aralık sonunda başa sarar); başlangıç ofseti {@code (sıra · g) mod N}.
     * Kayık dağılım: herhangi bir günde devamsız sayısı yaklaşık p × kadro. Bir makinist eklenince/çıkarılınca diğerlerinin bloğu DEĞİŞMEZ (sıra sabit).
     */
    static MakinistBilgisi izinli(MakinistBilgisi b, int sira, LocalDate bas, LocalDate bit, int devamsizlikYuzde) {
        int n = (int) ChronoUnit.DAYS.between(bas, bit) + 1;
        int hedef = (int) Math.ceil(devamsizlikYuzde * n / 100.0 - 1e-9);
        int var = 0;
        for (int i = 0; i < n; i++) {
            LocalDate gun = bas.plusDays(i);
            if (b.izinler().stream().anyMatch(z -> z.durum() == MakinistBilgisi.IzinDurumu.ONAY && !gun.isBefore(z.baslangic()) && !gun.isAfter(z.bitis()))) {
                var++;
            }
        }
        int g = Math.min(n, Math.max(0, hedef - var));
        if (g == 0) {
            return b;
        }
        int ofset = (int) ((long) sira * g % n);
        List<MakinistBilgisi.Izin> izinler = new ArrayList<>(b.izinler());
        int son = ofset + g - 1;
        izinler.add(new MakinistBilgisi.Izin(bas.plusDays(ofset), bas.plusDays(Math.min(son, n - 1)), MakinistBilgisi.IzinDurumu.ONAY));
        if (son >= n) { // aralığın sonunu aşan blok başa sarar: kenar günleri de aynı yoğunlukta devamsız olur
            izinler.add(new MakinistBilgisi.Izin(bas, bas.plusDays(son - n), MakinistBilgisi.IzinDurumu.ONAY));
        }
        return new MakinistBilgisi(b.id(), b.bagliGarId(), b.durum(), b.ehliyetBitis(), b.saglikBitis(), b.psikoteknikBitis(), b.aracTipiSinavlari(), b.hatBroveleri(),
                b.kartSonSurus(), izinler);
    }

    /** Mevcut kadro + ilk {@code adet} eklenen (zincir sırasıyla, kimlikler sıraya göre sabit). */
    static List<MakinistGirdisi> kadro(List<MakinistGirdisi> mevcut, List<Grup> eklenen, int adet, Baglam b) {
        List<MakinistGirdisi> kadro = new ArrayList<>(mevcut);
        for (int i = 0; i < adet; i++) {
            kadro.add(sanal(i, eklenen.get(i), b));
        }
        return kadro;
    }

    // ---------------------------------------------------------------- doluluk eğrisi

    /**
     * Eğri sonucu: {@code noktalar} koşulmuş her ön ek uzunluğu -> plan (0 = mevcut kadro); {@code zincir} ekleme sırası; {@code hedefAdet} kabul edilen boş sayısı -> hedefi
     * sağlayan en küçük KOŞULMUŞ uzunluk ({@code null} = zincirin sonuna kadar hiçbir koşulmuş noktada sağlanmadı); {@code kosuSayisi} planlayıcı çağrısı.
     */
    record Egri(Map<Integer, Sonuc> noktalar, List<Grup> zincir, Map<Integer, Integer> hedefAdet, int kosuSayisi) {
    }

    static int say(Sonuc s) {
        return s.bosKalanlar().size();
    }

    /**
     * @param planlayici kadro -> plan (bütçe yok; EŞZAMANLI çağrılabilir: planla saftır ve kadro listelerini değiştirmez)
     * @param hedefBos   kabul edilen boş sayıları
     * @param enCokEk    zincir uzunluğu (denenecek en çok sanal makinist)
     * @param havuz      bağımsız koşuların çalışacağı havuz (tek iş parçacıklı havuz sonucu değiştirmez)
     */
    static Egri egri(List<MakinistGirdisi> mevcut, Baglam b, BiFunction<List<MakinistGirdisi>, Long, Sonuc> planlayici, List<Integer> hedefBos, int enCokEk, ExecutorService havuz) {
        Map<Integer, Sonuc> noktalar = new TreeMap<>();
        int[] kosu = { 1 };
        Sonuc tur0 = planlayici.apply(mevcut, 0L);
        noktalar.put(0, tur0);
        List<Grup> zincir = say(tur0) == 0 ? List.of() : zincir(tur0, b, enCokEk);
        int ust = zincir.size();
        // 1. basamak ölçeği: 2, 4, 8, ... ve zincirin sonu
        TreeSet<Integer> basamak = new TreeSet<>();
        for (int n = 2; n < ust; n *= 2) {
            basamak.add(n);
        }
        if (ust > 0) {
            basamak.add(ust);
        }
        kos(basamak, mevcut, zincir, b, planlayici, noktalar, kosu, havuz);
        // 2. her hedef için (lo, hi] aralığını k-bölmeyle daralt
        for (int tur = 0; tur < INCE_TUR; tur++) {
            TreeSet<Integer> yeni = new TreeSet<>();
            for (int hedef : hedefBos) {
                int hi = enKucukSaglayan(noktalar, hedef);
                if (hi <= 0) {
                    continue; // sağlanmıyor ya da mevcut kadro zaten sağlıyor
                }
                int lo = 0;
                for (Map.Entry<Integer, Sonuc> e : noktalar.entrySet()) {
                    if (e.getKey() < hi && say(e.getValue()) > hedef) {
                        lo = Math.max(lo, e.getKey());
                    }
                }
                int aralik = hi - lo;
                if (aralik <= Math.max(1, (int) Math.ceil(TOLERANS * hi))) {
                    continue;
                }
                for (int j = 1; j <= IC_NOKTA; j++) {
                    int n = lo + (int) Math.round(j * aralik / (double) (IC_NOKTA + 1));
                    if (n > lo && n < hi && !noktalar.containsKey(n)) {
                        yeni.add(n);
                    }
                }
            }
            if (yeni.isEmpty()) {
                break;
            }
            kos(yeni, mevcut, zincir, b, planlayici, noktalar, kosu, havuz);
        }
        Map<Integer, Integer> hedefAdet = new LinkedHashMap<>();
        for (int hedef : hedefBos) {
            int hi = enKucukSaglayan(noktalar, hedef);
            hedefAdet.put(hedef, hi < 0 ? null : hi);
        }
        return new Egri(noktalar, zincir, hedefAdet, kosu[0]);
    }

    /** Koşulmuş noktalar içinde boşu {@code hedef} ve altında olan EN KÜÇÜK uzunluk; yoksa −1. */
    private static int enKucukSaglayan(Map<Integer, Sonuc> noktalar, int hedef) {
        for (Map.Entry<Integer, Sonuc> e : noktalar.entrySet()) { // TreeMap: artan uzunluk
            if (say(e.getValue()) <= hedef) {
                return e.getKey();
            }
        }
        return -1;
    }

    /** {@code uzunluklar}'ın henüz koşulmamış olanlarını havuzda bağımsız koşar; sonuçlar uzunluk sırasıyla eklenir. */
    private static void kos(Set<Integer> uzunluklar, List<MakinistGirdisi> mevcut, List<Grup> zincir, Baglam b, BiFunction<List<MakinistGirdisi>, Long, Sonuc> planlayici,
            Map<Integer, Sonuc> noktalar, int[] kosu, ExecutorService havuz) {
        List<Integer> yapilacak = uzunluklar.stream().filter(n -> !noktalar.containsKey(n)).toList();
        List<Callable<Sonuc>> isler = new ArrayList<>();
        for (int n : yapilacak) {
            isler.add(() -> planlayici.apply(kadro(mevcut, zincir, n, b), 0L));
        }
        try {
            List<Future<Sonuc>> sonuclar = havuz.invokeAll(isler);
            for (int i = 0; i < yapilacak.size(); i++) {
                noktalar.put(yapilacak.get(i), sonuclar.get(i).get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kadro hesabı kesildi", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
        }
        kosu[0] += yapilacak.size();
    }

    /**
     * Ekleme zinciri: mevcut kadro planında boş kalan seferlerin gruplarının (genel makinistte yalnız kalkış garı) boş talep dakikasıyla ORANTILI en büyük bölüntü (D'Hondt):
     * her sıradaki sanal makinist {@code dakika / (şimdiye kadarki kişi + 1)} en büyük gruba gider (eşitte küçük grup). İlk n eleman n kişilik dağılımdır.
     */
    static List<Grup> zincir(Sonuc tur0, Baglam b, int uzunluk) {
        Map<Grup, Long> dakika = new TreeMap<>();
        for (Bos bos : tur0.bosKalanlar()) {
            SeferGirdisi s = b.seferler().get(bos.seferId());
            dakika.merge(b.genel() != null ? new Grup(s.gorev().kalkisGarId(), 0, 0) : new Grup(s.gorev().kalkisGarId(), s.gorev().aracTipiId(), s.gorev().hatId()), s.dakika(),
                    Long::sum);
        }
        Map<Grup, Integer> kisi = new HashMap<>();
        List<Grup> zincir = new ArrayList<>(uzunluk);
        for (int i = 0; i < uzunluk; i++) {
            Grup en = null;
            double enDeger = -1;
            for (Map.Entry<Grup, Long> e : dakika.entrySet()) { // TreeMap: artan grup (eşitte küçük grup kazanır)
                double deger = e.getValue() / (kisi.getOrDefault(e.getKey(), 0) + 1.0);
                if (deger > enDeger) {
                    en = e.getKey();
                    enDeger = deger;
                }
            }
            kisi.merge(en, 1, Integer::sum);
            zincir.add(en);
        }
        return zincir;
    }
}
