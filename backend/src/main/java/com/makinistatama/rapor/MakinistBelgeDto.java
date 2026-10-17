package com.makinistatama.rapor;

import java.time.LocalDate;
import java.util.List;

/** Makinistin kendi belge görünümü ({@code /api/benim/belgeler}): yalnız kendi belgeleri; {@code bugun} hesabın dayandığı gün, {@code uyariGun} YAKLASIYOR eşiği. */
public record MakinistBelgeDto(LocalDate bugun, int uyariGun, BelgeDurumuDto.Durum enKotu, List<BelgeDurumuDto.Belge> belgeler) {
}
