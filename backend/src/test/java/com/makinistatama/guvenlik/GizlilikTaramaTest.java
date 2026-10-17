package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * NFR-07: kişisel veri tasarımı. Kod, şema ve API'de TC kimlik, rapor tanısı, telefon, adres, doğum tarihi alanı yoktur (makinist kartında yalnız
 * sicil, ad soyad ve belge BİTİŞ tarihleri); günlük (log) satırlarında ad soyad geçmez (sicil geçebilir). Kullanıcı hesabı (e-posta) kapsam dışıdır.
 */
class GizlilikTaramaTest extends TestVeritabani {

    /** Yasak tanımlayıcı/sütun adları (ASCII, tam sözcük; camelCase ve snake_case parçaları ayrı sözcük sayılır). */
    static final Pattern YASAK_AD = Pattern.compile("(?i)(?<![a-z0-9])(tc|tckn|tc_kimlik\\w*|tckimlik\\w*|kimlik_?no|tani|teshis|telefon|gsm|cep_?tel\\w*|dogum\\w*|adres)(?![a-z0-9])");

    private static String yorumsuz(String kaynak) {
        return kaynak.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ").replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    }

    private static List<Path> kaynaklar(boolean kullaniciHaric) throws IOException {
        try (Stream<Path> s = Files.walk(Path.of("src/main/java/com/makinistatama"))) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !kullaniciHaric || !(p.toString().replace('\\', '/').contains("/kullanici/") || p.toString().replace('\\', '/').contains("/guvenlik/")))
                    .toList();
        }
    }

    @Test
    void kaynakKodda_yasakKisiselVeriAlaniYok() throws IOException {
        List<String> bulunan = new ArrayList<>();
        for (Path p : kaynaklar(true)) {
            var m = YASAK_AD.matcher(yorumsuz(Files.readString(p)));
            while (m.find()) {
                bulunan.add(p.getFileName() + ": " + m.group());
            }
        }
        assertThat(bulunan).isEmpty();
    }

    @Test
    void veritabaniSemasinda_yasakKolonYok() {
        List<String> kolonlar = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name NOT IN ('kullanici', 'flyway_schema_history')""", String.class);
        assertThat(kolonlar).isNotEmpty();
        assertThat(kolonlar.stream().filter(k -> YASAK_AD.matcher(k.substring(k.indexOf('.') + 1)).find()).toList()).isEmpty();
    }

    @Test
    void apiYanitlarinda_yasakAnahtarYok() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long s = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
        var t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        JsonMapper json = JsonMapper.builder().build();

        for (String url : List.of("/api/makinist", "/api/makinist/" + m, "/api/sefer?gun=2026-10-12", "/api/atama/gun?tarih=2026-10-12",
                "/api/atama/sefer/" + s + "/adaylar", "/api/rapor/belge-durumu", "/api/parametre", "/api/kural")) {
            String govde = t.get(url).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            anahtarlar(json.readTree(govde), url);
        }
    }

    private static void anahtarlar(JsonNode dugum, String url) {
        if (dugum.isObject()) {
            dugum.properties().forEach(e -> {
                assertThat(YASAK_AD.matcher(e.getKey()).find()).as("%s: yasak alan '%s'", url, e.getKey()).isFalse();
                anahtarlar(e.getValue(), url);
            });
        } else if (dugum.isArray()) {
            dugum.forEach(d -> anahtarlar(d, url));
        }
    }

    @Test
    void logSatirlarinda_adSoyadGecmez() throws IOException {
        Pattern logCagrisi = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(([^;]*)\\);", Pattern.DOTALL);
        Pattern ad = Pattern.compile("(?i)adSoyad|ad_soyad|\\.ad\\(\\)|getAd\\b");
        List<String> bulunan = new ArrayList<>();
        for (Path p : kaynaklar(false)) {
            var m = logCagrisi.matcher(Files.readString(p));
            while (m.find()) {
                if (ad.matcher(m.group(2)).find()) {
                    bulunan.add(p.getFileName() + ": " + m.group());
                }
            }
        }
        assertThat(bulunan).isEmpty();
    }
}
