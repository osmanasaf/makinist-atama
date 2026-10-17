package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.kural.ZamanHesabi;

/** T11.1: otomatik atama çekirdeği (saf, DB'siz): en az çalışmış makinist, bağ kırıcı, boş + neden, UYARI'lı elenir, belirleyicilik. */
class OtomatikAtamaTest {

    static final long ANKARA = 1;
    static final long ESKISEHIR = 2;
    static final long HAT = 10;
    static final long ARAC = 20;
    static final Map<Long, String> ADLAR = Map.of(ANKARA, "Ankara", ESKISEHIR, "Eskişehir", HAT, "Ankara–Eskişehir", ARAC, "YHT");
    static final YearMonth EKIM = YearMonth.of(2026, 10);
    static final Parametreler P = VarsayilanParametreler.varsayilan();

    static Instant an(String yerel) {
        return LocalDateTime.parse(yerel).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    static Gorev gorev(long id, String bas, String bit) {
        return gorev(id, ANKARA, ANKARA, bas, bit, 100);
    }

    static Gorev gorev(long id, long kalkisGar, long varisGar, String bas, String bit, double km) {
        return new Gorev(id, kalkisGar, varisGar, HAT, ARAC, an(bas), an(bit), an(bas), an(bit), km);
    }

    static SeferGirdisi sefer(long id, String bas, String bit) {
        return sefer(id, ANKARA, ANKARA, bas, bit, 100);
    }

    static SeferGirdisi sefer(long id, long kalkisGar, long varisGar, String bas, String bit, double km) {
        Gorev g = gorev(id, kalkisGar, varisGar, bas, bit, km);
        return new SeferGirdisi(id, ZamanHesabi.gun(g.kalkis()), YearMonth.from(ZamanHesabi.gun(g.kalkis())), g);
    }

    static MakinistBilgisi.HatBrove hatBrove() {
        return new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1));
    }

    static MakinistBilgisi bilgi(long id, boolean hat, List<MakinistBilgisi.Izin> izin) {
        LocalDate bitis = LocalDate.of(2030, 1, 1);
        return new MakinistBilgisi(id, ANKARA, MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis, Map.of(ARAC, LocalDate.of(2024, 1, 1)),
                hat ? Map.of(HAT, hatBrove()) : Map.of(), LocalDate.of(2026, 9, 1), izin);
    }

    static MakinistGirdisi makinist(long id, String sicil, double saat, double km) {
        return makinist(id, sicil, saat, km, List.of(), true, List.of());
    }

    static MakinistGirdisi makinist(long id, String sicil, double saat, double km, List<Gorev> gorevler, boolean hat, List<MakinistBilgisi.Izin> izin) {
        Map<YearMonth, double[]> aylik = new HashMap<>();
        aylik.put(EKIM, new double[] { saat, km });
        return new MakinistGirdisi(id, sicil, bilgi(id, hat, izin), gorevler, Map.of(), null, aylik);
    }

    static Sonuc planla(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler) {
        return OtomatikAtama.planla(seferler, makinistler, ADLAR, P, an("2026-10-10T10:00").plusSeconds(0), 15, true);
    }

    @Test
    void enAzCalisan_baglariKmSonraSicilKirar_ikinciSeferCakisanaVerilmez() {
        // A 40 s, B 32 s / 1800 km, C 32 s / 1500 km
        List<MakinistGirdisi> m = List.of(makinist(1, "A", 40, 900), makinist(2, "B", 32, 1800), makinist(3, "C", 32, 1500));

        Sonuc s = planla(List.of(sefer(101, "2026-10-12T08:00", "2026-10-12T12:00"), sefer(102, "2026-10-12T10:00", "2026-10-12T15:00")), m);

        assertThat(s.atananlar()).containsExactly(new Atandi(101, 3), new Atandi(102, 2)); // 1. sefer C (km az); 2. sefer C çakışır -> B
        assertThat(s.bosKalanlar()).isEmpty();
    }

    @Test
    void seferlerKalkisSirasiyla_girisSirasiNeOlursaOlsun_belirleyici() {
        List<MakinistGirdisi> m = List.of(makinist(1, "A", 0, 0), makinist(2, "B", 0, 0), makinist(3, "C", 0, 0));
        List<SeferGirdisi> girdi = List.of(sefer(103, "2026-10-12T20:00", "2026-10-12T21:00"), sefer(101, "2026-10-12T08:00", "2026-10-12T09:00"),
                sefer(102, "2026-10-12T14:00", "2026-10-12T15:00"));
        List<SeferGirdisi> ters = new ArrayList<>(girdi);
        java.util.Collections.reverse(ters);

        Sonuc a = planla(girdi, m);
        Sonuc b = planla(ters, m);

        assertThat(a).isEqualTo(b); // aynı girdi kümesi -> aynı sonuç
        // eşit toplamlarda sicil bağ kırıcı: ilk sefer A; A artık 1 saat -> ikinci B; üçüncü C
        assertThat(a.atananlar()).containsExactly(new Atandi(101, 1), new Atandi(102, 2), new Atandi(103, 3));
    }

    @Test
    void kimseUygunDegil_bosVeNedenOzeti_hatIzinDinlenme() {
        MakinistGirdisi hatsiz = makinist(1, "A", 0, 0, List.of(), false, List.of());
        MakinistGirdisi izinli = makinist(2, "B", 0, 0, List.of(), true,
                List.of(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13), MakinistBilgisi.IzinDurumu.ONAY)));
        MakinistGirdisi yorgun = makinist(3, "C", 0, 0, List.of(gorev(900, "2026-10-12T14:00", "2026-10-12T22:00")), true, List.of()); // 22:00 de bitti, 06:00 sefer: 8 s dinlenme

        Sonuc s = planla(List.of(sefer(101, "2026-10-13T06:00", "2026-10-13T10:00")), List.of(hatsiz, izinli, yorgun));

        assertThat(s.atananlar()).isEmpty();
        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> {
            assertThat(b.seferId()).isEqualTo(101);
            assertThat(b.neden()).isEqualTo("0/3 uygun (yetkili 2, yetkisiz 1): dinlenme 1, izin 1, hat yetkisi 1"); // asıl (kapasite) nedenler önde, yetkisizlik sonda
            assertThat(b.ornekler()).anyMatch(o -> o.startsWith("hat yetkisi: Hat yetkisi yok")).anyMatch(o -> o.startsWith("izin: Onaylı izin"))
                    .anyMatch(o -> o.startsWith("dinlenme: Dinlenme"));
        });
    }

    @Test
    void herkesGunlukSureyiAsar_bos_ornekteGunlukSure() {
        List<MakinistGirdisi> m = List.of(makinist(1, "A", 0, 0), makinist(2, "B", 0, 0), makinist(3, "C", 0, 0));

        Sonuc s = planla(List.of(sefer(101, "2026-10-12T22:00", "2026-10-13T12:00")), m); // 14 saatlik sefer

        assertThat(s.atananlar()).isEmpty();
        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> {
            assertThat(b.neden()).isEqualTo("0/3 uygun: günlük süre 3");
            assertThat(b.ornekler().get(0)).contains("12 s > 11 s");
        });
    }

    @Test
    void uyariliAdayOtomatikSecilmez_konumUyusmazligi() {
        // A dünkü seferini Eskişehir'de bitirdi, yeni sefer Ankara'dan kalkıyor: K-20 UYARI -> otomatik atama yalnız kurallara tam uyanı seçer
        MakinistGirdisi konumsuz = makinist(1, "A", 0, 0, List.of(gorev(900, ANKARA, ESKISEHIR, "2026-10-11T06:00", "2026-10-11T10:00", 100)), true, List.of());
        MakinistGirdisi temiz = makinist(2, "B", 50, 5000);

        Sonuc s = planla(List.of(sefer(101, "2026-10-12T08:00", "2026-10-12T10:00")), List.of(konumsuz, temiz));
        assertThat(s.atananlar()).containsExactly(new Atandi(101, 2)); // B çok çalışmış olsa da UYARI'sız tek aday

        Sonuc yalniz = planla(List.of(sefer(101, "2026-10-12T08:00", "2026-10-12T10:00")), List.of(konumsuz));
        assertThat(yalniz.bosKalanlar()).singleElement().satisfies(b -> assertThat(b.neden()).isEqualTo("0/1 uygun: konum 1"));
    }

    @Test
    void mevcutGorevler_ikiYonluKontrol_sonrakiGorevDinlenmeyiBozmaz() {
        // A'nın ertesi sabah 06:00 seferi var; bu gece 22:00 seferi ona 8 s dinlenme bırakır -> A elenir, B alır
        MakinistGirdisi a = makinist(1, "A", 0, 0, List.of(gorev(900, "2026-10-13T06:00", "2026-10-13T08:00")), true, List.of());
        MakinistGirdisi b = makinist(2, "B", 10, 100);

        Sonuc s = planla(List.of(sefer(101, "2026-10-12T20:00", "2026-10-12T22:00")), List.of(a, b));

        assertThat(s.atananlar()).containsExactly(new Atandi(101, 2));
    }

    @Test
    void bitmisSefer_k22_uyari_oldugundan_bosKalir() {
        Sonuc s = OtomatikAtama.planla(List.of(sefer(101, "2026-10-09T08:00", "2026-10-09T10:00")), List.of(makinist(1, "A", 0, 0)), ADLAR, P,
                an("2026-10-10T10:00"), 15, true);

        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> assertThat(b.neden()).isEqualTo("0/1 uygun: geçmiş sefer 1"));
        // K-22 kapatılırsa atanır
        Parametreler kapali = Parametreler.olustur(VarsayilanParametreler.DEGERLER, withSiddet("K-22", Siddet.KAPALI));
        Sonuc k = OtomatikAtama.planla(List.of(sefer(101, "2026-10-09T08:00", "2026-10-09T10:00")), List.of(makinist(1, "A", 0, 0)), ADLAR, kapali,
                an("2026-10-10T10:00"), 15, true);
        assertThat(k.atananlar()).hasSize(1);
    }

    @Test
    void herAtamaToplamiGunceller_dengeliDagitim() {
        List<MakinistGirdisi> m = List.of(makinist(1, "A", 0, 0), makinist(2, "B", 0, 0));
        List<SeferGirdisi> seferler = new ArrayList<>();
        for (int i = 0; i < 6; i++) { // 6 ayrı gün, 3 saatlik seferler: dönüşümlü dağıtılır
            seferler.add(sefer(100 + i, "2026-10-%02dT08:00".formatted(12 + i), "2026-10-%02dT11:00".formatted(12 + i)));
        }

        Sonuc s = planla(seferler, m);

        assertThat(s.atananlar()).extracting(Atandi::makinistId).containsExactly(1L, 2L, 1L, 2L, 1L, 2L);
    }

    /** 05-review INFO-2: yetkisizler (hat yetkisi) başa geçip asıl nedeni (dolu yetkililer) gizlemez; ornekler de kapasite nedeniyle başlar. */
    @Test
    void bosNedenOzeti_kapasiteNedeniOnde_yetkisizlerSonda() {
        List<MakinistGirdisi> m = new ArrayList<>();
        for (int i = 1; i <= 6; i++) { // 6 hat yetkisiz (çoğunluk), 2 yetkili ama o gün 14 saatlik başka görevi olan
            m.add(makinist(i, "H" + i, 0, 0, List.of(), false, List.of()));
        }
        m.add(makinist(7, "Y1", 0, 0, List.of(gorev(900, "2026-10-12T05:00", "2026-10-12T16:00")), true, List.of()));
        m.add(makinist(8, "Y2", 0, 0, List.of(gorev(901, "2026-10-12T05:00", "2026-10-12T16:00")), true, List.of()));

        Sonuc s = planla(List.of(sefer(101, "2026-10-12T09:00", "2026-10-12T10:00")), m);

        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> {
            assertThat(b.neden()).isEqualTo("0/8 uygun (yetkili 2, yetkisiz 6): çakışma 2, hat yetkisi 6");
            assertThat(b.ornekler().getFirst()).startsWith("çakışma: ");
        });
    }

    /** 05-review INFO-1: seferin kalkış garında önceki görevi biten makinist, geçmişsiz (serbest) makinistten önce gelir; 4 seferin 4'ü de dolar. */
    @Test
    void yerindeBekleyen_adaletSirasindanOnceGelir_dortSeferDolar() {
        List<MakinistGirdisi> m = List.of(makinist(1, "M1", 0, 0), makinist(2, "M2", 0, 0));
        List<SeferGirdisi> seferler = List.of(sefer(101, ANKARA, ESKISEHIR, "2026-10-12T06:00", "2026-10-12T08:00", 100),
                sefer(102, ESKISEHIR, ANKARA, "2026-10-12T09:00", "2026-10-12T11:00", 100), sefer(103, ANKARA, ESKISEHIR, "2026-10-12T11:30", "2026-10-12T13:30", 100),
                sefer(104, ANKARA, ESKISEHIR, "2026-10-12T11:45", "2026-10-12T13:45", 100));

        Sonuc s = planla(seferler, m);

        assertThat(s.bosKalanlar()).isEmpty();
        assertThat(s.atananlar()).containsExactly(new Atandi(101, 1), new Atandi(102, 1), new Atandi(103, 2), new Atandi(104, 1));
        // 102: M1 Eskişehir'de bitirdi (yerinde); eskiden 0 saatli M2 seçilir, M1 orada kalır, 104 boş kalırdı. 103: ikisi de Ankara'da (M2 ev garında) -> adalet: M2
    }

    /** 05-review INFO-3: aylık toplam tam dakika; 0,1+0,2 saat ile 0,3 saat aynı toplamdır, bağ km ile kırılır (double karşılaştırması B'yi seçerdi). */
    @Test
    void saatToplami_tamDakika_baglantiKmKirar() {
        List<MakinistGirdisi> m = List.of(makinist(1, "A", 0.1 + 0.2, 100), makinist(2, "B", 0.3, 200));

        Sonuc s = planla(List.of(sefer(101, "2026-10-12T08:00", "2026-10-12T09:00")), m);

        assertThat(s.atananlar()).containsExactly(new Atandi(101, 1));
    }

    private static Map<String, Siddet> withSiddet(String kod, Siddet s) {
        Map<String, Siddet> m = new HashMap<>(VarsayilanParametreler.SIDDETLER);
        m.put(kod, s);
        return m;
    }
}
