package com.makinistatama.konaklama;

import java.time.OffsetDateTime;

/** Makinistin kendi konaklaması (OK-10, {@code /api/benim/konaklama}): not alanı YOKTUR (atamacının iç notu makinistin ekranına çıkmaz). */
public record MakinistKonaklamaDto(Long id, String gar, KonaklamaYerTipi yerTipi, OffsetDateTime giris, OffsetDateTime cikis, int gece) {
}
