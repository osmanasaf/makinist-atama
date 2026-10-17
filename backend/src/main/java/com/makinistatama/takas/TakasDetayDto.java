package com.makinistatama.takas;

import java.util.List;

import com.makinistatama.atama.OneriDto;
import com.makinistatama.atama.TakasIhlali;

/**
 * Atamacı ayrıntı ekranı: kural ihlallerinin AYRINTISI yalnız burada (hangi makinist hangi kural, ENGEL/UYARI). {@code oneri}: bırakmada mevcut "yerine ata" önerisi (en çok 3 aday);
 * talep kapanmış ya da bayatsa {@code ihlaller} boş, {@code oneri} null.
 */
public record TakasDetayDto(TakasSatiriDto talep, List<TakasIhlali> ihlaller, OneriDto oneri) {
}
