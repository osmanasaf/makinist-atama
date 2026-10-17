package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MK-5 değişmezi serbest intikalle (NFR-02, MK-106/MK-107): iki mod, ikisinde de takvimde SQL ile yazılmış rastgele intikaller vardır; garlar karışıktır (konum uyarıları çıkar).
 * (a) aday listesinin "uygun" dediği ve POST'un kabul ettiği SEFER ataması hemen ardından gün listesinde kırmızı olmaz (mevcut görevler arasında intikal de var);
 * (b) kabul edilen {@code POST /api/intikal}: intikalin canlı durumu kırmızı değildir ve aynı makinistin başka satırlarında YENİ kırmızı (atama ya da intikal) doğmaz.
 */
class AtamaKirmiziIntikalRastgeleTest extends TestVeritabani {

    static final LocalDate BAS = LocalDate.of(2026, 10, 12);
    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    @Test
    void kabulEdilenSeferAtamasiHemenKirmiziOlmaz_mevcutIntikalli() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici t = atamaci();
        Random r = new Random(11);
        int kabul = 0;
        int uygunDegil = 0;
        int intikalMevcut = 0;
        for (int tur = 0; tur < 60; tur++) {
            long m = v.makinist("RA" + tur);
            intikalMevcut += mevcutTakvim(v, r, m, "A" + tur);
            long aday = rastgeleSefer(v, r, "ADAY" + tur);
            LocalDate gun = jdbc.queryForObject("SELECT tarih FROM sefer WHERE id = ?", LocalDate.class, aday);

            JsonNode adaylar = json(t.get("/api/atama/sefer/" + aday + "/adaylar?rol=ASIL").andReturn().getResponse().getContentAsString());
            boolean uygun = false;
            for (JsonNode a : adaylar) {
                if (a.get("makinistId").asLong() == m) {
                    uygun = a.get("uygun").asBoolean();
                }
            }
            String govde = "{\"makinistId\":" + m + ",\"rol\":\"ASIL\",\"gerekce\":\"deneme\"}";
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
                            assertThat(g.get("kirmizi").asBoolean()).as("tur %d: kabul edilen atama kırmızı: %s", tur, g.get("kirmiziNedenler")).isFalse();
                        }
                    }
                }
            }
        }
        assertThat(kabul).as("kabul edilen senaryo sayısı").isGreaterThan(5);
        assertThat(uygunDegil).isGreaterThan(3);
        assertThat(intikalMevcut).isGreaterThan(20);
    }

    @Test
    void kabulEdilenIntikalKirmiziOlmaz_yeniKirmiziDogurmaz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici t = atamaci();
        Random r = new Random(5);
        int kabul = 0;
        int red = 0;
        for (int tur = 0; tur < 60; tur++) {
            long m = v.makinist("RB" + tur);
            mevcutTakvim(v, r, m, "B" + tur);
            int[] once = sayac(t);

            LocalDateTime kalkis = BAS.plusDays(r.nextInt(21)).atTime(LocalTime.parse(SAATLER[r.nextInt(SAATLER.length)]));
            LocalDateTime varis = kalkis.plusMinutes(SURELER[r.nextInt(SURELER.length)]);
            boolean ters = r.nextBoolean();
            String govde = "{\"makinistId\":" + m + ",\"kalkisGarId\":" + (ters ? v.eskisehir : v.ankara) + ",\"varisGarId\":" + (ters ? v.ankara : v.eskisehir) + ",\"kalkis\":\"" + kalkis
                    + ":00+03:00\",\"varis\":\"" + varis + ":00+03:00\",\"aracTuru\":\"SERVIS\",\"gerekce\":\"deneme\"}";
            int durum = t.post("/api/intikal", govde).andReturn().getResponse().getStatus();
            if (durum == 422) {
                red++;
                continue;
            }
            assertThat(durum).as("tur %d: beklenmeyen durum", tur).isEqualTo(201);
            kabul++;
            String yanit = t.get("/api/intikal?bas=" + kalkis.toLocalDate() + "&bit=" + kalkis.toLocalDate() + "&makinistId=" + m).andReturn().getResponse().getContentAsString();
            for (JsonNode i : json(yanit)) {
                if (i.get("kalkis").asString().startsWith(kalkis.toString())) {
                    assertThat(i.get("kirmizi").asBoolean()).as("tur %d: kabul edilen intikal kırmızı: %s", tur, i.get("nedenler")).isFalse();
                }
            }
            int[] sonra = sayac(t);
            assertThat(sonra[0]).as("tur %d: kırmızı atama arttı", tur).isLessThanOrEqualTo(once[0]);
            assertThat(sonra[1]).as("tur %d: kırmızı intikal arttı", tur).isLessThanOrEqualTo(once[1]);
        }
        assertThat(kabul).as("kabul edilen senaryo sayısı").isGreaterThan(5);
        assertThat(red).isGreaterThan(3);
    }

    /** Dönüş: {kırmızı atama, kırmızı intikal} (tüm takvim aralığı). */
    private int[] sayac(Tarayici t) throws Exception {
        JsonNode n = json(t.get("/api/atama/kirmizi-sayisi?bas=" + BAS.minusDays(2) + "&bit=" + BAS.plusDays(24)).andReturn().getResponse().getContentAsString());
        return new int[] { n.get("kirmiziAtama").asInt(), n.get("kirmiziIntikal").asInt() };
    }

    /** Makinistin rastgele mevcut takvimi: 3-8 sefer ataması + 1-3 SQL intikal. Dönüş: intikal sayısı. */
    private int mevcutTakvim(AtamaVeri v, Random r, long m, String ad) {
        int adet = 3 + r.nextInt(6);
        for (int i = 0; i < adet; i++) {
            v.atama(rastgeleSefer(v, r, "M" + ad + "_" + i), m, r.nextInt(4) == 0 ? "YOLCU" : "ASIL");
        }
        int intikal = 1 + r.nextInt(3);
        for (int i = 0; i < intikal; i++) {
            LocalDate gun = BAS.plusDays(r.nextInt(21));
            String kalkis = SAATLER[r.nextInt(SAATLER.length)];
            String varis = LocalTime.parse(kalkis).plusMinutes(SURELER[r.nextInt(SURELER.length)]).toString();
            boolean ters = r.nextBoolean();
            v.intikal(m, ters ? v.eskisehir : v.ankara, ters ? v.ankara : v.eskisehir, gun, kalkis, varis);
        }
        return intikal;
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
