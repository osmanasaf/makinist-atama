package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Bos;
import com.makinistatama.atama.OtomatikAtama.Sonuc;

/** F2d MK-69 (saf, DB'siz): iyileştirme döngüsü; planlayıcı ve saat sahte. */
class IyilestirmeTest {

    private static Sonuc sonuc(long... bosIdler) {
        List<Bos> bos = new ArrayList<>();
        for (long id : bosIdler) {
            bos.add(new Bos(id, "x", List.of()));
        }
        return new Sonuc(List.of(), bos);
    }

    /** Sahte saat: her çağrıda ilerlemez; yalnız planlayıcı içinden {@code turMs} ilerletilir. */
    private static final class Saat {
        final AtomicLong ns = new AtomicLong();

        long oku() {
            return ns.get();
        }

        void ilerle(long ms) {
            ns.addAndGet(ms * 1_000_000);
        }
    }

    @Test
    void tur0BosOncelikleKosulur_enAzBosluTurSecilir_esitteIlkKazanir() {
        List<Map<Long, Integer>> cagrilar = new ArrayList<>();
        List<Long> kaydirmalar = new ArrayList<>();
        BiFunction<Map<Long, Integer>, Long, Sonuc> tur = (o, k) -> {
            cagrilar.add(o);
            kaydirmalar.add(k);
            return switch (cagrilar.size()) {
                case 1 -> sonuc(1, 2, 3); // taban 3 boş
                case 2 -> sonuc(1, 2); // 2
                case 3 -> sonuc(5, 6); // 2: eşit -> ilk kalır (tur 1)
                case 4 -> sonuc(1, 2, 3, 4); // kötü
                default -> sonuc(1, 2, 3);
            };
        };
        Iyilestirme.Bulgu b = Iyilestirme.ara(tur, 4, 1_000_000, new Saat()::oku);

        assertThat(cagrilar.getFirst()).isEmpty();
        assertThat(kaydirmalar.getFirst()).isZero();
        assertThat(b.tabanBos()).isEqualTo(3);
        assertThat(b.sonuc().bosKalanlar()).extracting(Bos::seferId).containsExactly(1L, 2L);
        assertThat(b.tur()).isEqualTo(4);
        assertThat(cagrilar).hasSize(4);
        // anahtar = ENİ İYİ turun girdisi (tur 1: tabanda boş kalanlar +1, kaydırma KAYDIRMA[0])
        assertThat(b.oncelik()).isEqualTo(Map.of(1L, 1, 2L, 1, 3L, 1));
        assertThat(b.kaydirDk()).isEqualTo(kaydirmalar.get(1)).isEqualTo(1440);
        assertThat(kaydirmalar.get(2)).isEqualTo(240);
    }

    /** W3 / UX-1: bitiş nedeni ayrı ayrı bildirilir (arayüz dürüst mesaj versin). */
    @Test
    void durmaNedeni_bosYok_bosSifir_turSiniri_sure() {
        assertThat(Iyilestirme.ara((o, k) -> sonuc(), 5, 1_000_000, new Saat()::oku).durma()).isEqualTo("BOS_YOK");
        int[] n = { 0 };
        assertThat(Iyilestirme.ara((o, k) -> ++n[0] == 1 ? sonuc(1, 2) : sonuc(), 5, 1_000_000, new Saat()::oku).durma()).isEqualTo("BOS_SIFIR");
        assertThat(Iyilestirme.ara((o, k) -> sonuc(1, 2), 3, 1_000_000, new Saat()::oku).durma()).isEqualTo("TUR_SINIRI");
        Iyilestirme.Bulgu tek = Iyilestirme.ara((o, k) -> sonuc(1, 2), 1, 1_000_000, new Saat()::oku);
        assertThat(tek.durma()).as("tur ayarı 1: ek deneme yok").isEqualTo("TUR_SINIRI");
        assertThat(tek.tur()).isEqualTo(1);
        // tur 0 bütçeyi doldurdu: döngü hiç başlamaz
        Saat saat = new Saat();
        Iyilestirme.Bulgu sure = Iyilestirme.ara((o, k) -> {
            saat.ilerle(15_000);
            return sonuc(1, 2);
        }, 20, 15_000, saat::oku);
        assertThat(sure.durma()).isEqualTo("SURE");
        assertThat(sure.tur()).isEqualTo(1);
    }

    @Test
    void agirlikOncekiTurlardaBosKalanlarinBirikimidir() {
        List<Map<Long, Integer>> cagrilar = new ArrayList<>();
        Iyilestirme.ara((o, k) -> {
            cagrilar.add(o);
            return cagrilar.size() == 1 ? sonuc(1, 2) : cagrilar.size() == 2 ? sonuc(2, 3) : sonuc(2);
        }, 4, 1_000_000, new Saat()::oku);
        assertThat(cagrilar.get(1)).isEqualTo(Map.of(1L, 1, 2L, 1));
        assertThat(cagrilar.get(2)).isEqualTo(Map.of(1L, 1, 2L, 2, 3L, 1));
        assertThat(cagrilar.get(3)).isEqualTo(Map.of(1L, 1, 2L, 3, 3L, 1));
    }

    @Test
    void taban_tabandanKotuOlamaz_iyilesmeYoksaTur0DonerAnahtarBos() {
        Iyilestirme.Bulgu b = Iyilestirme.ara((o, k) -> o.isEmpty() ? sonuc(1) : sonuc(1, 2), 5, 1_000_000, new Saat()::oku);
        assertThat(b.sonuc().bosKalanlar()).hasSize(1);
        assertThat(b.oncelik()).isEmpty();
        assertThat(b.kaydirDk()).isZero();
        assertThat(b.tur()).isEqualTo(5);
    }

    @Test
    void bosSayisiSifirOlunca_durur() {
        List<Integer> cagri = new ArrayList<>();
        Iyilestirme.Bulgu b = Iyilestirme.ara((o, k) -> {
            cagri.add(1);
            return cagri.size() == 2 ? sonuc() : sonuc(1);
        }, 40, 1_000_000, new Saat()::oku);
        assertThat(cagri).hasSize(2);
        assertThat(b.sonuc().bosKalanlar()).isEmpty();
        assertThat(b.tur()).isEqualTo(2);
    }

    @Test
    void tabanZatenBossuz_hicTurKosulmaz() {
        List<Integer> cagri = new ArrayList<>();
        Iyilestirme.ara((o, k) -> {
            cagri.add(1);
            return sonuc();
        }, 40, 1_000_000, new Saat()::oku);
        assertThat(cagri).hasSize(1);
    }

    @Test
    void enCokTurAsilmaz_turBirIyilestirmeyiKapatir() {
        List<Integer> cagri = new ArrayList<>();
        Iyilestirme.ara((o, k) -> {
            cagri.add(1);
            return sonuc(1, 2);
        }, 1, 1_000_000, new Saat()::oku);
        assertThat(cagri).hasSize(1);
    }

    @Test
    void sureButcesi_gecenArtiSonTurAsacaksaYeniTurBaslamaz() {
        Saat saat = new Saat();
        List<Integer> cagri = new ArrayList<>();
        // her tur 400 ms; bütçe 1000 ms: tur0 (400) -> 400+400=800 <= 1000 tur1 (800) -> 800+400=1200 > 1000 DUR
        Iyilestirme.Bulgu b = Iyilestirme.ara((o, k) -> {
            cagri.add(1);
            saat.ilerle(400);
            return sonuc(1, 2);
        }, 40, 1000, saat::oku);
        assertThat(cagri).hasSize(2);
        assertThat(b.tur()).isEqualTo(2);
        assertThat(b.sureMs()).isEqualTo(800);
    }

    @Test
    void belirleyici_ayniSahtePlanlayiciIkiKezAyniBulguyuVerir() {
        BiFunction<Map<Long, Integer>, Long, Sonuc> tur = (o, k) -> sonuc(java.util.stream.LongStream.rangeClosed(1, 6 - Math.min(5, o.size() + k / 240)).toArray());
        Iyilestirme.Bulgu a = Iyilestirme.ara(tur, 12, 1_000_000, new Saat()::oku);
        Iyilestirme.Bulgu b = Iyilestirme.ara(tur, 12, 1_000_000, new Saat()::oku);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void anahtarlaTekCagri_enIyiSonucuYenidenUretir() {
        BiFunction<Map<Long, Integer>, Long, Sonuc> tur = (o, k) -> sonuc(java.util.stream.LongStream.rangeClosed(1, 6 - Math.min(5, o.size() + k / 240)).toArray());
        Iyilestirme.Bulgu b = Iyilestirme.ara(tur, 12, 1_000_000, new Saat()::oku);
        assertThat(tur.apply(b.oncelik(), b.kaydirDk())).isEqualTo(b.sonuc());
    }
}
