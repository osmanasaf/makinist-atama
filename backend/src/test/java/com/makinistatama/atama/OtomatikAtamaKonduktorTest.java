package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F3b MK-88 (FR-06): otomatik atama tür bazında; çekirdek rol bilmez, servis havuzu, aday seferleri ve yazılan rolü belirler. */
class OtomatikAtamaKonduktorTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    Tarayici t;
    long y1;
    long y2;
    long y3;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        t = atamaci();
        y1 = v.seferEv("Y1", GUN, "06:00", "08:00");
        y2 = v.seferEv("Y2", GUN, "07:00", "09:00");
        y3 = v.seferEv("Y3", GUN, "06:30", "08:30"); // ikisiyle de çakışır
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU' WHERE id IN (?, ?, ?)", y1, y2, y3);
    }

    private static String govde(boolean uygula, String ek) {
        return "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-12\",\"uygula\":" + uygula + ek + "}";
    }

    @Test
    void konduktorHavuzu_cakisanTrenlerFarkliKisiler_ucuncuBosNedenli_yazilanRolKonduktor() throws Exception {
        v.makinist("D001");
        long c1 = v.konduktor("K001");
        long c2 = v.konduktor("K002");

        t.post("/api/atama/otomatik", govde(false, ",\"personelTuru\":\"KONDUKTOR\"")).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(2))
                .andExpect(jsonPath("$.bosSayisi").value(1)).andExpect(jsonPath("$.bosKalanlar[0].neden").value(startsWith("0/2 uygun")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).as("önizleme yazmaz").isZero();

        t.post("/api/atama/otomatik", govde(true, ",\"personelTuru\":\"KONDUKTOR\"")).andExpect(status().isOk()).andExpect(jsonPath("$.uygulandi").value(true));
        assertThat(jdbc.queryForList("SELECT makinist_id FROM atama WHERE rol = 'KONDUKTOR' ORDER BY makinist_id", Long.class)).containsExactly(c1, c2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol <> 'KONDUKTOR'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'", String.class)).contains("\"tur\":\"KONDUKTOR\"");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM atama a JOIN makinist m ON m.id = a.makinist_id
                WHERE a.rol <> 'YOLCU' AND (a.rol = 'KONDUKTOR') <> (m.tur = 'KONDUKTOR')""", Integer.class)).isZero();

        // ikinci çalıştırma: KONDUKTOR'u dolu seferler planlanmaz, kalan çakışan tren yine boş
        t.post("/api/atama/otomatik", govde(true, ",\"personelTuru\":\"KONDUKTOR\"")).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(2);
    }

    @Test
    void yukTreniVeTurBosSefer_kondukturOtomatigineGirmez() throws Exception {
        v.konduktor("K001");
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", y1);
        jdbc.update("UPDATE sefer SET tren_turu = NULL WHERE id = ?", y2);
        t.post("/api/atama/otomatik", govde(false, ",\"personelTuru\":\"KONDUKTOR\"")).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(1))
                .andExpect(jsonPath("$.atananlar[0].trenNo").value("Y3"));
    }

    @Test
    void personelTuruYokYaDaMakinist_bugunkuCikti_yalnizMakinistVeAsil() throws Exception {
        long d1 = v.makinist("D001");
        v.konduktor("K001");
        String yok = t.post("/api/atama/otomatik", govde(false, "")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String acik = t.post("/api/atama/otomatik", govde(false, ",\"personelTuru\":\"MAKINIST\"")).andReturn().getResponse().getContentAsString();
        assertThat(acik).isEqualTo(yok);

        t.post("/api/atama/otomatik", govde(true, "")).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT rol FROM atama", String.class)).containsOnly("ASIL");
        assertThat(jdbc.queryForList("SELECT makinist_id FROM atama", Long.class)).containsOnly(d1);
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'", String.class)).doesNotContain("\"tur\"");
    }

    @Test
    void konduktorIleYolculukVeIyilestir_400() throws Exception {
        v.konduktor("K001");
        t.post("/api/atama/otomatik", govde(false, ",\"personelTuru\":\"KONDUKTOR\",\"yolculuk\":true")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("YOLCULUK_YALNIZ_MAKINIST"));
        t.post("/api/atama/otomatik", govde(false, ",\"personelTuru\":\"KONDUKTOR\",\"iyilestir\":true")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_YALNIZ_MAKINIST"));
        t.post("/api/atama/otomatik", govde(true, ",\"personelTuru\":\"KONDUKTOR\",\"iyilestir\":true")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_YALNIZ_MAKINIST"));
        t.post("/api/atama/otomatik", govde(false, ",\"personelTuru\":\"MANEVRACI\"")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }

    @Test
    void makinistAsilYazildiktanSonraKonduktorAyriSatir_ikisiDeAyniSeferde() throws Exception {
        v.makinist("D001");
        v.makinist("D002");
        v.makinist("D003");
        v.konduktor("K001");
        v.konduktor("K002");
        v.konduktor("K003");
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU'");
        t.post("/api/atama/otomatik", govde(true, "")).andExpect(status().isOk());
        t.post("/api/atama/otomatik", govde(true, ",\"personelTuru\":\"KONDUKTOR\"")).andExpect(status().isOk()).andExpect(jsonPath("$.atananlar", hasSize(3)));
        List<String> roller = jdbc.queryForList("SELECT rol FROM atama ORDER BY rol", String.class);
        assertThat(roller).containsExactlyInAnyOrder("ASIL", "ASIL", "ASIL", "KONDUKTOR", "KONDUKTOR", "KONDUKTOR");
    }
}
