package com.makinistatama.hesap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * 05-review WARNING 1: oturumlu kaba kuvvet. Yanlış eski parola girişleri giriş kilidiyle AYNI sayaca işler; kilitliyken parola değişimi (doğru eski parolayla bile)
 * reddedilir ve kilit süresi uzatılmaz. INFO 2: makinistin seçtiği parola en az bir harf ve bir rakam içerir.
 */
class ParolaKilitTest extends TestVeritabani {

    private static String govde(String eski, String yeni) {
        return "{\"eskiParola\":\"" + eski + "\",\"yeniParola\":\"" + yeni + "\"}";
    }

    @Test
    void besYanlisEskiParola_kilitler_doguruyla_bile429_kilitUzamaz() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");

        for (int i = 0; i < 5; i++) {
            m.post("/api/auth/parola", govde("yanlis-parola-" + i, "benim-yeni-parolam-9")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("PAROLA_HATALI"));
        }
        Timestamp kilit = jdbc.queryForObject("SELECT kilit_bitis FROM kullanici WHERE makinist_id = ?", Timestamp.class, id);
        assertThat(kilit).as("5. hatada kilit konur").isNotNull();

        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "benim-yeni-parolam-9")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.kod").value("COK_FAZLA_DENEME"));
        m.post("/api/auth/parola", govde("yanlis-parola-x", "benim-yeni-parolam-9")).andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT kilit_bitis FROM kullanici WHERE makinist_id = ?", Timestamp.class, id)).as("kilit süresi uzatılmaz").isEqualTo(kilit);
        assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE makinist_id = ?", Integer.class, id)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PAROLA_DEGISTI'", Integer.class)).isZero();
    }

    @Test
    void kilitBitinceParolaDegistirilebilir() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");
        for (int i = 0; i < 5; i++) {
            m.post("/api/auth/parola", govde("yanlis-parola-" + i, "benim-yeni-parolam-9")).andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE kullanici SET kilit_bitis = now() - interval '1 minute', parola_kilit_bitis = now() - interval '1 minute' WHERE makinist_id = ?", id); // iki kilit de süresini doldurdu (V18: parola değişimi sınırı ayrı)

        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "benim-yeni-parolam-9")).andExpect(status().isNoContent());
    }

    @Test
    void yeniParola_enAzBirHarfVeBirRakam() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");

        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "yalnizharflerburada")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.yeniParola").exists());
        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "1234567890123")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.yeniParola").exists());
        m.post("/api/auth/parola", govde(Tarayici.PAROLA, "benim-yeni-parolam-9")).andExpect(status().isNoContent());
    }
}
