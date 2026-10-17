package com.makinistatama.makinist;

import java.time.LocalDate;
import java.util.List;

/**
 * Makinist kartı yanıtı. {@code gecerlilikBitis} türetilmiştir: sınav tarihi + {@code belge.sinav_gecerlilik_ay}.
 * Liste satırı {@link Ozet}, kart ayrıntısı {@link MakinistDto}.
 */
public record MakinistDto(Long id, String sicil, String adSoyad, Long bagliGarId, String bagliGar, MakinistDurum durum, PersonelTuru tur, LocalDate ehliyetBitis,
        LocalDate saglikBitis, LocalDate psikoteknikBitis, LocalDate sonSurus, long versiyon, List<BroveAracTipiDto> broveAracTipleri,
        List<BroveHatDto> broveHatlar, List<IzinDto> izinler) {

    public record BroveAracTipiDto(Long aracTipiId, String aracTipi, LocalDate sinavTarihi, LocalDate gecerlilikBitis) {
    }

    public record BroveHatDto(Long hatId, String hat, LocalDate sinavTarihi, LocalDate sonSurus, LocalDate gecerlilikBitis) {
    }

    public record IzinDto(Long id, LocalDate baslangic, LocalDate bitis, IzinTuru tur, IzinDurumu durum, String olusturan) {

        static IzinDto of(Izin i) {
            return new IzinDto(i.id(), i.baslangic(), i.bitis(), i.tur(), i.durum(), i.olusturan());
        }
    }

    /** Liste satırı (bröve ve izin ayrıntısı yok). */
    public record Ozet(Long id, String sicil, String adSoyad, Long bagliGarId, String bagliGar, MakinistDurum durum, PersonelTuru tur, LocalDate ehliyetBitis,
            LocalDate saglikBitis, LocalDate psikoteknikBitis) {
    }
}
