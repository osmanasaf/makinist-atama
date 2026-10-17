package com.makinistatama.kural;

import static com.makinistatama.kural.ZamanHesabi.sure;
import static com.makinistatama.kural.ZamanHesabi.tarih;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yönetici tanımlı kuralların değerlendirmesi (MK-17, MK-18): durumsuz, saf, tek statik giriş. Desen yok: ölçü {@link Olcu} enum'unda VERİ,
 * hesap {@code switch (olcu)}. Her kural için sıra: geçerlilik (sefer günü) → kapsam (aday kapsam dışıysa kural uygulanmaz) → ölçü; her kural
 * adayın dokunduğu pencerelerin EN KÖTÜSÜ için en çok bir {@link Ihlal} üretir (kod {@code Ö-NNN}, şiddet kuralın kendisi, KAPALI üretmez).
 * Onhesap motordan gelir (yeniden kurulmaz); gün dizisi bir çağrıda kapsam başına en çok bir kez kurulur.
 * <p>
 * MK-5: toplam/sayı ölçüleri adaya göre monotondur ve sorumlu komşular tüm komşuların altkümesidir; vardiya kapsamı nöbet sınıfına bağlı
 * olduğundan (nöbet birleşince sınıf değişir) vardiya süzmeli kurallarda adayın TÜM nöbeti taranır ({@code Kapsamli#alan}). Azalan ölçüler (izin günü,
 * görevsiz aralık, dinlenme: yalnız "en az") atama eklendikçe yalnız azalır; adayın dokunduğu her pencere TÜM görevlerle ölçüldüğünden kabul edilen aday
 * önceki görevlerin penceresini (onların daha az görevi görür) bozmaz. Seri ölçüleri adayı içeren seriyi iki yönde ölçer. "Nöbetler arası dinlenme"
 * kapsamı boşluğun İKİ ucundaki nöbete bakar (komşu nöbet kapsamdaysa aday kapsam dışı olsa da boşluk denetlenir).
 */
final class OzelKuralDegerlendirici {

    private OzelKuralDegerlendirici() {
    }

    static List<Ihlal> kontrol(KuralBaglami b, Onhesap h) {
        Parametreler p = b.parametreler();
        List<Ihlal> o = new ArrayList<>(2);
        Hesap tam = null;
        Map<List<LocalDate>, Hesap> kesikler = null; // geçerlilik aralığına göre kesilmiş görev evreni (aralık dışında görev varsa)
        LocalDate seferGunu = null;
        for (OzelKural k : p.ozelKurallar()) {
            if (k.siddet() == Siddet.KAPALI) {
                continue;
            }
            if (seferGunu == null) {
                seferGunu = ZamanHesabi.gun(b.aday().kalkis());
            }
            if (!k.gecerliMi(seferGunu)) {
                continue;
            }
            if (!k.kapsam().turKapsar(b.makinist().tur())) {
                continue; // F3b (MK-85): kişi düzeyi tür kapısı; ölçüden ve dinlenme istisnasından bağımsız
            }
            // MK-5: kural geçerlilik aralığı dışındaki görevleri ölçmez (yoksa yürürlük öncesi atama, yürürlükteki görevi sessizce kırmızı yapar)
            Instant bas = ZamanHesabi.gunBasi(k.gecerlilikBas());
            Instant son = k.gecerlilikBit() == null ? null : ZamanHesabi.gunBasi(k.gecerlilikBit().plusDays(1));
            List<LocalDate> anahtar = Arrays.asList(k.gecerlilikBas(), k.gecerlilikBit());
            if (kesikler == null) {
                kesikler = new HashMap<>(2);
            }
            Hesap hs = kesikler.get(anahtar);
            if (hs == null) {
                if (h.siraliTum().stream().noneMatch(g -> disarida(g, bas, son))) {
                    if (tam == null) {
                        tam = new Hesap(b, h);
                    }
                    hs = tam;
                } else {
                    List<Gorev> icerdekiler = b.digerGorevler().stream().filter(g -> !disarida(g, bas, son)).toList();
                    KuralBaglami kesik = new KuralBaglami(b.aday(), icerdekiler, b.makinist(), b.pencereOncesiHatSonSurus(), b.pencereOncesiGenelSonSurus(), b.tanimAdlari(),
                            p, b.simdi());
                    hs = new Hesap(kesik, Onhesap.of(kesik));
                }
                kesikler.put(anahtar, hs);
            }
            if (k.olcu() != Olcu.NOBETLER_ARASI_DINLENME && !hs.adayKapsamda(k.kapsam())) {
                continue; // dinlenme kapsamı boşluğun iki ucuna bakar, kendi denetler
            }
            hs.olc(k, o);
        }
        return o;
    }

    /** Görev (sefer = kalkış günü) kuralın geçerlilik aralığı [bas, son) dışında mı. */
    private static boolean disarida(Gorev g, Instant bas, Instant son) {
        return g.kalkis().isBefore(bas) || (son != null && !g.kalkis().isBefore(son));
    }

    /**
     * Gece nöbetinin günü = gecenin BAŞLADIĞI takvim günü. Gece penceresi gece yarısını aşıyorsa (20:00–06:00) gece yarısından sonra başlayan nöbet önceki geceye
     * yazılır ("Cuma 01:00" = Perşembe gecesi): başlangıç − gece bitişi. Pencere gün içindeyse (00:00–06:00) başlangıç günü.
     */
    static LocalDate geceGunu(Instant baslangic, Parametreler p) {
        LocalTime ge = p.saat("gece.bitis");
        return ZamanHesabi.gun(p.saat("gece.baslangic").isAfter(ge) ? baslangic.minusSeconds(ge.toSecondOfDay()) : baslangic);
    }

    // ---------- pencere yardimcilari ----------

    static LocalDate pencereBasi(Pencere pen, LocalDate gun) {
        return switch (pen) {
            case GUN -> gun;
            case HAFTA -> ZamanHesabi.haftaBasi(gun);
            case AY -> ZamanHesabi.ayBasi(gun);
            case YIL -> gun.withDayOfYear(1);
            default -> throw new IllegalArgumentException("Takvim penceresi değil: " + pen);
        };
    }

    /** Pencerenin ilk gününden bir sonraki pencerenin ilk gününe (son hariç). */
    static LocalDate pencereSon(Pencere pen, LocalDate bas) {
        return switch (pen) {
            case GUN -> bas.plusDays(1);
            case HAFTA -> bas.plusDays(7);
            case AY -> bas.plusMonths(1);
            case YIL -> bas.plusYears(1);
            default -> throw new IllegalArgumentException("Takvim penceresi değil: " + pen);
        };
    }

    /** [bas, bit) aralığının dokunduğu pencerelerin ilk günleri. */
    static List<LocalDate> pencereBaslari(Pencere pen, Instant bas, Instant bit) {
        return switch (pen) {
            case GUN -> ZamanHesabi.gunler(bas, bit);
            case HAFTA -> ZamanHesabi.haftalar(bas, bit);
            case AY -> ZamanHesabi.aylar(bas, bit);
            case YIL -> {
                List<LocalDate> g = ZamanHesabi.gunler(bas, bit);
                List<LocalDate> l = new ArrayList<>();
                for (int y = g.get(0).getYear(); y <= g.get(g.size() - 1).getYear(); y++) {
                    l.add(LocalDate.of(y, 1, 1));
                }
                yield l;
            }
            default -> throw new IllegalArgumentException("Takvim penceresi değil: " + pen);
        };
    }

    /** Ölçülen bir pencere: [bas, son) gün aralığı; {@code anahtar} ihlal metnindeki etiket için. */
    private record Pen(Object anahtar, LocalDate bas, LocalDate son) {
    }

    /** {@code tarama} aralığının dokunduğu pencereler: takvim pencereleri ya da (KAYAN) dokunulan günü kapsayan her N günlük aralık. */
    private static List<Pen> pencereler(OzelKural k, Aralik tarama) {
        List<Pen> l = new ArrayList<>(4);
        if (k.pencere() == Pencere.KAYAN) {
            int n = k.kayanGun();
            LocalDate ilk = ZamanHesabi.gun(tarama.bas());
            LocalDate son = ZamanHesabi.gun(tarama.bit().minusNanos(1));
            for (LocalDate s = ilk.minusDays(n - 1L); !s.isAfter(son); s = s.plusDays(1)) {
                l.add(new Pen(s, s, s.plusDays(n)));
            }
        } else {
            for (LocalDate w : pencereBaslari(k.pencere(), tarama.bas(), tarama.bit())) {
                l.add(new Pen(w, w, pencereSon(k.pencere(), w)));
            }
        }
        return l;
    }

    private static String etiket(OzelKural k, Object anahtar) {
        return switch (k.pencere()) {
            case SEFER -> "";
            case NOBET -> "nöbet " + ZamanHesabi.an((Instant) anahtar);
            case GUN -> tarih((LocalDate) anahtar);
            case HAFTA -> CalismaSuresiKurallari.hafta((LocalDate) anahtar);
            case AY -> ZamanHesabi.ay((LocalDate) anahtar);
            case YIL -> ((LocalDate) anahtar).getYear() + " yılı";
            case KAYAN -> k.pencereMetni() + " " + tarih((LocalDate) anahtar) + " – " + tarih(((LocalDate) anahtar).plusDays(k.kayanGun() - 1L));
            case SERI -> {
                LocalDate[] a = (LocalDate[]) anahtar;
                yield "üst üste " + tarih(a[0]) + " – " + tarih(a[1]);
            }
        };
    }

    // ---------- bir kontrol cagrisinin hesabi ----------

    /** Bir kapsamın süzülmüş görünümü: kapsama giren görevler, bunların nöbetleri ve çalışma aralıkları. */
    private static final class Kapsamli {
        final List<Nobet> nobetler;
        final Nobet adayNobet;
        final List<Aralik> araliklar;
        final List<Gorev> gorevler;
        /** Adayın dokunduğu pencereleri belirleyen aralık. */
        final Aralik alan;
        private GunDizisi gun;
        private GunDizisi.Sayac kalkisSayac;
        private GunDizisi.Sayac geceNobetSayac;
        private Set<LocalDate> geceGunleri;

        Kapsamli(List<Gorev> gorevler, List<Nobet> nobetler, Nobet adayNobet, List<Aralik> araliklar, Aralik alan) {
            this.gorevler = gorevler;
            this.nobetler = nobetler;
            this.adayNobet = adayNobet;
            this.araliklar = araliklar;
            this.alan = alan;
        }

        GunDizisi gun() {
            if (gun == null) {
                gun = GunDizisi.of(araliklar);
            }
            return gun;
        }

        /** Görev kalkışlarının gün başına sayımı (kayan pencere: pencere başına görev döngüsü yerine önek toplamı). */
        GunDizisi.Sayac kalkisSayac() {
            if (kalkisSayac == null) {
                List<Instant> anlar = new ArrayList<>(gorevler.size());
                gorevler.forEach(g -> {
                    if (!g.yolculuk()) { // GOREV_SAYISI yolculuğu saymaz (MK-40)
                        anlar.add(g.kalkis());
                    }
                });
                kalkisSayac = gun().sayac(anlar);
            }
            return kalkisSayac;
        }

        GunDizisi.Sayac geceNobetSayac(Parametreler p) {
            if (geceNobetSayac == null) {
                List<Instant> anlar = new ArrayList<>();
                for (Nobet n : nobetler) {
                    if (GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p)) {
                        anlar.add(n.baslangic());
                    }
                }
                geceNobetSayac = gun().sayac(anlar);
            }
            return geceNobetSayac;
        }

        /** Gece nöbeti başlayan günler (ardışık gece günü serisi). */
        Set<LocalDate> geceGunleri(Parametreler p) {
            if (geceGunleri == null) {
                geceGunleri = new HashSet<>();
                for (Nobet n : nobetler) {
                    if (GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p)) {
                        geceGunleri.add(geceGunu(n.baslangic(), p));
                    }
                }
            }
            return geceGunleri;
        }
    }

    /** En kötü pencereyi biriktirir: eşiği en çok aşan; aşan yoksa ihlal yok. */
    private static final class EnKotu {
        final boolean fazla;
        final long esik;
        boolean var;
        long asim;
        long olculen;
        Object anahtar;

        EnKotu(boolean fazla, long esik) {
            this.fazla = fazla;
            this.esik = esik;
        }

        void ekle(Object pencereAnahtari, long deger) {
            long a = fazla ? deger - esik : esik - deger;
            if (a > 0 && (!var || a > asim)) {
                var = true;
                asim = a;
                olculen = deger;
                anahtar = pencereAnahtari;
            }
        }
    }

    private static final class Hesap {
        final KuralBaglami b;
        final Onhesap h;
        final Parametreler p;
        final boolean bekleme;
        final long ayirma;
        private Boolean adayGece;
        private Set<Gorev> geceGorevleri;
        private final Map<Kapsam, Kapsamli> kapsamlar = new HashMap<>(4);

        Hesap(KuralBaglami b, Onhesap h) {
            this.b = b;
            this.h = h;
            this.p = b.parametreler();
            this.bekleme = p.bayrak("nobet.bekleme_sayilir");
            this.ayirma = p.dakika("nobet.ayirma_saat");
        }

        boolean adayKapsamda(Kapsam k) {
            if (k.tumu()) {
                return true;
            }
            if (!k.gorevKapsar(b.aday())) {
                return false;
            }
            return switch (k.vardiya()) {
                case HEPSI -> true;
                case GECE -> adayGece();
                case GUNDUZ -> !adayGece();
            };
        }

        private boolean adayGece() {
            if (adayGece == null) {
                adayGece = GeceKurallari.geceMi(GeceKurallari.ozet(h.adayNobet(), p), p);
            }
            return adayGece;
        }

        /** Nöbet kapsamda mı: kapsama giren görevi var ve (vardiya süzülüyorsa) nöbetin sınıfı uyuyor. Yalnız nöbetler arası dinlenme için. */
        private boolean nobetKapsamda(Kapsam k, Nobet n) {
            if (k.tumu()) {
                return true;
            }
            boolean var = false;
            for (Gorev g : n.gorevler()) {
                if (k.gorevKapsar(g)) {
                    var = true;
                    break;
                }
            }
            if (!var) {
                return false;
            }
            return switch (k.vardiya()) {
                case HEPSI -> true;
                case GECE -> GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p);
                case GUNDUZ -> !GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p);
            };
        }

        private Set<Gorev> geceGorevleri() {
            if (geceGorevleri == null) {
                geceGorevleri = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Nobet n : h.tumNobet()) {
                    if (GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p)) {
                        geceGorevleri.addAll(n.gorevler());
                    }
                }
            }
            return geceGorevleri;
        }

        Kapsamli kapsamli(Kapsam k) {
            Kapsamli ks = kapsamlar.get(k);
            if (ks == null) {
                ks = k.tumu() ? new Kapsamli(h.siraliTum(), h.tumNobet(), h.adayNobet(), h.araliklar(), h.alan()) : suz(k);
                kapsamlar.put(k, ks);
            }
            return ks;
        }

        /** V4/V6: yalnız kapsama giren görevler sayılır, nöbetler süzülmüş listeden yeniden türetilir. */
        private Kapsamli suz(Kapsam k) {
            Set<Gorev> gece = k.vardiya() == Vardiya.HEPSI ? null : geceGorevleri();
            boolean geceIstenen = k.vardiya() == Vardiya.GECE;
            List<Gorev> f = new ArrayList<>();
            for (Gorev g : h.siraliTum()) {
                if (k.gorevKapsar(g) && (gece == null || gece.contains(g) == geceIstenen)) {
                    f.add(g);
                }
            }
            List<Nobet> nobetler = NobetHesabi.gruplaSirali(f, ayirma);
            Nobet aday = NobetHesabi.nobetiBul(nobetler, b.aday());
            Aralik alan = bekleme ? new Aralik(aday.baslangic(), aday.bitis()) : new Aralik(b.aday().baslangic(), b.aday().bitis());
            if (gece != null) { // nöbet sınıfı komşu birleşmesiyle değişir: adayın tüm nöbeti taranır (MK-5)
                Nobet tam = h.adayNobet();
                alan = new Aralik(alan.bas().isBefore(tam.baslangic()) ? alan.bas() : tam.baslangic(), alan.bit().isAfter(tam.bitis()) ? alan.bit() : tam.bitis());
            }
            return new Kapsamli(f, nobetler, aday, NobetHesabi.araliklar(nobetler, bekleme), alan);
        }

        /** Kuralı değerlendirir; ihlal varsa (en kötü pencere için) listeye ekler. */
        void olc(OzelKural k, List<Ihlal> o) {
            boolean fazla = k.karsilastirma() == Karsilastirma.EN_FAZLA;
            EnKotu e = new EnKotu(fazla, k.esikDeger());
            Pencere pen = k.pencere();
            if (k.olcu() == Olcu.NOBETLER_ARASI_DINLENME) {
                dinlenme(k, e);
            } else {
                Kapsamli ks = kapsamli(k.kapsam());
                switch (k.olcu()) {
                    case TEK_SEFER_SURESI -> {
                        if (!b.aday().yolculuk()) { // yolculuk sürüş değil (K-21 ile aynı; MK-40)
                            e.ekle(null, Duration.between(b.aday().kalkis(), b.aday().varis()).toMinutes());
                        }
                    }
                    case TOPLAM_GOREV_SURESI -> {
                        if (pen == Pencere.NOBET) {
                            e.ekle(ks.adayNobet.baslangic(), GeceKurallari.ozet(ks.adayNobet, p)[0]);
                        } else {
                            GunDizisi gd = ks.gun();
                            for (Pen w : pencereler(k, ks.alan)) {
                                e.ekle(w.anahtar(), gd.toplam(w.bas(), w.son()));
                            }
                        }
                    }
                    case GECE_SURESI -> {
                        if (pen == Pencere.NOBET) {
                            e.ekle(ks.adayNobet.baslangic(), GeceKurallari.ozet(ks.adayNobet, p)[1]);
                        } else {
                            GunDizisi gd = ks.gun();
                            LocalTime gb = p.saat("gece.baslangic");
                            LocalTime ge = p.saat("gece.bitis");
                            for (Pen w : pencereler(k, ks.alan)) {
                                e.ekle(w.anahtar(), gd.gece(w.bas(), w.son(), gb, ge));
                            }
                        }
                    }
                    case GOREV_SAYISI -> gorevSayisi(k, ks, e);
                    case GECE_NOBETI_SAYISI -> geceNobetiSayisi(k, ks, e);
                    case ARDISIK_CALISMA_GUNU -> {
                        GunDizisi gd = ks.gun();
                        seri(e, gd::gorevli, ZamanHesabi.gun(ks.alan.bas()), ZamanHesabi.gun(ks.alan.bit().minusNanos(1)));
                    }
                    case ARDISIK_GECE_GUNU -> {
                        if (GeceKurallari.geceMi(GeceKurallari.ozet(ks.adayNobet, p), p)) { // gündüz nöbeti gece serisine katılmaz
                            Set<LocalDate> gunler = ks.geceGunleri(p);
                            LocalDate d = geceGunu(ks.adayNobet.baslangic(), p);
                            seri(e, gunler::contains, d, d);
                        }
                    }
                    case GOREVSIZ_ARALIK -> {
                        for (Pen w : pencereler(k, ks.alan)) {
                            e.ekle(w.anahtar(), CalismaSuresiKurallari.enUzunBosluk(ks.araliklar, ZamanHesabi.gunBasi(w.bas()), ZamanHesabi.gunBasi(w.son())));
                        }
                    }
                    case IZIN_GUNU -> {
                        GunDizisi gd = ks.gun();
                        for (Pen w : pencereler(k, ks.alan)) {
                            e.ekle(w.anahtar(), java.time.temporal.ChronoUnit.DAYS.between(w.bas(), w.son()) - gd.gorevliGun(w.bas(), w.son()));
                        }
                    }
                    case NOBETLER_ARASI_DINLENME -> throw new IllegalStateException("yukarıda ele alındı");
                }
            }
            if (!e.var) {
                return;
            }
            Olcu ol = k.olcu();
            boolean sure = ol.sure();
            double olculen = sure ? e.olculen / 60.0 : e.olculen;
            double esik = sure ? e.esik / 60.0 : e.esik;
            String et = etiket(k, e.anahtar);
            String metin = ol.ad() + (et.isEmpty() ? "" : " (" + et + ")") + ": " + goster(ol, e.olculen) + (fazla ? " > " : " < ") + goster(ol, e.esik)
                    + " (Kural: " + k.ad() + ")";
            Ihlal.ekle(o, p, k.kod(), olculen, esik, ol.birim(), metin);
        }

        private void gorevSayisi(OzelKural k, Kapsamli ks, EnKotu e) {
            if (b.aday().yolculuk()) {
                return; // yolculuk görev sayısına girmez (MK-40): aday sayıyı artırmaz
            }
            Instant kalkis = b.aday().kalkis();
            // sayı adayın KALKIŞ gününe yazılır; vardiya süzmesinde nöbetin tamamı taranır (sınıf değişebilir)
            Aralik tarama = k.kapsam().vardiya() == Vardiya.HEPSI ? new Aralik(kalkis, kalkis.plusSeconds(1)) : ks.alan;
            boolean kayan = k.pencere() == Pencere.KAYAN;
            for (Pen w : pencereler(k, tarama)) {
                if (kayan) {
                    e.ekle(w.anahtar(), ks.kalkisSayac().toplam(w.bas(), w.son()));
                    continue;
                }
                Instant bas = ZamanHesabi.gunBasi(w.bas());
                Instant son = ZamanHesabi.gunBasi(w.son());
                long adet = 0;
                for (Gorev g : ks.gorevler) {
                    if (!g.yolculuk() && !g.kalkis().isBefore(bas) && g.kalkis().isBefore(son)) {
                        adet++;
                    }
                }
                e.ekle(w.anahtar(), adet);
            }
        }

        private void geceNobetiSayisi(OzelKural k, Kapsamli ks, EnKotu e) {
            if (!GeceKurallari.geceMi(GeceKurallari.ozet(ks.adayNobet, p), p)) {
                return; // gündüz nöbeti gece nöbeti sayısını artırmaz (önceden var olan fazlalık adaya yazılmaz)
            }
            Instant baslangic = ks.adayNobet.baslangic();
            boolean kayan = k.pencere() == Pencere.KAYAN;
            for (Pen w : pencereler(k, new Aralik(baslangic, baslangic.plusSeconds(1)))) {
                if (kayan) {
                    e.ekle(w.anahtar(), ks.geceNobetSayac(p).toplam(w.bas(), w.son()));
                    continue;
                }
                Instant bas = ZamanHesabi.gunBasi(w.bas());
                Instant son = ZamanHesabi.gunBasi(w.son());
                long adet = 0;
                for (Nobet n : ks.nobetler) {
                    if (!n.baslangic().isBefore(bas) && n.baslangic().isBefore(son) && GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p)) {
                        adet++;
                    }
                }
                e.ekle(w.anahtar(), adet);
            }
        }

        /** Adayın günlerini [ilk, son] içeren aralıksız seri (iki yönde genişletilir); ölçülen = seri uzunluğu. */
        private static void seri(EnKotu e, java.util.function.Predicate<LocalDate> dolu, LocalDate ilk, LocalDate son) {
            LocalDate bas = ilk;
            while (dolu.test(bas.minusDays(1))) {
                bas = bas.minusDays(1);
            }
            LocalDate bit = son;
            while (dolu.test(bit.plusDays(1))) {
                bit = bit.plusDays(1);
            }
            e.ekle(new LocalDate[] { bas, bit }, java.time.temporal.ChronoUnit.DAYS.between(bas, bit) + 1);
        }

        /** Nöbetler arası dinlenme: adayın nöbetinin önceki ve sonraki NÖBETLE (tüm görevlerden) arasındaki boşluk; en kötü uç raporlanır. */
        private void dinlenme(OzelKural k, EnKotu e) {
            List<Nobet> tum = h.tumNobet();
            Nobet n = h.adayNobet();
            int i = 0;
            while (tum.get(i) != n) {
                i++;
            }
            boolean icerde = nobetKapsamda(k.kapsam(), n);
            if (i > 0 && (icerde || nobetKapsamda(k.kapsam(), tum.get(i - 1)))) {
                e.ekle(n.baslangic(), Duration.between(tum.get(i - 1).bitis(), n.baslangic()).toMinutes());
            }
            if (i < tum.size() - 1 && (icerde || nobetKapsamda(k.kapsam(), tum.get(i + 1)))) {
                e.ekle(n.baslangic(), Duration.between(n.bitis(), tum.get(i + 1).baslangic()).toMinutes());
            }
        }

        private static String goster(Olcu o, long deger) {
            return o.sure() ? sure(deger) : o.birim().equals("gün") ? deger + " gün" : Long.toString(deger);
        }
    }
}
