package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.MakinistBilgisi;

/** F3b MK-85 (FR-03): özel kural kapsamında personelTurleri API'de: kayıt, özet cümlesi, denetim kaydı, geçersiz değer 400. */
class OzelKuralTurApiTest extends TestVeritabani {

    private static String govde(String kapsam) {
        return OzelKuralTest.govde("Kondüktör günlük 9 s", "TOPLAM_GOREV_SURESI", "GUN", "9", kapsam, "ENGEL", "");
    }

    @Test
    void personelTurleriKaydedilir_ozetteGorunur_geriOkunur() throws Exception {
        yonetici().post("/api/ozel-kural", govde("{\"personelTurleri\":[\"KONDUKTOR\"]}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kapsam.personelTurleri[0]").value("KONDUKTOR"))
                .andExpect(jsonPath("$.ozet").value("Toplam görev süresi (gün) en fazla 9 s · Personel: Kondüktör"));
        assertThat(jdbc.queryForObject("SELECT kapsam FROM ozel_kural", String.class)).contains("\"personelTurleri\":[\"KONDUKTOR\"]");
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'KURAL_EKLE'", String.class)).contains("\"personelTurleri\":[\"KONDUKTOR\"]");
        yonetici().get("/api/ozel-kural").andExpect(status().isOk()).andExpect(jsonPath("$[0].kapsam.personelTurleri[0]").value(MakinistBilgisi.Tur.KONDUKTOR.name()));
    }

    @Test
    void kapsamdaTurYoksa_ozetDegismez_gecersizTur400() throws Exception {
        yonetici().post("/api/ozel-kural", govde(null)).andExpect(status().isCreated()).andExpect(jsonPath("$.ozet").value("Toplam görev süresi (gün) en fazla 9 s"))
                .andExpect(jsonPath("$.kapsam.personelTurleri.length()").value(0));
        yonetici().post("/api/ozel-kural", OzelKuralTest.govde("Başka", "TOPLAM_GOREV_SURESI", "GUN", "9", "{\"personelTurleri\":[\"MANEVRACI\"]}", "ENGEL", "")).andExpect(status().isBadRequest());
    }
}
