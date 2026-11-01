package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kural profili paketi (MK-162): tek JSON belge, sürümlü, ad tabanlı. Anahtar kümesi veritabanı kataloğundan gelir (kodda anahtar listesi yok); K-10 pakette yoktur.
 * {@code dayanak}: anahtar -> değerin dayandığı kaynak parçası (hazır profillerde). Özel kural kapsamı kimlik değil tanım ADI taşır (başka kuruluma taşınabilir).
 * Değerler bilerek ham metin/sayıdır: bozuk bir değer tüm dosyayı değil yalnız kendi satırını GECERSIZ yapar.
 */
public record ProfilPaketi(String format, Integer profilSurumu, String ad, String aciklama, String etiket, String uretimZamani, String ureten, Map<String, String> dayanak,
        Map<String, String> parametreler, Map<String, String> siddetler, Map<String, Number> ayarlar, List<OzelKuralGirdisi> ozelKurallar) {

    public static final String FORMAT = "makinist-atama-profil";
    /** Desteklenen en büyük biçim sürümü; büyük sürüm 400 {@code PROFIL_SURUMU}, küçük sürüm önizlemede uyarı. */
    public static final int SURUM = 1;
    /** Hazır ve dışa aktarılan profillerin değişmez uyarı etiketi (OK-44b: yasal doğruluk/uyum iddiası yok). */
    public static final String ETIKET = "Örnek başlangıç — kurum mevzuatıyla doğrulanmalı. Karar destek aracıdır.";

    public ProfilPaketi {
        dayanak = sirali(dayanak);
        parametreler = sirali(parametreler);
        siddetler = sirali(siddetler);
        ayarlar = sirali(ayarlar);
        ozelKurallar = ozelKurallar == null ? List.of() : ozelKurallar.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static <T> Map<String, T> sirali(Map<String, T> m) {
        return m == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    /** Özel kural satırı: {@code kapsam} tanım adlarıyla; sayısal alanlar ham (doğrulama {@code OzelKuralServisi}'nde). */
    public record OzelKuralGirdisi(String ad, String olcu, String pencere, Integer kayanGun, String karsilastirma, BigDecimal esik, KapsamAdlari kapsam, String siddet,
            LocalDate gecerlilikBas, LocalDate gecerlilikBit, String gerekce, String kaynak) {
    }

    /** Kapsam kümeleri tanım ADI olarak ({@code trenTurleri}: YUK/YOLCU, {@code personelTurleri}: MAKINIST/KONDUKTOR, {@code vardiya}: HEPSI/GECE/GUNDUZ). */
    public record KapsamAdlari(List<String> hatlar, List<String> aracTipleri, List<String> kalkisGarlari, List<String> varisGarlari, List<String> trenTurleri, String vardiya,
            List<String> personelTurleri) {

        public KapsamAdlari {
            hatlar = hatlar == null ? List.of() : hatlar;
            aracTipleri = aracTipleri == null ? List.of() : aracTipleri;
            kalkisGarlari = kalkisGarlari == null ? List.of() : kalkisGarlari;
            varisGarlari = varisGarlari == null ? List.of() : varisGarlari;
            trenTurleri = trenTurleri == null ? List.of() : trenTurleri;
            personelTurleri = personelTurleri == null ? List.of() : personelTurleri;
        }
    }

    /** {@code POST /api/profil/uygula} gövdesi: paket + seçilen satır kimlikleri ({@code TUR:anahtar}) + neden. */
    public record UygulaIstegi(ProfilPaketi paket, List<String> secilen, String neden) {
    }
}
