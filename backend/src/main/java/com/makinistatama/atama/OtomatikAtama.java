package com.makinistatama.atama;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.NobetHesabi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;

/**
 * Otomatik atama çekirdeği (FR-06, MK-8): SAF (Spring/JPA/IO yok), DB'siz, BELİRLEYİCİ tek döngü; optimizasyon/Strategy yok. Seferler kalkış sırasıyla
 * (eşitse sefer kimliği) gezilir; her sefer için tüm makinistler kural motoruyla değerlendirilir (aday TÜM komşu görevlerle, iki yönlü); hiç
 * ihlali olmayanlar (ENGEL de UYARI da) arasından bu ay en az çalışmış (saat, eşitse km, eşitse sicil) seçilir ve görev listesi/aylık toplam
 * bellekte güncellenir. Yalnız ASIL doldurulur. Kimse uygun değilse sefer BOŞ kalır ve nedeni özetlenir ("0/3 uygun: hat yetkisi 1, izin 1, ...").
 * Önizleme ile uygulama aynı fonksiyonu çağırır.
 */
public final class OtomatikAtama {

    /**
     * Doldurulacak sefer. {@code ay}: "bu ay" toplamının ait olduğu planlanan kalkış ayı. {@code oncekiParca} / {@code sonrakiParca} (F2d Tur 3, W1): aynı trenin bitişik parçası da bu
     * çalışmada doldurulacaksa onun sefer kimliği (yoksa null); aynı makinistte ikisi birden yerleşirse aralarında hazırlık / alıkoyma uygulanmaz (OK-17).
     */
    public record SeferGirdisi(long seferId, LocalDate gun, YearMonth ay, Gorev gorev, Long oncekiParca, Long sonrakiParca) {
        public SeferGirdisi(long seferId, LocalDate gun, YearMonth ay, Gorev gorev) {
            this(seferId, gun, ay, gorev, null, null);
        }

        long dakika() {
            return gorev.sureDakika(); // çalışma aralığı: adalet/puantajla aynı tanım (OK-2)
        }
    }

    /**
     * Makinist: {@code gorevler} mevcut tüm görevleri (herhangi sırada; planlama penceresini kendisi uygular), {@code aylik} planlanan ay ->
     * {saat, km} başlangıç toplamlarıdır (içeride tam sayı dakika / 0,1 km olarak tutulur: bağ kırıcı kayan nokta gürültüsünden etkilenmez).
     */
    public record MakinistGirdisi(long id, String sicil, MakinistBilgisi bilgi, List<Gorev> gorevler, Map<Long, LocalDate> hatSon, LocalDate genelSon,
            Map<YearMonth, double[]> aylik) {
    }

    /** {@code yolculukSeferId} (F2b, MK-42): doluysa makinist önce bu seferde YOLCU olarak gider, sonra {@code seferId}'e ASIL atanır. */
    public record Atandi(long seferId, long makinistId, Long yolculukSeferId) {
        public Atandi(long seferId, long makinistId) {
            this(seferId, makinistId, null);
        }
    }

    /**
     * OK-17 (F2d tur 2): makinist aday seferin zincir komşusunu zaten taşıyorsa (aday henüz atanmamış), aday görev (komşu yönünde hazırlık / alıkoyma yok) ve komşu parçaların
     * yeni görevleri. Saf veri; üretimi {@code BaglamYukleyici.Yuk.varyant}.
     */
    public record ZincirVaryanti(Gorev aday, List<Gorev> komsular) {
    }

    /** {@code neden}: "0/3 uygun: hat yetkisi 1, ..." özeti; {@code ornekler}: en sık nedenlerden birer örnek ihlal metni. */
    public record Bos(long seferId, String neden, List<String> ornekler) {
    }

    /** {@code yolculukAramaSayisi}: yolculuk aşamasında makinist başına yapılan arama sayısı (ölçüm; aşama kapalıyken 0). */
    public record Sonuc(List<Atandi> atananlar, List<Bos> bosKalanlar, int yolculukAramaSayisi) {
        public Sonuc(List<Atandi> atananlar, List<Bos> bosKalanlar) {
            this(atananlar, bosKalanlar, 0);
        }
    }

    private static final Comparator<Gorev> SIRA = Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId);
    private static final int EN_COK_ORNEK = 3;
    // ponytail: makinist başına en çok 3 yolculuk adayı denenir (NFR-02 bütçesi); 1'e indirmek ölçek aşımında ilk gevşetmedir (MK-42 aşım sırası)
    private static final int EN_COK_YOLCULUK_ADAYI = 3;
    // ponytail: boş kalanlar en çok bir kez yeniden denenir (NFR-02 bütçesi); ilerleme bitene dek döngü 8 ay+yıl kuralı ölçeğinde 30 sn bütçesini aşıyordu
    private static final int EN_COK_GECIS = 2;
    /** Yolculuk aşamasında aday sırası: bu ay dakika, km, sicil (kalkış garında olma burada anlamsız: K-20 adayları zaten orada değil). İki yolda (tembel / eski) aynı. */
    private static final Comparator<Aday> ASAMA_SIRA = Comparator.comparingLong((Aday a) -> a.toplam()[0]).thenComparingLong(a -> a.toplam()[1])
            .thenComparing(a -> a.durum().girdi.sicil());

    /** Bir makinistin planlama sırasındaki değişen durumu. */
    private static final class Durum {
        final MakinistGirdisi girdi;
        final ArrayList<Gorev> sirali;
        final Set<Long> seferler = new HashSet<>();
        /** Planlanan ay -> {dakika, km × 10} (tam sayı: toplama sırası sonucu değiştirmez; INFO-3). */
        final Map<YearMonth, long[]> aylik = new HashMap<>();
        /** Bu çalışmada yerleştirilen asıl seferler (MK-67 kapısı: öncelikli sırada sonraki plan görevleri); yolculuk görevleri girmez. */
        final Map<Long, SeferGirdisi> plan = new HashMap<>();

        Durum(MakinistGirdisi g) {
            this.girdi = g;
            this.sirali = new ArrayList<>(g.gorevler());
            this.sirali.sort(SIRA);
            g.gorevler().forEach(x -> seferler.add(x.seferId()));
            g.aylik().forEach((k, v) -> aylik.put(k, new long[] { Math.round(v[0] * 60), Math.round(v[1] * 10) }));
        }

        /** Listedeki mevcut görev (varyantla değişmiş olabilir). */
        Gorev gorev(long seferId) {
            return sirali.stream().filter(g -> g.seferId() == seferId).findFirst().orElseThrow();
        }

        long[] ay(YearMonth ay) {
            return aylik.computeIfAbsent(ay, k -> new long[2]);
        }

        /**
         * INFO-1: makinist seferin kalkış garında mı: önceki görevi (başlangıca göre sıralı {@code diger} penceresinde) orada bitmiş; önceki görevi yoksa bağlı garı
         * orası (K-20 de geçmişsiz makinisti engellemez, ev garında sayılır).
         */
        boolean yerinde(List<Gorev> diger, Gorev sefer) {
            for (int i = diger.size() - 1; i >= 0; i--) {
                if (diger.get(i).baslangic().isBefore(sefer.baslangic())) {
                    return diger.get(i).varisGarId() == sefer.kalkisGarId();
                }
            }
            return girdi.bilgi().bagliGarId() == sefer.kalkisGarId();
        }

        /**
         * {@link #yerinde} ile aynı sonuç, pencere listesini kopyalamadan: seferden önce başlayan son görev ({@code alt}'tan önceyse pencerede sayılmaz) ya da bağlı gar.
         * Tembel doğrulamada tercih sırası kural motorundan ÖNCE kurulur.
         */
        boolean yerindeHizli(Gorev sefer, Instant alt) {
            int onceki = ilkIndeks(sefer.baslangic()) - 1;
            return onceki >= ilkIndeks(alt) ? sirali.get(onceki).varisGarId() == sefer.kalkisGarId() : girdi.bilgi().bagliGarId() == sefer.kalkisGarId();
        }

        /** Penceredeki görevler: başlangıcı [alt, ust) aralığında olanlar. */
        List<Gorev> pencere(Instant alt, Instant ust) {
            int lo = ilkIndeks(alt);
            int hi = ilkIndeks(ust);
            return new ArrayList<>(sirali.subList(lo, Math.max(lo, hi)));
        }

        private int ilkIndeks(Instant an) {
            int a = 0;
            int b = sirali.size();
            while (a < b) {
                int o = (a + b) >>> 1;
                if (sirali.get(o).baslangic().isBefore(an)) {
                    a = o + 1;
                } else {
                    b = o;
                }
            }
            return a;
        }

        /** Yolculuk: görev listesine, görev kümesine ve aylık dakikaya girer (saat {@code sureDakika()}, km 0). */
        void ekleYolculuk(YolculukOnerisi.YolculukAdayi y) {
            int yer = Collections.binarySearch(sirali, y.gorev(), SIRA);
            sirali.add(yer < 0 ? -yer - 1 : yer, y.gorev());
            seferler.add(y.seferId());
            ay(YearMonth.from(y.gun()))[0] += y.gorev().sureDakika();
        }

        /** OK-17 varyantlı ekleme: aday görev varyantla eklenir, komşu parçalar yeni görevleriyle değiştirilir (aylık toplam adayın görev süresiyle). */
        void ekle(SeferGirdisi s, ZincirVaryanti v) {
            if (v == null) {
                ekle(s);
                return;
            }
            for (Gorev yeniKomsu : v.komsular()) {
                for (int i = 0; i < sirali.size(); i++) {
                    if (sirali.get(i).seferId() == yeniKomsu.seferId()) {
                        sirali.remove(i);
                        break;
                    }
                }
                int yer = Collections.binarySearch(sirali, yeniKomsu, SIRA);
                sirali.add(yer < 0 ? -yer - 1 : yer, yeniKomsu);
            }
            int yer = Collections.binarySearch(sirali, v.aday(), SIRA);
            sirali.add(yer < 0 ? -yer - 1 : yer, v.aday());
            seferler.add(s.seferId());
            plan.put(s.seferId(), s);
            long[] t = ay(s.ay());
            t[0] += v.aday().sureDakika();
            t[1] += Math.round(s.gorev().km() * 10);
        }

        void ekle(SeferGirdisi s) {
            int yer = Collections.binarySearch(sirali, s.gorev(), SIRA);
            sirali.add(yer < 0 ? -yer - 1 : yer, s.gorev());
            seferler.add(s.seferId());
            plan.put(s.seferId(), s);
            long[] t = ay(s.ay());
            t[0] += s.dakika();
            t[1] += Math.round(s.gorev().km() * 10);
        }

        /** Varyantlı {@link #ekle(SeferGirdisi, ZincirVaryanti)}'in tersi: liste ekleme öncesi kopyasına döner (komşu parçalar dahil). */
        void cikar(SeferGirdisi s, ZincirVaryanti v, List<Gorev> yedek) {
            if (v == null) {
                cikar(s);
                return;
            }
            sirali.clear();
            sirali.addAll(yedek);
            seferler.remove(s.seferId());
            plan.remove(s.seferId());
            long[] t = ay(s.ay());
            t[0] -= v.aday().sureDakika();
            t[1] -= Math.round(s.gorev().km() * 10);
        }

        /** {@link #ekle}'nin tam tersi (MK-67 kapısı geri alır): liste, küme, plan ve aylık toplam eskisine döner. */
        void cikar(SeferGirdisi s) {
            sirali.remove(Collections.binarySearch(sirali, s.gorev(), SIRA));
            seferler.remove(s.seferId());
            plan.remove(s.seferId());
            long[] t = ay(s.ay());
            t[0] -= s.dakika();
            t[1] -= Math.round(s.gorev().km() * 10);
        }
    }

    private OtomatikAtama() {
    }

    /**
     * @param pencereGun K-18'in iki yönde baktığı pencere ({@code max(15, 7 × (gece.ard_hafta_max + 1))})
     * @param yillik     K-15 açık mı (pencerenin geri sınırı yıl başına uzar)
     * @param simdi      K-22 (bitmiş sefere atama) için; null ise denetlenmez
     */
    public static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Map<Long, String> adlar, Parametreler p, Instant simdi,
            int pencereGun, boolean yillik) {
        return planla(seferler, makinistler, adlar, p, simdi, pencereGun, yillik, true);
    }

    /**
     * @param tembel true: aday sırası önce ucuz tercih anahtarıyla (kalkış garında mı, bu ay dakika, km, sicil) kurulur ve kural motoru yalnız sırayla İLK ihlalsiz
     *               çıkana kadar çağrılır (kazanan o; tercih sırası tam sıralı olduğundan seçim, tümünü değerlendirip en iyiyi seçmekle özdeştir). false: eski yol, hepsi
     *               değerlendirilir ve en iyisi seçilir (yalnız eşdeğerlik testinin referansı). İkisi de kimse uygun değilse tüm adayları değerlendirmiş olur.
     */
    static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Map<Long, String> adlar, Parametreler p, Instant simdi,
            int pencereGun, boolean yillik, boolean tembel) {
        return planla(seferler, makinistler, adlar, p, simdi, pencereGun, yillik, tembel, List.of(), 0, 0);
    }

    /**
     * Yolculuk aşamalı (F2b, MK-42): {@code yolculukAdaylari} boş değilse, hiçbir makinist ihlalsiz çıkmayan seferde, ihlal kümesi TAM {@code {K-20}} olan makinistler (yolculuk yalnız
     * konumu değiştirir: kayıpsız budama, {@code elenenler} zaten elde) sırayla denenir; ilk (YOLCU + ASIL) çifti tüm kurallardan ihlalsiz geçen makinist seçilir. Liste boşsa aşama
     * HİÇ çalışmaz (eski kod yolu, çıktı birebir aynı). 
     */
    public static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Map<Long, String> adlar, Parametreler p, Instant simdi, int pencereGun,
            boolean yillik, List<YolculukOnerisi.YolculukAdayi> yolculukAdaylari, int aktarmaDk, int aramaSaat) {
        return planla(seferler, makinistler, adlar, p, simdi, pencereGun, yillik, true, yolculukAdaylari, aktarmaDk, aramaSaat);
    }

    static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Map<Long, String> adlar, Parametreler p, Instant simdi, int pencereGun,
            boolean yillik, boolean tembel, List<YolculukOnerisi.YolculukAdayi> yolculukAdaylari, int aktarmaDk, int aramaSaat) {
        return planla(seferler, makinistler, adlar, p, simdi, pencereGun, yillik, tembel, yolculukAdaylari, aktarmaDk, aramaSaat, Map.of(), 0);
    }

    /**
     * İyileştirme (F2d, MK-66/67): {@code oncelik} sefer kimliği -> ağırlık; sefer sırası anahtarı {@code kalkış − 60 s × kaydirDk × ağırlık} (eşitte sefer kimliği). Planda olmayan
     * kimlikler yok sayılır; kalan harita boşsa (ya da kaydırma 0 ise) anahtar kalkıştır, yani sıra ve çıktı {@code oncelik}'siz çağrıyla BİREBİR aynıdır. Dolu ve kaydırma > 0 iken kronolojik sıra bozulduğundan her
     * yerleştirme, aynı makinistin bu çalışmada yerleştirilmiş SONRAKİ görevlerinden birini kırmızı yapmıyor mu diye ayrıca denenir (MK-5). Yolculuk ve eski (tembel=false)
     * yolla birlikte desteklenmez.
     */
    static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Map<Long, String> adlar, Parametreler p, Instant simdi, int pencereGun,
            boolean yillik, boolean tembel, List<YolculukOnerisi.YolculukAdayi> yolculukAdaylari, int aktarmaDk, int aramaSaat, Map<Long, Integer> oncelik, long kaydirDk) {
        return planla(seferler, makinistler, adlar, p, simdi, pencereGun, yillik, tembel, yolculukAdaylari, aktarmaDk, aramaSaat, oncelik, kaydirDk, Map.of());
    }

    /** {@code zincir}: makinist -> sefer -> {@link ZincirVaryanti} (OK-17; boşsa eski davranış birebir). */
    static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Map<Long, String> adlar, Parametreler p, Instant simdi, int pencereGun,
            boolean yillik, boolean tembel, List<YolculukOnerisi.YolculukAdayi> yolculukAdaylari, int aktarmaDk, int aramaSaat, Map<Long, Integer> oncelik, long kaydirDk,
            Map<Long, Map<Long, ZincirVaryanti>> zincir) {
        Map<Long, Integer> onc = new HashMap<>();
        for (SeferGirdisi s : seferler) {
            Integer w = oncelik.get(s.seferId());
            if (w != null && w > 0) {
                onc.put(s.seferId(), w);
            }
        }
        boolean kapi = !onc.isEmpty() && kaydirDk > 0;
        if (kapi && (!tembel || !yolculukAdaylari.isEmpty())) {
            throw new IllegalArgumentException("Öncelik yolculuk aşaması ya da eski değerlendirme yoluyla birlikte kullanılamaz");
        }
        Map<Long, List<YolculukOnerisi.YolculukAdayi>> varisGaraGore = new HashMap<>(); // aşama: hedefin kalkış garına varan seferler
        for (YolculukOnerisi.YolculukAdayi y : yolculukAdaylari) {
            varisGaraGore.computeIfAbsent(y.gorev().varisGarId(), k -> new ArrayList<>()).add(y);
        }
        int yolculukAramaSayisi = 0;
        List<SeferGirdisi> sirali = new ArrayList<>(seferler);
        sirali.sort(Comparator.comparing((SeferGirdisi s) -> s.gorev().kalkis().minusSeconds(60L * kaydirDk * onc.getOrDefault(s.seferId(), 0)))
                .thenComparingLong(SeferGirdisi::seferId));
        List<Durum> durumlar = makinistler.stream().sorted(Comparator.comparing(MakinistGirdisi::sicil)).map(Durum::new).toList();

        List<Atandi> atananlar = new ArrayList<>();
        List<Bos> bosKalanlar = new ArrayList<>();
        List<SeferGirdisi> kuyruk = sirali;
        Map<Long, SeferGirdisi> seferGirdisi = new HashMap<>();
        sirali.forEach(g -> seferGirdisi.put(g.seferId(), g));
        for (int gecis = 1; ; gecis++) {
            int onceki = atananlar.size();
            bosKalanlar.clear();
            for (SeferGirdisi s : kuyruk) {
                Instant alt = ZamanHesabi.pencereBasi(s.gun(), pencereGun, yillik).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
                Instant ust = ZamanHesabi.pencereSonu(s.gun(), pencereGun, p.ozelYillik()).plusDays(1).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
                List<List<Ihlal>> elenenler = new ArrayList<>(Collections.nCopies(durumlar.size(), null)); // makinist sırasında (NedenOzeti için)
                List<Aday> adaylar = new ArrayList<>(durumlar.size());
                for (int i = 0; i < durumlar.size(); i++) {
                    Durum d = durumlar.get(i);
                    if (d.seferler.contains(s.seferId())) { // bu seferde zaten (ör. YARDIMCI) görevli
                        elenenler.set(i, List.of(new Ihlal("K-10", Siddet.ENGEL, 0, 0, "", "Makinist bu seferde zaten görevli")));
                    } else {
                        adaylar.add(new Aday(i, d, d.ay(s.ay()), tembel ? d.yerindeHizli(s.gorev(), alt) : false));
                    }
                }
                if (tembel) {
                    adaylar.sort(TERCIH);
                }
                Aday secilen = null;
                YolculukOnerisi.YolculukAdayi secilenYolculuk = null;
                for (Aday a : adaylar) {
                    List<Gorev> diger = a.durum().pencere(alt, ust);
                    ZincirVaryanti varyant = varyant(a.durum(), s, zincir);
                    if (varyant != null) {
                        diger = komsulariDegistir(diger, varyant);
                    }
                    List<Ihlal> ihlaller = KuralMotoru.degerlendirYazma(new KuralBaglami(varyant == null ? s.gorev() : varyant.aday(), diger, a.durum().girdi.bilgi(), a.durum().girdi.hatSon(),
                            a.durum().girdi.genelSon(), adlar, p, simdi));
                    if (!ihlaller.isEmpty()) { // ENGEL de UYARI da elenir: otomatik atama yalnız kurallara tam uyanı seçer
                        elenenler.set(a.indeks(), ihlaller);
                        continue;
                    }
                    if (tembel) {
                        if (kapi) { // MK-67: bu yerleştirme aynı makinistin sonraki plan görevlerinden birini kırmızı yapıyorsa aday elenir
                            List<Ihlal> sonraki = sonrakiKirmizi(a.durum(), s, varyant, adlar, p, pencereGun, yillik);
                            if (!sonraki.isEmpty()) {
                                elenenler.set(a.indeks(), sonraki);
                                continue;
                            }
                        }
                        secilen = a;
                        break;
                    }
                    // eski yol: seferin kalkış garındaki makinist, başka garda olandan önce gelir (INFO-1); aynı grupta adalet sırası
                    Aday tam = new Aday(a.indeks(), a.durum(), a.toplam(), a.durum().yerinde(diger, s.gorev()));
                    if (secilen == null || TERCIH.compare(tam, secilen) < 0) {
                        secilen = tam;
                    }
                }
                List<YolculukOnerisi.YolculukAdayi> garaVaranlar = varisGaraGore.get(s.gorev().kalkisGarId());
                if (secilen == null && garaVaranlar != null) {
                    List<Aday> k20 = new ArrayList<>();
                    for (Aday a : adaylar) {
                        if (yalnizK20(elenenler.get(a.indeks()))) {
                            k20.add(a);
                        }
                    }
                    k20.sort(ASAMA_SIRA);
                    for (Aday a : k20) {
                        yolculukAramaSayisi++;
                        MakinistGirdisi g = a.durum().girdi;
                        List<YolculukOnerisi.Oneri> bulunan = YolculukOnerisi.oner(a.durum().sirali, new YolculukOnerisi.MakinistBaglami(g.bilgi(), g.hatSon(), g.genelSon(), adlar),
                                s.gorev(), s.gun(), garaVaranlar, p, simdi, aktarmaDk, aramaSaat, true, EN_COK_YOLCULUK_ADAYI, true, pencereGun, yillik);
                        if (!bulunan.isEmpty()) {
                            long yolculukSefer = bulunan.getFirst().seferId();
                            secilenYolculuk = garaVaranlar.stream().filter(y -> y.seferId() == yolculukSefer).findFirst().orElseThrow();
                            secilen = a;
                            break;
                        }
                    }
                }
                if (secilen != null) {
                    if (secilenYolculuk != null) {
                        secilen.durum().ekleYolculuk(secilenYolculuk);
                    }
                    secilen.durum().ekle(s, varyant(secilen.durum(), s, zincir));
                    atananlar.add(new Atandi(s.seferId(), secilen.durum().girdi.id(), secilenYolculuk == null ? null : secilenYolculuk.seferId()));
                } else {
                    List<String> ilkKodlar = NedenOzeti.birincil(elenenler);
                    Map<String, List<String>> metinler = new LinkedHashMap<>();
                    for (int i = 0; i < ilkKodlar.size(); i++) {
                        String kod = ilkKodlar.get(i);
                        metinler.computeIfAbsent(kod, k -> new ArrayList<>()).add(elenenler.get(i).stream().filter(x -> x.kod().equals(kod)).findFirst().orElseThrow().metin());
                    }
                    bosKalanlar.add(new Bos(s.seferId(), NedenOzeti.ozet(durumlar.size(), ilkKodlar, p), ornekler(ilkKodlar, metinler, p)));
                }
            }
            // F2b: tek geçişli greedy, yolculuk aşamasında sonradan oluşan duruma göre önceki geçişte boş kalanı doldurabilir; ikinci "uygula" yeni satır üretmesin diye
            // (idempotent) aşama açıkken boş kalanlar aynı bellek durumunda bir kez daha denenir ({@link #EN_COK_GECIS}). Kapalıyken tek geçiş (eski çıktı birebir).
            if (gecis >= EN_COK_GECIS || yolculukAdaylari.isEmpty() || bosKalanlar.isEmpty() || atananlar.size() == onceki) {
                break;
            }
            kuyruk = bosKalanlar.stream().map(b -> seferGirdisi.get(b.seferId())).toList();
        }
        return new Sonuc(atananlar, bosKalanlar, yolculukAramaSayisi);
    }

    /**
     * MK-67 (öncelikli sıra): {@code yeni} makinistin görev listesine geçici eklenir; bu çalışmada yerleştirilmiş ve {@link #SIRA}'da yeniden sonra gelen her görev
     * {@code KirmiziAtamaHesabi} tanımıyla (sorumlu komşular, {@code simdi=null}) denenir. Dönüş: ilk kırmızı olan görevin ENGEL ihlalleri; boşsa kırmızı yok. Durum her
     * hâlükârda eskisine döner (kalıcı ekleme çağıranda).
     */
    private static List<Ihlal> sonrakiKirmizi(Durum d, SeferGirdisi yeni, ZincirVaryanti v, Map<Long, String> adlar, Parametreler p, int pencereGun, boolean yillik) {
        List<Gorev> yedek = v == null ? null : new ArrayList<>(d.sirali); // OK-17 varyantı komşu parçaları da değiştirir; geri alma listeyi geri yükler
        d.ekle(yeni, v);
        Gorev eklenen = v == null ? yeni.gorev() : v.aday();
        try {
            for (int i = d.ilkIndeks(eklenen.baslangic()); i < d.sirali.size(); i++) {
                Gorev x = d.sirali.get(i);
                SeferGirdisi plan = d.plan.get(x.seferId());
                if (plan == null || SIRA.compare(x, eklenen) <= 0) {
                    continue;
                }
                Instant alt = ZamanHesabi.pencereBasi(plan.gun(), pencereGun, yillik).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
                Instant ust = ZamanHesabi.pencereSonu(plan.gun(), pencereGun, p.ozelYillik()).plusDays(1).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
                List<Gorev> diger = d.pencere(alt, ust).stream().filter(g -> g.seferId() != x.seferId()).toList();
                List<Gorev> sorumlu = NobetHesabi.sorumluKomsular(diger, x, p.dakika("nobet.ayirma_saat"));
                List<Ihlal> ihlaller = KuralMotoru.degerlendir(new KuralBaglami(x, sorumlu, d.girdi.bilgi(), d.girdi.hatSon(), d.girdi.genelSon(), adlar, p, null));
                if (KuralMotoru.engelVarMi(ihlaller)) {
                    return ihlaller.stream().filter(h -> h.siddet() == Siddet.ENGEL).toList();
                }
            }
            return List.of();
        } finally {
            d.cikar(yeni, v, yedek);
        }
    }

    /**
     * OK-17 varyantı: önce DB'deki komşu (hazır {@code zincir}), yoksa AYNI çalışmada bu makinistte yerleştirilmiş bitişik parça (W1): aday hazırlıksız / alıkoymasız, yerleşmiş
     * parça da karşı yönde. DB komşusu ve çalışma içi komşu birlikte varsa (3+ parçalı tren) muhafazakâr kalınır (bağsız görevler).
     */
    private static ZincirVaryanti varyant(Durum d, SeferGirdisi s, Map<Long, Map<Long, ZincirVaryanti>> zincir) {
        ZincirVaryanti v = zincir.isEmpty() ? null : zincir.getOrDefault(d.girdi.id(), Map.of()).get(s.seferId());
        if (v != null || (s.oncekiParca() == null && s.sonrakiParca() == null)) {
            return v;
        }
        Gorev onceki = s.oncekiParca() != null && d.plan.containsKey(s.oncekiParca()) ? d.gorev(s.oncekiParca()) : null;
        Gorev sonraki = s.sonrakiParca() != null && d.plan.containsKey(s.sonrakiParca()) ? d.gorev(s.sonrakiParca()) : null;
        if (onceki == null && sonraki == null) {
            return null;
        }
        Gorev aday = s.gorev();
        List<Gorev> komsular = new ArrayList<>(2);
        if (onceki != null) {
            aday = hazirliksiz(aday);
            komsular.add(alikoymasiz(onceki));
        }
        if (sonraki != null) {
            aday = alikoymasiz(aday);
            komsular.add(hazirliksiz(sonraki));
        }
        return new ZincirVaryanti(aday, komsular);
    }

    /** Çalışma aralığı kalkıştan başlar (hazırlık yok); bu çalışmanın görevleri güncel kalkışla kurulduğundan başlangıç = güncel kalkış. */
    private static Gorev hazirliksiz(Gorev g) {
        return new Gorev(g.seferId(), g.kalkisGarId(), g.varisGarId(), g.hatId(), g.aracTipiId(), g.kalkis(), g.varis(), g.kalkis(), g.bitis(), g.km(), g.trenTuru(), g.yolculuk(), g.sureSayilir(), true);
    }

    /** Çalışma aralığı varışta biter (alıkoyma yok). */
    private static Gorev alikoymasiz(Gorev g) {
        return new Gorev(g.seferId(), g.kalkisGarId(), g.varisGarId(), g.hatId(), g.aracTipiId(), g.kalkis(), g.varis(), g.baslangic(), g.varis(), g.km(), g.trenTuru(), g.yolculuk(), g.sureSayilir(), g.devamEden());
    }

    /** Komşu parçaları OK-17 varyantındaki yeni görevleriyle değiştirir (sıra korunur). */
    private static List<Gorev> komsulariDegistir(List<Gorev> diger, ZincirVaryanti v) {
        List<Gorev> sonuc = new ArrayList<>(diger.size());
        for (Gorev g : diger) {
            Gorev yeni = null;
            for (Gorev k : v.komsular()) {
                if (k.seferId() == g.seferId()) {
                    yeni = k;
                }
            }
            sonuc.add(yeni == null ? g : yeni);
        }
        sonuc.sort(SIRA);
        return sonuc;
    }

    /** Makinistin bu sefer için ihlalleri tam olarak {K-20}'den ibaret mi (kayıpsız budama: başka ihlal yolculukla düzelmez). */
    private static boolean yalnizK20(List<Ihlal> ihlaller) {
        return ihlaller != null && !ihlaller.isEmpty() && ihlaller.stream().allMatch(i -> i.kod().equals("K-20"));
    }

    /** Bir sefer için aday makinist: makinist sırasındaki yeri, bu ay {dakika, km × 10} (canlı dizi) ve kalkış garında olup olmadığı. */
    private record Aday(int indeks, Durum durum, long[] toplam, boolean yerinde) {
    }

    /** Tercih sırası (tam sıralı): kalkış garında olan önce, sonra bu ay dakika, eşitse km, eşitse sicil (belirleyici bağ kırıcı; tam sayı karşılaştırması). */
    private static final Comparator<Aday> TERCIH = Comparator.comparing(Aday::yerinde, Comparator.reverseOrder())
            .thenComparingLong((Aday a) -> a.toplam()[0]).thenComparingLong(a -> a.toplam()[1]).thenComparing(a -> a.durum().girdi.sicil());

    /** En çok 3 nedenden (etiket bazında; önce kapasite nedenleri, sonra yetkisizlik, her grupta en sık olan önde) birer örnek metin: "hat yetkisi: Hat yetkisi yok: X". */
    private static List<String> ornekler(List<String> ilkKodlar, Map<String, List<String>> metinler, Parametreler p) {
        Map<String, Integer> sayac = new LinkedHashMap<>();
        Map<String, String> ornek = new LinkedHashMap<>();
        Set<String> yetkisizEtiketleri = new HashSet<>();
        for (String kod : ilkKodlar) {
            String etiket = NedenOzeti.etiket(kod, p);
            if (NedenOzeti.yetkisizlik(kod)) {
                yetkisizEtiketleri.add(etiket);
            }
            sayac.merge(etiket, 1, Integer::sum);
            ornek.putIfAbsent(etiket, metinler.get(kod).getFirst());
        }
        return sayac.entrySet().stream().sorted(Comparator.comparing((Map.Entry<String, Integer> e) -> yetkisizEtiketleri.contains(e.getKey())).thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed()).thenComparing(Map.Entry.comparingByKey()))
                .limit(EN_COK_ORNEK).map(e -> e.getKey() + ": " + ornek.get(e.getKey())).toList();
    }
}
