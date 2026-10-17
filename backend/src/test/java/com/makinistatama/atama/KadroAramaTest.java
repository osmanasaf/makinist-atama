package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.KadroArama.Baglam;
import com.makinistatama.atama.KadroArama.Grup;
import com.makinistatama.atama.OtomatikAtama.Bos;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.VarsayilanParametreler;

import static com.makinistatama.atama.OtomatikAtamaTest.ADLAR;
import static com.makinistatama.atama.OtomatikAtamaTest.an;

/** F3a (saf, sahte planlayıcı, DB'siz): sanal (tek bröveli ve GENEL) makinist, sentetik izin, doluluk eğrisi (OK-22): hedef bazında en küçük koşulmuş ön ek, belirleyicilik. */
class KadroAramaTest {

    static final LocalDate BAS = LocalDate.of(2026, 10, 12);
    static final LocalDate BIT = LocalDate.of(2026, 11, 8); // 28 gün
    static final long HAT = 10;
    static final long ARAC = 20;

    static Baglam baglam(Map<Long, SeferGirdisi> seferler, int gercek) {
        return new Baglam(seferler, BAS, BIT, 13, gercek, null);
    }

    // ---------------------------------------------------------------- (a) sanal makinist: gerçek kural motoruyla

    private static Gorev gorev(long id, long kalkisGar, long varisGar, long hat, long arac, String bas, String bit) {
        return new Gorev(id, kalkisGar, varisGar, hat, arac, an(bas), an(bit), an(bas), an(bit), 100);
    }

    private static List<String> kodlar(Parametreler p, MakinistGirdisi m, Gorev g) {
        return KuralMotoru.degerlendirYazma(new KuralBaglami(g, List.of(), m.bilgi(), m.hatSon(), m.genelSon(), ADLAR, p, null)).stream().map(Ihlal::kod).toList();
    }

    @Test
    void sanalMakinist_grubaIhlalsiz_baskaHatVeAraccaYetkisiz_parametredenBagimsiz() {
        Baglam b = baglam(Map.of(), 0);
        MakinistGirdisi s = KadroArama.sanal(0, new Grup(1, ARAC, HAT), b);

        assertThat(s.id()).isEqualTo(-1);
        assertThat(s.sicil()).isEqualTo("SANAL-001");
        assertThat(s.bilgi().aracTipiSinavlari()).containsOnlyKeys(ARAC);
        assertThat(s.bilgi().hatBroveleri()).containsOnlyKeys(HAT);
        assertThat(s.bilgi().aracTipiSinavlari().get(ARAC)).isEqualTo(BIT); // sınav = aralık sonu
        assertThat(s.bilgi().ehliyetBitis()).isAfter(BIT);
        assertThat(s.bilgi().kartSonSurus()).isNull();
        assertThat(s.gorevler()).isEmpty();

        Gorev ayni = gorev(1, 1, 2, HAT, ARAC, "2026-10-20T08:00", "2026-10-20T12:00");
        Gorev baskaHat = gorev(2, 1, 2, 11, ARAC, "2026-10-20T08:00", "2026-10-20T12:00");
        Gorev baskaArac = gorev(3, 1, 2, HAT, 21, "2026-10-20T08:00", "2026-10-20T12:00");
        for (String[] ayar : new String[][] { { "60", "18", "18" }, { "1", "1", "1" }, { "240", "120", "120" } }) { // parametre değeri ne olursa olsun
            Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
            d.put("belge.sinav_gecerlilik_ay", ayar[0]);
            d.put("hat.ara_max_ay", ayar[1]);
            d.put("brove.surus_ara_max_ay", ayar[2]);
            Parametreler p = Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER);
            List<String> ihlal = kodlar(p, s, ayni);
            assertThat(ihlal).as("belge/yetki " + String.join("/", ayar)).noneMatch(k -> Set.of("K-01", "K-02", "K-03", "K-04", "K-05", "K-06", "K-07", "K-08", "K-09").contains(k));
            assertThat(kodlar(p, s, baskaHat)).contains("K-06");
            assertThat(kodlar(p, s, baskaArac)).contains("K-05");
        }
        // aralığın son günü ve ertesi güne taşan gece seferi de ihlalsiz
        Gorev son = gorev(4, 1, 2, HAT, ARAC, "2026-11-08T22:00", "2026-11-09T05:00");
        assertThat(kodlar(Parametreler.olustur(VarsayilanParametreler.DEGERLER, VarsayilanParametreler.SIDDETLER), s, son)).noneMatch(k -> k.compareTo("K-10") < 0);
    }

    // ---------------------------------------------------------------- (b) sentetik izin

    private static MakinistBilgisi bilgi(List<MakinistBilgisi.Izin> izin) {
        LocalDate bitis = LocalDate.of(2030, 1, 1);
        return new MakinistBilgisi(1, 1, MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis, Map.of(), Map.of(), null, izin);
    }

    private static int izinGunu(MakinistBilgisi b, LocalDate gun) {
        return b.izinler().stream().anyMatch(i -> i.durum() == MakinistBilgisi.IzinDurumu.ONAY && !gun.isBefore(i.baslangic()) && !gun.isAfter(i.bitis())) ? 1 : 0;
    }

    @Test
    void sentetikIzin_kisiBasinaCeilPN_gercekIzinDusulur_alttanSifir() {
        // p = %13, N = 28 -> ceil(3,64) = 4 gün
        MakinistBilgisi hic = KadroArama.izinli(bilgi(List.of()), 0, BAS, BIT, 13);
        assertThat(java.util.stream.IntStream.range(0, 28).map(i -> izinGunu(hic, BAS.plusDays(i))).sum()).isEqualTo(4);

        // 1 gün gerçek onaylı izin -> 3 gün eklenir; toplam 4 (gerçek izin blokla çakışmasın diye sıra 0 kayığı: blok 0..2, gerçek izin 20. gün)
        MakinistBilgisi gercek = bilgi(List.of(new MakinistBilgisi.Izin(BAS.plusDays(20), BAS.plusDays(20), MakinistBilgisi.IzinDurumu.ONAY)));
        MakinistBilgisi bir = KadroArama.izinli(gercek, 0, BAS, BIT, 13);
        assertThat(java.util.stream.IntStream.range(0, 28).map(i -> izinGunu(bir, BAS.plusDays(i))).sum()).isEqualTo(4);

        // gerçek izin zaten 4+ gün: değişmez; TALEP/RED sayılmaz
        MakinistBilgisi cok = bilgi(List.of(new MakinistBilgisi.Izin(BAS, BAS.plusDays(5), MakinistBilgisi.IzinDurumu.ONAY)));
        assertThat(KadroArama.izinli(cok, 3, BAS, BIT, 13)).isSameAs(cok);
        MakinistBilgisi talep = bilgi(List.of(new MakinistBilgisi.Izin(BAS, BAS.plusDays(5), MakinistBilgisi.IzinDurumu.TALEP)));
        assertThat(KadroArama.izinli(talep, 0, BAS, BIT, 13).izinler()).hasSizeGreaterThan(1);
        // %0: hiç izin eklenmez
        MakinistBilgisi sifir = bilgi(List.of());
        assertThat(KadroArama.izinli(sifir, 5, BAS, BIT, 0)).isSameAs(sifir);
    }

    @Test
    void sentetikIzin_kayikDagilim_herGundeDevamsizYaklasikPYuzdeKadro() {
        for (int kadro : new int[] { 50, 66, 100, 37 }) {
            List<MakinistBilgisi> kisiler = new ArrayList<>();
            for (int i = 0; i < kadro; i++) {
                kisiler.add(KadroArama.izinli(bilgi(List.of()), i, BAS, BIT, 13));
            }
            int enAz = Integer.MAX_VALUE;
            int enCok = 0;
            for (int g = 0; g < 28; g++) {
                int gun = g;
                int devamsiz = kisiler.stream().mapToInt(k -> izinGunu(k, BAS.plusDays(gun))).sum();
                enAz = Math.min(enAz, devamsiz);
                enCok = Math.max(enCok, devamsiz);
            }
            double beklenen = kadro * 4 / 28.0;
            assertThat(enAz).as("kadro " + kadro).isGreaterThanOrEqualTo((int) Math.floor(beklenen) - 1);
            assertThat(enCok).as("kadro " + kadro).isLessThanOrEqualTo((int) Math.ceil(beklenen) + 1);
        }
    }

    @Test
    void sentetikIzin_biriEklenipCikarilincaDigerlerininBloguDegismez() {
        MakinistBilgisi once = KadroArama.izinli(bilgi(List.of()), 7, BAS, BIT, 13);
        MakinistBilgisi sonra = KadroArama.izinli(bilgi(List.of()), 7, BAS, BIT, 13);
        assertThat(sonra).isEqualTo(once); // blok yalnız sıraya ve parametreye bağlı
        // sanal sıra gerçek sayısından devam eder: farklı gerçek sayısı farklı ofset ama ayni (ekleSira, gercek) ayni sonuc
        Baglam b = baglam(Map.of(), 66);
        assertThat(KadroArama.sanal(2, new Grup(1, ARAC, HAT), b)).isEqualTo(KadroArama.sanal(2, new Grup(1, ARAC, HAT), b));
        assertThat(KadroArama.sanal(2, new Grup(1, ARAC, HAT), b).bilgi().izinler()).isNotEqualTo(KadroArama.sanal(3, new Grup(1, ARAC, HAT), b).bilgi().izinler());
    }

    // ---------------------------------------------------------------- sahte dünya

    /**
     * Sahte planlayıcı: her grubun sefer sayısı ve sanal makinist başına karşılama kapasitesi; boş = {@code max(0, sefer − kapasite × kişi)}, kişi = gruba ait makinist.
     * Boş seferler grup içinde kimlik sırasıyla. İş parçacığı güvenlidir (durumsuz okuma), çağrı sayısını sayar.
     */
    static final class Sahte {
        final Map<Long, SeferGirdisi> seferler = new HashMap<>();
        final Map<Grup, List<Long>> grupSeferleri = new HashMap<>();
        final Map<Grup, Integer> kapasite = new HashMap<>();
        final AtomicInteger cagri = new AtomicInteger();
        long sonId = 1;

        Grup grup(long gar, int seferSayisi, int kisiBasinaKapasite) {
            Grup g = new Grup(gar, ARAC, HAT);
            List<Long> idler = new ArrayList<>();
            for (int i = 0; i < seferSayisi; i++) {
                long id = sonId++;
                Gorev gr = gorev(id, gar, gar + 100, HAT, ARAC, "2026-10-20T08:00", "2026-10-20T09:00");
                seferler.put(id, new SeferGirdisi(id, LocalDate.of(2026, 10, 20), YearMonth.of(2026, 10), gr));
                idler.add(id);
            }
            grupSeferleri.put(g, idler);
            kapasite.put(g, kisiBasinaKapasite);
            return g;
        }

        Sonuc planla(List<MakinistGirdisi> kadro, long butce) {
            cagri.incrementAndGet();
            Map<Grup, Integer> kisi = new HashMap<>();
            for (MakinistGirdisi m : kadro) {
                kisi.merge(new Grup(m.bilgi().bagliGarId(), m.bilgi().aracTipiSinavlari().keySet().iterator().next(), m.bilgi().hatBroveleri().keySet().iterator().next()), 1,
                        Integer::sum);
            }
            List<Bos> bos = new ArrayList<>();
            grupSeferleri.keySet().stream().sorted().forEach(g -> {
                int bosSayi = Math.max(0, grupSeferleri.get(g).size() - kapasite.get(g) * kisi.getOrDefault(g, 0));
                grupSeferleri.get(g).stream().limit(bosSayi).forEach(id -> bos.add(new Bos(id, "kapasite", List.of())));
            });
            bos.sort(java.util.Comparator.comparingLong(Bos::seferId));
            return new Sonuc(List.of(), bos);
        }

        Baglam baglam() {
            return KadroAramaTest.baglam(seferler, 0);
        }
    }

    private static MakinistGirdisi gercek(Grup g, long id) {
        MakinistBilgisi b = new MakinistBilgisi(id, g.garId(), MakinistBilgisi.Durum.AKTIF, BIT, BIT, BIT, Map.of(g.aracTipiId(), BIT), Map.of(g.hatId(), new MakinistBilgisi.HatBrove(BIT, null)),
                null, List.of());
        return new MakinistGirdisi(id, "D" + id, b, List.of(), Map.of(), null, Map.of());
    }

    private static long adet(List<Grup> l, Grup g) {
        return l.stream().filter(g::equals).count();
    }

    // ---------------------------------------------------------------- OK-22: genel makinist, izin sarması, doluluk eğrisi

    @Test
    void genelSanalMakinist_tarifedekiTumBroveler_hicbirHatVeAracaYetkisizDegil() {
        Baglam b = new Baglam(Map.of(), BAS, BIT, 13, 0, new KadroArama.Broveler(Set.of(ARAC, 21L), Set.of(HAT, 11L)));
        MakinistGirdisi s = KadroArama.sanal(0, new Grup(1, 0, 0), b);

        assertThat(s.bilgi().aracTipiSinavlari()).containsOnlyKeys(ARAC, 21L);
        assertThat(s.bilgi().hatBroveleri()).containsOnlyKeys(HAT, 11L);
        assertThat(s.bilgi().bagliGarId()).isEqualTo(1);
        Parametreler p = Parametreler.olustur(VarsayilanParametreler.DEGERLER, VarsayilanParametreler.SIDDETLER);
        for (long hat : new long[] { HAT, 11 }) {
            for (long arac : new long[] { ARAC, 21 }) {
                assertThat(kodlar(p, s, gorev(1, 1, 2, hat, arac, "2026-10-20T08:00", "2026-10-20T12:00"))).as("hat " + hat + " araç " + arac)
                        .noneMatch(k -> Set.of("K-01", "K-02", "K-03", "K-04", "K-05", "K-06", "K-07", "K-08", "K-09").contains(k));
            }
        }
        assertThat(kodlar(p, s, gorev(2, 1, 2, 12, ARAC, "2026-10-20T08:00", "2026-10-20T12:00"))).as("tarifede olmayan hat").contains("K-06");
    }

    /** Review W5 (F5): sentetik izin bloğu aralık sonunda başa SARAR (28 gün, %10 -> 3 gün, sıra 9 -> ofset 27: gün 27, 0, 1). */
    @Test
    void sentetikIzin_aralikSonunuAsanBlokBasaSarar() {
        MakinistBilgisi b = KadroArama.izinli(bilgi(List.of()), 9, BAS, BIT, 10);
        assertThat(izinGunu(b, BAS.plusDays(27))).isEqualTo(1);
        assertThat(izinGunu(b, BAS)).isEqualTo(1);
        assertThat(izinGunu(b, BAS.plusDays(1))).isEqualTo(1);
        assertThat(izinGunu(b, BAS.plusDays(2))).isZero();
        assertThat(java.util.stream.IntStream.range(0, 28).map(i -> izinGunu(b, BAS.plusDays(i))).sum()).isEqualTo(3);
    }

    @Test
    void zincir_bosTalepDakikasiylaOrantili_ilkNEleman_nKisilikDagilim() {
        Sahte s = new Sahte();
        Grup a = s.grup(1, 6, 1); // 6 saat boş
        Grup b = s.grup(2, 2, 1); // 2 saat boş
        List<Grup> z = KadroArama.zincir(s.planla(List.of(), 0), s.baglam(), 8);

        assertThat(adet(z, a)).isEqualTo(6); // D'Hondt: 3:1 oranı
        assertThat(adet(z, b)).isEqualTo(2);
        assertThat(z.subList(0, 4)).containsExactly(a, a, a, b); // 6, 3, 2 (B de 2: eşitte küçük gar A önce), sonra B(2) > A(1,5)
        // eşit ağırlıkta küçük grup kimliği kazanır (belirleyici)
        assertThat(KadroArama.zincir(s.planla(List.of(), 0), s.baglam(), 8)).isEqualTo(z);
    }

    @Test
    void egri_hedefBasinaEnKucukKosulmusOnEk_gevsekHedefSikidenKucukVeyaEsit() {
        Sahte s = new Sahte();
        Grup a = s.grup(1, 10, 3); // 4 kişi gerekir
        Grup b = s.grup(2, 4, 3); // 2 kişi

        ExecutorService havuz = Executors.newFixedThreadPool(3);
        KadroArama.Egri e = KadroArama.egri(List.of(), s.baglam(), s::planla, List.of(6, 2, 0), 16, havuz);
        havuz.shutdown();

        Map<Integer, Sonuc> nokta = e.noktalar();
        assertThat(nokta).containsKey(0); // mevcut kadro (tur 0) her zaman eğrinin başı
        assertThat(e.hedefAdet().get(0)).isBetween(6, 8); // 4 + 2 kişi gerekir; sabit orantılı zincir en çok 1-2 fazla ister
        for (int hedef : List.of(6, 2, 0)) {
            int adet = e.hedefAdet().get(hedef);
            assertThat(nokta.get(adet).bosKalanlar().size()).as("hedef " + hedef).isLessThanOrEqualTo(hedef);
            assertThat(nokta.keySet().stream().filter(k -> k < adet).map(k -> nokta.get(k).bosKalanlar().size())).as("daha küçük hiçbir koşulmuş ön ek hedefi sağlamaz").allMatch(x -> x > hedef);
        }
        assertThat(e.hedefAdet().get(6)).isLessThanOrEqualTo(e.hedefAdet().get(2)).isLessThanOrEqualTo(e.hedefAdet().get(0));
        assertThat(e.kosuSayisi()).isEqualTo(s.cagri.get());
        assertThat(e.kosuSayisi()).as("tur 0 + basamak (2,4,8,16) + ince tur: bütçeli ve veriden bağımsız").isLessThanOrEqualTo(1 + 4 + 3 * 3 * KadroArama.INCE_TUR);
    }

    @Test
    void egri_ulasilamayanHedef_null_mevcutZatenSaglarsaSifir() {
        Sahte s = new Sahte();
        s.grup(1, 6, 3);
        s.grup(2, 5, 0); // hiçbir kişi bu grubu kapatamaz

        ExecutorService havuz = Executors.newSingleThreadExecutor();
        KadroArama.Egri e = KadroArama.egri(List.of(), s.baglam(), s::planla, List.of(11, 5, 0), 16, havuz);
        havuz.shutdown();

        assertThat(e.hedefAdet().get(11)).isZero(); // 6 + 5 = 11 boş: mevcut kadro zaten kabul edilen sınırda
        assertThat(e.hedefAdet().get(5)).isNotNull().isPositive();
        assertThat(e.hedefAdet().get(0)).isNull(); // ulaşılamadı
    }

    /** Eğri iş parçacığı sayısından ve tamamlanma sırasından bağımsızdır (aynı noktalar, aynı hedefler). */
    @Test
    void egri_tekIsParcacigiIleCokIsParcacigiAyniSonuc() {
        Map<Integer, List<Integer>> sonuclar = new HashMap<>();
        for (int parcacik : new int[] { 1, 2, 5 }) {
            Sahte s = new Sahte();
            s.grup(3, 13, 2);
            s.grup(1, 21, 2);
            s.grup(2, 9, 2);
            ExecutorService havuz = Executors.newFixedThreadPool(parcacik);
            KadroArama.Egri e = KadroArama.egri(List.of(), s.baglam(), (k, bu) -> {
                try {
                    Thread.sleep(k.size() % 3 * 5L); // tamamlanma sırasını karıştır
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
                return s.planla(k, bu);
            }, List.of(8, 3, 0), 32, havuz);
            havuz.shutdown();
            List<Integer> ozet = new ArrayList<>();
            e.noktalar().forEach((n, r) -> {
                ozet.add(n);
                ozet.add(r.bosKalanlar().size());
            });
            e.hedefAdet().forEach((h, n) -> ozet.add(n == null ? -1 : n));
            sonuclar.put(parcacik, ozet);
        }
        assertThat(sonuclar.get(2)).isEqualTo(sonuclar.get(1));
        assertThat(sonuclar.get(5)).isEqualTo(sonuclar.get(1));
    }

    /** Büyük sayılarda daraltma toleranslıdır (+40 için ±%12): sonuç yine KOŞULMUŞ ve hedefi sağlayan sayıdır; koşu sayısı tam daraltmadan azdır. */
    @Test
    void egri_buyukSayidaToleransliDaralma_kosulmusVeSaglayanSayi() {
        Sahte s = new Sahte();
        s.grup(1, 200, 5); // tam 40 kişi gerekir
        ExecutorService havuz = Executors.newFixedThreadPool(3);
        KadroArama.Egri e = KadroArama.egri(List.of(), s.baglam(), s::planla, List.of(0), 64, havuz);
        havuz.shutdown();

        int n = e.hedefAdet().get(0);
        assertThat(n).isBetween(40, 40 + (int) Math.ceil(KadroArama.TOLERANS * 45));
        assertThat(e.noktalar().get(n).bosKalanlar()).isEmpty();
        assertThat(e.noktalar().keySet().stream().filter(k -> k < 40).map(k -> e.noktalar().get(k).bosKalanlar().size())).allMatch(x -> x > 0);
        assertThat(e.kosuSayisi()).as("tur 0 + basamak (2..64) + en çok iki daraltma turu").isLessThanOrEqualTo(1 + 6 + 2 * KadroArama.IC_NOKTA);
    }
}
