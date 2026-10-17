package com.makinistatama.atama;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Atamacı görünümü (MK-106/MK-107). {@code kirmizi/nedenler/uyarilar} liste okumasında canlı hesaplanır, SAKLANMAZ; yalnız PLANLI için dolu (IPTAL: hiçbiri).
 * {@code gordu}/{@code gorduZamani}: makinistin "gördüm" onayı (özet değişince DEGISTI).
 */
public record IntikalDto(Long id, Long makinistId, String sicil, String adSoyad, Long kalkisGarId, String kalkisGar, Long varisGarId, String varisGar, OffsetDateTime kalkis,
        OffsetDateTime varis, AracTuru aracTuru, String notu, IntikalDurumu durum, long versiyon, GordumDurumu gordu, OffsetDateTime gorduZamani, boolean kirmizi,
        List<String> nedenler, List<String> uyarilar) {
}
