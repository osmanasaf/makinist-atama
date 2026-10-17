package com.makinistatama.takas;

import java.util.List;

/**
 * Talep yaşam döngüsü (MK-34). {@code BAYAT} ve {@code SURESI_DOLDU} okumada TÜRETİLİR ({@code TakasServisi.etkinDurum}); veritabanına yalnız bir yazma yolu kapattığında yazılır.
 */
public enum TakasDurumu {
    KARSI_ONAY_BEKLIYOR,
    ATAMACI_ONAYI_BEKLIYOR,
    ONAYLANDI,
    REDDEDILDI,
    IPTAL,
    SURESI_DOLDU,
    BAYAT;

    static final List<TakasDurumu> ACIKLAR = List.of(KARSI_ONAY_BEKLIYOR, ATAMACI_ONAYI_BEKLIYOR);

    boolean acik() {
        return ACIKLAR.contains(this);
    }
}
