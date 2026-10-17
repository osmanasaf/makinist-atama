package com.makinistatama.takas;

import java.time.OffsetDateTime;

import com.makinistatama.degisiklik.Neden;

/**
 * Atamacının talep satırı (makinist kimlikleri ve ad dahil; neden notu dahil). {@code durum} etkin durumdur (okumada türetilir), {@code kayitliDurum} veritabanındaki. {@code sonKabul}: karşı onay için son an
 * (karşılıklı takas); {@code sonKalkis}: görevlerin en erken güncel kalkışı (atamacı onayı bundan önce).
 */
public record TakasSatiriDto(Long id, TakasTuru tur, TakasDurumu durum, TakasDurumu kayitliDurum, Long kaynakSeferId, Long kaynakMakinistId, String kaynakSicil, String kaynakAd,
        String kaynakGorev, Long hedefMakinistId, String hedefSicil, String hedefAd, String hedefGorev, Neden neden, String nedenNotu, OffsetDateTime olusturma,
        OffsetDateTime sonKabul, OffsetDateTime sonKalkis) {
}
