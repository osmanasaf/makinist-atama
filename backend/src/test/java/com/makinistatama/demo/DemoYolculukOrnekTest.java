package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F2b: isteğe bağlı demo örnekleri (temel demo yüklemesi değişmez): 3 yolculuk + 1 konaklama; ikinci çağrı 409; veri yokken 409; sıfırlama hepsini siler. */
@ActiveProfiles("dev")
class DemoYolculukOrnekTest extends TestVeritabani {

    @Test
    void ornekler_uc_yolculuk_bir_konaklama_ikinciCagri409_yuklemeOncesi409() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yolculuk-ornekleri", "").andExpect(status().isConflict()); // demo veri yok

        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).as("temel demo yüklemesi atama yazmaz").isZero();
        y.post("/api/demo/yolculuk-ornekleri", "").andExpect(status().isCreated()).andExpect(jsonPath("$.yolculuk").value(3));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'YOLCU'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isEqualTo(1);
        LocalDate gun = jdbc.queryForObject("SELECT min(tarih) + 2 FROM sefer", LocalDate.class);
        y.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[?(@.makinistler[0].rol=='YOLCU')]").isNotEmpty());
        y.post("/api/demo/yolculuk-ornekleri", "").andExpect(status().isConflict());

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }
}
