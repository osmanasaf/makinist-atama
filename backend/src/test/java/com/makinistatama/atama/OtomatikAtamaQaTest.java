package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * QA (M3): otomatik atamanın bağımsız değişmezleri. Motorun kendi kurallarını yeniden yazmak yerine SQL ile sonucun fiziksel tutarlılığı sınanır:
 * örtüşme yok, haftalık 45 s ve günlük 11 s aşılmaz, kimse bir seferde iki kez yok, uygulama sonrası kırmızı 0, atanan + boş = tüm sefer,
 * boş seferin neden özetindeki sayıların toplamı makinist sayısına eşit (her makinist tam bir nedenle sayılır).
 */
class OtomatikAtamaQaTest extends TestVeritabani {

    static final LocalDate PAZARTESI = LocalDate.of(2026, 10, 12);
    static final int MAKINIST = 6;
    static final int GUNLUK_SEFER = 16;

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        for (int i = 1; i <= MAKINIST; i++) {
            v.makinist("D00" + i);
        }
        // Talep (16 sefer x 3 s x 7 gün = 336 s) kapasiteyi (6 x 45 = 270 s) aşar: bazı seferler boş kalmak zorunda
        for (int g = 0; g < 7; g++) {
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int saat = 5 + i; // 05:00 .. 20:00 kalkış, 3 saatlik
                v.seferEv("Q%d-%02d".formatted(g, i), PAZARTESI.plusDays(g), "%02d:00".formatted(saat), "%02d:00".formatted(saat + 3));
            }
        }
    }

    private static String govde(boolean uygula) {
        return "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-18\",\"uygula\":" + uygula + "}";
    }

    /** OK-24: K-14 ENGEL'e alınırsa (eski varsayılan) haftalık 45 s fiziksel sınırdır. */
    @Test
    void uygula_fizikselDegismezler_haftalik45_K14Engel_gunluk11_ortusmeYok_kirmiziSifir() throws Exception {
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-14'"); // TestVeritabani tohuma döndürür
        fizikselDegismezler(45.0);
    }

    /** OK-24 varsayılanı (K-14 KAPALI): haftalık tavan yok; günlük 11 s x en çok 6 gün (hafta tatili) = 66 s fiziksel sınırdır. */
    @Test
    void uygula_fizikselDegismezler_varsayilanK14Kapali_haftalik66_gunluk11_ortusmeYok_kirmiziSifir() throws Exception {
        fizikselDegismezler(66.0);
    }

    private void fizikselDegismezler(double haftalikSinir) throws Exception {
        Tarayici t = atamaci();
        String yanit = t.post("/api/atama/otomatik", govde(true)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int atanan = JsonPath.read(yanit, "$.atananSayisi");
        int bos = JsonPath.read(yanit, "$.bosSayisi");

        assertThat(atanan + bos).isEqualTo(7 * GUNLUK_SEFER);
        assertThat(bos).as("talep kapasiteyi aşıyor: boş kalan olmalı").isPositive();
        assertThat(atanan).as("kimse tüm talebi karşılayamaz ama kapasite boşa gitmemeli").isGreaterThan(MAKINIST * 40 / 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'ASIL'", Integer.class)).isEqualTo(atanan);

        // haftalık saat <= sınır (tüm seferler aynı ISO haftasında)
        List<Map<String, Object>> haftalik = jdbc.queryForList("""
                SELECT m.sicil, sum(extract(epoch FROM s.planlanan_varis - s.planlanan_kalkis)) / 3600 AS saat
                FROM atama a JOIN sefer s ON s.id = a.sefer_id JOIN makinist m ON m.id = a.makinist_id GROUP BY m.sicil""");
        assertThat(haftalik).isNotEmpty().allSatisfy(r -> assertThat(((Number) r.get("saat")).doubleValue()).as("haftalık saat " + r.get("sicil")).isLessThanOrEqualTo(haftalikSinir));

        // günlük saat <= 11
        List<Map<String, Object>> gunluk = jdbc.queryForList("""
                SELECT m.sicil, s.tarih, sum(extract(epoch FROM s.planlanan_varis - s.planlanan_kalkis)) / 3600 AS saat
                FROM atama a JOIN sefer s ON s.id = a.sefer_id JOIN makinist m ON m.id = a.makinist_id GROUP BY m.sicil, s.tarih""");
        assertThat(gunluk).allSatisfy(r -> assertThat(((Number) r.get("saat")).doubleValue()).as("günlük saat " + r.get("sicil") + " " + r.get("tarih")).isLessThanOrEqualTo(11.0));

        // aynı makinist iki seferde aynı anda yok
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM atama a1 JOIN sefer s1 ON s1.id = a1.sefer_id
                JOIN atama a2 ON a2.makinist_id = a1.makinist_id AND a2.sefer_id > a1.sefer_id
                JOIN sefer s2 ON s2.id = a2.sefer_id
                WHERE s1.planlanan_kalkis < s2.planlanan_varis AND s2.planlanan_kalkis < s1.planlanan_varis""", Integer.class)).isZero();

        // iki ardışık görev arasında en az 4 s ise (yeni nöbet) 11 s dinlenme var: ayırma eşiğinin altı aynı nöbettir
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                  SELECT s.planlanan_kalkis AS k, lag(s.planlanan_varis) OVER (PARTITION BY a.makinist_id ORDER BY s.planlanan_kalkis) AS onceki_varis
                  FROM atama a JOIN sefer s ON s.id = a.sefer_id) x
                WHERE onceki_varis IS NOT NULL AND k - onceki_varis > interval '4 hours' AND k - onceki_varis < interval '11 hours'""", Integer.class))
                .as("4 s ile 11 s arası boşluk = yeni nöbet ama yetersiz dinlenme").isZero();

        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-12&bit=2026-10-18").andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(0))
                .andExpect(jsonPath("$.kirmiziSefer").value(0));
        // idempotent: ikinci koşu yeni atama yapmaz, boş sayısı değişmez
        t.post("/api/atama/otomatik", govde(true)).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosSayisi").value(bos));
    }

    @Test
    void bosSeferNedenOzeti_sayilarinToplami_makinistSayisinaEsit() throws Exception {
        String yanit = atamaci().post("/api/atama/otomatik", govde(false)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> nedenler = JsonPath.read(yanit, "$.bosKalanlar[*].neden");
        assertThat(nedenler).isNotEmpty();
        Pattern sayi = Pattern.compile("(\\d+)(?:,|$)");
        for (String neden : nedenler) {
            Matcher m = Pattern.compile("^0/(\\d+) uygun(?: \\(yetkili \\d+, yetkisiz \\d+\\))?: (.*)$").matcher(neden);
            assertThat(m.matches()).as(neden).isTrue();
            int toplam = 0;
            Matcher s = sayi.matcher(m.group(2).replace(", ", ","));
            while (s.find()) {
                toplam += Integer.parseInt(s.group(1));
            }
            assertThat(Integer.parseInt(m.group(1))).isEqualTo(MAKINIST);
            assertThat(toplam).as("her makinist tam bir nedenle sayılmalı: " + neden).isEqualTo(MAKINIST);
        }
    }
}
