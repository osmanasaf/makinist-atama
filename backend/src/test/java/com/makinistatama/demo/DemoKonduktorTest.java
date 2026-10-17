package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F3b MK-92: isteğe bağlı kondüktör örneği (temel demo yüklemesi ve 68 makinist değişmez): 4 kondüktör, 3 atama, ayar 1, K001 hesabı; ikinci çağrı 409; veri yokken 409; sıfırlama ayarı geri alır. */
@ActiveProfiles("dev")
class DemoKonduktorTest extends TestVeritabani {

    @Test
    void ornek_dortKonduktor_ucAtama_ayarAcik_ikinciCagri409_sifirlamaAyariKapatir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/konduktor-ornegi", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("DEMO_YOK"));

        y.post("/api/demo/yukle", "").andExpect(status().isCreated()).andExpect(jsonPath("$.makinist").value(68));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE tur = 'KONDUKTOR'", Integer.class)).as("temel demo kondüktör açmaz").isZero();
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'konduktor.yolcu_trenine_gerekli'", Integer.class)).isZero();

        y.post("/api/demo/konduktor-ornegi", "").andExpect(status().isCreated()).andExpect(jsonPath("$.konduktor").value(4)).andExpect(jsonPath("$.atama").value(3))
                .andExpect(jsonPath("$.hesap.sicil").value("K001")).andExpect(jsonPath("$.hesap.geciciParola").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE tur = 'MAKINIST'", Integer.class)).isEqualTo(68);
        assertThat(jdbc.queryForList("SELECT ad_soyad FROM makinist WHERE tur = 'KONDUKTOR' ORDER BY sicil", String.class))
                .containsExactly("Demo Kondüktör 01", "Demo Kondüktör 02", "Demo Kondüktör 03", "Demo Kondüktör 04");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'KONDUKTOR'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'konduktor.yolcu_trenine_gerekli'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici WHERE rol = 'MAKINIST' AND makinist_id = (SELECT id FROM makinist WHERE sicil = 'K001')", Integer.class)).isEqualTo(1);
        LocalDate bugun = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        assertThat(jdbc.queryForObject("SELECT ehliyet_bitis FROM makinist WHERE sicil = 'K004'", LocalDate.class)).isBefore(bugun.plusDays(20)); // belgesi yakında biten kondüktör

        LocalDate gun = jdbc.queryForObject("SELECT min(tarih) + 1 FROM sefer", LocalDate.class);
        y.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[?(@.makinistler[0].rol=='KONDUKTOR')]").isNotEmpty());
        y.post("/api/demo/konduktor-ornegi", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("DEMO_KONDUKTOR_VAR"));

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'konduktor.yolcu_trenine_gerekli'", Integer.class)).isZero();
    }
}
