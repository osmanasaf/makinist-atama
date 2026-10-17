package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.NobetHesabi;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;

import static com.makinistatama.atama.OtomatikAtamaTest.ADLAR;
import static com.makinistatama.atama.OtomatikAtamaTest.ANKARA;
import static com.makinistatama.atama.OtomatikAtamaTest.ARAC;
import static com.makinistatama.atama.OtomatikAtamaTest.ESKISEHIR;
import static com.makinistatama.atama.OtomatikAtamaTest.EKIM;
import static com.makinistatama.atama.OtomatikAtamaTest.HAT;
import static com.makinistatama.atama.OtomatikAtamaTest.P;
import static com.makinistatama.atama.OtomatikAtamaTest.an;

/**
 * F2d MK-66/MK-67 (saf, DB'siz): sıralama anahtarı ve "sonraki plan görevi kırmızı mı" kapısı. Öncelik yokken çıktı eskisiyle özdeş; doluyken hiçbir yerleştirme aynı
 * makinistin başka bir plan görevini kırmızı yapmaz (KirmiziAtamaHesabi tanımı, testte bağımsız yeniden hesaplanır).
 */
class OtomatikAtamaOncelikTest {

    private static final Instant SIMDI = an("2026-10-10T10:00");
    private static final long HAT2 = 11;
    private static final String[] SAATLER = { "05:00", "08:00", "11:00", "14:00", "17:00", "21:00" };
    private static final long[] KAYDIRMA = { 60, 120, 240, 480 };

    private record Senaryo(Parametreler p, List<MakinistGirdisi> makinistler, List<SeferGirdisi> seferler) {
        int pencere() {
            return BaglamYukleyici.pencereGun(p);
        }

        boolean yillik() {
            return BaglamYukleyici.yillik(p);
        }
    }

    private static OzelKural kural(long id, Olcu olcu, Pencere pencere, Integer kayan, Karsilastirma kars, String esik, Siddet siddet) {
        return new OzelKural(id, "K" + id, olcu, pencere, kayan, kars, new BigDecimal(esik), Kapsam.TUMU, siddet, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    /** Ölçü çeşitliliği: kayan/hafta/ay/seri/izin/nöbet pencereleri (OtomatikAtamaTembelEsdegerTest havuzu). */
    private static List<OzelKural> havuz() {
        Karsilastirma f = Karsilastirma.EN_FAZLA;
        return List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 5, f, "20", Siddet.ENGEL), kural(2, Olcu.GOREV_SAYISI, Pencere.HAFTA, null, f, "6", Siddet.ENGEL),
                kural(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, null, f, "60", Siddet.UYARI), kural(4, Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, null, f, "3", Siddet.ENGEL),
                kural(5, Olcu.IZIN_GUNU, Pencere.HAFTA, null, Karsilastirma.EN_AZ, "2", Siddet.ENGEL), kural(6, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, null, f, "400", Siddet.UYARI),
                kural(7, Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, null, Karsilastirma.EN_AZ, "13", Siddet.ENGEL));
    }

    private static Gorev rastgeleGorev(Random r, long id, LocalDate gun, long kalkisGar) {
        Instant kalkis = gun.atTime(LocalTime.parse(SAATLER[r.nextInt(SAATLER.length)])).atZone(ZamanHesabi.ISTANBUL).toInstant();
        Instant varis = kalkis.plusSeconds(60L * (60 + 30 * r.nextInt(10)));
        long varisGar = r.nextBoolean() ? ANKARA : ESKISEHIR;
        return new Gorev(id, kalkisGar, varisGar, HAT, ARAC, kalkis, varis, kalkis, varis, 50 + 50 * r.nextInt(3));
    }

    private static Senaryo senaryo(long tohum) {
        return senaryo(tohum, false);
    }

    /** {@code yogun}: az makinist, çok sefer, kısa pencere: haftalık/dinlenme kuralları sık dolar (kapı bu senaryolarda devreye girer). */
    private static Senaryo senaryo(long tohum, boolean yogun) {
        Random r = new Random(tohum);
        List<OzelKural> secilen = new ArrayList<>();
        for (int i = r.nextInt(4); i > 0; i--) {
            OzelKural k = havuz().get(r.nextInt(havuz().size()));
            if (secilen.stream().noneMatch(y -> y.id() == k.id())) {
                secilen.add(k);
            }
        }
        Parametreler p = Parametreler.olustur(P.degerler(), P.siddetler(), secilen);
        List<MakinistGirdisi> makinistler = new ArrayList<>();
        for (int m = 0, sayi = yogun ? 2 + r.nextInt(3) : 4 + r.nextInt(8); m < sayi; m++) {
            List<Gorev> gecmis = new ArrayList<>();
            double saat = 0;
            for (int i = r.nextInt(8); i > 0; i--) {
                Gorev g = rastgeleGorev(r, 1000L * (m + 1) + i, LocalDate.of(2026, 10, 1).plusDays(r.nextInt(45)), r.nextBoolean() ? ANKARA : ESKISEHIR);
                gecmis.add(g);
                if (g.kalkis().atZone(ZamanHesabi.ISTANBUL).getMonthValue() == 10) {
                    saat += g.sureDakika() / 60.0;
                }
            }
            List<MakinistBilgisi.Izin> izin = r.nextInt(5) == 0
                    ? List.of(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 14 + r.nextInt(6)), LocalDate.of(2026, 10, 20 + r.nextInt(3)), MakinistBilgisi.IzinDurumu.ONAY))
                    : List.of();
            LocalDate bitis = LocalDate.of(2030, 1, 1);
            MakinistBilgisi bilgi = new MakinistBilgisi(m + 1, r.nextBoolean() ? ANKARA : ESKISEHIR, MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis,
                    Map.of(ARAC, LocalDate.of(2024, 1, 1)), r.nextInt(6) == 0 ? Map.of() : Map.of(HAT, OtomatikAtamaTest.hatBrove()), LocalDate.of(2026, 9, 1), izin);
            Map<YearMonth, double[]> aylik = new HashMap<>();
            aylik.put(EKIM, new double[] { Math.round(saat), 100.0 * r.nextInt(3) });
            makinistler.add(new MakinistGirdisi(m + 1, String.format("D%03d", r.nextInt(2) == 0 ? m + 1 : 100 - m), bilgi, gecmis, Map.of(), null, aylik));
        }
        List<SeferGirdisi> seferler = new ArrayList<>();
        for (int i = 0, n = yogun ? 25 + r.nextInt(25) : 8 + r.nextInt(20); i < n; i++) {
            Gorev g = rastgeleGorev(r, 5000 + i, LocalDate.of(2026, 10, 12).plusDays(r.nextInt(yogun ? 4 : 7)), r.nextBoolean() ? ANKARA : ESKISEHIR);
            LocalDate gun = ZamanHesabi.gun(g.kalkis());
            seferler.add(new SeferGirdisi(g.seferId(), gun, YearMonth.from(gun), g));
        }
        return new Senaryo(p, makinistler, seferler);
    }

    private static Sonuc planla(Senaryo s, Map<Long, Integer> oncelik, long kaydirDk) {
        return OtomatikAtama.planla(s.seferler(), s.makinistler(), ADLAR, s.p(), SIMDI, s.pencere(), s.yillik(), true, List.of(), 0, 0, oncelik, kaydirDk);
    }

    @Test
    void oncelikYok_mevcutCagriylaOzdes_yolculukKapaliVeAcik() {
        int atanan = 0;
        for (long t = 0; t < 30; t++) {
            Senaryo s = senaryo(t);
            Sonuc eski = OtomatikAtama.planla(s.seferler(), s.makinistler(), ADLAR, s.p(), SIMDI, s.pencere(), s.yillik());
            assertThat(planla(s, Map.of(), 0)).as("tohum " + t).isEqualTo(eski);
            assertThat(planla(s, Map.of(), 240)).as("kaydırma tek başına sırayı değiştirmez, tohum " + t).isEqualTo(eski);
            assertThat(planla(s, Map.of(999_999L, 3), 240)).as("bilinmeyen kimlik yok sayılır, tohum " + t).isEqualTo(eski);
            List<YolculukOnerisi.YolculukAdayi> yolcular = List.of(OtomatikAtamaYolculukTest.yolculuk(900, "2026-10-14T06:00", "2026-10-14T08:00"),
                    OtomatikAtamaYolculukTest.yolculuk(901, "2026-10-15T12:00", "2026-10-15T14:00"));
            assertThat(OtomatikAtama.planla(s.seferler(), s.makinistler(), ADLAR, s.p(), SIMDI, s.pencere(), s.yillik(), true, yolcular, 15, 12, Map.of(), 0))
                    .as("yolculuk açık, tohum " + t).isEqualTo(OtomatikAtama.planla(s.seferler(), s.makinistler(), ADLAR, s.p(), SIMDI, s.pencere(), s.yillik(), yolcular, 15, 12));
            atanan += eski.atananlar().size();
        }
        assertThat(atanan).isGreaterThan(100);
    }

    @Test
    void oncelikliSefer_one_alinir_bugunBosKalaniDoldurur() {
        // A her iki hatta, B yalnız HAT'ta yetkili. 101 (HAT) 08:00, 102 (HAT2) 09:00, çakışıyorlar. Bugün: 101 -> A (sicil), 102 -> yalnız A uygun, çakışır -> BOŞ.
        MakinistGirdisi a = makinist(1, "A", Map.of(HAT, OtomatikAtamaTest.hatBrove(), HAT2, OtomatikAtamaTest.hatBrove()));
        MakinistGirdisi b = makinist(2, "B", Map.of(HAT, OtomatikAtamaTest.hatBrove()));
        List<SeferGirdisi> seferler = List.of(sefer(101, HAT, "2026-10-12T08:00", "2026-10-12T10:00"), sefer(102, HAT2, "2026-10-12T09:00", "2026-10-12T11:00"));
        Senaryo s = new Senaryo(P, List.of(a, b), seferler);

        Sonuc bugun = planla(s, Map.of(), 0);
        assertThat(bugun.atananlar()).containsExactly(new Atandi(101, 1));
        assertThat(bugun.bosKalanlar()).extracting(OtomatikAtama.Bos::seferId).containsExactly(102L);

        Sonuc oncelikli = planla(s, Map.of(102L, 1), 120);
        assertThat(oncelikli.atananlar()).containsExactlyInAnyOrder(new Atandi(102, 1), new Atandi(101, 2));
        assertThat(oncelikli.bosKalanlar()).isEmpty();
    }

    @Test
    void oncelikliSiradaKirmiziKapisi_sonrakiGorevKirmiziYapanAdayElenir() {
        // A ve B Ankara'da, 101 (Ankara->Eskişehir 06:00-08:00) ve 102 (Eskişehir->Ankara 09:00-11:00) ile 103 (Ankara->Ankara 11:30-12:30).
        // Öncelik 103'ü öne alır; sonra gelen yerleştirmeler hiçbir plan görevini kırmızı yapmamalı (bağımsız hesap).
        MakinistGirdisi a = makinist(1, "A", Map.of(HAT, OtomatikAtamaTest.hatBrove()));
        MakinistGirdisi b = makinist(2, "B", Map.of(HAT, OtomatikAtamaTest.hatBrove()));
        List<SeferGirdisi> seferler = List.of(sefer(101, ANKARA, ESKISEHIR, HAT, "2026-10-12T06:00", "2026-10-12T08:00"),
                sefer(102, ESKISEHIR, ANKARA, HAT, "2026-10-12T09:00", "2026-10-12T11:00"), sefer(103, ANKARA, ANKARA, HAT, "2026-10-12T11:30", "2026-10-12T12:30"));
        Senaryo s = new Senaryo(P, List.of(a, b), seferler);
        Sonuc r = planla(s, Map.of(103L, 3, 102L, 2), 480);
        assertThat(kirmizilar(s, r)).isEmpty();
    }

    @Test
    void rastgele_oncelikliPlandaKirmiziYok_herSeferEnCokBirKez() {
        int kontrol = 0;
        int farkli = 0;
        for (long t = 0; t < 100; t++) { // çift tohum seyrek, tek tohum yoğun senaryo
            Senaryo s = senaryo(t, t % 2 == 1);
            Sonuc taban = planla(s, Map.of(), 0);
            assertThat(kirmizilar(s, taban)).as("kontrol (bugünkü plan) tohum " + t).isEmpty();
            Random r = new Random(10_000 + t);
            for (int k = 0; k < 4; k++) {
                Map<Long, Integer> oncelik = new HashMap<>();
                for (SeferGirdisi g : s.seferler()) {
                    if (r.nextInt(3) == 0) {
                        oncelik.put(g.seferId(), 1 + r.nextInt(5));
                    }
                }
                if (oncelik.isEmpty()) {
                    oncelik.put(s.seferler().getFirst().seferId(), 3);
                }
                Sonuc sonuc = planla(s, oncelik, KAYDIRMA[k]);
                Set<Long> goruldu = new HashSet<>();
                sonuc.atananlar().forEach(a -> assertThat(goruldu.add(a.seferId())).as("sefer " + a.seferId() + " iki kez atanmış").isTrue());
                assertThat(sonuc.atananlar().size() + sonuc.bosKalanlar().size()).isEqualTo(s.seferler().size());
                assertThat(kirmizilar(s, sonuc)).as("tohum " + t + " öncelik " + oncelik + " kaydırma " + KAYDIRMA[k]).isEmpty();
                kontrol += sonuc.atananlar().size();
                farkli += sonuc.equals(taban) ? 0 : 1;
            }
        }
        assertThat(kontrol).isGreaterThan(500);
        assertThat(farkli).as("duyarlılık: öncelik planı gerçekten değiştiriyor").isGreaterThan(20);
    }

    @Test
    void oncelikVeYolculukBirlikte_reddedilir() {
        Senaryo s = senaryo(1);
        List<YolculukOnerisi.YolculukAdayi> yolcular = List.of(OtomatikAtamaYolculukTest.yolculuk(900, "2026-10-14T06:00", "2026-10-14T08:00"));
        Long id = s.seferler().getFirst().seferId();
        assertThatThrownBy(() -> OtomatikAtama.planla(s.seferler(), s.makinistler(), ADLAR, s.p(), SIMDI, s.pencere(), s.yillik(), true, yolcular, 15, 12, Map.of(id, 1), 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtomatikAtama.planla(s.seferler(), s.makinistler(), ADLAR, s.p(), SIMDI, s.pencere(), s.yillik(), false, List.of(), 0, 0, Map.of(id, 1), 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- bağımsız kırmızı hesabı (KirmiziAtamaHesabi tanımı: sorumlu komşular + degerlendir(simdi=null)) ----------

    /** Plandaki her atamanın ENGEL'li olup olmadığını makinistin NİHAİ görev listesiyle yeniden hesaplar; kırmızı olanların "makinist/sefer" listesini döner. */
    private static List<String> kirmizilar(Senaryo s, Sonuc sonuc) {
        Map<Long, SeferGirdisi> sefer = new HashMap<>();
        s.seferler().forEach(g -> sefer.put(g.seferId(), g));
        Map<Long, MakinistGirdisi> mak = new HashMap<>();
        Map<Long, List<Gorev>> son = new HashMap<>();
        for (MakinistGirdisi m : s.makinistler()) {
            mak.put(m.id(), m);
            son.put(m.id(), new ArrayList<>(m.gorevler()));
        }
        sonuc.atananlar().forEach(a -> son.get(a.makinistId()).add(sefer.get(a.seferId()).gorev()));
        List<String> kirmizi = new ArrayList<>();
        for (Atandi a : sonuc.atananlar()) {
            SeferGirdisi g = sefer.get(a.seferId());
            MakinistGirdisi m = mak.get(a.makinistId());
            Instant alt = ZamanHesabi.pencereBasi(g.gun(), s.pencere(), s.yillik()).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
            Instant ust = ZamanHesabi.pencereSonu(g.gun(), s.pencere(), s.p().ozelYillik()).plusDays(1).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
            List<Gorev> diger = son.get(m.id()).stream().filter(x -> x.seferId() != g.seferId() && !x.baslangic().isBefore(alt) && x.baslangic().isBefore(ust))
                    .sorted(Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId)).toList();
            List<Gorev> sorumlu = NobetHesabi.sorumluKomsular(diger, g.gorev(), s.p().dakika("nobet.ayirma_saat"));
            if (KuralMotoru.engelVarMi(KuralMotoru.degerlendir(new KuralBaglami(g.gorev(), sorumlu, m.bilgi(), m.hatSon(), m.genelSon(), ADLAR, s.p(), null)))) {
                kirmizi.add(m.sicil() + "/" + g.seferId());
            }
        }
        return kirmizi;
    }

    private static MakinistGirdisi makinist(long id, String sicil, Map<Long, MakinistBilgisi.HatBrove> hatlar) {
        LocalDate bitis = LocalDate.of(2030, 1, 1);
        MakinistBilgisi bilgi = new MakinistBilgisi(id, ANKARA, MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis, Map.of(ARAC, LocalDate.of(2024, 1, 1)), hatlar,
                LocalDate.of(2026, 9, 1), List.of());
        Map<YearMonth, double[]> aylik = new HashMap<>();
        aylik.put(EKIM, new double[] { 0, 0 });
        return new MakinistGirdisi(id, sicil, bilgi, List.of(), Map.of(), null, aylik);
    }

    private static SeferGirdisi sefer(long id, long hat, String bas, String bit) {
        return sefer(id, ANKARA, ANKARA, hat, bas, bit);
    }

    private static SeferGirdisi sefer(long id, long kalkisGar, long varisGar, long hat, String bas, String bit) {
        Gorev g = new Gorev(id, kalkisGar, varisGar, hat, ARAC, an(bas), an(bit), an(bas), an(bit), 100);
        return new SeferGirdisi(id, ZamanHesabi.gun(g.kalkis()), YearMonth.from(ZamanHesabi.gun(g.kalkis())), g);
    }
}
