package com.makinistatama.rapor;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.makinistatama.atama.AtamaRol;
import com.makinistatama.makinist.IzinTuru;
import com.makinistatama.makinist.PersonelTuru;

/**
 * Aylık çizelge ızgarası (F5, MK-177): AKTIF personel (satır) × ay günleri (sütun). Hücre SEYREK: yalnız görevi ya da onaylı izni olan günler listelenir ({@code gun} 1..gunSayisi); 300 × 31
 * için JSON ≤ 800 KB. Satır toplamları {@link AdaletServisi} satırlarından (aynı {@code PuantajHesabi}; ikinci formül yok) alınır. Salt okunur; makinist görmez (OK-35).
 *
 * @param kontrol {@code true} ise görevlerde canlı kırmızı bayrağı hesaplanmıştır (ağır hesap, isteğe bağlı); {@code false} iken {@code kirmizi} alanı hiç yazılmaz
 */
public record CizelgeDto(String ay, int gunSayisi, PersonelTuru tur, Long garId, boolean kontrol, OffsetDateTime uretimZamani, String ureten, List<Satir> satirlar, List<Tatil> tatiller) {

    /** Bir personel satırı: kimlik, adalet raporuyla aynı toplamlar ve seyrek hücreler. */
    public record Satir(Long makinistId, String sicil, String adSoyad, String bagliGar, double gorevSaat, double geceSaat, double km, int seferSayisi, int yolculukSayisi, int izinGunu,
            List<Hucre> hucreler) {
    }

    /** Bir gün: onaylı izin türü ({@code null} = izin yok) ve o günün görevleri (kalkışa göre sıralı; boşsa yazılmaz). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Hucre(int gun, IzinTuru izin, List<Gorev> gorevler) {
    }

    /**
     * Hücredeki görev. {@code seferId}: serbest intikalde yok ({@code null}). {@code gece}/{@code yolculuk}/{@code kirmizi} yalnız doğruysa yazılır (JSON boyutu); {@code gece}: kalkış ya da
     * varış gece penceresinde ({@code gece.baslangic}-{@code gece.bitis}) ya da sefer ertesi güne sarkıyor (atama masasının gece göstergesiyle aynı kural).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Gorev(Long seferId, String trenNo, String kalkis, String varis, AtamaRol rol, Boolean gece, Boolean yolculuk, Boolean kirmizi) {
    }

    /** Ay içindeki resmi tatil (yalnız gösterim): ayın günü ve ad. */
    public record Tatil(int gun, String ad) {
    }
}
