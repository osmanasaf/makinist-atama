package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.ParcaliTrenUcAUcaTest.Kurulum;
import com.makinistatama.sefer.SeferYuklemeServisi;

/**
 * QA (F2c): keşif testi bulgusu BUG-1. AYNI makinist ardışık iki parçayı sürerken (değişimsiz gün, analiz Q4) atama gecikmeden ÖNCE yapılmışsa ve parça 1'e gecikme girilirse
 * (devam açık), parça 2 kayar ama atamanın OK-6 başlangıcı planlanan saatte donuk kaldığı için aynı makinist kendi parça 1'iyle "çakışıyor" görünür (sahte kırmızı).
 * Beklenen: tek makinistin zincirlenmiş iki parçası, tren gecikince kırmızı OLMAZ. Bu test üretim koduna dokunmaz; şu an KIRMIZI olması beklenir.
 */
class ParcaliTrenQaTest extends TestVeritabani {

    @Autowired
    SeferYuklemeServisi yukleme;

    Kurulum k;

    @BeforeEach
    void kur() {
        k = new Kurulum(jdbc, yukleme);
    }

    @Test
    void ayniMakinistIkiParca_parca1GecikinceParca2Kayar_sahteCakismaKirmizisiOlmaz() throws Exception {
        long[] p = k.parcali("DE-1504", ParcaliTrenUcAUcaTest.GUN, "08:00", "10:00", "10:10", "13:00"); // 10 dk planlı bekleme
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());

        t.post("/api/sefer/" + p[0] + "/gecikme", "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isOk()); // parça 2 de +30 olur

        t.get("/api/atama/gun?tarih=" + ParcaliTrenUcAUcaTest.GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[1].guncelKalkis").value("2026-10-12T10:40:00+03:00"))
                .andExpect(jsonPath("$.satirlar[*].makinistler[*].kirmizi", not(hasItem(true))));
    }

    /**
     * OK-17 tek kaynak: puantaj ve adalet de aynı çalışma aralığını görür. Gecikmeden ÖNCE atanan zincir (D-A), gecikmeden SONRA atanan eşdeğer zincirle (D-B, ertesi gün)
     * aynı fiili süre / km / gece saatini verir; donuk başlangıç parça 2'ye ek (çakışan) süre yazmaz.
     */
    @Test
    void ayniMakinistIkiParca_gecikmedenSonra_puantajVeAdalet_gecikmedenSonraAtananlaAynidir() throws Exception {
        long[] p = k.parcali("DE-1504", ParcaliTrenUcAUcaTest.GUN, "08:00", "10:00", "10:10", "13:00");
        long[] q = k.parcali("DE-1505", "2026-10-13", "08:00", "10:00", "10:10", "13:00");
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        long b = k.makinist("D-B", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        Tarayici t = atamaci();
        String gecikme = "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}";
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated()); // gecikmeden ÖNCE
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/sefer/" + p[0] + "/gecikme", gecikme).andExpect(status().isOk());
        t.post("/api/sefer/" + q[0] + "/gecikme", gecikme).andExpect(status().isOk());
        t.post("/api/atama/sefer/" + q[0], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated()); // gecikmeden SONRA
        t.post("/api/atama/sefer/" + q[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());

        Tarayici y = yonetici();
        String puantaj = y.get("/api/rapor/puantaj?ay=2026-10").andReturn().getResponse().getContentAsString();
        String adalet = y.get("/api/rapor/adalet?ay=2026-10").andReturn().getResponse().getContentAsString();
        for (String alan : new String[] { "fiiliSaat", "km", "geceSaat" }) {
            assertThat(oku(puantaj, "D-A", alan)).as("puantaj " + alan).isEqualTo(oku(puantaj, "D-B", alan));
        }
        for (String alan : new String[] { "gorevSaat", "km", "geceSaat" }) {
            assertThat(oku(adalet, "D-A", alan)).as("adalet " + alan).isEqualTo(oku(adalet, "D-B", alan));
        }
        assertThat(oku(puantaj, "D-A", "fiiliSaat")).isNotEqualTo(0.0);
    }

    private static Object oku(String json, String sicil, String alan) {
        return ((java.util.List<?>) com.jayway.jsonpath.JsonPath.read(json, "$.satirlar[?(@.sicil=='" + sicil + "')]." + alan)).get(0);
    }
}
