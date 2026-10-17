package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.Vardiya;
import com.makinistatama.kural.ZamanHesabi;

/**
 * T1.3 erken NFR-02 ölçüm kapısı (MK-18), DB'siz: saf {@link OtomatikAtama#planla} ile 40 makinist x 31 gün x 100 sefer/gün (124.000 değerlendirme)
 * önizlemesi; 0 / 1 AY kuralı / 5 / 10 özel kural. Yükleme penceresi {@link BaglamYukleyici#pencereGun} ve {@link BaglamYukleyici#yillik} ile
 * üretimdekiyle aynı hesaplanır (pencere büyümesi dahil). Kurallar eşikleri gevşek seçilmiştir: yerleşik atama sonucu (kaç atama) aynı kalsın, yük ölçülsün.
 * Çalıştırma: {@code mvnw test -Pyavas "-Dtest=OzelKuralDegerlendiriciOlcekTesti"}.
 */
@Tag("yavas")
class OzelKuralDegerlendiriciOlcekTesti {

    static final int MAKINIST = 40;
    static final int GUN = 31;
    static final int GUNLUK_SEFER = 100;
    static final LocalDate ILK = LocalDate.of(2026, 10, 12);
    static final long ANKARA = 1;
    static final long HAT = 10;
    static final long ARAC = 20;

    static Instant an(LocalDate gun, int dk) {
        return LocalDateTime.of(gun, java.time.LocalTime.MIDNIGHT).plusMinutes(dk).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    static OzelKural k(int id, Olcu o, Pencere p, String esik, Kapsam kapsam, Siddet s) {
        return new OzelKural(id, "Kural " + id, o, p, Karsilastirma.EN_FAZLA, new BigDecimal(esik), kapsam, s, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    static OzelKural k2(int id, Olcu o, Pencere p, Karsilastirma kars, String esik, Integer kayanGun) {
        return new OzelKural(id, "Kural " + id, o, p, kayanGun, kars, new BigDecimal(esik), Kapsam.TUMU, Siddet.ENGEL, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    static List<OzelKural> kurallar(int adet) {
        Kapsam hat = new Kapsam(Set.of(HAT), null, null, null, null, null);
        Kapsam gece = new Kapsam(null, null, null, null, null, Vardiya.GECE);
        List<OzelKural> tum = List.of(
                k(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, "190", Kapsam.TUMU, Siddet.ENGEL),
                k(2, Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "11.5", Kapsam.TUMU, Siddet.ENGEL),
                k(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.HAFTA, "46", Kapsam.TUMU, Siddet.ENGEL),
                k(4, Olcu.TEK_SEFER_SURESI, Pencere.SEFER, "9", hat, Siddet.ENGEL),
                k2(5, Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, Karsilastirma.EN_FAZLA, "31", null),
                k2(6, Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, Karsilastirma.EN_AZ, "1", null),
                k2(7, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, Karsilastirma.EN_FAZLA, "400", 30),
                k2(8, Olcu.IZIN_GUNU, Pencere.AY, Karsilastirma.EN_AZ, "1", null),
                k(9, Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, "11.5", gece, Siddet.ENGEL),
                k(10, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, "2300", Kapsam.TUMU, Siddet.ENGEL));
        if (adet == 1) {
            return List.of(tum.get(0)); // yalniz AY (pencere etkisi)
        }
        return tum.subList(0, adet);
    }

    record Olcum(double sn, int atanan, int bos) {
    }

    static Olcum kos(Parametreler p, int gunSayisi) {
        MakinistBilgisi bilgi = new MakinistBilgisi(1, ANKARA, MakinistBilgisi.Durum.AKTIF, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1),
                Map.of(ARAC, LocalDate.of(2024, 1, 1)), Map.of(HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1))),
                LocalDate.of(2026, 9, 1), List.of());
        List<MakinistGirdisi> makinistler = new ArrayList<>();
        for (int i = 0; i < MAKINIST; i++) {
            MakinistBilgisi b = new MakinistBilgisi(i + 1, ANKARA, bilgi.durum(), bilgi.ehliyetBitis(), bilgi.saglikBitis(), bilgi.psikoteknikBitis(),
                    bilgi.aracTipiSinavlari(), bilgi.hatBroveleri(), bilgi.kartSonSurus(), List.of());
            makinistler.add(new MakinistGirdisi(i + 1, String.format("D%03d", i + 1), b, List.of(), Map.of(), null, new HashMap<YearMonth, double[]>()));
        }
        List<SeferGirdisi> seferler = new ArrayList<>();
        long id = 1;
        for (int g = 0; g < gunSayisi; g++) {
            LocalDate gun = ILK.plusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10;
                Instant kalkis = an(gun, dk);
                Instant varis = kalkis.plusSeconds(60 * 90);
                seferler.add(new SeferGirdisi(id, gun, YearMonth.from(gun), new Gorev(id, ANKARA, ANKARA, HAT, ARAC, kalkis, varis, kalkis, varis, 250)));
                id++;
            }
        }
        int pencere = BaglamYukleyici.pencereGun(p);
        boolean yillik = BaglamYukleyici.yillik(p);
        Instant simdi = an(ILK.minusDays(1), 0);
        long t0 = System.nanoTime();
        OtomatikAtama.Sonuc s = OtomatikAtama.planla(seferler, makinistler, Map.of(), p, simdi, pencere, yillik);
        double sn = (System.nanoTime() - t0) / 1e9;
        return new Olcum(sn, s.atananlar().size(), s.bosKalanlar().size());
    }

    private static Parametreler param(int adet) {
        Parametreler t = com.makinistatama.kural.VarsayilanParametreler.varsayilan();
        return Parametreler.olustur(t.degerler(), t.siddetler(), kurallar(adet));
    }

    @Test
    void otuzBirGun_0_1_5_10_kural_marjinal_dokum() {
        kos(param(0), 3); // JIT isinmasi
        kos(param(10), 3);
        Olcum sifir = kos(param(0), GUN);
        Olcum bir = kos(param(1), GUN);
        Olcum bes = kos(param(5), GUN);
        Olcum on = kos(param(10), GUN);
        System.out.printf("NFR-02 ozel kural olcum (31 gun, 124.000 degerlendirme, DB'siz planla): 0 kural %.2f sn (%d atandi / %d bos); 1 AY kurali %.2f sn (%d/%d); "
                + "5 kural %.2f sn (%d/%d); 10 kural %.2f sn (%d/%d); pencereGun 0->%d, 1->%d, 10->%d%n", sifir.sn(), sifir.atanan(), sifir.bos(), bir.sn(), bir.atanan(), bir.bos(),
                bes.sn(), bes.atanan(), bes.bos(), on.sn(), on.atanan(), on.bos(), BaglamYukleyici.pencereGun(param(0)), BaglamYukleyici.pencereGun(param(1)),
                BaglamYukleyici.pencereGun(param(10)));
        assertThat(on.sn()).as("10 kural, 31 gun: tavan 30 sn").isLessThanOrEqualTo(30.0);
        assertThat(sifir.atanan()).isGreaterThan(0);
    }
}
