package com.makinistatama.konaklama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F2b QA (FR-10): konaklama Excel'inin HÜCRE değerleri elle hesapla aynı: gece sınırları (24 s tam = 1, 24 s 1 dk = 2, 40 s = 2, 36 s = 2), ay = giriş günü
 * (31 Eki 22:00 girişli kayıt Ekim'e yazılır, 1 Kas 00:30 girişli Kasım'a), makinist x gar kırılımı, toplam satırı ve dipnot.
 */
class KonaklamaExcelQaTest extends TestVeritabani {

    Tarayici t;
    long m;
    long sivas;
    long konya;

    @BeforeEach
    void kur() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        m = v.makinist("D020");
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        konya = TestTanimlari.gar(jdbc, "Konya");
        t = atamaci();
        kaydet(sivas, "2026-10-25T08:00:00+03:00", "2026-10-26T08:00:00+03:00"); // tam 24 s: 1
        kaydet(sivas, "2026-10-27T08:00:00+03:00", "2026-10-28T08:01:00+03:00"); // 24 s 1 dk: 2
        kaydet(sivas, "2026-10-20T20:00:00+03:00", "2026-10-22T12:00:00+03:00"); // 40 s: 2
        kaydet(konya, "2026-10-31T22:00:00+03:00", "2026-11-02T10:00:00+03:00"); // 36 s: 2, ay = Ekim
        kaydet(konya, "2026-11-05T00:30:00+03:00", "2026-11-05T06:00:00+03:00"); // Kasım: 1
    }

    private void kaydet(long gar, String giris, String cikis) throws Exception {
        t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + gar + ",\"yerTipi\":\"OTEL\",\"giris\":\"" + giris + "\",\"cikis\":\"" + cikis + "\"}")
                .andExpect(status().isCreated());
    }

    private XSSFWorkbook excel(String ay) throws Exception {
        byte[] xlsx = t.get("/api/konaklama/rapor/excel?ay=" + ay).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    @Test
    void ekim_garKirilimi_makinistOzeti_toplam_elleHesapla_ayniDegerler() throws Exception {
        try (XSSFWorkbook k = excel("2026-10")) {
            Sheet kon = k.getSheet("Konaklama");
            assertThat(kon.getLastRowNum()).isEqualTo(2); // başlık + Konya + Sivas
            assertThat(kon.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Konya");
            assertThat((int) kon.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(1); // 1 kayıt (ay = giriş günü)
            assertThat((int) kon.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(2); // 36 s -> 2 gece
            assertThat(kon.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Sivas");
            assertThat((int) kon.getRow(2).getCell(3).getNumericCellValue()).isEqualTo(3);
            assertThat((int) kon.getRow(2).getCell(4).getNumericCellValue()).isEqualTo(5); // 1 + 2 + 2

            Sheet ozet = k.getSheet("Makinist özeti");
            assertThat(ozet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("D020");
            assertThat((int) ozet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(4);
            assertThat((int) ozet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(7);

            Sheet bilgi = k.getSheet("Rapor bilgisi");
            boolean toplam = false;
            for (var satir : bilgi) {
                if ("Toplam gece".equals(satir.getCell(0).getStringCellValue())) {
                    assertThat((int) satir.getCell(1).getNumericCellValue()).isEqualTo(7);
                    toplam = true;
                }
            }
            assertThat(toplam).isTrue();
        }
    }

    @Test
    void kasim_yalnizKasimdaGirisliKayit_1Gece() throws Exception {
        try (XSSFWorkbook k = excel("2026-11")) {
            Sheet kon = k.getSheet("Konaklama");
            assertThat(kon.getLastRowNum()).isEqualTo(1);
            assertThat(kon.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Konya");
            assertThat((int) kon.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(1);
        }
    }
}
