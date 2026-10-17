package com.makinistatama.kural;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kural testleri icin kisa kurucular. Varsayilan makinist HER kurala uygundur (belgeler 2030'a kadar). */
final class TestVerisi {

    static final long ANKARA = 1;
    static final long ESKISEHIR = 2;
    static final long HAT = 10;
    static final long ARAC = 20;
    static final Map<Long, String> ADLAR = Map.of(ANKARA, "Ankara", ESKISEHIR, "Eskişehir",
            HAT, "Ankara–Eskişehir", ARAC, "YHT");

    private TestVerisi() {
    }

    /** "2026-10-12T08:00" -> Istanbul yerel saatinden Instant. */
    static Instant an(String yerel) {
        return LocalDateTime.parse(yerel).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    static Gorev gorev(long id, String bas, String bit) {
        return gorev(id, ANKARA, ANKARA, bas, bit);
    }

    static Gorev gorev(long id, long kalkisGar, long varisGar, String bas, String bit) {
        Instant b = an(bas);
        Instant e = an(bit);
        return new Gorev(id, kalkisGar, varisGar, HAT, ARAC, b, e, b, e, 100);
    }

    static Parametreler param(String... anahtarDeger) {
        return param(Map.of(), anahtarDeger);
    }

    static Parametreler param(Map<String, Siddet> siddet, String... anahtarDeger) {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        for (int i = 0; i < anahtarDeger.length; i += 2) {
            d.put(anahtarDeger[i], anahtarDeger[i + 1]);
        }
        Map<String, Siddet> s = new HashMap<>(VarsayilanParametreler.SIDDETLER);
        s.putAll(siddet);
        return Parametreler.olustur(d, s);
    }

    /** Makinist yapicisi: varsayilanlar gecerli. */
    static final class M {
        long id = 1;
        long bagliGar = ANKARA;
        MakinistBilgisi.Durum durum = MakinistBilgisi.Durum.AKTIF;
        LocalDate ehliyet = LocalDate.of(2030, 1, 1);
        LocalDate saglik = LocalDate.of(2030, 1, 1);
        LocalDate psikoteknik = LocalDate.of(2030, 1, 1);
        Map<Long, LocalDate> arac = new HashMap<>(Map.of(ARAC, LocalDate.of(2024, 1, 1)));
        Map<Long, MakinistBilgisi.HatBrove> hat = new HashMap<>(
                Map.of(HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1))));
        LocalDate sonSurus = LocalDate.of(2026, 9, 1);
        List<MakinistBilgisi.Izin> izin = new ArrayList<>();

        MakinistBilgisi yap() {
            return new MakinistBilgisi(id, bagliGar, durum, ehliyet, saglik, psikoteknik, arac, hat, sonSurus, izin);
        }
    }

    static MakinistBilgisi makinist() {
        return new M().yap();
    }

    static KuralBaglami baglam(Gorev aday, List<Gorev> diger) {
        return baglam(aday, diger, makinist(), param());
    }

    static KuralBaglami baglam(Gorev aday, List<Gorev> diger, Parametreler p) {
        return baglam(aday, diger, makinist(), p);
    }

    static KuralBaglami baglam(Gorev aday, List<Gorev> diger, MakinistBilgisi m, Parametreler p) {
        return new KuralBaglami(aday, diger, m, Map.of(), null, ADLAR, p);
    }

    static List<String> kodlar(List<Ihlal> ihlaller) {
        return ihlaller.stream().map(Ihlal::kod).toList();
    }

    static List<Ihlal> kod(List<Ihlal> ihlaller, String kod) {
        return ihlaller.stream().filter(i -> i.kod().equals(kod)).toList();
    }

    static List<Ihlal> degerlendir(Gorev aday, List<Gorev> diger, Parametreler p) {
        return KuralMotoru.degerlendir(baglam(aday, diger, p));
    }

    static List<Ihlal> degerlendir(Gorev aday, List<Gorev> diger) {
        return degerlendir(aday, diger, param());
    }
}
