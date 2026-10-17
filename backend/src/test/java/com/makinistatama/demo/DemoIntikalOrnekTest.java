package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * İ-2 (MK-111): isteğe bağlı serbest intikal örneği (3 gar çifti süresi + 1 intikal); temel demo yüklemesi DEĞİŞMEZ, ikinci çağrı 409, veri yokken 409, sıfırlama siler. Örnekten
 * sonra otomatik atama (7 gün) hiçbir kırmızı atama / kırmızı intikal bırakmaz.
 */
@ActiveProfiles("dev")
class DemoIntikalOrnekTest extends TestVeritabani {

    @Test
    void ornek_ucSure_birIntikal_ikinciCagri409_yuklemeOncesi409_otomatikAtamaKirmiziSifir_sifirlamaSiler() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/intikal-ornek", "").andExpect(status().isConflict()); // demo veri yok

        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).as("temel demo yüklemesi intikal yazmaz").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gar_intikal_suresi", Integer.class)).isZero();

        y.post("/api/demo/intikal-ornek", "").andExpect(status().isCreated()).andExpect(jsonPath("$.sure").value(3)).andExpect(jsonPath("$.intikal").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gar_intikal_suresi", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal WHERE durum = 'PLANLI'", Integer.class)).isEqualTo(1);
        LocalDate ilk = jdbc.queryForObject("SELECT min(tarih) FROM sefer", LocalDate.class);
        y.get("/api/intikal?bas=" + ilk + "&bit=" + ilk.plusDays(2)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].kalkisGar").value("Eskişehir"))
                .andExpect(jsonPath("$[0].varisGar").value("Ankara")).andExpect(jsonPath("$[0].kirmizi").value(false));
        y.get("/api/tanimlar/intikal-sure").andExpect(jsonPath("$.length()").value(3));

        y.post("/api/demo/intikal-ornek", "").andExpect(status().isConflict());

        y.post("/api/atama/otomatik", "{\"bas\":\"" + ilk + "\",\"bit\":\"" + ilk.plusDays(6) + "\",\"uygula\":true}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isPositive();
        y.get("/api/atama/kirmizi-sayisi?bas=" + ilk + "&bit=" + ilk.plusDays(8)).andExpect(jsonPath("$.kirmiziAtama").value(0)).andExpect(jsonPath("$.kirmiziIntikal").value(0));

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gar_intikal_suresi", Integer.class)).isZero();
    }
}
