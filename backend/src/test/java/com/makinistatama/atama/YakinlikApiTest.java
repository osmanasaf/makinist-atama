package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.guvenlik.BenimUclari;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F3 / MK-174 / AK-3.3, 3.4, 3.8: yakınlık ve yorgunluk uçları: aday havuzu {@code adaylar} ile aynı, marj kapalıyken boş, ayar değişince bir sonraki istekte etki, yetki
 * (makinist 403, anonim 401), makinist uçlarında (/api/benim/**) yakınlık/yorgunluk YOK (OK-35), mevcut DTO'lar değişmez.
 */
class YakinlikApiTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);
    static final JsonMapper JSON = new JsonMapper();

    private JsonNode oku(Tarayici t, String yol) throws Exception {
        return JSON.readTree(t.get(yol).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode satir(JsonNode yanit, String alan, long kimlik) {
        for (JsonNode s : yanit.get("satirlar")) {
            if (s.get(alan).asLong() == kimlik) {
                return s;
            }
        }
        return null;
    }

    private static JsonNode isaret(JsonNode satir, String kod) {
        for (JsonNode i : satir.get("yakinlik")) {
            if (i.get("kod").asString().equals(kod)) {
                return i;
            }
        }
        return null;
    }

    /** D001: 06:00-14:00 (8 s) + aday 15:00-17:40 (2 s 40 dk) = 10 s 40 dk; sınır 11 s -> K-13 kalan 20 dk. */
    private record Kurulum(long d1, long d2, long t0, long t1) {
    }

    private Kurulum kur() {
        AtamaVeri v = new AtamaVeri(jdbc);
        long d1 = v.makinist("D001");
        long d2 = v.makinist("D002");
        long t0 = v.seferEv("T0", GUN, "06:00", "14:00");
        v.atama(t0, d1, "ASIL");
        long t1 = v.seferEv("T1", GUN, "15:00", "17:40");
        return new Kurulum(d1, d2, t0, t1);
    }

    @Test
    void adayHavuzu_adaylarlaAyni_askidaVeKonduktorYok() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        v.makinist("D001");
        v.makinist("D002");
        v.makinist("D900", true, "ASKIDA");
        v.konduktor("K001");
        long s = v.seferEv("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        for (String rol : List.of("ASIL", "YARDIMCI", "KONDUKTOR", "YOLCU")) {
            List<Long> adaylar = new ArrayList<>();
            oku(t, "/api/atama/sefer/" + s + "/adaylar?rol=" + rol).forEach(a -> adaylar.add(a.get("makinistId").asLong()));
            List<Long> yakin = new ArrayList<>();
            oku(t, "/api/atama/sefer/" + s + "/yakinlik?rol=" + rol).get("satirlar").forEach(a -> yakin.add(a.get("makinistId").asLong()));
            assertThat(yakin).as("rol " + rol).containsExactlyInAnyOrderElementsOf(adaylar).isNotEmpty();
        }
    }

    @Test
    void adayUcu_yakinlikVeYorgunlukDoner_digerAdayBos() throws Exception {
        Kurulum k = kur();
        JsonNode y = oku(atamaci(), "/api/atama/sefer/" + k.t1 + "/yakinlik");
        assertThat(y.get("marjAktif").asBoolean()).isTrue();
        assertThat(y.get("esikler").get("pencereGun").asInt()).isEqualTo(7);
        JsonNode d1 = satir(y, "makinistId", k.d1);
        JsonNode i = isaret(d1, "K-13");
        assertThat(i).isNotNull();
        assertThat(i.get("kalan").asDouble()).isEqualTo(20.0);
        assertThat(i.get("birim").asString()).isEqualTo("dk");
        assertThat(i.get("metin").asString()).isEqualTo("Günlük süre (12 Eki 2026): 10 s 40 dk / 11 s");
        assertThat(d1.get("yorgunluk").get("toplamDk").asLong()).isEqualTo(640);
        assertThat(d1.get("yorgunluk").get("geceNobetSayisi").asInt()).isZero();
        assertThat(d1.get("yorgunluk").get("enKisaDinlenmeDk").isNull()).isTrue();
        JsonNode d2 = satir(y, "makinistId", k.d2);
        assertThat(d2.get("yakinlik")).isEmpty();
        assertThat(d2.get("yorgunluk").get("toplamDk").asLong()).isEqualTo(160);
        assertThat(d2.get("atamaId").isNull()).isTrue();
    }

    @Test
    void marjAktifSifir_ikiUcBosDoner() throws Exception {
        Kurulum k = kur();
        yonetici().put("/api/ayar/marj.aktif", "{\"deger\":0,\"neden\":\"işaret kapatıldı\"}").andExpect(status().isOk());
        Tarayici t = atamaci();
        t.get("/api/atama/sefer/" + k.t1 + "/yakinlik").andExpect(status().isOk()).andExpect(jsonPath("$.marjAktif").value(false)).andExpect(jsonPath("$.satirlar", hasSize(0)));
        t.get("/api/atama/yakinlik?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.marjAktif").value(false)).andExpect(jsonPath("$.satirlar", hasSize(0)));
    }

    @Test
    void marjDegisince_birSonrakiIstekteEtki_onbellekYok() throws Exception {
        Kurulum k = kur();
        Tarayici t = atamaci();
        Tarayici y = yonetici();
        assertThat(isaret(satir(oku(t, "/api/atama/sefer/" + k.t1 + "/yakinlik"), "makinistId", k.d1), "K-13")).isNotNull();
        y.put("/api/ayar/marj.gunluk_dk", "{\"deger\":10,\"neden\":\"daha dar\"}").andExpect(status().isOk());
        assertThat(isaret(satir(oku(t, "/api/atama/sefer/" + k.t1 + "/yakinlik"), "makinistId", k.d1), "K-13")).as("marj 10 < kalan 20: işaret yok").isNull();
        y.put("/api/ayar/marj.gunluk_dk", "{\"deger\":20,\"neden\":\"tam sınır\"}").andExpect(status().isOk());
        assertThat(isaret(satir(oku(t, "/api/atama/sefer/" + k.t1 + "/yakinlik"), "makinistId", k.d1), "K-13")).as("marj = kalan: işaret var").isNotNull();
    }

    @Test
    void gunUcu_atamaBasinaYakinlik_yolcuVeIptalHaric() throws Exception {
        Kurulum k = kur();
        AtamaVeri v = new AtamaVeri(jdbc);
        long a1 = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, k.t0);
        long a2 = v.atama(k.t1, k.d1, "ASIL");
        long yolcu = v.atama(k.t1, k.d2, "YOLCU");
        long iptalSefer = v.seferEv("T9", GUN, "20:00", "21:00");
        long iptalAtama = v.atama(iptalSefer, k.d2, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptalSefer);

        JsonNode y = oku(atamaci(), "/api/atama/yakinlik?tarih=" + GUN);
        assertThat(y.get("satirlar")).hasSize(2);
        assertThat(satir(y, "atamaId", yolcu)).as("YOLCU hariç").isNull();
        assertThat(satir(y, "atamaId", iptalAtama)).as("iptal sefer hariç").isNull();
        for (long atama : List.of(a1, a2)) {
            JsonNode s = satir(y, "atamaId", atama);
            assertThat(s.get("makinistId").asLong()).isEqualTo(k.d1);
            assertThat(isaret(s, "K-13").get("kalan").asDouble()).as("her iki atama da günün 10 s 40 dk toplamını görür").isEqualTo(20.0);
        }
        assertThat(oku(atamaci(), "/api/atama/yakinlik?tarih=" + GUN.plusDays(5)).get("satirlar")).isEmpty();
    }

    @Test
    void yetki_makinist403_anonim401_atamaciVeYonetici200_hataKodlari() throws Exception {
        Kurulum k = kur();
        String[] uclar = { "/api/atama/sefer/" + k.t1 + "/yakinlik", "/api/atama/yakinlik?tarih=" + GUN };
        Tarayici makinist = makinistGirisli(k.d1, "D001");
        for (String uc : uclar) {
            makinist.get(uc).andExpect(status().isForbidden());
            new Tarayici(mvc).get(uc).andExpect(status().isUnauthorized());
            atamaci().get(uc).andExpect(status().isOk());
            yonetici().get(uc).andExpect(status().isOk());
        }
        atamaci().get("/api/atama/sefer/99999/yakinlik").andExpect(status().isNotFound());
        atamaci().get("/api/atama/sefer/" + k.t1 + "/yakinlik?rol=BILINMEZ").andExpect(status().isBadRequest());
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", k.t1);
        atamaci().get("/api/atama/sefer/" + k.t1 + "/yakinlik").andExpect(status().isConflict());
    }

    /** OK-35: makinist uçlarının hiçbiri yakınlık/yorgunluk verisi taşımaz (kendi kaydı olsa bile). */
    @Test
    void benimUclari_yakinlikVeYorgunlukIcermez() throws Exception {
        Kurulum k = kur();
        jdbc.update("INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk) VALUES (?, ?, 'ASIL', 'test', 0)", k.t1, k.d1);
        Tarayici makinist = makinistGirisli(k.d1, "D001");
        int okunan = 0;
        for (String anahtar : BenimUclari.tumu()) {
            if (!BenimUclari.okuma(anahtar)) {
                continue;
            }
            String govde = makinist.istek(BenimUclari.istek(anahtar)).andReturn().getResponse().getContentAsString().toLowerCase(java.util.Locale.ROOT);
            assertThat(govde).as(anahtar).doesNotContain("yakinlik").doesNotContain("yorgunluk").doesNotContain("sınıra yakın");
            okunan++;
        }
        assertThat(okunan).as("duyarlılık: makinist okuma uçları gezildi").isGreaterThanOrEqualTo(6);
    }

    @Test
    void mevcutSozlesmeler_adayDtoVeGunListesiDtoYakinlikAlaniTasimaz() throws Exception {
        Kurulum k = kur();
        Tarayici t = atamaci();
        String adaylar = t.get("/api/atama/sefer/" + k.t1 + "/adaylar").andReturn().getResponse().getContentAsString();
        String gun = t.get("/api/atama/gun?tarih=" + GUN).andReturn().getResponse().getContentAsString();
        assertThat(adaylar.toLowerCase(java.util.Locale.ROOT)).doesNotContain("yakinlik").doesNotContain("yorgunluk");
        assertThat(gun.toLowerCase(java.util.Locale.ROOT)).doesNotContain("yakinlik").doesNotContain("yorgunluk");
    }
}
