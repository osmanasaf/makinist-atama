package com.makinistatama.makinist;

import java.util.List;

/**
 * Makinist Excel içe aktarma yanıtı (sefer {@code YuklemeSonucuDto}'su ile aynı alan adları). Önizlemede ({@code onizleme = true}) {@code eklenen} "eklenecek" demektir ve hiçbir şey yazılmamıştır.
 * {@code satirlar}: geçerli satırlar (durum YENI ya da ZATEN_VAR: var olan sicil güncellenmez, atlanır); {@code hatalar}: sayfa + satır no + sicil + neden;
 * {@code eksikTanimlar}: dosyada geçen ama Tanımlar'da olmayan gar / hat / araç tipi adları (tanım ÜRETİLMEZ).
 */
public record MakinistYuklemeSonucuDto(boolean onizleme, int toplamSatir, int eklenen, int zatenVar, int hataSayisi, int uyariSayisi, List<Satir> satirlar, List<Hata> hatalar,
        List<EksikTanim> eksikTanimlar) {

    public record Satir(int satirNo, String sicil, String adSoyad, PersonelTuru tur, String bagliGar, MakinistDurum makinistDurumu, int aracTipiSayisi, int hatSayisi, String durum,
            List<String> uyarilar) {
    }

    public record Hata(String sayfa, int satirNo, String sicil, String neden) {
    }

    public record EksikTanim(String tip, String ad) {
    }
}
