package com.makinistatama.parametre;

public record AyarDto(String anahtar, String ad, int deger, String birim, int alt, int ust, String aciklama, int varsayilan, String grup, int sira) {

    static AyarDto of(UygulamaAyar a) {
        return new AyarDto(a.anahtar(), AyarAdlari.ad(a.anahtar()), a.deger(), a.birim(), a.alt(), a.ust(), a.aciklama(), a.varsayilan(), a.grup(), a.sira());
    }
}
