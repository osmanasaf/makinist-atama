package com.makinistatama.kural;

/**
 * Yakınlık marjları (F3, MK-173; {@code uygulama_ayar} grup MARJ). Bilgi eşikleridir, kural parametresi değil: {@link Parametreler}'e girmez. {@code aktif=false} ise
 * {@link YakinlikKontrolu} hiçbir şey hesaplamaz. Dakika/saat/hafta birimleri ayar tablosundakiyle aynıdır.
 */
public record YakinlikAyarlari(boolean aktif, int gunlukDk, int geceDk, int dinlenmeDk, int haftaTatiliDk, int yillikSaat, int aktarmaDk, int geceHafta) {

    /** Hiçbir şey üretmeyen (kapalı) ayar. */
    public static YakinlikAyarlari kapali() {
        return new YakinlikAyarlari(false, 0, 0, 0, 0, 0, 0, 0);
    }
}
