package com.makinistatama.tanim;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Resmi tatil ekleme: tarih (yyyy-MM-dd) ve ad (en çok 80 karakter). */
public record ResmiTatilIstegi(
        @NotNull(message = "Tarih gerekli.") LocalDate tarih,
        @NotBlank(message = "Ad gerekli.") @Size(max = 80, message = "Ad en çok 80 karakter olabilir.") String ad) {
}
