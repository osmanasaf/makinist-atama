package com.makinistatama.sefer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sefer ekleme / düzenleme. Saatler İstanbul saatiyle; {@code varisTarihi} verilmezse varış, kalkıştan sonraki ilk {@code varisSaat}'tir
 * (kalkıştan küçük ya da eşitse ertesi gün). Süre 24 saati aşamaz. {@code versiyon}: güncellemede zorunlu.
 */
public record SeferIstegi(
        @NotBlank(message = "Tren no boş olamaz.") @Size(max = 30, message = "Tren no en fazla 30 karakter olabilir.") String trenNo,
        @NotNull(message = "Tarih gerekli.") LocalDate tarih,
        @NotNull(message = "Kalkış garı seçilmeli.") Long kalkisGarId,
        @NotNull(message = "Kalkış saati gerekli.") LocalTime kalkisSaat,
        @NotNull(message = "Varış garı seçilmeli.") Long varisGarId,
        @NotNull(message = "Varış saati gerekli.") LocalTime varisSaat,
        LocalDate varisTarihi,
        @NotNull(message = "Araç tipi seçilmeli.") Long aracTipiId,
        @NotNull(message = "Hat seçilmeli.") Long hatId,
        @DecimalMin(value = "0", message = "KM negatif olamaz.") @DecimalMax(value = "99999", message = "KM çok büyük.") BigDecimal km,
        Long versiyon,
        TrenTuru trenTuru) {
}
