package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/**
 * [V12] / NFR-M02: makinist yanıtlarında karşı tarafın kural ayrıntısı (belge, dinlenme, çakışma), adı, kimliği ve açılış nedeni YOKTUR; ayrıntı yalnız atamacı ayrıntı ekranındadır.
 * Üçüncü bir makinist talebe hiçbir yoldan ulaşamaz.
 */
class TakasSizintiTest extends TakasTabani {

    @Test
    void makinistYanitlarindaKuralAyrintisiAdKimlikVeNedenYok_ayrintiYalnizAtamacida() throws Exception {
        Sahne s = sahne();
        hesapli("D003");
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        Tarayici tc = giris("D003");
        String acma = ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "HASTALIK", "gizli not")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long talep = ((Number) com.jayway.jsonpath.JsonPath.read(acma, "$.id")).longValue();
        // takas sonrası B için ihlal doğsun (talep açıldıktan sonra): onay ekranı bunu gösterir, makinist yanıtları göstermez
        atama(sefer("TL", gun(3), "08:30", "09:30"), s.b(), "ASIL");

        String bListe = tb.get("/api/benim/takas").andReturn().getResponse().getContentAsString();
        String bKabul = tb.post("/api/benim/takas/" + talep + "/kabul", "").andReturn().getResponse().getContentAsString();
        String aListe = ta.get("/api/benim/takas").andReturn().getResponse().getContentAsString();
        for (String yanit : new String[] { acma, bListe, bKabul, aListe }) {
            assertThat(yanit).doesNotContain("ihlal").doesNotContain("K-10").doesNotContain("ehliyet").doesNotContain("Demo Makinist").doesNotContain("makinistId")
                    .doesNotContain("adSoyad").doesNotContain("kaynakMakinistId").doesNotContain("hedefMakinistId");
        }
        assertThat(bListe).as("karşı tarafa neden / not gösterilmez").doesNotContain("gizli not").doesNotContain("HASTALIK");

        atamaci().get("/api/takas/" + talep).andExpect(status().isOk()).andExpect(jsonPath("$.talep.nedenNotu").value("gizli not")).andExpect(jsonPath("$.ihlaller[0].sicil").value("D002"))
                .andExpect(jsonPath("$.ihlaller[0].ihlal.kod").value("K-10"));

        tc.get("/api/benim/takas").andExpect(jsonPath("$.length()").value(0));
        tc.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isNotFound());
        tc.post("/api/benim/takas/" + talep + "/red", "").andExpect(status().isNotFound());
        tc.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isNotFound());
        tc.get("/api/takas/" + talep).andExpect(status().isForbidden());
    }

    @Test
    void ucuncuMakinistinHedefListesindeBaskasininTalepAtamalariGorunmez() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        long ataC = atama(sefer("TC", gun(5), "08:00", "09:00"), c, "ASIL");
        giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated());
        String liste = giris("D003").get("/api/benim/takas/hedefler?atamaId=" + ataC).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(liste).as("açık talepteki iki atama hedef olamaz").isEqualTo("[]");
    }
}
