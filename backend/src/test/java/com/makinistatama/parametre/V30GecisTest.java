package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.makinistatama.TestVeritabani;

/**
 * MK-99 / S-2: V30 yeni varsayılanlara "değiştirilmemiş" satırlarda geçer. Gerçek Flyway, aynı kapta AYRI şemada: önce hedef 29, durum kurulur, sonra 30.
 * Değiştirilmemiş = değer eski tohumla aynı VE demo dışı PARAMETRE_DEGISTI kaydı yok. {@code varsayilan} her durumda yeni değerdir.
 */
class V30GecisTest extends TestVeritabani {

    private static final String NOT = "V30: yeni varsayılan (OK-24)";

    @Value("${spring.datasource.url}")
    String url;
    @Value("${spring.datasource.username}")
    String kullanici;
    @Value("${spring.datasource.password}")
    String parola;

    private String sema;
    private JdbcTemplate db;

    /** Şemayı kurar, V29'a kadar göç eder. */
    private void kur(String ad) {
        sema = ad;
        flyway("29").migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + sema, kullanici, parola);
        db = new JdbcTemplate(ds);
    }

    private Flyway flyway(String hedef) {
        return Flyway.configure().dataSource(url, kullanici, parola).schemas(sema).defaultSchema(sema).createSchemas(true).locations("classpath:db/migration").target(hedef).load();
    }

    @AfterEach
    void sil() {
        if (sema != null) {
            jdbc.execute("DROP SCHEMA IF EXISTS " + sema + " CASCADE");
        }
    }

    private void kayit(String varlikTipi, String anahtar, String eski, String yeni, String kim, String notu) {
        db.update("INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, eski, yeni, neden, neden_notu, ayrinti) VALUES (NULL, ?, 'PARAMETRE_DEGISTI', ?, ?, ?, 'DIGER', ?, ?)",
                kim, varlikTipi, eski, yeni, notu, "{\"anahtar\":\"" + anahtar + "\"}");
    }

    private String siddet(String kod) {
        return db.queryForObject("SELECT siddet FROM kural_siddet WHERE kod = ?", String.class, kod);
    }

    private String deger(String anahtar) {
        return db.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = ?", String.class, anahtar);
    }

    private List<Map<String, Object>> sistemKayitlari() {
        return db.queryForList("SELECT ayrinti, eski, yeni, neden_notu, kullanici_adi FROM degisiklik_kaydi WHERE kullanici_adi = 'sistem' ORDER BY id");
    }

    private void varsayilanlarYeni() {
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_siddet WHERE kod = 'K-14'", String.class)).isEqualTo("KAPALI");
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_siddet WHERE kod = 'K-23'", String.class)).isEqualTo("ENGEL");
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_parametre WHERE anahtar = 'nobet.asgari_aktarma_dk'", String.class)).isEqualTo("20");
    }

    @Test
    void taze_ucDegerCevrilir_ucSistemKaydi_varsayilanYeni() {
        kur("v30_taze");
        assertThat(siddet("K-14")).isEqualTo("ENGEL"); // V29 sonrası hâlâ eski
        flyway("30").migrate();

        assertThat(siddet("K-14")).isEqualTo("KAPALI");
        assertThat(siddet("K-23")).isEqualTo("ENGEL");
        assertThat(deger("nobet.asgari_aktarma_dk")).isEqualTo("20");
        assertThat(deger("haftalik.max_saat")).isEqualTo("45");
        assertThat(sistemKayitlari()).hasSize(3).allSatisfy(k -> assertThat(k.get("neden_notu")).isEqualTo(NOT));
        varsayilanlarYeni();
    }

    @Test
    void yoneticiK23UyariVeEsik30_dokunulmaz_amaVarsayilanYeni() {
        kur("v30_yonetici");
        db.update("UPDATE kural_siddet SET siddet = 'UYARI' WHERE kod = 'K-23'");
        db.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'nobet.asgari_aktarma_dk'");
        kayit("KURAL_SIDDET", "K-23", "KAPALI", "UYARI", "Yöne Tici", "ekip dönüşü");
        kayit("PARAMETRE", "nobet.asgari_aktarma_dk", "0", "30", "Yöne Tici", "ekip dönüşü");
        flyway("30").migrate();

        assertThat(siddet("K-23")).isEqualTo("UYARI");
        assertThat(deger("nobet.asgari_aktarma_dk")).isEqualTo("30");
        assertThat(siddet("K-14")).as("K-14'e dokunulmamış: çevrilir").isEqualTo("KAPALI");
        assertThat(sistemKayitlari()).hasSize(1);
        varsayilanlarYeni();
    }

    @Test
    void yoneticiK23AcipKapatmis_degerTohumlaAyniAmaBilincliKarar_dokunulmaz() {
        kur("v30_geri");
        kayit("KURAL_SIDDET", "K-23", "KAPALI", "ENGEL", "Yöne Tici", "deneme");
        kayit("KURAL_SIDDET", "K-23", "ENGEL", "KAPALI", "Yöne Tici", "geri aldım");
        kayit("PARAMETRE", "nobet.asgari_aktarma_dk", "0", "0", "Yöne Tici", "aynı");
        flyway("30").migrate();

        assertThat(siddet("K-23")).isEqualTo("KAPALI");
        assertThat(deger("nobet.asgari_aktarma_dk")).as("demo dışı kayıt var: bilinçli karar").isEqualTo("0");
        assertThat(sistemKayitlari()).extracting(k -> k.get("ayrinti")).containsExactly("{\"anahtar\":\"K-14\"}");
        varsayilanlarYeni();
    }

    @Test
    void demoArtigi_yalnizDemoKayitlari_cevrilir() {
        kur("v30_demo_artigi");
        // demo yükle -> sıfırla: değerler tohuma döndü, geriye yalnız DEMO kayıtları kaldı
        kayit("KURAL_SIDDET", "K-23", "KAPALI", "ENGEL", "Yöne Tici", "DEMO: aynı nöbette ardışık görevler arası en az 20 dk aktarma (K-23) – gerçek değil");
        kayit("KURAL_SIDDET", "K-23", "ENGEL", "KAPALI", "Yöne Tici", "Demo sıfırlandı");
        kayit("PARAMETRE", "nobet.asgari_aktarma_dk", "0", "20", "Yöne Tici", "DEMO: aynı nöbette ardışık görevler arası en az 20 dk aktarma (K-23) – gerçek değil");
        kayit("PARAMETRE", "nobet.asgari_aktarma_dk", "20", "0", "Yöne Tici", "Demo sıfırlandı");
        flyway("30").migrate();

        assertThat(siddet("K-23")).isEqualTo("ENGEL");
        assertThat(deger("nobet.asgari_aktarma_dk")).isEqualTo("20");
        assertThat(sistemKayitlari()).hasSize(3);
        assertThat(db.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE neden_notu = ?", Integer.class, NOT)).isEqualTo(3);
        varsayilanlarYeni();
    }

    @Test
    void demoYuklu_k23AyniKalir_haftalik55_45eDoner_elleGirilen55Kalir() {
        kur("v30_demo_yuklu");
        db.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-23'");
        db.update("UPDATE kural_parametre SET deger = '20' WHERE anahtar = 'nobet.asgari_aktarma_dk'");
        db.update("UPDATE kural_parametre SET deger = '55' WHERE anahtar = 'haftalik.max_saat'");
        kayit("KURAL_SIDDET", "K-23", "KAPALI", "ENGEL", "Yöne Tici", "DEMO: aynı nöbette ardışık görevler arası en az 20 dk aktarma (K-23) – gerçek değil");
        kayit("PARAMETRE", "nobet.asgari_aktarma_dk", "0", "20", "Yöne Tici", "DEMO: aynı nöbette ardışık görevler arası en az 20 dk aktarma (K-23) – gerçek değil");
        kayit("PARAMETRE", "haftalik.max_saat", "45", "55", "Yöne Tici", "DEMO: fazla mesai gösterimi için haftalık üst sınır 55 saat – gerçek değil");
        flyway("30").migrate();

        assertThat(siddet("K-23")).isEqualTo("ENGEL");
        assertThat(deger("nobet.asgari_aktarma_dk")).isEqualTo("20");
        assertThat(deger("haftalik.max_saat")).isEqualTo("45");
        assertThat(siddet("K-14")).isEqualTo("KAPALI");
        assertThat(db.queryForList("SELECT ayrinti FROM degisiklik_kaydi WHERE neden_notu LIKE 'V30:%' ORDER BY id", String.class))
                .containsExactly("{\"anahtar\":\"K-14\"}", "{\"anahtar\":\"haftalik.max_saat\"}");
        varsayilanlarYeni();
    }

    @Test
    void elleGirilen55_dokunulmaz() {
        kur("v30_elle55");
        db.update("UPDATE kural_parametre SET deger = '55' WHERE anahtar = 'haftalik.max_saat'");
        kayit("PARAMETRE", "haftalik.max_saat", "45", "55", "Yöne Tici", "ek vardiya");
        flyway("30").migrate();

        assertThat(deger("haftalik.max_saat")).isEqualTo("55");
    }
}
