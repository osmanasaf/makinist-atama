package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.demo.SabitTarihliDemo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * F3a-1 uçtan uca (demo 28 gün, 12 Ekim 2026): POST /api/rapor/kadro, yalnız analitik. Elle hesap: ED 1.155 / YHT 735 / Dizel 315 sa/hafta (toplam 2.205), demo
 * haftalık sınır 55 ve %13 devamsızlıkla U = 47,85; 48 kişi (üç sınıf yaklaşımı), yedek 10 -> 12.
 */
class KadroAnalitikTest extends TestVeritabani {

    static final String ISTEK = "{\"bas\":\"2026-10-12\",\"hafta\":4,\"yalnizAnalitik\":true}";
    static final List<String> TABLOLAR = List.of("atama", "sefer", "makinist", "izin", "degisiklik_kaydi", "kural_parametre", "uygulama_ayar", "tanim");

    @Autowired
    ApplicationContext ctx;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void kur() {
        SabitTarihliDemo.yukle(ctx);
    }

    String ham(String istek) throws Exception {
        return yonetici().post("/api/rapor/kadro", istek).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    JsonNode kadro(String istek) throws Exception {
        return json.readTree(ham(istek));
    }

    @Test
    void demo_talepKapasiteAltSinirYedek_elleHesapla() throws Exception {
        JsonNode r = kadro(ISTEK);

        JsonNode v = r.get("varsayim");
        assertThat(v.get("gunSayisi").asInt()).isEqualTo(28);
        assertThat(v.get("bit").asText()).isEqualTo("2026-11-08");
        assertThat(v.get("kisiBasinaSaat").asDouble()).isCloseTo(57.42, within(1e-9)); // OK-24: K-14 KAPALI, haftalık tavan yok: 11 x (7 - 1) x (1 - 0,13)
        assertThat(v.get("haftalikTalepSaat").asDouble()).isCloseTo(2205, within(2205 * 0.02));
        JsonNode talep = r.get("talep");
        assertThat(talep).hasSize(4);
        double yht = 0;
        double ed = 0;
        double dizel = 0;
        for (JsonNode s : talep) {
            switch (s.get("aracTipi").asText()) {
                case "YHT" -> yht += s.get("haftalikSaat").asDouble();
                case "Elektrikli Dizi" -> ed += s.get("haftalikSaat").asDouble();
                default -> dizel += s.get("haftalikSaat").asDouble();
            }
        }
        assertThat(ed).isCloseTo(1155, within(1155 * 0.03));
        assertThat(yht).isCloseTo(735, within(735 * 0.03));
        assertThat(dizel).isCloseTo(315, within(315 * 0.03));

        JsonNode a = r.get("analitik");
        assertThat(a.get("mevcutAsil").asInt()).isEqualTo(66);
        assertThat(a.get("tekBroveliGerekli").asInt()).isBetween(40, 42); // 41 ±1 (OK-24: K-14 KAPALI, U 57,42; eskiden 48)
        assertThat(a.get("gerekliAltSinir").asInt()).isBetween(38, 40); // ölçülen 39 (eskiden 46-49)
        assertThat(a.get("gerekliAltSinir").asInt()).isLessThanOrEqualTo(a.get("tekBroveliGerekli").asInt());
        assertThat(a.get("eksikAltSinir").asInt()).isZero(); // 66 >= 41: mevcut kadro saat/yetki bakımından yeter
        assertThat(a.get("mevcutAsil").asInt() - a.get("gerekliAltSinir").asInt()).isPositive();

        int toplam = 0;
        List<Integer> sayilar = new ArrayList<>();
        for (JsonNode k : r.get("kumeler")) {
            toplam += k.get("mevcut").asInt();
            sayilar.add(k.get("mevcut").asInt());
        }
        assertThat(toplam).isEqualTo(66);
        assertThat(sayilar).containsExactlyInAnyOrder(27, 18, 13, 8); // ED 27, karma 18, YHT 13 + YHT/dizel 8 (= 21)
        int garToplam = 0;
        for (JsonNode g : r.get("garlar")) {
            garToplam += g.get("mevcutMakinist").asInt();
        }
        assertThat(garToplam).isEqualTo(66);

        JsonNode y = r.get("yedek");
        assertThat(y.get("ihtiyac").asInt()).isEqualTo(10);
        assertThat(y.has("havuz")).as("OK-22: yedek devamsızlık içermez, havuz alanı yok").isFalse();
        assertThat(r.get("simulasyon").isNull()).isTrue();
        assertThat(r.get("senaryo").isNull()).isTrue();
    }

    @Test
    void uzmanKumeOnceDolar_esnekKisilerBosKalir() throws Exception {
        // ED'ye özel 27 kişi ED talebini önce taşır; ED'yi de karşılayabilen karma küme (YHT + ED) boşta kalır
        JsonNode r = kadro(ISTEK);
        boolean bulundu = false;
        double karmaKullanim = -1;
        for (JsonNode k : r.get("kumeler")) {
            if (k.get("aracTipleri").size() == 2 && k.get("aracTipleri").toString().contains("Elektrikli Dizi")) {
                karmaKullanim = k.get("kullanimYuzde").asDouble();
            }
        }
        for (JsonNode k : r.get("kumeler")) {
            if (k.get("aracTipleri").size() == 1 && k.get("aracTipleri").get(0).asText().equals("Elektrikli Dizi")) {
                bulundu = true;
                assertThat(k.get("kullanimYuzde").asDouble()).isBetween(70.0, 100.0); // 1155 / (27 x 57,42) = %74,5 (OK-24: K-14 KAPALI, eskiden U 47,85 ile %89)
                assertThat(k.get("kullanimYuzde").asDouble()).as("uzman küme karma kümeden önce dolar").isGreaterThan(karmaKullanim); // OK-24: K-14 KAPALI ile kapasite arttı, ED kümesi artık BOL (eskiden SIKISIK)
            }
        }
        assertThat(bulundu).isTrue();
    }

    @Test
    void yetki_atamaci403_girisYok401_hataliGirdi400() throws Exception {
        atamaci().post("/api/rapor/kadro", ISTEK).andExpect(status().isForbidden());
        new Tarayici(mvc).post("/api/rapor/kadro", ISTEK).andExpect(status().isUnauthorized());
        Tarayici y = yonetici();
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"hafta\":9}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.hafta").exists());
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"hafta\":0}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"devamsizlikYuzde\":60}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.devamsizlikYuzde").exists());
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"devamsizlikYuzde\":-1}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"sureSn\":31}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.sureSn").exists());
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"sureSn\":4}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"hafta\":4}").andExpect(status().isBadRequest()); // bas yok
        y.post("/api/rapor/kadro", "{\"bas\":\"\"}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"bas\":\"12.10.2026\"}").andExpect(status().isBadRequest());
    }

    @Test
    void ayniIstekIkiKez_jsonBirebir_veritabaniDegismez() throws Exception {
        List<Integer> once = sayilar();
        String a = ham(ISTEK);
        String b = ham(ISTEK);
        assertThat(b).isEqualTo(a); // NFR-02
        assertThat(sayilar()).isEqualTo(once); // NFR-01
    }

    private List<Integer> sayilar() {
        List<Integer> s = new ArrayList<>();
        for (String t : TABLOLAR) {
            s.add(jdbc.queryForObject("SELECT count(*) FROM " + t, Integer.class));
        }
        return s;
    }

    @Test
    void varsayilanlar_istekteSadeceBas() throws Exception {
        JsonNode r = kadro("{\"bas\":\"2026-10-12\"}");
        assertThat(r.get("varsayim").get("hafta").asInt()).isEqualTo(4);
        assertThat(r.get("varsayim").get("devamsizlikYuzde").asInt()).isEqualTo(13);
        assertThat(r.get("varsayim").get("sureSn").asInt()).isEqualTo(25);
    }
}
