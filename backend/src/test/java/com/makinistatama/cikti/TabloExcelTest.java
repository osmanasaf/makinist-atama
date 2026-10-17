package com.makinistatama.cikti;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** T12.1: genel tablo -> xlsx: başlık, satır sayısı, Türkçe karakter, formül enjeksiyonu nötr. */
class TabloExcelTest {

    private static Sheet oku(byte[] xlsx, int sayfa) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(sayfa);
    }

    @Test
    void baslikSatirlarVeTurkceKarakter() throws Exception {
        byte[] xlsx = TabloExcel.yaz(List.of(new TabloExcel.Sayfa("Değişiklikler", List.of("Zaman", "Kim", "Sayı"),
                List.of(Arrays.asList("12.10.2026 08:00", "Çağlar Şişman", 3), Arrays.asList("12.10.2026 09:00", "Öğüt İğde", null)))));

        Sheet s = oku(xlsx, 0);

        assertThat(s.getSheetName()).isEqualTo("Değişiklikler");
        assertThat(s.getLastRowNum()).isEqualTo(2); // başlık + 2 satır
        assertThat(s.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Kim");
        assertThat(s.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Çağlar Şişman");
        assertThat(s.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(3.0);
        assertThat(s.getRow(2).getCell(2)).isNull();
    }

    @Test
    void formulEnjeksiyonu_metinOlarakYazilir_onuneTirnak() throws Exception {
        byte[] xlsx = TabloExcel.yaz(List.of(new TabloExcel.Sayfa("S", List.of("A"),
                List.of(List.of("=HYPERLINK(\"http://kotu\")"), List.of("+1"), List.of("-2"), List.of("@SUM(A1)"), List.of("normal")))));

        Sheet s = oku(xlsx, 0);

        for (int i = 1; i <= 4; i++) {
            assertThat(s.getRow(i).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(s.getRow(i).getCell(0).getStringCellValue()).startsWith("'");
        }
        assertThat(s.getRow(5).getCell(0).getStringCellValue()).isEqualTo("normal");
    }

    @Test
    void sayfaAdiGecersizKarakterVeUzunluk() throws Exception {
        byte[] xlsx = TabloExcel.yaz(List.of(new TabloExcel.Sayfa("Rapor: 1/2 [x] ve çok uzun bir sayfa adı kırk karakteri aşar", List.of("A"), List.of())));

        assertThat(oku(xlsx, 0).getSheetName()).hasSizeLessThanOrEqualTo(31).doesNotContain(":", "/", "[");
    }
}
