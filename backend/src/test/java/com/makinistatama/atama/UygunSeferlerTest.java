package com.makinistatama.atama;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;

/** T10.4: iptal seferin boşta kalan makinistine, günün makinistsiz seferlerinden ENGEL'siz olanlar önerilir. */
class UygunSeferlerTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Test
    void yalnizMakinistsiz_iptalOlmayan_cakismayan_seferler() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long iptal = v.seferEv("IPTAL", GUN, "08:00", "10:00");
        v.atama(iptal, m, "ASIL");
        atamaci().post("/api/sefer/" + iptal + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        long bos = v.seferEv("BOS", GUN, "14:00", "16:00"); // makinistsiz, uygun
        long dolu = v.seferEv("DOLU", GUN, "15:00", "17:00"); // zaten ASIL var
        v.atama(dolu, v.makinist("D002"), "ASIL");
        long baskaIptal = v.seferEv("IPTAL2", GUN, "18:00", "19:00");
        atamaci().post("/api/sefer/" + baskaIptal + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        long cakisan = v.seferEv("CAKIS", GUN, "09:00", "11:00"); // makinistin kendi (başka) seferiyle çakışır
        long kendi = v.seferEv("KENDI", GUN, "10:30", "12:30");
        v.atama(kendi, m, "ASIL");

        atamaci().get("/api/atama/makinist/" + m + "/uygun-seferler?gun=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].seferId").value(bos)).andExpect(jsonPath("$[0].trenNo").value("BOS")).andExpect(jsonPath("$[0].uyarilar", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(cakisan).isPositive();
    }

    @Test
    void askidaMakinist_bos_olmayanMakinist404() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long askida = v.makinist("D001", true, "ASKIDA");
        v.seferEv("BOS", GUN, "14:00", "16:00");

        atamaci().get("/api/atama/makinist/" + askida + "/uygun-seferler?gun=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        atamaci().get("/api/atama/makinist/9999/uygun-seferler?gun=" + GUN).andExpect(status().isNotFound());
        atamaci().get("/api/atama/makinist/" + askida + "/uygun-seferler").andExpect(status().isBadRequest());
    }
}
