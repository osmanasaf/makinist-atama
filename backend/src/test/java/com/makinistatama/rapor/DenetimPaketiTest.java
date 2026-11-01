package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F4 / AK-4.5 / MK-181 / T5.4: denetim paketi 7 sayfa (adlar ve başlıklar sabit), "Değişiklik kayıtları" satır sayısı denetim raporunun {@code kayitSayisi}'na eşit, hiçbir hücre formül değil (formül enjeksiyonu
 * koruması {@code TabloExcel}'de), demo etiketi, kural değerleri kaynak/güvenilirlikle.
 */
class DenetimPaketiTest extends TestVeritabani {

    static final YearMonth AY = YearMonth.from(LocalDate.now(AtamaVeri.ISTANBUL));
    static final String ENJEKSIYON = "=HYPERLINK(\"http://kotu.example\",\"tikla\")";

    Tarayici y;
    Tarayici t;
    String aralik = "bas=" + AY.atDay(1) + "&bit=" + AY.atEndOfMonth();

    @BeforeEach
    void kur() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        y = yonetici();
        t = atamaci();
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        v.atama(v.seferEv("T1", AY.atDay(1), "06:00", "08:00"), m1, "ASIL");
        long gecmis = v.seferEv("G", LocalDate.of(2026, 10, 9), "08:00", "10:00"); // şimdi 10 Eki 10:00: bitmiş sefer -> K-22 UYARI
        t.post("/api/atama/sefer/" + gecmis, "{\"makinistId\":" + m2 + ",\"gerekce\":\"" + ENJEKSIYON.replace("\"", "\\\"") + "\"}").andExpect(status().isCreated());
        y.put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"10\",\"neden\":\"TİS\"}").andExpect(status().isOk());
        y.post("/api/ozel-kural", "{\"ad\":\"" + ENJEKSIYON.replace("\"", "\\\"") + "\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":8,"
                + "\"siddet\":\"ENGEL\",\"gerekce\":\"TİS\",\"gecerlilikBas\":\"2026-01-01\"}").andExpect(status().isCreated());
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

    private XSSFWorkbook paket() throws Exception {
        MockHttpServletResponse r = y.get("/api/rapor/denetim-paketi/excel?ay=" + AY).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(r.getContentType()).contains("spreadsheetml");
        assertThat(r.getHeader("Content-Disposition")).contains("denetim-paketi-" + AY + ".xlsx");
        return new XSSFWorkbook(new ByteArrayInputStream(r.getContentAsByteArray()));
    }

    @Test
    void yediSayfa_adlarVeBasliklarSabit() throws Exception {
        try (var k = paket()) {
            List<String> adlar = new ArrayList<>();
            for (int i = 0; i < k.getNumberOfSheets(); i++) {
                adlar.add(k.getSheetName(i));
            }
            assertThat(adlar).containsExactly("Özet", "Değişiklik kayıtları", "Uyarılı atamalar", "Ayar değişiklikleri", "Kural değerleri", "Adalet", "Rapor bilgisi");
            assertThat(satir(k.getSheet("Özet").getRow(0))).containsExactly("Alan", "Değer");
            assertThat(satir(k.getSheet("Değişiklik kayıtları").getRow(0))).containsExactly("Zaman", "Kim", "Bölüm", "Tür", "Sicil", "Tren no", "Sefer tarihi", "Eski", "Yeni", "Neden", "Not",
                    "Uyarılı", "Uyarı gerekçesi", "Uyaran kural", "Otomatik");
            assertThat(satir(k.getSheet("Uyarılı atamalar").getRow(0))).containsExactly("Zaman", "Kim", "Sicil", "Tren no", "Sefer tarihi", "Uyarı gerekçesi (atamacı)", "Uyaran kural");
            assertThat(satir(k.getSheet("Ayar değişiklikleri").getRow(0))).containsExactly("Zaman", "Kim", "Tür", "Eski", "Yeni", "Neden", "Not");
            assertThat(satir(k.getSheet("Kural değerleri").getRow(0))).containsExactly("Tür", "Anahtar", "Ad", "Değer", "Birim", "Kaynak", "Güvenilirlik");
            assertThat(satir(k.getSheet("Adalet").getRow(0))).startsWith("Sicil", "Ad soyad", "Sefer", "Görev saati");
            assertThat(satir(k.getSheet("Rapor bilgisi").getRow(0))).containsExactly("Alan", "Değer");
        }
    }

    @Test
    void atamaKayitlari_satirSayisi_denetimRaporununKayitSayisinaEsit_uyarililarVeAyarlarAyri() throws Exception {
        String rapor = y.get("/api/degisiklik?" + aralik).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int kayit = JsonPath.<Number>read(rapor, "$.kayitSayisi").intValue();
        assertThat(kayit).isGreaterThanOrEqualTo(3); // atama(uyarılı) + parametre + özel kural
        try (var k = paket()) {
            assertThat(k.getSheet("Değişiklik kayıtları").getLastRowNum()).isEqualTo(kayit); // başlık + kayıt
            assertThat(k.getSheet("Uyarılı atamalar").getLastRowNum()).isEqualTo(1);
            assertThat(satir(k.getSheet("Uyarılı atamalar").getRow(1))).contains("G", "'" + ENJEKSIYON);
            List<String> ayarTurleri = new ArrayList<>();
            Sheet a = k.getSheet("Ayar değişiklikleri");
            for (int i = 1; i <= a.getLastRowNum(); i++) {
                ayarTurleri.add(satir(a.getRow(i)).get(2));
            }
            assertThat(ayarTurleri).anyMatch(s -> s.startsWith("Ayar değişti")).anyMatch(s -> s.equals("Kural eklendi"));
        }
    }

    @Test
    void formulEnjeksiyonu_hicbirHucreFormulDegil_degerBasindaTekTirnak() throws Exception {
        try (var k = paket()) {
            for (int s = 0; s < k.getNumberOfSheets(); s++) {
                for (Row r : k.getSheetAt(s)) {
                    for (Cell c : r) {
                        assertThat(c.getCellType()).as(k.getSheetName(s) + " hücre " + c.getAddress()).isNotEqualTo(CellType.FORMULA);
                    }
                }
            }
            boolean uyarilida = false;
            for (Row r : k.getSheet("Uyarılı atamalar")) {
                uyarilida |= satir(r).contains("'" + ENJEKSIYON);
            }
            assertThat(uyarilida).isTrue();
            boolean kuralda = false;
            for (Row r : k.getSheet("Kural değerleri")) {
                kuralda |= satir(r).contains("'" + ENJEKSIYON);
            }
            assertThat(kuralda).as("özel kural adı da korunur").isTrue();
        }
    }

    @Test
    void kuralDegerleri_kaynakVeGuvenilirlikle_parametreSiddetAyarOzelKural() throws Exception {
        try (var k = paket()) {
            Sheet s = k.getSheet("Kural değerleri");
            List<List<String>> satirlar = new ArrayList<>();
            for (int i = 1; i <= s.getLastRowNum(); i++) {
                satirlar.add(satir(s.getRow(i)));
            }
            var ev = satirlar.stream().filter(x -> x.get(1).equals("dinlenme.ev_saat")).findFirst().orElseThrow();
            assertThat(ev.get(0)).isEqualTo("Parametre");
            assertThat(ev.get(3)).isEqualTo("10"); // o anki değer
            assertThat(ev.get(5)).isNotBlank(); // kaynak
            assertThat(ev.get(6)).isNotBlank(); // güvenilirlik
            assertThat(satirlar).anyMatch(x -> x.get(0).equals("Kural şiddeti") && x.get(1).equals("K-14"));
            assertThat(satirlar).anyMatch(x -> x.get(0).equals("Uygulama ayarı"));
            assertThat(satirlar).anyMatch(x -> x.get(0).equals("Özel kural"));
        }
    }

    @Test
    void ozetVeBilgiSayfasi_demoEtiketi_uretenVeDonem_ozetSayilariJsonlaAyni() throws Exception {
        String ozet = y.get("/api/rapor/ozet?ay=" + AY).andReturn().getResponse().getContentAsString();
        try (var k = paket()) {
            List<List<String>> bilgi = new ArrayList<>();
            for (Row r : k.getSheet("Rapor bilgisi")) {
                bilgi.add(satir(r));
            }
            assertThat(bilgi.get(1)).containsExactly("UYARI", "DEMO – gerçek değil"); // test makinistleri demo
            assertThat(bilgi).anyMatch(x -> x.get(0).equals("Dönem") && x.get(1).equals(AY.toString()));
            assertThat(bilgi).anyMatch(x -> x.get(0).equals("Üreten") && !x.get(1).isBlank());
            assertThat(bilgi).anyMatch(x -> x.get(0).equals("Standart sapma") && x.get(1).contains("n'e bölünür"));
            List<List<String>> ozetSatirlari = new ArrayList<>();
            for (Row r : k.getSheet("Özet")) {
                ozetSatirlari.add(satir(r));
            }
            int kayit = JsonPath.<Number>read(ozet, "$.degisiklik.kayitSayisi").intValue();
            assertThat(ozetSatirlari).anyMatch(x -> x.get(0).equals("Değişiklik kaydı sayısı") && x.get(1).equals(String.valueOf(kayit)));
            int kirmizi = JsonPath.<Number>read(ozet, "$.kirmizi.atama").intValue();
            assertThat(ozetSatirlari).anyMatch(x -> x.get(0).equals("Kural dışı (kırmızı) atama") && x.get(1).equals(String.valueOf(kirmizi)));
        }
        jdbc.update("UPDATE makinist SET ad_soyad = 'Gerçek Kişi ' || sicil");
        try (var k = paket()) {
            assertThat(satir(k.getSheet("Rapor bilgisi").getRow(1))).doesNotContain("UYARI");
        }
    }
}
