package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.YolculukOnerisi.MakinistBaglami;
import com.makinistatama.atama.YolculukOnerisi.Oneri;
import com.makinistatama.atama.YolculukOnerisi.YolculukAdayi;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.kural.ZamanHesabi;

/** F2b MK-41/FR-06: yolculuk önerisi saf çekirdeği (DB'siz): ön süzgeç, aktarma, çakışma, kural süzgeci, sıra, en çok 3, zaten görevli, yük treni. */
class YolculukOnerisiTest {

    static final long ANKARA = 1;
    static final long ESKISEHIR = 2;
    static final long HAT = 10;
    static final long ARAC = 20;
    static final LocalDate GUN = LocalDate.of(2026, 10, 14);
    static final Instant SIMDI = an("2026-10-10T10:00");
    static final Parametreler P = Parametreler.olustur(VarsayilanParametreler.DEGERLER, VarsayilanParametreler.SIDDETLER);
    static final MakinistBilgisi BILGI = new MakinistBilgisi(1, ANKARA, MakinistBilgisi.Durum.AKTIF, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1),
            LocalDate.of(2030, 1, 1), Map.of(ARAC, LocalDate.of(2024, 1, 1)), Map.of(HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1))),
            LocalDate.of(2026, 9, 1), List.of());
    static final MakinistBaglami MB = new MakinistBaglami(BILGI, Map.of(), null, Map.of(ANKARA, "Ankara", ESKISEHIR, "Eskişehir"));

    static Instant an(String yerel) {
        return LocalDateTime.parse(yerel).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    static Gorev surus(long id, long kalkisGar, long varisGar, String bas, String bit) {
        Instant b = an(bas);
        Instant e = an(bit);
        return new Gorev(id, kalkisGar, varisGar, HAT, ARAC, b, e, b, e, 100);
    }

    static YolculukAdayi aday(long id, String bas, String bit) {
        return new YolculukAdayi(id, GUN, Gorev.yolculukOlustur(id, ANKARA, ESKISEHIR, HAT, ARAC, an(bas), an(bas), an(bit), true), false);
    }

    /** Hedef: Eskişehir -> Ankara 10:30-15:00 (14 Eki). */
    static Gorev hedef() {
        return surus(99, ESKISEHIR, ANKARA, "2026-10-14T10:30", "2026-10-14T15:00");
    }

    static List<Oneri> oner(List<Gorev> sirali, List<YolculukAdayi> adaylar) {
        return YolculukOnerisi.oner(sirali, MB, hedef(), GUN, adaylar, P, SIMDI, 15, 12, false, 3, false, BaglamYukleyici.pencereGun(P), false);
    }

    private static List<Long> kimlikler(List<Oneri> o) {
        return o.stream().map(Oneri::seferId).toList();
    }

    @Test
    void aktarma60dk_onerilir_aktarma10dk_onerilmez_cakisan_onerilmez() {
        List<Oneri> sonuc = oner(List.of(), List.of(aday(1, "2026-10-14T08:00", "2026-10-14T09:30"), aday(2, "2026-10-14T08:50", "2026-10-14T10:20"),
                aday(3, "2026-10-14T09:00", "2026-10-14T10:35")));

        assertThat(kimlikler(sonuc)).containsExactly(1L);
        assertThat(sonuc.getFirst().bekleDk()).isEqualTo(60);
    }

    @Test
    void sinirDeger_aktarma15dkTamOnerilir_14dkOnerilmez() {
        assertThat(kimlikler(oner(List.of(), List.of(aday(1, "2026-10-14T08:45", "2026-10-14T10:15"))))).containsExactly(1L);
        assertThat(oner(List.of(), List.of(aday(1, "2026-10-14T08:46", "2026-10-14T10:16")))).isEmpty();
    }

    @Test
    void dortUygun_ucDoner_sirayaBeklemeSonraSure() {
        List<Oneri> sonuc = oner(List.of(), List.of(
                aday(1, "2026-10-14T06:00", "2026-10-14T07:30"), // bekle 180
                aday(2, "2026-10-14T07:30", "2026-10-14T09:00"), // bekle 90
                aday(3, "2026-10-14T08:00", "2026-10-14T09:30"), // bekle 60, süre 90
                aday(4, "2026-10-14T08:15", "2026-10-14T09:30"))); // bekle 60, süre 75: eşit beklemede kısa süre önce

        assertThat(kimlikler(sonuc)).containsExactly(4L, 3L, 2L);
    }

    @Test
    void k12DinlenmeYuzundenUygunOlmayanElenir() {
        // makinist 13 Eki 12:00-20:00 Ankara'da görevdeydi: 02:00'daki yolculukta dinlenme 6 s < 11 s (K-11 ENGEL); 08:00'dekinde 12 s
        List<Gorev> sirali = List.of(surus(50, ANKARA, ANKARA, "2026-10-13T12:00", "2026-10-13T20:00"));

        List<Oneri> sonuc = oner(sirali, List.of(aday(1, "2026-10-14T02:00", "2026-10-14T03:30"), aday(2, "2026-10-14T08:00", "2026-10-14T09:30")));

        assertThat(kimlikler(sonuc)).containsExactly(2L);
    }

    @Test
    void makinistYolculukSeferindeZatenGorevliyse_onerilmez() {
        List<Gorev> sirali = List.of(surus(1, ANKARA, ANKARA, "2026-10-14T08:00", "2026-10-14T09:30"));

        assertThat(oner(sirali, List.of(aday(1, "2026-10-14T08:00", "2026-10-14T09:30")))).isEmpty();
    }

    @Test
    void konumuAdayKalkisGarinaUymayan_elenir_kalkisGecmiseVeAramaPenceresiDisinaDusen_elenir() {
        Gorev baskaGardaBitti = surus(50, ANKARA, ESKISEHIR, "2026-10-14T04:00", "2026-10-14T05:30"); // makinist 05:30'dan beri Eskişehir'de
        assertThat(oner(List.of(baskaGardaBitti), List.of(aday(1, "2026-10-14T07:00", "2026-10-14T08:30")))).as("yolculuk Ankara'dan kalkar, makinist Eskişehir'de").isEmpty();
        // kalkış şimdiden önce
        List<Oneri> gecmis = YolculukOnerisi.oner(List.of(), MB, hedef(), GUN, List.of(aday(1, "2026-10-14T08:00", "2026-10-14T09:30")), P, an("2026-10-14T08:30"), 15, 12, false, 3,
                false, 15, false);
        assertThat(gecmis).isEmpty();
        // arama penceresi 12 saat: 10:30 - 12 s = 22:30 (13 Eki, dahil); 21:00 kalkış pencere dışı; 22:30-23:30 sonrası 11 s dinlenme (K-12 sınırı) uygun
        assertThat(oner(List.of(), List.of(aday(1, "2026-10-13T21:00", "2026-10-13T22:30")))).isEmpty();
        assertThat(kimlikler(oner(List.of(), List.of(aday(1, "2026-10-13T22:30", "2026-10-13T23:30"))))).containsExactly(1L);
    }

    @Test
    void yukTreni_ayarKapaliysaElenir_acikIseGirer() {
        YolculukAdayi yuk = new YolculukAdayi(1, GUN, Gorev.yolculukOlustur(1, ANKARA, ESKISEHIR, HAT, ARAC, an("2026-10-14T08:00"), an("2026-10-14T08:00"), an("2026-10-14T09:30"), true), true);

        assertThat(oner(List.of(), List.of(yuk))).isEmpty();
        assertThat(YolculukOnerisi.oner(List.of(), MB, hedef(), GUN, List.of(yuk), P, SIMDI, 15, 12, true, 3, false, 15, false)).hasSize(1);
    }

    @Test
    void temiz_istenirse_uyarisiOlanElenir_manuelde_uyariyla_doner() {
        // makinist yolculuğun seferinde kuralı geçer ama yolculuk 21:30-23:00 gece 1,5 s: uyarı üreten bir kural yok; K-20 UYARI üreten aday: makinist Eskişehir'de
        Gorev baskaGarda = surus(50, ANKARA, ESKISEHIR, "2026-10-14T02:00", "2026-10-14T03:30");
        YolculukAdayi eskisehirdenKalkan = new YolculukAdayi(1, GUN, Gorev.yolculukOlustur(1, ESKISEHIR, ESKISEHIR, HAT, ARAC, an("2026-10-14T07:00"), an("2026-10-14T07:00"),
                an("2026-10-14T08:30"), true), false);
        // varış garı hedef kalkış garı (Eskişehir) ve makinistin konumu Eskişehir: süzgeçten geçer, kuralda K-20 yok → öneri temiz
        List<Gorev> sirali = new ArrayList<>(List.of(baskaGarda));

        List<Oneri> temiz = YolculukOnerisi.oner(sirali, MB, hedef(), GUN, List.of(eskisehirdenKalkan), P, SIMDI, 15, 12, false, 3, true, 15, false);

        assertThat(kimlikler(temiz)).containsExactly(1L);
        assertThat(temiz.getFirst().yolculukIhlalleri()).isEmpty();
        assertThat(temiz.getFirst().hedefIhlalleri()).isEmpty();
    }

    @Test
    void konumGar_ikiliArama_oncekiGorevinVarisi_yoksaBagliGar() {
        List<Gorev> sirali = new ArrayList<>(List.of(surus(1, ANKARA, ESKISEHIR, "2026-10-14T08:00", "2026-10-14T09:30"),
                surus(2, ESKISEHIR, ESKISEHIR, "2026-10-14T10:00", "2026-10-14T11:00")));
        Map<String, Long> beklenen = new HashMap<>();
        beklenen.put("2026-10-14T07:00", ANKARA); // hiç görev yok: bağlı gar
        beklenen.put("2026-10-14T08:00", ANKARA); // aynı anda başlayan görev "önce" sayılmaz
        beklenen.put("2026-10-14T08:01", ESKISEHIR);
        beklenen.put("2026-10-14T12:00", ESKISEHIR);
        beklenen.forEach((an, gar) -> assertThat(YolculukOnerisi.konumGar(sirali, ANKARA, an(an))).as(an).isEqualTo(gar));
    }
}
