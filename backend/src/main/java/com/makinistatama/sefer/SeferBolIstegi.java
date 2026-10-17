package com.makinistatama.sefer;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Seferi ara istasyon(lar)da böler (MK-128). Saatler İstanbul saatiyle (HH:mm); gün sunucuda Excel kuralıyla çözülür (önceki saatten küçük/eşitse ertesi gün).
 * {@code km}: noktaya kadar KÜMÜLATİF km (sefer km doluysa zorunlu, boşsa girilemez); {@code hatId/aracTipiId}: boşsa sefer satırınki (noktadan başlayan parça için).
 * {@code onizle}: orkestratör yazdığı her şeyi işlem sonunda geri alır; çekirdek bu bayrağı bilmez.
 */
public record SeferBolIstegi(
        long versiyon,
        @NotEmpty(message = "En az bir ara istasyon girilmeli.") @Size(max = 5, message = "En çok 5 ara istasyon girilebilir.") List<@NotNull(message = "Ara istasyon boş olamaz.") @Valid Nokta> noktalar,
        Boolean onizle) {

    /** MK-128: {@code onizle} varsayılanı false (gövdede yoksa yazar; ilkel {@code boolean} olsaydı Jackson eksik alanı 400 yapardı). */
    public SeferBolIstegi {
        onizle = Boolean.TRUE.equals(onizle);
    }

    public record Nokta(
            @NotNull(message = "Ara istasyon garı seçilmeli.") Long garId,
            @NotNull(message = "Varış saati gerekli.") LocalTime varisSaat,
            @NotNull(message = "Kalkış saati gerekli.") LocalTime kalkisSaat,
            @DecimalMin(value = "0", message = "KM negatif olamaz.") @DecimalMax(value = "99999", message = "KM çok büyük.") BigDecimal km,
            Long hatId,
            Long aracTipiId) {
    }
}
