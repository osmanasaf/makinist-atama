package com.makinistatama.konaklama;

import java.time.OffsetDateTime;

/** Konaklama kaydı. {@code gece}: {@link KonaklamaHesabi#gece} (ay = giriş günü). */
public record KonaklamaDto(Long id, Long makinistId, String sicil, String adSoyad, Long garId, String gar, KonaklamaYerTipi yerTipi, OffsetDateTime giris, OffsetDateTime cikis,
        int gece, String notu, long versiyon) {
}
