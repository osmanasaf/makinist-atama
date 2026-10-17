package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** F2b MK-45/FR-13: makinistin ekranında YOLCU görevi "Yolcu" etiketiyle; eş makinist yok; asıl makinistin kartında yolcu adı yok; gördüm ve DEĞİŞTİ aynı; özet adaletle eşit. */
class BenimYolculukTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    @Test
    void yolcuGorevi_yolcuEtiketi_esMakinistBos_asilKartindaYolcuAdiYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long yolcu = v.makinist("D001");
        long asil = v.makinist("D002");
        long sefer = v.sefer("E1", BUGUN.plusDays(1), "08:00", "09:30"); // Ankara -> Eskişehir
        v.atama(sefer, yolcu, "YOLCU");
        v.atama(sefer, asil, "ASIL");
        Tarayici ty = makinistGirisli(yolcu, "D001");
        Tarayici ta = makinistGirisli(asil, "D002");

        String yg = ty.get("/api/benim/gorevler").andExpect(status().isOk()).andExpect(jsonPath("$.gorevler.length()").value(1)).andExpect(jsonPath("$.gorevler[0].rol").value("YOLCU"))
                .andExpect(jsonPath("$.gorevler[0].esMakinist").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(yg, "$.gorevler[0].ozet")).startsWith("Yolcu · Ankara").contains("→ Eskişehir");
        assertThat(yg).doesNotContain("D002");

        String ag = ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].rol").value("ASIL")).andExpect(jsonPath("$.gorevler[0].esMakinist").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(ag).doesNotContain("D001");
    }

    @Test
    void gordumGecerli_gecikmeOzetiDegistirir_DEGISTI() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("E1", BUGUN.plusDays(1), "08:00", "09:30");
        long atama = v.atama(sefer, m, "YOLCU");
        Tarayici t = makinistGirisli(m, "D001");

        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU"));
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 25, varis_gecikme_dk = 25 WHERE id = ?", sefer);
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
        atamaci().get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + BUGUN.plusDays(2)).andExpect(jsonPath("$.toplam").value(1)).andExpect(jsonPath("$.gorulmeyen").value(1));
    }

    @Test
    void ozet_adaletRaporununKendiSatiri_yolculukSayisiylaEsit() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        LocalDate gun = BUGUN.withDayOfMonth(15);
        v.atama(v.sefer("E1", gun, "08:00", "09:30"), m, "YOLCU");
        v.atama(v.sefer("S2", gun, "10:30", "15:00", v.eskisehir, v.ankara), m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        YearMonth ay = YearMonth.from(gun);

        String ozet = t.get("/api/benim/ozet?ay=" + ay).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rapor = atamaci().get("/api/rapor/adalet?ay=" + ay).andReturn().getResponse().getContentAsString();
        List<Number> saat = JsonPath.read(rapor, "$.satirlar[?(@.sicil=='D001')].gorevSaat");
        List<Number> yolculuk = JsonPath.read(rapor, "$.satirlar[?(@.sicil=='D001')].yolculukSayisi");
        assertThat(((Number) JsonPath.read(ozet, "$.gorevSaat")).doubleValue()).isEqualTo(saat.getFirst().doubleValue()).isEqualTo(6.0);
        assertThat(((Number) JsonPath.read(ozet, "$.seferSayisi")).intValue()).isEqualTo(1);
        assertThat(((Number) JsonPath.read(ozet, "$.yolculukSayisi")).intValue()).isEqualTo(yolculuk.getFirst().intValue()).isEqualTo(1);
        assertThat(((Number) JsonPath.read(ozet, "$.km")).doubleValue()).isEqualTo(250.0);
    }
}
