package com.makinistatama.takas;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/** MK-95: kapanmış taleplerin listede kalma günü {@code takas.kapanmis_gun} ayarıdır (varsayılan 7). */
class TakasKapanmisGunTest extends TakasTabani {

    @Test
    void sekizGunlukKapanmisTalep_varsayilanda_gizli_ayar10iken_gorunur() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        atamaci().post("/api/takas/" + talep + "/red", "").andExpect(status().isOk());
        jdbc.update("UPDATE takas_talebi SET olusturma = now() - interval '8 days' WHERE id = ?", talep);

        ta.get("/api/benim/takas").andExpect(jsonPath("$.length()").value(0));
        atamaci().get("/api/takas").andExpect(jsonPath("$.talepler.length()").value(0));
        yonetici().put("/api/ayar/takas.kapanmis_gun", "{\"deger\":10,\"neden\":\"deneme\"}").andExpect(status().isOk());
        ta.get("/api/benim/takas").andExpect(jsonPath("$.length()").value(1));
        atamaci().get("/api/takas").andExpect(status().isOk()).andExpect(jsonPath("$.talepler.length()").value(1));
    }
}
