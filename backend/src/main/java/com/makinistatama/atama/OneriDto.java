package com.makinistatama.atama;

import java.util.List;

/**
 * "Yerine ata" önerisi (FR-07): mevcut makinist hariç ilk 3 ENGEL'siz aday (UYARI'sızlar önce; sonra bu ay saat, km, sicil), her biri gerekçesiyle.
 * Aday yoksa {@code uygunAdayYok} ve {@code mesaj} ("0/N uygun: ..." ihlal özeti) dolar.
 */
public record OneriDto(List<Oneri> oneriler, boolean uygunAdayYok, String mesaj) {

    public record Oneri(Long makinistId, String sicil, String adSoyad, String bagliGar, double buAySaat, double buAyKm, List<IhlalDto> uyarilar,
            String gerekce) {
    }
}
