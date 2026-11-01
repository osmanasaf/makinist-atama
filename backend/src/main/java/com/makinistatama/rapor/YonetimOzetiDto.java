package com.makinistatama.rapor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Yönetim özeti (F4, MK-180; OK-44d): her sayı mevcut bir raporun AYNI sayısıdır (adalet, puantaj, kırmızı sayısı, denetim raporu, yedek, belge panosu, gün listesi); yeni formül yok. Dönem takvim ayıdır.
 * Veri yoksa dağılım alanları {@code null} (sıfıra bölme yok). {@code demo}: veritabanında demo makinist var (ekran ve Excel "DEMO – gerçek değil" der). {@code belge} DÖNEM değil BUGÜNKÜ durumdur ({@code etiket}).
 */
public record YonetimOzetiDto(String ay, OffsetDateTime uretimZamani, String ureten, boolean demo, Doluluk doluluk, Kirmizi kirmizi, Uyarili uyarili, AdaletDagilimi adalet,
        FazlaMesai fazlaMesai, Belge belge, Degisiklik degisiklik, Yedek yedek) {

    /** İptal olmayan sefer; {@code bos}: ASIL'ı olmayan (gün listesindeki "BOŞ" tanımı); {@code yuzde} = dolu / toplam, toplam 0 ise {@code null}. */
    public record Doluluk(int toplamSefer, int dolu, int bos, Double yuzde) {
    }

    /** {@code atama}: {@code /api/atama/kirmizi-sayisi} {@code kirmiziAtama}'sı (Atama Masası rozetiyle aynı). */
    public record Kirmizi(int atama, int sefer, int intikal) {
    }

    /** {@code atama}: denetim raporundaki uyarılı satır sayısı; {@code gerekceler}: atamacının yazdığı gerekçe → adet (en sık 10). */
    public record Uyarili(int atama, List<Adet> gerekceler) {
    }

    public record Adet(String ad, int adet) {
    }

    /** Adalet raporu AKTIF makinist grubunun görev saatleri. {@code ortalamaSaat} adalet raporunun kendi değeridir; {@code standartSapmaSaat} popülasyon sapmasıdır (n'e bölünür). n=0 → alanlar {@code null}. */
    public record AdaletDagilimi(int n, Double ortalamaSaat, Double standartSapmaSaat, Double minSaat, Double maxSaat) {
    }

    /** Puantaj raporundaki fazla mesai saatlerinin toplamı ve fazla mesai yapan makinist sayısı. */
    public record FazlaMesai(double toplamSaat, int makinistSayisi) {
    }

    /** AKTIF personelin en kötü belge durumuna göre dağılımı (belge panosuyla aynı sınıflama); {@code bugun} hesabın dayandığı gün. */
    public record Belge(String etiket, LocalDate bugun, int gecerli, int yaklasan, int dolmus, int girilmemis) {
    }

    /** Denetim raporunun {@code kayitSayisi}'sı ve neden (enum adı) → adet. */
    public record Degisiklik(int kayitSayisi, List<Adet> nedenler) {
    }

    /** Yedek raporundaki açığı olan gün sayısı ve toplam açık. */
    public record Yedek(int acikGunSayisi, int toplamAcik) {
    }
}
