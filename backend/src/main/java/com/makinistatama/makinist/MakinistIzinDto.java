package com.makinistatama.makinist;

import java.time.LocalDate;

/** Makinistin kendi izin satırı: karar veren/açan personel adı ve başka makinist bilgisi yok. */
public record MakinistIzinDto(Long id, LocalDate baslangic, LocalDate bitis, IzinTuru tur, IzinDurumu durum) {

    static MakinistIzinDto of(Izin i) {
        return new MakinistIzinDto(i.id(), i.baslangic(), i.bitis(), i.tur(), i.durum());
    }
}
