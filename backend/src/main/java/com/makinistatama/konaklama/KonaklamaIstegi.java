package com.makinistatama.konaklama;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Konaklama ekleme / düzenleme. {@code versiyon}: güncellemede zorunlu. Anlar ISO-8601 ofsetli (+03:00). */
public record KonaklamaIstegi(
        @NotNull(message = "Makinist seçilmeli.") Long makinistId,
        @NotNull(message = "Gar seçilmeli.") Long garId,
        @NotNull(message = "Yer tipi seçilmeli.") KonaklamaYerTipi yerTipi,
        @NotNull(message = "Giriş zamanı gerekli.") OffsetDateTime giris,
        @NotNull(message = "Çıkış zamanı gerekli.") OffsetDateTime cikis,
        @Size(max = 200, message = "Not en çok 200 karakter olabilir.") String notu,
        Long versiyon) {
}
