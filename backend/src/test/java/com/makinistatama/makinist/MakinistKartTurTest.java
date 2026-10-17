package com.makinistatama.makinist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

/** F3b MK-82/83 (FR-01): kart türü oluşturmada seçilir (vars. MAKINIST), kondüktörde bröve olmaz, tür sonradan değişmez; havuz türü zorunlu. */
class MakinistKartTurTest extends TestVeritabani {

    @Autowired
    MakinistServisi servis;

    long ankara;
    long yht;

    @BeforeEach
    void tanimlar() {
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        yht = TestTanimlari.aracTipi(jdbc, "YHT");
    }

    String govde(String sicil, String turAlani, long versiyon, String brove) {
        return """
                {"sicil":"%s","adSoyad":"Demo Kondüktör 01","bagliGarId":%d,"durum":"AKTIF",
                 "ehliyetBitis":"2030-01-01","saglikBitis":"2027-06-01","psikoteknikBitis":"2028-01-01",
                 "versiyon":%d%s%s}""".formatted(sicil, ankara, versiyon, turAlani, brove);
    }

    String brove() {
        return ",\"broveAracTipleri\":[{\"aracTipiId\":%d,\"sinavTarihi\":\"2022-03-10\"}]".formatted(yht);
    }

    @Test
    void turVerilmezseMakinist_konduktorOlusur_listeTurSuzer() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("D001", "", 0, "")).andExpect(status().isCreated()).andExpect(jsonPath("$.tur").value("MAKINIST"));
        t.post("/api/makinist", govde("K001", ",\"tur\":\"KONDUKTOR\"", 0, "")).andExpect(status().isCreated()).andExpect(jsonPath("$.tur").value("KONDUKTOR"));

        t.get("/api/makinist").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        t.get("/api/makinist?tur=KONDUKTOR").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].sicil").value("K001")).andExpect(jsonPath("$[0].tur").value("KONDUKTOR"));
        t.get("/api/makinist?tur=MAKINIST").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].sicil").value("D001"));
    }

    @Test
    void konduktoreBroveGonderilirse400() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("K001", ",\"tur\":\"KONDUKTOR\"", 0, brove()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("KONDUKTOR_BROVESI_OLMAZ"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class)).isZero();
    }

    @Test
    void turSonradanDegistirilemez_ama_turYollanmazsaGuncellemeGecer() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("K001", ",\"tur\":\"KONDUKTOR\"", 0, "")).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'K001'", Long.class);

        t.put("/api/makinist/" + id, govde("K001", ",\"tur\":\"MAKINIST\"", 0, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("TUR_DEGISTIRILEMEZ"));
        t.put("/api/makinist/" + id, govde("K001", "", 0, "")).andExpect(status().isOk()).andExpect(jsonPath("$.tur").value("KONDUKTOR"));
        t.put("/api/makinist/" + id, govde("K001", ",\"tur\":\"KONDUKTOR\"", 1, brove()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("KONDUKTOR_BROVESI_OLMAZ"));
        assertThat(jdbc.queryForObject("SELECT tur FROM makinist WHERE id = ?", String.class, id)).isEqualTo("KONDUKTOR");
    }

    @Test
    void havuzTuruZorunlu_aktifDetaylarTureGoreSuzer() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/makinist", govde("D001", "", 0, "")).andExpect(status().isCreated());
        t.post("/api/makinist", govde("K001", ",\"tur\":\"KONDUKTOR\"", 0, "")).andExpect(status().isCreated());

        assertThat(servis.aktifDetaylar(PersonelTuru.MAKINISTLER)).extracting(MakinistDto::sicil).containsExactly("D001");
        assertThat(servis.aktifDetaylar(PersonelTuru.KONDUKTORLER)).extracting(MakinistDto::sicil).containsExactly("K001");
        assertThat(servis.aktifDetaylar(PersonelTuru.TUMU)).extracting(MakinistDto::sicil).containsExactly("D001", "K001");
        assertThatThrownBy(() -> servis.aktifDetaylar(Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(servis.kimlikler(MakinistDurum.AKTIF, PersonelTuru.KONDUKTORLER)).hasSize(1);
        assertThat(servis.liste(null, null)).hasSize(2); // sicil/ad haritaları tür-bağımsız
    }
}
