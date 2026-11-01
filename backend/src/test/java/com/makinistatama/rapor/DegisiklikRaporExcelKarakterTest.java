package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F4 / T5.3 / MK-181 KARAKTERİZASYON: {@code DegisiklikRaporServisi.excel} çıktısı {@code sayfalar(r)} ayrımından ÖNCE ve SONRA birebir aynı olmalı. Sayfa adları, başlık satırları ve her satırın
 * zaman dışı hücreleri (zaman gerçek saattir: hücre 0 ve "Üretim zamanı" karşılaştırmaya girmez) sabitlenir; satırlar zaman sırasındadır, bu yüzden küme olarak (sıralı metin) karşılaştırılır.
 */
class DegisiklikRaporExcelKarakterTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Test
    void excelCiktisi_sayfaAdlariBasliklarVeHucreler_sabit() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long m3 = v.makinist("D003");
        long s1 = v.seferEv("T1", GUN, "06:00", "08:00");
        long s2 = v.seferEv("T2", GUN, "09:00", "11:00");
        long s3 = v.seferEv("T3", GUN, "12:00", "14:00");
        v.atama(s1, m1, "ASIL");
        v.atama(s2, m2, "ASIL");
        Tarayici t = atamaci();
        t.post("/api/sefer/" + s1 + "/gecikme", "{\"kalkisGecikmeDk\":20,\"neden\":\"GECIKME\"}").andExpect(status().isOk());
        t.post("/api/sefer/" + s3 + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + s1, "{\"makinistId\":" + m3 + ",\"neden\":\"GECIKME\"}").andExpect(status().isOk());
        long gecmis = v.seferEv("G", LocalDate.of(2026, 10, 9), "08:00", "10:00"); // şimdi 10 Eki 10:00: bitmiş sefer -> K-22 UYARI
        t.post("/api/atama/sefer/" + gecmis, "{\"makinistId\":" + m3 + ",\"gerekce\":\"geçmiş kaydın düzeltilmesi\"}").andExpect(status().isCreated());
        yonetici().put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"10\",\"neden\":\"TİS\"}").andExpect(status().isOk());
        LocalDate bugun = LocalDate.now(AtamaVeri.ISTANBUL);

        byte[] xlsx = t.get("/api/degisiklik/excel?bas=" + bugun.minusDays(1) + "&bit=" + bugun.plusDays(1)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(kitap.getNumberOfSheets()).isEqualTo(2);
            assertThat(kitap.getSheetName(0)).isEqualTo("Değişiklikler");
            assertThat(kitap.getSheetName(1)).isEqualTo("Rapor bilgisi");
            Sheet degisiklik = kitap.getSheetAt(0);
            assertThat(satir(degisiklik.getRow(0))).containsExactly("Zaman", "Kim", "Bölüm", "Tür", "Sicil", "Tren no", "Sefer tarihi", "Eski", "Yeni", "Neden", "Not", "Uyarılı",
                    "Uyarı gerekçesi", "Uyaran kural", "Otomatik");
            List<String> govde = new ArrayList<>();
            for (int i = 1; i <= degisiklik.getLastRowNum(); i++) {
                List<String> h = satir(degisiklik.getRow(i));
                assertThat(h.get(0)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?"); // zaman biçimi; değeri gerçek saat
                govde.add(String.join(" | ", h.subList(1, h.size())));
            }
            govde.sort(String::compareTo);
            assertThat(govde).containsExactlyElementsOf(ALTIN);
            Sheet bilgi = kitap.getSheetAt(1);
            assertThat(satir(bilgi.getRow(0))).containsExactly("Alan", "Değer");
            List<String> alanlar = new ArrayList<>();
            for (int i = 1; i <= bilgi.getLastRowNum(); i++) {
                alanlar.add(bilgi.getRow(i).getCell(0).getStringCellValue());
            }
            assertThat(alanlar).containsExactly("Üretim zamanı", "Üreten", "Tarih aralığı", "Makinist", "Tren no", "Kullanıcı", "Tür", "Kayıt sayısı");
            assertThat(bilgi.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Ata Maci");
            assertThat(bilgi.getRow(3).getCell(1).getStringCellValue()).isEqualTo(bugun.minusDays(1) + " – " + bugun.plusDays(1));
            assertThat(bilgi.getRow(8).getCell(1).getNumericCellValue()).isEqualTo(ALTIN.size());
        }
    }

    private static List<String> satir(Row r) {
        List<String> l = new ArrayList<>();
        DataFormatter f = new DataFormatter();
        for (int i = 0; i < r.getLastCellNum(); i++) {
            Cell c = r.getCell(i);
            l.add(c == null ? "" : f.formatCellValue(c));
        }
        return l;
    }

    /** Refaktör ÖNCESİ gerçek çıktıdan (zaman sütunu hariç) sabitlendi; sıralı. */
    static final List<String> ALTIN = List.of(
            "Ata Maci | Atama değişiklikleri (yerine atama, kaldırma) | Makinist değiştirildi | D003 | T1 | 2026-10-12 | ASIL:D001 | ASIL:D003 | GECIKME |  |  |  |  | ",
            "Ata Maci | Sefer iptal ve gecikmeleri | Sefer gecikmesi |  | T1 | 2026-10-12 | 06:00-08:00 | 06:20-08:20 | GECIKME |  |  |  |  | ",
            "Ata Maci | Sefer iptal ve gecikmeleri | Sefer iptali |  | T3 | 2026-10-12 | PLANLI | IPTAL | IPTAL |  |  |  |  | ",
            "Ata Maci | Uyarılı atamalar (gerekçeyle yapılan) | Atama | D003 | G | 2026-10-09 |  | ASIL:D003 |  |  | Evet | geçmiş kaydın düzeltilmesi | K-22: Sefer 9 Eki 10:00'da bitti; geçmiş tarihli sefere atama | ",
            "Yöne Tici | Parametre ve kural değişiklikleri | Ayar değişti: Bağlı garda dinlenme süresi |  |  |  | 11 | 10 | DIGER | TİS |  |  |  | ");
}
