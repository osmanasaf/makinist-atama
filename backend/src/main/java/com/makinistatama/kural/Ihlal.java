package com.makinistatama.kural;

import java.util.List;

/**
 * Bir kural ihlali. {@code olculen}/{@code esik} {@code birim} cinsindendir (s, ay, gun; sayisal olmayan
 * kurallarda 0 ve bos birim). {@code metin} insan okur Turkcedir.
 */
public record Ihlal(String kod, Siddet siddet, double olculen, double esik, String birim, String metin) {

    /** Kural KAPALI degilse listeye ekler; siddet Parametreler'den gelir. */
    static void ekle(List<Ihlal> liste, Parametreler p, String kod, double olculen, double esik, String birim,
                     String metin) {
        Siddet s = p.siddet(kod);
        if (s != Siddet.KAPALI) {
            liste.add(new Ihlal(kod, s, olculen, esik, birim, metin));
        }
    }
}
