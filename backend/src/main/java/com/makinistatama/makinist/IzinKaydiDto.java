package com.makinistatama.makinist;

import java.time.LocalDate;

/** İzin listesi satırı (talep onayı ekranı): makinist sicil ve adıyla. */
public record IzinKaydiDto(Long id, Long makinistId, String sicil, String adSoyad, LocalDate baslangic, LocalDate bitis, IzinTuru tur, IzinDurumu durum,
        String olusturan, String kararVeren) {
}
