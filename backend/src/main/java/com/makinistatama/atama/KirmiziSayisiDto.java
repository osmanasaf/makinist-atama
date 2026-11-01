package com.makinistatama.atama;

import java.time.LocalDate;

/**
 * Aralıktaki (iki uç dahil, planlanan gün) kırmızı sefer ve kırmızı atama sayısı; üst çubuk rozeti. {@code kirmiziIntikal} (OK-28): canlı kırmızı serbest intikal sayısı; ATAMA sayacına
 * KATILMAZ (kırmızıAtama aynı kalır) ama gizli de kalmaz, ayrı gösterilir. {@code ilkKirmiziGun} (B-2): aralıktaki en erken kırmızı SEFER günü, yoksa {@code null}; ana sayfa
 * "kontrol gerekli" bağlantısı doğru güne gitsin diye (toplamsal alan).
 */
public record KirmiziSayisiDto(LocalDate bas, LocalDate bit, int kirmiziSefer, int kirmiziAtama, int kirmiziIntikal, LocalDate ilkKirmiziGun) {
}
