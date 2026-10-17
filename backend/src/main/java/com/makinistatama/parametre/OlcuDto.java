package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.util.List;

import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.Pencere;

/** Ölçü kataloğu satırı (MK-22): form seçenekleri ve eşik sınırları sunucudaki {@link Olcu} enum'undan gelir. */
public record OlcuDto(Olcu kod, String ad, String birim, String aciklama, List<PencereSecenegi> pencereler, List<KarsilastirmaSecenegi> karsilastirmalar) {

    /** {@code esikAlt} dahil değilse (süre) 0'dan büyük, dahilse (adet) 0 serbest; {@code esikUst} dahil. */
    public record PencereSecenegi(Pencere kod, String ad, String aciklama, BigDecimal esikAlt, boolean esikAltDahil, BigDecimal esikUst) {
    }

    public record KarsilastirmaSecenegi(Karsilastirma kod, String ad, String aciklama) {
    }
}
