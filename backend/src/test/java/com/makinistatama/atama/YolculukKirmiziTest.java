package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F2b MK-40/MK-46/FR-07: canlı kırmızı hesabı YOLCU satırını yolculuk görevi olarak değerlendirir; gecikme/iptal zinciri; öksüz yolculuk yalnız uyarıdır. */
class YolculukKirmiziTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long e1;
    long s2;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        e1 = v.sefer("E1", GUN, "08:00", "09:30"); // Ankara -> Eskişehir (yolculuk)
        s2 = v.sefer("S2", GUN, "10:30", "15:00", v.eskisehir, v.ankara); // Eskişehir -> Ankara (sürüş)
        v.atama(e1, m, "YOLCU");
        v.atama(s2, m, "ASIL");
        t = atamaci();
    }

    private JsonNode gorevli(long sefer, String rol) throws Exception {
        JsonNode gun = new JsonMapper().readTree(t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode s : gun.get("satirlar")) {
            if (s.get("seferId").asLong() == sefer) {
                for (JsonNode g : s.get("makinistler")) {
                    if (g.get("rol").asString().equals(rol)) {
                        return g;
                    }
                }
            }
        }
        throw new AssertionError("görevli yok: " + sefer + " " + rol);
    }

    private void gecikme(long sefer, int dk) {
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = ?, varis_gecikme_dk = ? WHERE id = ?", dk, dk, sefer);
    }

    @Test
    void yolculukBelgeVeHazirlikSebebiyleKirmiziOlmaz_yetkisizMakinistleDe() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.hazirlik_dk'");
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.alikoyma_dk'");
        long yetkisiz = v.makinist("D009", false, "AKTIF");
        v.atama(e1, yetkisiz, "YOLCU");

        // yolculuk sefer saatlerini değil yolculuk aralığını kullanır: E1 08:00–09:30, S2 hazırlıkla 10:00–15:30: çakışma yok
        assertThat(gorevli(e1, "YOLCU").get("kirmizi").asBoolean()).isFalse();
        assertThat(gorevli(s2, "ASIL").get("kirmizi").asBoolean()).isFalse();
    }

    @Test
    void gecikme45_kirmiziYok_gecikme90_sonrakiSeferKirmizi_30dkOrtusme() throws Exception {
        gecikme(e1, 45);
        assertThat(gorevli(e1, "YOLCU").get("kirmizi").asBoolean()).isFalse();
        assertThat(gorevli(s2, "ASIL").get("kirmizi").asBoolean()).isFalse();

        gecikme(e1, 90); // yolculuk 08:00 (planlanan) – 11:00: S2 (10:30) ile 30 dk örtüşür
        JsonNode s2g = gorevli(s2, "ASIL");
        assertThat(s2g.get("kirmizi").asBoolean()).isTrue();
        assertThat(s2g.get("kirmiziNedenler").toString()).contains("30 dk örtüşüyor");
        // K-10 çakışması aynı nöbetteki iki görevde simetriktir (BUG-14: nöbet birleşince sonraki görevler de sorumlu); yolculuk, sürüşle aynı davranır
        assertThat(gorevli(e1, "YOLCU").get("kirmizi").asBoolean()).isTrue();
    }

    @Test
    void cakismaDavranisi_yolculukLaSurusAyni() throws Exception {
        // referans: aynı iki sefer ikisi de sürüş (ASIL) iken kırmızı durumu
        long m2 = v.makinist("D002");
        long a = v.sefer("A", GUN, "12:00", "13:30");
        long b = v.sefer("B", GUN, "13:00", "15:00", v.ankara, v.eskisehir);
        v.atama(a, m2, "ASIL");
        v.atama(b, m2, "ASIL");
        boolean aKirmizi = gorevli(a, "ASIL").get("kirmizi").asBoolean();
        boolean bKirmizi = gorevli(b, "ASIL").get("kirmizi").asBoolean();

        gecikme(e1, 90);
        assertThat(gorevli(e1, "YOLCU").get("kirmizi").asBoolean()).isEqualTo(aKirmizi);
        assertThat(gorevli(s2, "ASIL").get("kirmizi").asBoolean()).isEqualTo(bKirmizi);
    }

    @Test
    void yolculukSeferiIptal_yolcuSatiriKirmizi_SeferIptal() throws Exception {
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", e1);

        JsonNode g = gorevli(e1, "YOLCU");
        assertThat(g.get("kirmizi").asBoolean()).isTrue();
        assertThat(g.get("kirmiziNedenler").toString()).contains("İPTAL");
        // K-20 UYARI olduğu için S2 kırmızı OLMAZ (konum uyarısı gösterilir)
        JsonNode s2g = gorevli(s2, "ASIL");
        assertThat(s2g.get("kirmizi").asBoolean()).isFalse();
        assertThat(s2g.get("uyarilar").toString()).contains("Konum uyuşmazlığı");
        // K-20 ENGEL yapılınca S2 kırmızı olur
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-20'");
        assertThat(gorevli(s2, "ASIL").get("kirmizi").asBoolean()).isTrue();
    }

    @Test
    void oksuzYolculuk_asilKaldirilinca_uyariGelir_kirmiziSayisiArtmaz() throws Exception {
        assertThat(gorevli(e1, "YOLCU").get("uyarilar").toString()).doesNotContain("Yolculuktan sonra");

        t.delete("/api/atama/sefer/" + s2 + "?rol=ASIL&neden=IZIN").andExpect(status().isNoContent());

        JsonNode g = gorevli(e1, "YOLCU");
        assertThat(g.get("kirmizi").asBoolean()).isFalse();
        assertThat(g.get("uyarilar").toString()).contains("Yolculuktan sonra bu garda görev yok");
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-14&bit=2026-10-14").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void oksuzYolculuk_aramaSaatDisindakiGorev_uyariVerir_icindeyseVermez() throws Exception {
        // E1 09:30'da biter, Eskişehir; sonraki sürüş 10:30 (içinde). Pencereyi 1 saate indirip sürüşü 11:00'e ertele: dışarıda
        yonetici().put("/api/ayar/yolculuk.oksuz_saat", "{\"deger\":1,\"neden\":\"deneme\"}").andExpect(status().isOk());
        assertThat(gorevli(e1, "YOLCU").get("uyarilar").toString()).doesNotContain("Yolculuktan sonra"); // 10:30 ≤ 10:30
        jdbc.update("UPDATE sefer SET planlanan_kalkis = planlanan_kalkis + interval '30 minutes', planlanan_varis = planlanan_varis + interval '30 minutes' WHERE id = ?", s2);
        assertThat(gorevli(e1, "YOLCU").get("uyarilar").toString()).contains("Yolculuktan sonra");
    }

    /** Review INFO: uyarı penceresi aramadan AYRI ayardır (varsayılan 24 s): dinlenme + ertesi gün sürüşü (12,5 s sonra) yanlış alarm değildir. */
    @Test
    void oksuzYolculuk_varsayilanPencere24s_dinlenmeSonrasiSurus_uyariYok() throws Exception {
        jdbc.update("UPDATE sefer SET planlanan_kalkis = planlanan_kalkis + interval '22 hours', planlanan_varis = planlanan_varis + interval '22 hours', tarih = tarih + 1 WHERE id = ?", s2); // ertesi gün 08:30: yolculuk bitişinden 23 s sonra
        assertThat(gorevli(e1, "YOLCU").get("uyarilar").toString()).doesNotContain("Yolculuktan sonra");
        jdbc.update("UPDATE sefer SET planlanan_kalkis = planlanan_kalkis + interval '2 hours', planlanan_varis = planlanan_varis + interval '2 hours' WHERE id = ?", s2); // 25 s sonra: pencere dışı
        assertThat(gorevli(e1, "YOLCU").get("uyarilar").toString()).contains("Yolculuktan sonra");
    }

    /** Review INFO: yolculuk makinistin bağlı garına (eve dönüş) varıyorsa sonraki görev aranmaz. */
    @Test
    void oksuzYolculuk_evGarinaDonusYolculugu_uyariVermez() throws Exception {
        long don = v.sefer("DON", GUN, "11:00", "12:30", v.eskisehir, v.ankara); // Eskişehir -> Ankara (bağlı gar)
        v.atama(don, m, "YOLCU");
        t.delete("/api/atama/sefer/" + s2 + "?rol=ASIL&neden=IZIN").andExpect(status().is2xxSuccessful());
        assertThat(gorevli(e1, "YOLCU").get("uyarilar").toString()).contains("Yolculuktan sonra"); // Eskişehir'te biten yolculuk hâlâ öksüz
        assertThat(gorevli(don, "YOLCU").get("uyarilar").toString()).doesNotContain("Yolculuktan sonra");
    }
}
