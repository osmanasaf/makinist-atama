package com.makinistatama.kural;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kural parametreleri (DB'den okunan ham degerler) icin tur guvenli erisim. Eksik/bos anahtar veya bozuk
 * deger {@link IllegalArgumentException} (anahtar adi mesajda). K-10 sabit ENGEL'dir (siddet tablosunda yok).
 */
public record Parametreler(Map<String, String> degerler, Map<String, Siddet> siddetler, List<OzelKural> ozelKurallar) {

    public Parametreler {
        degerler = Collections.unmodifiableMap(new HashMap<>(degerler));
        siddetler = Collections.unmodifiableMap(new HashMap<>(siddetler));
        ozelKurallar = ozelKurallar.stream().sorted(java.util.Comparator.comparingLong(OzelKural::id)).toList(); // kimliğe göre sıralı: kod araması ikili arama (MK-17)
    }

    public static Parametreler olustur(Map<String, String> degerler, Map<String, Siddet> siddetler) {
        return new Parametreler(degerler, siddetler, List.of());
    }

    public static Parametreler olustur(Map<String, String> degerler, Map<String, Siddet> siddetler, List<OzelKural> ozelKurallar) {
        return new Parametreler(degerler, siddetler, ozelKurallar);
    }

    // ---- özel kurallar (MK-17): motor ham listeyi gezer, kod araması yalnız ihlal/etiket anında (<= 50 öğe)

    /** {@code Ö-NNN} kodlu kural; yoksa {@link IllegalArgumentException}. Liste kimliğe göre sıralı olduğundan ikili arama (kod biçimlemesi yok). */
    public OzelKural ozelKural(String kod) {
        long id = OzelKural.kimlik(kod);
        int lo = 0;
        int hi = ozelKurallar.size() - 1;
        while (lo <= hi) {
            int orta = (lo + hi) >>> 1;
            OzelKural k = ozelKurallar.get(orta);
            if (k.id() == id) {
                return k;
            }
            if (k.id() < id) {
                lo = orta + 1;
            } else {
                hi = orta - 1;
            }
        }
        throw new IllegalArgumentException("Özel kural yok: " + kod);
    }

    /** Kimliği aynı kural varsa onun YERİNE geçer (önizleme: düzenlenen kuralın taslağı), yoksa sona eklenir. */
    public Parametreler ozelDegistir(OzelKural kural) {
        List<OzelKural> yeni = new ArrayList<>(ozelKurallar);
        yeni.removeIf(k -> k.id() == kural.id());
        yeni.add(kural);
        return new Parametreler(degerler, siddetler, yeni);
    }

    /** Yükleme penceresinin (gün) özel kurallardan gelen alt sınırı (KAPALI olmayanlar); 0 = varsayılan yeter. */
    public int ozelPencereGun() {
        return ozelKurallar.stream().filter(k -> k.siddet() != Siddet.KAPALI).mapToInt(OzelKural::pencereGun).max().orElse(0);
    }

    /** Yıl penceresi olan açık kural var mı (yıl başından itibaren yükleme gerekir; K-15 ile aynı). */
    public boolean ozelYillik() {
        return ozelKurallar.stream().anyMatch(k -> k.siddet() != Siddet.KAPALI && k.pencere() == Pencere.YIL);
    }

    private String ham(String anahtar) {
        String v = degerler.get(anahtar);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Parametre eksik: " + anahtar);
        }
        return v.trim();
    }

    private IllegalArgumentException bozuk(String anahtar, RuntimeException e) {
        return new IllegalArgumentException("Parametre gecersiz: " + anahtar + "=" + degerler.get(anahtar), e);
    }

    /** Saat cinsinden (ondalikli olabilir) parametrenin dakikasi: "7.5" -> 450. */
    public long dakika(String saatAnahtari) {
        return oran(saatAnahtari).multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public long tamsayi(String anahtar) {
        return oran(anahtar).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public BigDecimal oran(String anahtar) {
        try {
            return new BigDecimal(ham(anahtar).replace(',', '.'));
        } catch (NumberFormatException e) {
            throw bozuk(anahtar, e);
        }
    }

    public LocalTime saat(String anahtar) {
        try {
            return LocalTime.parse(ham(anahtar));
        } catch (DateTimeParseException e) {
            throw bozuk(anahtar, e);
        }
    }

    public boolean bayrak(String anahtar) {
        String v = ham(anahtar);
        if (v.equals("1") || v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equals("0") || v.equalsIgnoreCase("false")) {
            return false;
        }
        throw bozuk(anahtar, new IllegalArgumentException(v));
    }

    public Siddet siddet(String kod) {
        if (kod.equals("K-10")) {
            return Siddet.ENGEL;
        }
        if (OzelKural.ozelMi(kod)) {
            return ozelKural(kod).siddet();
        }
        Siddet s = siddetler.get(kod);
        if (s == null) {
            throw new IllegalArgumentException("Kural siddeti eksik: " + kod);
        }
        return s;
    }
}
