package com.makinistatama.atama;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.sefer.SeferYuklemeServisi;

/**
 * F2c tur 3 (review "Tur 2"): OK-17 bağı henüz KURULMAMIŞKEN de görülür (parça 2 önceden aynı makinistte, parça 1 gecikmeli ve şimdi atanıyor), hazırlık/alıkoyma aynı makinistin ardışık
 * parçaları arasında uygulanmaz, ve OK-17'nin "önceki parça başka makinistte / iptal" dalları donuk kalır.
 */
class ParcaliTrenTur3Test extends TestVeritabani {

    static final String GUN = "2026-10-12";
    static final String GECIKME = "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}";

    @Autowired
    SeferYuklemeServisi yukleme;

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private long[] tren(String ad) {
        return ParcaliAnkaraTren.yukle(yukleme, jdbc, ad, GUN, "08:00", "10:00", "10:10", "13:00");
    }

    @Test
    void parca2OncedenAyniMakinistte_parca1GecikmeliAtanir_bagKurulmadanSahteCakismaYok() throws Exception {
        long[] p = tren("DE-1701");
        long m = v.makinist("D001");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/sefer/" + p[0] + "/gecikme", GECIKME).andExpect(status().isOk()); // parça 2 de 10:40'a kayar; M'nin parça 2 atamasında başlangıç donuk (10:10)

        t.get("/api/atama/sefer/" + p[0] + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$[0].uygun").value(true));
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[*].makinistler[*].kirmizi", not(hasItem(true))));
    }

    @Test
    void parca2OncedenAyniMakinistte_yolculukla_parca1GecikmeliYolcuOlarakEklenir() throws Exception {
        long[] p = tren("DE-1702");
        long m = v.makinist("D001");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/sefer/" + p[0] + "/gecikme", GECIKME).andExpect(status().isOk());

        t.post("/api/atama/sefer/" + p[0], "{\"makinistId\":" + m + ",\"rol\":\"YOLCU\"}").andExpect(status().isCreated());
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[*].makinistler[*].kirmizi", not(hasItem(true))));
    }

    // ---- OK-17 negatif dalları: bağ yoksa başlangıç DONUK kalır (mutant öldürücü; fiili süre puantajdan okunur)

    private double fiiliSaat(String sicil) throws Exception {
        String puantaj = yonetici().get("/api/rapor/puantaj?ay=2026-10").andReturn().getResponse().getContentAsString();
        return ((Number) ((java.util.List<?>) com.jayway.jsonpath.JsonPath.read(puantaj, "$.satirlar[?(@.sicil=='" + sicil + "')].fiiliSaat")).get(0)).doubleValue();
    }

    @Test
    void oncekiParcaBaskaMakinistte_parca2DonukKalir() throws Exception {
        long[] p = tren("DE-1710");
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated()); // gecikmeden ÖNCE: donuk 10:10
        t.post("/api/sefer/" + p[0] + "/gecikme", GECIKME).andExpect(status().isOk()); // parça 2: 10:40 - 13:30

        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D002")).as("B bağsız: planlanan 10:10 başlangıç (3 s 20 dk)").isCloseTo(3.33, org.assertj.core.data.Offset.offset(0.01));
        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D001")).as("A: parça 1 08:00 - 10:30").isCloseTo(2.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void oncekiParcaIptal_parca2DonukKalir() throws Exception {
        long[] p = tren("DE-1711");
        long m = v.makinist("D001");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        // iptal parça 1'e SQL ile (devam yayılımı parça 2'yi de iptal ederdi): parça 2 atamasının başlangıcı donuk kalır, parça 1 sayılmaz
        jdbc.update("UPDATE sefer SET durum = 'IPTAL', kalkis_gecikme_dk = 30, varis_gecikme_dk = 30 WHERE id = ?", p[0]);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 30, varis_gecikme_dk = 30 WHERE id = ?", p[1]);

        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D001")).as("yalnız parça 2: donuk 10:10 - 13:30").isCloseTo(3.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void oncekiParcaBaskaTrenGununde_parca2DonukKalir() throws Exception {
        java.util.List<Long> ids = ParcaliAnkaraTren.yukleGunler(yukleme, jdbc, "DE-1712", java.util.List.of("2026-10-12", "2026-10-13"), "08:00", "10:00", "10:10", "13:00");
        long m = v.makinist("D001");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + ids.get(0), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()); // 12. gün zincirinin parça 1'i (aynı tren no)
        t.post("/api/atama/sefer/" + ids.get(3), AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()); // 13. gün zincirinin parça 2'si: ÖNCEKİ parçası (13. gün parça 1) M'de değil
        t.post("/api/sefer/" + ids.get(2) + "/gecikme", GECIKME).andExpect(status().isOk()); // 13. gün parça 1 gecikti -> parça 2: 10:40 - 13:30

        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D001")).as("12. gün parça 1 (2 s) + 13. gün parça 2 donuk (3 s 20 dk)").isCloseTo(5.33, org.assertj.core.data.Offset.offset(0.01));
    }

    // ---- hazırlık/alıkoyma: aynı makinist trende kalır, ardışık parçalar arasında uygulanmaz

    private void hazirlikAlikoyma15() {
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar IN ('gorev.hazirlik_dk', 'gorev.alikoyma_dk')");
    }

    @Test
    void hazirlikAlikoyma_ayniMakinistArdisikParcalar_aralarindaUygulanmaz_iki_sirada_da() throws Exception {
        hazirlikAlikoyma15();
        long[] p = tren("DE-1720");
        long[] q = ParcaliAnkaraTren.yukle(yukleme, jdbc, "DE-1721", "2026-10-13", "08:00", "10:00", "10:10", "13:00");
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated()); // doğru sıra
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated()); // 10:10 - alıkoyma 10:15 örtüşmesi YOK
        t.post("/api/atama/sefer/" + q[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated()); // ters sıra
        t.post("/api/atama/sefer/" + q[0], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[*].makinistler[*].kirmizi", not(hasItem(true))));

        // 07:45 - 10:00 + 10:10 - 13:15 (hazırlık yalnız zincirin başında, alıkoyma yalnız sonunda)
        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D001")).isCloseTo(2.25 + 3.0833, org.assertj.core.data.Offset.offset(0.01));
        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D002")).isCloseTo(2.25 + 3.0833, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void hazirlikAlikoyma_parcalarFarkliMakinistte_ikisiDeUygulanir() throws Exception {
        hazirlikAlikoyma15();
        long[] p = tren("DE-1722");
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D001")).as("07:45 - 10:15").isCloseTo(2.5, org.assertj.core.data.Offset.offset(0.01));
        org.assertj.core.api.Assertions.assertThat(fiiliSaat("D002")).as("09:55 - 13:15").isCloseTo(3.3333, org.assertj.core.data.Offset.offset(0.01));
    }
}
