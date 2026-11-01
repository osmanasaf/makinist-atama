package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.atama.AtamaVeri;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** T1.5 (FR-2.1, AK-2.1): dışa aktarma katalogdan türer; değerler API ile aynı; K-10 yok; özel kural kapsamı tanım ADIYLA. */
class ProfilDisaAktarTest extends ProfilTestTabani {

    @Test
    void tumKatalogDisaAktarilir_32_22_22_degerlerAPIIleAyni_K10Yok() throws Exception {
        Tarayici y = yonetici();
        y.get("/api/profil/disa-aktar").andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        ObjectNode paket = disaAktar(y);

        assertThat(paket.get("format").asString()).isEqualTo("makinist-atama-profil");
        assertThat(paket.get("profilSurumu").asInt()).isEqualTo(1);
        assertThat(paket.get("etiket").asString()).contains("kurum mevzuatıyla doğrulanmalı");
        assertThat(paket.get("ureten").asString()).isEqualTo("Yöne Tici");
        assertThat(paket.get("uretimZamani").asString()).isNotBlank();
        assertThat(bolum(paket, "parametreler").size()).isEqualTo(32).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM kural_parametre", Integer.class));
        assertThat(bolum(paket, "siddetler").size()).isEqualTo(22).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM kural_siddet", Integer.class));
        assertThat(bolum(paket, "ayarlar").size()).isEqualTo(34).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM uygulama_ayar", Integer.class));
        assertThat(bolum(paket, "siddetler").has("K-10")).isFalse();

        // değerler API'deki ile aynı
        JsonNode apiParam = JSON.readTree(y.get("/api/parametre").andReturn().getResponse().getContentAsString());
        for (JsonNode p : apiParam) {
            assertThat(bolum(paket, "parametreler").get(p.get("anahtar").asString()).asString()).isEqualTo(p.get("deger").asString());
        }
        JsonNode apiKural = JSON.readTree(y.get("/api/kural").andReturn().getResponse().getContentAsString());
        for (JsonNode k : apiKural) {
            assertThat(bolum(paket, "siddetler").get(k.get("kod").asString()).asString()).isEqualTo(k.get("siddet").asString());
        }
        JsonNode apiAyar = JSON.readTree(y.get("/api/ayar").andReturn().getResponse().getContentAsString());
        for (JsonNode a : apiAyar) {
            assertThat(bolum(paket, "ayarlar").get(a.get("anahtar").asString()).asInt()).isEqualTo(a.get("deger").asInt());
        }
    }

    @Test
    void degisenDeger_dosyayaYansir() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"9\",\"neden\":\"deneme\"}").andExpect(status().isOk());

        assertThat(bolum(disaAktar(y), "parametreler").get("dinlenme.ev_saat").asString()).isEqualTo("9");
    }

    @Test
    void ozelKural_kapsamTanimAdiylaDisaAktarilir_kapaliKuralDaDahil() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Günlük sınır", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        y.post("/api/ozel-kural", ozelKuralGovdesi("Kapalı kural", "9", "KAPALI", v.eskisehir, v.hat)).andExpect(status().isCreated());

        JsonNode kurallar = disaAktar(y).get("ozelKurallar");

        assertThat(kurallar.size()).isEqualTo(2);
        JsonNode k = kurallar.get(0);
        assertThat(k.get("ad").asString()).isEqualTo("Günlük sınır");
        assertThat(k.get("kapsam").get("kalkisGarlari").get(0).asString()).isEqualTo("Ankara");
        assertThat(k.get("kapsam").get("hatlar").get(0).asString()).isEqualTo("Ankara–Eskişehir");
        assertThat(k.get("kapsam").toString()).as("kimlik değil ad").doesNotContain("\"" + v.ankara + "\"");
        assertThat(kurallar.get(1).get("siddet").asString()).isEqualTo("KAPALI");
    }
}
