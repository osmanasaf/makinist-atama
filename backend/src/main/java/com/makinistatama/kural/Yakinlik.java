package com.makinistatama.kural;

/**
 * Sınıra yakınlık BİLGİSİ (F3, MK-172): kuralın sınırına ayarlanabilir bir marjdan daha yakın ama sınırı AŞMAMIŞ ölçüm. {@link Ihlal} DEĞİLDİR: kural motorunun çıktısına girmez,
 * hiçbir atamayı engel/uyarı/kırmızı yapmaz. {@code olculen/sinir/kalan} {@code birim} cinsindendir ("dk" ya da "hafta"); {@code kalan >= 0} (dinlenme/hafta tatili/aktarma için
 * ölçülen - eşik). {@code metin} insan okur Türkçedir ("Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s").
 */
public record Yakinlik(String kod, double olculen, double sinir, double kalan, String birim, String metin) {
}
