package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * MK-32 W1 + şema: FK'siz tablolarda kimlik yeniden kullanımı. Eski onay satırı başka makinistten/seferden kalmışsa (yedekten dönüş, yarım sıfırlama) yeni atamaya
 * YAPIŞMAZ; makinist "gördüm" derse satır yerinde güncellenir (UNIQUE atama_id: tek satır).
 */
class GordumKimlikTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    @Test
    void baskaMakinistinKalanOnayi_yeniAtamayaYapismaz_gordumYerindeGuncellenir() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long eski = v.makinist("D002");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        // atama kimliği yeniden kullanılmış gibi: aynı atama_id'li onay, ÖZETİ AYNI ama başka makinistin
        String ozet = com.jayway.jsonpath.JsonPath.read(t.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString(), "$.gorevler[0].ozet");
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (?, ?, ?, ?, now())", atama, eski, sefer, ozet);

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM gorev_gordum WHERE atama_id = ?", Integer.class, atama)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM gorev_gordum WHERE atama_id = ?", Long.class, atama)).isEqualTo(m);
    }

    @Test
    void baskaSefereAitOnay_gecersiz() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long baskaSefer = v.sefer("T2", BUGUN.plusDays(1), "12:00", "14:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        String ozet = com.jayway.jsonpath.JsonPath.read(t.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString(), "$.gorevler[0].ozet");
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (?, ?, ?, ?, now())", atama, m, baskaSefer, ozet);

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
    }

    @Test
    void sema_atamaIdTekil_olayTuruSinirli_uyariSaatiTohumlu() {
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (1, 1, 1, 'o', now())");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (1, 2, 2, 'o', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, zaman) VALUES (1, 1, 'BILINMEYEN', 'o', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, zaman) VALUES (1, 1, 'KALDIRILDI', 'o', now())");
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'gordum.uyari_saat'", Integer.class)).isEqualTo(12);
    }
}
