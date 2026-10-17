package com.makinistatama.konaklama;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;

import com.makinistatama.ortak.ZamanAyari;

/**
 * Gece sayımı (MK-44, TEK yer): {@code max(1, ceil(süre / 24 saat))}, dakika hassasiyetinde (24 s tam = 1, 24 s 1 dk = 2); rapor ayı = GİRİŞ günü (Europe/Istanbul).
 * SAF (Spring/JPA yok). İş kuralı onay bekler (S3): değişirse yalnız bu sınıf ve {@code KonaklamaHesabiTest}.
 */
public final class KonaklamaHesabi {

    private KonaklamaHesabi() {
    }

    public static int gece(Instant giris, Instant cikis) {
        long dk = Duration.between(giris, cikis).toMinutes();
        return (int) Math.max(1, (dk + 24 * 60 - 1) / (24 * 60));
    }

    public static YearMonth ay(Instant giris) {
        return YearMonth.from(giris.atZone(ZamanAyari.BOLGE));
    }
}
