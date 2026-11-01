package com.makinistatama.makinist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.makinistatama.makinist.MakinistExcelOkuyucu.Sonuc;
import com.makinistatama.makinist.MakinistExcelOkuyucu.Tanimlar;
import com.makinistatama.ortak.AramaMetni;

/**
 * OK-50 / QA BUG-1: formül hücresi okunurken formül METNİ değil dosyada saklanan (Excel'in hesapladığı) sonuç alınır; formül ÇALIŞTIRILMAZ. Sonuç dosyada yoksa (dosyayı Excel değil bir kütüphane
 * üretmiştir) satır hatası olur. Gerçek bir Excel dosyasında ad/sicil sütunu sık sık formüldür (ör. {@code =B2&" "&C2}).
 */
class ExcelFormulHucreTest {

    private static final Tanimlar TANIMLAR = new Tanimlar(Map.of(AramaMetni.katla("Ankara"), 1L), Map.of(), Map.of());

    /** Tek makinist satırı: sicil ve ad formül, ehliyet bitişi =DATE(...) formülü; {@code hesapla}: Excel gibi sonuçları dosyaya yazar. */
    private static byte[] kitap(boolean hesapla) throws Exception {
        try (XSSFWorkbook k = new XSSFWorkbook(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            Sheet s = k.createSheet("Makinistler");
            Row b = s.createRow(0);
            for (int i = 0; i < TestMakinistExcel.MAKINIST_BASLIK.size(); i++) {
                b.createCell(i).setCellValue(TestMakinistExcel.MAKINIST_BASLIK.get(i));
            }
            Row r = s.createRow(1);
            r.createCell(0).setCellFormula("\"f\"&\"001\"");
            r.createCell(1).setCellFormula("\"Ali\"&\" Veli\"");
            r.createCell(3).setCellValue("Ankara");
            r.createCell(5).setCellFormula("DATE(2030,12,31)");
            r.createCell(6).setCellValue("31.12.2030");
            r.createCell(7).setCellValue("31.12.2030");
            if (hesapla) {
                k.getCreationHelper().createFormulaEvaluator().evaluateAll();
            }
            k.write(o);
            return o.toByteArray();
        }
    }

    @Test
    void hesaplanmisFormul_sonucuOkunur_formulMetniKayitaGirmez() throws Exception {
        Sonuc s = MakinistExcelOkuyucu.oku(kitap(true), TANIMLAR, LocalDate.of(2026, 11, 1));

        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).hasSize(1);
        MakinistIstegi i = s.gecerli().getFirst().istek();
        assertThat(i.sicil()).isEqualTo("F001");
        assertThat(i.adSoyad()).isEqualTo("Ali Veli");
        assertThat(i.ehliyetBitis()).isEqualTo(LocalDate.of(2030, 12, 31));
    }

    @Test
    void sonucuDosyadaOlmayanFormul_satirHatasi_netMesaj() throws Exception {
        Sonuc s = MakinistExcelOkuyucu.oku(kitap(false), TANIMLAR, LocalDate.of(2026, 11, 1));

        assertThat(s.gecerli()).isEmpty();
        assertThat(s.hatalar()).hasSize(1);
        assertThat(s.hatalar().getFirst().satirNo()).isEqualTo(2);
        assertThat(s.hatalar().getFirst().neden()).contains("Formül").contains("değeri yapıştırın");
    }
}
