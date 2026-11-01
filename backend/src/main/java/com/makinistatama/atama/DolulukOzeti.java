package com.makinistatama.atama;

/** Aralıktaki iptal olmayan sefer sayısı ve bunların kaç tanesinin ASIL'ı var / yok (F4, MK-180). "BOŞ" = gün listesindeki tanımla aynı: ASIL yok. */
public record DolulukOzeti(int toplam, int dolu, int bos) {
}
