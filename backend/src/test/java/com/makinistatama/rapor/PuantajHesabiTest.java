package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.rapor.PuantajHesabi.Gorev;
import com.makinistatama.rapor.PuantajHesabi.Oranlar;
import com.makinistatama.rapor.PuantajHesabi.Sonuc;

/**
 * T14.1: saf puantaj hesabı (DB'siz). Plan örnekleri: hafta 50 s -> fazla mesai 5 s × 200 × 1,5 = 1.500 TL; gece 7 s × 200 × 0,15 = 210 TL; 1.250 km × 0,40 = 500 TL;
 * 3 gün 4+4+7 = 15 s -> ücretlendirilen 22,5 s; 08:00-12:00 + 90 dk varış gecikmesi -> 5,5 s; iptal 0 s; ay sonu haftası Pazar'ı sonraki aydaysa fazla mesai sonraki aya.
 */
class PuantajHesabiTest {

    static final YearMonth EKIM = YearMonth.of(2026, 10);
    static final Oranlar DOLU = new Oranlar(45 * 60, LocalTime.of(20, 0), LocalTime.of(7, 0), new BigDecimal("0.15"), 450, new BigDecimal("200"), new BigDecimal("1.5"),
            new BigDecimal("0.40"));
    static final Oranlar BOS = new Oranlar(45 * 60, LocalTime.of(20, 0), LocalTime.of(7, 0), new BigDecimal("0.15"), 450, null, new BigDecimal("1.5"), null);

    static Instant an(String yerel) {
        return LocalDateTime.parse(yerel).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    static Gorev gorev(String kalkis, String varis, Double km) {
        return new Gorev(LocalDate.parse(kalkis.substring(0, 10)), an(kalkis), an(varis), km == null ? null : BigDecimal.valueOf(km));
    }

    /** 5 gün, günde 10 saat (06:00-16:00), 250 km: hafta Pzt {@code pazartesi}. */
    static List<Gorev> hafta(String pazartesi) {
        List<Gorev> l = new ArrayList<>();
        LocalDate p = LocalDate.parse(pazartesi);
        for (int i = 0; i < 5; i++) {
            l.add(gorev(p.plusDays(i) + "T06:00", p.plusDays(i) + "T16:00", 250.0));
        }
        return l;
    }

    @Test
    void hafta50Saat_fazlaMesai5Saat_1500TL_km1250_500TL() {
        Sonuc s = PuantajHesabi.hesapla(EKIM, hafta("2026-10-05"), DOLU); // Pazar 11 Eki: Ekim'in haftası

        assertThat(s.fiiliDakika()).isEqualTo(50 * 60);
        assertThat(s.fazlaMesaiDakika()).isEqualTo(5 * 60);
        assertThat(s.fazlaMesaiTl()).isEqualByComparingTo("1500.00");
        assertThat(s.km()).isEqualByComparingTo("1250");
        assertThat(s.kmTl()).isEqualByComparingTo("500.00");
        assertThat(s.calisilanGun()).isEqualTo(5);
        assertThat(s.ucretlendirilenDakika()).isEqualTo(50 * 60); // 50 s > 5 × 7,5 s
    }

    @Test
    void geceYedisaat_210TL_pencere20ile07() {
        Sonuc s = PuantajHesabi.hesapla(EKIM, List.of(gorev("2026-10-13T22:00", "2026-10-14T05:00", 100.0)), DOLU);

        assertThat(s.geceDakika()).isEqualTo(7 * 60);
        assertThat(s.geceTl()).isEqualByComparingTo("210.00");
        assertThat(s.calisilanGun()).isEqualTo(1); // gece yarısını aşan vardiya tek gün (W2)
        PuantajHesabi.Sonuc kural = PuantajHesabi.hesapla(EKIM, List.of(gorev("2026-10-13T06:30", "2026-10-13T08:00", 10.0)), DOLU);
        assertThat(kural.geceDakika()).isEqualTo(30); // 06:30-07:00 puantaj gece penceresinde (kural penceresi 20-06 olsaydı 0 olurdu)
    }

    @Test
    void ucGun_4_4_7Saat_ucretlendirilen22Bucuk() {
        List<Gorev> g = List.of(gorev("2026-10-12T08:00", "2026-10-12T12:00", 100.0), gorev("2026-10-13T08:00", "2026-10-13T12:00", 100.0),
                gorev("2026-10-14T08:00", "2026-10-14T15:00", 100.0));

        Sonuc s = PuantajHesabi.hesapla(EKIM, g, DOLU);

        assertThat(s.fiiliDakika()).isEqualTo(15 * 60);
        assertThat(s.calisilanGun()).isEqualTo(3);
        assertThat(s.ucretlendirilenDakika()).isEqualTo(22 * 60 + 30);
        assertThat(s.fazlaMesaiDakika()).isZero();
    }

    @Test
    void varisGecikmesiCalismaSuresindenSayilir_5Bucuk_iptalSifir() {
        // sefer 08:00-12:00, varış 90 dk gecikti: güncel varış 13:30 -> 5,5 s
        assertThat(PuantajHesabi.hesapla(EKIM, List.of(gorev("2026-10-12T08:00", "2026-10-12T13:30", 100.0)), DOLU).fiiliDakika()).isEqualTo(330);
        // iptal sefer listeye hiç girmez
        Sonuc bos = PuantajHesabi.hesapla(EKIM, List.of(), DOLU);
        assertThat(bos.fiiliDakika()).isZero();
        assertThat(bos.ucretlendirilenDakika()).isZero();
        assertThat(bos.fazlaMesaiTl()).isEqualByComparingTo("0");
    }

    @Test
    void ayinSonHaftasi_pazarSonrakiAydaysa_fazlaMesaiSonrakiAya() {
        List<Gorev> hafta = hafta("2026-10-26"); // Pzt 26 Eki - Paz 1 Kas: Pazar Kasım'da
        Sonuc ekim = PuantajHesabi.hesapla(EKIM, hafta, DOLU);
        Sonuc kasim = PuantajHesabi.hesapla(YearMonth.of(2026, 11), hafta, DOLU);

        assertThat(ekim.fazlaMesaiDakika()).isZero();
        assertThat(ekim.fiiliDakika()).isEqualTo(50 * 60); // fiili çalışma yine takvim ayına yazılır
        assertThat(kasim.fazlaMesaiDakika()).isEqualTo(5 * 60);
        assertThat(kasim.fiiliDakika()).isZero(); // 26-30 Eki görevleri Kasım'da fiili sayılmaz
        assertThat(kasim.km()).isZero(); // km planlanan gün ayında sayılır
    }

    @Test
    void ayGecisiGorevi_fiiliAylaKesisir() {
        List<Gorev> g = List.of(gorev("2026-10-31T22:00", "2026-11-01T04:00", 300.0));

        assertThat(PuantajHesabi.hesapla(EKIM, g, DOLU).fiiliDakika()).isEqualTo(2 * 60);
        assertThat(PuantajHesabi.hesapla(YearMonth.of(2026, 11), g, DOLU).fiiliDakika()).isEqualTo(4 * 60);
        assertThat(PuantajHesabi.hesapla(EKIM, g, DOLU).km()).isEqualByComparingTo("300");
        assertThat(PuantajHesabi.hesapla(YearMonth.of(2026, 11), g, DOLU).km()).isZero();
    }

    @Test
    void oranBosken_tlAlanlariNull_miktarlarHesaplanir_kmBilinmeyenSayilir() {
        List<Gorev> g = new ArrayList<>(hafta("2026-10-05"));
        g.add(gorev("2026-10-12T22:00", "2026-10-13T05:00", null));

        Sonuc s = PuantajHesabi.hesapla(EKIM, g, BOS);

        assertThat(s.fazlaMesaiDakika()).isEqualTo(5 * 60);
        assertThat(s.geceDakika()).isEqualTo(12 * 60); // 7 s gece görevi + 5 gün × (06:00-07:00 puantaj gece penceresi)
        assertThat(s.fazlaMesaiTl()).isNull();
        assertThat(s.geceTl()).isNull();
        assertThat(s.kmTl()).isNull();
        assertThat(s.kmBilinmeyenSefer()).isEqualTo(1);
        assertThat(s.km()).isEqualByComparingTo("1250");
    }

    @Test
    void w1_araYuvarlamaYok_20dkFazlaMesai_12345x15_61_73() {
        Oranlar o = new Oranlar(45 * 60, LocalTime.of(20, 0), LocalTime.of(7, 0), new BigDecimal("0.15"), 450, new BigDecimal("123.45"), new BigDecimal("1.5"), null);
        List<Gorev> g = new ArrayList<>();
        for (int i = 5; i <= 9; i++) { // Pzt-Cum 5 x 9 s = 45 s
            g.add(gorev("2026-10-0" + i + "T06:00", "2026-10-0" + i + "T15:00", 10.0));
        }
        g.add(gorev("2026-10-10T08:00", "2026-10-10T08:20", 1.0)); // cumartesi 20 dk

        Sonuc s = PuantajHesabi.hesapla(EKIM, g, o);

        assertThat(s.fazlaMesaiDakika()).isEqualTo(20);
        assertThat(s.fazlaMesaiTl()).isEqualByComparingTo("61.73"); // 20 × 123,45 × 1,5 / 60 = 61,725 -> HALF_UP
    }

    @Test
    void w1_kmBigDecimalToplam_1749km_x035_6122() {
        Oranlar o = new Oranlar(45 * 60, LocalTime.of(20, 0), LocalTime.of(7, 0), new BigDecimal("0.15"), 450, null, new BigDecimal("1.5"), new BigDecimal("0.35"));
        List<Gorev> g = List.of(gorev("2026-10-12T08:00", "2026-10-12T09:00", 34.9), gorev("2026-10-13T08:00", "2026-10-13T09:00", 51.8),
                gorev("2026-10-14T08:00", "2026-10-14T09:00", 88.2));

        Sonuc s = PuantajHesabi.hesapla(EKIM, g, o);

        assertThat(s.kmTl()).isEqualByComparingTo("61.22"); // 174,9 × 0,35 = 61,215 -> HALF_UP (double toplamı 174,89999… -> 61,21 verirdi)
    }

    @Test
    void w2_aralikliGeceVardiyasi_8SaatVardiya_tekGun_ucretlendirilen8() {
        // 13 Eki 22:00 -> 14 Eki 06:00, öncesi ve sonrası görevsiz: tek vardiya = tek çalışılan gün; ücretlendirilen max(8 s, 7,5 s) = 8 s (eskiden 15 s)
        Sonuc s = PuantajHesabi.hesapla(EKIM, List.of(gorev("2026-10-13T22:00", "2026-10-14T06:00", 100.0)), DOLU);

        assertThat(s.calisilanGun()).isEqualTo(1);
        assertThat(s.ucretlendirilenDakika()).isEqualTo(8 * 60);
    }
}
