package com.makinistatama.benim;

import java.time.LocalDate;

import com.makinistatama.makinist.IzinTuru;

import jakarta.validation.constraints.NotNull;

/** Makinistin kendi izin talebi: makinist kimliği YOK (oturumdan gelir, MK-28). */
public record BenimIzinIstegi(
        @NotNull(message = "Başlangıç tarihi gerekli.") LocalDate baslangic,
        @NotNull(message = "Bitiş tarihi gerekli.") LocalDate bitis,
        @NotNull(message = "İzin türü seçilmeli.") IzinTuru tur) {
}
