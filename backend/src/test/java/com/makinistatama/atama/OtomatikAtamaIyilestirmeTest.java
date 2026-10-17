package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.json.JsonMapper;

/**
 * F2d MK-68/70/71: opt-in "iyileştir" uçtan uca. Kurulum: D001 iki hatta, D002 yalnız bir hatta yetkili; 101 (hat) 08:00-10:00 ile 102 (hat2) 09:00-11:00 çakışır. Bugünkü
 * açgözlü 101'i D001'e verir, 102 boş kalır; iyileştirme 102'yi öne alıp ikisini de doldurur.
 */
class OtomatikAtamaIyilestirmeTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);
    static final JsonMapper JSON = new JsonMapper();

    AtamaVeri v;
    long d1;
    long d2;
    long s101;
    long s102;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        d1 = v.makinist("D001");
        d2 = v.makinist("D002");
        jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-09-01')", d1, v.hat2);
        s101 = v.seferEv("S101", GUN, "08:00", "10:00");
        s102 = v.seferEv("S102", GUN, "09:00", "11:00");
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, s102);
    }

    private static String govde(boolean uygula, String ek) {
        return "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-12\",\"uygula\":" + uygula + ek + "}";
    }

    @Test
    void bayrakYokVeFalse_ayniGovde_iyilestirmeNull_bugunkuPlan() throws Exception {
        Tarayici t = atamaci();
        String yok = t.post("/api/atama/otomatik", govde(false, "")).andExpect(status().isOk()).andExpect(jsonPath("$.iyilestirme").value(nullValue()))
                .andExpect(jsonPath("$.atananSayisi").value(1)).andExpect(jsonPath("$.bosSayisi").value(1)).andReturn().getResponse().getContentAsString();
        String kapali = t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":false")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(kapali).isEqualTo(yok);
    }

    @Test
    void onizleme_tabandanCokDoldurur_anahtarDoner() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true")).andExpect(status().isOk()).andExpect(jsonPath("$.iyilestirme.tabanBosSayisi").value(1))
                .andExpect(jsonPath("$.bosSayisi").value(0)).andExpect(jsonPath("$.atananSayisi").value(2)).andExpect(jsonPath("$.iyilestirme.tur").value(2))
                .andExpect(jsonPath("$.iyilestirme.anahtar.kaydirDk").value(1440)).andExpect(jsonPath("$.iyilestirme.anahtar.oncelik['" + s102 + "']").value(1));
    }

    @Test
    void onizlemeAnahtariylaUygula_ayniPlan_kirmiziYok_ikinciUygulaYazmaz() throws Exception {
        Tarayici t = atamaci();
        String onizleme = t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String anahtar = JSON.readTree(onizleme).get("iyilestirme").get("anahtar").toString();
        String uygulama = t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + anahtar)).andExpect(status().isOk())
                .andExpect(jsonPath("$.uygulandi").value(true)).andExpect(jsonPath("$.bosSayisi").value(0)).andExpect(jsonPath("$.iyilestirme.tabanBosSayisi").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(uygulama).get("atananlar")).isEqualTo(JSON.readTree(onizleme).get("atananlar"));
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s102)).isEqualTo(d1);
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s101)).isEqualTo(d2);
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-12&bit=2026-10-12").andExpect(jsonPath("$.kirmiziAtama").value(0));
        assertThat((String) jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'", String.class)).contains("\"iyilestir\":true");

        int once = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + anahtar)).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(once);
    }

    @Test
    void iyilestirmesizUygulama_denetimKaydindaIyilestirYok() throws Exception {
        atamaci().post("/api/atama/otomatik", govde(true, "")).andExpect(status().isOk());
        assertThat((String) jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'", String.class)).doesNotContain("iyilestir");
    }

    @Test
    void uygulaAnahtarsiz_400() throws Exception {
        atamaci().post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_ANAHTARI_GEREKLI"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }

    @Test
    void yolculukIleBirlikte_400() throws Exception {
        atamaci().post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true,\"yolculuk\":true")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_YOLCULUK"));
    }

    @Test
    void gecersizAnahtar_400() throws Exception {
        Tarayici t = atamaci();
        for (String anahtar : new String[] { "{\"kaydirDk\":-1,\"oncelik\":{}}", "{\"kaydirDk\":1441,\"oncelik\":{}}", "{\"kaydirDk\":60,\"oncelik\":{\"" + s102 + "\":0}}",
                "{\"kaydirDk\":60,\"oncelik\":{\"" + s102 + "\":101}}", "{\"kaydirDk\":60,\"oncelik\":{\"" + s102 + "\":null}}" }) {
            t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true,\"anahtar\":" + anahtar)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("DOGRULAMA"));
        }
        StringBuilder cok = new StringBuilder("{\"kaydirDk\":60,\"oncelik\":{");
        for (int i = 1; i <= 20_001; i++) {
            cok.append(i > 1 ? "," : "").append('"').append(i).append("\":1");
        }
        t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true,\"anahtar\":" + cok + "}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("DOGRULAMA"));
    }

    @Test
    void turAyari1_onizlemeTabanlaAyni() throws Exception {
        jdbc.update("UPDATE uygulama_ayar SET deger = 1 WHERE anahtar = 'iyilestir.tur'");
        atamaci().post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true")).andExpect(status().isOk()).andExpect(jsonPath("$.bosSayisi").value(1))
                .andExpect(jsonPath("$.iyilestirme.tabanBosSayisi").value(1)).andExpect(jsonPath("$.iyilestirme.tur").value(1))
                .andExpect(jsonPath("$.iyilestirme.anahtar.kaydirDk").value(0));
    }
}
