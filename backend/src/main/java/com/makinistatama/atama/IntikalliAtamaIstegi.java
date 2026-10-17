package com.makinistatama.atama;

import com.makinistatama.degisiklik.Neden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "İntikalle ata" (MK-110): intikalin gar çifti ve saatleri İSTEMCİDEN GELMEZ; sunucu süre tablosundan yeniden hesaplar (denetimsiz ikinci yol yok). {@code gerekce}: intikal ya da
 * sefer kuralı UYARI veriyorsa zorunlu; {@code neden}/{@code nedenNotu}: sefer ASIL'ı zaten doluysa ("değiştir") zorunlu.
 */
public record IntikalliAtamaIstegi(
        @NotNull(message = "Makinist seçilmeli.") Long makinistId,
        @NotNull(message = "Araç türü seçilmeli.") AracTuru aracTuru,
        @Size(max = 200, message = "Not en çok 200 karakter olabilir.") String notu,
        @Size(max = 500, message = "Gerekçe en çok 500 karakter olabilir.") String gerekce,
        Neden neden,
        String nedenNotu) {
}
