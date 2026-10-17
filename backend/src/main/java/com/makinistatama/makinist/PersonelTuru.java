package com.makinistatama.makinist;

import java.util.EnumSet;
import java.util.Set;

/**
 * Kartın türü (F3b, MK-82): makinist ya da kondüktör (tren şefi). Kartın ömür boyu özelliğidir, oluşturmada seçilir ve sonradan değişmez.
 * {@code kural.MakinistBilgisi.Tur} saf kural paketinin ikizidir ({@code PersonelTuruSabitleriTest} adları eşler; paket yönü iki enum ister).
 * Havuz okumaları ({@code MakinistServisi.aktifDetaylar}) türü zorunlu alır: aşağıdaki sabit kümeler varsayılanın bilinçli olduğunu gösterir.
 */
public enum PersonelTuru {
    MAKINIST,
    KONDUKTOR;

    public static final Set<PersonelTuru> MAKINISTLER = Set.copyOf(EnumSet.of(MAKINIST));
    public static final Set<PersonelTuru> KONDUKTORLER = Set.copyOf(EnumSet.of(KONDUKTOR));
    public static final Set<PersonelTuru> TUMU = Set.copyOf(EnumSet.allOf(PersonelTuru.class));
}
