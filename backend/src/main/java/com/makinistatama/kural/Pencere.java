package com.makinistatama.kural;

/**
 * Ölçünün hesaplandığı zaman aralığı (arayüzde "Zaman aralığı"; kod adı pencere). {@code KAYAN} gün hizalı N günlük kayan aralık ({@code kayanGun}), {@code SERI}
 * (arayüzde "üst üste") aralıksız gün dizisidir.
 */
public enum Pencere {
    SEFER("sefer", "Yalnız seferin kendisine bakılır."),
    NOBET("nöbet", "Aralarında az boşluk olan görevler (varsayılan 4 saatten kısa) tek nöbet sayılır; atamanın içinde olduğu nöbet ölçülür."),
    GUN("gün", "Takvim günü (00:00–24:00); atamanın dokunduğu her gün ayrı ölçülür."),
    HAFTA("hafta", "Takvim haftası (Pazartesi–Pazar); atamanın dokunduğu her hafta ayrı ölçülür."),
    AY("ay", "Takvim ayı; atamanın dokunduğu her ay ayrı ölçülür."),
    YIL("yıl", "Takvim yılı (1 Ocak–31 Aralık)."),
    KAYAN("kayan gün", "Seçtiğiniz gün sayısı kadar art arda günlük her aralık ölçülür (atamanın günü dahil), en kötüsü sayılır."),
    SERI("üst üste", "Art arda gelen günler; arada boş bir gün olursa sayım yeniden başlar.");

    private final String ad;
    private final String aciklama;

    Pencere(String ad, String aciklama) {
        this.ad = ad;
        this.aciklama = aciklama;
    }

    public String ad() {
        return ad;
    }

    /** Tek cümlelik açıklama (katalog ve form yardımı). */
    public String aciklama() {
        return aciklama;
    }
}
