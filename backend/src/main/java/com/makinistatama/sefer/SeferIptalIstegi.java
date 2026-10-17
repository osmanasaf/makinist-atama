package com.makinistatama.sefer;

import com.makinistatama.degisiklik.Neden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sefer iptali (FR-07). {@code neden} zorunlu; DİĞER'de {@code nedenNotu} da zorunludur. {@code devam}: parçalı trende bu ve sonraki parçaları da iptal et (boş = true, MK-63); parçasızda yok sayılır. */
public record SeferIptalIstegi(
        @NotNull(message = "Neden seçilmeli.") Neden neden,
        @Size(max = 500, message = "Not en çok 500 karakter olabilir.") String nedenNotu,
        Boolean devam) {
}
