package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** 05-review Tur 2 W1: "gördüm" makinistin EKRANDA gördüğü özeti onaylar; sunucudaki güncel özetle uyuşmazsa 409 (güncel görev döner) ve hiçbir şey yazılmaz. */
class GordumBayatEkranTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    @Test
    void bayatEkran_gecikmeSonrasiEskiOzetleGordum_409_guncelGorevDoner_satirVeKayitYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:10", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        String ekrandaki = com.jayway.jsonpath.JsonPath.read(t.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString(), "$.gorevler[0].ozet");

        atamaci().post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":90,\"varisGecikmeDk\":90,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        GordumYardimci.gordum(t, atama, ekrandaki).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("OZET_DEGISTI"))
                .andExpect(jsonPath("$.gorev.atamaId").value(atama)).andExpect(jsonPath("$.gorev.ozet").value(org.hamcrest.Matchers.containsString("09:40")))
                .andExpect(jsonPath("$.gorev.gordu").value("YENI"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gorev_gordum", Integer.class)).as("eşleşmeyen onay yazılmaz").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM'", Integer.class)).isZero();

        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU")); // güncel kartı görüp basınca geçer
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'GORDUM'", String.class)).contains("09:40");
    }

    @Test
    void zatenGoruldusePekBayatOzet409_govdesizIstek400() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long atama = v.atama(v.sefer("T1", BUGUN.plusDays(1), "08:10", "10:00"), m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        GordumYardimci.gordum(t, atama).andExpect(status().isOk());

        GordumYardimci.gordum(t, atama, "Asıl · eski özet").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("OZET_DEGISTI"));
        t.post("/api/benim/gorevler/" + atama + "/gordum", "").andExpect(status().isBadRequest());
        t.post("/api/benim/gorevler/" + atama + "/gordum", "{}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gorev_gordum", Integer.class)).isEqualTo(1);
    }

    @Test
    void yabanciAtamadaOzetSorulmadan404() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        long yabanci = v.atama(v.sefer("TB", BUGUN.plusDays(1), "08:00", "10:00"), baska, "ASIL");
        GordumYardimci.gordum(makinistGirisli(m, "D001"), yabanci, "Asıl · herhangi").andExpect(status().isNotFound());
    }
}
