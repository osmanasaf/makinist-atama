package com.makinistatama.parametre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Parametre değeri değişikliği: {@code deger} metin olarak gelir (tür ve sınır sunucuda doğrulanır), {@code neden} zorunludur. */
public record ParametreGuncellemeIstegi(
        @NotNull(message = "Değer gerekli.") @Size(max = 50, message = "Değer çok uzun.") String deger,
        @NotBlank(message = "Değişiklik nedeni yazılmalı.") @Size(max = 500, message = "Neden en çok 500 karakter olabilir.") String neden) {
}
