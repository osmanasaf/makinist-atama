package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.atama.YolculukOnerisi.YolculukAdayi;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.kural.ZamanHesabi;

/**
 * F2b MK-42/FR-08: otomatik atamanın yolculuk aşaması (saf, DB'siz). Yalnız hiç kimse ihlalsiz değilse ve makinistin ihlali TAM {K-20} ise yolculuk aranır; ilk
 * (yolcu + hedef) çifti ihlalsiz geçen, bu ay en az çalışmış makinist seçilir; aşama kapalı (boş aday listesi) iken sonuç eskisiyle aynıdır.
 */
class OtomatikAtamaYolculukTest {

    static final long ANKARA = 1;
    static final long ESKISEHIR = 2;
    static final long HAT = 10;
    static final long ARAC = 20;
    static final LocalDate GUN = LocalDate.of(2026, 10, 14);
    static final Map<Long, String> ADLAR = Map.of(ANKARA, "Ankara", ESKISEHIR, "Eskişehir", HAT, "Ankara–Eskişehir", ARAC, "YHT");
    static final YearMonth EKIM = YearMonth.of(2026, 10);
    static final Parametreler P = VarsayilanParametreler.varsayilan();
    static final Instant SIMDI = an("2026-10-10T10:00");

    static Instant an(String yerel) {
        return LocalDateTime.parse(yerel).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    static Gorev gorev(long id, long kalkisGar, long varisGar, String bas, String bit) {
        return new Gorev(id, kalkisGar, varisGar, HAT, ARAC, an(bas), an(bit), an(bas), an(bit), 100);
    }

    /** Hedef: Eskişehir -> Ankara 10:30–15:00. */
    static SeferGirdisi hedef() {
        Gorev g = gorev(102, ESKISEHIR, ANKARA, "2026-10-14T10:30", "2026-10-14T15:00");
        return new SeferGirdisi(102, GUN, EKIM, g);
    }

    static YolculukAdayi yolculuk(long id, String bas, String bit) {
        return new YolculukAdayi(id, GUN, Gorev.yolculukOlustur(id, ANKARA, ESKISEHIR, HAT, ARAC, an(bas), an(bas), an(bit), true), false);
    }

    static MakinistGirdisi makinist(long id, String sicil, double saat, List<Gorev> gorevler, List<MakinistBilgisi.Izin> izin) {
        LocalDate bitis = LocalDate.of(2030, 1, 1);
        MakinistBilgisi bilgi = new MakinistBilgisi(id, ANKARA, MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis, Map.of(ARAC, LocalDate.of(2024, 1, 1)),
                Map.of(HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1))), LocalDate.of(2026, 9, 1), izin);
        Map<YearMonth, double[]> aylik = new HashMap<>();
        aylik.put(EKIM, new double[] { saat, 0 });
        return new MakinistGirdisi(id, sicil, bilgi, gorevler, Map.of(), null, aylik);
    }

    /** Ankara'da (bağlı gar), görevsiz. */
    static MakinistGirdisi ankarada(long id, String sicil, double saat) {
        return makinist(id, sicil, saat, List.of(), List.of());
    }

    /** Dünkü görevi Eskişehir'de bitti: seferin kalkış garında bekliyor. */
    static MakinistGirdisi eskisehirde(long id, String sicil, double saat) {
        return makinist(id, sicil, saat, List.of(gorev(50 + id, ANKARA, ESKISEHIR, "2026-10-13T18:00", "2026-10-13T19:30")), List.of());
    }

    static Sonuc planla(List<MakinistGirdisi> m, List<YolculukAdayi> y) {
        return planla(m, y, SIMDI);
    }

    static Sonuc planla(List<MakinistGirdisi> m, List<YolculukAdayi> y, Instant simdi) {
        return OtomatikAtama.planla(List.of(hedef()), m, ADLAR, P, simdi, 15, true, y, 15, 12);
    }

    @Test
    void gardaBekleyenVarsa_asamaDenenmez_oSecilir() {
        Sonuc s = planla(List.of(eskisehirde(31, "D031", 80), ankarada(7, "D007", 10)), List.of(yolculuk(201, "2026-10-14T08:00", "2026-10-14T09:30")));

        assertThat(s.atananlar()).containsExactly(new Atandi(102, 31));
        assertThat(s.yolculukAramaSayisi()).isZero();
    }

    @Test
    void gardakiIzinliyse_ihlaliK20DegilAsamaDenenmez_bos() {
        MakinistGirdisi izinli = makinist(31, "D031", 0, List.of(gorev(81, ANKARA, ESKISEHIR, "2026-10-13T18:00", "2026-10-13T19:30")),
                List.of(new MakinistBilgisi.Izin(GUN, GUN, MakinistBilgisi.IzinDurumu.ONAY)));

        Sonuc s = planla(List.of(izinli), List.of(yolculuk(201, "2026-10-14T08:00", "2026-10-14T09:30")));

        assertThat(s.atananlar()).isEmpty();
        assertThat(s.bosKalanlar()).hasSize(1);
        assertThat(s.yolculukAramaSayisi()).isZero();
    }

    @Test
    void yalnizK20Olan_yolculukla_secilir_enAzCalisanOnce() {
        // D007 12 s, D009 30 s; ikisi de Ankara'da (K-20)
        Sonuc s = planla(List.of(ankarada(9, "D009", 30), ankarada(7, "D007", 12)), List.of(yolculuk(201, "2026-10-14T08:00", "2026-10-14T09:30")));

        assertThat(s.atananlar()).containsExactly(new Atandi(102, 7, 201L));
        assertThat(s.yolculukAramaSayisi()).isEqualTo(1);
        assertThat(s.bosKalanlar()).isEmpty();
    }

    @Test
    void ilkMakinistinYolculuguYoksa_siradakiDenenir() {
        // D007'nin bu yolculuğa binmesi dinlenmeyi bozar (dünkü görev 21:30'a kadar, yolculuk 08:00: 10 s 30 dk < 11 s); D009 serbest
        MakinistGirdisi yorgun = makinist(7, "D007", 12, List.of(gorev(60, ANKARA, ANKARA, "2026-10-13T13:00", "2026-10-13T21:30")), List.of());
        Sonuc s = planla(List.of(yorgun, ankarada(9, "D009", 30)), List.of(yolculuk(201, "2026-10-14T08:00", "2026-10-14T09:30")));

        assertThat(s.atananlar()).containsExactly(new Atandi(102, 9, 201L));
        assertThat(s.yolculukAramaSayisi()).isEqualTo(2);
    }

    @Test
    void payiUymayanYolculuk_bos_nedenAyniKalir() {
        // yolculuk varışı 10:25: hedef 10:30'a 5 dk < 15 dk aktarma
        Sonuc s = planla(List.of(ankarada(7, "D007", 12)), List.of(yolculuk(201, "2026-10-14T09:00", "2026-10-14T10:25")));

        assertThat(s.atananlar()).isEmpty();
        assertThat(s.bosKalanlar().getFirst().neden()).isEqualTo("0/1 uygun: konum 1");
        assertThat(s.yolculukAramaSayisi()).isEqualTo(1);
    }

    @Test
    void simdidenOnceKalkanYolculukYazilmaz() {
        Sonuc gecmis = planla(List.of(ankarada(7, "D007", 12)), List.of(yolculuk(201, "2026-10-14T08:00", "2026-10-14T09:30")), an("2026-10-14T08:30"));

        assertThat(gecmis.atananlar()).isEmpty();
    }

    @Test
    void asamaKapali_eskiYol_ayniBosSonuc() {
        List<MakinistGirdisi> m = List.of(ankarada(7, "D007", 12));

        Sonuc kapali = OtomatikAtama.planla(List.of(hedef()), m, ADLAR, P, SIMDI, 15, true);
        Sonuc bosListe = planla(m, List.of());

        assertThat(kapali.atananlar()).isEmpty();
        assertThat(kapali.bosKalanlar()).isEqualTo(bosListe.bosKalanlar());
        assertThat(kapali.yolculukAramaSayisi()).isZero();
        assertThat(bosListe.yolculukAramaSayisi()).isZero();
    }

    @Test
    void secilenCiftTumKurallardanGecer_ikinciHedefYolculuguKomsuGorur() {
        // yolculuk + hedef ardışık: ikinci sefer (Ankara'dan 16:00) D007'nin konumunu (hedef Ankara'da bitti) doğru görür
        SeferGirdisi ikinci = new SeferGirdisi(103, GUN, EKIM, gorev(103, ANKARA, ANKARA, "2026-10-14T16:00", "2026-10-14T17:00"));
        Sonuc s = OtomatikAtama.planla(List.of(hedef(), ikinci), List.of(ankarada(7, "D007", 0)), ADLAR, P, SIMDI, 15, true,
                List.of(yolculuk(201, "2026-10-14T08:00", "2026-10-14T09:30")), 15, 12);

        assertThat(s.atananlar()).containsExactly(new Atandi(102, 7, 201L), new Atandi(103, 7));
    }
}
