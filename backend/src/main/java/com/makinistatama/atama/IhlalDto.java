package com.makinistatama.atama;

import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.Siddet;

/** Kural ihlali yanıtı: kod, şiddet, ölçülen/eşik (birimiyle) ve insan okur Türkçe metin. */
public record IhlalDto(String kod, Siddet siddet, double olculen, double esik, String birim, String metin) {

    static IhlalDto of(Ihlal i) {
        return new IhlalDto(i.kod(), i.siddet(), yuvarla(i.olculen()), yuvarla(i.esik()), i.birim(), i.metin());
    }

    /** Yanıtta kayan nokta kuyruğu (1.4833333333333334) olmasın: 2 ondalık (BUG-13). */
    private static double yuvarla(double d) {
        return Math.round(d * 100) / 100.0;
    }
}
