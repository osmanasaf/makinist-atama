package com.makinistatama.sefer;

import com.makinistatama.degisiklik.Neden;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Gecikme girişi (FR-07): planlanan saate göre TOPLAM gecikme (dakika; önceki gecikmenin üzerine eklenmez, yerine yazılır; 0 = gecikme yok).
 * {@code varisGecikmeDk} boşsa kalkış gecikmesine eşit alınır. {@code neden} zorunlu; DİĞER'de not da zorunlu. {@code devam}: parçalı trende sonraki parçalara da yansıt (boş = true, MK-62); parçasızda yok sayılır.
 */
public record SeferGecikmeIstegi(
        @NotNull(message = "Kalkış gecikmesi (dakika) gerekli.") @Min(value = 0, message = "Gecikme negatif olamaz.")
        @Max(value = 1440, message = "Gecikme en çok 1440 dakika olabilir.") Integer kalkisGecikmeDk,
        @Min(value = 0, message = "Gecikme negatif olamaz.") @Max(value = 1440, message = "Gecikme en çok 1440 dakika olabilir.") Integer varisGecikmeDk,
        @NotNull(message = "Neden seçilmeli.") Neden neden,
        @Size(max = 500, message = "Not en çok 500 karakter olabilir.") String nedenNotu,
        Boolean devam) {
}
