package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.sefer.SeferBolIstegi.Nokta;

/**
 * T2.4 (MK-120, FR-01, NFR-02): iki yol AYNI sefer satırlarını üretir, yani ortak çekirdek gerçekten tek hesap. (a) Excel "Değişim Noktaları" sayfasıyla yükleme;
 * (b) parçasız yükleme + {@link SeferParcaServisi#bol}. Sabit tohumlu 40 rastgele tren (gece aşanlar, km boş/dolu, hat/araç mirası dahil).
 */
class ParcaYollariEsdegerTest extends TestVeritabani {

    static final ZoneId IST = ZoneId.of("Europe/Istanbul");
    static final DateTimeFormatter EXCEL_TARIH = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("HH:mm");
    static final Instant SIMDI = LocalDateTime.of(2026, 10, 10, 10, 0).atZone(IST).toInstant();
    static final int TREN_SAYISI = 40;

    @Autowired
    SeferYuklemeServisi yukleme;

    @Autowired
    SeferParcaServisi servis;

    /** garlar[0] = kalkış, garlar[1] = varış, 2.. = ara istasyon havuzu. */
    final String[] garlar = { "Ankara", "Kars", "Sivas", "Erzincan", "Erzurum", "Kayseri" };
    final String[] hatlar = { "H-1", "H-2", "H-3" };
    final String[] araclar = { "A-1", "A-2", "A-3" };
    long[] garId;
    long[] hatId;
    long[] aracId;

    @BeforeEach
    void tanimlar() {
        garId = java.util.Arrays.stream(garlar).mapToLong(g -> TestTanimlari.gar(jdbc, g)).toArray();
        hatId = java.util.Arrays.stream(hatlar).mapToLong(h -> TestTanimlari.hat(jdbc, h)).toArray();
        aracId = java.util.Arrays.stream(araclar).mapToLong(a -> TestTanimlari.aracTipi(jdbc, a)).toArray();
    }

    /** Bir tren: sefer satırı + ara noktalar (ekran girdisi). */
    record Tren(String no, LocalDate gun, LocalTime kalkis, LocalTime varis, BigDecimal km, String tur, int hat, int arac, List<Ara> noktalar) {
    }

    record Ara(int gar, LocalTime varis, LocalTime kalkis, BigDecimal km, Integer hat, Integer arac) {
    }

    /** Geçerli rastgele trenler: toplam süre <= 24 s, noktalar pencerede ve ardışık, km kümülatif artar (boşsa hiç yok). */
    List<Tren> trenler() {
        Random r = new Random(20261015L);
        List<Tren> sonuc = new ArrayList<>();
        for (int i = 0; i < TREN_SAYISI; i++) {
            LocalDate gun = LocalDate.of(2026, 10, 12).plusDays(i % 3);
            LocalTime kalkis = LocalTime.of(r.nextInt(24), r.nextInt(60));
            int n = 1 + r.nextInt(4);
            boolean kmVar = r.nextInt(3) != 0;
            int pos = 0;
            int kmPos = 0;
            List<Ara> noktalar = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                int varisDk = pos + 30 + r.nextInt(210);
                int kalkisDk = varisDk + r.nextInt(31);
                if (kalkis.plusMinutes(kalkisDk).isBefore(kalkis.plusMinutes(varisDk))) {
                    kalkisDk = varisDk; // nokta beklemesi gece yarısını geçemez (Excel ve ekran aynı kuralı uygular: saat karşılaştırması)
                }
                kmPos += 10 + r.nextInt(190);
                noktalar.add(new Ara(2 + k, kalkis.plusMinutes(varisDk), kalkis.plusMinutes(kalkisDk), kmVar ? BigDecimal.valueOf(kmPos) : null,
                        r.nextInt(3) == 0 ? r.nextInt(3) : null, r.nextInt(3) == 0 ? r.nextInt(3) : null));
                pos = kalkisDk;
            }
            int toplamDk = Math.min(1440, pos + 30 + r.nextInt(200));
            sonuc.add(new Tren("T-" + i, gun, kalkis, kalkis.plusMinutes(toplamDk), kmVar ? BigDecimal.valueOf(kmPos + 10 + r.nextInt(190)) : null,
                    new String[] { "Yük", "Yolcu", "" }[r.nextInt(3)], r.nextInt(3), r.nextInt(3), noktalar));
        }
        return sonuc;
    }

    List<String[]> anaSatirlar(List<Tren> trenler) {
        return trenler.stream().map(t -> new String[] { t.gun().format(EXCEL_TARIH), t.no(), garlar[0], t.kalkis().format(SAAT), garlar[1], t.varis().format(SAAT), araclar[t.arac()],
                hatlar[t.hat()], t.km() == null ? null : t.km().toPlainString(), t.tur() }).toList();
    }

    List<String[]> degisimSatirlari(List<Tren> trenler) {
        List<String[]> s = new ArrayList<>();
        for (Tren t : trenler) {
            for (int k = 0; k < t.noktalar().size(); k++) {
                Ara a = t.noktalar().get(k);
                s.add(new String[] { t.no(), String.valueOf(k + 1), garlar[a.gar()], a.varis().format(SAAT), a.kalkis().format(SAAT), a.km() == null ? null : a.km().toPlainString(),
                        a.hat() == null ? null : hatlar[a.hat()], a.arac() == null ? null : araclar[a.arac()] });
            }
        }
        return s;
    }

    /** Karşılaştırılan sefer satırı kümesi: kimlikten ve sürümden bağımsız, tren + parça sırasıyla. */
    List<String> satirlar() {
        return jdbc.queryForList("""
                SELECT s.tren_no || ' ' || coalesce(s.parca_no::text, '-') || '/' || coalesce(s.parca_sayisi::text, '-') || ' tarih=' || s.tarih || ' tg=' || coalesce(s.tren_gunu::text, '-')
                       || ' ' || kg.ad || '>' || vg.ad || ' ' || to_char(s.planlanan_kalkis AT TIME ZONE 'Europe/Istanbul', 'YYYY-MM-DD HH24:MI')
                       || ' ' || to_char(s.planlanan_varis AT TIME ZONE 'Europe/Istanbul', 'YYYY-MM-DD HH24:MI') || ' km=' || coalesce(s.km::text, '-')
                       || ' hat=' || h.ad || ' arac=' || a.ad || ' tur=' || coalesce(s.tren_turu, '-') || ' durum=' || s.durum || ' gec=' || s.kalkis_gecikme_dk || '/' || s.varis_gecikme_dk
                FROM sefer s JOIN tanim kg ON kg.id = s.kalkis_gar_id JOIN tanim vg ON vg.id = s.varis_gar_id JOIN tanim h ON h.id = s.hat_id JOIN tanim a ON a.id = s.arac_tipi_id
                ORDER BY s.tren_no, s.parca_no, s.planlanan_kalkis""", String.class);
    }

    @Test
    void excelDegisimNoktalariIleYuklemeVeParcasizYuklemePlusBol_ayniSatirlariUretir() {
        List<Tren> trenler = trenler();
        assertThat(trenler.stream().filter(t -> t.noktalar().getFirst().varis().isBefore(t.kalkis()) || t.varis().isBefore(t.kalkis())).count())
                .as("kümede gece aşan tren var").isPositive();

        // (a) Excel + Değişim Noktaları
        YuklemeSonucuDto a = yukleme.yukle(TestExcel.sefer(SeferExcelOkuyucu.BASLIKLAR, anaSatirlar(trenler), degisimSatirlari(trenler)), false);
        assertThat(a.hatalar()).isEmpty();
        assertThat(a.eklenen()).isGreaterThan(TREN_SAYISI);
        List<String> excelYolu = satirlar();
        assertThat(excelYolu).hasSize(a.eklenen());

        // (b) parçasız yükle + bol
        jdbc.update("DELETE FROM sefer");
        YuklemeSonucuDto b = yukleme.yukle(TestExcel.sefer(SeferExcelOkuyucu.BASLIKLAR, anaSatirlar(trenler)), false);
        assertThat(b.hatalar()).isEmpty();
        assertThat(b.eklenen()).isEqualTo(TREN_SAYISI);
        for (Tren t : trenler) {
            long id = jdbc.queryForObject("SELECT id FROM sefer WHERE tren_no = ?", Long.class, t.no());
            long versiyon = jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id);
            List<Nokta> noktalar = t.noktalar().stream().map(n -> new Nokta(garId[n.gar()], n.varis(), n.kalkis(), n.km(), n.hat() == null ? null : hatId[n.hat()],
                    n.arac() == null ? null : aracId[n.arac()])).toList();
            BolSonucu s = servis.bol(id, new SeferBolIstegi(versiyon, noktalar, false), SIMDI);
            assertThat(s.parcalar()).as(t.no()).hasSize(t.noktalar().size() + 1);
        }

        assertThat(satirlar()).isEqualTo(excelYolu);
    }
}
