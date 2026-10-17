package com.makinistatama.atama;

/**
 * Seferi birleştirir (MK-126/128). {@code versiyon}: yoldaki {id} satırının sürümü (zincirin herhangi bir parçası olabilir). {@code onizle}: orkestratör yazdığı her şeyi işlem
 * sonunda geri alır (varsayılan false; {@code SeferBolIstegi} gibi Boolean + kurucu, eksik alan 400 olmasın).
 */
public record SeferBirlestirIstegi(long versiyon, Boolean onizle) {

    public SeferBirlestirIstegi {
        onizle = Boolean.TRUE.equals(onizle);
    }
}
