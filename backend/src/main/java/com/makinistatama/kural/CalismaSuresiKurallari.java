package com.makinistatama.kural;

import static com.makinistatama.kural.ZamanHesabi.sure;
import static com.makinistatama.kural.ZamanHesabi.tarih;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Calisma suresi kurallari: K-13 gunluk, K-14 haftalik, K-15 yillik fazla mesai, K-19 haftalik dinlenme,
 * K-21 tek sefer. Gece kurallari (K-16, K-17, K-18) {@link GeceKurallari}'nda. Toplamlar
 * {@link Onhesap} (nobetlerden) uzerindendir ({@code nobet.bekleme_sayilir}); yalniz adayin dokundugu
 * gun/haftalar degerlendirilir.
 */
public final class CalismaSuresiKurallari {

    private CalismaSuresiKurallari() {
    }

    static List<Ihlal> kontrol(KuralBaglami b, Onhesap h) {
        List<Ihlal> o = new ArrayList<>();
        gunluk(o, b, h);
        haftalik(o, b, h);
        yillik(o, b, h);
        haftaTatili(o, b, h);
        tekSefer(o, b);
        return o;
    }

    // ---------- ortak yardimcilar ----------

    static long gunToplam(List<Aralik> ar, LocalDate gun) {
        return toplam(ar, ZamanHesabi.gunBasi(gun), ZamanHesabi.gunBasi(gun.plusDays(1)));
    }

    static long haftaToplam(List<Aralik> ar, LocalDate haftaBasi) {
        return toplam(ar, ZamanHesabi.gunBasi(haftaBasi), ZamanHesabi.gunBasi(haftaBasi.plusDays(7)));
    }

    /** [bas, son) ile kesisim toplami; sinirlar bir kez hesaplanir (her aralik icin saat dilimi donusumu pahali). */
    private static long toplam(List<Aralik> ar, Instant bas, Instant son) {
        long t = 0;
        for (Aralik a : ar) {
            t += ZamanHesabi.kesisim(a.bas(), a.bit(), bas, son);
        }
        return t;
    }

    /** Haftalik toplamin {@code haftalik.normal_saat} ustu (fazla mesai), dakika. */
    public static long fazlaMesaiDakika(long haftaDk, Parametreler p) {
        return Math.max(0, haftaDk - p.dakika("haftalik.normal_saat"));
    }

    static String hafta(LocalDate haftaBasi) {
        return tarih(haftaBasi) + " haftası";
    }

    // ---------- K-13 / K-14 / K-15 ----------

    private static void gunluk(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        long max = b.parametreler().dakika("gunluk.max_saat");
        for (LocalDate gun : ZamanHesabi.gunler(h.alan().bas(), h.alan().bit())) {
            long dk = gunToplam(h.araliklar(), gun);
            if (dk > max) {
                Ihlal.ekle(o, b.parametreler(), "K-13", dk / 60.0, max / 60.0, "s",
                        "Günlük çalışma " + tarih(gun) + ": " + sure(dk) + " > " + sure(max));
            }
        }
    }

    private static void haftalik(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        long max = b.parametreler().dakika("haftalik.max_saat");
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            long dk = haftaToplam(h.araliklar(), hafta);
            if (dk > max) {
                Ihlal.ekle(o, b.parametreler(), "K-14", dk / 60.0, max / 60.0, "s",
                        "Haftalık çalışma (" + hafta(hafta) + "): " + sure(dk) + " > " + sure(max));
            }
        }
    }

    /**
     * K-15: aday, adayin haftasinin fazla mesaisini ARTIRIYORSA ve o yilin (haftanin Pazar gunune gore) toplam
     * fazla mesaisi {@code yillik.fazla_mesai_max_saat}'i asiyorsa ihlal. Toplam yalniz baglamdaki gorevlerden hesaplanir.
     */
    private static void yillik(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        Parametreler p = b.parametreler();
        if (p.siddet("K-15") == Siddet.KAPALI) {
            return;
        }
        long max = p.dakika("yillik.fazla_mesai_max_saat");
        Map<LocalDate, Long> haftaDk = null;
        TreeSet<Integer> bildirilen = new TreeSet<>();
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            long artis = fazlaMesaiDakika(haftaToplam(h.araliklar(), hafta), p)
                    - fazlaMesaiDakika(haftaToplam(h.adaysiz(), hafta), p);
            int yil = hafta.plusDays(6).getYear();
            if (artis <= 0 || bildirilen.contains(yil)) {
                continue;
            }
            if (haftaDk == null) {
                haftaDk = haftaToplamlari(h.araliklar()); // yalniz fazla mesai artisi varsa, tek gecista
            }
            long toplam = 0;
            for (Map.Entry<LocalDate, Long> w : haftaDk.entrySet()) {
                if (w.getKey().plusDays(6).getYear() == yil) {
                    toplam += fazlaMesaiDakika(w.getValue(), p);
                }
            }
            if (toplam > max) {
                bildirilen.add(yil);
                Ihlal.ekle(o, p, "K-15", toplam / 60.0, max / 60.0, "s",
                        yil + " yılı fazla mesai " + sure(toplam) + " > " + sure(max));
            }
        }
    }

    private static Map<LocalDate, Long> haftaToplamlari(List<Aralik> araliklar) {
        Map<LocalDate, Long> haftaDk = new HashMap<>();
        for (Aralik a : araliklar) {
            for (LocalDate w : ZamanHesabi.haftalar(a.bas(), a.bit())) {
                haftaDk.merge(w, ZamanHesabi.haftaDakika(a, w), Long::sum);
            }
        }
        return haftaDk;
    }

    // ---------- K-19 haftalik dinlenme ----------

    private static void haftaTatili(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        long min = b.parametreler().dakika("hafta_tatili.min_saat");
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            long enUzun = enUzunBosluk(h.araliklar(), hafta);
            if (enUzun < min) {
                Ihlal.ekle(o, b.parametreler(), "K-19", enUzun / 60.0, min / 60.0, "s",
                        "Haftalık dinlenme (" + hafta(hafta) + "): en uzun kesintisiz boşluk " + sure(enUzun) + " < " + sure(min));
            }
        }
    }

    /** Haftaya kirpilmis calisma araliklari arasindaki (kenarlar dahil) en uzun bosluk, dakika. */
    static long enUzunBosluk(List<Aralik> ar, LocalDate hafta) {
        return enUzunBosluk(ar, ZamanHesabi.gunBasi(hafta), ZamanHesabi.gunBasi(hafta.plusDays(7)));
    }

    /** [bas, son) aralığına kırpılmış çalışma aralıkları arasındaki (kenarlar dahil) en uzun boşluk, dakika (özel kural: görevsiz aralık). */
    static long enUzunBosluk(List<Aralik> ar, Instant bas, Instant son) {
        List<Aralik> sirali = new ArrayList<>();
        for (Aralik a : ar) {
            Instant s = a.bas().isBefore(bas) ? bas : a.bas();
            Instant e = a.bit().isAfter(son) ? son : a.bit();
            if (e.isAfter(s)) {
                sirali.add(new Aralik(s, e));
            }
        }
        sirali.sort(Comparator.comparing(Aralik::bas));
        long en = 0;
        Instant imlec = bas;
        for (Aralik a : sirali) {
            if (a.bas().isAfter(imlec)) {
                en = Math.max(en, Duration.between(imlec, a.bas()).toMinutes());
            }
            if (a.bit().isAfter(imlec)) {
                imlec = a.bit();
            }
        }
        return Math.max(en, Duration.between(imlec, son).toMinutes());
    }

    // ---------- K-21 ----------

    private static void tekSefer(List<Ihlal> o, KuralBaglami b) {
        if (b.aday().yolculuk()) {
            return; // yolculuk sürüş değil: tek sefer süresi sınırı uygulanmaz (MK-40)
        }
        Parametreler p = b.parametreler();
        long max = p.dakika("sefer.max_saat");
        long dk = Duration.between(b.aday().kalkis(), b.aday().varis()).toMinutes();
        if (dk > max) {
            Ihlal.ekle(o, p, "K-21", dk / 60.0, max / 60.0, "s", "Tek sefer süresi " + sure(dk) + " > " + sure(max));
        }
    }
}
