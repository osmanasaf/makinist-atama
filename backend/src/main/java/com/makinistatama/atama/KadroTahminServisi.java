package com.makinistatama.atama;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.atama.KadroAnalitigi.Imza;
import com.makinistatama.atama.KadroAnalitigi.Sinif;
import com.makinistatama.atama.KadroArama.Baglam;
import com.makinistatama.atama.KadroArama.Grup;
import com.makinistatama.atama.KadroRaporuDto.Analitik;
import com.makinistatama.atama.KadroRaporuDto.DagilimSatiri;
import com.makinistatama.atama.KadroRaporuDto.Doluluk;
import com.makinistatama.atama.KadroRaporuDto.EgriNokta;
import com.makinistatama.atama.KadroRaporuDto.GarSatiri;
import com.makinistatama.atama.KadroRaporuDto.Hedef;
import com.makinistatama.atama.KadroRaporuDto.HedefFark;
import com.makinistatama.atama.KadroRaporuDto.KalanBos;
import com.makinistatama.atama.KadroRaporuDto.Kume;
import com.makinistatama.atama.KadroRaporuDto.NedenSayisi;
import com.makinistatama.atama.KadroRaporuDto.Senaryo;
import com.makinistatama.atama.KadroRaporuDto.Simulasyon;
import com.makinistatama.atama.KadroRaporuDto.SinifSatiri;
import com.makinistatama.atama.KadroRaporuDto.TalepSatiri;
import com.makinistatama.atama.KadroRaporuDto.Varsayim;
import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.sefer.TrenTuru;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Kadro ihtiyacı tahmini (F3a, MK-74/MK-75; OK-22 / MK-82..84 ile yeniden kuruldu): SALT OKUMA. Yazma bağımlılığı (atama servisi, değişiklik kaydı, depolar) alınmaz; "temiz
 * sayfa" (aralık VE sonrasındaki atamalar çıkarılır) ve sanal makinistler yalnız bellektedir, veritabanı değişmez (NFR-01).
 * <p>
 * Başlık bir tek sayı değil DOLULUK EĞRİSİDİR: mevcut kadroyla doluluk, ek GENEL makinist (tarifedeki tüm bröveler) sayısına göre doluluk ve %98 / %99 / %100 için gereken
 * kişi. Eğri süre bütçesinden BAĞIMSIZ ve belirleyicidir (koşu sayısı yalnız veriye bağlı); {@code sureSn} bütçesi yalnız ek hesaplarda (iyileştirmeli plan, yolculuklu plan,
 * senaryo eğri farkı) geçerlidir. Devamsızlık YALNIZ burada (sentetik izin + aynı varsayımı paylaşan analitik U) sayılır; yedek satırı devamsızlık içermez.
 */
@Service
@Transactional(readOnly = true)
public class KadroTahminServisi {

    static final int EN_COK_HAFTA = 8;
    static final int EN_COK_DEVAMSIZLIK = 50;
    static final int EN_AZ_SURE_SN = 5;
    static final int EN_COK_SURE_SN = 30;
    static final int EN_COK_SANAL_SATIR = 10;
    static final int EN_COK_SANAL_SAAT = 24;
    static final int EN_COK_SANAL_SEFER = 3000;
    static final int EN_COK_KALAN_BOS = 100;
    static final LocalDate EN_ERKEN_BAS = LocalDate.of(2000, 1, 1);
    static final LocalDate EN_GEC_BAS = LocalDate.of(2100, 12, 31);
    /** Eğri zincirinin uzunluğu = denenecek en çok ek genel makinist: {@code min(64, max(16, mevcut kadro))}; koşu sayısını (dolayısıyla süreyi) veriden bağımsız üstten sınırlar. */
    static final int EN_AZ_ZINCIR = 16;
    static final int EN_COK_ZINCIR = 64;
    /** Başlık hedefleri (doluluk %); gevşekten sıkıya. */
    static final int[] HEDEF_YUZDE = { 98, 99, 100 };
    /** Dağılım tablosunun dayandığı hedef sırası (en talepkâr ulaşılan). */
    private static final int[] DAGILIM_HEDEFLERI = { 99, 98 };
    /** Senaryoda "mevcut kadro yeter" için gürültü eşiği: {@code max(5, %0,4 × sefer)} boş (başlangıç tarihi birkaç gün kayınca da toplam boş bu mertebede oynar; QA/review ölçümü). */
    static final int GURULTU_EN_AZ = 5;
    static final double GURULTU_ORAN = 0.004;
    /** İyileştirmeli ek hesapta tur sınırı (süre değil tur: belirleyici); {@code iyilestir.tur} ayarı bundan küçükse o geçerlidir. */
    static final int IYILESTIRME_TUR = 4;
    private static final double BOL_ESIGI = 75;
    private static final double SIKISIK_ESIGI = 95;
    private static final Locale TR = Locale.forLanguageTag("tr");

    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final BaglamYukleyici yukleyici;
    private final AyarServisi ayarlar;
    private final TanimServisi tanimlar;
    /** Aynı anda en çok bir simülasyonlu hesap (QA BUG-3): İptal sunucuyu durdurmaz; ikinci istek CPU'yu paylaşıp ek hesapların bütçesini yemesin. */
    private final Semaphore mesgul = new Semaphore(1);
    /** Yalnız süre testi değiştirir. */
    volatile LongSupplier nanoSaat = System::nanoTime;

    KadroTahminServisi(SeferServisi seferler, MakinistServisi makinistler, BaglamYukleyici yukleyici, AyarServisi ayarlar, TanimServisi tanimlar) {
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.yukleyici = yukleyici;
        this.ayarlar = ayarlar;
        this.tanimlar = tanimlar;
    }

    /** Hesap boyunca değişmeyen yüklenmiş girdi. */
    private record Taban(KadroIstegi istek, LocalDate bas, LocalDate bit, int gunSayisi, Yuk yuk, Parametreler p, List<MakinistDto> aktif, List<SeferDto> acik,
            List<SeferGirdisi> seferGirdileri, double u) {
    }

    /** Analitik bölümün ara sonuçları (simülasyon ve senaryo yeniden kullanır). */
    private record Analiz(Map<Sinif, Double> talep, Map<Imza, Integer> kume, Map<Long, Integer> garMakinist, KadroAnalitigi.Akis akis, Analitik analitik, double toplamTalep) {
    }

    /** Testin bağımsız denetimi için: dağılım noktasının kadrosu, planı ve girdileri (sanal kimlik/sicil dışarı çıkan DTO'da yoktur). */
    record Ic(KadroRaporuDto rapor, List<MakinistGirdisi> baslikKadro, Sonuc baslikSonuc, Parametreler baslikParametreleri, List<SeferGirdisi> seferler,
            List<SeferGirdisi> ekSeferler, Map<Long, String> adlar, int pencereGun, boolean yillik) {
    }

    public KadroRaporuDto hesapla(KadroIstegi istek) {
        if (istek.yalnizAnalitik()) {
            return hesaplaIc(istek).rapor(); // ms'lik hesap: kilit gerekmez
        }
        if (!mesgul.tryAcquire()) {
            throw new UygulamaHatasi(HttpStatus.TOO_MANY_REQUESTS, "KADRO_HESABI_SURUYOR", "Başka bir kadro hesabı sürüyor; bitince yeniden deneyin.", null);
        }
        try {
            return hesaplaIc(istek).rapor();
        } finally {
            mesgul.release();
        }
    }

    Ic hesaplaIc(KadroIstegi istek) {
        long basNs = nanoSaat.getAsLong();
        dogrula(istek);
        Taban t = yukle(istek);
        List<SeferGirdisi> ek = sanalSeferler(istek, t);
        Map<Long, String> adlar = t.yuk().adlar();
        Analiz a = analiz(t.seferGirdileri(), t);
        double toplamTalep = a.toplamTalep();

        List<TalepSatiri> talepSatirlari = new ArrayList<>();
        Map<Sinif, Integer> seferSayisi = new TreeMap<>();
        t.seferGirdileri().forEach(s -> seferSayisi.merge(new Sinif(s.gorev().hatId(), s.gorev().aracTipiId()), 1, Integer::sum));
        a.talep().forEach((k, v) -> talepSatirlari.add(new TalepSatiri(ad(adlar, k.hatId()), ad(adlar, k.aracTipiId()), seferSayisi.get(k), v)));
        Map<Long, Double> garTalebi = new TreeMap<>();
        t.seferGirdileri().forEach(s -> garTalebi.merge(s.gorev().kalkisGarId(), s.dakika() / 60.0 * 7 / t.gunSayisi(), Double::sum));

        KadroRaporuDto.Yedek yedek = istek.yedekDahil() ? yedek(t) : null;
        Varsayim varsayim = new Varsayim(t.bas(), t.bit(), istek.hafta(), t.gunSayisi(), istek.devamsizlikYuzde(), t.u(), t.p().oran("yedek.oran").doubleValue(),
                (int) t.p().tamsayi("yedek.min"), istek.sureSn(), t.aktif().size(), t.seferGirdileri().size(), toplamTalep);

        if (istek.yalnizAnalitik()) {
            KadroRaporuDto r = new KadroRaporuDto(varsayim, talepSatirlari, gar(garTalebi, a.garMakinist(), Map.of(), adlar), a.analitik(),
                    kumeler(a.kume(), a.akis().kullanilan(), t.u(), adlar), "AKIS", yedek, null, ek.isEmpty() ? null : senaryoAnalitik(t, a, ek));
            return new Ic(r, List.of(), new Sonuc(List.of(), List.of()), t.p(), t.seferGirdileri(), ek, adlar, 0, false);
        }

        long bitisNs = basNs + istek.sureSn() * 1_000_000_000L;
        Sim s = simule(t, bitisNs);
        Senaryo senaryo = ek.isEmpty() ? null : senaryo(t, a, ek, s, bitisNs);
        boolean tamam = s.atlananlar().isEmpty() && (senaryo == null || senaryo.tamamlandi());
        Simulasyon sim = new Simulasyon(t.aktif().size(), s.mevcutDoluluk(), s.konumsuz(), s.iyilestirmeli(), s.yolculuk(), s.egri(), s.hedefler(), s.egriDurma(), s.egriKosu(),
                s.refAdet(), s.refEtiketi(), s.dagilim(), s.kalanBoslar(), s.kalanBosToplam(), s.kalanNedenler(), s.atlananlar(), tamam, (nanoSaat.getAsLong() - basNs) / 1_000_000);
        KadroRaporuDto r = new KadroRaporuDto(varsayim, talepSatirlari, gar(garTalebi, a.garMakinist(), s.eklenenGar(), adlar), a.analitik(),
                kumeler(a.kume(), s.kumeKullanim(), t.u(), adlar), "PLAN", yedek, sim, senaryo);
        return new Ic(r, s.refKadro(), s.refSonuc(), t.p(), t.seferGirdileri(), ek, adlar, s.pencereGun(), s.yillik());
    }

    // ---------------------------------------------------------------- analitik

    private Analiz analiz(List<SeferGirdisi> seferGirdileri, Taban t) {
        Map<Long, String> adlar = t.yuk().adlar();
        Map<Sinif, Double> talep = KadroAnalitigi.haftalikTalep(seferGirdileri, t.gunSayisi());
        Map<Imza, Integer> kume = new TreeMap<>();
        Map<Long, Integer> garMakinist = new TreeMap<>();
        for (MakinistDto m : t.aktif()) {
            kume.merge(imza(m), 1, Integer::sum);
            garMakinist.merge(m.bagliGarId(), 1, Integer::sum);
        }
        KadroAnalitigi.Akis akis = KadroAnalitigi.akis(talep, kume, t.u());
        double toplamTalep = talep.values().stream().mapToDouble(Double::doubleValue).sum();
        List<SinifSatiri> siniflar = new ArrayList<>();
        int tekBroveli = 0;
        for (Map.Entry<Sinif, Double> e : talep.entrySet()) {
            int gerekli = KadroAnalitigi.kisi(e.getValue(), t.u());
            tekBroveli += gerekli;
            double eksik = akis.eksik().get(e.getKey());
            int yetkili = kume.entrySet().stream().filter(k -> k.getKey().kapsar(e.getKey())).mapToInt(Map.Entry::getValue).sum();
            siniflar.add(new SinifSatiri(ad(adlar, e.getKey().hatId()), ad(adlar, e.getKey().aracTipiId()), e.getValue(), gerekli, yetkili, eksik, KadroAnalitigi.kisi(eksik, t.u())));
        }
        Analitik analitik = new Analitik(t.u(), KadroAnalitigi.kisi(toplamTalep, t.u()), tekBroveli, t.aktif().size(), akis.eksikToplam(),
                KadroAnalitigi.kisi(akis.eksikToplam(), t.u()), siniflar);
        return new Analiz(talep, kume, garMakinist, akis, analitik, toplamTalep);
    }

    // ---------------------------------------------------------------- girdi

    private static void dogrula(KadroIstegi istek) {
        if (istek.bas().isBefore(EN_ERKEN_BAS) || istek.bas().isAfter(EN_GEC_BAS)) { // tarih taşması 500 vermesin (review W6)
            throw UygulamaHatasi.dogrulama("bas", "Başlangıç tarihi " + EN_ERKEN_BAS.getYear() + " ile " + EN_GEC_BAS.getYear() + " arasında olmalı.");
        }
        if (istek.hafta() < 1 || istek.hafta() > EN_COK_HAFTA) {
            throw UygulamaHatasi.dogrulama("hafta", "Hafta sayısı 1 ile " + EN_COK_HAFTA + " arasında olmalı.");
        }
        if (istek.devamsizlikYuzde() < 0 || istek.devamsizlikYuzde() > EN_COK_DEVAMSIZLIK) {
            throw UygulamaHatasi.dogrulama("devamsizlikYuzde", "Devamsızlık yüzdesi 0 ile " + EN_COK_DEVAMSIZLIK + " arasında olmalı.");
        }
        if (istek.sureSn() < EN_AZ_SURE_SN || istek.sureSn() > EN_COK_SURE_SN) {
            throw UygulamaHatasi.dogrulama("sureSn", "Süre sınırı " + EN_AZ_SURE_SN + " ile " + EN_COK_SURE_SN + " saniye arasında olmalı.");
        }
        if (istek.seferler().size() > EN_COK_SANAL_SATIR) {
            throw UygulamaHatasi.dogrulama("seferler", "En çok " + EN_COK_SANAL_SATIR + " sanal sefer satırı verilebilir.");
        }
    }

    private Taban yukle(KadroIstegi istek) {
        int gun = istek.hafta() * 7;
        LocalDate bas = istek.bas();
        LocalDate bit = bas.plusDays(gun - 1L);
        // MK-75: açık sefer = aralıktaki tüm iptal-olmayanlar (asıl atamalı olsa bile; temiz sayfa), geçmişte kalan sefer süzgeci yok (tarife planlaması)
        List<SeferDto> acik = seferler.aralikListesi(bas, bit).stream().filter(s -> s.durum() != SeferDurumu.IPTAL).toList();
        List<MakinistDto> aktif = makinistler.aktifDetaylar(PersonelTuru.MAKINISTLER).stream().sorted(Comparator.comparingLong(MakinistDto::id)).toList();
        Yuk y = yukleyici.yukleAralik(bas, bit, aktif);
        List<SeferGirdisi> sg = y.seferGirdileri(acik); // F2d Tur 3 / W1: parçalı trenin bitişik parçaları (INFO-10: simülasyonda da hazırlık / alıkoyma zinciri)
        return new Taban(istek, bas, bit, gun, y, y.parametreler(), aktif, acik, sg, KadroAnalitigi.kisiBasinaSaat(y.parametreler(), istek.devamsizlikYuzde()));
    }

    static Imza imza(MakinistDto m) {
        return new Imza(m.broveAracTipleri().stream().map(MakinistDto.BroveAracTipiDto::aracTipiId).sorted().toList(),
                m.broveHatlar().stream().map(MakinistDto.BroveHatDto::hatId).sorted().toList());
    }

    private static String ad(Map<Long, String> adlar, long id) {
        return adlar.getOrDefault(id, "#" + id);
    }

    // ---------------------------------------------------------------- simülasyon

    /** Simülasyon ara sonuçları (DTO'ya ve senaryoya girdi). */
    private record Sim(Doluluk mevcutDoluluk, Doluluk konumsuz, Doluluk iyilestirmeli, Doluluk yolculuk, List<EgriNokta> egri, List<Hedef> hedefler, String egriDurma, int egriKosu,
            int refAdet, String refEtiketi, List<DagilimSatiri> dagilim, Map<Long, Integer> eklenenGar, List<KalanBos> kalanBoslar, int kalanBosToplam, List<NedenSayisi> kalanNedenler,
            List<String> atlananlar, Map<Imza, Double> kumeKullanim, List<MakinistGirdisi> mevcut, List<MakinistGirdisi> refKadro, Sonuc refSonuc, int pencereGun, boolean yillik,
            long egriMs, long enUzunMs) {
    }

    private static Parametreler k20Kapali(Parametreler p) {
        Map<String, Siddet> siddet = new HashMap<>(p.siddetler());
        siddet.put("K-20", Siddet.KAPALI);
        return new Parametreler(p.degerler(), siddet, p.ozelKurallar());
    }

    /**
     * Gerçek makinistler: aralıktaki VE aralık SONRASINDAKİ atamalar çıkarılır (temiz sayfa; yolculuk dahil, CRITICAL-2: sonraki atamalar her makinistin "sonraki görevi" olup
     * K-20 ile tüm yerleştirmeleri eliyordu), yalnız aralık ÖNCESİ kalır (K-15/K-07/K-11 geçmişi); sentetik izin bir kez kurulur.
     */
    private List<MakinistGirdisi> mevcutKadro(Taban t) {
        List<MakinistGirdisi> girdiler = new ArrayList<>();
        int sira = 0;
        for (MakinistDto m : t.aktif()) {
            List<AtamaSatiri> kalan = t.yuk().satirlar(m.id()).stream().filter(s -> s.tarih().isBefore(t.bas())).toList();
            Map<YearMonth, double[]> aylik = new HashMap<>();
            for (AtamaSatiri s : kalan) {
                double[] x = aylik.computeIfAbsent(YearMonth.from(s.tarih()), k -> new double[2]);
                x[0] += t.yuk().gorev(s).sureDakika() / 60.0;
                x[1] += s.yolculuk() || s.km() == null ? 0 : s.km().doubleValue();
            }
            MakinistBilgisi bilgi = KadroArama.izinli(Yuk.bilgi(m), sira++, t.bas(), t.bit(), t.istek().devamsizlikYuzde());
            girdiler.add(new MakinistGirdisi(m.id(), m.sicil(), bilgi, kalan.stream().map(t.yuk()::gorev).toList(), t.yuk().hatSon(m.id()), t.yuk().genelSon(m.id()),
                    aylik));
        }
        return girdiler;
    }

    private BiFunction<List<MakinistGirdisi>, Long, Sonuc> duz(List<SeferGirdisi> sg, Map<Long, String> adlar, Parametreler p, int pencereGun, boolean yillik) {
        return (kadro, butce) -> OtomatikAtama.planla(sg, kadro, adlar, p, null, pencereGun, yillik, true, List.of(), 0, 0, Map.of(), 0);
    }

    /** İyileştirmeli planlayıcı (F2d): tur sınırlı (süre değil), tur 0 düz planın kendisi, tabandan kötü olamaz. */
    private BiFunction<List<MakinistGirdisi>, Long, Sonuc> iyilestirmeli(List<SeferGirdisi> sg, Map<Long, String> adlar, Parametreler p, int pencereGun, boolean yillik) {
        int tur = Math.max(1, Math.min(IYILESTIRME_TUR, ayarlar.tamsayi("iyilestir.tur")));
        return (kadro, butce) -> Iyilestirme.ara((onc, kaydir) -> OtomatikAtama.planla(sg, kadro, adlar, p, null, pencereGun, yillik, true, List.of(), 0, 0, onc, kaydir), tur,
                Long.MAX_VALUE / 4, nanoSaat).sonuc();
    }

    /** Ek hesap bütçeye sığar mı: kalan süre beklenen süre × 1,25'ten az değilse (koşu ortasında kesme yok). */
    private boolean sigar(long bitisNs, long beklenenMs) {
        return (bitisNs - nanoSaat.getAsLong()) / 1_000_000 >= 1.25 * beklenenMs;
    }

    private static double yuzde(int sefer, int bos) {
        return sefer <= 0 ? 100 : 100.0 * (sefer - bos) / sefer;
    }

    private Doluluk doluluk(String ad, String etiket, int sefer, Sonuc s, long t0) {
        return new Doluluk(ad, etiket, sefer, s.bosKalanlar().size(), yuzde(sefer, s.bosKalanlar().size()), (nanoSaat.getAsLong() - t0) / 1_000_000);
    }

    /** Hedef doluluk % -> kabul edilebilir boş sefer sayısı. */
    private static List<Integer> hedefBos(int sefer) {
        return Arrays.stream(HEDEF_YUZDE).mapToObj(y -> (int) Math.floor(sefer * (100 - y) / 100.0 + 1e-9)).toList();
    }

    /** Tarifedeki TÜM araç tipi ve hat bröveleri: GENEL sanal makinist (OK-22; kişi sayısı ölçüsü). */
    private static KadroArama.Broveler broveler(List<SeferGirdisi> sg) {
        return new KadroArama.Broveler(sg.stream().map(s -> s.gorev().aracTipiId()).collect(Collectors.toCollection(TreeSet::new)),
                sg.stream().map(s -> s.gorev().hatId()).collect(Collectors.toCollection(TreeSet::new)));
    }

    private Baglam baglam(Taban t, List<SeferGirdisi> sg, int gercek) {
        return new Baglam(sg.stream().collect(Collectors.toMap(SeferGirdisi::seferId, x -> x)), t.bas(), t.bit(), t.istek().devamsizlikYuzde(), gercek, broveler(sg));
    }

    /**
     * Eğrinin bağımsız koşuları için iş parçacığı sayısı: {@code kadro.is_parcacigi} ayarı > 0 ise o sayı, 0 ise otomatik {@code min(6, çekirdek / 2)} (en az 1).
     * Sonuç iş parçacığı sayısından bağımsızdır; yalnız süre değişir.
     */
    static int paralel(int ayar, int cekirdek) {
        return ayar > 0 ? ayar : Math.max(1, Math.min(6, cekirdek / 2));
    }

    /** Eğriyi bağımsız koşularla paralel hesaplar; {@code enUzun}: tek bir planlayıcı çağrısının ölçülen en uzun süresi (ms). */
    private KadroArama.Egri egri(List<MakinistGirdisi> mevcut, Baglam b, BiFunction<List<MakinistGirdisi>, Long, Sonuc> planlayici, List<Integer> hedefBos, AtomicLong enUzun) {
        BiFunction<List<MakinistGirdisi>, Long, Sonuc> olcen = (kadro, bu) -> {
            long t0 = nanoSaat.getAsLong();
            Sonuc r = planlayici.apply(kadro, bu);
            enUzun.accumulateAndGet((nanoSaat.getAsLong() - t0) / 1_000_000, Math::max);
            return r;
        };
        ExecutorService havuz = Executors.newFixedThreadPool(paralel(ayarlar.tamsayi("kadro.is_parcacigi"), Runtime.getRuntime().availableProcessors()));
        try {
            return KadroArama.egri(mevcut, b, olcen, hedefBos, Math.min(EN_COK_ZINCIR, Math.max(EN_AZ_ZINCIR, mevcut.size())), havuz);
        } finally {
            havuz.shutdown();
        }
    }

    /** Eğri noktaları: koşulmuş her ön ek için zarf (≤ ek kişiyle ulaşılan en az boş); yalnız iyileşen noktalar kalır, marjinal = önceki noktaya göre kişi başı kapanan sefer. */
    private static List<EgriNokta> egriNoktalari(Map<Integer, Sonuc> onbellek, int sefer) {
        List<EgriNokta> sonuc = new ArrayList<>();
        int enIyi = Integer.MAX_VALUE;
        int oncekiEk = -1;
        for (int ek : new TreeSet<>(onbellek.keySet())) {
            int bos = onbellek.get(ek).bosKalanlar().size();
            if (bos < enIyi) {
                sonuc.add(new EgriNokta(ek, bos, yuzde(sefer, bos), oncekiEk < 0 ? null : (double) (enIyi - bos) / (ek - oncekiEk)));
                enIyi = bos;
                oncekiEk = ek;
            }
        }
        return sonuc;
    }

    private Sim simule(Taban t, long bitisNs) {
        Map<Long, String> adlar = t.yuk().adlar();
        int pencereGun = BaglamYukleyici.pencereGun(t.p());
        boolean yillik = BaglamYukleyici.yillik(t.p());
        List<MakinistGirdisi> mevcut = mevcutKadro(t);
        List<SeferGirdisi> sg = t.seferGirdileri();
        int n = sg.size();
        Parametreler pAcik = t.p();
        Baglam b = baglam(t, sg, mevcut.size());
        Map<Long, SeferGirdisi> seferMap = b.seferler();

        // ---- BAŞLIK: doluluk eğrisi (K-20 açık, düz planlayıcı, süre bütçesiz: koşu sayısı yalnız veriye bağlı)
        AtomicLong enUzunAt = new AtomicLong();
        long t0 = nanoSaat.getAsLong();
        List<Integer> hedefBos = hedefBos(n);
        KadroArama.Egri e = egri(mevcut, b, duz(sg, adlar, pAcik, pencereGun, yillik), hedefBos, enUzunAt);
        long egriMs = (nanoSaat.getAsLong() - t0) / 1_000_000;
        Map<Integer, Sonuc> noktalar = e.noktalar();
        Sonuc tur0 = noktalar.get(0);
        Doluluk mevcutDoluluk = new Doluluk("mevcut", "mevcut kadro, K-20 açık", n, tur0.bosKalanlar().size(), yuzde(n, tur0.bosKalanlar().size()), egriMs);
        List<EgriNokta> egri = egriNoktalari(noktalar, n);
        int enIyiAdet = egri.getLast().ek();
        int enIyiBos = egri.getLast().bos();
        List<Hedef> hedefler = new ArrayList<>();
        for (int i = 0; i < HEDEF_YUZDE.length; i++) {
            Integer adet = e.hedefAdet().get(hedefBos.get(i));
            if (adet != null) {
                int bos = noktalar.get(adet).bosKalanlar().size();
                hedefler.add(new Hedef(HEDEF_YUZDE[i], true, adet, bos, yuzde(n, bos)));
            } else {
                hedefler.add(new Hedef(HEDEF_YUZDE[i], false, null, enIyiBos, yuzde(n, enIyiBos)));
            }
        }
        String egriDurma = enIyiBos == 0 ? "BOS_SIFIR" : hedefler.stream().allMatch(Hedef::ulasildi) ? "TAMAM" : "EK_SINIRI";

        // ---- dağılım noktası: ulaşılan en talepkâr hedef (%99, yoksa %98), yoksa eğrinin en iyi noktası
        int refAdet = enIyiAdet;
        String refEtiketi = "eğrinin en iyi noktası (+" + enIyiAdet + " genel makinist)";
        for (int yuzdeHedef : DAGILIM_HEDEFLERI) {
            Hedef h = hedefler.stream().filter(x -> x.yuzde() == yuzdeHedef && x.ulasildi()).findFirst().orElse(null);
            if (h != null) {
                refAdet = h.ek();
                refEtiketi = "%" + yuzdeHedef + " hedefi için +" + h.ek() + " genel makinist";
                break;
            }
        }
        List<Grup> eklenen = e.zincir();
        Sonuc refSonuc = noktalar.get(refAdet);
        List<MakinistGirdisi> refKadro = KadroArama.kadro(mevcut, eklenen, refAdet, b);
        Map<Long, Integer> eklenenGar = new TreeMap<>();
        eklenen.subList(0, refAdet).forEach(g -> eklenenGar.merge(g.garId(), 1, Integer::sum));
        List<DagilimSatiri> dagilim = dagilim(refSonuc, seferMap, eklenen, refAdet, adlar, t.gunSayisi() / 7.0);

        // ---- kalan boşlar: eğrinin en iyi noktasında (nedenleriyle)
        Sonuc enIyi = noktalar.get(enIyiAdet);
        Map<Long, SeferDto> seferDto = t.acik().stream().collect(Collectors.toMap(SeferDto::id, x -> x));
        List<KalanBos> kalan = enIyi.bosKalanlar().stream().filter(x -> seferDto.containsKey(x.seferId()))
                .sorted(Comparator.comparing((OtomatikAtama.Bos x) -> seferDto.get(x.seferId()).planlananKalkis()).thenComparingLong(OtomatikAtama.Bos::seferId))
                .limit(EN_COK_KALAN_BOS).map(x -> {
                    SeferDto d = seferDto.get(x.seferId());
                    return new KalanBos(d.id(), d.trenNo(), d.tarih(), d.kalkisGar(), d.varisGar(), x.neden());
                }).toList();
        Map<String, Integer> nedenler = new TreeMap<>();
        enIyi.bosKalanlar().forEach(x -> nedenler.merge(x.ornekler().isEmpty() ? "diğer" : x.ornekler().getFirst().split(":", 2)[0], 1, Integer::sum));
        List<NedenSayisi> kalanNedenler = nedenler.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(x -> new NedenSayisi(x.getKey(), x.getValue())).toList();

        // ---- küme kullanımı: mevcut kadronun GERÇEK planından (kişinin işlediği saat, kişinin bröve kümesine; BUG-5)
        Map<Long, Imza> imzaOf = new HashMap<>();
        t.aktif().forEach(m -> imzaOf.put(m.id(), imza(m)));
        Map<Imza, Double> kumeKullanim = new TreeMap<>();
        for (Atandi at : tur0.atananlar()) {
            if (at.makinistId() >= 0) {
                kumeKullanim.merge(imzaOf.get(at.makinistId()), seferMap.get(at.seferId()).dakika() / 60.0 / (t.gunSayisi() / 7.0), Double::sum);
            }
        }

        // ---- ek hesaplar (süre bütçeli; sığmazsa atlanır ve açıkça yazılır): konumun payı, iyileştirmeli plan, yolculuklu plan
        long enUzun = enUzunAt.get();
        List<String> atlanan = new ArrayList<>();
        Doluluk konumsuz = null;
        if (sigar(bitisNs, enUzun)) {
            t0 = nanoSaat.getAsLong();
            konumsuz = doluluk("konumsuz", "mevcut kadro, K-20 kapalı (yalnız kapasite)", n, duz(sg, adlar, k20Kapali(pAcik), pencereGun, yillik).apply(mevcut, 0L), t0);
        } else {
            atlanan.add("K-20 kapalı plan (süre yetmedi)");
        }
        Doluluk iyilestirmeli = null;
        if (sigar(bitisNs, IYILESTIRME_TUR * enUzun)) {
            t0 = nanoSaat.getAsLong();
            iyilestirmeli = doluluk("iyilestirmeli", "mevcut kadro, 'daha çok sefer doldur' (iyileştirmeli)", n,
                    iyilestirmeli(sg, adlar, pAcik, pencereGun, yillik).apply(mevcut, 0L), t0);
        } else {
            atlanan.add("İyileştirmeli plan (süre yetmedi)");
        }
        Doluluk yolculuk = null;
        if (t.istek().yolculuk()) {
            if (sigar(bitisNs, 3 * enUzun)) {
                t0 = nanoSaat.getAsLong();
                yolculuk = doluluk("yolculuk", "mevcut kadro, yolculuk aşamalı", n, yolculukluPlanlayici(t, adlar, pAcik, pencereGun, yillik).apply(mevcut, 0L), t0);
            } else {
                atlanan.add("Yolculuklu plan (süre yetmedi)");
            }
        }
        return new Sim(mevcutDoluluk, konumsuz, iyilestirmeli, yolculuk, egri, hedefler, egriDurma, e.kosuSayisi(), refAdet, refEtiketi, dagilim, eklenenGar, kalan,
                enIyi.bosKalanlar().size(), kalanNedenler, atlanan, kumeKullanim, mevcut, refKadro, refSonuc, pencereGun, yillik, egriMs, enUzun);
    }

    /** Eklenen genel makinistlerin plandaki baskın kümesi: kişi başına en çok dakika alan (hat, araç tipi); iş almayan kişi "iş almadı" satırına. */
    private static List<DagilimSatiri> dagilim(Sonuc plan, Map<Long, SeferGirdisi> seferler, List<Grup> eklenen, int adet, Map<Long, String> adlar, double hafta) {
        Map<Long, Map<Sinif, Long>> dakika = new TreeMap<>();
        Map<Long, Map<Sinif, Integer>> sayi = new TreeMap<>();
        for (Atandi a : plan.atananlar()) {
            if (a.makinistId() < 0) {
                SeferGirdisi g = seferler.get(a.seferId());
                Sinif s = new Sinif(g.gorev().hatId(), g.gorev().aracTipiId());
                dakika.computeIfAbsent(a.makinistId(), x -> new TreeMap<>()).merge(s, g.dakika(), Long::sum);
                sayi.computeIfAbsent(a.makinistId(), x -> new TreeMap<>()).merge(s, 1, Integer::sum);
            }
        }
        Map<Grup, int[]> toplam = new TreeMap<>(); // kişi, sefer, dakika
        for (int i = 0; i < adet; i++) {
            long id = -(i + 1L);
            long gar = eklenen.get(i).garId();
            Map<Sinif, Long> m = dakika.get(id);
            Sinif baskin = m == null ? null : m.entrySet().stream().max(Map.Entry.<Sinif, Long>comparingByValue().thenComparing(Map.Entry.<Sinif, Long>comparingByKey().reversed())).orElseThrow().getKey();
            Grup g = baskin == null ? new Grup(gar, 0, 0) : new Grup(gar, baskin.aracTipiId(), baskin.hatId());
            int[] x = toplam.computeIfAbsent(g, y -> new int[3]);
            x[0]++;
            if (m != null) {
                x[1] += sayi.get(id).values().stream().mapToInt(Integer::intValue).sum();
                x[2] += (int) m.values().stream().mapToLong(Long::longValue).sum();
            }
        }
        return toplam.entrySet().stream().sorted(Map.Entry.<Grup, int[]>comparingByValue(Comparator.comparingInt((int[] x) -> x[0]).reversed()).thenComparing(Map.Entry.comparingByKey()))
                .map(en -> new DagilimSatiri(ad(adlar, en.getKey().garId()), en.getKey().aracTipiId() == 0 ? "(iş almadı)" : ad(adlar, en.getKey().aracTipiId()),
                        en.getKey().hatId() == 0 ? "—" : ad(adlar, en.getKey().hatId()), en.getValue()[0], en.getValue()[1], en.getValue()[2] / 60.0 / hafta))
                .toList();
    }

    /** Yolculuk aşamalı planlayıcı (F2b): ayarlar ve aday seferler yalnız bu ek hesapta okunur. */
    private BiFunction<List<MakinistGirdisi>, Long, Sonuc> yolculukluPlanlayici(Taban t, Map<Long, String> adlar, Parametreler p, int pencereGun, boolean yillik) {
        int aktarma = ayarlar.tamsayi("yolculuk.aktarma_dk");
        int arama = ayarlar.tamsayi("yolculuk.arama_saat");
        boolean yukBinilir = ayarlar.tamsayi("yolculuk.yuk_trenine_binilir") == 1;
        List<YolculukOnerisi.YolculukAdayi> adaylar = seferler.aralikListesi(t.bas().minusDays(Math.max(1, (arama + 23) / 24)), t.bit()).stream()
                .filter(s -> s.durum() != SeferDurumu.IPTAL && (yukBinilir || s.trenTuru() != TrenTuru.YUK))
                .map(s -> new YolculukOnerisi.YolculukAdayi(s.id(), s.tarih(), t.yuk().yolculukGorevi(s), false)).toList();
        return (kadro, butce) -> OtomatikAtama.planla(t.seferGirdileri(), kadro, adlar, p, null, pencereGun, yillik, true, adaylar, aktarma, arama, Map.of(), 0);
    }

    // ---------------------------------------------------------------- senaryo (Faz 3)

    /**
     * Sanal seferleri bellekte açar (MK-80): her satır, aralığın seçili hafta günlerinde her kalkış saatinde bir sefer; kimlikler −1, −2, … (gerçek kimlikle çakışmaz). Girdi
     * doğrulaması burada (güven sınırı, MK-79): kimlikler doğru türde tanım olmalı, sınırlar aşılırsa alan adıyla 400.
     */
    private List<SeferGirdisi> sanalSeferler(KadroIstegi istek, Taban t) {
        List<SeferGirdisi> sonuc = new ArrayList<>();
        List<KadroIstegi.SanalSefer> satirlar = istek.seferler();
        for (int i = 0; i < satirlar.size(); i++) {
            KadroIstegi.SanalSefer s = satirlar.get(i);
            String alan = "seferler[" + i + "]";
            tanimlar.bul(TanimTipi.HAT, s.hatId(), alan + ".hatId");
            tanimlar.bul(TanimTipi.ARAC_TIPI, s.aracTipiId(), alan + ".aracTipiId");
            tanimlar.bul(TanimTipi.GAR, s.kalkisGarId(), alan + ".kalkisGarId");
            tanimlar.bul(TanimTipi.GAR, s.varisGarId(), alan + ".varisGarId");
            if (s.saatler().isEmpty() || s.saatler().size() > EN_COK_SANAL_SAAT || s.saatler().stream().anyMatch(java.util.Objects::isNull)) {
                throw UygulamaHatasi.dogrulama(alan + ".saatler", "Kalkış saati sayısı 1 ile " + EN_COK_SANAL_SAAT + " arasında olmalı.");
            }
            if (s.sureDk() < 1 || s.sureDk() > 1440) {
                throw UygulamaHatasi.dogrulama(alan + ".sureDk", "Süre 1 ile 1440 dakika arasında olmalı.");
            }
            if (s.km() != null && (s.km().signum() < 0 || s.km().compareTo(BigDecimal.valueOf(5000)) > 0)) {
                throw UygulamaHatasi.dogrulama(alan + ".km", "Km 0 ile 5000 arasında olmalı.");
            }
            if (s.trenTuru() != null && !s.trenTuru().equals("YUK") && !s.trenTuru().equals("YOLCU")) {
                throw UygulamaHatasi.dogrulama(alan + ".trenTuru", "Tren türü YUK ya da YOLCU olmalı.");
            }
            double km = s.km() == null ? 0 : s.km().doubleValue();
            for (int g = 0; g < t.gunSayisi(); g++) {
                LocalDate gun = t.bas().plusDays(g);
                if (!s.gunler().isEmpty() && !s.gunler().contains(gun.getDayOfWeek())) {
                    continue;
                }
                for (java.time.LocalTime saat : s.saatler().stream().distinct().sorted().toList()) {
                    if (sonuc.size() >= EN_COK_SANAL_SEFER) {
                        throw UygulamaHatasi.dogrulama("seferler", "Açılan sanal sefer sayısı en çok " + EN_COK_SANAL_SEFER + " olabilir.");
                    }
                    Instant kalkis = gun.atTime(saat).atZone(ZamanHesabi.ISTANBUL).toInstant();
                    long id = -(sonuc.size() + 1L);
                    Gorev gorev = Gorev.olustur(id, s.kalkisGarId(), s.varisGarId(), s.hatId(), s.aracTipiId(), kalkis, kalkis, kalkis.plusSeconds(60L * s.sureDk()), km, s.trenTuru(), t.p());
                    sonuc.add(new SeferGirdisi(id, gun, YearMonth.from(gun), gorev));
                }
            }
        }
        return sonuc;
    }

    private static List<SeferGirdisi> birlestir(List<SeferGirdisi> a, List<SeferGirdisi> b) {
        List<SeferGirdisi> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
    }

    /** Ek talebin dokunduğu sınıflar için okunur gerekçe: yetkili kişi, talep önce/sonra, kapasite. */
    private static String sinifGerekcesi(Analiz once, Analiz sonra, double u) {
        List<String> parcalar = new ArrayList<>();
        Map<String, SinifSatiri> oncekiler = once.analitik().siniflar().stream().collect(Collectors.toMap(x -> x.hat() + "|" + x.aracTipi(), x -> x, (x, y) -> x));
        for (SinifSatiri x : sonra.analitik().siniflar()) {
            SinifSatiri o = oncekiler.get(x.hat() + "|" + x.aracTipi());
            if (o == null || Math.abs(x.haftalikSaat() - o.haftalikSaat()) > 1e-6) {
                parcalar.add(String.format(TR, "%s / %s: yetkili %d kişi (kapasite %.0f sa/hafta), talep %.0f → %.0f sa/hafta", x.hat(), x.aracTipi(),
                        x.mevcutYetkili(), x.mevcutYetkili() * u, o == null ? 0.0 : o.haftalikSaat(), x.haftalikSaat()));
            }
        }
        return String.join("; ", parcalar);
    }

    /** Analitik taraf: ek talep (kişi-eşdeğeri), mevcut kadroda atıl saat kapasitesi (kişi-eşdeğeri) ve eksik saat önce/sonra. */
    private record AnalitikFark(double ekTalepKisi, double atilKisi, double tabanEksikSaat, double senaryoEksikSaat) {
    }

    private AnalitikFark analitikFark(Taban t, Analiz a, Analiz aEk, List<SeferGirdisi> ek) {
        double atilSaat = Math.max(0, t.aktif().size() * t.u() - a.toplamTalep());
        return new AnalitikFark(ekSaat(ek, t) / t.u(), atilSaat / t.u(), a.analitik().eksikSaat(), aEk.analitik().eksikSaat());
    }

    /** Yalnız analitik istekte senaryo: ek talep ve analitik karşılaştırma (simülasyon yok). */
    private Senaryo senaryoAnalitik(Taban t, Analiz a, List<SeferGirdisi> ek) {
        Analiz aEk = analiz(birlestir(t.seferGirdileri(), ek), t);
        AnalitikFark f = analitikFark(t, a, aEk, ek);
        boolean yeter = aEk.analitik().eksikAltSinir() == 0;
        return new Senaryo(ek.size(), ekSaat(ek, t), null, null, 0, 0, yeter, (yeter ? "Saat/yetki bakımından mevcut kadro yeter. " : "Saat/yetki bakımından mevcut kadro yetmez: en az "
                + aEk.analitik().eksikAltSinir() + " ek makinist. ") + String.format(TR, "Ek talep %.1f kişi-eşdeğeri, mevcut kadroda atıl kapasite %.1f kişi-eşdeğeri. ", f.ekTalepKisi(), f.atilKisi())
                + sinifGerekcesi(a, aEk, t.u()), null, f.ekTalepKisi(), f.atilKisi(), f.tabanEksikSaat(), f.senaryoEksikSaat(), true, "Yalnız analitik hesap (simülasyon yapılmadı).");
    }

    private static double ekSaat(List<SeferGirdisi> ek, Taban t) {
        return ek.stream().mapToLong(SeferGirdisi::dakika).sum() / 60.0 * 7 / t.gunSayisi();
    }

    /**
     * "Ne olurdu" (MK-80, OK-22): AYNI mevcut kadroyla (66 kişi) taban ve senaryo koşusu; fark = toplam boş farkı (yeni seferlerin boşu değil: yeni seferler yer değiştirip eskilerden
     * birkaçını boşa düşürebilir, "fark 0" bunu gizliyordu). "Mevcut kadro yeter" ancak artış gürültü eşiğinin altındaysa. Eğri farkı (hedef bazında +n) süre yetiyorsa hesaplanır.
     */
    private Senaryo senaryo(Taban t, Analiz a, List<SeferGirdisi> ek, Sim s, long bitisNs) {
        Analiz aEk = analiz(birlestir(t.seferGirdileri(), ek), t);
        AnalitikFark f = analitikFark(t, a, aEk, ek);
        List<SeferGirdisi> tum = birlestir(t.seferGirdileri(), ek);
        Map<Long, String> adlar = t.yuk().adlar();
        int pencereGun = s.pencereGun();
        boolean yillik = s.yillik();
        long t0 = nanoSaat.getAsLong();
        Sonuc r = duz(tum, adlar, t.p(), pencereGun, yillik).apply(s.mevcut(), 0L);
        Doluluk senaryoD = doluluk("senaryo", "mevcut kadro + ek seferler, K-20 açık", tum.size(), r, t0);
        Doluluk tabanD = s.mevcutDoluluk();
        int bosFarki = senaryoD.bos() - tabanD.bos();
        int gurultu = Math.max(GURULTU_EN_AZ, (int) Math.ceil(GURULTU_ORAN * t.seferGirdileri().size()));
        boolean yeter = bosFarki <= gurultu;

        List<HedefFark> egriFarki = null;
        String uyari = null;
        if (sigar(bitisNs, s.egriMs())) {
            Baglam bs = baglam(t, tum, s.mevcut().size());
            List<Integer> hedefBos = hedefBos(tum.size());
            KadroArama.Egri es = egri(s.mevcut(), bs, duz(tum, adlar, t.p(), pencereGun, yillik), hedefBos, new AtomicLong());
            egriFarki = new ArrayList<>();
            for (int i = 0; i < HEDEF_YUZDE.length; i++) {
                Integer senaryoEk = es.hedefAdet().get(hedefBos.get(i));
                Integer tabanEk = s.hedefler().get(i).ek();
                egriFarki.add(new HedefFark(HEDEF_YUZDE[i], tabanEk, senaryoEk, tabanEk == null || senaryoEk == null ? null : senaryoEk - tabanEk));
            }
        } else {
            uyari = "Süre sınırı: eğri farkı hesaplanmadı; toplam boş farkı geçerlidir (daha uzun süre sınırı verilebilir).";
        }

        StringBuilder g = new StringBuilder();
        g.append(String.format(TR, "Aynı %d kişilik kadroyla toplam boş sefer %d → %d (%+d; doluluk %%%.1f → %%%.1f). ", s.mevcut().size(), tabanD.bos(), senaryoD.bos(), bosFarki,
                tabanD.dolulukYuzde(), senaryoD.dolulukYuzde()));
        g.append(yeter ? String.format(TR, "Artış gürültü eşiğinin (±%d boş) altında: mevcut kadro yeni seferleri de taşır. ", gurultu)
                : String.format(TR, "Artış gürültü eşiğinin (±%d boş) üstünde: mevcut kadro yetmez; yeni seferler başka seferlerin de boşa düşmesine yol açıyor. ", gurultu));
        g.append(String.format(TR, "Ek talep %.1f kişi-eşdeğeri; mevcut kadroda saat bakımından atıl kapasite %.1f kişi-eşdeğeri. ", f.ekTalepKisi(), f.atilKisi()));
        if (egriFarki != null) {
            g.append("Eğri farkı: ").append(egriFarki.stream().map(h -> "%" + h.yuzde() + " için " + (h.fark() == null ? "ulaşılamadı" : String.format(TR, "%+d", h.fark()))).collect(Collectors.joining(", ")))
                    .append(". ");
        }
        g.append(sinifGerekcesi(a, aEk, t.u()));
        return new Senaryo(ek.size(), ekSaat(ek, t), tabanD, senaryoD, bosFarki, gurultu, yeter, g.toString().strip(), egriFarki, f.ekTalepKisi(), f.atilKisi(), f.tabanEksikSaat(),
                f.senaryoEksikSaat(), egriFarki != null, uyari);
    }

    // ---------------------------------------------------------------- rapor parçaları

    private static List<GarSatiri> gar(Map<Long, Double> garTalebi, Map<Long, Integer> garMakinist, Map<Long, Integer> eklenenGar, Map<Long, String> adlar) {
        TreeSet<Long> idler = new TreeSet<>(garTalebi.keySet());
        idler.addAll(garMakinist.keySet());
        idler.addAll(eklenenGar.keySet());
        return idler.stream().map(g -> new GarSatiri(ad(adlar, g), garTalebi.getOrDefault(g, 0.0), garMakinist.getOrDefault(g, 0), eklenenGar.getOrDefault(g, 0))).toList();
    }

    /** Mevcut kümeler: kişi sayısı ve haftalık kullanılan saat ({@code kullanilan}: plan ya da akış tahsisi); kullanım % = kullanılan / (kişi × U). */
    private static List<Kume> kumeler(Map<Imza, Integer> mevcut, Map<Imza, Double> kullanilan, double u, Map<Long, String> adlar) {
        List<Kume> sonuc = new ArrayList<>();
        for (Imza i : new TreeSet<>(mevcut.keySet())) {
            int m = mevcut.get(i);
            double kapasite = m * u;
            double kul = kullanilan.getOrDefault(i, 0.0);
            double yuzde = kapasite <= 0 ? 0 : 100 * kul / kapasite;
            sonuc.add(new Kume(i.aracTipleri().stream().map(x -> ad(adlar, x)).toList(), i.hatlar().stream().map(x -> ad(adlar, x)).toList(), m, kul, kapasite, yuzde,
                    yuzde < BOL_ESIGI ? "BOL" : yuzde >= SIKISIK_ESIGI ? "SIKISIK" : "NORMAL"));
        }
        return sonuc;
    }

    /** Yedek: günlük ihtiyacın en büyüğü (YedekServisi formülü; PLANLI sefer sayısı). Devamsızlık İÇERMEZ: yedek operasyonel tampondur, devamsızlık yalnız simülasyonda sayılır (OK-22). */
    private KadroRaporuDto.Yedek yedek(Taban t) {
        Map<LocalDate, Long> planli = t.acik().stream().filter(s -> s.durum() == SeferDurumu.PLANLI).collect(Collectors.groupingBy(SeferDto::tarih, Collectors.counting()));
        List<Integer> gunler = new ArrayList<>();
        for (int i = 0; i < t.gunSayisi(); i++) {
            gunler.add(planli.getOrDefault(t.bas().plusDays(i), 0L).intValue());
        }
        BigDecimal oran = t.p().oran("yedek.oran");
        int min = (int) t.p().tamsayi("yedek.min");
        return new KadroRaporuDto.Yedek(KadroAnalitigi.yedekIhtiyaci(oran, min, gunler), gunler.stream().mapToInt(Integer::intValue).max().orElse(0));
    }
}
