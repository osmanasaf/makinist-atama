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

/**
 * T14.2: puantaj ön hesabı servisi ve uçları (FR-12). Oranlar boşsa TL alanları null ("oran girilmedi"), dolunca hesap {@link PuantajHesabiTest} örnekleriyle tutarlı;
 * etiket "ücret bordrosu değildir"; DEMO etiketi yalnız demo makinist varken.
 */
class PuantajTest extends TestVeritabani {

    AtamaVeri v;
    long makinist;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        makinist = v.makinist("D001");
        v.makinist("D002");
        LocalDate pazartesi = LocalDate.of(2026, 10, 5);
        for (int i = 0; i < 5; i++) { // 5 x 10 saat, 250 km: haftalık 50 s -> fazla mesai 5 s
            long sefer = v.sefer("T" + i, pazartesi.plusDays(i), "06:00", "16:00");
            v.atama(sefer, makinist, "ASIL");
        }
        t = atamaci();
    }

    @Test
    void oranlarBos_tlAlanlariNull_miktarlarVar_etiketVeDemoUyarisi() throws Exception {
        t.get("/api/rapor/puantaj?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.etiket").value("Puantaj ön hesabı — ücret bordrosu değildir"))
                .andExpect(jsonPath("$.saatUcretiGirildi").value(false)).andExpect(jsonPath("$.kmBirimGirildi").value(false)).andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.satirlar", hasSize(2)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].fiiliSaat").value(50.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].fazlaMesaiSaat").value(5.0))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].km").value(1250.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].calisilanGun").value(5))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].fazlaMesaiTl").value((Object) null)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].kmTl").value((Object) null))
                .andExpect(jsonPath("$.oranlar.saatUcreti").value((Object) null)).andExpect(jsonPath("$.oranlar.fazlaMesaiCarpani").value(1.5))
                .andExpect(jsonPath("$.oranlar.gecePenceresi").value("20:00–07:00"));
    }

    @Test
    void oranlarDolunca_serviseHesapTutarli_1500_500_ve_makinistSuzgeci() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/puantaj.saat_ucreti", "{\"deger\":\"200\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        y.put("/api/parametre/puantaj.km_birim_tutari", "{\"deger\":\"0.40\",\"neden\":\"deneme\"}").andExpect(status().isOk());

        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + makinist).andExpect(jsonPath("$.saatUcretiGirildi").value(true)).andExpect(jsonPath("$.satirlar", hasSize(1)))
                .andExpect(jsonPath("$.satirlar[0].fazlaMesaiTl").value(1500.0)).andExpect(jsonPath("$.satirlar[0].kmTl").value(500.0)).andExpect(jsonPath("$.satirlar[0].geceTl").value(150.0)); // 06:00-07:00 puantaj gece penceresinde: 5 gün × 1 s = 5 s × 200 × 0,15
        // ay sonu haftası kuralı: bu hafta (5-11 Eki) Ekim'e yazılır, Kasım'da hiçbir şey yok
        t.get("/api/rapor/puantaj?ay=2026-11&makinist=" + makinist).andExpect(jsonPath("$.satirlar[0].fazlaMesaiSaat").value(0.0)).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(0.0));
    }

    @Test
    void demoEtiketiYalnizDemoMakinistVarken_excelBilgiSayfasi() throws Exception {
        byte[] demo = t.get("/api/rapor/puantaj/excel?ay=2026-10").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(demo))) {
            assertThat(kitap.getSheetAt(0).getLastRowNum()).isEqualTo(2); // başlık + 2 AKTIF makinist
            assertThat(kitap.getSheetAt(1).getRow(1).getCell(1).getStringCellValue()).isEqualTo("DEMO – gerçek değil");
            assertThat(kitap.getSheetAt(0).getRow(1).getCell(9)).isNull(); // oran boş: TL hücresi yok
        }

        jdbc.update("UPDATE makinist SET ad_soyad = 'Gerçek Kişi ' || sicil");
        t.get("/api/rapor/puantaj?ay=2026-10").andExpect(jsonPath("$.demo").value(false));
        byte[] gercek = t.get("/api/rapor/puantaj/excel?ay=2026-10").andReturn().getResponse().getContentAsByteArray();
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(gercek))) {
            assertThat(kitap.getSheetAt(1).getRow(1).getCell(0).getStringCellValue()).isEqualTo("Ay");
        }
    }

    @Test
    void dogrulama_404_anonim401() throws Exception {
        t.get("/api/rapor/puantaj?ay=2026-13").andExpect(status().isBadRequest());
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=99999").andExpect(status().isNotFound());
        new Tarayici(mvc).get("/api/rapor/puantaj?ay=2026-10").andExpect(status().isUnauthorized());
    }
}
