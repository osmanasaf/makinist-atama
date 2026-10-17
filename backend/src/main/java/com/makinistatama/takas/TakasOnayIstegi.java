package com.makinistatama.takas;

import jakarta.validation.constraints.Size;

/**
 * Atamacının onayı. {@code yeniMakinistId}: yalnız BIRAK talebinde, görevi devralacak makinist (boşsa görev yalnız kaldırılır). {@code gerekce}: kural UYARI veriyorsa zorunlu (mevcut {@code ata} davranışı).
 */
public record TakasOnayIstegi(
        Long yeniMakinistId,
        @Size(max = 500, message = "Gerekçe en çok 500 karakter olabilir.") String gerekce) {
}
