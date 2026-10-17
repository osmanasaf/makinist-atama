package com.makinistatama.atama;

/** Makinistin kendi görevi için onay durumu: {@code YENI} hiç görülmedi, {@code GORULDU} güncel özet görüldü, {@code DEGISTI} görüldükten sonra özet değişti (yeniden onay). */
public enum GordumDurumu {
    YENI,
    GORULDU,
    DEGISTI
}
