package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.sefer.SeferExcelOkuyucu.Sonuc;
import com.makinistatama.sefer.SeferExcelOkuyucu.Tanimlar;

/** T4.3: saf okuyucu, DB'siz. */
class SeferExcelOkuyucuTest {

    static final Tanimlar TANIMLAR = new Tanimlar(
            Map.of(AramaMetni.katla("Ankara"), 1L, AramaMetni.katla("Eskişehir"), 2L),
            Map.of(AramaMetni.katla("Ankara–Eskişehir"), 10L),
            Map.of(AramaMetni.katla("YHT"), 20L));

    static String[] satir(String tarih, String tren, String kalkisSaat, String varisSaat) {
        return new String[] { tarih, tren, "Ankara", kalkisSaat, "Eskişehir", varisSaat, "YHT", "Ankara–Eskişehir", "245" };
    }

    @Test
    void yuzYirmiSatir_3Hatali_117Gecerli_hataSatirNoVeNedenleri() {
        List<String[]> satirlar = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            satirlar.add(satir("12.10.2026", "T" + i, "08:00", "09:30"));
        }
        satirlar.set(4, new String[] { "12.10.2026", "T5", "Ankara", "08:00", "Eskisehr", "09:30", "YHT", "Ankara–Eskişehir", "245" }); // bilinmeyen gar -> Excel satır 6
        satirlar.set(49, satir("12.10.2026", "T50", "25:70", "09:30"));                                                              // saat -> satır 51
        satirlar.set(99, new String[] { "12.10.2026", "T100", "Ankara", "08:00", "Eskişehir", "09:30", "", "Ankara–Eskişehir", "245" }); // araç tipi boş -> satır 101

        Sonuc s = SeferExcelOkuyucu.oku(TestExcel.sefer(satirlar), TANIMLAR);

        assertThat(s.gecerli()).hasSize(117);
        assertThat(s.hatalar()).hasSize(3);
        assertThat(s.hatalar().get(0).satirNo()).isEqualTo(6);
        assertThat(s.hatalar().get(0).neden()).contains("Varış garı bilinmiyor").contains("Eskisehr");
        assertThat(s.hatalar().get(1).satirNo()).isEqualTo(51);
        assertThat(s.hatalar().get(1).neden()).contains("Kalkış saati geçersiz");
        assertThat(s.hatalar().get(2).satirNo()).isEqualTo(101);
        assertThat(s.hatalar().get(2).neden()).contains("Araç tipi boş");
    }

    @Test
    void varisKalkistenKucukse_ertesiGun_istanbulSaati() {
        Sonuc s = SeferExcelOkuyucu.oku(TestExcel.sefer(List.<String[]>of(satir("12.10.2026", "T1", "22:00", "06:00"))), TANIMLAR);

        assertThat(s.gecerli()).hasSize(1);
        assertThat(s.gecerli().getFirst().kalkis()).isEqualTo(Instant.parse("2026-10-12T19:00:00Z"));
        assertThat(s.gecerli().getFirst().varis()).isEqualTo(Instant.parse("2026-10-13T03:00:00Z"));
        assertThat(s.gecerli().getFirst().km()).isEqualByComparingTo("245");
    }

    @Test
    void saatHucresiSayisalExcelZamaniOlabilir_veTarihHucresiDe() throws Exception {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            Sheet sayfa = kitap.createSheet("S");
            Row b = sayfa.createRow(0);
            for (int i = 0; i < 8; i++) {
                b.createCell(i).setCellValue(TestExcel.BASLIK.get(i));
            }
            CellStyle saat = kitap.createCellStyle();
            saat.setDataFormat(kitap.createDataFormat().getFormat("hh:mm"));
            CellStyle tarih = kitap.createCellStyle();
            tarih.setDataFormat(kitap.createDataFormat().getFormat("dd.mm.yyyy"));
            Row r = sayfa.createRow(1);
            r.createCell(0).setCellValue(java.time.LocalDate.of(2026, 10, 12));
            r.getCell(0).setCellStyle(tarih);
            r.createCell(1).setCellValue(81001); // sayısal tren no
            r.createCell(2).setCellValue("Ankara");
            r.createCell(3).setCellValue(8.0 / 24 + 30.0 / 1440); // 08:30
            r.getCell(3).setCellStyle(saat);
            r.createCell(4).setCellValue("Eskişehir");
            r.createCell(5).setCellValue(10.0 / 24); // 10:00
            r.getCell(5).setCellStyle(saat);
            r.createCell(6).setCellValue("YHT");
            r.createCell(7).setCellValue("Ankara–Eskişehir");
            kitap.write(cikti);

            Sonuc s = SeferExcelOkuyucu.oku(cikti.toByteArray(), TANIMLAR);

            assertThat(s.hatalar()).isEmpty();
            assertThat(s.gecerli().getFirst().trenNo()).isEqualTo("81001");
            assertThat(s.gecerli().getFirst().kalkis()).isEqualTo(Instant.parse("2026-10-12T05:30:00Z"));
            assertThat(s.gecerli().getFirst().varis()).isEqualTo(Instant.parse("2026-10-12T07:00:00Z"));
            assertThat(s.gecerli().getFirst().km()).isNull();
        }
    }

    @Test
    void baslikEksikse_veXlsxDegilse_400() {
        byte[] eksik = TestExcel.sefer(List.of("Tarih", "Tren No"), List.<String[]>of());
        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(eksik, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("eksik sütun");
        assertThatThrownBy(() -> SeferExcelOkuyucu.oku("merhaba".getBytes(), TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(new byte[0], TANIMLAR)).isInstanceOf(UygulamaHatasi.class);
    }

    @Test
    void sablon_okunabilir_ornekSatirBilinmeyenTanimdanHataVerir() {
        byte[] sablon = SeferExcelOkuyucu.sablon();

        Sonuc s = SeferExcelOkuyucu.oku(sablon, TANIMLAR);

        assertThat(s.gecerli()).hasSize(1); // şablon örneği: Ankara / Eskişehir / YHT / Ankara–Eskişehir
        assertThat(s.gecerli().getFirst().trenNo()).isEqualTo("YHT-81001");
    }

    // ---------- review Tur 1: W3 (yükleme sırasında bellek/satır sınırı), W4 (STRICT tarih) ----------

    @Test
    void gecersizTakvimGunu_satirHatasi_sessizce_duzeltilmez() {
        Sonuc s = SeferExcelOkuyucu.oku(TestExcel.sefer(List.<String[]>of(
                satir("31.02.2026", "T1", "08:00", "09:30"), satir("31.04.2026", "T2", "08:00", "09:30"),
                satir("29.02.2027", "T3", "08:00", "09:30"), satir("29.02.2028", "T4", "08:00", "09:30"),
                satir("2026-02-31", "T5", "08:00", "09:30"))), TANIMLAR);

        assertThat(s.gecerli()).extracting(g -> g.trenNo()).containsExactly("T4"); // 2028 artık yıl
        assertThat(s.hatalar()).extracting(h -> h.satirNo()).containsExactly(2, 3, 4, 6);
        assertThat(s.hatalar()).allSatisfy(h -> assertThat(h.neden()).contains("Tarih geçersiz"));
    }

    /** Yalnız zip kabuğu: gerçek bir kitap olmasına gerek yok; denetim POI'den ÖNCE çalışmalı. */
    private static byte[] zip(String ad, int tekrar, java.util.function.IntFunction<String> parca) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(b)) {
            z.putNextEntry(new java.util.zip.ZipEntry(ad));
            for (int i = 1; i <= tekrar; i++) {
                z.write(parca.apply(i).getBytes());
            }
            z.closeEntry();
        }
        return b.toByteArray();
    }

    @Test
    void acilmisBoyutuAsanZip_POI_yuklenmeden_reddedilir() throws Exception {
        String hucre = "<c><is><t>" + "a".repeat(64) + "</t></is></c>";
        byte[] bomba = zip("xl/worksheets/sheet1.xml", 400_000, i -> hucre); // ~31 MB açılmış, birkaç yüz KB sıkışmış
        assertThat(bomba.length).isLessThan((int) SeferExcelOkuyucu.EN_FAZLA_BAYT);
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(bomba, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("açıldığında çok büyük");
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(2_000);
    }

    @Test
    void satirSiniriniAsanSayfa_POI_yuklenmeden_reddedilir() throws Exception {
        byte[] cok = zip("xl/worksheets/sheet1.xml", SeferExcelOkuyucu.EN_FAZLA_SATIR + 5,
                i -> "<row r=\"" + i + "\"><c r=\"A" + i + "\"><v>1</v></c></row>");
        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(cok, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("en çok 20000 satır");
    }

    @Test
    void sinirdaki_20000_satirlik_gercek_dosya_kabul_edilir() {
        List<String[]> satirlar = new ArrayList<>();
        for (int i = 1; i <= SeferExcelOkuyucu.EN_FAZLA_SATIR; i++) {
            satirlar.add(satir("12.10.2026", "T" + i, "08:00", "09:30"));
        }
        byte[] dosya = TestExcel.sefer(satirlar);
        assertThat(dosya.length).isLessThan((int) SeferExcelOkuyucu.EN_FAZLA_BAYT);
        assertThat(SeferExcelOkuyucu.oku(dosya, TANIMLAR).gecerli()).hasSize(SeferExcelOkuyucu.EN_FAZLA_SATIR);
    }

    @Test
    void cokSayfali_dosyada_satirlar_sayfa_basina_sayilir() throws Exception {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            for (int sayfaNo = 0; sayfaNo < 2; sayfaNo++) {
                Sheet sayfa = kitap.createSheet("S" + sayfaNo);
                Row b = sayfa.createRow(0);
                for (int i = 0; i < TestExcel.BASLIK.size(); i++) {
                    b.createCell(i).setCellValue(TestExcel.BASLIK.get(i));
                }
                for (int r = 1; r <= 15_000; r++) { // iki sayfa toplam 30.000 satır; her sayfa sınırın altında
                    Row satir = sayfa.createRow(r);
                    String[] s = satir("12.10.2026", "T" + r, "08:00", "09:30");
                    for (int i = 0; i < s.length; i++) {
                        satir.createCell(i).setCellValue(s[i]);
                    }
                }
            }
            kitap.write(cikti);
            assertThat(SeferExcelOkuyucu.oku(cikti.toByteArray(), TANIMLAR).gecerli()).hasSize(15_000);
        }
    }

    // ------------------------------------------------------------------ Tur 2 (K2, FR-09): isteğe bağlı "Tren Türü" kolonu

    private static String[] trenli(String tren, String tur) {
        return new String[] { "12.10.2026", tren, "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245", tur };
    }

    @Test
    void trenTuruKolonu_yuk_yolcu_bos_gecerli_baskaDeger_satirHatasi() {
        List<String> baslik = new ArrayList<>(TestExcel.BASLIK);
        baslik.add("Tren Türü");
        Sonuc s = SeferExcelOkuyucu.oku(TestExcel.sefer(baslik, List.of(trenli("T1", "Yük"), trenli("T2", "yolcu"), trenli("T3", null), trenli("T4", "Buharlı"))), TANIMLAR);

        assertThat(s.gecerli()).extracting(SeferExcelOkuyucu.YuklemeSatiri::trenNo, SeferExcelOkuyucu.YuklemeSatiri::trenTuru)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("T1", TrenTuru.YUK), org.assertj.core.groups.Tuple.tuple("T2", TrenTuru.YOLCU),
                        org.assertj.core.groups.Tuple.tuple("T3", null));
        assertThat(s.hatalar()).hasSize(1);
        assertThat(s.hatalar().get(0).satirNo()).isEqualTo(5);
        assertThat(s.hatalar().get(0).neden()).contains("Tren türü geçersiz").contains("Buharlı");
    }

    @Test
    void trenTuruKolonuYoksa_eskiDosyalarAynenYuklenir() {
        Sonuc s = SeferExcelOkuyucu.oku(TestExcel.sefer(List.<String[]>of(satir("12.10.2026", "T1", "08:00", "09:30"))), TANIMLAR);
        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).singleElement().satisfies(x -> assertThat(x.trenTuru()).isNull());
    }

    /** OK-50 / QA BUG-1: sefer okuyucusu da ortak {@code ExcelOkuma.metin}'i kullanır; tren no formül hücresiyse hesaplanmış sonuç okunur, hesaplanmamışsa satır hatası olur. */
    @Test
    void trenNoFormulHucresi_hesaplanmisSonucOkunur_hesaplanmamisSatirHatasi() throws Exception {
        for (boolean hesapla : new boolean[] { true, false }) {
            try (XSSFWorkbook k = new XSSFWorkbook(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
                Sheet s = k.createSheet("Seferler");
                Row b = s.createRow(0);
                for (int i = 0; i < TestExcel.BASLIK.size(); i++) {
                    b.createCell(i).setCellValue(TestExcel.BASLIK.get(i));
                }
                Row r = s.createRow(1);
                String[] d = satir("12.10.2026", "-", "08:00", "09:30");
                for (int i = 0; i < d.length; i++) {
                    if (i == 1) {
                        r.createCell(i).setCellFormula("\"DE-\"&\"1503\"");
                    } else {
                        r.createCell(i).setCellValue(d[i]);
                    }
                }
                if (hesapla) {
                    k.getCreationHelper().createFormulaEvaluator().evaluateAll();
                }
                k.write(o);
                Sonuc sonuc = SeferExcelOkuyucu.oku(o.toByteArray(), TANIMLAR);
                if (hesapla) {
                    assertThat(sonuc.hatalar()).isEmpty();
                    assertThat(sonuc.gecerli()).singleElement().satisfies(x -> assertThat(x.trenNo()).isEqualTo("DE-1503"));
                } else {
                    assertThat(sonuc.gecerli()).isEmpty();
                    assertThat(sonuc.hatalar()).singleElement().satisfies(h -> assertThat(h.neden()).contains("Formül").contains("değeri yapıştırın"));
                }
            }
        }
    }
}
