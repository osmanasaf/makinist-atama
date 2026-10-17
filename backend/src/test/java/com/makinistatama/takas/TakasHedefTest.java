package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** MK-34/7: takas hedef listesi. Kural motoru çalışmaz ve ENGEL'e göre süzülmez (başkasının belge / dinlenme durumu toplu sorgulanamaz); yalnız sicil ve pano bilgisi. */
class TakasHedefTest extends TakasTabani {

    private static Set<Long> kimlikler(String govde) {
        Set<Long> kume = new java.util.HashSet<>();
        for (JsonNode n : new JsonMapper().readTree(govde)) {
            kume.add(n.get("hedefAtamaId").asLong());
        }
        return kume;
    }

    @Test
    void suzgecler_kendiAyniSeferHesapsizPasifAskidaUfukDisiAcikTalepKalkisSiniri() throws Exception {
        long a = hesapli("D001");
        long ataA = atama(sefer("TA", gun(3), "08:00", "09:00"), a, "ASIL");
        long iyi = atama(sefer("T1", gun(4), "08:00", "09:00"), hesapli("D002"), "ASIL");
        long ayniSefer = atama(jdbc.queryForObject("SELECT sefer_id FROM atama WHERE id = ?", Long.class, ataA), hesapli("D003"), "YARDIMCI");
        long hesapsiz = atama(sefer("T3", gun(4), "10:00", "11:00"), v.makinist("D004"), "ASIL");
        long pasif = atama(sefer("T4", gun(4), "12:00", "13:00"), hesapli("D005"), "ASIL");
        jdbc.update("UPDATE makinist SET durum = 'PASIF' WHERE sicil = 'D005'");
        long askida = atama(sefer("T5", gun(4), "14:00", "15:00"), hesapli("D006"), "ASIL");
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE sicil = 'D006'");
        long ufukDisi = atama(sefer("T6", gun(9), "08:00", "09:00"), hesapli("D007"), "ASIL");
        long acikTalepte = atama(sefer("T7", gun(5), "08:00", "09:00"), hesapli("D008"), "ASIL");
        giris("D008").post("/api/benim/takas", acGovdesi(acikTalepte, null, "IZIN", null)).andExpect(status().isCreated());
        long yakin = atama(seferSimdiden("T8", 11, 50), hesapli("D009"), "ASIL");                         // 12 saatten yakın: açılışta kesin 422
        long iptalSefer = atama(sefer("T9", gun(5), "10:00", "11:00"), hesapli("D010"), "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = (SELECT sefer_id FROM atama WHERE id = ?)", iptalSefer);

        String govde = giris("D001").get("/api/benim/takas/hedefler?atamaId=" + ataA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<Long> liste = kimlikler(govde);
        assertThat(liste).contains(iyi).doesNotContain(ataA, ayniSefer, hesapsiz, pasif, askida, ufukDisi, acikTalepte, yakin, iptalSefer);
    }

    @Test
    void kuralMotoruCalismaz_engelOlanAdayDaListelenir_yanitaYalnizSicilVePanoBilgisi() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long ataA = atama(sefer("TA", gun(3), "08:00", "09:00"), a, "ASIL");
        long ataB = atama(sefer("TB", gun(4), "08:00", "09:00"), b, "ASIL");
        atama(sefer("TL", gun(3), "08:30", "09:30"), b, "YARDIMCI"); // B, SA'yı alırsa çakışırdı: ENGEL
        jdbc.update("UPDATE makinist SET ehliyet_bitis = '2020-01-01' WHERE id = ?", b);           // belge de geçersiz

        String govde = giris("D001").get("/api/benim/takas/hedefler?atamaId=" + ataA).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(kimlikler(govde)).as("kuralı bozan aday listeden SÜZÜLMEZ").contains(ataB);
        JsonNode ilk = new JsonMapper().readTree(govde).get(0);
        assertThat(ilk.propertyNames()).containsExactlyInAnyOrder("hedefAtamaId", "sicil", "gorev", "trenNo", "tarih", "kalkisGar", "varisGar", "guncelKalkis");
        assertThat(govde).doesNotContain("Demo Makinist").doesNotContain("ehliyet").doesNotContain("ihlal").doesNotContain("makinistId").doesNotContain("K-0").doesNotContain("K-1");
        java.util.List<String> gorev = JsonPath.read(govde, "$[?(@.hedefAtamaId==" + ataB + ")].gorev");
        assertThat(gorev).hasSize(1);
        assertThat(gorev.getFirst()).contains("Ankara").contains("Asıl");
    }

    @Test
    void yabanciYaDaOlmayanAtamaIcinHedefListesi404() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        atama(sefer("TA", gun(3), "08:00", "09:00"), a, "ASIL");
        long ataB = atama(sefer("TB", gun(4), "08:00", "09:00"), b, "ASIL");
        Tarayici ta = giris("D001");
        ta.get("/api/benim/takas/hedefler?atamaId=" + ataB).andExpect(status().isNotFound());
        ta.get("/api/benim/takas/hedefler?atamaId=987654").andExpect(status().isNotFound());
        ta.get("/api/benim/takas/hedefler").andExpect(status().isBadRequest());
    }

    @Test
    void acilisUcuAyniSuzgeci_listede_olmayanHedef404() throws Exception {
        long a = hesapli("D001");
        long ataA = atama(sefer("TA", gun(3), "08:00", "09:00"), a, "ASIL");
        long hesapsiz = atama(sefer("T3", gun(4), "10:00", "11:00"), v.makinist("D004"), "ASIL");
        giris("D001").post("/api/benim/takas", acGovdesi(ataA, hesapsiz, "IZIN", null)).andExpect(status().isNotFound());
    }
}
