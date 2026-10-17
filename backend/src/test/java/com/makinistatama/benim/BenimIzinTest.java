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

/** FR-M06: makinist kendi adına izin talebi açar; {@code POST /api/izin} IDOR'u kapalıdır. */
class BenimIzinTest extends TestVeritabani {

    static final LocalDate BUGUN = BenimGorevTest.BUGUN;

    private static String govde(LocalDate bas, LocalDate bit) {
        return "{\"baslangic\":\"" + bas + "\",\"bitis\":\"" + bit + "\",\"tur\":\"YILLIK\"}";
    }

    @Test
    void talep201_olusturanHesapIsaretli_birIzinTalepKaydi_atamaciOnaylar() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");

        long izinId = ((Number) JsonPath.read(t.post("/api/benim/izinler", govde(BUGUN.plusDays(5), BUGUN.plusDays(9))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.durum").value("TALEP")).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long hesap = jdbc.queryForObject("SELECT id FROM kullanici WHERE makinist_id = ?", Long.class, m);
        assertThat(jdbc.queryForObject("SELECT olusturan_kullanici_id FROM izin WHERE id = ?", Long.class, izinId)).isEqualTo(hesap);
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM izin WHERE id = ?", Long.class, izinId)).isEqualTo(m);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'IZIN_TALEP' AND makinist_id = ?", Integer.class, m)).isEqualTo(1);

        atamaci().post("/api/izin/" + izinId + "/onay", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAY")); // KENDI_TALEBI kuralı atamacıyı engellemez
        t.get("/api/benim/izinler").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].durum").value("ONAY"));
        String liste = t.get("/api/benim/izinler").andReturn().getResponse().getContentAsString();
        assertThat(liste).doesNotContain("Ata Maci").doesNotContain("kararVeren").doesNotContain("olusturan");
    }

    @Test
    void cakisma409_gecmisBaslangic400_bitisBaslangictanOnce400() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(5), BUGUN.plusDays(9))).andExpect(status().isCreated());
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(8), BUGUN.plusDays(12))).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("IZIN_CAKISMASI"));
        t.post("/api/benim/izinler", govde(BUGUN.minusDays(1), BUGUN.plusDays(1))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.baslangic").exists());
        t.post("/api/benim/izinler", govde(BUGUN, BUGUN)).andExpect(status().isCreated()); // bugün serbest
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(20), BUGUN.plusDays(19))).andExpect(status().isBadRequest());
    }

    @Test
    void makinistPostApiIzin403_govdedekiYabanciSicilYokSayilir() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        Tarayici t = makinistGirisli(m, "D001");

        t.post("/api/izin", "{\"makinistId\":" + baska + ",\"baslangic\":\"" + BUGUN.plusDays(5) + "\",\"bitis\":\"" + BUGUN.plusDays(6) + "\",\"tur\":\"YILLIK\"}")
                .andExpect(status().isForbidden());
        // benim ucuna fazladan makinistId alanı gönderilse bile talep kendi sicilinedir
        t.post("/api/benim/izinler", "{\"makinistId\":" + baska + ",\"baslangic\":\"" + BUGUN.plusDays(5) + "\",\"bitis\":\"" + BUGUN.plusDays(6) + "\",\"tur\":\"YILLIK\"}")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin WHERE makinist_id = ?", Integer.class, baska)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin WHERE makinist_id = ?", Integer.class, m)).isEqualTo(1);
        t.get("/api/izin").andExpect(status().isForbidden()); // tüm izin listesi personele
    }
}
