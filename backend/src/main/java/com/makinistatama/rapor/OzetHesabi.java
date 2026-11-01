package com.makinistatama.rapor;

import java.util.List;

/**
 * Yönetim özetindeki TEK aritmetik yer (F4, MK-180; OK-44d: yeni hesap formülü icat edilmez). Saf: Spring/DB yok. Standart sapma POPÜLASYON sapmasıdır (n'e bölünür); ekran ve Excel
 * formülünü yazar. Veri yoksa ({@code n = 0}) {@code null}: sıfıra bölme yok.
 */
final class OzetHesabi {

    /** {@code ortalama}/{@code standartSapma}: ham (yuvarlanmamış) değerler; gösterim yuvarlaması çağıranda. */
    record Dagilim(int n, double ortalama, double standartSapma, double min, double max) {
    }

    private OzetHesabi() {
    }

    static Dagilim dagilim(List<Double> degerler) {
        if (degerler.isEmpty()) {
            return null;
        }
        int n = degerler.size();
        double ortalama = degerler.stream().mapToDouble(Double::doubleValue).sum() / n;
        double kareler = degerler.stream().mapToDouble(x -> (x - ortalama) * (x - ortalama)).sum();
        return new Dagilim(n, ortalama, Math.sqrt(kareler / n), degerler.stream().mapToDouble(Double::doubleValue).min().getAsDouble(),
                degerler.stream().mapToDouble(Double::doubleValue).max().getAsDouble());
    }

    /** pay/payda yüzdesi, bir ondalık; payda 0 ise {@code null}. */
    static Double yuzde(int pay, int payda) {
        return payda == 0 ? null : Math.round(pay * 1000.0 / payda) / 10.0;
    }

    /** Gösterim yuvarlaması: iki ondalık (kaynak raporlar saati iki ondalıkla verir). */
    static double ikiOndalik(double deger) {
        return Math.round(deger * 100) / 100.0;
    }

    /** Saat toplamı, iki ondalık (kaynak raporlar da saati iki ondalıkla verir). */
    static double toplam(List<Double> degerler) {
        return ikiOndalik(degerler.stream().mapToDouble(Double::doubleValue).sum());
    }
}
