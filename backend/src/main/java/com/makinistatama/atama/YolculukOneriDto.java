package com.makinistatama.atama;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Yolculuk öner" yanıtı (F2b, MK-41): hedef görevden önce, hedefin kalkış garına varan en çok 3 yolculuk. {@code aktarmaDk}: yolculuk varışı ile hedef başlangıcı
 * arası; {@code uyarilar}: yolculuk ve hedef için ENGEL olmayan ihlaller (varsa "Yolculukla ata" gerekçe ister).
 * {@code intikal} (İ-2, MK-110): gar süre tablosuna göre TEK serbest intikal önerisi; {@code null} = öneri yok (süre tanımsız, konum zaten hedef garı, geçmiş saat ya da kural engeli).
 */
public record YolculukOneriDto(List<Oneri> oneriler, IntikalOneri intikal) {

    public YolculukOneriDto(List<Oneri> oneriler) {
        this(oneriler, null);
    }

    public record Oneri(Long seferId, String trenNo, String kalkisGar, String varisGar, OffsetDateTime kalkis, OffsetDateTime varis, long aktarmaDk, List<IhlalDto> uyarilar,
            String gerekce) {
    }

    /** Serbest intikal önerisi: saatler sunucuda hesaplanır (varış = hedef başlangıcı - {@code yolculuk.aktarma_dk}, kalkış = varış - tablodaki süre). */
    public record IntikalOneri(String kalkisGar, String varisGar, OffsetDateTime kalkis, OffsetDateTime varis, int sureDk, long aktarmaDk, List<IhlalDto> uyarilar, String gerekce) {
    }
}
