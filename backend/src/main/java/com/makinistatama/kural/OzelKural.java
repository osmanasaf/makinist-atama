package com.makinistatama.kural;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Yönetici tanımlı kural (saf kayıt). Kod saklanmaz: {@code Ö-NNN} = {@code "Ö-" + %03d id} (MK-16); önizlemedeki henüz kaydedilmemiş
 * taslak kimlik 0 taşır ({@code Ö-000}). {@code esik} süre ölçülerinde saat, diğerlerinde adet. Geçerlilik sefer gününe göredir (iki uç dahil) ve ölçüm kapsamını da kısar: kural yalnız aralıktaki görevleri sayar (MK-5, bkz. {@link OzelKuralDegerlendirici#kontrol}).
 */
public record OzelKural(long id, String ad, Olcu olcu, Pencere pencere, Integer kayanGun, Karsilastirma karsilastirma, BigDecimal esik, Kapsam kapsam,
                        Siddet siddet, LocalDate gecerlilikBas, LocalDate gecerlilikBit, String gerekce, String kaynak, long versiyon) {

    static final String ONEK = "Ö-";

    public OzelKural {
        kapsam = kapsam == null ? Kapsam.TUMU : kapsam;
    }

    /** K1 biçimi: kayan pencere yok. */
    public OzelKural(long id, String ad, Olcu olcu, Pencere pencere, Karsilastirma karsilastirma, BigDecimal esik, Kapsam kapsam, Siddet siddet,
                     LocalDate gecerlilikBas, LocalDate gecerlilikBit, String gerekce, String kaynak, long versiyon) {
        this(id, ad, olcu, pencere, null, karsilastirma, esik, kapsam, siddet, gecerlilikBas, gecerlilikBit, gerekce, kaynak, versiyon);
    }

    public static String kod(long id) {
        String s = Long.toString(id);
        return ONEK + "0".repeat(Math.max(0, 3 - s.length())) + s;
    }

    /** {@code Ö-NNN} kodundan kimlik; biçim bozuksa {@link IllegalArgumentException}. */
    static long kimlik(String kod) {
        try {
            return Long.parseLong(kod.substring(ONEK.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Özel kural kodu geçersiz: " + kod, e);
        }
    }

    public static boolean ozelMi(String kod) {
        return kod.startsWith(ONEK);
    }

    public String kod() {
        return kod(id);
    }

    /** Süre ölçülerinde eşik dakika, adet ölçülerinde adet. */
    long esikDeger() {
        return olcu.sure() ? esik.multiply(BigDecimal.valueOf(60)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
                : esik.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    boolean gecerliMi(LocalDate seferGunu) {
        return !seferGunu.isBefore(gecerlilikBas) && (gecerlilikBit == null || !seferGunu.isAfter(gecerlilikBit));
    }

    /**
     * Bu kuralın yükleme penceresine katkısı (gün, adayın etrafında iki yönde); 0 = varsayılan yeter. Ay 31 (aynı ayın en uzak günü; gece yarısını aşan görev komşu ayın başına da dokunur), hafta 7, kayan N, seri eşik + 1
     * (seri eşikten uzunsa ihlal zaten kesindir). Yıl penceresi gün sayısıyla değil yıl başı/sonu ile genişler ({@link ZamanHesabi#pencereBasi}/{@link ZamanHesabi#pencereSonu}).
     */
    int pencereGun() {
        return switch (pencere) {
            case AY -> 31;
            case HAFTA -> 7;
            case KAYAN -> kayanGun == null ? 0 : kayanGun;
            case SERI -> esik.intValue() + 1;
            default -> 0;
        };
    }

    /** "yıl", "kayan 7 gün" gibi okunur pencere adı. */
    public String pencereMetni() {
        return pencere == Pencere.KAYAN ? "kayan " + kayanGun + " gün" : pencere.ad();
    }
}
