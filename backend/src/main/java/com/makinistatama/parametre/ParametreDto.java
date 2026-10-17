package com.makinistatama.parametre;

import java.math.BigDecimal;

/** Parametre ekranı satırı: kaynak ve güvenilirlik her satırda görünür (karar destek aracı notu için). {@code ad}: okunur ad ({@link AyarAdlari}); {@code varsayilan/grup/sira}: Ayarlar ekranı kataloğu (MK-93). */
public record ParametreDto(String anahtar, String ad, String deger, String tip, BigDecimal altSinir, BigDecimal ustSinir, String birim, String aciklama,
        String kaynak, String guvenilirlik, String varsayilan, String grup, int sira) {

    static ParametreDto of(KuralParametre p) {
        return new ParametreDto(p.anahtar(), AyarAdlari.ad(p.anahtar()), p.deger(), p.tip(), p.altSinir(), p.ustSinir(), p.birim(), p.aciklama(), p.kaynak(), p.guvenilirlik(),
                p.varsayilan(), p.grup(), p.sira());
    }
}
