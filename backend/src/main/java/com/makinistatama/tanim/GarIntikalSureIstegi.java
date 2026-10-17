package com.makinistatama.tanim;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Süre yazma (ekleme ya da güncelleme). {@code versiyon}: çift zaten kayıtlıysa zorunlu (iyimser kilit). Gar sırası önemsiz (yönsüz). */
public record GarIntikalSureIstegi(
        @NotNull(message = "Gar seçilmeli.") Long garAId,
        @NotNull(message = "Gar seçilmeli.") Long garBId,
        @NotNull(message = "Süre gerekli.") @Min(value = 1, message = "Süre en az 1 dakika olmalı.") @Max(value = 1440, message = "Süre en çok 1440 dakika (24 saat) olabilir.") Integer dakika,
        Long versiyon) {
}
