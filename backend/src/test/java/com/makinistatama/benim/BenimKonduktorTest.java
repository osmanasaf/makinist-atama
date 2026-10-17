package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** F3b MK-91 (FR-09): kondüktör MAKINIST kullanıcı rolüyle girer; görev rol etiketi "Kondüktör", gördüm, izin talebi ve kendi belge özeti (3 satır). */
class BenimKonduktorTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    @Test
    void kondukturBenimEkranlari() throws Exception {
        var v = new AtamaVeri(jdbc);
        long k = v.konduktor("K001");
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long ata = v.atama(sefer, k, "KONDUKTOR");
        v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(k, "K001");

        t.get("/api/benim/gorevler").andExpect(status().isOk()).andExpect(jsonPath("$.gorevler.length()").value(1)).andExpect(jsonPath("$.gorevler[0].rol").value("KONDUKTOR"))
                .andExpect(jsonPath("$.gorevler[0].ozet").value(org.hamcrest.Matchers.startsWith("Kondüktör · ")))
                .andExpect(jsonPath("$.gorevler[0].esMakinist").value("Demo Makinist D001")); // kondüktör asıl makinisti eş görür

        GordumYardimci.gordum(t, ata).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));

        t.post("/api/benim/izinler", "{\"baslangic\":\"" + BUGUN.plusDays(5) + "\",\"bitis\":\"" + BUGUN.plusDays(6) + "\",\"tur\":\"YILLIK\"}").andExpect(status().isCreated());

        String govde = t.get("/api/benim/belgeler").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(govde, "$.belgeler[*].ad")).containsExactlyInAnyOrder("Kişisel emniyet belgesi", "Sağlık raporu", "Psikoteknik raporu");
    }

    @Test
    void makinistinEsiKonduktorOlmaz_makinistCiktisiAyni() throws Exception {
        var v = new AtamaVeri(jdbc);
        long k = v.konduktor("K001");
        long m = v.makinist("D001");
        long m2 = v.makinist("D002");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        v.atama(sefer, k, "KONDUKTOR");
        v.atama(sefer, m, "ASIL");
        v.atama(sefer, m2, "YARDIMCI");
        Tarayici t = makinistGirisli(m, "D001");
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].esMakinist").value("Demo Makinist D002"));
    }
}
