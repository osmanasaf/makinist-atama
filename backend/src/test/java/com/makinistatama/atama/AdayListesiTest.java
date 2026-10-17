package com.makinistatama.atama;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T8.2: aday listesi: soluk (ENGEL'li) adaylar nedenleriyle, sıralama, ASKIDA listede yok. */
class AdayListesiTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Test
    void kirkMakinist_yirmiBeşinde_hatYetkisiYok_yirmiBesSoluk_onBesSecilebilir_askidaYok() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        for (int i = 1; i <= 40; i++) {
            v.makinist(String.format("D%03d", i), i > 25, "AKTIF");
        }
        v.makinist("D900", true, "ASKIDA");
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().get("/api/atama/sefer/" + s + "/adaylar?rol=ASIL").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(40)))
                .andExpect(jsonPath("$[?(@.uygun==true)]", hasSize(15))).andExpect(jsonPath("$[?(@.uygun==false)]", hasSize(25)))
                .andExpect(jsonPath("$[?(@.sicil=='D900')]", hasSize(0)))
                .andExpect(jsonPath("$[0].uygun").value(true)).andExpect(jsonPath("$[14].uygun").value(true)).andExpect(jsonPath("$[15].uygun").value(false))
                .andExpect(jsonPath("$[15].ihlaller[0].metin").value(containsString("Hat yetkisi yok: Ankara–Eskişehir")));
    }

    @Test
    void siralama_buAySaatSonraKmSonraSicil_veBuSeferdeRol() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long c = v.makinist("D003");
        long calisilan = v.sefer("T0", GUN.minusDays(3), "08:00", "12:00"); // 4 saat, 250 km
        v.atama(calisilan, a, "ASIL");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        v.atama(s, c, "YARDIMCI");

        Tarayici t = atamaci();
        t.get("/api/atama/sefer/" + s + "/adaylar").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sicil").value("D002")).andExpect(jsonPath("$[1].sicil").value("D003")).andExpect(jsonPath("$[2].sicil").value("D001"))
                .andExpect(jsonPath("$[2].buAySaat").value(4.0)).andExpect(jsonPath("$[2].buAyKm").value(250.0))
                .andExpect(jsonPath("$[1].buSeferdeRol").value("YARDIMCI")).andExpect(jsonPath("$[0].buSeferdeRol").isEmpty());
        t.get("/api/atama/sefer/999/adaylar").andExpect(status().isNotFound());
        t.get("/api/atama/sefer/" + s + "/adaylar?rol=BILINMEZ").andExpect(status().isBadRequest());
    }

    /** M2 QA BUG-7: sunucu bu ay saatini kayan nokta gürültüsüyle döndürmesin (88 dk = 1,4666... saat). */
    @Test
    void buAySaatVeKm_ikiOndalikYuvarlanir() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        v.atama(v.sefer("T0", GUN.minusDays(3), "08:00", "09:28"), a, "ASIL"); // 88 dk
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().get("/api/atama/sefer/" + s + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$[0].buAySaat").value(1.47));
    }
}
