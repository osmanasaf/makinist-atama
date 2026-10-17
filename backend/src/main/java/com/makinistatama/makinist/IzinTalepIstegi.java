package com.makinistatama.makinist;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** İzin talebi (FR-11): TALEP durumunda açılır; onaylanana dek atamayı etkilemez (K-09 yalnız ONAY'ı sayar). */
public record IzinTalepIstegi(
        @NotNull(message = "Makinist seçilmeli.") Long makinistId,
        @NotNull(message = "Başlangıç tarihi gerekli.") LocalDate baslangic,
        @NotNull(message = "Bitiş tarihi gerekli.") LocalDate bitis,
        @NotNull(message = "İzin türü seçilmeli.") IzinTuru tur) {
}
