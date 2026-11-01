package com.makinistatama.kurulum;

import java.util.List;

/** Kurulum durumu (MK-171): adımlar veriden türetilir. {@code ayrinti}: sayı özeti (kişisel veri içermez). {@code gizli}: yönetici ana sayfadaki kartı gizledi. */
public record KurulumDurumDto(List<Adim> adimlar, boolean tamam, boolean gizli) {

    public record Adim(String kod, String ad, boolean yapildi, String ayrinti) {
    }

    public record GizleIstegi(boolean gizli) {
    }
}
