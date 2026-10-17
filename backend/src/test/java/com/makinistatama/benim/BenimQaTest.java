package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** QA (F2a-1) ek senaryoları: sicille giriş, reddedilen talep sonrası yeniden talep, makinistler arası bağımsızlık, '@' içeren sicil. */
class BenimQaTest extends TestVeritabani {

    static final LocalDate BUGUN = BenimGorevTest.BUGUN;

    private static String govde(LocalDate bas, LocalDate bit) {
        return "{\"baslangic\":\"" + bas + "\",\"bitis\":\"" + bit + "\",\"tur\":\"YILLIK\"}";
    }

    @Test
    void sicilleGiris_buyukVeKucukHarfIkisiDeCalisir_yanlisParola401() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        makinistGirisli(m, "D001");

        new Tarayici(mvc).giris("D001", Tarayici.PAROLA).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("MAKINIST"));
        new Tarayici(mvc).giris("d001", Tarayici.PAROLA).andExpect(status().isOk());
        new Tarayici(mvc).giris("D001", "yanlis-parola-123").andExpect(status().isUnauthorized());
    }

    @Test
    void reddedilenIzinAyniAraliktaYenidenTalepEdilir_listedeRedGorunur() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");

        long ilk = ((Number) JsonPath.read(t.post("/api/benim/izinler", govde(BUGUN.plusDays(5), BUGUN.plusDays(9))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(5), BUGUN.plusDays(9))).andExpect(status().isConflict()); // bekleyen varken çakışır

        atamaci().post("/api/izin/" + ilk + "/red", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("RED"));

        t.post("/api/benim/izinler", govde(BUGUN.plusDays(5), BUGUN.plusDays(9))).andExpect(status().isCreated()); // RED sayılmaz
        String liste = t.get("/api/benim/izinler").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(liste, "$[*].durum")).containsExactlyInAnyOrder("RED", "TALEP");
    }

    @Test
    void ikiMakinistAyniAraligiBagimsizTalepEder_birininIzniDigerininListesindeYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        Tarayici ta = makinistGirisli(a, "D001");
        Tarayici tb = makinistGirisli(b, "D002");

        ta.post("/api/benim/izinler", govde(BUGUN.plusDays(3), BUGUN.plusDays(4))).andExpect(status().isCreated());
        tb.post("/api/benim/izinler", govde(BUGUN.plusDays(3), BUGUN.plusDays(4))).andExpect(status().isCreated());
        ta.get("/api/benim/izinler").andExpect(jsonPath("$.length()").value(1));
        tb.get("/api/benim/izinler").andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin WHERE olusturan_kullanici_id = (SELECT id FROM kullanici WHERE makinist_id = ?) AND makinist_id = ?", Integer.class, a, b)).isZero();
    }

    @Test
    void atEslesenSicilliMakinistinHesabiAcilmaz400() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D@01");
        atamaci().post("/api/makinist/" + m + "/hesap", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.sicil").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici WHERE makinist_id = ?", Integer.class, m)).isZero();
    }
}
