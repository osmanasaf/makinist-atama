package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.List;

/**
 * "Kuralı dene" sonucu (FR-07): taslak kural kaydedilirse {@code [bas, bit]} aralığındaki mevcut atamalardan kaçı yeni kırmızı (ENGEL) ya da yeni UYARI olur.
 * {@code ornekler}: en çok 10 satır (önce kırmızılar; tarih, sicil sırasıyla; aynı makinist/gün/metin tek satır).
 */
public record OzelKuralOnizlemeDto(LocalDate bas, LocalDate bit, int toplamAtama, int kirmiziAtama, int kirmiziMakinist, int uyariAtama, int uyariMakinist,
        List<Ornek> ornekler) {

    /** {@code durum}: KIRMIZI ya da UYARI. */
    public record Ornek(String sicil, LocalDate tarih, String trenNo, String durum, String metin) {
    }
}
