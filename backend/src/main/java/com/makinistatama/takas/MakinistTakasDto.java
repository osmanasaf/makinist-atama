package com.makinistatama.takas;

import java.time.OffsetDateTime;

import com.makinistatama.degisiklik.Neden;

/**
 * Makinistin kendi takas listesi. Karşı tarafın yalnız sicili ve o seferin özeti görünür: kural ayrıntısı, ad, belge, makinist kimliği YOK [V12]. {@code gelen}: talep bana geldi.
 * {@code neden}/{@code nedenNotu} yalnız kendi açtığı talepte dolu (karşı tarafa sağlık nedeni gösterilmez). {@code durum} okuma anındaki etkin durumdur (bayat / süresi dolmuş türetilir).
 * {@code yanitBekliyor}: benim kabul ya da reddetmem gereken. {@code reddeden}: durum REDDEDILDI ise "KARSI_MAKINIST" ya da "ATAMACI" (kime sorulacağı belli olsun; kural ayrıntısı değil), değilse null.
 */
public record MakinistTakasDto(Long id, TakasTuru tur, TakasDurumu durum, boolean gelen, boolean yanitBekliyor, String benimGorev, String karsiSicil, String karsiGorev,
        Neden neden, String nedenNotu, OffsetDateTime olusturma, OffsetDateTime sonKabul, String reddeden) {
}
