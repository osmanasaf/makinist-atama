package com.makinistatama.rapor;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * OK-1, OK-2, OK-3 (M4 review W3, W4): çalışma süresinin TEK tanımı. Puantaj ve adalet aynı aralığı (planlanan kalkıştan güncel varışa, hazırlık/alıkoyma dahil) aynı
 * şekilde ay sınırından bölerek sayar; gece saati iki raporda aynı sayıdır (UX-4).
 */
class CalismaSuresiTekKaynakTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long m;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        t = atamaci();
    }

    private void iki(String ay, double saat) throws Exception {
        t.get("/api/rapor/puantaj?ay=" + ay + "&makinist=" + m).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(saat));
        t.get("/api/rapor/adalet?ay=" + ay).andExpect(jsonPath("$.satirlar[0].gorevSaat").value(saat));
    }

    @Test
    void kalkisGecikmesiCalismaSuresindenSayilir_08_12_gecikme60_60_bes_saat() throws Exception {
        long s = v.sefer("S1", GUN, "08:00", "12:00");
        v.atama(s, m, "ASIL");
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 60, varis_gecikme_dk = 60 WHERE id = ?", s);

        iki("2026-10", 5.0); // makinist 08:00'de hazır, tren 09:00'da kalktı, 13:00'te vardı
    }

    @Test
    void gecikmeGirildiktenSonraYapilanAtama_guncelKalkistanSayilir_OK6() throws Exception {
        long s = v.sefer("S1", GUN, "08:00", "12:00");
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 120, varis_gecikme_dk = 120 WHERE id = ?", s);
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        iki("2026-10", 4.0); // makinist 10:00-14:00; gecikme atamadan önce bilindiği için bekleme sayılmaz
    }

    @Test
    void hazirlikVeAlikoyma_puantajVeAdaletteDeSayilir() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.hazirlik_dk'");
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.alikoyma_dk'");
        v.atama(v.sefer("S1", GUN, "08:00", "12:00"), m, "ASIL");

        iki("2026-10", 5.0);
    }

    @Test
    void gecikmeVeHazirlikBirlikte_tekAralik() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.hazirlik_dk'");
        long s = v.sefer("S1", GUN, "08:00", "12:00");
        v.atama(s, m, "ASIL");
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 60, varis_gecikme_dk = 90 WHERE id = ?", s);

        iki("2026-10", 6.0); // 07:30 -> 13:30
    }

    @Test
    void ayiAsanVardiya_ikiRaporda_geceYarisindanBolunur_geceSaatiAyni() throws Exception {
        v.atama(v.seferEv("G", LocalDate.of(2026, 10, 31), "22:00", "03:00"), m, "ASIL");

        iki("2026-10", 2.0);
        iki("2026-11", 3.0);
        for (String ay : new String[] { "2026-10", "2026-11" }) {
            double gece = ay.endsWith("10") ? 2.0 : 3.0;
            t.get("/api/rapor/puantaj?ay=" + ay + "&makinist=" + m).andExpect(jsonPath("$.satirlar[0].geceSaat").value(gece));
            t.get("/api/rapor/adalet?ay=" + ay).andExpect(jsonPath("$.satirlar[0].geceSaat").value(gece));
        }
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[0].seferSayisi").value(1)); // sefer ve km planlanan günün ayına
        t.get("/api/rapor/adalet?ay=2026-11").andExpect(jsonPath("$.satirlar[0].seferSayisi").value(0)).andExpect(jsonPath("$.satirlar[0].km").value(0.0));
    }

    @Test
    void gece20_07_adaletPuantajAyniGeceSaati() throws Exception {
        v.atama(v.seferEv("G", GUN, "18:00", "08:00"), m, "ASIL"); // 14 s; gece penceresi 20:00-07:00 = 11 s

        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].geceSaat").value(11.0));
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[0].geceSaat").value(11.0)).andExpect(jsonPath("$.geceSaatiPenceresi").value("20:00–07:00"));
    }
}
