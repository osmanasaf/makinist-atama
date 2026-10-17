package com.makinistatama.atama;

import com.makinistatama.degisiklik.Neden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ata / değiştir. {@code rol} boşsa ASIL. {@code gerekce}: kural UYARI veriyorsa zorunlu. {@code neden}: o rolde zaten görevli varsa (değiştirme)
 * zorunlu, DİĞER'de {@code nedenNotu} da.
 */
public record AtamaIstegi(
        @NotNull(message = "Makinist seçilmeli.") Long makinistId,
        AtamaRol rol,
        @Size(max = 500, message = "Gerekçe en çok 500 karakter olabilir.") String gerekce,
        Neden neden,
        @Size(max = 500, message = "Not en çok 500 karakter olabilir.") String nedenNotu) {
}
