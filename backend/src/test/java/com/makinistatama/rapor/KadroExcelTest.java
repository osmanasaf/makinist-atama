package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.KadroRaporuDto;
import com.makinistatama.atama.KadroRaporuDto.Analitik;
import com.makinistatama.atama.KadroRaporuDto.GarSatiri;
import com.makinistatama.atama.KadroRaporuDto.KalanBos;
import com.makinistatama.atama.KadroRaporuDto.DagilimSatiri;
import com.makinistatama.atama.KadroRaporuDto.Doluluk;
import com.makinistatama.atama.KadroRaporuDto.EgriNokta;
import com.makinistatama.atama.KadroRaporuDto.Hedef;
import com.makinistatama.atama.KadroRaporuDto.HedefFark;
import com.makinistatama.atama.KadroRaporuDto.NedenSayisi;
import com.makinistatama.atama.KadroRaporuDto.Kume;
import com.makinistatama.atama.KadroRaporuDto.Senaryo;
import com.makinistatama.atama.KadroRaporuDto.Simulasyon;
import com.makinistatama.atama.KadroRaporuDto.SinifSatiri;
import com.makinistatama.atama.KadroRaporuDto.TalepSatiri;
import com.makinistatama.atama.KadroRaporuDto.Varsayim;
import com.makinistatama.atama.KadroRaporuDto.Yedek;

import tools.jackson.databind.ObjectMapper;

/** F3a-3 Excel (MK-79): istemcinin geri gönderdiği rapordan üretilir; sayfalar, formül enjeksiyonu, boyut/dize sınırları, yetki. */
class KadroExcelTest extends TestVeritabani {

    @Autowired
    ObjectMapper json;

    private static Doluluk dol(String ad, int bos) {
        return new Doluluk(ad, "etiket " + ad, 2800, bos, 100.0 * (2800 - bos) / 2800, 100);
    }

    private static KadroRaporuDto rapor(String hat, int kalanBosSayisi, boolean simulasyonlu, boolean senaryolu) {
        Simulasyon sim = !simulasyonlu ? null
                : new Simulasyon(66, dol("mevcut", 122), dol("konumsuz", 35), null, null, List.of(new EgriNokta(0, 122, 95.6, null), new EgriNokta(5, 59, 97.9, 12.6)),
                        List.of(new Hedef(98, true, 9, 55, 98.0), new Hedef(99, false, null, 12, 99.6), new Hedef(100, false, null, 12, 99.6)), "IYILESME_YOK", 14, 22,
                        "%99 hedefi için +22 genel makinist", List.of(new DagilimSatiri("Ankara", "YHT", hat, 4, 200, 10.5)), kalanBoslar(kalanBosSayisi), kalanBosSayisi,
                        List.of(new NedenSayisi("konum", kalanBosSayisi)), List.of(), true, 1000);
        Senaryo sen = !senaryolu ? null
                : new Senaryo(112, 154.0, dol("taban", 122), dol("senaryo", 143), 21, 11, false, "Mevcut kadro yetmez. =HYPERLINK(\"x\")", List.of(new HedefFark(98, 9, 12, 3)), 3.2, 14.6, 0, 0, true, null);
        return new KadroRaporuDto(new Varsayim(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 8), 4, 28, 13, 47.85, 0.1, 1, 25, 66, 2800, 2201.3),
                List.of(new TalepSatiri(hat, "YHT", 840, 350.6)), List.of(new GarSatiri("Ankara", 943.3, 37, 4)),
                new Analitik(47.85, 47, 49, 66, 0, 0, List.of(new SinifSatiri(hat, "YHT", 350.6, 8, 39, 0, 0))),
                List.of(new Kume(List.of("YHT"), List.of(hat), 13, 622.0, 813.45, 76.5, "NORMAL")), "PLAN", new Yedek(10, 100), sim, sen);
    }

    private static List<KalanBos> kalanBoslar(int n) {
        List<KalanBos> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new KalanBos((long) i, "T" + i, LocalDate.of(2026, 10, 12), "Ankara", "Sivas", "0/66 uygun: konum 5"));
        }
        return l;
    }

    private MvcResult post(Tarayici t, KadroRaporuDto r, int beklenen) throws Exception {
        return t.post("/api/rapor/kadro/excel", json.writeValueAsString(r)).andExpect(status().is(beklenen)).andReturn();
    }

    private static Workbook kitap(MvcResult m) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(m.getResponse().getContentAsByteArray()));
    }

    @Test
    void sayfalar_ozetVeVarsayimlar_dosyaAdi_uygunBicim() throws Exception {
        MvcResult m = post(yonetici(), rapor("Ankara–Eskişehir", 3, true, true), 200);
        assertThat(m.getResponse().getContentType()).contains("spreadsheetml");
        assertThat(m.getResponse().getHeader("Content-Disposition")).contains("kadro-ihtiyaci-2026-10-12.xlsx");
        try (Workbook k = kitap(m)) {
            List<String> adlar = new ArrayList<>();
            k.forEach(s -> adlar.add(s.getSheetName()));
            assertThat(adlar).containsExactly("Özet", "Doluluk eğrisi", "Eklenen dağılımı", "Kalan boş nedenleri", "Kalan boşlar", "Talep", "Garlar", "Bröve kümeleri", "Sınıf analizi", "Senaryo farkı", "Varsayımlar");
            Sheet ozet = k.getSheet("Özet");
            assertThat(ozet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Kalem");
            var bicim0 = new org.apache.poi.ss.usermodel.DataFormatter();
            boolean baslik = false;
            boolean hedef98 = false;
            StringBuilder ozetMetni = new StringBuilder();
            for (int i = 1; i <= ozet.getLastRowNum(); i++) {
                String ad = ozet.getRow(i).getCell(0).getStringCellValue();
                String deger = bicim0.formatCellValue(ozet.getRow(i).getCell(1));
                ozetMetni.append(deger).append('\n');
                if (ad.startsWith("BAŞLIK")) {
                    baslik = true;
                    assertThat(deger).contains("122 boş").contains("doluluk %95.6");
                }
                if (ad.startsWith("%98 doluluk")) {
                    hedef98 = true;
                    assertThat(deger).isEqualTo("9");
                }
            }
            assertThat(baslik).isTrue();
            assertThat(hedef98).isTrue();
            assertThat(ozetMetni).contains("pencereye bağlıdır");
            assertThat(k.getSheet("Doluluk eğrisi").getLastRowNum()).isEqualTo(2);
            assertThat(k.getSheet("Kalan boşlar").getLastRowNum()).isEqualTo(3);
            // Varsayımlar: "gerçek ihtiyaç daha düşük olabilir" notu; sunucu hesabı iddiası yok
            StringBuilder v = new StringBuilder();
            var bicim = new org.apache.poi.ss.usermodel.DataFormatter();
            k.getSheet("Varsayımlar").forEach(r -> v.append(r.getCell(1) == null ? "" : bicim.formatCellValue(r.getCell(1))).append('\n'));
            assertThat(v).contains("sunucu yeniden hesaplamadı").doesNotContain("gerçek ihtiyaç daha düşük olabilir");
        }
    }

    @Test
    void analitikRapor_simulasyonVeSenaryoSayfalariYok() throws Exception {
        try (Workbook k = kitap(post(yonetici(), rapor("Hat", 0, false, false), 200))) {
            List<String> adlar = new ArrayList<>();
            k.forEach(s -> adlar.add(s.getSheetName()));
            assertThat(adlar).containsExactly("Özet", "Talep", "Garlar", "Bröve kümeleri", "Sınıf analizi", "Varsayımlar");
        }
    }

    @Test
    void formulEnjeksiyonu_hucreBasindaTirnak() throws Exception {
        try (Workbook k = kitap(post(yonetici(), rapor("=CMD|'/c calc'!A1", 1, true, true), 200))) {
            assertThat(k.getSheet("Talep").getRow(1).getCell(0).getStringCellValue()).isEqualTo("'=CMD|'/c calc'!A1");
            assertThat(k.getSheet("Eklenen dağılımı").getRow(1).getCell(2).getStringCellValue()).startsWith("'=");
            for (int i = 0; i <= k.getSheet("Senaryo farkı").getLastRowNum(); i++) {
                var c = k.getSheet("Senaryo farkı").getRow(i).getCell(1);
                if (c != null && c.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                    throw new AssertionError("formül hücresi yok olmalı");
                }
            }
        }
    }

    @Test
    void sinirlar_cokSatirVeUzunMetin_400_eksikRapor400_yetki() throws Exception {
        Tarayici y = yonetici();
        post(y, rapor("Hat", 4996, true, false), 400); // toplam satır (5 + 4996) > 5.000
        post(y, rapor("Hat", 4000, true, false), 200); // sınır içinde
        post(y, rapor("x".repeat(501), 1, true, false), 400); // dize > 500
        post(y, rapor("x".repeat(500), 1, true, false), 200);
        y.post("/api/rapor/kadro/excel", "{}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro/excel", "{\"varsayim\":null}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro/excel", "bozuk").andExpect(status().isBadRequest());
        post(atamaci(), rapor("Hat", 1, true, false), 403);
        post(new Tarayici(mvc), rapor("Hat", 1, true, false), 401);
    }
}
