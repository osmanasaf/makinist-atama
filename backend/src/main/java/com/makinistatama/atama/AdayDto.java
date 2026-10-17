package com.makinistatama.atama;

import java.util.List;

/**
 * Aday listesi satırı. {@code uygun}: hiç ENGEL yok (yalnız UYARI varsa atama için gerekçe gerekir). {@code buSeferdeRol}: makinist bu seferde
 * zaten görevliyse rolü (seçilemez).
 */
public record AdayDto(Long makinistId, String sicil, String adSoyad, String bagliGar, boolean uygun, List<IhlalDto> ihlaller, double buAySaat,
        double buAyKm, AtamaRol buSeferdeRol) {
}
