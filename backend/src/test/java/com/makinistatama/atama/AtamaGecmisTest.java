package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * K-22 (M2 review INFO): bitmiş (geçmiş tarihli) sefere atama. Varsayılan UYARI (gerekçeyle atanır), YONETICI ENGEL ya da KAPALI yapabilir.
 * Testlerde "şimdi" 2026-10-10 10:00 (TestVeritabani, {@code makinist.atama.simdi}).
 */
class AtamaGecmisTest extends TestVeritabani {

    static final LocalDate DUN = LocalDate.of(2026, 10, 9);
    static final LocalDate BUGUN = LocalDate.of(2026, 10, 10);
    static final LocalDate YARIN = LocalDate.of(2026, 10, 11);

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    @Test
    void bitmisSefer_varsayilanUyari_gerekceZorunlu_gerekceyleAtanir_kayitta() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", DUN, "08:00", "10:00");
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + s + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$[0].uygun").value(true))
                .andExpect(jsonPath("$[0].ihlaller[0].kod").value("K-22")).andExpect(jsonPath("$[0].ihlaller[0].siddet").value("UYARI"));
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m + ",\"gerekce\":\"kayıt düzeltmesi\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.uyarilar[0].kod").value("K-22"));

        String json = jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class);
        assertThat(json).contains("\"sonuc\":\"UYARI\"").contains("K-22").contains("kayıt düzeltmesi").contains("\"siddetler\"");
    }

    @Test
    void gelecekVeSurenSefer_k22Yok() throws Exception {
        v.makinist("D001");
        long gelecek = v.sefer("T1", YARIN, "08:00", "10:00");
        long suren = v.sefer("T2", BUGUN, "08:00", "12:00"); // şimdi 10:00: yolda, bitmedi

        for (long s : new long[] { gelecek, suren }) {
            atamaci().get("/api/atama/sefer/" + s + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$[0].ihlaller[*].kod", not(hasItem("K-22"))));
        }
    }

    @Test
    void siddetEngel_422_siddetKapali_ihlalYok() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", DUN, "08:00", "10:00");
        yonetici().put("/api/kural/K-22/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"geçmişe atama yasak\"}").andExpect(status().isOk());

        atamaci().post("/api/atama/sefer/" + s, "{\"makinistId\":" + m + ",\"gerekce\":\"x\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[0].kod").value("K-22"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();

        yonetici().put("/api/kural/K-22/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"serbest\"}").andExpect(status().isOk());
        atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
    }
}
