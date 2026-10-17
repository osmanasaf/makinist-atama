package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F3b MK-89 (FR-07): adalet ve puantaj tür bazında ayrı grup (vars. MAKINIST); ortalama karışmaz; kondüktör yalnız kendi grubunda. */
class RaporTurTest extends TestVeritabani {

    AtamaVeri v;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        t = atamaci();
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long k1 = v.konduktor("K001");
        long k2 = v.konduktor("K002");
        // makinistler 8 s + 0 s; kondüktörler 4 s + 4 s (ortalamalar ayrı: 4 s ve 4 s değil; karışsa 4 s olurdu)
        v.atama(v.seferEv("A1", LocalDate.of(2026, 10, 5), "06:00", "14:00"), m1, "ASIL");
        v.atama(v.seferEv("A2", LocalDate.of(2026, 10, 6), "06:00", "10:00"), k1, "KONDUKTOR");
        v.atama(v.seferEv("A3", LocalDate.of(2026, 10, 7), "06:00", "10:00"), k2, "KONDUKTOR");
        assertThat(m2).isPositive();
    }

    @Test
    void adalet_turYoksaYalnizMakinist_turKonduktorYalnizKondukturler() throws Exception {
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.tur").value("MAKINIST")).andExpect(jsonPath("$.satirlar", hasSize(2)))
                .andExpect(jsonPath("$.makinistSayisi").value(2)).andExpect(jsonPath("$.ortalamaSaat").value(4.0));
        t.get("/api/rapor/adalet?ay=2026-10&tur=MAKINIST").andExpect(jsonPath("$.satirlar", hasSize(2)));
        t.get("/api/rapor/adalet?ay=2026-10&tur=KONDUKTOR").andExpect(status().isOk()).andExpect(jsonPath("$.tur").value("KONDUKTOR")).andExpect(jsonPath("$.satirlar", hasSize(2)))
                .andExpect(jsonPath("$.satirlar[0].sicil").value("K001")).andExpect(jsonPath("$.ortalamaSaat").value(4.0)).andExpect(jsonPath("$.satirlar[0].farkYuzde").value(0.0));
        t.get("/api/rapor/adalet?ay=2026-10&tur=MANEVRACI").andExpect(status().isBadRequest());
    }

    @Test
    void adaletExcel_basligindaTur() throws Exception {
        byte[] xlsx = t.get("/api/rapor/adalet/excel?ay=2026-10&tur=KONDUKTOR").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(kitap.getSheetAt(0).getLastRowNum()).isEqualTo(2); // başlık + 2 kondüktör
            boolean turSatiri = false;
            for (var satir : kitap.getSheetAt(1)) {
                turSatiri |= "Personel türü".equals(satir.getCell(0).getStringCellValue()) && "KONDUKTOR".equals(satir.getCell(1).getStringCellValue());
            }
            assertThat(turSatiri).isTrue();
        }
    }

    @Test
    void puantaj_tureGoreAyriGrup_tekKisiSuzgeciKisininTuru() throws Exception {
        t.get("/api/rapor/puantaj?ay=2026-10").andExpect(jsonPath("$.tur").value("MAKINIST")).andExpect(jsonPath("$.satirlar", hasSize(2)));
        t.get("/api/rapor/puantaj?ay=2026-10&tur=KONDUKTOR").andExpect(jsonPath("$.tur").value("KONDUKTOR")).andExpect(jsonPath("$.satirlar", hasSize(2)))
                .andExpect(jsonPath("$.satirlar[0].sicil").value("K001")).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(4.0));
        long k1 = jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'K001'", Long.class);
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + k1).andExpect(jsonPath("$.tur").value("KONDUKTOR")).andExpect(jsonPath("$.satirlar", hasSize(1)));
    }
}
