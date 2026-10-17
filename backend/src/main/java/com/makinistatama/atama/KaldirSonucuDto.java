package com.makinistatama.atama;

import java.util.List;

/**
 * Atama kaldırmanın yan etkisi: aynı makinistin kaldırmadan önce kırmızı OLMAYAN, sonra kırmızı olan atamaları (ör. nöbet bölününce gece sınıfı değişir, K-18).
 * Kural gereğidir (hata değil) ama atamacı farkında olmalıdır; her iki liste de boşsa uç 204 döner.
 */
public record KaldirSonucuDto(List<Etkilenen> yeniKirmizi, List<EtkilenenIntikal> yeniKirmiziIntikal) {

    /** Aynı makinistin serbest intikali kaldırmadan sonra kırmızı oldu (ör. kaldırılan sefer konumu geri getirdi: K-20); {@code ozet}: "İntikal · Eskişehir 10:00 → Ankara 11:30 · Servis". */
    public record EtkilenenIntikal(Long intikalId, String ozet, List<String> nedenler) {
    }

    public record Etkilenen(Long atamaId, Long seferId, String trenNo, List<String> nedenler) {
    }
}
