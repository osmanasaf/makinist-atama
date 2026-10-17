package com.makinistatama.atama;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * İntikal ekleme / düzeltme (MK-106). {@code versiyon}: düzeltmede zorunlu. {@code gerekce}: kural UYARISI varsa zorunlu (atamadaki gibi). Anlar ISO-8601 ofsetli (+03:00).
 * Düzeltmede {@code makinistId} değişemez (iptal edip yeniden eklenir).
 */
public record IntikalIstegi(
        @NotNull(message = "Makinist seçilmeli.") Long makinistId,
        @NotNull(message = "Kalkış garı seçilmeli.") Long kalkisGarId,
        @NotNull(message = "Varış garı seçilmeli.") Long varisGarId,
        @NotNull(message = "Kalkış zamanı gerekli.") OffsetDateTime kalkis,
        @NotNull(message = "Varış zamanı gerekli.") OffsetDateTime varis,
        @NotNull(message = "Araç türü seçilmeli.") AracTuru aracTuru,
        @Size(max = 200, message = "Not en çok 200 karakter olabilir.") String notu,
        @Size(max = 500, message = "Gerekçe en çok 500 karakter olabilir.") String gerekce,
        Long versiyon) {
}
