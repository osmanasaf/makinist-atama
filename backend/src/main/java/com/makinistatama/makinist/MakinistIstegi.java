package com.makinistatama.makinist;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Makinist kartı isteği (oluşturma ve güncelleme). {@code tur} (F3b): oluşturmada seçilir, yoksa MAKINIST; güncellemede farklıysa 400 TUR_DEGISTIRILEMEZ. Bröve kümeleri BÜTÜN olarak değiştirilir. {@code versiyon}: güncellemede zorunlu
 * (iyimser kilit; oluştururken yok sayılır).
 */
public record MakinistIstegi(
        @NotBlank(message = "Sicil boş olamaz.") @Size(max = 20, message = "Sicil en fazla 20 karakter olabilir.") String sicil,
        @NotBlank(message = "Ad soyad boş olamaz.") @Size(max = 120, message = "Ad soyad en fazla 120 karakter olabilir.") String adSoyad,
        @NotNull(message = "Bağlı gar seçilmeli.") Long bagliGarId,
        @NotNull(message = "Durum seçilmeli.") MakinistDurum durum,
        @NotNull(message = "Ehliyet bitiş tarihi gerekli.") LocalDate ehliyetBitis,
        @NotNull(message = "Sağlık raporu bitiş tarihi gerekli.") LocalDate saglikBitis,
        @NotNull(message = "Psikoteknik bitiş tarihi gerekli.") LocalDate psikoteknikBitis,
        LocalDate sonSurus,
        Long versiyon,
        List<@Valid BroveAracTipiIstegi> broveAracTipleri,
        List<@Valid BroveHatIstegi> broveHatlar,
        PersonelTuru tur) {

    public record BroveAracTipiIstegi(
            @NotNull(message = "Araç tipi seçilmeli.") Long aracTipiId,
            @NotNull(message = "Sınav tarihi gerekli.") LocalDate sinavTarihi) {
    }

    public record BroveHatIstegi(
            @NotNull(message = "Hat seçilmeli.") Long hatId,
            @NotNull(message = "Sınav tarihi gerekli.") LocalDate sinavTarihi,
            LocalDate sonSurus) {
    }
}
