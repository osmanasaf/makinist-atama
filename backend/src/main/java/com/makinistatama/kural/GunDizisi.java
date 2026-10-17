package com.makinistatama.kural;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Gün başına çalışılan dakika dizisi + önek toplamı (MK-18): çalışma aralıkları BİR geçişte günlere dağıtılır, gün/hafta/ay/yıl
 * pencere toplamları önek farkıdır (aralık × pencere döngüsü ve aralık başına saat dilimi dönüşümü yok). Gün sınırları İstanbul
 * takvimiyle bir kez hesaplanır. Dizi yalnız aralıkların kapsadığı günleri tutar; dışı 0 sayılır. Gece dakikası ayrı, ilk istekte kurulur.
 */
final class GunDizisi {

    private final List<Aralik> araliklar;
    private final long ilkGun; // epoch day
    private final int n;
    private final Instant[] sinir; // sinir[i] = (ilk + i) gununun baslangici; n + 1 eleman
    private final long[] dk;
    private final long[] onek;
    private long[] geceOnek;
    private long[] gorevliOnek;

    private GunDizisi(List<Aralik> araliklar, long ilkGun, int n, Instant[] sinir, long[] dk) {
        this.araliklar = araliklar;
        this.ilkGun = ilkGun;
        this.n = n;
        this.sinir = sinir;
        this.dk = dk;
        this.onek = onek(dk);
    }

    private static long[] onek(long[] v) {
        long[] o = new long[v.length + 1];
        for (int i = 0; i < v.length; i++) {
            o[i + 1] = o[i] + v[i];
        }
        return o;
    }

    static GunDizisi of(List<Aralik> ar) {
        Instant min = null;
        Instant max = null;
        for (Aralik a : ar) {
            if (!a.bit().isAfter(a.bas())) {
                continue;
            }
            min = min == null || a.bas().isBefore(min) ? a.bas() : min;
            max = max == null || a.bit().isAfter(max) ? a.bit() : max;
        }
        if (min == null) {
            return new GunDizisi(ar, 0, 0, new Instant[0], new long[0]);
        }
        LocalDate ilk = ZamanHesabi.gun(min);
        int n = (int) ChronoUnit.DAYS.between(ilk, ZamanHesabi.gun(max.minusNanos(1))) + 1;
        Instant[] sinir = new Instant[n + 1];
        for (int i = 0; i <= n; i++) {
            sinir[i] = ZamanHesabi.gunBasi(ilk.plusDays(i));
        }
        long[] dk = new long[n];
        for (Aralik a : ar) {
            if (!a.bit().isAfter(a.bas())) {
                continue;
            }
            for (int i = gunIndeksi(sinir, a.bas()); i < n && sinir[i].isBefore(a.bit()); i++) {
                dk[i] += ZamanHesabi.kesisim(a.bas(), a.bit(), sinir[i], sinir[i + 1]);
            }
        }
        return new GunDizisi(ar, ilk.toEpochDay(), n, sinir, dk);
    }

    private static int gunIndeksi(Instant[] sinir, Instant an) {
        int i = Arrays.binarySearch(sinir, an);
        return i >= 0 ? i : -i - 2;
    }

    private int sinirla(LocalDate g) {
        long i = g.toEpochDay() - ilkGun;
        return (int) Math.max(0, Math.min(n, i));
    }

    /** [bas, son) gün aralığındaki toplam çalışma dakikası. */
    long toplam(LocalDate bas, LocalDate son) {
        return fark(onek, bas, son);
    }

    /** [bas, son) gün aralığındaki toplam gece dakikası ({@code gece.baslangic}-{@code gece.bitis} penceresi). */
    long gece(LocalDate bas, LocalDate son, LocalTime gb, LocalTime ge) {
        if (geceOnek == null) {
            geceOnek = onek(geceDakikalari(gb, ge));
        }
        return fark(geceOnek, bas, son);
    }

    /** [bas, son) gün aralığında en az bir çalışma dakikası olan gün sayısı (izin günü = gün sayısı − bu). */
    long gorevliGun(LocalDate bas, LocalDate son) {
        if (gorevliOnek == null) {
            long[] v = new long[n];
            for (int i = 0; i < n; i++) {
                v[i] = dk[i] > 0 ? 1 : 0;
            }
            gorevliOnek = onek(v);
        }
        return fark(gorevliOnek, bas, son);
    }

    /** Gün çalışma dakikası içeriyor mu (dizi dışı günler: hayır). */
    boolean gorevli(LocalDate gun) {
        long i = gun.toEpochDay() - ilkGun;
        return i >= 0 && i < n && dk[(int) i] > 0;
    }

    /** Verilen anların (ör. görev kalkışları) gün başına sayımı: dizinin kapsadığı günlerde önek toplamı; sorgu {@link Sayac#toplam}. */
    Sayac sayac(List<Instant> anlar) {
        long[] v = new long[n];
        for (Instant a : anlar) {
            int i = gunIndeksi(sinir, a);
            if (i >= 0 && i < n) {
                v[i]++;
            }
        }
        return new Sayac(onek(v));
    }

    final class Sayac {
        private final long[] o;

        private Sayac(long[] o) {
            this.o = o;
        }

        /** [bas, son) gün aralığındaki sayı. */
        long toplam(LocalDate bas, LocalDate son) {
            return fark(o, bas, son);
        }
    }

    private long fark(long[] o, LocalDate bas, LocalDate son) {
        int lo = sinirla(bas);
        int hi = sinirla(son);
        return hi > lo ? o[hi] - o[lo] : 0;
    }

    /** Günün gece parçaları gün sınırlarına bölünür (gece yarısını aşan pencere iki güne yazılır); gün sınırı örtüşmesi {@link ZamanHesabi#geceDakika} ile aynı toplamı verir. */
    private long[] geceDakikalari(LocalTime gb, LocalTime ge) {
        long[] g = new long[n];
        if (n == 0 || gb.equals(ge)) {
            return g;
        }
        Instant[] s1 = new Instant[n];
        Instant[] e1 = new Instant[n];
        Instant[] s2 = new Instant[n];
        Instant[] e2 = new Instant[n];
        for (Aralik a : araliklar) {
            if (!a.bit().isAfter(a.bas())) {
                continue;
            }
            for (int i = gunIndeksi(sinir, a.bas()); i < n && sinir[i].isBefore(a.bit()); i++) {
                if (s1[i] == null) {
                    LocalDate d = LocalDate.ofEpochDay(ilkGun + i);
                    if (gb.isAfter(ge)) { // 22:00-06:00: [gun basi, 06:00) ve [22:00, gun sonu)
                        s1[i] = sinir[i];
                        e1[i] = ZamanHesabi.an(d, ge);
                        s2[i] = ZamanHesabi.an(d, gb);
                        e2[i] = sinir[i + 1];
                    } else { // 00:00-05:00 gibi: tek parca
                        s1[i] = ZamanHesabi.an(d, gb);
                        e1[i] = ZamanHesabi.an(d, ge);
                        s2[i] = e2[i] = sinir[i];
                    }
                }
                g[i] += ZamanHesabi.kesisim(a.bas(), a.bit(), s1[i], e1[i]) + ZamanHesabi.kesisim(a.bas(), a.bit(), s2[i], e2[i]);
            }
        }
        return g;
    }
}
