package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * F3a-3 "ne olurdu": sanal seferler bellekte açılır (DB'ye yazılmaz). Demo: Ankara–Sivas Elektrikli Dizi günde +4 sefer (2 gidiş + 2 dönüş, 330 dk): ek talep 154 sa/hafta,
 * analitik fark +3 (25 -> 28). Senaryo >= taban yapısaldır; kesin ek makinist sayısı ölçülür (04-gelistirme.md), testte yalnız aralık.
 */
class KadroSenaryoTest extends TestVeritabani {

    @Autowired
    ApplicationContext ctx;
    @Autowired
    ObjectMapper json;
    @Autowired
    KadroTahminServisi servis;

    long hat;
    long ed;
    long ankara;
    long sivas;

    @BeforeEach
    void kur() {
        SabitTarihliDemo.yukle(ctx);
        hat = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'HAT' AND ad LIKE 'Ankara%Sivas'", Long.class);
        ed = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'ARAC_TIPI' AND ad = 'Elektrikli Dizi'", Long.class);
        ankara = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = 'Ankara'", Long.class);
        sivas = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = 'Sivas'", Long.class);
    }

    private String satir(long kalkis, long varis, String saatler, int sure) {
        return "{\"hatId\":" + hat + ",\"aracTipiId\":" + ed + ",\"kalkisGarId\":" + kalkis + ",\"varisGarId\":" + varis + ",\"saatler\":[" + saatler + "],\"sureDk\":" + sure + "}";
    }

    private String istek(int hafta, boolean yalnizAnalitik, String... seferler) {
        return "{\"bas\":\"2026-10-12\",\"hafta\":" + hafta + ",\"sureSn\":30,\"yalnizAnalitik\":" + yalnizAnalitik + ",\"seferler\":[" + String.join(",", seferler) + "]}";
    }

    private String edArti4(int hafta, boolean yalnizAnalitik) {
        return istek(hafta, yalnizAnalitik, satir(ankara, sivas, "\"06:00\",\"14:00\"", 330), satir(sivas, ankara, "\"06:00\",\"14:00\"", 330));
    }

    private JsonNode post(String govde) throws Exception {
        return json.readTree(yonetici().post("/api/rapor/kadro", govde).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static void sureleriSil(JsonNode n) {
        if (n instanceof ObjectNode o) {
            o.remove("sureMs");
            o.properties().forEach(e -> sureleriSil(e.getValue()));
        } else if (n.isArray()) {
            n.forEach(KadroSenaryoTest::sureleriSil);
        }
    }

    @Test
    void analitikSenaryo_ekTalep154_fark3_seferTablosuDegismez() throws Exception {
        int once = jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class);

        JsonNode r = post(edArti4(4, true));

        JsonNode s = r.get("senaryo");
        assertThat(r.get("simulasyon").isNull()).isTrue();
        assertThat(s.get("ekSeferSayisi").asInt()).isEqualTo(4 * 28);
        assertThat(s.get("ekHaftalikSaat").asDouble()).isBetween(154 * 0.97, 154 * 1.03);
        assertThat(s.get("ekTalepKisiEsdegeri").asDouble()).isBetween(154 * 0.97 / 57.42, 154 * 1.03 / 57.42); // ek talep kişi-eşdeğeri (2,7): K-14 KAPALI, U = 11 x 6 x 0,87 = 57,42 (OK-24)
        assertThat(s.get("atilKapasiteKisiEsdegeri").asDouble()).as("mevcut 66 kişinin saat bakımından atıl kapasitesi").isGreaterThan(5);
        assertThat(s.has("analitikFarkKisi")).as("OK-22: çelişkili 'analitik fark kişi' alanı kalktı").isFalse();
        assertThat(s.get("mevcutYeter").asBoolean()).isTrue(); // 45 ED-yetkili kişi, yeterli saat
        assertThat(s.get("gerekce").asText()).contains("Elektrikli Dizi").contains("yetkili 45 kişi");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).isEqualTo(once); // sanal seferler DB'ye yazılmaz
    }

    @Test
    void simulasyonSenaryosu_ayniKadrodaToplamBosFarki_tabanKismiSenaryosuzIstekleAyni() throws Exception {
        JsonNode taban = post(istek(2, false));
        JsonNode sen = post(edArti4(2, false));

        JsonNode s = sen.get("senaryo");
        System.out.println("KADRO senaryo (2 hafta, ED +4/gün): " + s);
        // AYNI mevcut kadro, taban ve senaryo: fark toplam boş farkıdır (yeni seferlerin boşu değil)
        assertThat(s.get("taban").get("bos").asInt()).isEqualTo(taban.get("simulasyon").get("mevcut").get("bos").asInt());
        assertThat(s.get("bosFarki").asInt()).isEqualTo(s.get("senaryo").get("bos").asInt() - s.get("taban").get("bos").asInt());
        assertThat(s.get("senaryo").get("seferSayisi").asInt()).isEqualTo(s.get("taban").get("seferSayisi").asInt() + s.get("ekSeferSayisi").asInt());
        int esik = Math.max(5, (int) Math.ceil(0.004 * s.get("taban").get("seferSayisi").asInt()));
        assertThat(s.get("gurultuEsigi").asInt()).isEqualTo(esik);
        assertThat(s.get("mevcutYeter").asBoolean()).as("yeter ⇔ artış gürültü eşiğinin altında").isEqualTo(s.get("bosFarki").asInt() <= esik);
        assertThat(s.get("ekSeferSayisi").asInt()).isEqualTo(4 * 14);
        assertThat(s.get("gerekce").asText()).contains("toplam boş sefer").contains("kişi-eşdeğeri");
        if (s.get("egriFarki").isNull()) {
            assertThat(s.get("uyari").asText()).contains("eğri farkı hesaplanmadı");
        } else {
            assertThat(s.get("egriFarki")).hasSize(3);
            assertThat(s.get("tamamlandi").asBoolean()).isTrue();
        }
        assertThat(sen.toString()).doesNotContain("SANAL");

        // senaryosuz istek ile senaryolu isteğin taban kısmı aynı (talep, analitik, yedek, taban kademeleri)
        assertThat(sen.get("analitik")).isEqualTo(taban.get("analitik"));
        assertThat(sen.get("yedek")).isEqualTo(taban.get("yedek"));
        assertThat(sen.get("simulasyon").get("egri")).isEqualTo(taban.get("simulasyon").get("egri"));
        assertThat(sen.get("simulasyon").get("hedefler")).isEqualTo(taban.get("simulasyon").get("hedefler"));
        assertThat(sen.get("simulasyon").get("mevcut").get("bos")).isEqualTo(taban.get("simulasyon").get("mevcut").get("bos"));
        // varsayim.haftalikTalepSaat TABANDIR (ek talep ayrı alanda)
        assertThat(sen.get("varsayim").get("haftalikTalepSaat")).isEqualTo(taban.get("varsayim").get("haftalikTalepSaat"));
    }

    @Test
    void agirSenaryo_mevcutKadroYetmez_farkPozitif_belirleyici() throws Exception {
        // Ankara–Sivas ED günde 48 ek sefer (2 satır x 24 saat): ED talebi mevcut ED kapasitesini aşar
        StringBuilder saatler = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            saatler.append(h == 0 ? "" : ",").append(String.format("\"%02d:00\"", h));
        }
        String govde = istek(1, false, satir(ankara, sivas, saatler.toString(), 330), satir(sivas, ankara, saatler.toString(), 330));

        JsonNode a = post(govde);
        JsonNode s = a.get("senaryo");
        System.out.println("KADRO ağır senaryo (1 hafta): " + s);
        assertThat(s.get("mevcutYeter").asBoolean()).isFalse();
        assertThat(s.get("bosFarki").asInt()).isGreaterThan(s.get("gurultuEsigi").asInt());
        assertThat(s.get("ekTalepKisiEsdegeri").asDouble()).isGreaterThan(s.get("atilKapasiteKisiEsdegeri").asDouble());
        assertThat(s.get("gerekce").asText()).contains("yetmez");
        JsonNode b = post(govde);
        sureleriSil(a);
        sureleriSil(b);
        if (s.get("tamamlandi").asBoolean() && b.get("senaryo").get("tamamlandi").asBoolean()) {
            assertThat(b).as("belirleyicilik").isEqualTo(a);
        }
    }

    @Test
    void girdiDogrulama_400_alanAdiyla() throws Exception {
        Tarayici y = yonetici();
        long gecersiz = 999_999;
        String ok = satir(ankara, sivas, "\"06:00\"", 330);
        // olmayan hat / araç tipi / gar; yanlış türde tanım (hat kimliği gar yerine)
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"hatId\":" + hat, "\"hatId\":" + gecersiz))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar['seferler[0].hatId']").exists());
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"aracTipiId\":" + ed, "\"aracTipiId\":" + gecersiz))).andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"kalkisGarId\":" + ankara, "\"kalkisGarId\":" + hat))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar['seferler[0].kalkisGarId']").exists());
        // süre <= 0, > 1440; saat listesi boş / 25 saat; km; tren türü
        y.post("/api/rapor/kadro", istek(1, true, satir(ankara, sivas, "\"06:00\"", 0))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar['seferler[0].sureDk']").exists());
        y.post("/api/rapor/kadro", istek(1, true, satir(ankara, sivas, "\"06:00\"", -5))).andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", istek(1, true, satir(ankara, sivas, "\"06:00\"", 1441))).andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", istek(1, true, satir(ankara, sivas, "", 330))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar['seferler[0].saatler']").exists());
        StringBuilder yirmiBes = new StringBuilder();
        for (int h = 0; h < 25; h++) {
            yirmiBes.append(h == 0 ? "" : ",").append(String.format("\"%02d:%02d\"", h % 24, h));
        }
        y.post("/api/rapor/kadro", istek(1, true, satir(ankara, sivas, yirmiBes.toString(), 330))).andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"sureDk\"", "\"km\":-1,\"sureDk\""))).andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"sureDk\"", "\"trenTuru\":\"X\",\"sureDk\""))).andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"saatler\"", "\"gunler\":[\"CUMARTESI\"],\"saatler\""))).andExpect(status().isBadRequest()); // geçersiz enum
        // geçerli ek alanlar kabul edilir
        y.post("/api/rapor/kadro", istek(1, true, ok.replace("\"sureDk\"", "\"km\":603,\"trenTuru\":\"YOLCU\",\"gunler\":[\"MONDAY\",\"FRIDAY\"],\"sureDk\""))).andExpect(status().isOk());

        // 10'dan çok satır; 3.000'den çok sanal sefer
        List<String> onBir = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onBir.add(ok);
        }
        y.post("/api/rapor/kadro", istek(1, true, onBir.toArray(new String[0]))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.seferler").exists());
        StringBuilder yirmiDort = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            yirmiDort.append(h == 0 ? "" : ",").append(String.format("\"%02d:00\"", h));
        }
        List<String> cok = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cok.add(satir(ankara, sivas, yirmiDort.toString(), 60)); // 10 x 24 x 56 gün = 13.440
        }
        y.post("/api/rapor/kadro", istek(8, true, cok.toArray(new String[0]))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.seferler").exists());
        // 3.000'e kadar kabul: 4 satir x 24 saat x 28 gün = 2.688
        y.post("/api/rapor/kadro", istek(4, true, cok.subList(0, 4).toArray(new String[0]))).andExpect(status().isOk());
    }

    @Test
    void gunlerSuzgeci_yalnizSecilenGunlerdeAcilir() throws Exception {
        // 4 hafta, yalnız Pazartesi: 4 gün x 1 saat
        String govde = istek(4, true, satir(ankara, sivas, "\"06:00\"", 330).replace("\"saatler\"", "\"gunler\":[\"MONDAY\"],\"saatler\""));
        assertThat(post(govde).get("senaryo").get("ekSeferSayisi").asInt()).isEqualTo(4);
        assertThat(LocalDate.of(2026, 10, 12).getDayOfWeek().name()).isEqualTo("MONDAY");
    }
}
