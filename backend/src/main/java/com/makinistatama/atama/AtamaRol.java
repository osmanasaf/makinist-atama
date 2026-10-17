package com.makinistatama.atama;

import java.util.Set;

import com.makinistatama.makinist.PersonelTuru;

/**
 * Seferdeki yer: bir ASIL ve en çok bir YARDIMCI makinist (MK-1; kurallar iki rolde aynıdır). KONDUKTOR (F3b, MK-86): seferde en çok bir kondüktör (tren şefi);
 * ASIL/YARDIMCI ile ayrı satırdır. YOLCU (F2b, MK-38): personel o seferde görevsiz yolcu olarak gider (başka bir gara intikal); bir seferde birden çok olabilir,
 * sürüş sayılmaz ama konumu taşır ve çakışma/dinlenme kurallarına girer. Sıra (gün listesi): ASIL, YARDIMCI, KONDUKTOR, YOLCU.
 */
public enum AtamaRol {
    ASIL,
    YARDIMCI,
    KONDUKTOR,
    YOLCU;

    /**
     * Bu role atanabilecek personel türleri (MK-86): rol-tür uyumunun TEK kaynağı (ata, aday havuzu, otomatik yazım, takas aynı çağrıyı kullanır).
     * İki tablo arasında olduğundan DB CHECK/trigger yoktur.
     */
    public Set<PersonelTuru> turler() {
        return switch (this) {
            case ASIL, YARDIMCI -> PersonelTuru.MAKINISTLER;
            case KONDUKTOR -> PersonelTuru.KONDUKTORLER;
            case YOLCU -> PersonelTuru.TUMU;
        };
    }
}
