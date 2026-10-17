package com.makinistatama.atama;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Şimdi" (K-22: bitmiş sefere atama). Üretimde sistem saati; testlerde {@code makinist.atama.simdi} özelliğiyle sabitlenir (test verisi
 * zamanla "geçmiş" olup kırılmasın diye; genel {@link Clock} kimlik doğrulama kilidi gibi başka yerlerde de kullanıldığından değiştirilmez).
 */
@Component
class AtamaSaati {

    private final Clock saat;
    private final Instant sabit;

    AtamaSaati(Clock saat, @Value("${makinist.atama.simdi:}") String sabit) {
        this.saat = saat;
        this.sabit = sabit == null || sabit.isBlank() ? null : OffsetDateTime.parse(sabit.strip()).toInstant();
    }

    Instant simdi() {
        return sabit != null ? sabit : saat.instant();
    }
}
