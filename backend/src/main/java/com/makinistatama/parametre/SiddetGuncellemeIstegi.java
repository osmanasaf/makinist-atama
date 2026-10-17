package com.makinistatama.parametre;

import com.makinistatama.kural.Siddet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Kural şiddeti değişikliği (ENGEL / UYARI / KAPALI); {@code neden} zorunludur. */
public record SiddetGuncellemeIstegi(
        @NotNull(message = "Şiddet seçilmeli.") Siddet siddet,
        @NotBlank(message = "Değişiklik nedeni yazılmalı.") @Size(max = 500, message = "Neden en çok 500 karakter olabilir.") String neden) {
}
