package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** 05-review Tur 2 INFO: FK'siz {@code gorev_gordum} satırı, atama silinince (değiştir / kaldır / takas) aynı işlemde temizlenir; denetim kaydı (GORDUM) kalır. */
class GordumOksuzTemizlikTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    private int onay() {
        return jdbc.queryForObject("SELECT count(*) FROM gorev_gordum", Integer.class);
    }

    @Test
    void degistirVeKaldir_eskiAtamaninOnayiniSiler_denetimKaydiKalir() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long atama = v.atama(sefer, m1, "ASIL");
        Tarayici t = makinistGirisli(m1, "D001");
        GordumYardimci.gordum(t, atama).andExpect(status().isOk());
        assertThat(onay()).isEqualTo(1);

        Tarayici atamaci = atamaci();
        atamaci.post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + m2 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk());
        assertThat(onay()).as("değiştirilen atamanın onayı silindi").isZero();

        long yeni = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sefer);
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (?, ?, ?, 'o', now())", yeni, m2, sefer);
        atamaci.delete("/api/atama/sefer/" + sefer + "?neden=IZIN").andExpect(status().isNoContent());
        assertThat(onay()).as("kaldırılan atamanın onayı silindi").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM'", Integer.class)).as("denetim kaydı kalır").isEqualTo(1);
    }
}
