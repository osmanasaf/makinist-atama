package com.makinistatama.takas;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Takas için seçilebilir bir görev (başkasının). Kural motoru ÇALIŞMAZ, listeden süzülmez (toplu 1-bit kehanet olmasın, MK-34/7): yalnız sicil ve herkese açık pano bilgisi. */
public record TakasHedefDto(Long hedefAtamaId, String sicil, String gorev, String trenNo, LocalDate tarih, String kalkisGar, String varisGar, OffsetDateTime guncelKalkis) {
}
