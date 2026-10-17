package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.KadroAnalitigi.Imza;
import com.makinistatama.atama.KadroAnalitigi.Sinif;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;

import static com.makinistatama.atama.OtomatikAtamaTest.sefer;

/** F3a-1 (saf, DB'siz): haftalık talep, kişi başı saat U, gevşetilmiş akış (alt sınır), yedek formülü. Elle hesap: demo ED 1.155 / YHT 735 / Dizel 315 sa/hafta. */
class KadroAnalitigiTest {

    private static Parametreler p(String haftalik) {
        return p(haftalik, Siddet.ENGEL);
    }

    /** K-14 şiddeti açıkça verilir: varsayılan K-14 KAPALI olsa da bu testler haftalık tavan senaryosunu sınar. */
    private static Parametreler p(String haftalik, Siddet k14) {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        d.put("haftalik.max_saat", haftalik);
        Map<String, Siddet> s = new HashMap<>(VarsayilanParametreler.SIDDETLER);
        s.put("K-14", k14);
        return Parametreler.olustur(d, s);
    }

    // ---------------------------------------------------------------- (a) talep

    @Test
    void talep_hatVeAracTipineGore_haftalikSaat() {
        // 14 gün, hat 10 / araç 20: 2 sefer x 4 saat = 8 saat -> haftalık 8 x 7 / 14 = 4
        List<SeferGirdisi> sefer = List.of(sefer(1, "2026-10-12T08:00", "2026-10-12T12:00"), sefer(2, "2026-10-20T08:00", "2026-10-20T12:00"));

        Map<Sinif, Double> talep = KadroAnalitigi.haftalikTalep(sefer, 14);

        assertThat(talep).containsOnlyKeys(new Sinif(10, 20));
        assertThat(talep.get(new Sinif(10, 20))).isCloseTo(4.0, within(1e-9));
    }

    // ---------------------------------------------------------------- (b) U

    @Test
    void kisiBasinaSaat_parametredenOkunur_devamsizlikDusulur() {
        // min(45, 11 x (7 - ceil(24/24)) = 66) = 45 -> %13 devamsızlık: 39,15
        assertThat(KadroAnalitigi.kisiBasinaSaat(p("45"), 13)).isCloseTo(39.15, within(1e-9));
        // min(55, 66) = 55 -> 47,85 (demo ayarı)
        assertThat(KadroAnalitigi.kisiBasinaSaat(p("55"), 13)).isCloseTo(47.85, within(1e-9));
        assertThat(KadroAnalitigi.kisiBasinaSaat(p("55"), 0)).isCloseTo(55.0, within(1e-9));
        // günlük sınır bağlayıcı: 5 saat x 6 gün = 30
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        d.put("gunluk.max_saat", "5");
        assertThat(KadroAnalitigi.kisiBasinaSaat(Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER), 0)).isCloseTo(30.0, within(1e-9));
        // hafta tatili 25 saat -> 2 gün düşer: 11 x 5 = 55, haftalık 45 yine bağlayıcı; tatil 49 saat -> 3 gün: 11 x 4 = 44
        d.put("gunluk.max_saat", "11");
        d.put("hafta_tatili.min_saat", "49");
        assertThat(KadroAnalitigi.kisiBasinaSaat(Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER), 0)).isCloseTo(44.0, within(1e-9));
    }

    /** MK-97: K-14 KAPALI iken haftalık tavan yoktur (günlük x 6 gün); UYARI ve ENGEL'de bugünkü formül. */
    @Test
    void kisiBasinaSaat_k14Kapali_haftalikTavanYok() {
        assertThat(KadroAnalitigi.kisiBasinaSaat(p("45", Siddet.KAPALI), 13)).isCloseTo(57.42, within(1e-9));
        assertThat(KadroAnalitigi.kisiBasinaSaat(p("45", Siddet.ENGEL), 13)).isCloseTo(39.15, within(1e-9));
        assertThat(KadroAnalitigi.kisiBasinaSaat(p("45", Siddet.UYARI), 13)).isCloseTo(39.15, within(1e-9));
    }

    // ---------------------------------------------------------------- (c) akış

    private static Sinif s(long hat, long arac) {
        return new Sinif(hat, arac);
    }

    private static Imza imza(List<Long> arac, List<Long> hat) {
        return new Imza(arac, hat);
    }

    @Test
    void akis_karmaKumeIkiSinifiPaylasir_ikiKezSayilmaz() {
        // iki sınıf 40'ar saat, U = 40; tek karma kişi ikisini de karşılayabilir ama yalnız 40 saat taşır
        Map<Sinif, Double> talep = Map.of(s(1, 1), 40.0, s(2, 2), 40.0);
        Imza karma = imza(List.of(1L, 2L), List.of(1L, 2L));

        KadroAnalitigi.Akis a = KadroAnalitigi.akis(talep, Map.of(karma, 1), 40.0);

        assertThat(a.eksikToplam()).isCloseTo(40.0, within(1e-9)); // naif sınıf-sınıf bakış 0 derdi
        assertThat(a.kullanilan().get(karma)).isCloseTo(40.0, within(1e-9));
        assertThat(KadroAnalitigi.kisi(a.eksikToplam(), 40.0)).isEqualTo(1);
    }

    /** Review W5 (F6): akış sırası, esnek (karma) imzayı sona bırakır; uzman önce dolar, karma boşta kalır (usulüne göre sıra tersine çevrilirse karma 5 saat taşırdı). */
    @Test
    void akis_uzmanKumeOnceDolar_esnekKumeSonraKullanilir() {
        Map<Sinif, Double> talep = Map.of(s(1, 1), 5.0, s(2, 2), 5.0);
        Imza uzman = imza(List.of(1L), List.of(1L)); // yalnız sınıf 1
        Imza karma = imza(List.of(1L, 2L), List.of(1L, 2L)); // iki sınıf

        KadroAnalitigi.Akis a = KadroAnalitigi.akis(talep, Map.of(uzman, 1, karma, 1), 10.0);

        assertThat(a.eksikToplam()).isCloseTo(0.0, within(1e-9));
        assertThat(a.kullanilan().get(uzman)).isCloseTo(5.0, within(1e-9)); // sınıf 1'i uzman karşılar
        assertThat(a.kullanilan().get(karma)).isCloseTo(5.0, within(1e-9)); // karma yalnız sınıf 2'yi (uzmanın yapamadığı işi) alır
    }

    @Test
    void akis_mevcutYetiyorsaEksikSifir_kullanimYuzdesiVar() {
        Map<Sinif, Double> talep = Map.of(s(1, 1), 60.0);
        Imza i = imza(List.of(1L), List.of(1L));

        KadroAnalitigi.Akis a = KadroAnalitigi.akis(talep, Map.of(i, 3), 40.0); // kapasite 120

        assertThat(a.eksikToplam()).isCloseTo(0.0, within(1e-9));
        assertThat(a.kullanilan().get(i) / (3 * 40.0)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void akis_imzasinaGirmeyenSinifiKarsilamaz_arac_ve_hatIkisiDeGerekir() {
        // kişi yalnız (araç 1, hat 1); talep (hat 2, araç 1) ve (hat 1, araç 2): ikisi de karşılanmaz
        Map<Sinif, Double> talep = Map.of(s(2, 1), 30.0, s(1, 2), 20.0);
        Imza i = imza(List.of(1L), List.of(1L));

        KadroAnalitigi.Akis a = KadroAnalitigi.akis(talep, Map.of(i, 5), 40.0);

        assertThat(a.eksik().get(s(2, 1))).isCloseTo(30.0, within(1e-9));
        assertThat(a.eksik().get(s(1, 2))).isCloseTo(20.0, within(1e-9));
        assertThat(a.kullanilan().getOrDefault(i, 0.0)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void akis_kartezyenTanimi_aracVeHatKumeleriCaprazlamaKarsilar() {
        // imza {araç 1, 2} x {hat 1, 2}: (hat 1, araç 2) de dahil (K-05/K-06 ayrı ayrı denetlenir)
        Map<Sinif, Double> talep = Map.of(s(1, 2), 20.0);
        Imza i = imza(List.of(1L, 2L), List.of(1L, 2L));

        assertThat(KadroAnalitigi.akis(talep, Map.of(i, 1), 40.0).eksikToplam()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void akis_uzmanKisiOnceDolar_esnekKisiBosKalir() {
        Imza uzman = imza(List.of(1L), List.of(1L));
        Imza esnek = imza(List.of(1L, 2L), List.of(1L, 2L));

        KadroAnalitigi.Akis a = KadroAnalitigi.akis(Map.of(s(1, 1), 40.0, s(2, 2), 10.0), Map.of(uzman, 1, esnek, 1), 40.0);

        assertThat(a.kullanilan().get(uzman)).isCloseTo(40.0, within(1e-9));
        assertThat(a.kullanilan().get(esnek)).isCloseTo(10.0, within(1e-9)); // yalnız (2, 2) için gerekli olan
        assertThat(a.eksikToplam()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void akis_belirleyici_ayniGirdiAyniSonuc() {
        Map<Sinif, Double> talep = new HashMap<>();
        Map<Imza, Integer> kume = new HashMap<>();
        for (long k = 1; k <= 6; k++) {
            talep.put(s(k, k % 3), 30.0 + 7 * k);
            kume.put(imza(List.of(k % 3, (k + 1) % 3), List.of(k, (k % 6) + 1)), (int) k);
        }
        assertThat(KadroAnalitigi.akis(talep, kume, 40.0)).isEqualTo(KadroAnalitigi.akis(new HashMap<>(talep), new HashMap<>(kume), 40.0));
    }

    // ---------------------------------------------------------------- (d) demo elle sayılar

    @Test
    void demoElleSayilar_tekBroveliGerekli_asilAltSinir() {
        // ED 1.155 / YHT 735 / Dizel 315 sa/hafta (üç sınıf yaklaşımı): U = 47,85 -> 25 + 16 + 7 = 48; haftalık 45 (U = 39,15) -> 30 + 19 + 9 = 58
        double[] talep = { 1155, 735, 315 };
        int toplam55 = 0;
        int toplam45 = 0;
        for (double t : talep) {
            toplam55 += KadroAnalitigi.kisi(t, 47.85);
            toplam45 += KadroAnalitigi.kisi(t, 39.15);
        }
        assertThat(toplam55).isEqualTo(48);
        assertThat(toplam45).isEqualTo(58);
        assertThat(KadroAnalitigi.kisi(80.0, 40.0)).isEqualTo(2); // tam bölünme: yukarı yuvarlanmaz
        assertThat(KadroAnalitigi.kisi(80.01, 40.0)).isEqualTo(3);
        assertThat(KadroAnalitigi.kisi(0.0, 40.0)).isZero();
    }

    // ---------------------------------------------------------------- (e) yedek

    @Test
    void yedek_gunlukIhtiyac_enBuyukGun() {
        BigDecimal oran = new BigDecimal("0.10");
        assertThat(KadroAnalitigi.yedekGunIhtiyaci(oran, 1, 100)).isEqualTo(10);
        assertThat(KadroAnalitigi.yedekGunIhtiyaci(oran, 1, 5)).isEqualTo(1); // ceil(0,5) = 1
        assertThat(KadroAnalitigi.yedekGunIhtiyaci(oran, 3, 5)).isEqualTo(3); // min
        assertThat(KadroAnalitigi.yedekGunIhtiyaci(oran, 1, 0)).isEqualTo(1);
        assertThat(KadroAnalitigi.yedekGunIhtiyaci(BigDecimal.ZERO, 0, 100)).isZero();

        List<Integer> gunler = new ArrayList<>(List.of(80, 100, 95));
        assertThat(KadroAnalitigi.yedekIhtiyaci(oran, 1, gunler)).isEqualTo(10);
        assertThat(KadroAnalitigi.yedekIhtiyaci(BigDecimal.ZERO, 0, gunler)).isZero();
    }
}
