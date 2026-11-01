package com.makinistatama.makinist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Testlerde bellekte makinist içe aktarma .xlsx üretir. Hücre: String -> metin, LocalDate -> Excel tarihi, null -> hücre yok. */
public final class TestMakinistExcel {

    public static final List<String> MAKINIST_BASLIK = List.of("Sicil", "Ad Soyad", "Tür", "Bağlı Gar", "Durum", "Ehliyet Bitiş", "Sağlık Bitiş", "Psikoteknik Bitiş", "Son Sürüş");
    public static final List<String> ARAC_BASLIK = List.of("Sicil", "Araç Tipi", "Sınav Tarihi");
    public static final List<String> HAT_BASLIK = List.of("Sicil", "Hat", "Sınav Tarihi", "Son Sürüş");

    private TestMakinistExcel() {
    }

    public static Object[] makinist(String sicil, String ad, String tur, String gar, String durum, Object ehliyet, Object saglik, Object psiko, Object sonSurus) {
        return new Object[] { sicil, ad, tur, gar, durum, ehliyet, saglik, psiko, sonSurus };
    }

    /** Geçerli, uzak gelecekli belgelerle sade makinist satırı. */
    public static Object[] makinist(String sicil, String ad, String gar) {
        return makinist(sicil, ad, null, gar, null, "31.12.2030", "31.12.2030", "31.12.2030", null);
    }

    public static byte[] kitap(List<Object[]> makinistler, List<Object[]> araclar, List<Object[]> hatlar) {
        return kitap(MAKINIST_BASLIK, makinistler, araclar, hatlar);
    }

    /** {@code araclar} / {@code hatlar} null ise o sayfa hiç yazılmaz. */
    public static byte[] kitap(List<String> makinistBaslik, List<Object[]> makinistler, List<Object[]> araclar, List<Object[]> hatlar) {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            CellStyle tarih = kitap.createCellStyle();
            tarih.setDataFormat(kitap.createDataFormat().getFormat("dd.mm.yyyy"));
            sayfa(kitap.createSheet("Makinistler"), makinistBaslik, makinistler, tarih);
            if (araclar != null) {
                sayfa(kitap.createSheet("Araç Tipi Bröveleri"), ARAC_BASLIK, araclar, tarih);
            }
            if (hatlar != null) {
                sayfa(kitap.createSheet("Hat Bröveleri"), HAT_BASLIK, hatlar, tarih);
            }
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sayfa(Sheet sayfa, List<String> baslik, List<Object[]> satirlar, CellStyle tarih) {
        Row b = sayfa.createRow(0);
        for (int i = 0; i < baslik.size(); i++) {
            b.createCell(i).setCellValue(baslik.get(i));
        }
        int r = 1;
        for (Object[] s : satirlar) {
            Row satir = sayfa.createRow(r++);
            for (int i = 0; i < s.length; i++) {
                if (s[i] instanceof String m) {
                    satir.createCell(i).setCellValue(m);
                } else if (s[i] instanceof LocalDate d) {
                    var c = satir.createCell(i);
                    c.setCellValue(d);
                    c.setCellStyle(tarih);
                }
            }
        }
    }
}
