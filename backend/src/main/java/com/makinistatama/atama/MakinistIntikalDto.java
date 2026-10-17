package com.makinistatama.atama;

import java.time.OffsetDateTime;

/**
 * Makinistin kendi intikali (MK-108, {@code /api/benim/...}): atamacının iç {@code notu} YOKTUR. IPTAL satırı da gelir ("İPTAL" etiketi, yeniden "gördüm" ister).
 * {@code ozet}: güncel özet; {@code eskiOzet}: DEGISTI iken en son görülen özet.
 */
public record MakinistIntikalDto(Long id, String kalkisGar, String varisGar, OffsetDateTime kalkis, OffsetDateTime varis, AracTuru aracTuru, IntikalDurumu durum,
        GordumDurumu gordu, OffsetDateTime gorduZamani, String ozet, String eskiOzet) {
}
