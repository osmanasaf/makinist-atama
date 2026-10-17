package com.makinistatama.konaklama;

import java.time.OffsetDateTime;

/** Konaklama önerisi (otomatik yazılmaz): dış garda iki nöbet arası boşluk. {@code kayitli}: aynı makinist + aynı gar + kesişen konaklama kaydı var. */
public record KonaklamaOneriDto(Long makinistId, String sicil, String adSoyad, Long garId, String gar, OffsetDateTime bas, OffsetDateTime bit, long sureDk, int gece,
        boolean kayitli) {
}
