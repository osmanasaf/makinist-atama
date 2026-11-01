package com.makinistatama;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.makinistatama.kullanici.KullaniciIstekleri;
import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.kullanici.Rol;

/**
 * Entegrasyon testlerinin tabanı: JVM başına <b>tek</b> gerçek PostgreSQL 17 kabı (Testcontainers), tüm test sınıfları aynı Spring
 * bağlamını ve kabı paylaşır. Her testten önce veri tabloları {@code TRUNCATE ... RESTART IDENTITY CASCADE} ile boşaltılır;
 * {@code flyway_schema_history} ile migration'ın tohumladığı {@code kural_parametre}/{@code kural_siddet} korunur; değerleri her testten önce
 * migration tohumuna döndürülür. Kap JVM çıkışında Testcontainers'ın Ryuk'u tarafından silinir.
 */
@SpringBootTest(properties = "makinist.atama.simdi=2026-10-10T10:00:00+03:00")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(HizliSifreleyici.class)
public abstract class TestVeritabani {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    /** Tohumlu, temizlikte korunan tablolar. */
    private static final List<String> KORUNAN = List.of("flyway_schema_history", "kural_parametre", "kural_siddet", "uygulama_ayar", "kurum");

    private static java.util.Map<String, String> tohumDegerler;
    private static java.util.Map<String, String> tohumSiddetler;
    private static java.util.Map<String, Integer> tohumAyarlar;

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void ozellikler(DynamicPropertyRegistry kayit) {
        kayit.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        kayit.add("spring.datasource.username", POSTGRES::getUsername);
        kayit.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected KullaniciServisi kullanicilar;

    /** YONETICI rolünde giriş yapmış tarayıcı (kullanıcıyı yoksa oluşturur). */
    protected Tarayici yonetici() throws Exception {
        return girisli("yonetici@ornek.test", "Yöne Tici", Rol.YONETICI);
    }

    /** ATAMACI rolünde giriş yapmış tarayıcı. */
    protected Tarayici atamaci() throws Exception {
        return girisli("atamaci@ornek.test", "Ata Maci", Rol.ATAMACI);
    }

    protected Tarayici girisli(String eposta, String ad, Rol rol) throws Exception {
        if (kullanicilar.epostaIle(eposta).isEmpty()) {
            kullanicilar.olustur(new KullaniciIstekleri.Olustur(eposta, ad, rol, Tarayici.PAROLA));
        }
        return Tarayici.girisYapmis(mvc, eposta, Tarayici.PAROLA);
    }

    /** MAKINIST hesabı açık ve ilk parolası belirlenmiş (bayrak kapalı) makinist için giriş yapmış tarayıcı; giriş adı küçük harf sicil, parola {@link Tarayici#PAROLA}. */
    protected Tarayici makinistGirisli(long makinistId, String sicil) throws Exception {
        kullanicilar.makinistHesabiAc(makinistId, sicil, "Demo Makinist " + sicil, Tarayici.PAROLA);
        jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE makinist_id = ?", makinistId);
        return Tarayici.girisYapmis(mvc, sicil.toLowerCase(java.util.Locale.ROOT), Tarayici.PAROLA);
    }

    @BeforeEach
    void tablolariTemizle() {
        List<String> tablolar = jdbc.queryForList(
                "SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname = 'public' AND tablename <> ALL (?)",
                String.class, (Object) KORUNAN.toArray(new String[0]));
        if (!tablolar.isEmpty()) {
            jdbc.execute("TRUNCATE " + String.join(", ", tablolar) + " RESTART IDENTITY CASCADE");
        }
        // parametre/şiddet tohumu korunur ama bir testte değiştirilmiş olabilir (PUT /api/parametre): her test migration tohumuyla başlar
        if (tohumDegerler == null) { // JVM'deki ilk test gövdesinden önce: henüz kimse değiştirmedi
            tohumDegerler = jdbc.queryForList("SELECT anahtar, deger FROM kural_parametre").stream().collect(java.util.stream.Collectors.toMap(r -> (String) r.get("anahtar"), r -> (String) r.get("deger")));
            tohumSiddetler = jdbc.queryForList("SELECT kod, siddet FROM kural_siddet").stream().collect(java.util.stream.Collectors.toMap(r -> (String) r.get("kod"), r -> (String) r.get("siddet")));
        }
        if (tohumAyarlar == null) {
            tohumAyarlar = jdbc.queryForList("SELECT anahtar, deger FROM uygulama_ayar").stream().collect(java.util.stream.Collectors.toMap(r -> (String) r.get("anahtar"), r -> ((Number) r.get("deger")).intValue()));
        }
        jdbc.update("UPDATE kurum SET ad = NULL, logo = NULL, logo_tur = NULL, logo_boyut = 0, sihirbaz_gizli = FALSE, versiyon = 0, guncelleme = NULL"); // tek satırlı kurum tablosu: her test boş kurulumla başlar (V34 satırı korunur)
        tohumAyarlar.forEach((k, v) -> jdbc.update("UPDATE uygulama_ayar SET deger = ? WHERE anahtar = ? AND deger <> ?", v, k, v));
        tohumDegerler.forEach((k, v) -> jdbc.update("UPDATE kural_parametre SET deger = ? WHERE anahtar = ? AND deger <> ?", v, k, v));
        tohumSiddetler.forEach((k, v) -> jdbc.update("UPDATE kural_siddet SET siddet = ? WHERE kod = ? AND siddet <> ?", v, k, v));
    }
}
