package com.makinistatama.atama;

import java.time.LocalDate;

/**
 * Atamacının "kim gördü" sayaçları ([bas, bit] planlanan gün, yalnız kalkışı geçmemiş atamalar ve serbest intikaller). {@code gorulmeyen}: hesabı olup güncel özeti görmemiş;
 * {@code hesapsiz}: makinistin hesabı yok (ayrı sayaç, görülmeyene dahil değil); {@code uyari}: görülmeyenlerden kalkışa {@code gordum.uyari_saat}'ten AZ kalmış olanlar (eşit değil).
 */
public record GordumOzetiDto(LocalDate bas, LocalDate bit, int toplam, int gorulen, int gorulmeyen, int hesapsiz, int uyari) {
}
