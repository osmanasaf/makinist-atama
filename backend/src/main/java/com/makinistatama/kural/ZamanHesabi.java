package com.makinistatama.kural;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Zaman icin tek kapi (MK-3): sabit Europe/Istanbul takvimi, yari-acik [bas, bit) araliklar, dakika (long).
 * Gun/hafta bolme {@code atStartOfDay(ISTANBUL)} ile yapilir (Instant'a 24 saat eklenmez); hafta Pazartesi baslar.
 */
public final class ZamanHesabi {

    public static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private static final String[] AYLAR = {"Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"};
    private static final String[] AY_ADLARI = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
    // Turkce bulunma eki (-de/-da/-te/-ta): son okunan sayinin son sozcugune gore
    private static final String[] BIRLER_EK = {"", "de", "de", "te", "te", "te", "da", "de", "de", "da"};
    private static final String[] ONLAR_EK = {"da", "da", "de", "da", "ta", "de"}; // sifir, on, yirmi, otuz, kirk, elli

    /** Yari-acik zaman araligi [bas, bit). */
    public record Aralik(Instant bas, Instant bit) {
        public long dakika() {
            return Duration.between(bas, bit).toMinutes();
        }
    }

    private ZamanHesabi() {
    }

    // ---------- gun / hafta ----------

    public static LocalDate gun(Instant t) {
        return t.atZone(ISTANBUL).toLocalDate();
    }

    /**
     * Bir günün kural bağlamı penceresinin geri sınırı: {@code gün - pencereGun - 1} (bir önceki güne uzanan çalışma aralığı için); K-15 (yıllık fazla mesai) açıksa en geç yıl başından
     * (Pazar-yılı kuralı için −7 gün) başlar. Tek tanım: BaglamYukleyici ve otomatik atama aynı pencereyi kullanır.
     */
    public static LocalDate pencereBasi(LocalDate gun, int pencereGun, boolean yillik) {
        LocalDate bas = gun.minusDays(pencereGun + 1L); // +1: çalışma aralığı (hazırlık, nöbet birleşmesi) sefer gününden önceki güne uzanabilir ({@link #pencereSonu} ile simetrik)
        if (yillik) {
            LocalDate yilBasi = LocalDate.of(gun.minusDays(2).getYear(), 1, 1).minusDays(7); // gün - 2: gece yarısı öncesi başlayan nöbet önceki yıla düşer
            bas = bas.isBefore(yilBasi) ? bas : yilBasi;
        }
        return bas;
    }

    /**
     * Bir günün kural bağlamı penceresinin ileri sınırı (dahil): {@code gün + pencereGun}; yıl penceresi olan açık özel kural varsa ({@code yillikOzel})
     * en geç yıl sonuna kadar uzar. Yıl toplamı ileride atanmış görevleri de içerdiğinden, kabul edilen bir atamanın ileri tarihteki mevcut görevi
     * kırmızı yapmaması (MK-5) için ileri yükleme de yıl sonuna kadar olmalıdır. Gece yarısını aşan görev ertesi yıla taşar (31 Ara 22:00 – 1 Oca 04:00; nöbet
     * birleşmesiyle 2 güne kadar): yıl sonu, {@code gün + 2}'nin yılının sonudur. Tek tanım: BaglamYukleyici ve otomatik atama.
     */
    public static LocalDate pencereSonu(LocalDate gun, int pencereGun, boolean yillikOzel) {
        LocalDate son = gun.plusDays(pencereGun);
        if (yillikOzel) {
            LocalDate yilSonu = LocalDate.of(gun.plusDays(2).getYear(), 12, 31);
            son = son.isAfter(yilSonu) ? son : yilSonu;
        }
        return son;
    }

    public static Instant gunBasi(LocalDate g) {
        return g.atStartOfDay(ISTANBUL).toInstant();
    }

    public static Instant an(LocalDate g, LocalTime s) {
        return ZonedDateTime.of(g, s, ISTANBUL).toInstant();
    }

    public static LocalDate haftaBasi(LocalDate g) {
        return g.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Iki ucu dahil gunler (izin, belge gunu). */
    public static List<LocalDate> gunler(LocalDate bas, LocalDate bit) {
        List<LocalDate> l = new ArrayList<>();
        for (LocalDate d = bas; !d.isAfter(bit); d = d.plusDays(1)) {
            l.add(d);
        }
        return l;
    }

    /** [bas, bit) araliginin dokundugu gunler; 00:00'da biten aralik ertesi gune dokunmaz. */
    public static List<LocalDate> gunler(Instant bas, Instant bit) {
        return gunler(gun(bas), bit.isAfter(bas) ? gun(bit.minusNanos(1)) : gun(bas));
    }

    /** [bas, bit) araliginin dokundugu haftalarin Pazartesi gunleri. */
    public static List<LocalDate> haftalar(Instant bas, Instant bit) {
        List<LocalDate> l = new ArrayList<>();
        List<LocalDate> g = gunler(bas, bit);
        for (LocalDate h = haftaBasi(g.get(0)); !h.isAfter(g.get(g.size() - 1)); h = h.plusWeeks(1)) {
            l.add(h);
        }
        return l;
    }

    /** Takvim ayının ilk günü. */
    public static LocalDate ayBasi(LocalDate g) {
        return g.withDayOfMonth(1);
    }

    /** [bas, bit) araliginin dokundugu takvim aylarinin ilk gunleri. */
    public static List<LocalDate> aylar(Instant bas, Instant bit) {
        List<LocalDate> g = gunler(bas, bit);
        List<LocalDate> l = new ArrayList<>();
        for (LocalDate a = ayBasi(g.get(0)); !a.isAfter(g.get(g.size() - 1)); a = a.plusMonths(1)) {
            l.add(a);
        }
        return l;
    }

    // ---------- dakika hesaplari ----------

    /** Iki yari-acik aralik kesisimi, dakika (>= 0). */
    public static long kesisim(Instant a1, Instant a2, Instant b1, Instant b2) {
        Instant s = a1.isAfter(b1) ? a1 : b1;
        Instant e = a2.isBefore(b2) ? a2 : b2;
        return e.isAfter(s) ? Duration.between(s, e).toMinutes() : 0;
    }

    public static long gunDakika(Aralik a, LocalDate gun) {
        return kesisim(a.bas(), a.bit(), gunBasi(gun), gunBasi(gun.plusDays(1)));
    }

    public static long haftaDakika(Aralik a, LocalDate haftaBasi) {
        return kesisim(a.bas(), a.bit(), gunBasi(haftaBasi), gunBasi(haftaBasi.plusDays(7)));
    }

    /**
     * Aralikla gece penceresinin kesisimi (dakika). {@code gb > ge} ise pencere gece yarisini asar: her gun d icin
     * [d-1 gb, d ge) ve [d gb, d+1 ge) parcalari hesaba girer; {@code gb < ge} ise [d gb, d ge).
     */
    public static long geceDakika(Instant bas, Instant bit, LocalTime gb, LocalTime ge) {
        if (!bit.isAfter(bas) || gb.equals(ge)) {
            return 0;
        }
        LocalDate ilk = gun(bas);
        LocalDate son = gun(bit.minusNanos(1));
        long toplam = 0;
        if (gb.isAfter(ge)) {
            for (LocalDate d = ilk.minusDays(1); !d.isAfter(son); d = d.plusDays(1)) {
                toplam += kesisim(bas, bit, an(d, gb), an(d.plusDays(1), ge));
            }
        } else {
            for (LocalDate d = ilk; !d.isAfter(son); d = d.plusDays(1)) {
                toplam += kesisim(bas, bit, an(d, gb), an(d, ge));
            }
        }
        return toplam;
    }

    // ---------- Turkce bicim ----------

    /** Dakika -> "8 s", "9 s 30 dk", "10 s 59 dk", "30 dk" (tek biçim: saat ondalık gösterilmez). */
    public static String sure(long dk) {
        if (dk < 0) {
            return "-" + sure(-dk);
        }
        long sa = dk / 60;
        long r = dk % 60;
        if (sa == 0) {
            return r + " dk";
        }
        return r == 0 ? sa + " s" : sa + " s " + r + " dk";
    }

    /** "12 Eki 2026". */
    public static String tarih(LocalDate d) {
        return d.getDayOfMonth() + " " + AYLAR[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** "Ekim 2026" (ayın ilk günü ya da herhangi bir günü). */
    public static String ay(LocalDate d) {
        return AY_ADLARI[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** "12 Eki 22:00". */
    public static String an(Instant t) {
        ZonedDateTime z = t.atZone(ISTANBUL);
        return String.format("%d %s %02d:%02d", z.getDayOfMonth(), AYLAR[z.getMonthValue() - 1], z.getHour(), z.getMinute());
    }

    /** "12 Eki 22:00'de" / "13 Eki 06:00'da" (bulunma eki, okunusa gore). */
    public static String deEk(Instant t) {
        ZonedDateTime z = t.atZone(ISTANBUL);
        int n = z.getMinute() != 0 ? z.getMinute() : z.getHour();
        String ek = n % 10 != 0 ? BIRLER_EK[n % 10] : ONLAR_EK[n / 10];
        return an(t) + "'" + ek;
    }
}
