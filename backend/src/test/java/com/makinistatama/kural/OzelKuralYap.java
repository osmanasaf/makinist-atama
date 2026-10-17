package com.makinistatama.kural;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Özel kural testleri için kısa kurucular. */
final class OzelKuralYap {

    static final LocalDate EVVEL = LocalDate.of(2000, 1, 1);

    private OzelKuralYap() {
    }

    static OzelKural kural(long id, String ad, Olcu olcu, Pencere pencere, String esik, Kapsam kapsam, Siddet siddet) {
        return new OzelKural(id, ad, olcu, pencere, Karsilastirma.EN_FAZLA, new BigDecimal(esik), kapsam, siddet, EVVEL, null, "gerekçe", "kaynak", 0);
    }

    /** K2: karşılaştırma ve kayan gün verilebilir (şiddet ENGEL). */
    static OzelKural kural(long id, String ad, Olcu olcu, Pencere pencere, Karsilastirma kars, String esik, Kapsam kapsam, Integer kayanGun) {
        return new OzelKural(id, ad, olcu, pencere, kayanGun, kars, new BigDecimal(esik), kapsam, Siddet.ENGEL, EVVEL, null, "gerekçe", "kaynak", 0);
    }

    static OzelKural kural(long id, String ad, Olcu olcu, Pencere pencere, String esik) {
        return kural(id, ad, olcu, pencere, esik, Kapsam.TUMU, Siddet.ENGEL);
    }

    static Kapsam hat(long... hatlar) {
        Set<Long> s = new java.util.HashSet<>();
        for (long h : hatlar) {
            s.add(h);
        }
        return new Kapsam(s, null, null, null, null, null);
    }

    static Kapsam vardiya(Vardiya v) {
        return new Kapsam(null, null, null, null, null, v);
    }

    static Parametreler p(OzelKural... kurallar) {
        return p(Map.of(), kurallar);
    }

    static Parametreler p(Map<String, Siddet> siddet, OzelKural... kurallar) {
        Parametreler t = TestVerisi.param(siddet);
        List<OzelKural> l = new ArrayList<>(List.of(kurallar));
        return Parametreler.olustur(t.degerler(), t.siddetler(), l);
    }

    static Parametreler p(Map<String, Siddet> siddet, List<OzelKural> kurallar, String... anahtarDeger) {
        Parametreler t = TestVerisi.param(siddet, anahtarDeger);
        return Parametreler.olustur(t.degerler(), t.siddetler(), kurallar);
    }

    /** Hat kimliği verilebilen görev (TestVerisi.gorev hep HAT kullanır). */
    static Gorev gorevHat(long id, long hat, String bas, String bit) {
        var b = TestVerisi.an(bas);
        var e = TestVerisi.an(bit);
        return new Gorev(id, TestVerisi.ANKARA, TestVerisi.ANKARA, hat, TestVerisi.ARAC, b, e, b, e, 100);
    }
}
