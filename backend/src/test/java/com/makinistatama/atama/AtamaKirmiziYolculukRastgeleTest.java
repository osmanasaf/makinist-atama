package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MK-5 değişmezi yolculukla (F2b, MK-40/NFR-T2): aday listesinin "uygun" dediği ve POST'un kabul ettiği atama (ASIL ya da YOLCU), mevcut görevleri arasında
 * YOLCU satırları da olan rastgele takvimde, hemen ardından gün listesinde kırmızı OLMAZ. Garlar karışıktır (konum uyarıları çıkar), gecikmeler ve
 * {@code yolculuk.calisma_sayilir=0} da denenir.
 */
class AtamaKirmiziYolculukRastgeleTest extends TestVeritabani {

    static final LocalDate BAS = LocalDate.of(2026, 10, 5);
    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_adayAsil_mevcutYolculuklu() throws Exception {
        dene("ASIL", false, true);
    }

    @Test
    void kabulEdilenYolculukHemenKirmiziOlmaz_gecikmeli() throws Exception {
        dene("YOLCU", true, true);
    }

    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_yolculukSayilmiyorken() throws Exception {
        dene("ASIL", true, false);
        dene("YOLCU", false, false);
    }

    private void dene(String adayRol, boolean gecikmeli, boolean sayilir) throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        if (!sayilir) {
            jdbc.update("UPDATE uygulama_ayar SET deger = 0 WHERE anahtar = 'yolculuk.calisma_sayilir'");
        }
        Tarayici t = atamaci();
        Random r = new Random(gecikmeli ? 21 : 7);
        int kabul = 0;
        int uygunDegil = 0;
        int yolcuMevcut = 0;
        for (int tur = 0; tur < 60; tur++) {
            long m = v.makinist("R" + adayRol.charAt(0) + tur + (sayilir ? "s" : "n") + (gecikmeli ? "g" : "d"));
            int adet = 3 + r.nextInt(6);
            for (int i = 0; i < adet; i++) {
                long sefer = rastgeleSefer(v, r, "M" + tur + "_" + i + adayRol + gecikmeli + sayilir);
                boolean yolcu = r.nextInt(4) == 0;
                yolcuMevcut += yolcu ? 1 : 0;
                if (gecikmeli && r.nextBoolean()) {
                    gecikme(sefer, r);
                }
                v.atama(sefer, m, yolcu ? "YOLCU" : "ASIL");
            }
            long aday = rastgeleSefer(v, r, "A" + tur + adayRol + gecikmeli + sayilir);
            if (gecikmeli && r.nextBoolean()) {
                gecikme(aday, r);
            }
            LocalDate gun = jdbc.queryForObject("SELECT tarih FROM sefer WHERE id = ?", LocalDate.class, aday);

            JsonNode adaylar = json(t.get("/api/atama/sefer/" + aday + "/adaylar?rol=" + adayRol).andReturn().getResponse().getContentAsString());
            boolean uygun = false;
            for (JsonNode a : adaylar) {
                if (a.get("makinistId").asLong() == m) {
                    uygun = a.get("uygun").asBoolean();
                }
            }
            String govde = "{\"makinistId\":" + m + ",\"rol\":\"" + adayRol + "\",\"gerekce\":\"deneme\"}";
            if (!uygun) {
                uygunDegil++;
                t.post("/api/atama/sefer/" + aday, govde).andExpect(status().isUnprocessableEntity()); // aday listesi ile POST tutarlı
                continue;
            }
            t.post("/api/atama/sefer/" + aday, govde).andExpect(status().isCreated());
            kabul++;
            JsonNode liste = json(t.get("/api/atama/gun?tarih=" + gun).andReturn().getResponse().getContentAsString());
            for (JsonNode s : liste.get("satirlar")) {
                if (s.get("seferId").asLong() == aday) {
                    for (JsonNode g : s.get("makinistler")) {
                        if (g.get("makinistId").asLong() == m) {
                            assertThat(g.get("kirmizi").asBoolean()).as("tur %d (%s): kabul edilen atama kırmızı: %s", tur, adayRol, g.get("kirmiziNedenler")).isFalse();
                        }
                    }
                }
            }
        }
        assertThat(kabul).as("kabul edilen senaryo sayısı (testin anlamlı olması için)").isGreaterThan(5);
        assertThat(uygunDegil).isGreaterThan(3);
        assertThat(yolcuMevcut).isGreaterThan(20);
    }

    private void gecikme(long sefer, Random r) {
        int k = 30 * (1 + r.nextInt(6));
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = ?, varis_gecikme_dk = ? WHERE id = ?", k, k + 30 * r.nextInt(3), sefer);
    }

    private long rastgeleSefer(AtamaVeri v, Random r, String ad) {
        LocalDate gun = BAS.plusDays(r.nextInt(21));
        String kalkis = SAATLER[r.nextInt(SAATLER.length)];
        String varis = LocalTime.parse(kalkis).plusMinutes(SURELER[r.nextInt(SURELER.length)]).toString();
        long kalkisGar = r.nextInt(3) == 0 ? v.eskisehir : v.ankara;
        long varisGar = r.nextInt(3) == 0 ? v.eskisehir : v.ankara;
        return v.sefer(ad, gun, kalkis, varis, kalkisGar, varisGar);
    }

    private static JsonNode json(String s) {
        return new JsonMapper().readTree(s);
    }
}
