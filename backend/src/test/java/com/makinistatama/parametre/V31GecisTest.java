package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.makinistatama.TestVeritabani;

/**
 * Review W1: V29 {@code varsayilan} sütunlarını kurulu DB'deki o anki değerden doldurmuştu. V31 (V29/V30 uygulanmış kurulumlar için, V29'a dokunmadan) sütunları SABİT tohum
 * değerlerine çeker; çalışan değerlere dokunmaz. Gerçek Flyway, aynı kapta AYRI şemalarda.
 */
class V31GecisTest extends TestVeritabani {

    @Value("${spring.datasource.url}")
    String url;
    @Value("${spring.datasource.username}")
    String kullanici;
    @Value("${spring.datasource.password}")
    String parola;

    private final java.util.List<String> semalar = new java.util.ArrayList<>();

    private Flyway flyway(String sema, String hedef) {
        return Flyway.configure().dataSource(url, kullanici, parola).schemas(sema).defaultSchema(sema).createSchemas(true).locations("classpath:db/migration").target(hedef).load();
    }

    private JdbcTemplate kur(String sema, String hedef) {
        semalar.add(sema);
        flyway(sema, hedef).migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + sema, kullanici, parola));
    }

    @AfterEach
    void sil() {
        semalar.forEach(s -> jdbc.execute("DROP SCHEMA IF EXISTS " + s + " CASCADE"));
    }

    private static Map<String, String> varsayilanlar(JdbcTemplate db) {
        return db.queryForList("SELECT 'P:' || anahtar AS k, varsayilan AS v FROM kural_parametre UNION ALL SELECT 'S:' || kod, varsayilan FROM kural_siddet UNION ALL SELECT 'A:' || anahtar, varsayilan::text FROM uygulama_ayar")
                .stream().collect(Collectors.toMap(r -> (String) r.get("k"), r -> (String) r.get("v")));
    }

    /** Review senaryosu: yönetici ev_saat=12, K-13=UYARI, takas.son_saat=10, yedek.oran=0.2 yapmış kurulum (V30 uygulanmış). */
    @Test
    void yoneticiDegistirmisKurulum_varsayilanSabitTohumaDoner_degerlerKalir() {
        JdbcTemplate db = kur("v31_yonetici", "30");
        db.update("UPDATE kural_parametre SET deger = '12' WHERE anahtar = 'dinlenme.ev_saat'");
        db.update("UPDATE kural_parametre SET deger = '0.2' WHERE anahtar = 'yedek.oran'");
        db.update("UPDATE kural_siddet SET siddet = 'UYARI' WHERE kod = 'K-13'");
        db.update("UPDATE uygulama_ayar SET deger = 10 WHERE anahtar = 'takas.son_saat'");
        // V29'un hatası kurulu DB'de görünür: varsayılan, değiştirilmiş değerden doldurulmuş olabilir
        db.update("UPDATE kural_parametre SET varsayilan = deger WHERE anahtar IN ('dinlenme.ev_saat', 'yedek.oran')");
        db.update("UPDATE kural_siddet SET varsayilan = siddet WHERE kod = 'K-13'");
        db.update("UPDATE uygulama_ayar SET varsayilan = deger WHERE anahtar = 'takas.son_saat'");
        flyway("v31_yonetici", "31").migrate();

        assertThat(db.queryForObject("SELECT varsayilan FROM kural_parametre WHERE anahtar = 'dinlenme.ev_saat'", String.class)).isEqualTo("11");
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_parametre WHERE anahtar = 'yedek.oran'", String.class)).isEqualTo("0.10");
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_siddet WHERE kod = 'K-13'", String.class)).isEqualTo("ENGEL");
        assertThat(db.queryForObject("SELECT varsayilan FROM uygulama_ayar WHERE anahtar = 'takas.son_saat'", Integer.class)).isEqualTo(12);
        assertThat(db.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'dinlenme.ev_saat'", String.class)).as("çalışan değer dokunulmaz").isEqualTo("12");
        assertThat(db.queryForObject("SELECT siddet FROM kural_siddet WHERE kod = 'K-13'", String.class)).isEqualTo("UYARI");
        assertThat(db.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'takas.son_saat'", Integer.class)).isEqualTo(10);
        // yeni varsayılanlar (V30) korunur
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_siddet WHERE kod = 'K-14'", String.class)).isEqualTo("KAPALI");
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_siddet WHERE kod = 'K-23'", String.class)).isEqualTo("ENGEL");
        assertThat(db.queryForObject("SELECT varsayilan FROM kural_parametre WHERE anahtar = 'nobet.asgari_aktarma_dk'", String.class)).isEqualTo("20");
    }

    /** Her satırın sabit varsayılanı, taze kurulumdaki (V29+V30+V31) ile birebir aynı: tek doğruluk kaynağı. */
    @Test
    void tumSatirlar_tazeKurulumdakiVarsayilanlaAyni_veEkranMetniTemiz() {
        JdbcTemplate taze = kur("v31_taze", "31");
        JdbcTemplate eski = kur("v31_eski", "30");
        eski.update("UPDATE kural_parametre SET varsayilan = deger || 'x'"); // her varsayılan bozuk
        eski.update("UPDATE kural_siddet SET varsayilan = 'UYARI'");
        eski.update("UPDATE uygulama_ayar SET varsayilan = ust");
        flyway("v31_eski", "31").migrate();

        assertThat(varsayilanlar(eski)).isEqualTo(varsayilanlar(taze)).hasSize(32 + 22 + 22);
        List<String> jargon = taze.queryForList("SELECT kaynak FROM kural_parametre UNION ALL SELECT kaynak FROM kural_siddet UNION ALL SELECT aciklama FROM uygulama_ayar", String.class).stream()
                .filter(m -> m.contains("MK-") || m.contains("SON-QA") || m.contains("429")).toList();
        assertThat(jargon).isEmpty();
    }
}
