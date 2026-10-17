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
import org.springframework.mock.web.MockHttpServletResponse;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * T12.2: şeffaflık / denetim raporu. Plan örneği: bir günde 3 gecikme + 1 iptal + 2 yerine-ata (makinist değiştirme) = 6 satır; her satırda eski -> yeni, kim,
 * ne zaman, neden; Excel satır sayısı yanıttaki kayıt sayısına eşit; süzgeçler; her kayıt tam bir bölümde.
 */
class DegisiklikRaporTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    Tarayici t;
    String aralik;
    long m1;
    long m2;
    long m3;
    long m4;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m1 = v.makinist("D001");
        m2 = v.makinist("D002");
        m3 = v.makinist("D003");
        m4 = v.makinist("D004");
        long s1 = v.seferEv("T1", GUN, "06:00", "08:00");
        long s2 = v.seferEv("T2", GUN, "09:00", "11:00");
        long s3 = v.seferEv("T3", GUN, "12:00", "14:00");
        long s4 = v.seferEv("T4", GUN, "15:00", "17:00");
        v.atama(s1, m1, "ASIL"); // kurulum SQL ile: rapor kaydı üretmez
        v.atama(s2, m2, "ASIL");
        t = atamaci();
        for (long s : new long[] { s1, s2, s3 }) {
            t.post("/api/sefer/" + s + "/gecikme", "{\"kalkisGecikmeDk\":20,\"neden\":\"GECIKME\"}").andExpect(status().isOk());
        }
        t.post("/api/sefer/" + s4 + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + s1, "{\"makinistId\":" + m3 + ",\"neden\":\"GECIKME\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + s2, "{\"makinistId\":" + m4 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk());
        LocalDate bugun = LocalDate.now(AtamaVeri.ISTANBUL); // kayıt zamanı gerçek saattir
        aralik = "bas=" + bugun.minusDays(1) + "&bit=" + bugun.plusDays(1);
    }

    @Test
    void altiSatir_bolumlerde_eskiYeniKimNeden_excelSatirSayisiEsit() throws Exception {
        t.get("/api/degisiklik?" + aralik).andExpect(status().isOk()).andExpect(jsonPath("$.kayitSayisi").value(6)).andExpect(jsonPath("$.ureten").value("Ata Maci"))
                .andExpect(jsonPath("$.uretimZamani").isNotEmpty()).andExpect(jsonPath("$.filtre.bas").isNotEmpty())
                .andExpect(jsonPath("$.bolumler[?(@.kod=='IPTAL_GECIKME')].sayi").value(4))
                .andExpect(jsonPath("$.bolumler[?(@.kod=='ATAMA_DEGISIKLIKLERI')].sayi").value(2))
                .andExpect(jsonPath("$..satirlar[?(@.sicil=='D003' && @.tur=='ATAMA_DEGISTIR')].eski").value("ASIL:D001"))
                .andExpect(jsonPath("$..satirlar[?(@.sicil=='D003' && @.tur=='ATAMA_DEGISTIR')].yeni").value("ASIL:D003"))
                .andExpect(jsonPath("$..satirlar[?(@.sicil=='D003' && @.tur=='ATAMA_DEGISTIR')].neden").value("GECIKME"))
                .andExpect(jsonPath("$..satirlar[?(@.sicil=='D003' && @.tur=='ATAMA_DEGISTIR')].trenNo").value("T1"))
                .andExpect(jsonPath("$..satirlar[?(@.sicil=='D004' && @.tur=='ATAMA_DEGISTIR')].neden").value("HASTALIK"))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='SEFER_GECIKME')].eski").value(hasSize(3)))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='SEFER_IPTAL')].yeni").value("IPTAL"));

        MockHttpServletResponse excel = t.get("/api/degisiklik/excel?" + aralik).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(excel.getContentType()).contains("spreadsheetml");
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(excel.getContentAsByteArray()))) {
            assertThat(kitap.getSheetAt(0).getLastRowNum()).isEqualTo(6); // başlık + 6
            assertThat(kitap.getSheetAt(1).getRow(2).getCell(1).getStringCellValue()).isEqualTo("Ata Maci"); // bilgi sayfası: üreten
            assertThat(kitap.getSheetAt(1).getRow(8).getCell(1).getNumericCellValue()).isEqualTo(6.0); // kayıt sayısı
        }
    }

    @Test
    void suzgecler_tur_makinist_trenNo_kullanici() throws Exception {
        t.get("/api/degisiklik?" + aralik + "&tur=SEFER_GECIKME").andExpect(jsonPath("$.kayitSayisi").value(3));
        t.get("/api/degisiklik?" + aralik + "&makinistId=" + m3).andExpect(jsonPath("$.kayitSayisi").value(1)).andExpect(jsonPath("$.filtre.makinist").value("D003"));
        t.get("/api/degisiklik?" + aralik + "&trenNo=t1").andExpect(jsonPath("$.kayitSayisi").value(2)); // gecikme + yerine atama
        t.get("/api/degisiklik?" + aralik + "&kullanici=ATA").andExpect(jsonPath("$.kayitSayisi").value(6));
        t.get("/api/degisiklik?" + aralik + "&kullanici=baska").andExpect(jsonPath("$.kayitSayisi").value(0));
        t.get("/api/degisiklik?bas=2020-01-01&bit=2020-01-02").andExpect(jsonPath("$.kayitSayisi").value(0)).andExpect(jsonPath("$.bolumler", hasSize(6)));
    }

    /** 05-review M3 WARNING: süzgeç çıkarılan makinistı ve iptal/gecikmeden o anda etkilenenleri de bulmalı (olay sırası önemli). */
    @Test
    void makinistSuzgeci_cikarilaniVeIptalGecikmedenEtkilenenleriBulur() throws Exception {
        long a = v.makinist("D010");
        long b = v.makinist("D011");
        long sf = v.seferEv("T9", GUN.plusDays(1), "09:00", "11:00");
        t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + a + "}").andExpect(status().isCreated()); // D010 atandı
        t.post("/api/sefer/" + sf + "/gecikme", "{\"kalkisGecikmeDk\":20,\"neden\":\"GECIKME\"}").andExpect(status().isOk()); // etkilenen: D010
        t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + b + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk()); // D010 çıktı, D011 girdi
        t.post("/api/sefer/" + sf + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk()); // etkilenen: D011 (D010 artık seferde değil)

        // D010: kendi ataması + gecikme + çıkarıldığı yerine atama = 3; iptal yok
        t.get("/api/degisiklik?" + aralik + "&makinistId=" + a).andExpect(jsonPath("$.kayitSayisi").value(3))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='SEFER_GECIKME')]").value(hasSize(1))).andExpect(jsonPath("$..satirlar[?(@.tur=='SEFER_IPTAL')]").isEmpty())
                .andExpect(jsonPath("$..satirlar[?(@.tur=='ATAMA_DEGISTIR')].eski").value("ASIL:D010"));
        // D011: yerine atama (yeni) + iptal = 2; gecikme (D011 henüz seferde değildi) yok
        t.get("/api/degisiklik?" + aralik + "&makinistId=" + b).andExpect(jsonPath("$.kayitSayisi").value(2))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='SEFER_IPTAL')]").value(hasSize(1))).andExpect(jsonPath("$..satirlar[?(@.tur=='SEFER_GECIKME')]").isEmpty());
        // kaldırma: çıkarılan makinist süzgeçte bulunur
        long sk = v.seferEv("T10", GUN.plusDays(2), "09:00", "11:00");
        t.post("/api/atama/sefer/" + sk, "{\"makinistId\":" + a + "}").andExpect(status().isCreated());
        t.delete("/api/atama/sefer/" + sk + "?rol=ASIL&neden=HASTALIK").andExpect(status().isNoContent());
        t.get("/api/degisiklik?" + aralik + "&makinistId=" + a + "&tur=ATAMA_KALDIR").andExpect(jsonPath("$.kayitSayisi").value(1));
    }

    /** 05-review INFO-4 / QA BUG-13: otomatik atama satırları ayırt edilir ve "Atamalar" bölümünde toplanır; Excel'de "Otomatik" sütunu. */
    @Test
    void otomatikAtamaSatirlari_ayirtEdilir_atamalarBolumunde_excelSutunu() throws Exception {
        long sf = v.seferEv("OT1", GUN.plusDays(5), "09:00", "11:00");
        t.post("/api/atama/otomatik", "{\"bas\":\"" + GUN.plusDays(5) + "\",\"bit\":\"" + GUN.plusDays(5) + "\",\"uygula\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.atananSayisi").value(1));
        long el = v.seferEv("EL1", GUN.plusDays(6), "09:00", "11:00");
        t.post("/api/atama/sefer/" + el, "{\"makinistId\":" + m4 + "}").andExpect(status().isCreated());

        t.get("/api/degisiklik?" + aralik + "&trenNo=OT1").andExpect(jsonPath("$.kayitSayisi").value(1)).andExpect(jsonPath("$..satirlar[0].otomatik").value(true));
        t.get("/api/degisiklik?" + aralik + "&tur=OTOMATIK_ATAMA").andExpect(jsonPath("$.bolumler[?(@.kod=='ATAMALAR')].sayi").value(1));
        t.get("/api/degisiklik?" + aralik + "&trenNo=EL1").andExpect(jsonPath("$..satirlar[0].otomatik").value(false))
                .andExpect(jsonPath("$.bolumler[?(@.kod=='ATAMALAR')].sayi").value(1));
        t.get("/api/degisiklik?" + aralik + "&tur=ATAMA").andExpect(jsonPath("$.bolumler[?(@.kod=='ATAMALAR')].sayi").value(2)); // OT1 + EL1
        try (var kitap = new XSSFWorkbook(new ByteArrayInputStream(t.get("/api/degisiklik/excel?" + aralik + "&tur=ATAMA").andReturn().getResponse().getContentAsByteArray()))) {
            var baslik = kitap.getSheetAt(0).getRow(0);
            assertThat(baslik.getCell(baslik.getLastCellNum() - 1).getStringCellValue()).isEqualTo("Otomatik");
            assertThat(kitap.getSheetAt(0).getRow(1).getCell(baslik.getLastCellNum() - 1).getStringCellValue()).isEqualTo("Evet"); // OT1 (zaman sırası)
            assertThat(kitap.getSheetAt(0).getRow(2).getCell(baslik.getLastCellNum() - 1).getStringCellValue()).isEmpty(); // EL1: boş
        }
    }

    @Test
    void uyariliAtamaVeParametre_bolumleri_toplamKayitSayisinaEsit() throws Exception {
        long gecmis = v.seferEv("G", LocalDate.of(2026, 10, 9), "08:00", "10:00"); // şimdi 10 Eki 10:00: bitmiş sefer -> K-22 UYARI
        t.post("/api/atama/sefer/" + gecmis, "{\"makinistId\":" + m3 + ",\"gerekce\":\"geçmiş kaydın düzeltilmesi\"}").andExpect(status().isCreated());
        yonetici().put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"10\",\"neden\":\"TİS\"}").andExpect(status().isOk());

        var cevap = t.get("/api/degisiklik?" + aralik).andExpect(status().isOk()).andExpect(jsonPath("$.kayitSayisi").value(8))
                .andExpect(jsonPath("$.bolumler[?(@.kod=='UYARILI_ATAMALAR')].sayi").value(1))
                .andExpect(jsonPath("$..satirlar[?(@.uyarili==true)].uyariGerekcesi").value("geçmiş kaydın düzeltilmesi"))
                .andExpect(jsonPath("$.bolumler[?(@.kod=='PARAMETRE')].sayi").value(1))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='PARAMETRE_DEGISTI')].yeni").value("10"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cevap).contains("\"kayitSayisi\":8");
    }

    /** QA BUG-1: ayar değişikliği satırı ayarın OKUNUR adını taşır (parametre, kural şiddeti, uygulama ayarı); eski-yeni değerler yerinde. */
    @Test
    void ayarDegisikligi_satirdaOkunurAdiGosterilir() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/nobet.asgari_aktarma_dk", "{\"deger\":\"30\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        y.put("/api/kural/K-14/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        y.put("/api/ayar/gordum.kalem_gun", "{\"deger\":10,\"neden\":\"deneme\"}").andExpect(status().isOk());

        t.get("/api/degisiklik?" + aralik + "&tur=PARAMETRE_DEGISTI").andExpect(status().isOk()).andExpect(jsonPath("$.kayitSayisi").value(3))
                .andExpect(jsonPath("$..satirlar[?(@.yeni=='30')].turAdi").value("Ayar değişti: Nöbet içinde asgari aktarma süresi"))
                .andExpect(jsonPath("$..satirlar[?(@.yeni=='ENGEL')].turAdi").value("Kural değişti: K-14 Haftalık görev süresi"))
                .andExpect(jsonPath("$..satirlar[?(@.yeni=='10')].turAdi").value("Ayar değişti: Elinden alınan görev bildiriminin ekranda kalma süresi"));
    }

    /** QA UX-2: V30'un sistem kaydındaki sürüm/iş-kodu notu raporda anlaşılır yazılır (kayıt değişmez). */
    @Test
    void sistemKaydi_surumNotuOkunurYazilir() throws Exception {
        jdbc.update("INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, eski, yeni, neden, neden_notu, ayrinti) VALUES (NULL, 'sistem', 'PARAMETRE_DEGISTI', 'KURAL_SIDDET', 'ENGEL', 'KAPALI', 'DIGER', "
                + "'V30: yeni varsayılan (OK-24)', '{\"anahtar\":\"K-14\"}')");

        t.get("/api/degisiklik?" + aralik + "&tur=PARAMETRE_DEGISTI").andExpect(jsonPath("$..satirlar[0].nedenNotu").value("Sürüm güncellemesi: yeni varsayılan değer"))
                .andExpect(jsonPath("$..satirlar[0].turAdi").value("Kural değişti: K-14 Haftalık görev süresi"));
        assertThat(jdbc.queryForObject("SELECT neden_notu FROM degisiklik_kaydi WHERE kullanici_adi = 'sistem'", String.class)).isEqualTo("V30: yeni varsayılan (OK-24)");
    }

    /** FR-12: kural ekleme/güncelleme kayıtları "Parametre ve kural değişiklikleri" bölümünde, türleri "Kural eklendi/güncellendi", eski -> yeni tam JSON. */
    @Test
    void kuralKayitlari_parametreBolumunde_turAdlariYeniVeEskiJsonlu() throws Exception {
        String govde = "{\"ad\":\"Günlük sınır\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":8,\"siddet\":\"ENGEL\","
                + "\"gerekce\":\"TİS\",\"gecerlilikBas\":\"2026-01-01\"";
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", govde + "}").andExpect(status().isCreated());
        y.put("/api/ozel-kural/1", govde.replace("\"esik\":8", "\"esik\":9") + ",\"versiyon\":0,\"neden\":\"gevşetildi\"}").andExpect(status().isOk());

        t.get("/api/degisiklik?" + aralik + "&tur=KURAL_EKLE").andExpect(status().isOk()).andExpect(jsonPath("$.kayitSayisi").value(1))
                .andExpect(jsonPath("$.bolumler[?(@.kod=='PARAMETRE')].sayi").value(1)).andExpect(jsonPath("$..satirlar[0].turAdi").value("Kural eklendi"));
        t.get("/api/degisiklik?" + aralik + "&tur=KURAL_GUNCELLE").andExpect(jsonPath("$.kayitSayisi").value(1))
                .andExpect(jsonPath("$..satirlar[0].turAdi").value("Kural güncellendi")).andExpect(jsonPath("$..satirlar[0].neden").value("DIGER"))
                .andExpect(jsonPath("$..satirlar[0].nedenNotu").value("gevşetildi"))
                .andExpect(jsonPath("$..satirlar[0].eski").value(org.hamcrest.Matchers.hasItem("Ö-001 Günlük sınır — Eşik 8 s")))
                .andExpect(jsonPath("$..satirlar[0].yeni").value(org.hamcrest.Matchers.hasItem("Eşik 9 s")));
        // Tur 2 (UX-4): ham JSON değil okunur metin
        t.get("/api/degisiklik?" + aralik + "&tur=KURAL_EKLE").andExpect(jsonPath("$..satirlar[0].yeni").value(org.hamcrest.Matchers.hasItem(
                org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("Ö-001 Günlük sınır — Ölçü Toplam görev süresi · Pencere gün · Koşul en fazla · Eşik 8 s"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("{"))))));
    }

    @Test
    void dogrulama_vekimlik() throws Exception {
        t.get("/api/degisiklik?bas=2026-10-13&bit=2026-10-12").andExpect(status().isBadRequest());
        t.get("/api/degisiklik?bas=2020-01-01&bit=2026-10-12").andExpect(status().isBadRequest());
        t.get("/api/degisiklik?bas=2026-10-12").andExpect(status().isBadRequest());
        t.get("/api/degisiklik?" + aralik + "&makinistId=99999").andExpect(status().isNotFound());
        t.get("/api/degisiklik?" + aralik + "&tur=YOK").andExpect(status().isBadRequest());
        new Tarayici(mvc).get("/api/degisiklik?" + aralik).andExpect(status().isUnauthorized());
    }

    /** MK-127: sefer bölme/birleştirme kayıtları okunur ad taşır, "Diğer kayıtlar" bölümünde kalır ve seferde görevli makinistin süzgecinde görünür. */
    @Test
    void seferBolVeBirlestir_adlari_bolumDiger_atamaliMakinistSuzgecindeGorunur() throws Exception {
        long a = v.makinist("D020");
        long b = v.makinist("D021");
        long sf = v.seferEv("TB1", GUN.plusDays(3), "09:00", "11:00");
        t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + a + "}").andExpect(status().isCreated()); // D020 seferde; D021 hiç değil
        for (String[] tur : new String[][] { { "SEFER_BOL", "Sefer bölündü" }, { "SEFER_BIRLESTIR", "Sefer birleştirildi" } }) {
            jdbc.update("INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, varlik_id, sefer_id, eski, yeni) VALUES (NULL, 'sistem', ?, 'SEFER', ?, ?, 'eski', 'yeni')",
                    tur[0], sf, sf);
            t.get("/api/degisiklik?" + aralik + "&tur=" + tur[0]).andExpect(status().isOk()).andExpect(jsonPath("$.kayitSayisi").value(1))
                    .andExpect(jsonPath("$..satirlar[0].turAdi").value(tur[1])).andExpect(jsonPath("$.bolumler[?(@.kod=='DIGER')].sayi").value(1));
            t.get("/api/degisiklik?" + aralik + "&tur=" + tur[0] + "&makinistId=" + a).andExpect(jsonPath("$.kayitSayisi").value(1));
            t.get("/api/degisiklik?" + aralik + "&tur=" + tur[0] + "&makinistId=" + b).andExpect(jsonPath("$.kayitSayisi").value(0));
        }
    }
}
