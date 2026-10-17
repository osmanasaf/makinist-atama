package com.makinistatama.rapor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;

/**
 * Şeffaflık / denetim raporu (FR-08): süzgece uyan TÜM değişiklik kayıtları, her biri tam bir bölümde. Yanıt kendi içinde kanıtlıdır: üretim zamanı,
 * üreten kullanıcı, süzgeç ve kayıt sayısı (= bölümlerdeki satır toplamı = Excel satır sayısı) birlikte gelir. Satırlar ek-yalnız kayıttan okunur.
 */
public record DegisiklikRaporuDto(OffsetDateTime uretimZamani, String ureten, Filtre filtre, int kayitSayisi, List<Bolum> bolumler) {

    public record Filtre(LocalDate bas, LocalDate bit, Long makinistId, String makinist, String trenNo, String kullanici, DegisiklikTuru tur) {
    }

    public record Bolum(String kod, String ad, int sayi, List<Satir> satirlar) {
    }

    /**
     * {@code eski}/{@code yeni}: değişiklik türüne göre okunur metin (atamada {@code ASIL:D001}, gecikmede güncel saat aralığı, parametrede değer).
     * {@code uyarili}: atama anında UYARI düzeyinde ihlal vardı; {@code uyariGerekcesi} atamacının yazdığı gerekçe. {@code otomatik}: kaydı otomatik atama üretti (ya da otomatik atama çalıştırma kaydı). {@code uyarilar}: atama anında UYARI veren kuralların "kod: metin" satırları (kural adı metinde; boş olabilir). Kural ekleme/güncelleme satırlarında {@code eski}/{@code yeni} okunur metindir (ham JSON değil).
     */
    public record Satir(Long id, OffsetDateTime zaman, String kim, DegisiklikTuru tur, String turAdi, String sicil, String trenNo, LocalDate seferTarihi,
            String eski, String yeni, Neden neden, String nedenNotu, boolean uyarili, String uyariGerekcesi, boolean otomatik, List<String> uyarilar) {
    }
}
