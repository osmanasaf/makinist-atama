package com.makinistatama.atama;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** İzin aralığına denk gelen atanmış sefer (izin kaydından sonra atamacıya uyarı listesi). */
public record IzinEtkisiDto(Long atamaId, Long seferId, LocalDate tarih, String trenNo, AtamaRol rol, String kalkisGar, String varisGar,
        OffsetDateTime kalkis, OffsetDateTime varis) {
}
