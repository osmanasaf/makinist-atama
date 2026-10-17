package com.makinistatama.tanim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Yeni tanım: {@code POST /api/tanimlar}. */
public record TanimIstegi(
        @NotNull(message = "Tanım türü gerekli.") TanimTipi tip,
        @NotBlank(message = "Ad boş olamaz.") @Size(max = 100, message = "Ad en fazla 100 karakter olabilir.") String ad) {
}
