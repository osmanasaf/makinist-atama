package com.makinistatama.takas;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.atama.ParcaliAnkaraTren;
import com.makinistatama.sefer.SeferYuklemeServisi;

/**
 * F2c tur 3 (review "Tur 2"): OK-17 takasta da bağ KURULMADAN görülür. M parça 2'de (atama gecikmeden önce, donuk başlangıç), A parça 1'de; parça 1 gecikmeli. M, kendi başka
 * görevini A'nın parça 1'iyle takas etmek ister: takas sonrası M iki parçayı da süreceği için parça 2 güncel kalkışla değerlendirilmeli; sahte K-10 çıkmamalı.
 */
class TakasParcaOk17Test extends TakasTabani {

    @Autowired
    SeferYuklemeServisi yukleme;

    /** F2d tur 2 / W2: hazırlık = alıkoyma = 15 dk iken aynı takas (sentetik aday satırı gerçek zincir bağıyla kurulur). */
    @Test
    void hazirlikAlikoyma15_parca2ndeOlanMakinist_parca1iTakastaAlir() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar IN ('gorev.hazirlik_dk', 'gorev.alikoyma_dk')");
        long m = hesapli("D001");
        long a = hesapli("D002");
        long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, "DE-1903", gun(3).toString(), "08:00", "10:00", "10:10", "13:00");
        atama(p[1], m, "ASIL");
        long ataA = atama(p[0], a, "ASIL");
        long ataX = atama(sefer("TX", gun(5), "08:00", "09:00"), m, "ASIL");
        atamaci().post("/api/sefer/" + p[0] + "/gecikme", "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        giris("D001").post("/api/benim/takas", acGovdesi(ataX, ataA, "IZIN", null)).andExpect(status().isCreated());
    }

    /**
     * F2d Tur 3 / W2 (BIRAKILAN parçanın komşusu): D001 aynı trenin iki parçasını taşıyor; parça 1'i D002'nin TX görevi ile takas etmek ister. Takas sonrası parça 2'nin önceki
     * parçası D001'de değildir: hazırlık geri gelir (09:55) ve TX'in alıkoymasıyla (10:10) örtüşür. Ayar 15'te takas açılışta reddedilmeli (onaylanıp kırmızı atama üretmemeli).
     */
    @Test
    void hazirlikAlikoyma15_parca1iBirakipBaskaGorevAlan_parca2Orselesir_takasReddedilir() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar IN ('gorev.hazirlik_dk', 'gorev.alikoyma_dk')");
        long m = hesapli("D001");
        long a = hesapli("D002");
        long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, "DE-2903", gun(3).toString(), "08:00", "10:00", "10:10", "13:00");
        long ataP1 = atama(p[0], m, "ASIL");
        atama(p[1], m, "ASIL");
        long ataX = atama(sefer("TX", gun(3), "08:10", "09:55"), a, "ASIL");

        giris("D001").post("/api/benim/takas", acGovdesi(ataP1, ataX, "IZIN", null)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.kod").value("TAKAS_KURALA_UYMUYOR"));
        atamaci().get("/api/atama/kirmizi-sayisi?bas=" + gun(2) + "&bit=" + gun(4))
                .andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    /** Aynı sahne ayar 0'da (kontrol): hazırlık/alıkoyma yok, takas geçer ve kırmızı atama oluşmaz. */
    @Test
    void ayar0_parca1iBirakipBaskaGorevAlan_takasOnaylanir_kirmiziYok() throws Exception {
        jdbc.update("UPDATE kural_siddet SET siddet = 'KAPALI' WHERE kod = 'K-23'"); // OK-24: varsayılan K-23 ENGEL/20 dk; bu test hazırlık/alıkoyma ayarını sınar (TestVeritabani tohuma döndürür)
        long m = hesapli("D001");
        long a = hesapli("D002");
        long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, "DE-2904", gun(3).toString(), "08:00", "10:00", "10:10", "13:00");
        long ataP1 = atama(p[0], m, "ASIL");
        atama(p[1], m, "ASIL");
        long ataX = atama(sefer("TX", gun(3), "08:10", "09:55"), a, "ASIL");

        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(ataP1, ataX, "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());
        atamaci().get("/api/atama/kirmizi-sayisi?bas=" + gun(2) + "&bit=" + gun(4))
                .andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void parca2ndeOlanMakinist_gecikmeliParca1iAlirkenSahteCakismaYok() throws Exception {
        long m = hesapli("D001");
        long a = hesapli("D002");
        long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, "DE-1703", gun(3).toString(), "08:00", "10:00", "10:10", "13:00");
        long ataM = atama(p[1], m, "ASIL"); // gecikmeden ÖNCE: donuk başlangıç 10:10
        long ataA = atama(p[0], a, "ASIL");
        long ataX = atama(sefer("TX", gun(5), "08:00", "09:00"), m, "ASIL");
        atamaci().post("/api/sefer/" + p[0] + "/gecikme", "{\"kalkisGecikmeDk\":0,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        giris("D001").post("/api/benim/takas", acGovdesi(ataX, ataA, "IZIN", null)).andExpect(status().isCreated());
    }
}
