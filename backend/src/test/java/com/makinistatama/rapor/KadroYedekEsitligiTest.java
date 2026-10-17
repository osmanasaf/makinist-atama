package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.KadroAnalitigi;
import com.makinistatama.demo.SabitTarihliDemo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** MK-74: yedek formülü {@code KadroAnalitigi}'nde kopyadır ({@code atama} {@code rapor}'u göremez); {@code YedekServisi} günlük ihtiyacıyla aynı veride eşit olmalı. */
class KadroYedekEsitligiTest extends TestVeritabani {

    @Autowired
    ApplicationContext ctx;
    @Autowired
    ObjectMapper json;

    @Test
    void gunlukIhtiyac_yedekServisiyleAyni_oranVeMinDegisseDe() throws Exception {
        SabitTarihliDemo.yukle(ctx);
        for (String[] ayarlar : new String[][] { { "0.10", "1" }, { "0.07", "3" }, { "0", "0" }, { "0.333", "5" } }) {
            jdbc.update("UPDATE kural_parametre SET deger = ? WHERE anahtar = 'yedek.oran'", ayarlar[0]);
            jdbc.update("UPDATE kural_parametre SET deger = ? WHERE anahtar = 'yedek.min'", ayarlar[1]);
            for (String ay : new String[] { "2026-10", "2026-11" }) {
                JsonNode r = json.readTree(yonetici().get("/api/rapor/yedek?ay=" + ay).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                for (JsonNode g : r.get("gunler")) {
                    assertThat(g.get("ihtiyac").asInt()).as(ay + " " + g.get("tarih").asText() + " oran " + ayarlar[0])
                            .isEqualTo(KadroAnalitigi.yedekGunIhtiyaci(new BigDecimal(ayarlar[0]), Integer.parseInt(ayarlar[1]), g.get("planliSefer").asInt()));
                }
            }
        }
    }
}
