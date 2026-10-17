package com.makinistatama.kural;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Özel kural ölçü kataloğu (MK-18, MK-22): yalnız VERİ (ad, birim, açıklama, izinli pencere/karşılaştırma, eşik aralığı); hesap
 * {@link OzelKuralDegerlendirici}'de {@code switch (olcu)} ile yapılır. Hem sunucu doğrulaması hem {@code GET /api/ozel-kural/katalog}
 * bu enum'dan beslenir (ekranda ikinci bir tablo yok). "Azalan" ölçüler (atama eklemek değeri asla artırmaz) yalnız "en az", diğerleri yalnız "en fazla" ile kullanılır.
 */
public enum Olcu {

    TOPLAM_GOREV_SURESI("Toplam görev süresi", "s", false, "Hazırlık ve alıkoyma dahil çalışma süresinin seçilen zaman aralığındaki toplamı.",
            EnumSet.of(Pencere.NOBET, Pencere.GUN, Pencere.HAFTA, Pencere.AY, Pencere.YIL, Pencere.KAYAN)),
    GECE_SURESI("Gece süresi", "s", false, "Çalışma süresinin gece saatlerine (parametrelerdeki gece saatleri) düşen kısmının toplamı.",
            EnumSet.of(Pencere.NOBET, Pencere.GUN, Pencere.HAFTA, Pencere.AY, Pencere.KAYAN)),
    TEK_SEFER_SURESI("Tek sefer süresi", "s", false, "Seferin kalkıştan varışa süresi.", EnumSet.of(Pencere.SEFER)),
    GOREV_SAYISI("Görev sayısı", "adet", false, "Zaman aralığındaki görev sayısı (görev kalkış gününe yazılır).",
            EnumSet.of(Pencere.GUN, Pencere.HAFTA, Pencere.AY, Pencere.KAYAN)),
    GECE_NOBETI_SAYISI("Gece nöbeti sayısı", "adet", false, "Zaman aralığındaki gece nöbeti sayısı (nöbet başladığı güne yazılır).",
            EnumSet.of(Pencere.HAFTA, Pencere.AY, Pencere.KAYAN)),
    ARDISIK_CALISMA_GUNU("Ardışık çalışma günü", "gün", false, "Görevi olan günlerin üst üste dizisinin uzunluğu; görevsiz bir gün sayımı sıfırlar.",
            EnumSet.of(Pencere.SERI)),
    ARDISIK_GECE_GUNU("Ardışık gece günü", "gün", false, "Gece nöbeti tutulan günlerin üst üste dizisinin uzunluğu; gece nöbetsiz bir gün sayımı sıfırlar. Gece yarısından sonra başlayan nöbet, gecenin başladığı güne yazılır (Cuma 01:00 = Perşembe gecesi).",
            EnumSet.of(Pencere.SERI)),
    GOREVSIZ_ARALIK("En uzun görevsiz aralık", "s", true, "Zaman aralığındaki en uzun kesintisiz görevsiz süre.",
            EnumSet.of(Pencere.GUN, Pencere.HAFTA, Pencere.AY)),
    IZIN_GUNU("İzin günü sayısı", "gün", true, "Zaman aralığında hiç görevi olmayan gün sayısı; boş günler izin sayılır.",
            EnumSet.of(Pencere.HAFTA, Pencere.AY, Pencere.KAYAN)),
    NOBETLER_ARASI_DINLENME("Nöbetler arası dinlenme", "s", true, "Atamanın nöbeti ile önceki ve sonraki nöbet arasındaki boşluk.",
            EnumSet.of(Pencere.NOBET));

    /** Eşik aralığı: {@code alt} dahil değil (süre) ya da dahil (adet/gün); {@code ust} dahil. */
    public record EsikAraligi(BigDecimal alt, boolean altDahil, BigDecimal ust) {
    }

    private final String ad;
    private final String birim;
    private final boolean azalan;
    private final String aciklama;
    private final Set<Pencere> pencereler;

    Olcu(String ad, String birim, boolean azalan, String aciklama, Set<Pencere> pencereler) {
        this.ad = ad;
        this.birim = birim;
        this.azalan = azalan;
        this.aciklama = aciklama;
        this.pencereler = pencereler;
    }

    public String ad() {
        return ad;
    }

    /** "s" (saat), "gün" ya da "adet". */
    public String birim() {
        return birim;
    }

    public String aciklama() {
        return aciklama;
    }

    public boolean sure() {
        return birim.equals("s");
    }

    /** Azalan ölçü: atama eklemek değeri asla artırmaz (izin günü, görevsiz aralık, dinlenme); yalnız "en az" ile kullanılır. */
    public boolean azalan() {
        return azalan;
    }

    public Set<Pencere> izinliPencereler() {
        return pencereler;
    }

    /** Artan ölçülerde yalnız "en fazla", azalan ölçülerde yalnız "en az": "ayda en az 100 saat çalışma" gibi hedefler atama anında kesinleşmez [V2]. */
    public Set<Karsilastirma> izinliKarsilastirmalar() {
        return EnumSet.of(azalan ? Karsilastirma.EN_AZ : Karsilastirma.EN_FAZLA);
    }

    /** Eşik aralığı; anlamsız değerler (ör. "görev sayısı en fazla 0", "ayda en az 0 gün izin") alt sınırla dışarıda kalır. */
    public EsikAraligi esikAraligi(Pencere p) {
        return esikAraligi(p, null);
    }

    /** {@code kayanGun}: pencere KAYAN ise N (katalogda null = en geniş, 366). */
    public EsikAraligi esikAraligi(Pencere p, Integer kayanGun) {
        int n = kayanGun == null ? 366 : kayanGun;
        return switch (this) {
            case ARDISIK_CALISMA_GUNU, ARDISIK_GECE_GUNU -> aralik(1, true, 31);
            case IZIN_GUNU -> aralik(1, true, switch (p) {
                case HAFTA -> 7;
                case AY -> 28; // en küçük ay
                default -> n;
            });
            case GOREV_SAYISI -> aralik(1, true, switch (p) {
                case GUN -> 50;
                case HAFTA -> 350;
                case KAYAN -> 50 * n;
                default -> 1500;
            });
            case GECE_NOBETI_SAYISI -> aralik(0, true, switch (p) { // 0: "hiç gece nöbeti verme" anlamlı (kapsamla birlikte)
                case HAFTA -> 14;
                case KAYAN -> 2 * n;
                default -> 62;
            });
            default -> aralik(0, false, switch (p) {
                case SEFER -> 48;
                case NOBET -> 72;
                case GUN -> 24;
                case HAFTA -> 168;
                case AY -> 744;
                case YIL -> 8784;
                case KAYAN -> 24 * n;
                case SERI -> 24;
            });
        };
    }

    private static EsikAraligi aralik(int alt, boolean altDahil, int ust) {
        return new EsikAraligi(BigDecimal.valueOf(alt), altDahil, BigDecimal.valueOf(ust));
    }

    // ---- doğrulama: hata metni ya da null (alan adını çağıran bilir)

    public String pencereHatasi(Pencere p) {
        return pencereler.contains(p) ? null : ad + " ölçüsü \"" + p.ad() + "\" zaman aralığıyla kullanılamaz.";
    }

    public String karsilastirmaHatasi(Karsilastirma k) {
        return izinliKarsilastirmalar().contains(k) ? null
                : ad + " ölçüsünde \"" + k.ad() + "\" kullanılamaz; yalnız \"" + izinliKarsilastirmalar().iterator().next().ad() + "\".";
    }

    /** Kayan pencere için N (2–366) zorunlu, diğer pencerelerde yazılamaz. */
    public static String kayanGunHatasi(Pencere p, Integer kayanGun) {
        if (p == Pencere.KAYAN) {
            return kayanGun == null || kayanGun < 2 || kayanGun > 366 ? "Kayan gün sayısı 2 ile 366 gün arasında olmalı." : null;
        }
        return kayanGun != null ? "Gün sayısı yalnız kayan gün aralığında yazılır." : null;
    }

    /** {@code pencere} izinli olmalıdır (önce {@link #pencereHatasi}). Süre ölçülerinde eşik dakika duyarlığında olmalıdır. */
    public String esikHatasi(Pencere p, BigDecimal esik) {
        return esikHatasi(p, esik, null);
    }

    public String esikHatasi(Pencere p, BigDecimal esik, Integer kayanGun) {
        EsikAraligi a = esikAraligi(p, kayanGun);
        boolean altta = a.altDahil() ? esik.compareTo(a.alt()) < 0 : esik.compareTo(a.alt()) <= 0;
        if (altta || esik.compareTo(a.ust()) > 0) {
            if (a.altDahil() && a.alt().signum() > 0) {
                return "Koşul " + sinirMetni(a.alt()) + " ile " + sinirMetni(a.ust()) + " " + birim + " arasında olmalı.";
            }
            return "Koşul " + (a.altDahil() ? "" : "0'dan büyük ve ") + sinirMetni(a.ust()) + " " + birim + " ya da daha az olmalı.";
        }
        if (sure()) {
            if (esik.scale() > 2 && esik.stripTrailingZeros().scale() > 2) {
                return "Süre en çok iki ondalıklı yazılabilir (ör. 7,5).";
            }
            if (esik.multiply(BigDecimal.valueOf(60)).stripTrailingZeros().scale() > 0) {
                return "Süre dakika duyarlığında olmalı (ör. 7,5 ya da 8,25).";
            }
        } else if (esik.stripTrailingZeros().scale() > 0) {
            return "Koşul tam sayı olmalı.";
        }
        return null;
    }

    private static String sinirMetni(BigDecimal b) {
        return b.stripTrailingZeros().toPlainString();
    }
}
