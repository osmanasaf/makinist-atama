package com.makinistatama.rapor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.makinistatama.kural.ZamanHesabi;

/**
 * Puantaj ön hesabı (FR-12, T14.1): SAF (Spring/JPA/IO yok), DB'siz. Bir makinistin bir ayı için görev listesinden fiili çalışma, ücretlendirilen süre,
 * fazla mesai, gece süresi ve km'yi hesaplar; TL satırları yalnız ilgili oran doluysa üretilir.
 * <ul>
 * <li>Fiili süre: çalışma aralığının ({@code kural.Gorev.calismaAraligi}: planlanan kalkıştan güncel varışa, hazırlık/alıkoyma dahil; kural, adalet ve puantajın tek
 * tanımı) ay ile kesişimi; gece yarısından bölünür (OK-3). İptal seferler listeye girmez.</li>
 * <li>Çalışılan gün: planlanan günü ay içinde olan vardiyaların günleri (gece yarısını aşan vardiya tek gündür). Ücretlendirilen =
 * {@code max(fiili, çalışılan gün × asgari günlük süre)}; asgari güvence vardiyanın planlanan günün ayına yazılır (ay sınırında aşan kısım güvenceye girmez).</li>
 * <li>Fazla mesai: takvim haftası (Pzt-Paz) toplamı − {@code haftalik.normal_saat}; hafta, <b>Pazar günü hangi aydaysa o aya</b> yazılır (ay sonu haftası Pazar sonraki
 * aydaysa fazla mesai sonraki aya). Bu yüzden görev listesi ayın ilk haftasının Pazartesi'sinden başlamalıdır.</li>
 * <li>Gece süresi: {@code gece.baslangic}-{@code puantaj.gece_bitis} penceresi (kural penceresinden ayrı).</li>
 * <li>Km: planlanan günü ay içinde olan seferlerin km toplamı; km'si boş seferler sayılır ({@code kmBilinmeyenSefer}), toplama girmez.</li>
 * </ul>
 */
public final class PuantajHesabi {

    /** Bir görev: planlanan gün, çalışma aralığı [bas, bit) ({@link com.makinistatama.kural.Gorev#calismaAraligi}: tek kaynak), km (boş olabilir). */
    public record Gorev(LocalDate tarih, Instant bas, Instant bit, BigDecimal km) {
    }

    /** Parametreler; {@code saatUcreti} ve {@code kmBirimTutari} boş (null) olabilir. */
    public record Oranlar(long normalHaftaDakika, LocalTime geceBaslangic, LocalTime geceBitis, BigDecimal geceOrani, long asgariGunDakika, BigDecimal saatUcreti,
            BigDecimal fazlaMesaiCarpani, BigDecimal kmBirimTutari) {
    }

    public record Sonuc(long fiiliDakika, int calisilanGun, long ucretlendirilenDakika, long fazlaMesaiDakika, long geceDakika, BigDecimal km, int kmBilinmeyenSefer,
            BigDecimal fazlaMesaiTl, BigDecimal geceTl, BigDecimal kmTl) {
    }

    private PuantajHesabi() {
    }

    public static Sonuc hesapla(YearMonth ay, List<Gorev> gorevler, Oranlar o) {
        Instant ayBas = ZamanHesabi.gunBasi(ay.atDay(1));
        Instant ayBit = ZamanHesabi.gunBasi(ay.atEndOfMonth().plusDays(1));
        long fiili = 0;
        long gece = 0;
        BigDecimal km = BigDecimal.ZERO;
        int kmYok = 0;
        Set<LocalDate> gunler = new HashSet<>();
        for (Gorev g : gorevler) {
            Instant bas = g.bas().isAfter(ayBas) ? g.bas() : ayBas;
            Instant bit = g.bit().isBefore(ayBit) ? g.bit() : ayBit;
            boolean ayIci = YearMonth.from(g.tarih()).equals(ay);
            if (bit.isAfter(bas)) {
                fiili += java.time.Duration.between(bas, bit).toMinutes();
                gece += ZamanHesabi.geceDakika(bas, bit, o.geceBaslangic(), o.geceBitis());
                if (ayIci) {
                    gunler.add(g.tarih()); // W2: vardiya tek gündür (planlanan gün); gece yarısını aşan vardiya iki gün sayılmaz
                }
            }
            if (ayIci) {
                if (g.km() == null) {
                    kmYok++;
                } else {
                    km = km.add(g.km());
                }
            }
        }
        long fazla = 0;
        for (LocalDate pazar = ay.atDay(1).with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)); !pazar.isAfter(ay.atEndOfMonth()); pazar = pazar.plusWeeks(1)) {
            LocalDate pazartesi = pazar.minusDays(6);
            long hafta = 0;
            for (Gorev g : gorevler) {
                hafta += ZamanHesabi.haftaDakika(new ZamanHesabi.Aralik(g.bas(), g.bit()), pazartesi);
            }
            fazla += Math.max(0, hafta - o.normalHaftaDakika());
        }
        long ucretlendirilen = Math.max(fiili, gunler.size() * o.asgariGunDakika());
        BigDecimal fazlaTl = o.saatUcreti() == null || o.fazlaMesaiCarpani() == null ? null : tl(fazla, o.saatUcreti(), o.fazlaMesaiCarpani());
        BigDecimal geceTl = o.saatUcreti() == null || o.geceOrani() == null ? null : tl(gece, o.saatUcreti(), o.geceOrani());
        BigDecimal kmTl = o.kmBirimTutari() == null ? null : km.multiply(o.kmBirimTutari()).setScale(2, RoundingMode.HALF_UP);
        return new Sonuc(fiili, gunler.size(), ucretlendirilen, fazla, gece, km, kmYok, fazlaTl, geceTl, kmTl);
    }

    /** W1: ara yuvarlama yok; {@code dakika × ücret × oran / 60} tek ifadede, bölme en sonda, HALF_UP 2 basamak. */
    private static BigDecimal tl(long dakika, BigDecimal saatUcreti, BigDecimal oran) {
        return BigDecimal.valueOf(dakika).multiply(saatUcreti).multiply(oran).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
