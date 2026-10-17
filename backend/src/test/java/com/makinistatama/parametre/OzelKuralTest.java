package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.Siddet;

/** T2.1-T2.3: özel kural saklama, doğrulama, uçlar, sürüm ve denetim kaydı (FR-01, FR-04, FR-08, FR-12, NFR-04). */
class OzelKuralTest extends TestVeritabani {

    @Autowired
    ParametreServisi parametreler;

    static String govde(String ad, String olcu, String pencere, String esik, String kapsam, String siddet, String ek) {
        return "{\"ad\":\"" + ad + "\",\"olcu\":\"" + olcu + "\",\"pencere\":\"" + pencere + "\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":" + esik
                + (kapsam == null ? "" : ",\"kapsam\":" + kapsam) + ",\"siddet\":\"" + siddet + "\",\"gerekce\":\"TİS ek protokol md. 4\",\"gecerlilikBas\":\"2026-01-01\"" + ek + "}";
    }

    static String gunluk8(String ad) {
        return govde(ad, "TOPLAM_GOREV_SURESI", "GUN", "8", null, "ENGEL", "");
    }

    private void satirEkle(String ad, String siddet) {
        jdbc.update("""
                INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan)
                VALUES (?, 'TOPLAM_GOREV_SURESI', 'GUN', 'EN_FAZLA', 8, '{}', ?, '2026-01-01', 'deneme', 'test')""", ad, siddet);
    }

    // ------------------------------------------------------------------ migrasyon

    @Test
    void migrasyon_tablo_CHECKler_ve_benzersiz_ad() {
        satirEkle("Aa", "ENGEL");
        assertThatThrownBy(() -> satirEkle("AA", "ENGEL")).isInstanceOf(DataIntegrityViolationException.class); // lower(ad) benzersiz
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan)
                VALUES ('x', 'BILINMEYEN', 'GUN', 'EN_FAZLA', 8, '{}', 'ENGEL', '2026-01-01', 'g', 't')""")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gecerlilik_bit, gerekce, olusturan)
                VALUES ('y', 'GOREV_SAYISI', 'GUN', 'EN_FAZLA', 8, '{}', 'ENGEL', '2026-02-01', '2026-01-01', 'g', 't')""")).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ ekleme ve doğrulama

    @Test
    void ekle_201_kodOzetDenetimKaydi() throws Exception {
        long hat = TestTanimlari.hat(jdbc, "Ankara–Sivas");
        String kapsam = "{\"hatlar\":[" + hat + "],\"vardiya\":\"GECE\"}";
        yonetici().post("/api/ozel-kural", govde("Sivas gece 6 s", "TOPLAM_GOREV_SURESI", "GUN", "6", kapsam, "UYARI", "")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.kod").value("Ö-001")).andExpect(jsonPath("$.versiyon").value(0))
                .andExpect(jsonPath("$.esik").value(6)).andExpect(jsonPath("$.birim").value("s")).andExpect(jsonPath("$.siddet").value("UYARI"))
                .andExpect(jsonPath("$.ozet").value("Toplam görev süresi (gün) en fazla 6 s · Hat: Ankara–Sivas · Vardiya: gece"))
                .andExpect(jsonPath("$.olusturan").value("Yöne Tici"));

        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'KURAL_EKLE'");
        assertThat(kayit).containsEntry("varlik_tipi", "OZEL_KURAL").containsEntry("varlik_id", 1L).containsEntry("kullanici_adi", "Yöne Tici")
                .containsEntry("neden", "DIGER").containsEntry("neden_notu", "TİS ek protokol md. 4");
        assertThat((String) kayit.get("yeni")).contains("\"ad\":\"Sivas gece 6 s\"").contains("\"esik\":6").contains("\"vardiya\":\"GECE\"").contains("\"siddet\":\"UYARI\"");
        assertThat(kayit.get("eski")).isNull();
        assertThat(parametreler.yukle().ozelKural("Ö-001").kapsam().hatlar()).containsExactly(hat);
    }

    @Test
    void izinsizBirlesim_esikSiniri_gerekce_400_alanlarla() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", govde("a", "TOPLAM_GOREV_SURESI", "SEFER", "8", null, "ENGEL", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.pencere").exists());
        y.post("/api/ozel-kural", govde("a", "GOREV_SAYISI", "GUN", "3", null, "ENGEL", "").replace("EN_FAZLA", "EN_AZ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.karsilastirma").exists());
        y.post("/api/ozel-kural", govde("a", "TOPLAM_GOREV_SURESI", "GUN", "25", null, "ENGEL", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.esik").exists());
        y.post("/api/ozel-kural", govde("a", "TOPLAM_GOREV_SURESI", "GUN", "0", null, "ENGEL", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.esik").exists());
        y.post("/api/ozel-kural", govde("a", "TEK_SEFER_SURESI", "SEFER", "8.01", null, "ENGEL", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.esik").exists());
        y.post("/api/ozel-kural", gunluk8("a").replace("\"gerekce\":\"TİS ek protokol md. 4\"", "\"gerekce\":\"  \"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.gerekce").exists());
        y.post("/api/ozel-kural", gunluk8("a").replace("\"gecerlilikBas\":\"2026-01-01\"", "\"gecerlilikBas\":\"2026-03-01\",\"gecerlilikBit\":\"2026-02-01\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gecerlilikBit").exists());
        y.post("/api/ozel-kural", gunluk8("a").replace("\"olcu\":\"TOPLAM_GOREV_SURESI\"", "\"olcu\":\"YOK\"")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur LIKE 'KURAL_%'", Integer.class)).isZero();
    }

    @Test
    void adTekrari_409_turkceKatlamali() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", gunluk8("Günlük sınır")).andExpect(status().isCreated());
        y.post("/api/ozel-kural", gunluk8("GÜNLÜK SINIR")).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("KURAL_AD_TEKRARI"));
        y.post("/api/ozel-kural", gunluk8("gunluk  sinir")).andExpect(status().isConflict());
        y.post("/api/ozel-kural", gunluk8("Başka")).andExpect(status().isCreated()).andExpect(jsonPath("$.kod").value("Ö-002"));
    }

    @Test
    void kapsam_kimlikleri_varlik_ve_tip_denetimi() throws Exception {
        long hat = TestTanimlari.hat(jdbc, "Ankara–Sivas");
        long gar = TestTanimlari.gar(jdbc, "Ankara");
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", govde("a", "TOPLAM_GOREV_SURESI", "GUN", "8", "{\"hatlar\":[9999]}", "ENGEL", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.kapsam").exists());
        y.post("/api/ozel-kural", govde("a", "TOPLAM_GOREV_SURESI", "GUN", "8", "{\"hatlar\":[" + gar + "]}", "ENGEL", "")).andExpect(status().isBadRequest()); // gar, hat değil
        y.post("/api/ozel-kural", govde("a", "TOPLAM_GOREV_SURESI", "GUN", "8", "{\"kalkisGarlari\":[" + gar + "],\"hatlar\":[null]}", "ENGEL", "")).andExpect(status().isCreated());
        y.post("/api/ozel-kural", govde("b", "TOPLAM_GOREV_SURESI", "GUN", "8", "{\"kalkisGarlari\":[" + hat + "]}", "ENGEL", "")).andExpect(status().isBadRequest()); // hat, gar değil
        y.post("/api/ozel-kural", govde("c", "TOPLAM_GOREV_SURESI", "GUN", "8", "{\"trenTurleri\":[\"YUK\"]}", "ENGEL", "")).andExpect(status().isCreated()); // K2
        y.post("/api/ozel-kural", govde("d", "TOPLAM_GOREV_SURESI", "GUN", "8", "{\"trenTurleri\":[\"BUHARLI\"]}", "ENGEL", "")).andExpect(status().isBadRequest());
    }

    @Test
    void enCok50AcikKural_kapaliOlanlarSayilmaz() throws Exception {
        for (int i = 0; i < 50; i++) {
            satirEkle("Kural " + i, "ENGEL");
        }
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", gunluk8("Elli bir")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.siddet").exists());
        y.post("/api/ozel-kural", govde("Kapalı olan", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "KAPALI", "")).andExpect(status().isCreated());
        // bir kuralı kapat, yenisi eklenebilir
        y.put("/api/ozel-kural/1", govde("Kural 0", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "KAPALI", ",\"versiyon\":0,\"neden\":\"kapatıldı\"")).andExpect(status().isOk());
        y.post("/api/ozel-kural", gunluk8("Elli bir")).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ yetki

    @Test
    void yetki_atamaciOkurYazamaz_anonim401() throws Exception {
        satirEkle("Var", "ENGEL");
        Tarayici a = atamaci();
        a.get("/api/ozel-kural").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        a.get("/api/ozel-kural/katalog").andExpect(status().isOk());
        a.post("/api/ozel-kural", gunluk8("Yeni")).andExpect(status().isForbidden());
        a.put("/api/ozel-kural/1", gunluk8("Var").replace("}", ",\"versiyon\":0,\"neden\":\"x\"}")).andExpect(status().isForbidden());
        a.post("/api/ozel-kural/dene", gunluk8("Yeni")).andExpect(status().isForbidden());
        Tarayici anonim = new Tarayici(mvc);
        anonim.get("/api/ozel-kural").andExpect(status().isUnauthorized());
        anonim.post("/api/ozel-kural", gunluk8("Yeni")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ düzenleme, kapatma, sürüm

    @Test
    void guncelle_versiyonArtar_eskiYeniJsonKayitta_yeniDegerSonrakiYuklemedeGorunur() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", gunluk8("Günlük sınır")).andExpect(status().isCreated());

        y.put("/api/ozel-kural/1", govde("Günlük sınır", "TOPLAM_GOREV_SURESI", "GUN", "9", null, "ENGEL", ",\"versiyon\":0,\"neden\":\"eşik 9 s\"")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versiyon").value(1)).andExpect(jsonPath("$.esik").value(9));

        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'KURAL_GUNCELLE'");
        assertThat((String) kayit.get("eski")).contains("\"esik\":8").contains("\"versiyon\":0");
        assertThat((String) kayit.get("yeni")).contains("\"esik\":9").contains("\"versiyon\":1");
        assertThat(kayit).containsEntry("neden_notu", "eşik 9 s").containsEntry("varlik_id", 1L);
        assertThat(parametreler.yukle().ozelKural("Ö-001").esik()).isEqualByComparingTo("9");
    }

    @Test
    void guncelle_eskiVersiyon409_nedenZorunlu_degisiklikYoksaKayitYok() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", gunluk8("Günlük sınır")).andExpect(status().isCreated());
        String ayni = "TOPLAM_GOREV_SURESI";

        y.put("/api/ozel-kural/1", govde("Günlük sınır", ayni, "GUN", "9", null, "ENGEL", ",\"versiyon\":5,\"neden\":\"x\"")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        y.put("/api/ozel-kural/1", govde("Günlük sınır", ayni, "GUN", "9", null, "ENGEL", ",\"versiyon\":0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.neden").exists());
        y.put("/api/ozel-kural/1", govde("Günlük sınır", ayni, "GUN", "9", null, "ENGEL", ",\"neden\":\"x\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.versiyon").exists());
        y.put("/api/ozel-kural/99", govde("Günlük sınır", ayni, "GUN", "9", null, "ENGEL", ",\"versiyon\":0,\"neden\":\"x\"")).andExpect(status().isNotFound());

        // aynı içerik: sürüm artmaz, kayıt yazılmaz
        y.put("/api/ozel-kural/1", gunluk8("Günlük sınır").replace("}", ",\"versiyon\":0,\"neden\":\"x\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.versiyon").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'KURAL_GUNCELLE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT versiyon FROM ozel_kural", Long.class)).isZero();
    }

    @Test
    void kapat_siddetKAPALI_motordanCikar_silmeYok() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", gunluk8("Günlük sınır")).andExpect(status().isCreated());
        assertThat(parametreler.yukle().ozelKurallar()).hasSize(1);

        y.put("/api/ozel-kural/1", govde("Günlük sınır", "TOPLAM_GOREV_SURESI", "GUN", "8", null, "KAPALI", ",\"versiyon\":0,\"neden\":\"geçici kapatıldı\"")).andExpect(status().isOk())
                .andExpect(jsonPath("$.siddet").value("KAPALI"));

        assertThat(parametreler.yukle().ozelKurallar()).isEmpty();
        y.get("/api/ozel-kural").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].siddet").value("KAPALI")); // liste kapalıları da gösterir
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural", Integer.class)).isEqualTo(1);
        assertThat(parametreler.yukle().siddet("K-13")).isEqualTo(Siddet.ENGEL); // yerleşikler etkilenmez
    }

    @Test
    void yerlesikKuralKodlariAyni_ozelKodOzelKuralaAit() throws Exception {
        yonetici().post("/api/ozel-kural", gunluk8("A")).andExpect(status().isCreated());
        assertThat(parametreler.yukle().siddet("Ö-001")).isEqualTo(Siddet.ENGEL);
        assertThat(parametreler.yukle().siddetler()).doesNotContainKey("Ö-001"); // yerleşik şiddet tablosunda değil
    }

    // ------------------------------------------------------------------ katalog

    @Test
    void katalog_olcuEnumundanGelir_izinsizBirlesimYok() throws Exception {
        atamaci().get("/api/ozel-kural/katalog").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].aciklama").isNotEmpty()).andExpect(jsonPath("$[0].kod").value("TOPLAM_GOREV_SURESI")).andExpect(jsonPath("$[0].ad").value("Toplam görev süresi"))
                .andExpect(jsonPath("$[0].birim").value("s")).andExpect(jsonPath("$[0].pencereler[*].kod", org.hamcrest.Matchers.contains("NOBET", "GUN", "HAFTA", "AY", "YIL", "KAYAN")))
                .andExpect(jsonPath("$[0].pencereler[1].esikUst").value(24)).andExpect(jsonPath("$[0].karsilastirmalar[*].kod", org.hamcrest.Matchers.contains("EN_FAZLA")))
                .andExpect(jsonPath("$[2].kod").value("TEK_SEFER_SURESI")).andExpect(jsonPath("$[2].pencereler", hasSize(1)))
                .andExpect(jsonPath("$[3].birim").value("adet")).andExpect(jsonPath("$[3].pencereler[0].esikAltDahil").value(true))
                .andExpect(jsonPath("$[3].pencereler[0].esikAlt").value(1)) // görev sayısı en az 1
                .andExpect(jsonPath("$[8].kod").value("IZIN_GUNU")).andExpect(jsonPath("$[8].karsilastirmalar[*].kod", org.hamcrest.Matchers.contains("EN_AZ")))
                .andExpect(jsonPath("$[8].pencereler[?(@.kod=='KAYAN')].aciklama").isNotEmpty());
    }

    // ------------------------------------------------------------------ Tur 2: PUT'ta boş geçerlilik başlangıcı, eşzamanlı 50 sınırı

    @Test
    void guncelle_gecerlilikBasGonderilmezse_mevcutDegerKorunur() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", gunluk8("Günlük sınır")).andExpect(status().isCreated()); // gecerlilikBas 2026-01-01
        String basiz = govde("Günlük sınır", "TOPLAM_GOREV_SURESI", "GUN", "9", null, "ENGEL", ",\"versiyon\":0,\"neden\":\"eşik 9 s\"").replace(",\"gecerlilikBas\":\"2026-01-01\"", "");
        y.put("/api/ozel-kural/1", basiz).andExpect(status().isOk()).andExpect(jsonPath("$.gecerlilikBas").value("2026-01-01"));
        assertThat(jdbc.queryForObject("SELECT gecerlilik_bas::text FROM ozel_kural", String.class)).isEqualTo("2026-01-01");
    }

    @Test
    void enCok50AcikKural_eszamanliYazimdaAsilmaz() throws Exception {
        for (int i = 0; i < 46; i++) {
            satirEkle("Kural " + i, "ENGEL");
        }
        Tarayici y = yonetici();
        var havuz = java.util.concurrent.Executors.newFixedThreadPool(8);
        var baslat = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> sonuc = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String ad = "Eşzamanlı " + i;
            sonuc.add(havuz.submit(() -> {
                baslat.await();
                return y.post("/api/ozel-kural", gunluk8(ad)).andReturn().getResponse().getStatus();
            }));
        }
        baslat.countDown();
        int basarili = 0;
        for (var f : sonuc) {
            if (f.get() == 201) {
                basarili++;
            }
        }
        havuz.shutdown();
        assertThat(basarili).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural WHERE siddet <> 'KAPALI'", Integer.class)).isEqualTo(50);
    }
}
