package com.makinistatama.sefer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Anlar ISO-8601 ofsetli (+03:00). {@code tarih} planlanan kalkış günüdür (gecikmeyle kaymaz). {@code parcaNo/parcaSayisi}: ara istasyonda makinist değişen trenin parçası (F2c), aksi halde null; {@code trenGunu}: zincirin 1. parçasının günü (parçasızda null). */
public record SeferDto(Long id, String trenNo, LocalDate tarih, Long kalkisGarId, String kalkisGar, Long varisGarId, String varisGar,
        OffsetDateTime planlananKalkis, OffsetDateTime planlananVaris, int kalkisGecikmeDk, int varisGecikmeDk, OffsetDateTime guncelKalkis,
        OffsetDateTime guncelVaris, Long aracTipiId, String aracTipi, Long hatId, String hat, BigDecimal km, SeferDurumu durum, long versiyon, TrenTuru trenTuru,
        Integer parcaNo, Integer parcaSayisi, LocalDate trenGunu) {
}
