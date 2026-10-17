package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * QA (M3): gecikme / iptal girişinin sınırları ve denetim çıktısı. BUG-11: güncel varış, güncel kalkıştan önceye düşürülebiliyor
 * (yalnız kalkış gecikmesi büyük, varış gecikmesi küçük girilince); sonuçta gün listesi, aday listesi, kırmızı sayısı ve otomatik atama 500 veriyor.
 */
class SeferGecikmeQaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    long sefer;
    long makinist;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        makinist = v.makinist("D001");
        sefer = v.seferEv("T1", GUN, "08:00", "09:30"); // 90 dk
        v.atama(sefer, makinist, "ASIL");
    }

    /** BUG-11: kalkış +300 dk, varış +0 dk -> güncel kalkış 13:00, güncel varış 09:30. Beklenen: 400 DOGRULAMA; gün listesi çökmemeli. */
    @Test
    void gecikme_guncelVarisGuncelKalkistenOnceyeDusemez_400_gunListesiCokmez() throws Exception {
        Tarayici t = atamaci();

        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":300,\"varisGecikmeDk\":0,\"neden\":\"GECIKME\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("DOGRULAMA"));

        assertThat(jdbc.queryForObject("SELECT kalkis_gecikme_dk FROM sefer WHERE id = ?", Integer.class, sefer)).isZero();
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk());
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(status().isOk());
    }

    @Test
    void gecikme_siniri_1440Kabul_1441Red_tarihPlanlananGundeKalir() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":1441,\"neden\":\"GECIKME\"}").andExpect(status().isBadRequest());

        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":1440,\"neden\":\"GECIKME\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tarih").value("2026-10-14")).andExpect(jsonPath("$.guncelKalkis").value(containsString("2026-10-15T08:00:00")))
                .andExpect(jsonPath("$.guncelVaris").value(containsString("2026-10-15T09:30:00")));
        // sefer planlanan gününde listelenir, ertesi günde değil (gün = planlanan kalkış günü)
        t.get("/api/atama/gun?tarih=2026-10-14").andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[?(@.trenNo=='T1')].kalkisGecikmeDk").value(1440));
        t.get("/api/atama/gun?tarih=2026-10-15").andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[?(@.trenNo=='T1')]").isEmpty());
    }

    @Test
    void denetimExcel_nedenNotundaFormul_metinOlarakYazilir() throws Exception {
        Tarayici t = atamaci();
        String notu = "=HYPERLINK(\\\"http://kotu.example\\\",\\\"tikla\\\")";
        t.post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"DIGER\",\"nedenNotu\":\"" + notu + "\"}").andExpect(status().isOk());

        LocalDate bugun = LocalDate.now(AtamaVeri.ISTANBUL);
        byte[] xlsx = t.get("/api/degisiklik/excel?bas=" + bugun.minusDays(1) + "&bit=" + bugun.plusDays(1)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sayfa = kitap.getSheetAt(0);
            int notSutunu = -1;
            for (int i = 0; i < sayfa.getRow(0).getLastCellNum(); i++) {
                if ("Not".equals(sayfa.getRow(0).getCell(i).getStringCellValue())) {
                    notSutunu = i;
                }
            }
            assertThat(notSutunu).isPositive();
            boolean bulundu = false;
            for (int r = 1; r <= sayfa.getLastRowNum(); r++) {
                var hucre = sayfa.getRow(r).getCell(notSutunu);
                if (hucre != null && hucre.getStringCellValue().contains("HYPERLINK")) {
                    bulundu = true;
                    assertThat(hucre.getCellType()).isEqualTo(CellType.STRING);
                    assertThat(hucre.getStringCellValue()).startsWith("'=");
                }
            }
            assertThat(bulundu).isTrue();
        }
    }
}
