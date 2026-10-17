package com.makinistatama.tanim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yeniden adlandırma: {@code PUT /api/tanimlar/{id}}. */
public record TanimAdIstegi(
        @NotBlank(message = "Ad boş olamaz.") @Size(max = 100, message = "Ad en fazla 100 karakter olabilir.") String ad) {
}
