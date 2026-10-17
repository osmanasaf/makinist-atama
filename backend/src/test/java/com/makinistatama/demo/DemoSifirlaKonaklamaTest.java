package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F2b W1 dersi: demo sıfırlaması yeni tabloyu (konaklama) da boşaltır; yolculuk satırları atama ile birlikte gider. */
@ActiveProfiles("dev")
class DemoSifirlaKonaklamaTest extends TestVeritabani {

    @Test
    void sifirla_konaklamaVeYolculukSatirlariniTemizler() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        long makinist = jdbc.queryForObject("SELECT min(id) FROM makinist", Long.class);
        long gar = jdbc.queryForObject("SELECT min(id) FROM tanim WHERE tip = 'GAR'", Long.class);
        jdbc.update("INSERT INTO konaklama (makinist_id, gar_id, yer_tipi, giris, cikis) VALUES (?, ?, 'OTEL', now(), now() + interval '12 hours')", makinist, gar);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isEqualTo(1);

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class)).isZero();
    }
}
