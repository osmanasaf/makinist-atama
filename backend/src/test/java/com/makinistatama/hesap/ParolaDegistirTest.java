package com.makinistatama.hesap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** FR-M02 / FR-M17: kendi parolasını değiştirme (tüm roller, MK-36). */
class ParolaDegistirTest extends TestVeritabani {

    private static String govde(String eski, String yeni) {
        return "{\"eskiParola\":\"" + eski + "\",\"yeniParola\":\"" + yeni + "\"}";
    }

    @Test
    void kurallar_dokuzKarakter400_ayniParola400_girisAdiniIcermez400() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");

        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "kisa-9-xx")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.yeniParola").exists());
        m.post("/api/auth/parola", govde(Tarayici.PAROLA, Tarayici.PAROLA)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.yeniParola").exists());
        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "xx-D001-yeni-parola")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.yeniParola").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PAROLA_DEGISTI'", Integer.class)).isZero();
    }

    @Test
    void basari_204_bayrakKapanir_eskiOturumDuser_yeniParolaylaGiris() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        kullanicilar.makinistHesabiAc(id, "d001", "Demo Makinist D001", "gecici-parola-123"); // bayrak açık
        Tarayici m = Tarayici.girisYapmis(mvc, "d001", "gecici-parola-123");
        m.get("/api/auth/ben").andExpect(jsonPath("$.parolaDegistirmeli").value(true));

        m.post("/api/auth/parola", govde("gecici-parola-123", "benim-yeni-parolam-9")).andExpect(status().isNoContent());

        m.get("/api/auth/ben").andExpect(status().isUnauthorized()); // parola izi değişti: oturum düştü
        assertThat(jdbc.queryForObject("SELECT parola_degistirmeli FROM kullanici WHERE makinist_id = ?", Boolean.class, id)).isFalse();
        new Tarayici(mvc).giris("d001", "gecici-parola-123").andExpect(status().isUnauthorized());
        Tarayici yeni = new Tarayici(mvc);
        yeni.giris("d001", "benim-yeni-parolam-9").andExpect(status().isOk());
        yeni.get("/api/auth/ben").andExpect(jsonPath("$.parolaDegistirmeli").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PAROLA_DEGISTI' AND makinist_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void yanlisEskiParola_400_sayacaIslenir_besHataKilitler() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");
        for (int i = 1; i <= 4; i++) {
            m.post("/api/auth/parola", govde("yanlis-eski-parola", "benim-yeni-parolam-9")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("PAROLA_HATALI"));
            assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE makinist_id = ?", Integer.class, id)).isEqualTo(i);
        }
        m.post("/api/auth/parola", govde("yanlis-eski-parola", "benim-yeni-parolam-9")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT kilit_bitis IS NOT NULL FROM kullanici WHERE makinist_id = ?", Boolean.class, id)).isTrue();
        new Tarayici(mvc).giris("d001", Tarayici.PAROLA).andExpect(status().isTooManyRequests());
    }

    @Test
    void personelDeDegistirir_csrfBelirteciOlmadan403() throws Exception {
        Tarayici a = atamaci();
        a.post("/api/auth/parola", govde(Tarayici.PAROLA, "atamaci-yeni-parola-1")).andExpect(status().isNoContent());
        new Tarayici(mvc).giris("atamaci@ornek.test", "atamaci-yeni-parola-1").andExpect(status().isOk());

        Tarayici b = atamaci2();
        var yanit = mvc.perform(MockMvcRequestBuilders.post("/api/auth/parola").session(b.oturum).contentType("application/json")
                .content(govde(Tarayici.PAROLA, "baska-yeni-parola-2"))).andReturn().getResponse();
        assertThat(yanit.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(yanit.getContentAsString(), "$.kod")).isEqualTo("GUVENLIK_TOKENI");
    }

    private Tarayici atamaci2() throws Exception {
        return girisli("atamaci2@ornek.test", "Ata Ci Iki", com.makinistatama.kullanici.Rol.ATAMACI);
    }
}
