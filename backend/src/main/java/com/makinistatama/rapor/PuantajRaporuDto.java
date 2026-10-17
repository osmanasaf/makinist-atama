package com.makinistatama.rapor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.makinistatama.makinist.PersonelTuru;

/**
 * Puantaj ön hesabı (FR-12). Ücret bordrosu DEĞİLDİR ({@code etiket}). Oranlar parametredir: boşsa ({@code saatUcretiGirildi}/{@code kmBirimGirildi} false) TL alanları
 * {@code null} döner ve ekran "oran girilmedi" der, yalnız miktar (saat, km) gösterilir. {@code demo}: veritabanında demo makinistler var (ekran "DEMO – gerçek değil" der).
 */
public record PuantajRaporuDto(String ay, OffsetDateTime uretimZamani, String ureten, String etiket, boolean demo, boolean saatUcretiGirildi, boolean kmBirimGirildi,
        Oranlar oranlar, List<Satir> satirlar, PersonelTuru tur) {

    public record Oranlar(BigDecimal saatUcreti, BigDecimal fazlaMesaiCarpani, BigDecimal geceTazminatOrani, BigDecimal kmBirimTutari, double asgariGunlukSaat,
            double normalHaftalikSaat, String gecePenceresi) {
    }

    public record Satir(Long makinistId, String sicil, String adSoyad, int calisilanGun, double fiiliSaat, double ucretlendirilenSaat, double fazlaMesaiSaat,
            double geceSaat, double km, int kmBilinmeyenSefer, BigDecimal fazlaMesaiTl, BigDecimal geceTl, BigDecimal kmTl) {
    }
}
