package com.makinistatama.makinist;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** İzin kaydı: iki uç dahil. İlk sürümde doğrudan ONAY olarak girilir. */
public record IzinIstegi(
        @NotNull(message = "Başlangıç tarihi gerekli.") LocalDate baslangic,
        @NotNull(message = "Bitiş tarihi gerekli.") LocalDate bitis,
        @NotNull(message = "İzin türü seçilmeli.") IzinTuru tur) {
}
