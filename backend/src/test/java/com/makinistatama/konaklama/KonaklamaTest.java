package com.makinistatama.konaklama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F2b MK-43/FR-09: konaklama kaydı CRUD'u: çakışma 409, geçersiz aralık 400, sürüm uyuşmazlığı 409, denetim kaydı, yetki. */
class KonaklamaTest extends TestVeritabani {

    AtamaVeri v;
    Tarayici t;
    long m;
    long sivas;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        sivas = com.makinistatama.TestTanimlari.gar(jdbc, "Sivas");
        t = atamaci();
    }

    private String govde(long makinist, long gar, String giris, String cikis, String ek) {
        return "{\"makinistId\":" + makinist + ",\"garId\":" + gar + ",\"yerTipi\":\"OTEL\",\"giris\":\"" + giris + "\",\"cikis\":\"" + cikis + "\"" + (ek.isEmpty() ? "" : "," + ek) + "}";
    }

    @Test
    void ekle_201_kayitDuser_gececeSayisiGelir() throws Exception {
        t.post("/api/konaklama", govde(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "\"notu\":\"lojman\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sicil").value("D001")).andExpect(jsonPath("$.gar").value("Sivas")).andExpect(jsonPath("$.gece").value(1)).andExpect(jsonPath("$.notu").value("lojman"))
                .andExpect(jsonPath("$.versiyon").value(0));

        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'KONAKLAMA_EKLE'", String.class)).isEqualTo("Sivas · OTEL · 14.10 11:30 → 15.10 06:00");
        t.get("/api/konaklama?bas=2026-10-14&bit=2026-10-14").andExpect(jsonPath("$", hasSize(1)));
        t.get("/api/konaklama?bas=2026-10-16&bit=2026-10-17").andExpect(jsonPath("$", hasSize(0)));
        t.get("/api/konaklama?bas=2026-10-15&bit=2026-10-15&makinistId=999").andExpect(jsonPath("$", hasSize(0)));
        t.get("/api/konaklama?bas=2026-10-15&bit=2026-10-15&makinistId=" + m).andExpect(jsonPath("$", hasSize(1))); // 15'inde sabaha kadar sürüyor: kesişir
    }

    @Test
    void cakisan409_bitisikOlan201_baskaMakinistinCakismasiSorun_degil() throws Exception {
        t.post("/api/konaklama", govde(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isCreated());

        t.post("/api/konaklama", govde(m, sivas, "2026-10-15T05:00:00+03:00", "2026-10-15T09:00:00+03:00", "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("KONAKLAMA_CAKISMASI"));
        t.post("/api/konaklama", govde(m, sivas, "2026-10-15T06:00:00+03:00", "2026-10-15T09:00:00+03:00", "")).andExpect(status().isCreated()); // bitişik: çıkış = giriş
        long baska = v.makinist("D002");
        t.post("/api/konaklama", govde(baska, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isEqualTo(3);
    }

    @Test
    void dogrulamalar_400() throws Exception {
        t.post("/api/konaklama", govde(m, sivas, "2026-10-15T06:00:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isBadRequest()); // çıkış = giriş
        t.post("/api/konaklama", govde(m, sivas, "2026-10-15T06:00:00+03:00", "2026-10-14T06:00:00+03:00", "")).andExpect(status().isBadRequest()); // çıkış < giriş
        t.post("/api/konaklama", govde(m, v.hat, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isBadRequest()); // gar değil, hat
        t.post("/api/konaklama", govde(m, sivas, "1999-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isBadRequest());
        t.post("/api/konaklama", govde(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "\"notu\":\"" + "x".repeat(201) + "\"")).andExpect(status().isBadRequest());
        t.post("/api/konaklama", govde(999, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isNotFound());
        t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + sivas + ",\"yerTipi\":\"CADIR\",\"giris\":\"2026-10-14T11:30:00+03:00\",\"cikis\":\"2026-10-15T06:00:00+03:00\"}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isZero();
    }

    @Test
    void guncelle_surumUyusmazligi409_basariliGuncelleme_kendisiyleCakismaz_sil204() throws Exception {
        t.post("/api/konaklama", govde(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM konaklama", Long.class);

        t.put("/api/konaklama/" + id, govde(m, sivas, "2026-10-14T12:00:00+03:00", "2026-10-15T07:00:00+03:00", "\"versiyon\":5")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        t.put("/api/konaklama/" + id, govde(m, sivas, "2026-10-14T12:00:00+03:00", "2026-10-15T07:00:00+03:00", "")).andExpect(status().isBadRequest()); // sürüm yok
        t.put("/api/konaklama/" + id, govde(m, sivas, "2026-10-14T12:00:00+03:00", "2026-10-15T07:00:00+03:00", "\"versiyon\":0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versiyon").value(1));
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'KONAKLAMA_GUNCELLE'", String.class)).isEqualTo("Sivas · OTEL · 14.10 11:30 → 15.10 06:00");
        t.put("/api/konaklama/999", govde(m, sivas, "2026-10-14T12:00:00+03:00", "2026-10-15T07:00:00+03:00", "\"versiyon\":0")).andExpect(status().isNotFound());

        t.delete("/api/konaklama/" + id).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'KONAKLAMA_SIL'", String.class)).contains("Sivas");
        t.delete("/api/konaklama/" + id).andExpect(status().isNotFound());
    }

    @Test
    void makinistOturumuYazamazOkuyamaz_403() throws Exception {
        Tarayici tm = makinistGirisli(m, "D001");

        tm.get("/api/konaklama?bas=2026-10-14&bit=2026-10-14").andExpect(status().isForbidden());
        tm.post("/api/konaklama", govde(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00", "")).andExpect(status().isForbidden());
        tm.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14").andExpect(status().isForbidden());
        tm.get("/api/konaklama/rapor?ay=2026-10").andExpect(status().isForbidden());
    }
}
