package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;

/** MK-29: uygulama ayarları (kural_parametre'den ayrı). */
class AyarTest extends TestVeritabani {

    private static String govde(int deger) {
        return "{\"deger\":" + deger + ",\"neden\":\"deneme\"}";
    }

    @Test
    void tohum7_yoneticiGunceller_kayitYazilir_atamaci403_sinirlarReddedilir() throws Exception {
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'makinist.gorunur_gun'", Integer.class)).isEqualTo(7);
        atamaci().put("/api/ayar/makinist.gorunur_gun", govde(5)).andExpect(status().isForbidden());

        var y = yonetici();
        y.put("/api/ayar/makinist.gorunur_gun", govde(5)).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value(5));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND varlik_tipi = 'AYAR' AND eski = '7' AND yeni = '5'", Integer.class)).isEqualTo(1);
        y.put("/api/ayar/makinist.gorunur_gun", govde(0)).andExpect(status().isBadRequest());
        y.put("/api/ayar/makinist.gorunur_gun", govde(31)).andExpect(status().isBadRequest());
        y.put("/api/ayar/makinist.gorunur_gun", "{\"deger\":5}").andExpect(status().isBadRequest()); // neden zorunlu
        y.put("/api/ayar/yok", govde(5)).andExpect(status().isNotFound());
    }

    @Test
    void kuralParametreKatalogu_degismedi() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kural_parametre", Integer.class)).isEqualTo(32);
    }
}
