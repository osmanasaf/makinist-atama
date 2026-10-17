package com.makinistatama.sefer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.makinistatama.ortak.ZamanAyari;

/**
 * Seferi N parçaya bölen SAF hesap (MK-120): Excel "Değişim Noktaları" ve ekran bölmesi aynı çekirdeği kullanır (kopya yok). Çerçeve/IO bilmez (MimariKuralTest).
 * Hata mesajları GÖVDE olarak döner; ön eki ("Değişim Noktaları satır N: " / "N. ara istasyon: ") çağıran ekler.
 */
final class ParcaHesabi {

    /** Parça en çok 6 (nokta en çok 5): vardiya değişimi için gerçekçi üst sınır, açma çarpanını sınırlar. */
    static final int EN_FAZLA_NOKTA = 5;

    private ParcaHesabi() {
    }

    /** Değişim noktası. Hat/araç tipi null ise o noktadan başlayan parça için satırınki kullanılır. {@code sira}: 1..N ardışık. */
    record Nokta(int sira, long garId, LocalTime varis, LocalTime kalkis, BigDecimal km, Long hatId, Long aracTipiId) {
    }

    /** Hesaplanan parça (framework'süz). {@code tarih}: parçanın kalkış günü; {@code trenGunu}: seferin özgün günü. */
    record Parca(LocalDate tarih, long kalkisGarId, long varisGarId, Instant kalkis, Instant varis, long aracTipiId, long hatId, BigDecimal km, int parcaNo, int parcaSayisi,
            LocalDate trenGunu) {
    }

    sealed interface Sonuc permits Parcalar, Hata {
    }

    record Parcalar(List<Parca> liste) implements Sonuc {
    }

    /**
     * @param noktaSirasi hatanın bağlandığı noktanın 1-tabanlı sırası (nokta listesindeki yeri); nokta yoksa 0
     * @param engelli azami parça süresi aşımı: saat yazım hatası olabilir, çağıran bunu durdurucu sayar
     */
    record Hata(String govde, int noktaSirasi, boolean engelli) implements Sonuc {
    }

    /** Tren başına kurallar: nokta sayısı, sıra 1..N ardışık, kümülatif KM azalmaz. Sorun yoksa null. */
    static Hata trenSorunu(List<Nokta> sirali) {
        if (sirali.size() > EN_FAZLA_NOKTA) {
            return new Hata("en çok " + EN_FAZLA_NOKTA + " değişim noktası olabilir (parça en çok " + (EN_FAZLA_NOKTA + 1) + ")", 1, false);
        }
        BigDecimal onceki = null;
        for (int i = 0; i < sirali.size(); i++) {
            Nokta n = sirali.get(i);
            if (n.sira() != i + 1) {
                return new Hata("sıra 1'den başlayıp ardışık olmalı (1.." + sirali.size() + ")", i + 1, false);
            }
            if (n.km() != null) {
                if (onceki != null && n.km().compareTo(onceki) < 0) {
                    return new Hata("KM sıra ilerledikçe azalamaz", i + 1, false);
                }
                onceki = n.km();
            }
        }
        return null;
    }

    /**
     * N-1 noktadan N parça açar: parça i kalkışı önceki noktanın kalkışı (ilk: satırın kalkışı), varışı bu noktanın varışı (son: satırın varışı); hat/araç tipi parçanın
     * BAŞLADIĞI noktada doluysa o, yoksa satırınki. Önce {@link #trenSorunu}. Parça {@code azamiParcaSaat}'ten uzunsa hata ENGELLEYİCİDİR (saat yazım hatası: varış 13:30
     * yerine 07:30 -> 23 s 30 dk'lık parça sessizce kabul edilmez); null = denetimsiz. {@code bitisSiniri} null değilse son parça varışı ona eşit olmalıdır.
     */
    static Sonuc parcala(LocalDate trenGunu, LocalTime kalkisSaat, LocalTime varisSaat, long kalkisGar, long varisGar, long aracTipi, long hat, BigDecimal km, List<Nokta> n,
            Integer azamiParcaSaat, Instant bitisSiniri) {
        Hata sorun = trenSorunu(n);
        if (sorun != null) {
            return sorun;
        }
        int sayi = n.size() + 1;
        List<Parca> parcalar = new ArrayList<>();
        LocalDate gun = trenGunu;
        LocalTime kalkis = kalkisSaat;
        for (int i = 0; i < sayi; i++) {
            boolean son = i == n.size();
            Nokta bas = i == 0 ? null : n.get(i - 1);
            Nokta bit = son ? null : n.get(i);
            int suc = son ? i : i + 1; // hatanın bağlandığı nokta (1-tabanlı): son parçada başlangıç noktası, değilse bitiş noktası
            LocalTime varis = son ? varisSaat : bit.varis();
            LocalDate varisGunu = varis.isAfter(kalkis) ? gun : gun.plusDays(1); // parça süresi hep (0, 24] saat: 24 saat denetimi kendiliğinden sağlanır
            BigDecimal parcaKm = bas == null ? (bit == null ? km : bit.km()) : (bit == null ? fark(km, bas.km()) : fark(bit.km(), bas.km()));
            if (parcaKm != null && parcaKm.signum() < 0) {
                return new Hata("KM tutarsız, " + (i + 1) + ". parçanın km'si negatif çıkıyor (Sefer KM'si noktanın KM'sinden küçük olamaz)", suc, false);
            }
            long sureDk = Duration.between(gun.atTime(kalkis), varisGunu.atTime(varis)).toMinutes();
            if (azamiParcaSaat != null && sureDk > 60L * azamiParcaSaat) {
                return new Hata((i + 1) + ". parça " + sureDk / 60 + " s " + sureDk % 60 + " dk sürüyor (en çok " + azamiParcaSaat
                        + " s; saat yazım hatası olabilir. Doğruysa 'sefer.parca_azami_saat' ayarını artırın)", suc, true);
            }
            parcalar.add(new Parca(gun, bas == null ? kalkisGar : bas.garId(), son ? varisGar : bit.garId(), gun.atTime(kalkis).atZone(ZamanAyari.BOLGE).toInstant(),
                    varisGunu.atTime(varis).atZone(ZamanAyari.BOLGE).toInstant(), bas != null && bas.aracTipiId() != null ? bas.aracTipiId() : aracTipi,
                    bas != null && bas.hatId() != null ? bas.hatId() : hat, parcaKm, i + 1, sayi, trenGunu));
            if (!son) {
                gun = varisGunu; // nokta kalkışı varıştan önce olamaz: bekleme aynı gün
                kalkis = bit.kalkis();
            }
        }
        if (bitisSiniri != null && !parcalar.getLast().varis().equals(bitisSiniri)) {
            ZonedDateTime sonVaris = parcalar.getLast().varis().atZone(ZamanAyari.BOLGE);
            ZonedDateTime seferVaris = bitisSiniri.atZone(ZamanAyari.BOLGE);
            boolean ayniGun = sonVaris.toLocalDate().equals(seferVaris.toLocalDate()); // saat aynı, gün farklıysa "11:49 / 11:49" çelişkisi: tarih de yazılır (seferi-bol BUG-3)
            DateTimeFormatter bicim = DateTimeFormatter.ofPattern(ayniGun ? "HH:mm" : "dd.MM HH:mm");
            return new Hata("saatler seferin zaman aralığında olmalı (sefer " + kalkisSaat + "–" + seferVaris.toLocalTime() + "): girilen ara istasyon saatleriyle son parça " + sonVaris.format(bicim)
                    + "'de varıyor, sefer " + seferVaris.format(bicim) + "'de varmalı." + (ayniGun ? "" : " Saatler sırayla ilerlerken son parça farklı bir güne kayıyor: bir ara istasyon saati seferin kalkış–varış aralığının dışında.")
                    + " Varış ve kalkış saatlerini seferin kalkış–varış saatleri arasına yerleştirin.", n.size(), false);
        }
        return new Parcalar(parcalar);
    }

    private static BigDecimal fark(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b);
    }
}
