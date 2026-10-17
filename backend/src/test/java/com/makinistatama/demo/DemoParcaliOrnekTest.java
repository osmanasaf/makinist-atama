package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F2c (MK-64): isteğe bağlı parçalı tren örneği; temel demo yüklemesi ve sayıları DEĞİŞMEZ, ikinci çağrı 409, veri yokken 409. */
@ActiveProfiles("dev")
class DemoParcaliOrnekTest extends TestVeritabani {

    @Test
    void parcaliOrnek_iki_parca_ikinciCagri409_yuklemeOncesi409_temelSayilarDegismez() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/parcali-ornek", "").andExpect(status().isConflict()); // demo veri yok

        y.post("/api/demo/yukle", "").andExpect(status().isCreated()).andExpect(jsonPath("$.sefer").value(DemoVeriServisi.GUN_SAYISI * (DemoVeriServisi.GUNLUK_SEFER + 10)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE parca_no IS NOT NULL", Integer.class)).as("temel demo yüklemesi parça içermez").isZero();

        y.post("/api/demo/parcali-ornek", "").andExpect(status().isCreated()).andExpect(jsonPath("$.parca").value(2));
        assertThat(jdbc.queryForList("SELECT parca_no || '/' || parca_sayisi || ' ' || tren_no FROM sefer WHERE parca_no IS NOT NULL ORDER BY parca_no", String.class))
                .containsExactly("1/2 DEMO-P1", "2/2 DEMO-P1");
        LocalDate gun = jdbc.queryForObject("SELECT min(tarih) + 1 FROM sefer", LocalDate.class);
        y.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[?(@.trenNo=='DEMO-P1')].parcaNo").value(hasItem(2)));

        y.post("/api/demo/parcali-ornek", "").andExpect(status().isConflict());
        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).isZero();
    }
}
