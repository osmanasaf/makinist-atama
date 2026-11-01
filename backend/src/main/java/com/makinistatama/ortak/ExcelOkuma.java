package com.makinistatama.ortak;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;

/**
 * Excel (.xlsx) okuyucularının ortak güvenlik ön denetimi ve hücre okuma yardımcıları (MK-169). {@code SeferExcelOkuyucu} ve
 * {@code MakinistExcelOkuyucu} tek yerden yararlanır: zip bombası ön denetimi iki kopya olmaz. Yalnız POI XSSF + JDK; POI'nin XXE korumaları açık kalır.
 */
public final class ExcelOkuma {

    public static final long EN_FAZLA_BAYT = 5L * 1024 * 1024;
    /** Zip içeriğinin açılmış toplam sınırı: POI sayfayı DOM olarak yükler (~20x bellek); 20 bin satır ~6 MB XML. */
    public static final long EN_FAZLA_ACILMIS_BAYT = 16L * 1024 * 1024;
    private static final int EN_FAZLA_ZIP_GIRDISI = 200;

    private static final byte[] ZIP_IMZASI = { 'P', 'K', 3, 4 };
    private static final byte[] SATIR_ETIKETI = { '<', 'r', 'o', 'w' };
    private static final DateTimeFormatter TARIH_TR = DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT); // 31.02 -> hata, 28 Şubat'a düzelmez

    static {
        // JVM-genel statik: POI kullanan her kod 16 MB'tan büyük zip girdisi okuyamaz.
        ZipSecureFile.setMaxEntrySize(EN_FAZLA_ACILMIS_BAYT);
    }

    private ExcelOkuma() {
    }

    /** Boş / 5 MB üstü / zip imzasız (.xls, parolalı, rastgele bayt) dosyayı reddeder; sonra {@link #onKontrol}. */
    public static void dosyaDenetle(byte[] girdi, int azamiSatir) {
        if (girdi == null || girdi.length == 0) {
            throw UygulamaHatasi.gecersizDosya("Dosya boş.");
        }
        if (girdi.length > EN_FAZLA_BAYT) {
            throw UygulamaHatasi.gecersizDosya("Dosya çok büyük (en çok 5 MB).");
        }
        if (girdi.length < 4 || girdi[0] != ZIP_IMZASI[0] || girdi[1] != ZIP_IMZASI[1] || girdi[2] != ZIP_IMZASI[2] || girdi[3] != ZIP_IMZASI[3]) {
            throw UygulamaHatasi.gecersizDosya("Dosya .xlsx biçiminde değil (eski .xls ve parolalı dosyalar desteklenmez).");
        }
        onKontrol(girdi, azamiSatir);
    }

    /**
     * POI dosyayı DOM olarak belleğe almadan ÖNCE: açılmış toplam bayt ve sayfa başına satır sayısı sınırı (zip girdileri akıtılarak
     * sayılır, hiçbir şey saklanmaz). Sıkıştırma oranı POI'nin 100:1 korumasının altında kalan zip-bombasını ve çok satırlı
     * sayfayı daha ilk geçişte keser.
     */
    public static void onKontrol(byte[] girdi, int azamiSatir) {
        long toplam = 0;
        long satir = 0; // geçerli sayfadaki satır sayısı (her sayfa için sıfırlanır)
        int girdiSayisi = 0;
        byte[] tampon = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(girdi))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++girdiSayisi > EN_FAZLA_ZIP_GIRDISI) {
                    throw UygulamaHatasi.gecersizDosya("Dosya geçerli bir .xlsx değil (çok fazla bileşen).");
                }
                boolean sayfa = e.getName().startsWith("xl/worksheets/") && e.getName().endsWith(".xml");
                satir = 0;
                int eslesen = 0; // "<row" ön eki
                boolean etiketSonu = false; // "<row" görüldü, sıradaki bayt ' ', '>' veya '/' ise satır
                int n;
                while ((n = zip.read(tampon)) > 0) {
                    toplam += n;
                    if (toplam > EN_FAZLA_ACILMIS_BAYT) {
                        throw UygulamaHatasi.gecersizDosya("Dosya açıldığında çok büyük (en çok 16 MB).");
                    }
                    for (int i = 0; sayfa && i < n; i++) {
                        byte c = tampon[i];
                        if (etiketSonu) {
                            etiketSonu = false;
                            if ((c == ' ' || c == '>' || c == '/') && ++satir > azamiSatir + 1L) { // + başlık satırı
                                throw UygulamaHatasi.gecersizDosya("Sayfada en çok " + azamiSatir + " satır olabilir.");
                            }
                        }
                        eslesen = c == SATIR_ETIKETI[eslesen] ? eslesen + 1 : (c == '<' ? 1 : 0);
                        if (eslesen == SATIR_ETIKETI.length) {
                            eslesen = 0;
                            etiketSonu = true;
                        }
                    }
                }
            }
        } catch (UygulamaHatasi ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw UygulamaHatasi.gecersizDosya("Dosya açılamadı: geçerli bir .xlsx olmalı.");
        }
    }

    public static boolean bos(Row satir, DataFormatter bicim) {
        for (Cell c : satir) {
            if (!bicim.formatCellValue(c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    public static String metin(Cell c, DataFormatter bicim) {
        if (c == null) {
            return "";
        }
        if (c.getCellType() == CellType.FORMULA) { // formül ÇALIŞTIRILMAZ: dosyada saklanan sonuç okunur (POI aksi halde formül metnini verir)
            if (formulSonucsuz(c)) {
                return "";
            }
            return switch (c.getCachedFormulaResultType()) {
                case STRING -> c.getStringCellValue().strip();
                case NUMERIC -> DateUtil.isCellDateFormatted(c) ? bicim.formatRawCellContents(c.getNumericCellValue(), c.getCellStyle().getDataFormat(), c.getCellStyle().getDataFormatString()).strip()
                        : sayiMetni(c.getNumericCellValue());
                case BOOLEAN -> c.getBooleanCellValue() ? "TRUE" : "FALSE";
                default -> "";
            };
        }
        if (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            return sayiMetni(c.getNumericCellValue());
        }
        return bicim.formatCellValue(c).strip();
    }

    private static String sayiMetni(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : BigDecimal.valueOf(d).toPlainString();
    }

    /** Formül hücresi, ama hesaplanmış sonucu dosyada yok: dosyayı Excel değil bir kütüphane üretmiştir (Excel kaydederken sonucu yazar); değer varmış gibi okunmaz. */
    public static boolean formulSonucsuz(Cell c) {
        return c != null && c.getCellType() == CellType.FORMULA && !(c instanceof XSSFCell x && x.getCTCell().isSetV());
    }

    /** Satırda sonucu olmayan formül hücresi varsa satır sorunu (yoksa null): kullanıcıya ne yapacağını söyler. */
    public static String formulSorunu(Row satir) {
        for (Cell c : satir) {
            if (formulSonucsuz(c)) {
                return "Formül hücresi (" + CellReference.convertNumToColString(c.getColumnIndex()) + ") dosyada hesaplanmış değer taşımıyor; dosyayı Excel'de açıp kaydedin ya da değeri yapıştırın";
            }
        }
        return null;
    }

    public static LocalDate tarih(Cell c, List<String> sorunlar) {
        return tarih(c, "Tarih", sorunlar);
    }

    /** Excel tarihi ya da metin ({@code g.a.yyyy}, ISO); {@code etiket} hata mesajının başına gelir ("Tarih boş", "Ehliyet bitişi geçersiz ..."). */
    public static LocalDate tarih(Cell c, String etiket, List<String> sorunlar) {
        if (c == null || c.getCellType() == CellType.BLANK) {
            sorunlar.add(etiket + " boş");
            return null;
        }
        if (formulSonucsuz(c)) {
            sorunlar.add(etiket + " geçersiz: formülün hesaplanmış değeri dosyada yok (değeri yapıştırın)");
            return null;
        }
        try {
            if (c.getCellType() == CellType.NUMERIC || (c.getCellType() == CellType.FORMULA && c.getCachedFormulaResultType() == CellType.NUMERIC)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String m = c.getStringCellValue().strip();
            try {
                return LocalDate.parse(m, TARIH_TR);
            } catch (DateTimeParseException e) {
                return LocalDate.parse(m);
            }
        } catch (RuntimeException e) {
            sorunlar.add(etiket + " geçersiz (gg.aa.yyyy beklenir)");
            return null;
        }
    }

    public static String ham(Cell c) {
        return c.getCellType() == CellType.STRING ? c.getStringCellValue() : new DataFormatter().formatCellValue(c);
    }
}
