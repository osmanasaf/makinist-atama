package com.makinistatama.cikti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Genel tablo -> .xlsx (FR-08, FR-10, FR-12; alan bilgisi yok, her rapor kendi başlık ve satırını verir). Metin hücreleri değer olarak yazılır, hiçbir zaman
 * formül olarak değil; ayrıca baştaki {@code = + - @} (formül enjeksiyonu) önüne {@code '} konur: kullanıcı verisi (tren no, ad, not) Excel'de formül çalıştıramaz.
 */
public final class TabloExcel {

    /** Bir çalışma sayfası: ad, başlık satırı, veri satırları (hücre: {@code String}, {@code Number} ya da {@code null}). */
    public record Sayfa(String ad, List<String> basliklar, List<List<Object>> satirlar) {
    }

    private static final int EN_COK_SUTUN_GENISLIGI = 60;

    private TabloExcel() {
    }

    public static byte[] yaz(List<Sayfa> sayfalar) {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            Font kalin = kitap.createFont();
            kalin.setBold(true);
            CellStyle baslikStili = kitap.createCellStyle();
            baslikStili.setFont(kalin);
            for (Sayfa s : sayfalar) {
                Sheet sayfa = kitap.createSheet(sayfaAdi(s.ad()));
                Row baslik = sayfa.createRow(0);
                for (int i = 0; i < s.basliklar().size(); i++) {
                    var hucre = baslik.createCell(i);
                    hucre.setCellValue(guvenli(s.basliklar().get(i)));
                    hucre.setCellStyle(baslikStili);
                }
                int satirNo = 1;
                for (List<Object> satir : s.satirlar()) {
                    Row r = sayfa.createRow(satirNo++);
                    for (int i = 0; i < satir.size(); i++) {
                        Object deger = satir.get(i);
                        if (deger instanceof Number n) {
                            r.createCell(i).setCellValue(n.doubleValue());
                        } else if (deger != null) {
                            r.createCell(i).setCellValue(guvenli(deger.toString()));
                        }
                    }
                }
                for (int i = 0; i < s.basliklar().size(); i++) {
                    sayfa.setColumnWidth(i, Math.min(EN_COK_SUTUN_GENISLIGI, Math.max(14, s.basliklar().get(i).length() + 4)) * 256);
                }
            }
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Formül enjeksiyonuna karşı: baştaki = + - @ (ve sekme / satır başı) için önüne tek tırnak. */
    static String guvenli(String metin) {
        if (metin == null || metin.isEmpty()) {
            return "";
        }
        char ilk = metin.charAt(0);
        return ilk == '=' || ilk == '+' || ilk == '-' || ilk == '@' || ilk == '\t' || ilk == '\r' ? "'" + metin : metin;
    }

    /** Excel sayfa adı: en çok 31 karakter, {@code : \ / ? * [ ]} yok. */
    private static String sayfaAdi(String ad) {
        String temiz = ad.replaceAll("[:\\\\/?*\\[\\]]", " ").strip();
        return temiz.isEmpty() ? "Sayfa" : temiz.substring(0, Math.min(31, temiz.length()));
    }
}
