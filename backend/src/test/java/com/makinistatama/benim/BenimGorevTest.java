package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.atama.MakinistGorevDto;
import com.makinistatama.ortak.ZamanAyari;

/** FR-M04: makinistin kendi görev listesi (bugün + ufuk, iptal dahil, yalnız kendi satırları). */
class BenimGorevTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    @Autowired
    BenimServisi servis;

    @Test
    void dortSatir_ufukDisindakilerYok_baskasininGorevi0() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        for (int gun : new int[] { 0, 1, 3, 6 }) {
            v.atama(v.sefer("T" + gun, BUGUN.plusDays(gun), "08:00", "10:00"), m, "ASIL");
        }
        v.atama(v.sefer("T7", BUGUN.plusDays(7), "08:00", "10:00"), m, "ASIL"); // ufuk 7 gün: bugün..bugün+6
        v.atama(v.sefer("T8", BUGUN.plusDays(8), "08:00", "10:00"), m, "ASIL");
        v.atama(v.sefer("TB", BUGUN.plusDays(1), "09:00", "11:00"), baska, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");

        t.get("/api/benim/gorevler").andExpect(status().isOk()).andExpect(jsonPath("$.ufukGun").value(7)).andExpect(jsonPath("$.gorevler.length()").value(4))
                .andExpect(jsonPath("$.gorevler[0].trenNo").value("T0")).andExpect(jsonPath("$.gorevler[0].kalkisGar").value("Ankara"))
                .andExpect(jsonPath("$.gorevler[0].rol").value("ASIL")).andExpect(jsonPath("$.gorevler[3].trenNo").value("T6"));
        String govde = t.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString();
        assertThat(govde).doesNotContain("TB").doesNotContain("D002").doesNotContain("Demo Makinist D002");
    }

    @Test
    void iptalSeferListedenDusmez_gecikmeGuncelSaatiGosterir() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long iptal = v.sefer("T1", BUGUN, "08:00", "10:00");
        long gecikme = v.sefer("T2", BUGUN.plusDays(1), "08:10", "10:00");
        v.atama(iptal, m, "ASIL");
        v.atama(gecikme, m, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptal);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 25, varis_gecikme_dk = 25 WHERE id = ?", gecikme);
        Tarayici t = makinistGirisli(m, "D001");

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(2)).andExpect(jsonPath("$.gorevler[0].durum").value("IPTAL"))
                .andExpect(jsonPath("$.gorevler[1].kalkisGecikmeDk").value(25)).andExpect(jsonPath("$.gorevler[1].planlananKalkis").value(org.hamcrest.Matchers.containsString("T08:10")))
                .andExpect(jsonPath("$.gorevler[1].guncelKalkis").value(org.hamcrest.Matchers.containsString("T08:35")));
    }

    @Test
    void geceYarisiSonrasiVaris_kalkisGundeKalir_esMakinistAdiGelir_sira() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long es = v.makinist("D002");
        long gece = v.sefer("TG", BUGUN, "23:30", "02:10");
        long erken = v.sefer("TE", BUGUN, "00:10", "01:00");
        v.atama(gece, m, "ASIL");
        v.atama(gece, es, "YARDIMCI");
        v.atama(erken, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(2))
                .andExpect(jsonPath("$.gorevler[0].trenNo").value("TE")).andExpect(jsonPath("$.gorevler[0].sira").value(1)).andExpect(jsonPath("$.gorevler[0].varisErtesiGun").value(false))
                .andExpect(jsonPath("$.gorevler[1].trenNo").value("TG")).andExpect(jsonPath("$.gorevler[1].sira").value(2)).andExpect(jsonPath("$.gorevler[1].varisErtesiGun").value(true))
                .andExpect(jsonPath("$.gorevler[1].tarih").value(BUGUN.toString())).andExpect(jsonPath("$.gorevler[1].esMakinist").value("Demo Makinist D002"))
                .andExpect(jsonPath("$.gorevler[0].esMakinist").doesNotExist());
    }

    @Test
    void sabitGun_gunSiniriVeAyarliUfuk() {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        LocalDate g = LocalDate.of(2026, 11, 10);
        v.atama(v.sefer("A", g.minusDays(1), "23:50", "01:00"), m, "ASIL"); // önceki gün: bugünün listesinde değil
        v.atama(v.sefer("B", g, "00:00", "01:00"), m, "ASIL"); // 00:00 İstanbul: bugün
        v.atama(v.sefer("C", g.plusDays(2), "10:00", "11:00"), m, "ASIL");
        v.atama(v.sefer("D", g.plusDays(3), "10:00", "11:00"), m, "ASIL");
        var kisi = new BenimKimlik.Kisi(m, "D001", "Demo Makinist D001");

        assertThat(servis.gorevler(kisi, g).gorevler()).extracting(MakinistGorevDto::trenNo).containsExactly("B", "C", "D");
        jdbc.update("UPDATE uygulama_ayar SET deger = 3 WHERE anahtar = 'makinist.gorunur_gun'");
        assertThat(servis.gorevler(kisi, g).gorevler()).extracting(MakinistGorevDto::trenNo).isEqualTo(List.of("B", "C"));
    }
}
