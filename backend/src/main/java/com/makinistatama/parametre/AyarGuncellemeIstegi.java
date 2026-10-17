package com.makinistatama.parametre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Uygulama ayarı değişikliği: tamsayı {@code deger}, {@code neden} zorunlu. */
public record AyarGuncellemeIstegi(
        @NotNull(message = "Değer gerekli.") Integer deger,
        @NotBlank(message = "Değişiklik nedeni yazılmalı.") @Size(max = 500, message = "Neden en çok 500 karakter olabilir.") String neden) {
}
