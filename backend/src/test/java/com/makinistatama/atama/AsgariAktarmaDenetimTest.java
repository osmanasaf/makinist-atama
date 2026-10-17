package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;

/** K-23 denetim anlık görüntüsü (OK-24): anlık görüntü her zaman tam; varsayılan ENGEL/20 dk kayıtta görünür, KAPALI ise "KAPALI" olarak görünür (eski gizleme hilesi kalktı, MK-100). */
class AsgariAktarmaDenetimTest extends TestVeritabani {

    private String anlikGoruntu() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.seferEv("S1", LocalDate.of(2026, 10, 12), "08:00", "10:00");
        atamaci().post("/api/atama/sefer/" + sefer, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA' ORDER BY id DESC LIMIT 1", String.class);
    }

    @Test
    void kapaliyken_anlikGoruntude_K23_KAPALI_olarak_gorunur() throws Exception {
        jdbc.update("UPDATE kural_siddet SET siddet = 'KAPALI' WHERE kod = 'K-23'");
        jdbc.update("UPDATE kural_parametre SET deger = '0' WHERE anahtar = 'nobet.asgari_aktarma_dk'");

        String a = anlikGoruntu();

        assertThat(a).contains("\"K-23\":\"KAPALI\"").contains("\"nobet.asgari_aktarma_dk\":\"0\"");
    }

    @Test
    void varsayilanda_anlikGoruntude_kod_esik_ve_siddet_var() throws Exception {
        String a = anlikGoruntu();

        assertThat(a).contains("\"K-23\"").contains("\"nobet.asgari_aktarma_dk\":\"20\"").contains("\"K-23\":\"ENGEL\"");
    }
}
