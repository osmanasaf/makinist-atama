package com.makinistatama.tanim;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;

class TanimTest extends TestVeritabani {

    @Test
    void ekle_201_listede_kayitSatiri() throws Exception {
        Tarayici y = yonetici();

        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"  Eskişehir \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ad").value("Eskişehir"))
                .andExpect(jsonPath("$.tip").value("GAR"));

        y.get("/api/tanimlar?tip=GAR").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        y.get("/api/tanimlar?tip=HAT").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TANIM_EKLE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void ayniAd_409_turkceDuyarsiz_amaFarkliTipteSerbest() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Eskişehir\"}").andExpect(status().isCreated());

        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"ESKISEHIR\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TANIM_TEKRARI"));
        y.post("/api/tanimlar", "{\"tip\":\"HAT\",\"ad\":\"Eskişehir\"}").andExpect(status().isCreated());
    }

    @Test
    void yenidenAdlandir_200_kayit_cakisma409() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Eskisehr\"}").andExpect(status().isCreated());
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Ankara\"}").andExpect(status().isCreated());

        y.put("/api/tanimlar/1", "{\"ad\":\"Eskişehir\"}").andExpect(status().isOk()).andExpect(jsonPath("$.ad").value("Eskişehir"));
        y.put("/api/tanimlar/1", "{\"ad\":\"ankara\"}").andExpect(status().isConflict());
        y.put("/api/tanimlar/99", "{\"ad\":\"X\"}").andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap(
                "SELECT eski, yeni FROM degisiklik_kaydi WHERE tur = 'TANIM_GUNCELLE'")).containsEntry("eski", "Eskisehr").containsEntry("yeni", "Eskişehir");
    }

    @Test
    void atamaci_okur_yazamaz403() throws Exception {
        yonetici().post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Ankara\"}").andExpect(status().isCreated());
        Tarayici a = atamaci();

        a.get("/api/tanimlar?tip=GAR").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        a.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Sivas\"}").andExpect(status().isForbidden());
        a.put("/api/tanimlar/1", "{\"ad\":\"Sivas\"}").andExpect(status().isForbidden());
    }

    @Test
    void gecersizIstek_400() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\" \"}").andExpect(status().isBadRequest());
        y.post("/api/tanimlar", "{\"ad\":\"X\"}").andExpect(status().isBadRequest());
        y.get("/api/tanimlar?tip=YOK").andExpect(status().isBadRequest());
    }
}
