package com.makinistatama.kural;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tek bir (aday gorev x makinist) degerlendirmesinin tum girdisi; DB'siz, saf veri.
 * <ul>
 * <li>{@code aday}: atanmak istenen gorev. {@code digerGorevler}: makinistin diger (iptal olmayan) gorevleri;
 *     aday bunlarin icinde YOKTUR.</li>
 * <li>Pencere: sefer gununden -15 / +8 gun (K-18 iki hafta geri bakar). K-15 (yillik fazla mesai) icin
 *     {@code digerGorevler} ayrica yil basindan adayin haftasina kadarki TUM gorevleri icermelidir; eksik
 *     haftalar 0 fazla mesai sayilir. Hafta, Pazar gununun yilina yazilir.</li>
 * <li>{@code pencereOncesiHatSonSurus} (hatId -> varis gunu) ve {@code pencereOncesiGenelSonSurus}: pencere
 *     oncesindeki son surus (toplu sorgu; bos/null olabilir); K-07/K-08 bunlari kart, hat brovesi ve
 *     penceredeki gorevlerle birlestirip en yenisini alir.</li>
 * <li>{@code tanimAdlari}: id -> ad (gar/hat/arac tipi); yalniz mesajlar icin, eksikse "#id".</li>
 * <li>{@code simdi}: bos degilse K-22 (bitmis sefere atama) denetlenir; kirmizi atama hesabi gibi tarihsel okumalarda bos birakilir.</li>
 * </ul>
 */
public record KuralBaglami(Gorev aday, List<Gorev> digerGorevler, MakinistBilgisi makinist,
                           Map<Long, LocalDate> pencereOncesiHatSonSurus, LocalDate pencereOncesiGenelSonSurus,
                           Map<Long, String> tanimAdlari, Parametreler parametreler, Instant simdi) {

    /** {@code simdi} olmadan: K-22 denetlenmez. */
    public KuralBaglami(Gorev aday, List<Gorev> digerGorevler, MakinistBilgisi makinist, Map<Long, LocalDate> pencereOncesiHatSonSurus,
                        LocalDate pencereOncesiGenelSonSurus, Map<Long, String> tanimAdlari, Parametreler parametreler) {
        this(aday, digerGorevler, makinist, pencereOncesiHatSonSurus, pencereOncesiGenelSonSurus, tanimAdlari, parametreler, null);
    }

    public KuralBaglami {
        digerGorevler = List.copyOf(digerGorevler);
        pencereOncesiHatSonSurus = pencereOncesiHatSonSurus == null ? Map.of() : Map.copyOf(pencereOncesiHatSonSurus);
        tanimAdlari = tanimAdlari == null ? Map.of() : Map.copyOf(tanimAdlari);
    }

    /** Diger gorevler + aday (aday son elemandir). */
    List<Gorev> tumGorevler() {
        List<Gorev> t = new ArrayList<>(digerGorevler);
        t.add(aday);
        return t;
    }

    String ad(long id) {
        return tanimAdlari.getOrDefault(id, "#" + id);
    }
}
