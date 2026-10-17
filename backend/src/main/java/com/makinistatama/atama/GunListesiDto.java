package com.makinistatama.atama;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.makinistatama.sefer.SeferDurumu;

/**
 * Günün sefer listesi (planlanan gün, saat sırası): atama masası ve yazdırma AYNI uçtan beslenir. {@code uretimZamani} sunucu yanıt zamanıdır
 * ("Son güncelleme"). "BOŞ" = satırda ASIL rolünde görevli yok. {@code kirmizi}: satırdaki herhangi bir atama kırmızı (MK-5, her okumada canlı hesaplanır). {@code bosNeden}: ASIL'ı olmayan, kalkışı geçmemiş seferde
 * kurallara tam uyan kimse yoksa otomatik atamanın "0/N uygun: ..." özeti (UX-13); aksi halde null.
 * {@code kondukturEksik} (F3b, MK-87): ayar {@code konduktor.yolcu_trenine_gerekli}=1 iken iptal olmayan YOLCU trenine KONDUKTOR atanmamış (BİLGİ rozeti; kırmızı/BOŞ değil, atamayı engellemez); ayar kapalıyken hep false.
 * {@code parcaNo/parcaSayisi}: ara istasyonda makinist değişen trenin parçası (F2c; satır sıradan bir sefer satırıdır), aksi halde null.
 */
public record GunListesiDto(LocalDate tarih, OffsetDateTime uretimZamani, List<GunSatiriDto> satirlar, int kondukturEksikSayisi) {

    public record GunSatiriDto(Long seferId, String trenNo, String kalkisGar, String varisGar, OffsetDateTime planlananKalkis, OffsetDateTime planlananVaris,
            OffsetDateTime guncelKalkis, OffsetDateTime guncelVaris, int kalkisGecikmeDk, int varisGecikmeDk, String aracTipi, String hat, BigDecimal km,
            SeferDurumu durum, List<GorevliDto> makinistler, boolean kirmizi, String bosNeden, Integer parcaNo, Integer parcaSayisi,
            boolean kondukturEksik) {
    }

    /** Atamacı görünürlüğü (MK-33): görev için makinist "gördüm" demiş mi; hesabı yoksa HESAP_YOK. */
    public enum GorduDurumu {
        GORDU,
        GORMEDI,
        HESAP_YOK
    }

    /**
     * Seferde görevli. {@code kirmizi}: atama kuralları artık sağlamıyor ya da sefer iptal (neden {@code kirmiziNedenler}); {@code uyarilar}: yalnız
     * UYARI düzeyindeki ihlaller (kırmızı değil, ama atamacı görmeli).
     * {@code gordu}/{@code gorduZamani}/{@code gorduUyari}: "gördüm" bilgisi (EK alan; yazdırma kullanmaz); kalkışı geçmiş seferde null/false (iptal sefer DAHİL, OK-9). {@code gorduUyari}: GORMEDI ve kalkışa
     * {@code gordum.uyari_saat}'ten AZ kalmış.
     */
    public record GorevliDto(Long atamaId, AtamaRol rol, Long makinistId, String sicil, String ad, boolean kirmizi, List<String> kirmiziNedenler,
            List<String> uyarilar, GorduDurumu gordu, OffsetDateTime gorduZamani, boolean gorduUyari) {
    }
}
