package com.makinistatama.atama;

import java.time.OffsetDateTime;
import java.util.List;

/** Boşta kalan makinistin atanabileceği, makinistsiz (ASIL yok) bir sefer; yalnız UYARI düzeyinde ihlal taşıyabilir (T10.4). */
public record UygunSeferDto(Long seferId, String trenNo, String kalkisGar, String varisGar, OffsetDateTime guncelKalkis, OffsetDateTime guncelVaris,
        String aracTipi, String hat, List<IhlalDto> uyarilar) {
}
