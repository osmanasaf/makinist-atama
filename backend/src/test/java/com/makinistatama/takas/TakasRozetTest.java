package com.makinistatama.takas;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/** FR-M16: rozet verisi. Makinist: kabul / reddetmesi gereken gelen talep sayısı ({@code bekleyenTakas}); atamacı: onay bekleyen talep sayısı ({@code GET /api/takas} → {@code bekleyen}). */
class TakasRozetTest extends TakasTabani {

    @Test
    void makinistRozeti_gelenTalepKabulVeRetVeSureyleDuser_atamaciRozetiKabuldenSonraDoger() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        Tarayici atamaci = atamaci();
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(0));

        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));                    // açan için yanıt beklemiyor
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(1));
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(0));                              // karşı onay aşamasında atamacı rozeti yok

        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(1));

        atamaci.post("/api/takas/" + talep + "/red", "").andExpect(status().isOk());
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(0));
        ta.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("REDDEDILDI"));                 // sonuç, talep durumundan (yeni tablo yok)
    }

    @Test
    void suresiDolanYaDaBayatTalepRozeteSayilmaz() throws Exception {
        Sahne s = sahne();
        Tarayici tb = giris("D002");
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(1));
        jdbc.update("UPDATE takas_talebi SET olusturma = now() - interval '25 hours' WHERE id = ?", talep);
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));
        tb.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("SURESI_DOLDU")).andExpect(jsonPath("$[0].yanitBekliyor").value(false));
    }
}
