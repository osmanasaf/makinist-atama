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
 * T13.1: adalet raporu (FR-10). Plan örneği: 120 / 100 / 80 / 0 saat -> ortalama 75; +%60, +%33,3, +%6,7, −%100 ("0 saat"). İptal sefer sayılmaz, gece saati
 * 22:00-08:00 görevinde 9 s (puantajla aynı 20:00-07:00 penceresi), km'si boş sefer sayaçta, PASIF makinist satırda yok, izin günü yalnız ONAY ve ay içi.
 */
class AdaletTest extends TestVeritabani {

    static final LocalDate EKIM = LocalDate.of(2026, 10, 1);

    AtamaVeri v;
    Tarayici t;

    private void gunler(long makinist, String onEk, int ilkGun, int adet, String kalkis, String varis) {
        for (int i = 0; i < adet; i++) {
            long sefer = v.sefer(onEk + (ilkGun + i), EKIM.plusDays(ilkGun + i - 1), kalkis, varis);
            v.atama(sefer, makinist, "ASIL");
        }
    }

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        long a = v.makinist("D001"); // 10 x 12 s = 120 s
        long b = v.makinist("D002"); // 9 x 10 s + gece 22:00-08:00 (10 s) = 100 s
        long c = v.makinist("D003"); // 8 x 10 s = 80 s (+ iptal sefer sayılmaz)
        v.makinist("D004"); // 0 saat
        long pasif = v.makinist("D005", true, "PASIF");
        gunler(a, "A", 1, 10, "06:00", "18:00");
        gunler(b, "B", 1, 9, "06:00", "16:00");
        long gece = v.sefer("BG", EKIM.plusDays(10), "22:00", "08:00");
        v.atama(gece, b, "ASIL");
        gunler(c, "C", 1, 8, "06:00", "16:00");
        long iptal = v.sefer("CI", EKIM.plusDays(8), "06:00", "16:00");
        v.atama(iptal, c, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptal);
        gunler(pasif, "P", 20, 2, "06:00", "08:00");
        jdbc.update("UPDATE sefer SET km = NULL WHERE tren_no = 'A1'"); // 9 x 250 km + 1 km bilinmiyor
        // D001: onaylı izin 28 Eki - 3 Kas (Ekim'de 4 gün), talep durumundaki izin sayılmaz
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, '2026-10-28', '2026-11-03', 'YILLIK', 'ONAY', 'test')", a);
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, '2026-10-05', '2026-10-06', 'YILLIK', 'TALEP', 'test')", a);
        t = atamaci();
    }

    @Test
    void saat_120_100_80_0_ortalama75_farklar_sifirSaatIsareti() throws Exception {
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.ay").value("2026-10")).andExpect(jsonPath("$.makinistSayisi").value(4))
                .andExpect(jsonPath("$.ortalamaSaat").value(75.0)).andExpect(jsonPath("$.satirlar", hasSize(4)))
                .andExpect(jsonPath("$.ureten").value("Ata Maci")).andExpect(jsonPath("$.geceSaatiPenceresi").value("20:00–07:00"))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].gorevSaat").value(120.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].farkYuzde").value(60.0))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D002')].gorevSaat").value(100.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D002')].farkYuzde").value(33.3))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D003')].gorevSaat").value(80.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D003')].farkYuzde").value(6.7))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D004')].gorevSaat").value(0.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D004')].farkYuzde").value(-100.0))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D004')].sifirSaat").value(true)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].sifirSaat").value(false))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].seferSayisi").value(10)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D003')].seferSayisi").value(8))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D005')]").isEmpty()); // PASIF satırda yok
    }

    @Test
    void geceSaati_kmBilinmeyenSayaci_izinGunu() throws Exception {
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[?(@.sicil=='D002')].geceSaat").value(18.0)) // puantajla aynı pencere 20:00-07:00: 22:00-08:00 görevi 9 s + 9 gün × 06:00-07:00
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].geceSaat").value(10.0)) // 10 sefer × 06:00-07:00
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].km").value(2250.0)).andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].kmBilinmeyenSefer").value(1))
                .andExpect(jsonPath("$.kmBilinmeyenSefer").value(1))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].izinGunu").value(4)) // 28-31 Eki; talep durumundaki izin sayılmaz
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D002')].izinGunu").value(0));
        t.get("/api/rapor/adalet?ay=2026-11").andExpect(jsonPath("$.satirlar[?(@.sicil=='D001')].izinGunu").value(3)) // 1-3 Kas
                .andExpect(jsonPath("$.ortalamaSaat").value(0.0)).andExpect(jsonPath("$.satirlar[0].farkYuzde").doesNotExist());
    }

    @Test
    void gecikmeSaateDahil_excelSatirSayisiVeSifirNotu() throws Exception {
        t.post("/api/sefer/" + jdbc.queryForObject("SELECT id FROM sefer WHERE tren_no = 'C1'", Long.class) + "/gecikme", "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":90,\"neden\":\"GECIKME\"}")
                .andExpect(status().isOk());

        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[?(@.sicil=='D003')].gorevSaat").value(81.5)); // 80 s + 90 dk gecikme

        byte[] xlsx = t.get("/api/rapor/adalet/excel?ay=2026-10").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(kitap.getSheetAt(0).getLastRowNum()).isEqualTo(4); // başlık + 4 AKTIF makinist
            assertThat(kitap.getSheetAt(0).getRow(4).getCell(9).getStringCellValue()).isEqualTo("0 saat");
            assertThat(kitap.getSheetAt(1).getSheetName()).isEqualTo("Rapor bilgisi");
        }
    }

    @Test
    void ayBozuk400_anonim401() throws Exception {
        t.get("/api/rapor/adalet?ay=2026-13").andExpect(status().isBadRequest());
        t.get("/api/rapor/adalet").andExpect(status().isBadRequest());
        new Tarayici(mvc).get("/api/rapor/adalet?ay=2026-10").andExpect(status().isUnauthorized());
    }
}
