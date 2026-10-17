package com.makinistatama.konaklama;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

/** F2b MK-44: gece sayımı tek yerde: ceil(süre / 24 s), en az 1; ay = giriş günü (Europe/Istanbul). */
class KonaklamaHesabiTest {

    private static Instant an(String s) {
        return Instant.parse(s);
    }

    @Test
    void gece_24saatTamBir_24saat1dkIki_ve_sinirlar() {
        Instant g = an("2026-10-14T08:00:00Z");
        assertEquals(1, KonaklamaHesabi.gece(g, g.plusSeconds(60)), "1 dk: en az 1");
        assertEquals(1, KonaklamaHesabi.gece(g, g.plusSeconds(18 * 3600 + 30 * 60)), "18 s 30 dk");
        assertEquals(1, KonaklamaHesabi.gece(g, g.plusSeconds(24 * 3600)), "tam 24 s");
        assertEquals(2, KonaklamaHesabi.gece(g, g.plusSeconds(24 * 3600 + 60)), "24 s 1 dk");
        assertEquals(2, KonaklamaHesabi.gece(g, g.plusSeconds(40 * 3600)), "40 s");
        assertEquals(2, KonaklamaHesabi.gece(g, g.plusSeconds(48 * 3600)), "tam 48 s");
        assertEquals(3, KonaklamaHesabi.gece(g, g.plusSeconds(48 * 3600 + 60)), "48 s 1 dk");
    }

    @Test
    void ay_girisGunununAyi_istanbulSaatiyle() {
        // 31 Ekim 21:30 UTC = 1 Kasım 00:30 İstanbul (UTC+3): giriş günü Kasım
        assertEquals(YearMonth.of(2026, 11), KonaklamaHesabi.ay(an("2026-10-31T21:30:00Z")));
        assertEquals(YearMonth.of(2026, 10), KonaklamaHesabi.ay(an("2026-10-31T20:59:00Z")));
    }
}
