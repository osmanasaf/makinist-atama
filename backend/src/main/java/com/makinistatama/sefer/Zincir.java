package com.makinistatama.sefer;

import java.time.LocalDate;
import java.util.List;

/** Parçalı trenin parça zinciri (MK-60): {@code idler} parça sırasıyla (ilki parça 1 = birleştirmede yaşayan satır). Satır kimliklerinden ibarettir; atama paketi okuyabilir, Sefer varlığını görmez. */
public record Zincir(String trenNo, LocalDate trenGunu, List<Long> idler) {

    public long parca1Id() {
        return idler.getFirst();
    }

    /** Birleştirmede silinecek satırlar (parça 2..N), parça sırasıyla. */
    public List<Long> silinecekIdler() {
        return idler.subList(1, idler.size());
    }
}
