package com.makinistatama.atama;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.makinistatama.degisiklik.Neden;

/** Makinistin elinden alınan görev kalemi. {@code nedenEtiketi}: yalnız neden türü (serbest not hiçbir yerde yok [V9]); {@code trenNo}/{@code tarih} sefer hâlâ varsa dolu. */
public record GorevOlayiDto(Long olayId, AtamaOlayi.Tur tur, Long seferId, String trenNo, LocalDate tarih, String ozet, Neden nedenEtiketi, OffsetDateTime zaman) {
}
