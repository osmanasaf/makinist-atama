package com.makinistatama.kural;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Gorevlerden nobet turetme (MK-2) ve nobet/gorev bazli "calisma araliklari". */
public final class NobetHesabi {

    /** Gorev sirasi: baslangic, esitlikte sefer no. */
    static final Comparator<Gorev> SIRA = Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId);

    private NobetHesabi() {
    }

    /**
     * Baslangica gore sirali gorevlerde, bir onceki nobetin (en gec) bitisi ile sonraki gorevin baslangici
     * arasindaki bosluk {@code ayirmaDk}'den KISA ise ayni nobet; esit veya uzun ise ayri nobet.
     */
    public static List<Nobet> grupla(List<Gorev> gorevler, long ayirmaDk) {
        List<Gorev> sirali = new ArrayList<>(gorevler);
        sirali.sort(SIRA);
        return gruplaSirali(sirali, ayirmaDk);
    }

    /** {@link #SIRA}'ya gore ZATEN sirali gorevlerden nobetler (onhesap siralamayi bir kez yapar). */
    static List<Nobet> gruplaSirali(List<Gorev> sirali, long ayirmaDk) {
        Duration ayirma = Duration.ofMinutes(ayirmaDk);
        List<Nobet> sonuc = new ArrayList<>();
        List<Gorev> grup = new ArrayList<>();
        Instant bit = null;
        for (Gorev g : sirali) {
            if (bit != null && Duration.between(bit, g.baslangic()).compareTo(ayirma) >= 0) {
                sonuc.add(nobet(grup));
                grup = new ArrayList<>();
                bit = null;
            }
            grup.add(g);
            bit = bit == null || g.bitis().isAfter(bit) ? g.bitis() : bit;
        }
        if (!grup.isEmpty()) {
            sonuc.add(nobet(grup));
        }
        return sonuc;
    }

    private static Nobet nobet(List<Gorev> grup) {
        Gorev son = grup.get(0);
        for (Gorev g : grup) {
            if (!g.bitis().isBefore(son.bitis())) {
                son = g;
            }
        }
        return new Nobet(grup.get(0).baslangic(), son.bitis(), grup.get(0).kalkisGarId(), son.varisGarId(), List.copyOf(grup));
    }

    /**
     * MK-5 (kirmizi atama = kronolojik sorumluluk): adayin SORUMLU oldugu komsular. Adaydan ONCE baslayanlar ve adayla AYNI NOBETTE olan sonrakiler;
     * nobet birlesince gece/gunduz sinifi ve toplamlar degisir, bu yuzden o sonrakiler dahildir (BUG-14). Nobeti ayiran (arasi {@code ayirmaDk} ve ustu)
     * sonraki gorevler sorumlu sayilmaz: zinciri bozan sonraki atamadir.
     */
    public static List<Gorev> sorumluKomsular(List<Gorev> digerler, Gorev aday, long ayirmaDk) {
        List<Gorev> tum = new ArrayList<>(digerler);
        tum.add(aday);
        Nobet nobet = nobetiBul(grupla(tum, ayirmaDk), aday);
        return digerler.stream().filter(g -> SIRA.compare(g, aday) < 0 || nobet.gorevler().contains(g)).toList();
    }

    /** Gorevi (referans esitligiyle) iceren nobet. */
    public static Nobet nobetiBul(List<Nobet> nobetler, Gorev g) {
        for (Nobet n : nobetler) {
            for (Gorev x : n.gorevler()) {
                if (x == g) {
                    return n;
                }
            }
        }
        throw new IllegalStateException("Gorev hicbir nobette yok: sefer " + g.seferId());
    }

    /**
     * {@code beklemeSayilir=false}: nobetin gorev araliklari; {@code true}: tek nobet araligi (bekleme dahil). F2b (MK-39): {@code sureSayilir=false} yolculuk gorevi
     * calisma araligina GIRMEZ (nobet zinciri, K-10, K-11/K-12 ve K-20 onu yine gorur: {@link Nobet} alanlari degismez); bekleme sayilirken aralik yalniz SAYILAN
     * gorevlerin [en erken baslangic, en gec bitis]'idir, hic sayilan yoksa aralik eklenmez.
     */
    public static List<Aralik> araliklar(Nobet n, boolean beklemeSayilir) {
        List<Aralik> l = new ArrayList<>(beklemeSayilir ? 1 : n.gorevler().size());
        araliklarEkle(l, n, beklemeSayilir);
        return l;
    }

    private static void araliklarEkle(List<Aralik> l, Nobet n, boolean beklemeSayilir) {
        if (beklemeSayilir) {
            Instant bas = null;
            Instant bit = null;
            boolean hepsiSayilir = true;
            for (Gorev g : n.gorevler()) {
                if (!g.sureSayilir()) {
                    hepsiSayilir = false;
                    continue;
                }
                bas = bas == null || g.baslangic().isBefore(bas) ? g.baslangic() : bas;
                bit = bit == null || g.bitis().isAfter(bit) ? g.bitis() : bit;
            }
            if (hepsiSayilir) {
                l.add(new Aralik(n.baslangic(), n.bitis()));
            } else if (bas != null) {
                l.add(new Aralik(bas, bit));
            }
            return;
        }
        for (Gorev g : n.gorevler()) {
            if (g.sureSayilir()) {
                l.add(new Aralik(g.baslangic(), g.bitis()));
            }
        }
    }

    /** K-13/14/15/17/19 ve gece payi icin calisma araliklari ({@code nobet.bekleme_sayilir}). */
    public static List<Aralik> calismaAraliklari(List<Gorev> gorevler, long ayirmaDk, boolean beklemeSayilir) {
        return araliklar(grupla(gorevler, ayirmaDk), beklemeSayilir);
    }

    /** Nobet listesinin calisma araliklari (onhesap: gruplama bir kez yapilir, bkz. {@link Onhesap}). */
    static List<Aralik> araliklar(List<Nobet> nobetler, boolean beklemeSayilir) {
        List<Aralik> l = new ArrayList<>();
        for (Nobet n : nobetler) {
            araliklarEkle(l, n, beklemeSayilir);
        }
        return l;
    }
}
