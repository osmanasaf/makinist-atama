package com.makinistatama.rapor;

import java.time.OffsetDateTime;
import java.util.List;

import com.makinistatama.makinist.PersonelTuru;

/**
 * Adalet raporu (FR-10): ay içinde AKTIF personel (F3b: tek tür, vars. makinist; {@code tur}) başına görev dağılımı. Ortalama, rapordaki tüm AKTIF makinistlerin (görevsizler dahil) görev saati
 * ortalamasıdır. {@code kmBilinmeyenSefer}: km'si girilmemiş seferler (km toplamlarına girmez; ekranda dipnot). {@code geceSaatiPenceresi}: gece saati
 * puantajla aynı pencereden ({@code gece.baslangic}-{@code puantaj.gece_bitis}) hesaplanır; iki rapor aynı makinist için aynı gece saatini verir.
 */
public record AdaletRaporuDto(String ay, OffsetDateTime uretimZamani, String ureten, int makinistSayisi, double ortalamaSaat, double ortalamaKm,
        int kmBilinmeyenSefer, String olcut, String geceSaatiPenceresi, List<AdaletSatiriDto> satirlar, PersonelTuru tur) {
}
