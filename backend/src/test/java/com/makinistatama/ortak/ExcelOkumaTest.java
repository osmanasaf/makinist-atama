package com.makinistatama.ortak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Ortak Excel yardımcılarının doğrudan testi (MK-169): sefer ve makinist okuyucuları bunlara yaslanır. */
class ExcelOkumaTest {

    private static final DataFormatter BICIM = new DataFormatter();

    private static byte[] zip(int satir, boolean sayfaAdi) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry(sayfaAdi ? "xl/worksheets/sheet1.xml" : "baska.xml"));
            for (int i = 1; i <= satir; i++) {
                z.write(("<row r=\"" + i + "\"><c r=\"A" + i + "\"><v>1</v></c></row>").getBytes());
            }
            z.closeEntry();
        }
        return b.toByteArray();
    }

    @Test
    void satirSiniri_azamiSatirParametresine_baglidir() throws Exception {
        byte[] bin = zip(1_001, true); // 1.000 veri + 1 başlık
        assertThatCode(() -> ExcelOkuma.onKontrol(bin, 1_000)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ExcelOkuma.onKontrol(bin, 999)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("en çok 999 satır");
        assertThatCode(() -> ExcelOkuma.onKontrol(zip(5_000, false), 10)).as("sayfa olmayan girdi satır saymaz").doesNotThrowAnyException();
    }

    @Test
    void dosyaDenetle_bosBuyukImzasiz_reddedilir() {
        assertThatThrownBy(() -> ExcelOkuma.dosyaDenetle(new byte[0], 10)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("boş");
        assertThatThrownBy(() -> ExcelOkuma.dosyaDenetle(null, 10)).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> ExcelOkuma.dosyaDenetle(new byte[(int) ExcelOkuma.EN_FAZLA_BAYT + 1], 10)).hasMessageContaining("en çok 5 MB");
        assertThatThrownBy(() -> ExcelOkuma.dosyaDenetle("merhaba dunya".getBytes(), 10)).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> ExcelOkuma.dosyaDenetle(new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0 }, 10)).as(".xls imzası").hasMessageContaining(".xlsx");
    }

    @Test
    void acilmisBoyut_16MBuAsarsa_zipBombasiReddedilir() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            byte[] blok = ("<c><is><t>" + "a".repeat(64) + "</t></is></c>").getBytes();
            for (int i = 0; i < 400_000; i++) {
                z.write(blok);
            }
            z.closeEntry();
        }
        assertThat(b.size()).isLessThan((int) ExcelOkuma.EN_FAZLA_BAYT);
        assertThatThrownBy(() -> ExcelOkuma.dosyaDenetle(b.toByteArray(), 20_000)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("açıldığında çok büyük");
    }

    @Test
    void fazlaZipBileseni_reddedilir() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (int i = 0; i < 201; i++) {
                z.putNextEntry(new ZipEntry("x" + i));
                z.closeEntry();
            }
        }
        assertThatThrownBy(() -> ExcelOkuma.onKontrol(b.toByteArray(), 10)).hasMessageContaining("çok fazla bileşen");
    }

    @Test
    void tarih_metinExcelTarihiVeHatalar() throws Exception {
        try (XSSFWorkbook kitap = new XSSFWorkbook()) {
            Row r = kitap.createSheet("S").createRow(0);
            r.createCell(0).setCellValue("5.3.2027");
            r.createCell(1).setCellValue("2027-03-05");
            r.createCell(2).setCellValue("31.02.2027"); // STRICT: 28 Şubat'a düzelmez
            r.createCell(3).setCellValue("yarin");
            r.createCell(4).setCellValue(LocalDate.of(2027, 3, 5));
            r.createCell(5);
            List<String> sorun = new ArrayList<>();
            assertThat(ExcelOkuma.tarih(r.getCell(0), sorun)).isEqualTo(LocalDate.of(2027, 3, 5));
            assertThat(ExcelOkuma.tarih(r.getCell(1), sorun)).isEqualTo(LocalDate.of(2027, 3, 5));
            assertThat(ExcelOkuma.tarih(r.getCell(4), sorun)).isEqualTo(LocalDate.of(2027, 3, 5));
            assertThat(sorun).isEmpty();
            assertThat(ExcelOkuma.tarih(r.getCell(2), sorun)).isNull();
            assertThat(ExcelOkuma.tarih(r.getCell(3), sorun)).isNull();
            assertThat(ExcelOkuma.tarih(r.getCell(5), sorun)).isNull();
            assertThat(ExcelOkuma.tarih(null, "Ehliyet bitişi", sorun)).isNull();
            assertThat(sorun).containsExactly("Tarih geçersiz (gg.aa.yyyy beklenir)", "Tarih geçersiz (gg.aa.yyyy beklenir)", "Tarih boş", "Ehliyet bitişi boş");
        }
    }

    @Test
    void metin_sayisalSicilTamsayiGorunur_bosSatirAlgilanir() throws Exception {
        try (XSSFWorkbook kitap = new XSSFWorkbook()) {
            Sheet s = kitap.createSheet("S");
            Row r = s.createRow(0);
            r.createCell(0).setCellValue(10234d);
            r.createCell(1).setCellValue(1.5d);
            r.createCell(2).setCellValue("  Ankara  ");
            Cell bos = s.createRow(1).createCell(0);
            bos.setCellValue("   ");
            assertThat(ExcelOkuma.metin(r.getCell(0), BICIM)).isEqualTo("10234");
            assertThat(ExcelOkuma.metin(r.getCell(1), BICIM)).isEqualTo("1.5");
            assertThat(ExcelOkuma.metin(r.getCell(2), BICIM)).startsWith("Ankara");
            assertThat(ExcelOkuma.metin(null, BICIM)).isEmpty();
            assertThat(ExcelOkuma.bos(s.getRow(1), BICIM)).isTrue();
            assertThat(ExcelOkuma.bos(r, BICIM)).isFalse();
            assertThat(ExcelOkuma.ham(r.getCell(0))).isEqualTo("10234");
        }
    }
}
