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
 * F2d QA (07-qa.md): {@link OtomatikAtamaIyilestirmeTest}'in karşılamadığı kenarlar. Aynı kurulum: 101 (hat) 08:00-10:00 ile 102 (hat2) 09:00-11:00 çakışır; bugünkü plan 102'yi
 * boş bırakır, iyileştirme ikisini de doldurur.
 */
class OtomatikAtamaIyilestirmeQaTest extends TestVeritabani {

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
    void doldurulacakSeferKalmadiysa_iyilestirmeKazancsiz_tabanBosSifir_tekTur() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + anahtar(t))).andExpect(status().isOk());
        int once = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);

        t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true")).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(0))
                .andExpect(jsonPath("$.bosSayisi").value(0)).andExpect(jsonPath("$.iyilestirme.tabanBosSayisi").value(0)).andExpect(jsonPath("$.iyilestirme.tur").value(1)).andExpect(jsonPath("$.iyilestirme.durma").value("BOS_YOK"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(once);
    }

    @Test
    void tur0Anahtari_uygulaBugunkuPlanlaAyni() throws Exception {
        jdbc.update("UPDATE uygulama_ayar SET deger = 1 WHERE anahtar = 'iyilestir.tur'"); // tur sınırı 1: önizlemenin anahtarı tur 0 (kaydırma 0, öncelik yok)
        Tarayici t = atamaci();
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + anahtar(t))).andExpect(status().isOk())
                .andExpect(jsonPath("$.atananSayisi").value(1)).andExpect(jsonPath("$.bosSayisi").value(1));
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s101)).isEqualTo(d1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, s102)).isZero();
    }

    /** W4 (F2d tur 2): anahtarı yalnız sunucu üretir (imzalı); uydurulmuş ya da değiştirilmiş anahtar 400, kilit altında hiçbir şey koşmaz. */
    @Test
    void uydurulmusDegistirilmisVeBaskaAraliginAnahtari_400_hicYazilmaz() throws Exception {
        Tarayici t = atamaci();
        int once = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);
        t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true,\"anahtar\":{\"kaydirDk\":60,\"oncelik\":{\"999999999\":5}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_ANAHTARI_GECERSIZ"));
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":{\"kaydirDk\":0,\"oncelik\":{}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_ANAHTARI_GECERSIZ"));
        // geçerli anahtarın ağırlığını ya da kaydırmasını değiştirmek imzayı bozar
        String gecerli = anahtar(t);
        var node = (tools.jackson.databind.node.ObjectNode) JSON.readTree(gecerli);
        node.put("kaydirDk", node.get("kaydirDk").asInt() + 1);
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + node)).andExpect(status().isBadRequest());
        // başka aralığa taşınan anahtar
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-13\",\"bit\":\"2026-10-13\",\"uygula\":true,\"iyilestir\":true,\"anahtar\":" + gecerli + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("IYILESTIRME_ANAHTARI_GECERSIZ"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(once);
        // değişmemiş anahtar kabul
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + gecerli)).andExpect(status().isOk());
    }

    @Test
    void onizlemedenSonraSeferElleAtandi_anahtarliUygulaKirmiziUretmez_elleAtamayaDokunmaz() throws Exception {
        Tarayici t = atamaci();
        String anahtar = anahtar(t);
        v.atama(s101, d1, "ASIL");
        t.post("/api/atama/otomatik", govde(true, ",\"iyilestir\":true,\"anahtar\":" + anahtar)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s101)).isEqualTo(d1);
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-12&bit=2026-10-12").andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void iyilestirmesizYanit_iyilestirmeAlaniYok_yolculukBayragiDegismedi() throws Exception {
        atamaci().post("/api/atama/otomatik", govde(false, ",\"yolculuk\":true")).andExpect(status().isOk()).andExpect(jsonPath("$.iyilestirme").value(nullValue()));
    }

    private String anahtar(Tarayici t) throws Exception {
        String onizleme = t.post("/api/atama/otomatik", govde(false, ",\"iyilestir\":true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(onizleme).get("iyilestirme").get("anahtar").toString();
    }
}
