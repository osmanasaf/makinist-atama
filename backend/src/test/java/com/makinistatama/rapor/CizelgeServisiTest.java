package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F5 / MK-177 / AK-5.1, 5.2, 5.3, 5.5: aylık çizelge ızgarası verisi. Bilinen kurulumda (izin, gece görevi, yolculuk, iptal sefer, ay sınırını aşan vardiya, çakışma) her hücre beklenen
 * içeriği verir; satır toplamları adalet raporuyla BİREBİR aynıdır (ikinci formül yok); süzgeçler yalnız ilgili satırları getirir; kırmızı bayrağı yalnız {@code kontrol=true} iken.
 */
class CizelgeServisiTest extends TestVeritabani {

    static final JsonMapper JSON = new JsonMapper();
    static final LocalDate EKIM = LocalDate.of(2026, 10, 1);

    AtamaVeri v;
    Tarayici t;
    long d1;
    long d2;
    long d3;
    long k1;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        d1 = v.makinist("D001"); // 1 Eki 06:00-14:00, 12 Eki 22:00-06:00 (gece), 31 Eki 22:00-06:00 (Kasım'a taşar)
        d2 = v.makinist("D002"); // izin 5-7 Eki (onaylı) + talep izni 9 Eki (sayılmaz); 3 Eki yolcu
        d3 = v.makinist("D003"); // iptal sefer 4 Eki; 20 Eki çakışma (ikinci atama kırmızı)
        jdbc.update("UPDATE makinist SET bagli_gar_id = ? WHERE id = ?", v.eskisehir, d3);
        k1 = v.konduktor("K001");
        v.atama(v.seferEv("T1", EKIM, "06:00", "14:00"), d1, "ASIL");
        v.atama(v.seferEv("T2", EKIM.plusDays(11), "22:00", "06:00"), d1, "ASIL");
        v.atama(v.seferEv("T3", EKIM.plusDays(30), "22:00", "06:00"), d1, "ASIL");
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, '2026-10-05', '2026-10-07', 'YILLIK', 'ONAY', 'test')", d2);
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, '2026-10-09', '2026-10-09', 'YILLIK', 'TALEP', 'test')", d2);
        v.atama(v.seferEv("T4", EKIM.plusDays(2), "09:00", "11:00"), d2, "YOLCU");
        long iptal = v.seferEv("T5", EKIM.plusDays(3), "08:00", "12:00");
        v.atama(iptal, d3, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptal);
        v.atama(v.seferEv("T6", EKIM.plusDays(19), "08:00", "12:00"), d3, "ASIL");
        v.atama(v.seferEv("T7", EKIM.plusDays(19), "10:00", "13:00"), d3, "ASIL");
        v.atama(v.seferEv("T8", EKIM.plusDays(7), "08:00", "12:00"), k1, "ASIL");
        t = atamaci();
    }

    private JsonNode cizelge(String sorgu) throws Exception {
        return JSON.readTree(t.get("/api/rapor/cizelge?" + sorgu).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode satir(JsonNode c, String sicil) {
        for (JsonNode s : c.get("satirlar")) {
            if (sicil.equals(s.get("sicil").asText())) {
                return s;
            }
        }
        throw new AssertionError("satır yok: " + sicil);
    }

    private static JsonNode hucre(JsonNode satir, int gun) {
        for (JsonNode h : satir.get("hucreler")) {
            if (h.get("gun").asInt() == gun) {
                return h;
            }
        }
        return null;
    }

    @Test
    void ustBilgi_gunSayisi_satirlarSicileGore_yalnizAktifMakinist() throws Exception {
        jdbc.update("UPDATE makinist SET durum = 'PASIF' WHERE sicil = 'D003'");
        JsonNode c = cizelge("ay=2026-10");
        assertThat(c.get("ay").asText()).isEqualTo("2026-10");
        assertThat(c.get("gunSayisi").asInt()).isEqualTo(31);
        assertThat(c.get("tur").asText()).isEqualTo("MAKINIST");
        assertThat(c.get("ureten").asText()).isEqualTo("Ata Maci");
        assertThat(c.get("kontrol").asBoolean()).isFalse();
        List<String> sicil = new ArrayList<>();
        c.get("satirlar").forEach(s -> sicil.add(s.get("sicil").asText()));
        assertThat(sicil).containsExactly("D001", "D002"); // kondüktör ayrı grup, PASIF yok
    }

    @Test
    void hucre_gorev_gece_baslandigiGun_iptalYok() throws Exception {
        JsonNode c = cizelge("ay=2026-10");
        JsonNode g1 = hucre(satir(c, "D001"), 1).get("gorevler").get(0);
        assertThat(g1.get("trenNo").asText()).isEqualTo("T1");
        assertThat(g1.get("kalkis").asText()).isEqualTo("06:00");
        assertThat(g1.get("varis").asText()).isEqualTo("14:00");
        assertThat(g1.get("rol").asText()).isEqualTo("ASIL");
        assertThat(g1.get("seferId").asLong()).isPositive();
        assertThat(g1.path("gece").asBoolean(false)).as("06:00-14:00 gündüz görevi (kalkış ve varış gece penceresinde değil)").isFalse();
        // gece yarısını geçen sefer BAŞLADIĞI günün hücresinde (planlanan gün), ertesi günde yok
        JsonNode gece = hucre(satir(c, "D001"), 12).get("gorevler").get(0);
        assertThat(gece.get("trenNo").asText()).isEqualTo("T2");
        assertThat(gece.get("gece").asBoolean()).isTrue();
        assertThat(hucre(satir(c, "D001"), 13)).isNull();
        assertThat(hucre(satir(c, "D001"), 2)).as("seyrek: görevi/izni olmayan gün hücre değil").isNull();
        // iptal sefer hücrede yok
        assertThat(hucre(satir(c, "D003"), 4)).isNull();
    }

    @Test
    void izinGunu_yalnizOnayliIzin_veYolculukIsareti() throws Exception {
        JsonNode c = cizelge("ay=2026-10");
        JsonNode d2 = satir(c, "D002");
        for (int gun : new int[] { 5, 6, 7 }) {
            assertThat(hucre(d2, gun).get("izin").asText()).isEqualTo("YILLIK");
        }
        assertThat(hucre(d2, 9)).as("TALEP durumundaki izin hücreye girmez").isNull();
        JsonNode yolcu = hucre(d2, 3).get("gorevler").get(0);
        assertThat(yolcu.get("yolculuk").asBoolean()).isTrue();
        assertThat(yolcu.get("rol").asText()).isEqualTo("YOLCU");
    }

    @Test
    void ayinSonundaBaslayanVardiya_oAyinHucresinde_toplamlarKasimaTasar() throws Exception {
        JsonNode ekim = cizelge("ay=2026-10");
        assertThat(hucre(satir(ekim, "D001"), 31).get("gorevler").get(0).get("trenNo").asText()).isEqualTo("T3");
        JsonNode kasim = cizelge("ay=2026-11");
        assertThat(hucre(satir(kasim, "D001"), 1)).as("hücre başladığı günün ayında").isNull();
        // taşan 06:00'a kadarki 6 saat Kasım toplamında (adaletle aynı bölme kuralı)
        assertThat(satir(kasim, "D001").get("gorevSaat").asDouble()).isGreaterThanOrEqualTo(6.0).isLessThan(8.0);
        eslestirAdaletle(kasim, "2026-11", "MAKINIST");
    }

    private void eslestirAdaletle(JsonNode c, String ay, String tur) throws Exception {
        JsonNode adalet = JSON.readTree(t.get("/api/rapor/adalet?ay=" + ay + "&tur=" + tur).andReturn().getResponse().getContentAsString());
        for (JsonNode s : c.get("satirlar")) {
            JsonNode a = null;
            for (JsonNode x : adalet.get("satirlar")) {
                if (x.get("makinistId").asLong() == s.get("makinistId").asLong()) {
                    a = x;
                }
            }
            assertThat(a).as("adalet satırı " + s.get("sicil")).isNotNull();
            assertThat(s.get("gorevSaat").asDouble()).as(s.get("sicil") + " saat").isEqualTo(a.get("gorevSaat").asDouble());
            assertThat(s.get("geceSaat").asDouble()).as(s.get("sicil") + " gece").isEqualTo(a.get("geceSaat").asDouble());
            assertThat(s.get("km").asDouble()).as(s.get("sicil") + " km").isEqualTo(a.get("km").asDouble());
            assertThat(s.get("seferSayisi").asInt()).as(s.get("sicil") + " sefer").isEqualTo(a.get("seferSayisi").asInt());
            assertThat(s.get("izinGunu").asInt()).as(s.get("sicil") + " izin").isEqualTo(a.get("izinGunu").asInt());
        }
    }

    @Test
    void satirToplamlari_adaletRaporuylaBirebir_makinistVeKonduktor() throws Exception {
        eslestirAdaletle(cizelge("ay=2026-10"), "2026-10", "MAKINIST");
        eslestirAdaletle(cizelge("ay=2026-10&tur=KONDUKTOR"), "2026-10", "KONDUKTOR");
        assertThat(cizelge("ay=2026-10&tur=KONDUKTOR").get("satirlar")).hasSize(1);
        assertThat(satir(cizelge("ay=2026-10"), "D002").get("izinGunu").asInt()).isEqualTo(3);
    }

    @Test
    void garSuzgeci_yalnizOGarinPersoneli_toplamlarSatirlarinki() throws Exception {
        JsonNode eskisehir = cizelge("ay=2026-10&garId=" + v.eskisehir);
        assertThat(eskisehir.get("satirlar")).hasSize(1);
        assertThat(eskisehir.get("satirlar").get(0).get("sicil").asText()).isEqualTo("D003");
        eslestirAdaletle(eskisehir, "2026-10", "MAKINIST");
        JsonNode ankara = cizelge("ay=2026-10&garId=" + v.ankara);
        assertThat(ankara.get("satirlar")).hasSize(2);
        eslestirAdaletle(ankara, "2026-10", "MAKINIST");
        assertThat(cizelge("ay=2026-10&garId=999999").get("satirlar")).isEmpty();
    }

    @Test
    void resmiTatil_yanitta_ayIcindekiler_yalnizGosterim() throws Exception {
        jdbc.update("INSERT INTO resmi_tatil (tarih, ad) VALUES ('2026-10-29', 'Cumhuriyet Bayramı'), ('2026-11-01', 'Kasım tatili')");
        JsonNode c = cizelge("ay=2026-10");
        assertThat(c.get("tatiller")).hasSize(1);
        assertThat(c.get("tatiller").get(0).get("gun").asInt()).isEqualTo(29);
        assertThat(c.get("tatiller").get(0).get("ad").asText()).isEqualTo("Cumhuriyet Bayramı");
        eslestirAdaletle(c, "2026-10", "MAKINIST");
    }

    @Test
    void kirmiziBayragi_yalnizKontrolTrueIken_cakisanAtamalar() throws Exception {
        JsonNode kapali = cizelge("ay=2026-10");
        assertThat(kapali.toString()).as("kontrol kapalıyken kırmızı alanı yok").doesNotContain("\"kirmizi\"");
        JsonNode acik = cizelge("ay=2026-10&kontrol=true");
        assertThat(acik.get("kontrol").asBoolean()).isTrue();
        List<String> kirmizilar = new ArrayList<>();
        for (JsonNode s : acik.get("satirlar")) {
            for (JsonNode h : s.get("hucreler")) {
                if (h.has("gorevler")) {
                    for (JsonNode g : h.get("gorevler")) {
                        if (g.path("kirmizi").asBoolean(false)) {
                            kirmizilar.add(g.get("trenNo").asText());
                        }
                    }
                }
            }
        }
        assertThat(kirmizilar).contains("T6", "T7").doesNotContain("T1", "T4", "T8"); // çakışmada İKİ atama da kırmızı (sorumlu ayrımı değil, durum); gece görevleri kural gereği ayrıca kırmızı olabilir
        // kirmizi-sayisi ucu iptal seferdeki atamayı da sayar ("İPTAL – makinist boşta"); iptal sefer ızgarada hücre olmadığından görünen kırmızılar = sayı − 1
        int sayi = JSON.readTree(t.get("/api/atama/kirmizi-sayisi?bas=2026-10-01&bit=2026-10-31").andReturn().getResponse().getContentAsString()).get("kirmiziAtama").asInt();
        assertThat(sayi).isEqualTo(kirmizilar.size() + 1);
        // aynı gün iki görev: tek hücrede iki görev, kalkışa göre sıralı
        JsonNode hucre20 = hucre(satir(acik, "D003"), 20);
        assertThat(hucre20.get("gorevler")).hasSize(2);
        assertThat(hucre20.get("gorevler").get(0).get("trenNo").asText()).isEqualTo("T6");
    }

    @Test
    void dogrulama400_ayYok_ayAraligiDisi_gecersizTur() throws Exception {
        t.get("/api/rapor/cizelge").andExpect(status().isBadRequest());
        t.get("/api/rapor/cizelge?ay=2020-01").andExpect(status().isBadRequest());
        t.get("/api/rapor/cizelge?ay=2099-01").andExpect(status().isBadRequest());
        t.get("/api/rapor/cizelge?ay=2026-10&tur=ASCI").andExpect(status().isBadRequest());
        t.get("/api/rapor/cizelge?ay=2026-13").andExpect(status().isBadRequest());
    }

    @Test
    void salt_okunur_hicbirAtamaVeKayitYazmaz() throws Exception {
        int atama = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);
        int kayit = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);
        cizelge("ay=2026-10&kontrol=true");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(atama);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(kayit);
    }
}
