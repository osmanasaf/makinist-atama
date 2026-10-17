package com.makinistatama.sefer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Excel yükleme yanıtı. Önizlemede ({@code onizleme = true}) {@code eklenen} "eklenecek" demektir ve hiçbir şey yazılmamıştır.
 * {@code satirlar}: geçerli satırlar (durum YENI ya da ZATEN_VAR); {@code hatalar}: sayfa + satır no + tren no + neden. {@code engelli}: onayı durduran hata var (ekran "Onayla"yı kilitler,
 * sunucu onayı reddeder).
 */
public record YuklemeSonucuDto(boolean onizleme, int toplamSatir, int eklenen, int zatenVar, int hataSayisi, List<Satir> satirlar,
        List<Hata> hatalar, boolean engelli) {

    public record Satir(int satirNo, String trenNo, LocalDate tarih, String kalkisGar, String varisGar, OffsetDateTime kalkis, OffsetDateTime varis,
            String aracTipi, String hat, String durum, Integer parcaNo, Integer parcaSayisi) {
    }

    public record Hata(int satirNo, String neden, String sayfa, String trenNo) {
    }
}
