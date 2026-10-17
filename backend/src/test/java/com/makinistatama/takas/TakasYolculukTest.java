package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F2b MK-45/FR-13: takas ve bırakma YOLCU atamaya KAPALI (tek sorgu süzgeci); komşu yolculuk takasta kural olarak görünür. */
class TakasYolculukTest extends TakasTabani {

    private static Set<Long> kimlikler(String govde) {
        Set<Long> kume = new java.util.HashSet<>();
        for (JsonNode n : new JsonMapper().readTree(govde)) {
            kume.add(n.get("hedefAtamaId").asLong());
        }
        return kume;
    }

    @Test
    void yolcuAtamayla_talepAcilamaz_birakmaDahil_404() throws Exception {
        long a = hesapli("D001");
        long yolcuAta = atama(sefer("E1", gun(3), "08:00", "09:00"), a, "YOLCU");
        long asilAta = atama(sefer("TA", gun(4), "08:00", "09:00"), hesapli("D002"), "ASIL");
        Tarayici ta = giris("D001");

        ta.post("/api/benim/takas", acGovdesi(yolcuAta, asilAta, "IZIN", null)).andExpect(status().isNotFound()); // karşılıklı
        ta.post("/api/benim/takas", acGovdesi(yolcuAta, null, "HASTALIK", null)).andExpect(status().isNotFound()); // bırakma
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isZero();
    }

    @Test
    void yolcuAtama_hedefListesindeGorunmez() throws Exception {
        long a = hesapli("D001");
        long ataA = atama(sefer("TA", gun(3), "08:00", "09:00"), a, "ASIL");
        long iyi = atama(sefer("T1", gun(4), "08:00", "09:00"), hesapli("D002"), "ASIL");
        long yolcu = atama(sefer("E2", gun(4), "10:00", "11:00"), hesapli("D003"), "YOLCU");

        String govde = giris("D001").get("/api/benim/takas/hedefler?atamaId=" + ataA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(kimlikler(govde)).contains(iyi).doesNotContain(yolcu);
    }

    @Test
    void komsuYolculuk_takastaKuralOlarakGorunur_cakisma422_ihlalSahibiD002() throws Exception {
        Sahne s = sahne();
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        // D002 (SB'de) SA gününde 08:30–09:30 YOLCU olarak gidiyor: SA'yı devralırsa yolculukla çakışır (yolculuk komşu olarak kural motorunda görünür)
        atama(sefer("TLB", gun(3), "08:30", "09:30"), s.b(), "YOLCU");

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"))
                .andExpect(jsonPath("$.ihlaller[0].sicil").value("D002")).andExpect(jsonPath("$.ihlaller[0].ihlal.kod").value("K-10"));
        assertThat(sayi("SELECT count(*) FROM atama WHERE makinist_id = ? AND rol <> 'YOLCU'", s.a())).isEqualTo(1);
    }
}
