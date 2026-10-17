package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Özel kural ekleme / düzenleme / kapatma / "dene" isteği. {@code kapsam} boşsa tümü; {@code gecerlilikBas} boşsa eklemede bugün, düzenlemede mevcut değer korunur. {@code kayanGun}: yalnız pencere KAYAN iken (2–366). {@code versiyon}: düzenlemede
 * zorunlu (iyimser kilit). {@code neden}: düzenlemede zorunlu (denetim kaydı), eklemede boşsa gerekçe yazılır. Ölçü/pencere/koşul birleşimi ve eşik sınırı
 * sunucuda {@code Olcu} kataloğundan doğrulanır.
 */
public record OzelKuralIstegi(
        @NotBlank(message = "Kural adı yazılmalı.") @Size(max = 80, message = "Kural adı en çok 80 karakter olabilir.") String ad,
        @NotNull(message = "Ölçü seçilmeli.") Olcu olcu,
        @NotNull(message = "Pencere seçilmeli.") Pencere pencere,
        Integer kayanGun,
        @NotNull(message = "Koşul türü seçilmeli.") Karsilastirma karsilastirma,
        @NotNull(message = "Koşul değeri yazılmalı.") BigDecimal esik,
        Kapsam kapsam,
        @NotNull(message = "Şiddet seçilmeli.") Siddet siddet,
        LocalDate gecerlilikBas,
        LocalDate gecerlilikBit,
        @NotBlank(message = "Gerekçe yazılmalı.") @Size(max = 500, message = "Gerekçe en çok 500 karakter olabilir.") String gerekce,
        @Size(max = 200, message = "Kaynak en çok 200 karakter olabilir.") String kaynak,
        Long versiyon,
        @Size(max = 500, message = "Neden en çok 500 karakter olabilir.") String neden) {
}
