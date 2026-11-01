package com.makinistatama.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.makinist.TestMakinistExcel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * QA (yeni-ozellikler): mevcut testlerin kapsamadığı KÖTÜ GİRDİ ve SINIR senaryoları. F1 makinist Excel (formül hücresi, boş/sahte/bozuk dosya, Türkçe karakter ve büyük/küçük harf
 * sicil, çok uzun ad, uçtan uca formül enjeksiyonu), F2 profil (bozuk/boş değerli JSON fuzz: hiçbir girdi 5xx vermemeli), F5 çizelge (28/29/31 günlük ay, ay sınırı vardiyası, tatil + hafta sonu).
 */
class YeniOzelliklerKotuGirdiTest extends TestVeritabani {

    static final JsonMapper JSON = new JsonMapper();

    @BeforeEach
    void tanimlar() {
        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.gar(jdbc, "Eskişehir");
        TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        TestTanimlari.aracTipi(jdbc, "YHT");
    }

    // ------------------------------------------------------------------ F1 makinist Excel

    ResultActions yukle(Tarayici t, byte[] dosya, boolean onizle) throws Exception {
        return t.istek(MockMvcRequestBuilders.multipart("/api/makinist/ice-aktar").file(new MockMultipartFile("dosya", "m.xlsx", "application/octet-stream", dosya))
                .param("onizle", Boolean.toString(onizle)));
    }

    int makinistSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class);
    }

    @Test
    void bosDosya_dosyaParcasiYok_sahteIcerik_bozukZip_hepsi400_asla500() throws Exception {
        Tarayici y = yonetici();
        yukle(y, new byte[0], true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        y.istek(MockMvcRequestBuilders.multipart("/api/makinist/ice-aktar").param("onizle", "true")).andExpect(status().isBadRequest()); // "dosya" parçası hiç yok
        yukle(y, "<html>sahte</html>".getBytes(StandardCharsets.UTF_8), true).andExpect(status().isBadRequest());
        yukle(y, new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 }, true).andExpect(status().isBadRequest()); // PNG imzası
        byte[] eskiXls = new byte[512];
        System.arraycopy(new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0 }, 0, eskiXls, 0, 4);
        yukle(y, eskiXls, true).andExpect(status().isBadRequest());
        // zip imzası var ama gövde çöp
        byte[] cop = new byte[2048];
        new Random(7).nextBytes(cop);
        cop[0] = 'P';
        cop[1] = 'K';
        cop[2] = 3;
        cop[3] = 4;
        yukle(y, cop, true).andExpect(status().isBadRequest());
        // geçerli zip ama xlsx değil
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (ZipOutputStream o = new ZipOutputStream(z)) {
            o.putNextEntry(new ZipEntry("merhaba.txt"));
            o.write("x".getBytes());
            o.closeEntry();
        }
        yukle(y, z.toByteArray(), true).andExpect(status().isBadRequest());
        assertThat(makinistSayisi()).isZero();
    }

    @Test
    void besMegabaytUstuDosya_400() throws Exception {
        byte[] buyuk = new byte[5 * 1024 * 1024 + 1];
        buyuk[0] = 'P';
        buyuk[1] = 'K';
        buyuk[2] = 3;
        buyuk[3] = 4;
        yukle(yonetici(), buyuk, true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
    }

    /** Hücre gerçek bir FORMÜL (=HYPERLINK) ise: sunucu formülü ÇALIŞTIRMAZ; ne 500 ne de formül metninin sessizce kayda geçmesi beklenir. */
    @Test
    void formulHucreleri_sicilAdTarih_calistirilmaz_500Yok() throws Exception {
        byte[] dosya;
        try (XSSFWorkbook k = new XSSFWorkbook(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            Sheet s = k.createSheet("Makinistler");
            Row b = s.createRow(0);
            for (int i = 0; i < TestMakinistExcel.MAKINIST_BASLIK.size(); i++) {
                b.createCell(i).setCellValue(TestMakinistExcel.MAKINIST_BASLIK.get(i));
            }
            Row r1 = s.createRow(1); // ad formül
            r1.createCell(0).setCellValue("F001");
            r1.createCell(1).setCellFormula("HYPERLINK(\"http://kotu.example/x\",\"Ali\")");
            r1.createCell(3).setCellValue("Ankara");
            r1.createCell(5).setCellValue("31.12.2030");
            r1.createCell(6).setCellValue("31.12.2030");
            r1.createCell(7).setCellValue("31.12.2030");
            Row r2 = s.createRow(2); // tarih formül (=DATE)
            r2.createCell(0).setCellValue("F002");
            r2.createCell(1).setCellValue("Veli");
            r2.createCell(3).setCellValue("Ankara");
            r2.createCell(5).setCellFormula("DATE(2030,12,31)");
            r2.createCell(6).setCellValue("31.12.2030");
            r2.createCell(7).setCellValue("31.12.2030");
            Row r3 = s.createRow(3); // sicil formül
            r3.createCell(0).setCellFormula("\"X\"&\"1\"");
            r3.createCell(1).setCellValue("Ayşe");
            r3.createCell(3).setCellValue("Ankara");
            r3.createCell(5).setCellValue("31.12.2030");
            r3.createCell(6).setCellValue("31.12.2030");
            r3.createCell(7).setCellValue("31.12.2030");
            k.write(o);
            dosya = o.toByteArray();
        }
        Tarayici y = yonetici();
        MvcResult r = yukle(y, dosya, true).andExpect(status().isOk()).andReturn();
        JsonNode j = JSON.readTree(r.getResponse().getContentAsString());
        // formül hücresi içerik olarak (formül metni) ad/sicil alanına GİRMEMELİ: satır ya hata verir ya da formülsüz değer taşır
        for (JsonNode s : j.get("satirlar")) {
            assertThat(s.get("adSoyad").asString()).as("formül metni ad alanına sızmamalı").doesNotContain("HYPERLINK").doesNotContain("kotu.example");
            assertThat(s.get("sicil").asString()).doesNotContain("&").doesNotContain("\"");
        }
        assertThat(makinistSayisi()).isZero();
    }

    @Test
    void turkceKarakter_kucukBuyukHarfSicil_garAdiTurkceDuyarsiz_ayniDosyadaIkiBicimTekrar() throws Exception {
        List<Object[]> m = new ArrayList<>();
        m.add(TestMakinistExcel.makinist("şen-01", "Şükrü Çığ İnönü", "ESKİŞEHİR")); // sicil küçük harf Türkçe, gar büyük harf
        m.add(TestMakinistExcel.makinist("ŞEN-01", "Başka Kişi", "Ankara")); // aynı sicil büyük harfle -> tekrar
        m.add(TestMakinistExcel.makinist("  ab-1  ", "Boşluklu Sicil", "ankara"));
        m.add(TestMakinistExcel.makinist("AB-1", "Boşluksuz", "Ankara")); // strip sonrası tekrar
        Tarayici y = yonetici();
        JsonNode j = JSON.readTree(yukle(y, TestMakinistExcel.kitap(m, List.of(), List.of()), false).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(j.get("eklenen").asInt()).isEqualTo(2);
        assertThat(j.get("hataSayisi").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT ad_soyad FROM makinist WHERE sicil = 'ŞEN-01'", String.class)).isEqualTo("Şükrü Çığ İnönü");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE sicil = 'AB-1'", Integer.class)).isEqualTo(1);
        // aynı dosya yine: hepsi "zaten var", yeni kayıt yok
        JsonNode ikinci = JSON.readTree(yukle(y, TestMakinistExcel.kitap(m.subList(0, 1), List.of(), List.of()), false).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(ikinci.get("zatenVar").asInt()).isEqualTo(1);
        assertThat(ikinci.get("eklenen").asInt()).isZero();
    }

    @Test
    void adSinirlari_120Kabul_121Hata_yalnizBosluk_hata_sicil20Kabul() throws Exception {
        List<Object[]> m = new ArrayList<>();
        m.add(TestMakinistExcel.makinist("A120", "x".repeat(120), "Ankara"));
        m.add(TestMakinistExcel.makinist("A121", "x".repeat(121), "Ankara"));
        m.add(TestMakinistExcel.makinist("A000", "     ", "Ankara"));
        m.add(TestMakinistExcel.makinist("S".repeat(20), "Yirmi Karakter", "Ankara"));
        m.add(TestMakinistExcel.makinist("S".repeat(21), "Yirmibir Karakter", "Ankara"));
        JsonNode j = JSON.readTree(yukle(yonetici(), TestMakinistExcel.kitap(m, List.of(), List.of()), false).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(j.get("eklenen").asInt()).isEqualTo(2);
        assertThat(j.get("hataSayisi").asInt()).isEqualTo(3);
        assertThat(makinistSayisi()).isEqualTo(2);
    }

    @Test
    void tarihSiniri_31subat_2digitYil_gelecek31yilAsimi_ve_excelSayisalTarih() throws Exception {
        List<Object[]> m = new ArrayList<>();
        m.add(TestMakinistExcel.makinist("T1", "Şubat 31", null, "Ankara", null, "31.02.2030", "31.12.2030", "31.12.2030", null));
        m.add(TestMakinistExcel.makinist("T2", "Çok eski", null, "Ankara", null, "31.12.1999", "31.12.2030", "31.12.2030", null));
        m.add(TestMakinistExcel.makinist("T3", "Çok ileri", null, "Ankara", null, "01.01." + (LocalDate.now().getYear() + 31), "31.12.2030", "31.12.2030", null));
        m.add(TestMakinistExcel.makinist("T4", "Excel tarihi", null, "Ankara", null, LocalDate.of(2031, 5, 5), LocalDate.of(2031, 5, 5), LocalDate.of(2031, 5, 5), LocalDate.of(2026, 1, 1)));
        m.add(TestMakinistExcel.makinist("T5", "ISO tarih", null, "Ankara", null, "2031-05-05", "2031-05-05", "2031-05-05", null));
        JsonNode j = JSON.readTree(yukle(yonetici(), TestMakinistExcel.kitap(m, List.of(), List.of()), true).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(j.get("eklenen").asInt()).as(j.toString()).isEqualTo(2);
        assertThat(j.get("hataSayisi").asInt()).isEqualTo(3);
    }

    @Test
    void ucAdimUcteUca_ad_formulEnjeksiyonuIcerir_yuklenir_ama_hicbirExcelCiktisindaFormulOlmaz() throws Exception {
        String kotu = "=HYPERLINK(\"http://kotu.example\",\"tikla\")";
        Tarayici y = yonetici();
        yukle(y, TestMakinistExcel.kitap(List.<Object[]>of(TestMakinistExcel.makinist("E001", kotu, "Ankara")), List.of(), List.of()), false).andExpect(status().isOk())
                .andExpect(jsonPath("$.eklenen").value(1));
        AtamaVeri v = new AtamaVeri(jdbc);
        long id = jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'E001'", Long.class);
        v.atama(v.seferEv("T1", LocalDate.now(), "08:00", "12:00"), id, "ASIL");
        YearMonth ay = YearMonth.now();
        String[] excelUclari = { "/api/rapor/denetim-paketi/excel?ay=" + ay, "/api/rapor/adalet/excel?ay=" + ay, "/api/rapor/puantaj/excel?ay=" + ay };
        int kontrolEdilen = 0;
        for (String yol : excelUclari) {
            var yanit = y.get(yol).andReturn().getResponse();
            if (yanit.getStatus() != 200) {
                continue; // uç adı bu sürümde farklı olabilir; yalnız 200 dönenler denetlenir
            }
            kontrolEdilen++;
            try (var k = new XSSFWorkbook(new ByteArrayInputStream(yanit.getContentAsByteArray()))) {
                for (Sheet s : k) {
                    for (Row r : s) {
                        for (Cell c : r) {
                            assertThat(c.getCellType()).as(yol + " " + s.getSheetName() + " " + c.getAddress()).isNotEqualTo(CellType.FORMULA);
                            if (c.getCellType() == CellType.STRING) {
                                assertThat(c.getStringCellValue()).as(yol + " " + c.getAddress()).doesNotStartWith("=");
                            }
                        }
                    }
                }
            }
        }
        assertThat(kontrolEdilen).as("en az denetim paketi 200 dönmeli").isGreaterThanOrEqualTo(1);
    }

    @Test
    void turkceBuyukHarfTurVeDurum_sayisalSicil_okunur() throws Exception {
        List<Object[]> m = new ArrayList<>();
        m.add(TestMakinistExcel.makinist("TR1", "Büyük Harf Kondüktör", "KONDÜKTÖR", "ANKARA", "AKTİF", "31.12.2030", "31.12.2030", "31.12.2030", null));
        m.add(TestMakinistExcel.makinist("TR2", "Askıda Kişi", "makinist", "Eskişehir", "ASKIDA", "31.12.2030", "31.12.2030", "31.12.2030", null));
        m.add(TestMakinistExcel.makinist("TR3", "Pasif Kişi", "Makinist", "Ankara", "PASİF", "31.12.2030", "31.12.2030", "31.12.2030", null));
        m.add(TestMakinistExcel.makinist("12345", "Sayısal Sicil", "Ankara")); // metin hücresi "12345"
        JsonNode j = JSON.readTree(yukle(yonetici(), TestMakinistExcel.kitap(m, List.of(), List.of()), false).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(j.get("hataSayisi").asInt()).as(j.get("hatalar").toString()).isZero();
        assertThat(jdbc.queryForObject("SELECT tur FROM makinist WHERE sicil = 'TR1'", String.class)).isEqualTo("KONDUKTOR");
        assertThat(jdbc.queryForObject("SELECT durum FROM makinist WHERE sicil = 'TR2'", String.class)).isEqualTo("ASKIDA");
        assertThat(jdbc.queryForObject("SELECT durum FROM makinist WHERE sicil = 'TR3'", String.class)).isEqualTo("PASIF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE sicil = '12345'", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ F2 profil: bozuk girdi fuzz

    @Test
    void profilOnizleVeUygula_bozukBicimliGovdeler_hicbiri5xxVermez_hicbiriYazmaz() throws Exception {
        Tarayici y = yonetici();
        String temel = "\"format\":\"makinist-atama-profil\",\"profilSurumu\":1";
        List<String> govdeler = List.of(
                "null", "[]", "{}", "\"metin\"", "123", "true", "{\"format\":null}", "{\"format\":123}", "{\"format\":\"makinist-atama-profil\"}",
                "{" + temel + ",\"profilSurumu\":\"bir\"}",
                "{" + temel + ",\"parametreler\":\"x\"}", "{" + temel + ",\"parametreler\":[1,2]}",
                "{" + temel + ",\"parametreler\":{\"k13.max\":null}}", "{" + temel + ",\"parametreler\":{\"k13.max\":{}}}", "{" + temel + ",\"parametreler\":{\"k13.max\":[1]}}",
                "{" + temel + ",\"parametreler\":{\"\":\"1\"}}", "{" + temel + ",\"parametreler\":{\"" + "a".repeat(5000) + "\":\"1\"}}",
                "{" + temel + ",\"siddetler\":{\"K-13\":null}}", "{" + temel + ",\"siddetler\":{\"K-13\":\"YOK_BOYLE\"}}",
                "{" + temel + ",\"ayarlar\":{\"marj.aktif\":null}}", "{" + temel + ",\"ayarlar\":{\"marj.aktif\":\"abc\"}}", "{" + temel + ",\"ayarlar\":{\"marj.aktif\":1e999}}",
                "{" + temel + ",\"ayarlar\":{\"marj.aktif\":-1}}", "{" + temel + ",\"ayarlar\":{\"marj.aktif\":99999999999999999999}}",
                "{" + temel + ",\"ozelKurallar\":[null]}", "{" + temel + ",\"ozelKurallar\":[{}]}", "{" + temel + ",\"ozelKurallar\":[{\"ad\":null}]}",
                "{" + temel + ",\"ozelKurallar\":[{\"ad\":\"a\",\"esik\":\"x\"}]}", "{" + temel + ",\"ozelKurallar\":[{\"ad\":\"a\",\"kapsam\":{\"hatlar\":[null]}}]}",
                "{" + temel + ",\"ozelKurallar\":[{\"ad\":\"a\",\"olcu\":\"YOK\",\"pencere\":\"YOK\",\"karsilastirma\":\"YOK\",\"esik\":1,\"siddet\":\"YOK\",\"gerekce\":\"g\"}]}",
                "{" + temel + ",\"ozelKurallar\":[{\"ad\":\"a\",\"gecerlilikBas\":\"31.02.2026\"}]}",
                "{" + temel + "}{" + temel + "}", "﻿{" + temel + "}", "{" + temel + ",\"parametreler\":{\"k13.max\":\"1\",\"k13.max\":\"2\"}}",
                "[".repeat(5000), "{\"a\":".repeat(3000));
        int once = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);
        List<String> bozuk = new ArrayList<>();
        for (String g : govdeler) {
            for (String yol : new String[] { "/api/profil/onizle", "/api/profil/uygula" }) {
                String gonder = yol.endsWith("uygula") ? "{\"paket\":" + g + ",\"secilen\":[null,\"\",\"PARAMETRE:yok\"],\"neden\":\"qa\"}" : g;
                int kod = y.post(yol, gonder).andReturn().getResponse().getStatus();
                if (kod >= 500) {
                    bozuk.add(kod + " " + yol + " <- " + (g.length() > 80 ? g.substring(0, 80) + "..." : g));
                }
            }
        }
        // ham govdeler (paket sarmalayıcısız uygula) ve secilen=null / neden 5000 karakter
        for (String g : List.of("null", "[]", "{\"paket\":null}", "{\"secilen\":null}", "{\"paket\":{" + temel + "},\"secilen\":[null],\"neden\":null}",
                "{\"paket\":{" + temel + "},\"secilen\":\"x\",\"neden\":\"qa\"}", "{\"paket\":{" + temel + "},\"neden\":\"" + "n".repeat(5000) + "\"}")) {
            int kod = y.post("/api/profil/uygula", g).andReturn().getResponse().getStatus();
            if (kod >= 500) {
                bozuk.add(kod + " uygula(ham) <- " + (g.length() > 80 ? g.substring(0, 80) + "..." : g));
            }
        }
        assertThat(bozuk).as("5xx veren bozuk profil girdileri").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).as("bozuk girdi yazmaz").isEqualTo(once);
    }

    @Test
    void profil_yanlisIcerikTuruVeBosGovde_5xxYok() throws Exception {
        Tarayici y = yonetici();
        for (String yol : new String[] { "/api/profil/onizle", "/api/profil/uygula" }) {
            int kod = y.istek(MockMvcRequestBuilders.post(yol).contentType("text/plain").content("merhaba")).andReturn().getResponse().getStatus();
            assertThat(kod).as(yol + " text/plain").isLessThan(500);
            kod = y.istek(MockMvcRequestBuilders.post(yol).contentType("application/json").content(new byte[0])).andReturn().getResponse().getStatus();
            assertThat(kod).as(yol + " boş").isEqualTo(400);
        }
    }

    // ------------------------------------------------------------------ F5 çizelge: takvim sınırları

    private JsonNode cizelge(Tarayici t, String sorgu) throws Exception {
        return JSON.readTree(t.get("/api/rapor/cizelge?" + sorgu).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void cizelge_28_29_31_gunluAylar_sonGunSeferiHucrede_ayAsanVardiyaIlkAydaKalir() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long d1 = v.makinist("D001");
        v.atama(v.seferEv("S28", LocalDate.of(2026, 2, 28), "22:00", "06:00"), d1, "ASIL"); // Şubat 2026 son günü, ertesi güne sarkar
        v.atama(v.seferEv("S29", LocalDate.of(2024, 2, 29), "09:00", "13:00"), d1, "ASIL"); // artık yıl
        v.atama(v.seferEv("S31", LocalDate.of(2026, 12, 31), "08:00", "09:00"), d1, "ASIL");
        Tarayici t = atamaci();
        JsonNode sub26 = cizelge(t, "ay=2026-02");
        assertThat(sub26.get("gunSayisi").asInt()).isEqualTo(28);
        assertThat(gun(sub26, "D001", 28)).isNotNull();
        assertThat(gun(sub26, "D001", 28).get("gorevler").get(0).get("trenNo").asString()).isEqualTo("S28");
        JsonNode mart26 = cizelge(t, "ay=2026-03");
        assertThat(gun(mart26, "D001", 1)).as("ay sonu vardiyası ertesi ayın 1'inde hücre OLMAZ").isNull();
        JsonNode sub24 = cizelge(t, "ay=2024-02");
        assertThat(sub24.get("gunSayisi").asInt()).isEqualTo(29);
        assertThat(gun(sub24, "D001", 29).get("gorevler").get(0).get("trenNo").asString()).isEqualTo("S29");
        JsonNode ara26 = cizelge(t, "ay=2026-12");
        assertThat(ara26.get("gunSayisi").asInt()).isEqualTo(31);
        assertThat(gun(ara26, "D001", 31).get("gorevler").get(0).get("trenNo").asString()).isEqualTo("S31");
        // hücre gün numaraları her zaman 1..gunSayisi aralığında
        for (JsonNode c : List.of(sub26, sub24, ara26)) {
            for (JsonNode s : c.get("satirlar")) {
                for (JsonNode h : s.get("hucreler")) {
                    assertThat(h.get("gun").asInt()).isBetween(1, c.get("gunSayisi").asInt());
                }
            }
        }
    }

    private static JsonNode gun(JsonNode c, String sicil, int gun) {
        for (JsonNode s : c.get("satirlar")) {
            if (sicil.equals(s.get("sicil").asString())) {
                for (JsonNode h : s.get("hucreler")) {
                    if (h.get("gun").asInt() == gun) {
                        return h;
                    }
                }
            }
        }
        return null;
    }

    @Test
    void cizelge_resmiTatil_haftaSonuyla_cakisir_ayinIlkVeSonGunu_yalnizGosterim() throws Exception {
        Tarayici y = yonetici();
        // 2026-03-21 Cumartesi, 2026-03-22 Pazar, 2026-03-01 Pazar (ayın ilk günü), 2026-03-31 Salı (son gün)
        for (String t : new String[] { "2026-03-21", "2026-03-22", "2026-03-01", "2026-03-31" }) {
            y.post("/api/resmi-tatil", "{\"tarih\":\"" + t + "\",\"ad\":\"Tatil " + t + "\"}").andExpect(status().isCreated());
        }
        AtamaVeri v = new AtamaVeri(jdbc);
        long d1 = v.makinist("D001");
        v.atama(v.seferEv("T1", LocalDate.of(2026, 3, 21), "08:00", "12:00"), d1, "ASIL"); // tatil + hafta sonu günü görev
        JsonNode c = cizelge(atamaci(), "ay=2026-03");
        List<Integer> gunler = new ArrayList<>();
        c.get("tatiller").forEach(t -> gunler.add(t.get("gun").asInt()));
        assertThat(gunler).containsExactly(1, 21, 22, 31);
        assertThat(gun(c, "D001", 21).get("gorevler")).hasSize(1);
        assertThat(cizelge(atamaci(), "ay=2026-02").get("tatiller")).as("komşu aya tatil sızmaz").isEmpty();
        assertThat(cizelge(atamaci(), "ay=2026-04").get("tatiller")).isEmpty();
    }

    @Test
    void cizelge_ayParametresiBozuk_400_garYok_bos200_makinistTuruKonduktorGrubu() throws Exception {
        Tarayici t = atamaci();
        for (String ay : new String[] { "2026-1", "2026/10", "202610", "ekim", "2026-00", "2026-13", "%20", "" }) {
            assertThat(t.get("/api/rapor/cizelge?ay=" + ay).andReturn().getResponse().getStatus()).as("ay=" + ay).isEqualTo(400);
        }
        assertThat(cizelge(t, "ay=2026-10&garId=999999").get("satirlar")).isEmpty();
        t.get("/api/rapor/cizelge?ay=2026-10&garId=abc").andExpect(status().isBadRequest());
        t.get("/api/rapor/cizelge?ay=2026-10&tur=ROBOT").andExpect(status().isBadRequest());
        YearMonth bu = YearMonth.now();
        t.get("/api/rapor/cizelge?ay=" + bu.minusMonths(36)).andExpect(status().isOk());
        t.get("/api/rapor/cizelge?ay=" + bu.minusMonths(37)).andExpect(status().isBadRequest());
        t.get("/api/rapor/cizelge?ay=" + bu.plusMonths(12)).andExpect(status().isOk());
        t.get("/api/rapor/cizelge?ay=" + bu.plusMonths(13)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ F1 kurum: logo / ad uçları kötü girdi

    @Test
    void kurumAdi_cokUzunBosHtmlVeKontrolKarakteri_asla500() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kurum", "{\"ad\":\"" + "x".repeat(121) + "\"}").andExpect(status().isBadRequest());
        y.put("/api/kurum", "{\"ad\":\"x\"}").andExpect(status().isOk());
        y.put("/api/kurum", "{}").andExpect(status().is4xxClientError());
        y.put("/api/kurum", "[]").andExpect(status().is4xxClientError());
        y.put("/api/kurum", "{\"ad\":null}").andExpect(result -> assertThat(result.getResponse().getStatus()).isLessThan(500));
        y.put("/api/kurum", "{\"ad\":\"<script>alert(1)</script>\"}").andExpect(status().isOk());
        String acik = new Tarayici(mvc).get("/api/kurum/acik").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(acik).as("ad kaçışlı JSON olarak döner (HTML değil)").contains("<script>alert(1)</script>");
        assertThat(new Tarayici(mvc).get("/api/kurum/acik").andReturn().getResponse().getContentType()).contains("application/json");
        y.put("/api/kurum", "{\"ad\":\"a\\u0000b\"}").andExpect(result -> assertThat(result.getResponse().getStatus()).isLessThan(500));
        y.put("/api/kurum", "{\"ad\":\"bayrak\\u202Ekotu\"}").andExpect(result -> assertThat(result.getResponse().getStatus()).isLessThan(500));
    }
}
