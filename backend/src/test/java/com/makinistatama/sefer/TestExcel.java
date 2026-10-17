package com.makinistatama.sefer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Testlerde bellekte sefer .xlsx üretir (başlık + satırlar; her satır 9 metin hücresi: Tarih, Tren No, Kalkış Gar, Kalkış Saat, Varış Gar, Varış Saat, Araç Tipi, Hat, KM). */
public final class TestExcel {

    public static final List<String> BASLIK = List.of("Tarih", "Tren No", "Kalkış Gar", "Kalkış Saat", "Varış Gar", "Varış Saat", "Araç Tipi", "Hat", "KM");

    public static final List<String> DEGISIM_BASLIK = List.of("Tren No", "Sıra", "Gar", "Varış Saat", "Kalkış Saat", "KM", "Hat", "Araç Tipi");

    private TestExcel() {
    }

    public static byte[] sefer(List<String[]> satirlar) {
        return sefer(BASLIK, satirlar);
    }

    public static byte[] sefer(List<String> baslik, List<String[]> satirlar) {
        return sefer(baslik, satirlar, null);
    }

    /** İkinci sayfa "Değişim Noktaları" (F2c): satırlar Tren No, Sıra, Gar, Varış Saat, Kalkış Saat, KM, [Hat], [Araç Tipi]; null = sayfa yok. */
    public static byte[] sefer(List<String> baslik, List<String[]> satirlar, List<String[]> degisimSatirlari) {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            sayfaYaz(kitap.createSheet("Seferler"), baslik, satirlar);
            if (degisimSatirlari != null) {
                sayfaYaz(kitap.createSheet("Değişim Noktaları"), DEGISIM_BASLIK, degisimSatirlari);
            }
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void sayfaYaz(Sheet sayfa, List<String> baslik, List<String[]> satirlar) {
        Row b = sayfa.createRow(0);
        for (int i = 0; i < baslik.size(); i++) {
            b.createCell(i).setCellValue(baslik.get(i));
        }
        int r = 1;
        for (String[] s : satirlar) {
            Row satir = sayfa.createRow(r++);
            for (int i = 0; i < s.length; i++) {
                if (s[i] != null) {
                    satir.createCell(i).setCellValue(s[i]);
                }
            }
        }
    }
}
