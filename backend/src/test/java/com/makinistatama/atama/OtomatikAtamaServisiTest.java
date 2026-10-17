package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * T11.2: otomatik atama servisi. Önizleme yazmaz ve uygulamayla aynı sonucu verir; uygula tek işlemde yazar (ATAMA kaydı + anlık görüntü + tek
 * OTOMATIK_ATAMA); ikinci koşu 0 yeni atama; elle atanmış ve iptal seferlere dokunulmaz; uygulama sonrası kırmızı sayısı 0.
 */
class OtomatikAtamaServisiTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long d1;
    long d2;
    long d3;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        d1 = v.makinist("D001");
        d2 = v.makinist("D002");
        d3 = v.makinist("D003");
        // bu ay (Ekim) önceden çalışılan: D001 40 s, D002 32 s / 1000 km, D003 32 s / 400 km
        calisma(d1, "A", 5);
        calisma(d2, "B", 4);
        calisma(d3, "C", 4);
        jdbc.update("UPDATE sefer SET km = 100 WHERE tren_no LIKE 'C%'");
    }

    private void calisma(long makinist, String onek, int gunSayisi) {
        for (int i = 0; i < gunSayisi; i++) {
            v.atama(v.seferEv(onek + i, LocalDate.of(2026, 10, 1 + i), "06:00", "14:00"), makinist, "ASIL"); // 8 saat
        }
    }

    private static String govde(boolean uygula) {
        return "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-12\",\"uygula\":" + uygula + "}";
    }

    /** UX-11: kalkışı geçmişte kalan (bitmiş ya da başlamış) sefer hedeflenmez; "boş" sayılmaz, ayrı sayıda bildirilir. Testlerde şimdi = 10 Eki 10:00. */
    @Test
    void gecmisSeferler_hedeflenmez_bosSayilmaz_ayriSayilir() throws Exception {
        LocalDate bugun = LocalDate.of(2026, 10, 10);
        long bitmis = v.seferEv("P1", bugun, "06:00", "09:00");
        long baslamis = v.seferEv("P2", bugun, "09:30", "12:00");
        long gelecek = v.seferEv("P3", bugun, "13:00", "15:00");
        String govde = "{\"bas\":\"2026-10-10\",\"bit\":\"2026-10-10\",\"uygula\":true}";

        atamaci().post("/api/atama/otomatik", govde).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(1))
                .andExpect(jsonPath("$.bosSayisi").value(0)).andExpect(jsonPath("$.gecmisSayisi").value(2)).andExpect(jsonPath("$.atananlar[0].trenNo").value("P3"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id IN (?, ?)", Integer.class, bitmis, baslamis)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, gelecek)).isEqualTo(1);
    }

    @Test
    void onizleme_enAzCalisanBagKirici_yazmaz_uygulamayaEsit() throws Exception {
        long s1 = v.seferEv("S1", GUN, "08:00", "12:00");
        long s2 = v.seferEv("S2", GUN, "10:00", "15:00");
        Tarayici t = atamaci();
        int once = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);

        String onizleme = t.post("/api/atama/otomatik", govde(false)).andExpect(status().isOk()).andExpect(jsonPath("$.uygulandi").value(false))
                .andExpect(jsonPath("$.atananSayisi").value(2)).andExpect(jsonPath("$.bosSayisi").value(0))
                .andExpect(jsonPath("$.atananlar[0].trenNo").value("S1")).andExpect(jsonPath("$.atananlar[0].sicil").value("D003"))
                .andExpect(jsonPath("$.atananlar[1].trenNo").value("S2")).andExpect(jsonPath("$.atananlar[1].sicil").value("D002"))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(once);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();

        String uygulama = t.post("/api/atama/otomatik", govde(true)).andExpect(status().isOk()).andExpect(jsonPath("$.uygulandi").value(true)).andReturn()
                .getResponse().getContentAsString();
        assertThat(uygulama.replace("\"uygulandi\":true", "\"uygulandi\":false")).isEqualTo(onizleme);
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s1)).isEqualTo(d3);
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s2)).isEqualTo(d2);
    }

    @Test
    void uygula_kayitlar_anlikGoruntu_ikinciKosuSifir_kirmiziSifir() throws Exception {
        for (int i = 0; i < 6; i++) {
            v.seferEv("S" + i, GUN.plusDays(i), "08:00", "11:00");
        }
        Tarayici t = atamaci();
        String govde = "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-17\",\"uygula\":true}";

        t.post("/api/atama/otomatik", govde).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(6)).andExpect(jsonPath("$.bosSayisi").value(0));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id IN (SELECT id FROM sefer WHERE tren_no LIKE 'S%') AND rol <> 'ASIL'", Integer.class)).isZero();
        var run = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'");
        assertThat(run).containsEntry("kullanici_adi", "Ata Maci");
        assertThat((String) run.get("ayrinti")).contains("\"atanan\":6").contains("\"bos\":0").contains("2026-10-12");
        String json = jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA' LIMIT 1", String.class);
        assertThat(json).contains("\"otomatik\":true").contains("\"sonuc\":\"UYGUN\"").contains("\"kontrolEdilenKodlar\"").contains("K-22");

        t.post("/api/atama/otomatik", govde).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosSayisi").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA'", Integer.class)).isEqualTo(6);
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-12&bit=2026-10-17").andExpect(jsonPath("$.kirmiziAtama").value(0)).andExpect(jsonPath("$.kirmiziSefer").value(0));
    }

    @Test
    void elleAtanmisVeIptalSeferlereDokunulmaz_bosKalanNedenle() throws Exception {
        long elle = v.seferEv("ELLE", GUN, "08:00", "10:00");
        v.atama(elle, d1, "ASIL");
        long iptal = v.seferEv("IPTAL", GUN, "11:00", "12:00");
        atamaci().post("/api/sefer/" + iptal + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        long yetkisizSefer = v.seferEv("HATSIZ", GUN, "16:00", "18:00");
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, yetkisizSefer); // hiçbir makinistin bröve hattı değil

        atamaci().post("/api/atama/otomatik", govde(true)).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(0))
                .andExpect(jsonPath("$.bosSayisi").value(1)).andExpect(jsonPath("$.bosKalanlar[0].trenNo").value("HATSIZ"))
                .andExpect(jsonPath("$.bosKalanlar[0].neden").value("0/3 uygun: hat yetkisi 3")).andExpect(jsonPath("$.bosKalanlar[0].ornekler[0]").value(containsString("Hat yetkisi yok")));

        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ?", Long.class, elle)).isEqualTo(d1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, iptal)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, yetkisizSefer)).isZero();
    }

    @Test
    void yalnizAsilDoldurulur_yardimciVarsaAyniMakinistAtanmaz() throws Exception {
        long s = v.seferEv("S1", GUN, "08:00", "12:00");
        v.atama(s, d3, "YARDIMCI"); // D003 en az çalışmış olsa da bu seferde zaten yardımcı

        atamaci().post("/api/atama/otomatik", govde(true)).andExpect(status().isOk()).andExpect(jsonPath("$.atananlar[0].sicil").value("D002"));

        assertThat(jdbc.queryForList("SELECT rol FROM atama WHERE sefer_id = ? ORDER BY rol", String.class, s)).containsExactly("ASIL", "YARDIMCI");
    }

    @Test
    void gecmisSefer_otomatikAtanmaz_bosSayilmaz_UX11() throws Exception {
        v.seferEv("GECMIS", LocalDate.of(2026, 10, 9), "08:00", "10:00");

        atamaci().post("/api/atama/otomatik", "{\"bas\":\"2026-10-09\",\"bit\":\"2026-10-09\",\"uygula\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosSayisi").value(0))
                .andExpect(jsonPath("$.bosKalanlar", hasSize(0))).andExpect(jsonPath("$.gecmisSayisi").value(1));
    }

    @Test
    void dogrulama_tarihAraligi() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-11\",\"uygula\":false}").andExpect(status().isBadRequest());
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-01-01\",\"bit\":\"2026-12-31\",\"uygula\":false}").andExpect(status().isBadRequest());
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"uygula\":false}").andExpect(status().isBadRequest());
        new Tarayici(mvc).post("/api/atama/otomatik", govde(false)).andExpect(status().isUnauthorized());
    }
}
