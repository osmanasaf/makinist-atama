package com.makinistatama.atama;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.ParcaliTrenUcAUcaTest.Kurulum;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.sefer.SeferYuklemeServisi;

/**
 * F2c Faz 3 (FR-06, FR-07, FR-09): parçalı trende gecikme/iptal zinciri; A parça 1, B parça 2 ASIL. "Benim" ekranı gerçek saatle çalıştığı için tarih "yarın"dır
 * (ufuk bugün..+6). Yöntemler birbirinden bağımsızdır; kurulum {@link Kurulum} (ParcaliTrenUcAUcaTest).
 */
class ParcaliTrenIsletmeUcAUcaTest extends TestVeritabani {

    static final String TREN = "DE-1503";

    @Autowired
    SeferYuklemeServisi yukleme;

    LocalDate yarin;
    String gg;
    long[] p;
    long a;
    long b;
    long atamaA;
    long atamaB;
    Tarayici ta;
    Tarayici tb;
    Tarayici at;

    @BeforeEach
    void kur() throws Exception {
        yarin = LocalDate.now(ZamanAyari.BOLGE).plusDays(1);
        gg = DateTimeFormatter.ofPattern("dd.MM").format(yarin);
        Kurulum k = new Kurulum(jdbc, yukleme);
        p = k.ankaraSivasKars(TREN, yarin.toString());
        a = k.makinist("D-A", k.ankara, k.hatAnkaraKars);
        b = k.makinist("D-B", k.sivas, k.hatSivasKars);
        at = atamaci();
        at.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        at.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());
        atamaA = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, p[0]);
        atamaB = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, p[1]);
        ta = makinistGirisli(a, "D-A");
        tb = makinistGirisli(b, "D-B");
        GordumYardimci.gordum(ta, atamaA).andExpect(status().isOk());
        GordumYardimci.gordum(tb, atamaB).andExpect(status().isOk());
    }

    String gecikme(int varis, Boolean devam) {
        return "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":" + varis + ",\"neden\":\"GECIKME\"" + (devam == null ? "" : ",\"devam\":" + devam) + "}";
    }

    String iptal(Boolean devam) {
        return "{\"neden\":\"IPTAL\"" + (devam == null ? "" : ",\"devam\":" + devam) + "}";
    }

    @Test
    void gecikme_parca1Varis40_parca2Kayar_ikisininOzetiVeGordumuDegisir() throws Exception {
        at.post("/api/sefer/" + p[0] + "/gecikme", gecikme(40, null)).andExpect(status().isOk());

        at.get("/api/atama/gun?tarih=" + yarin).andExpect(jsonPath("$.satirlar[0].guncelVaris").value(yarin + "T14:10:00+03:00"))
                .andExpect(jsonPath("$.satirlar[1].guncelKalkis").value(yarin + "T14:20:00+03:00")).andExpect(jsonPath("$.satirlar[1].guncelVaris").value(yarin + "T20:50:00+03:00"))
                .andExpect(jsonPath("$.satirlar[*].kirmizi", not(hasItem(true))));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].ozet").value("Asıl · Ankara " + gg + " 08:00 → Sivas " + gg + " 14:10"))
                .andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].ozet").value("Asıl · Sivas " + gg + " 14:20 → Kars " + gg + " 20:50"))
                .andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
    }

    @Test
    void gecikme_devamFalse_yalnizParca1_ikinciMakinistinGordumuKorunur() throws Exception {
        at.post("/api/sefer/" + p[0] + "/gecikme", gecikme(40, false)).andExpect(status().isOk());

        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU")).andExpect(jsonPath("$.gorevler[0].ozet").value("Asıl · Sivas " + gg + " 13:40 → Kars " + gg + " 20:10"));
    }

    @Test
    void iptal_parca1den_ikiAtamaDaIptalMakinistBosta_tumTren() throws Exception {
        at.post("/api/sefer/" + p[0] + "/iptal", iptal(null)).andExpect(status().isOk());

        at.get("/api/atama/gun?tarih=" + yarin).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value("İPTAL – makinist boşta")).andExpect(jsonPath("$.satirlar[1].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[1].makinistler[0].kirmiziNedenler[0]").value("İPTAL – makinist boşta"));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].durum").value("IPTAL")).andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].durum").value("IPTAL")).andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
    }

    @Test
    void iptal_parca2den_yalnizBKirmizi_ANinPuantajSaatiKorunur() throws Exception {
        String ay = YearMonth.from(yarin).toString();
        at.get("/api/rapor/adalet?ay=" + ay).andExpect(jsonPath("$.satirlar[?(@.sicil=='D-A')].gorevSaat").value(contains(5.5)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-B')].gorevSaat").value(contains(6.5)));

        at.post("/api/sefer/" + p[1] + "/iptal", iptal(null)).andExpect(status().isOk());

        at.get("/api/atama/gun?tarih=" + yarin).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false)).andExpect(jsonPath("$.satirlar[1].makinistler[0].kirmizi").value(true));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].durum").value("PLANLI")).andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU"));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].durum").value("IPTAL"));
        at.get("/api/rapor/adalet?ay=" + ay).andExpect(jsonPath("$.satirlar[?(@.sicil=='D-A')].gorevSaat").value(contains(5.5)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-A')].seferSayisi").value(contains(1)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-B')].gorevSaat").value(contains(0.0)));
    }

    @Test
    void iptal_devamFalse_parca1inYalnizKendisi() throws Exception {
        at.post("/api/sefer/" + p[0] + "/iptal", iptal(false)).andExpect(status().isOk());

        at.get("/api/atama/gun?tarih=" + yarin).andExpect(jsonPath("$.satirlar", hasSize(2))).andExpect(jsonPath("$.satirlar[0].durum").value("IPTAL"))
                .andExpect(jsonPath("$.satirlar[1].durum").value("PLANLI")).andExpect(jsonPath("$.satirlar[1].makinistler[0].kirmizi").value(false));
    }
}
