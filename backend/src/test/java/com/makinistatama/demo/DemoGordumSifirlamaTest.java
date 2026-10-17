package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.TestVeritabani;

/** MK-32 W1: FK'siz {@code gorev_gordum}/{@code atama_olayi} demo sıfırlamada açıkça boşaltılır (atama kimlikleri sıfırlanınca eski onay yeni atamaya yapışmasın). */
@ActiveProfiles("dev")
class DemoGordumSifirlamaTest extends TestVeritabani {

    @Test
    void sifirla_gordumVeOlayTablolariniBosaltir() throws Exception {
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (1, 1, 1, 'o', now())");
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, zaman) VALUES (1, 1, 'KALDIRILDI', 'o', now())");

        yonetici().post("/api/demo/sifirla", "").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM gorev_gordum", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama_olayi", Integer.class)).isZero();
    }
}
