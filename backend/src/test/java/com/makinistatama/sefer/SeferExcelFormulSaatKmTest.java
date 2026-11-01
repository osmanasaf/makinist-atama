package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.function.BiConsumer;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.makinistatama.sefer.SeferExcelOkuyucu.Sonuc;

/**
 * QA tur 2 YENİ-1: sefer Excel'inde saat ve KM hücresi formülse (Excel'in hesapladığı, önbellekli) tren no ve tarih gibi önbellek değeriyle okunur; eskiden "saat geçersiz: 'TIME(8,0,0)'" /
 * "KM sayı değil: '200+35'" diyordu. Hata sonuçlu formül (#DIV/0!) formül metni değil "hata" diye bildirilir.
 */
class SeferExcelFormulSaatKmTest {

    /** Tek sefer satırı; {@code duzenle}: satır hücrelerini ({@code kalkisSaat} = sütun 3, {@code km} = sütun 8) formülle değiştirir. */
    private static Sonuc oku(BiConsumer<Row, XSSFWorkbook> duzenle, boolean hesapla) throws Exception {
        try (XSSFWorkbook k = new XSSFWorkbook(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            Sheet s = k.createSheet("Seferler");
            Row b = s.createRow(0);
            for (int i = 0; i < TestExcel.BASLIK.size(); i++) {
                b.createCell(i).setCellValue(TestExcel.BASLIK.get(i));
            }
            Row r = s.createRow(1);
            String[] d = SeferExcelOkuyucuTest.satir("12.10.2026", "F1", "08:00", "09:30");
            for (int i = 0; i < d.length; i++) {
                r.createCell(i).setCellValue(d[i]);
            }
            duzenle.accept(r, k);
            if (hesapla) {
                k.getCreationHelper().createFormulaEvaluator().evaluateAll();
            }
            k.write(o);
            return SeferExcelOkuyucu.oku(o.toByteArray(), SeferExcelOkuyucuTest.TANIMLAR);
        }
    }

    @Test
    void saatVeKmFormulHucresi_onbellekDegeriOkunur() throws Exception {
        Sonuc s = oku((r, k) -> {
            r.getCell(3).setBlank();
            r.getCell(3).setCellFormula("TIME(8,0,0)"); // 0,3333 -> 08:00
            r.getCell(8).setBlank();
            r.getCell(8).setCellFormula("200+35");
        }, true);

        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).singleElement().satisfies(x -> {
            assertThat(x.kalkis().atZone(ZoneId.of("Europe/Istanbul")).toLocalTime().toString()).isEqualTo("08:00");
            assertThat(x.km()).isEqualByComparingTo(new BigDecimal("235"));
        });
    }

    @Test
    void hataSonuclu_formul_hataDiyeBildirilir_formulMetniDegil() throws Exception {
        Sonuc s = oku((r, k) -> {
            r.getCell(8).setBlank();
            r.getCell(8).setCellFormula("1/0");
        }, true);

        assertThat(s.gecerli()).isEmpty();
        assertThat(s.hatalar()).hasSize(1);
        assertThat(s.hatalar().getFirst().neden()).contains("hata sonucu").contains("#DIV/0!").doesNotContain("1/0");
    }
}
