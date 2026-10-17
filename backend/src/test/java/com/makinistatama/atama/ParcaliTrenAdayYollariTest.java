package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.sefer.SeferYuklemeServisi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * F2d tur 2 / W1 (review): OK-17 kurulmamış bağ TÜM aday yollarında görülür (uygun-seferler, otomatik atama, gün listesi boş nedeni; elle atama zaten doğruydu). Senaryo: M parça 2'ye
 * gecikmeden ÖNCE atandı (donuk başlangıç 10:10), parça 1 gecikmeli (varış +30 dk); M tek makinist. Ayar 0 ve hazırlık/alıkoyma 15 dk için.
 */
class ParcaliTrenAdayYollariTest extends TestVeritabani {

    static final String GUN = "2026-10-12";
    static final String GECIKME = "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}";

    @Autowired
    SeferYuklemeServisi yukleme;
    @Autowired
    ObjectMapper json;

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private void kontrol(String tren) throws Exception {
        long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, tren, GUN, "08:00", "10:00", "10:10", "13:00");
        long m = v.makinist("D001");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/sefer/" + p[0] + "/gecikme", GECIKME).andExpect(status().isOk());

        // elle atama (referans yol) zaten doğru
        JsonNode adaylar = json.readTree(t.get("/api/atama/sefer/" + p[0] + "/adaylar").andReturn().getResponse().getContentAsString());
        assertThat(adaylar.toString()).contains("\"uygun\":true");

        // uygun-seferler
        String uygun = t.get("/api/atama/makinist/" + m + "/uygun-seferler?gun=" + GUN).andReturn().getResponse().getContentAsString();
        assertThat(uygun).as("uygun-seferler parça 1'i içermeli").contains("\"seferId\":" + p[0]);

        // otomatik atama önizleme
        JsonNode oto = json.readTree(t.post("/api/atama/otomatik", "{\"bas\":\"" + GUN + "\",\"bit\":\"" + GUN + "\",\"uygula\":false}").andReturn().getResponse().getContentAsString());
        assertThat(oto.get("bosSayisi").asInt()).as(oto.toString()).isZero();
        assertThat(oto.get("atananlar").toString()).contains("\"seferId\":" + p[0]);

        // gün listesi: boş neden yok
        String gun = t.get("/api/atama/gun?tarih=" + GUN).andReturn().getResponse().getContentAsString();
        assertThat(gun).doesNotContain("0/1 uygun");

        // ve önizlemeyi uygulamak gerçekten yazılabilir (elle yol aynı sonucu verir)
        t.post("/api/atama/otomatik", "{\"bas\":\"" + GUN + "\",\"bit\":\"" + GUN + "\",\"uygula\":true}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'ASIL'", Integer.class)).isEqualTo(2);
        assertThat(t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andReturn().getResponse().getContentAsString()).contains("\"kirmiziAtama\":0");
    }

    /**
     * F2d Tur 3 / W1 kalanı: İKİ parça da atanmamışken (aynı çalışmada) bağ otomatik atamanın kendi yazdığı planlı görevler için de kurulur: tek makinist iki parçayı da alır,
     * yazılan atamalarda kırmızı yok (ayar 0 ve hazırlık / alıkoyma 15).
     */
    private void ikiParcaDaAtanmamis(String tren) throws Exception {
        long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, tren, GUN, "08:00", "10:00", "10:10", "13:00");
        v.makinist("D001");
        Tarayici t = atamaci();
        JsonNode oto = json.readTree(t.post("/api/atama/otomatik", "{\"bas\":\"" + GUN + "\",\"bit\":\"" + GUN + "\",\"uygula\":false}").andReturn().getResponse().getContentAsString());
        assertThat(oto.get("bosSayisi").asInt()).as(oto.toString()).isZero();
        assertThat(oto.get("atananlar").size()).isEqualTo(2);
        t.post("/api/atama/otomatik", "{\"bas\":\"" + GUN + "\",\"bit\":\"" + GUN + "\",\"uygula\":true}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'ASIL'", Integer.class)).isEqualTo(2);
        assertThat(t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andReturn().getResponse().getContentAsString()).contains("\"kirmiziAtama\":0");
        assertThat(p).hasSize(2);
    }

    @Test
    void ayar0_ikiParcaDaAtanmamis_birMakinistIkisiniDeAlir() throws Exception {
        ikiParcaDaAtanmamis("DE-2811");
    }

    @Test
    void hazirlikAlikoyma15_ikiParcaDaAtanmamis_birMakinistIkisiniDeAlir() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar IN ('gorev.hazirlik_dk', 'gorev.alikoyma_dk')");
        ikiParcaDaAtanmamis("DE-2812");
    }

    /** F3a review INFO-10: kadro simülasyonu da bitişik parçaları aynı kişide hazırlık / alıkoymasız görür (tek makinist iki parçayı da alır, boş 0). */
    @Test
    void kadroSimulasyonu_ikiParcaTekMakinist_hazirlikAlikoyma15_bosYok(@Autowired KadroTahminServisi kadro) {
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar IN ('gorev.hazirlik_dk', 'gorev.alikoyma_dk')");
        ParcaliAnkaraTren.yukle(yukleme, jdbc, "DE-2821", GUN, "08:00", "10:00", "10:10", "13:00");
        v.makinist("D001");
        KadroRaporuDto r = kadro.hesapla(new KadroIstegi(java.time.LocalDate.parse(GUN), 1, 0, false, false, 30, false, null));
        assertThat(r.simulasyon().mevcut().seferSayisi()).isEqualTo(2);
        assertThat(r.simulasyon().mevcut().bos()).as("zincir bağı olmadan ikinci parça sahte çakışmayla boş kalırdı").isZero();
    }

    @Test
    void ayar0_parca2MakinisteteykenGecikmeliParca1_tumAdayYollari() throws Exception {
        kontrol("DE-2801");
    }

    @Test
    void hazirlikAlikoyma15_ayniSenaryo() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar IN ('gorev.hazirlik_dk', 'gorev.alikoyma_dk')");
        kontrol("DE-2802");
    }
}
