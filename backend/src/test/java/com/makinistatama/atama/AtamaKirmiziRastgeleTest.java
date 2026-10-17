package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * MK-5 değişmezi (BUG-14), rastgele senaryolarla: aday listesinin "uygun" dediği ve POST'un kabul ettiği atama, hemen ardından gün listesinde kırmızı OLMAZ.
 * Makinist başına rastgele (gece ağırlıklı) görevler SQL ile yazılır (kural dışı olabilir; bu yüzden çoğu aday uygun çıkmaz), sonra rastgele bir sefer sorulur.
 */
class AtamaKirmiziRastgeleTest extends TestVeritabani {

    static final LocalDate BAS = LocalDate.of(2026, 10, 5);
    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_rastgele60Senaryo() throws Exception {
        dene(false, false, 5, 5);
    }

    /** OK-6: aynı değişmez, gecikmeli seferlerle (atama gecikmeden önce de sonra da yapılmış olabilir). */
    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_gecikmeliSeferlerle_OK6() throws Exception {
        dene(true, false, 5, 5);
    }

    /** NFR-06: aynı değişmez, 7 açık özel kuralla (gün/hafta/ay/gece-vardiya kapsamı/gece süresi/görev sayısı/gece nöbeti sayısı). */
    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_ozelKurallarlaKarisik() throws Exception {
        dene(false, true, 3, 5);
    }

    /** NFR-06 + OK-6: özel kurallar ve gecikmeli seferler birlikte. */
    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_ozelKurallarVeGecikmeliSeferlerle() throws Exception {
        dene(true, true, 3, 5);
    }

    /** K-23 (asgari aktarma 20 dk, ENGEL) açıkken aynı değişmez; rastgele görevlerin bir kısmı 20 dk'dan kısa aktarma üretir ve K-23 ile reddedilir. */
    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_asgariAktarmaAcik_K23() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '20' WHERE anahtar = 'nobet.asgari_aktarma_dk'"); // TestVeritabani her testten önce tohuma döndürür
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-23'");
        dene(false, false, 5, 5);
        assertThat(k23Red).as("K-23 ile reddedilen aday sayısı (kural gerçekten devrede)").isGreaterThan(0);
    }

    /** OK-24: eski varsayılan (K-14 haftalık üst sınır ENGEL) da desteklenen yapılandırmadır; aynı değişmez bu yapılandırmada da geçerli olmalı (varsayılan KAPALI + K-23 ENGEL diğer testlerde). */
    @Test
    void kabulEdilenAtamaHemenKirmiziOlmaz_K14Engel() throws Exception {
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-14'"); // TestVeritabani her testten önce tohuma döndürür
        dene(false, false, 5, 5);
    }

    private int k23Red;

    private void ozelKurallariYaz() {
        String[][] kurallar = {
            { "TOPLAM_GOREV_SURESI", "GUN", "9", "{}" },
            { "TOPLAM_GOREV_SURESI", "HAFTA", "30", "{}" },
            { "TOPLAM_GOREV_SURESI", "GUN", "5", "{\"vardiya\":\"GECE\"}" },
            { "GECE_SURESI", "GUN", "4", "{}" },
            { "GOREV_SAYISI", "GUN", "3", "{}" },
            { "GECE_NOBETI_SAYISI", "HAFTA", "2", "{}" },
            { "TOPLAM_GOREV_SURESI", "AY", "50", "{\"vardiya\":\"GUNDUZ\"}" } };
        for (int i = 0; i < kurallar.length; i++) {
            jdbc.update("INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan) "
                    + "VALUES (?, ?, ?, 'EN_FAZLA', ?, ?, 'ENGEL', '2026-01-01', 'rastgele', 'test')", "Rastgele " + i, kurallar[i][0], kurallar[i][1],
                    new java.math.BigDecimal(kurallar[i][2]), kurallar[i][3]);
        }
    }

    private void dene(boolean gecikmeli, boolean ozel, int enAzKabul, int enAzRed) throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        if (ozel) {
            ozelKurallariYaz();
        }
        Tarayici t = atamaci();
        Random r = new Random(gecikmeli ? 6 : 14);
        int kabul = 0;
        int uygunDegil = 0;
        for (int tur = 0; tur < 60; tur++) {
            long m = v.makinist("R" + tur);
            int adet = 3 + r.nextInt(6);
            List<Long> mevcut = new ArrayList<>();
            for (int i = 0; i < adet; i++) {
                mevcut.add(rastgeleSefer(v, r, "M" + tur + "_" + i));
                boolean sonra = gecikmeli && r.nextBoolean();
                if (gecikmeli && !sonra) {
                    gecikme(mevcut.getLast(), r);
                }
                v.atama(mevcut.getLast(), m, "ASIL");
                if (sonra) {
                    gecikme(mevcut.getLast(), r);
                }
            }
            long aday = rastgeleSefer(v, r, "A" + tur);
            if (gecikmeli && r.nextBoolean()) {
                gecikme(aday, r);
            }
            LocalDate gun = jdbc.queryForObject("SELECT tarih FROM sefer WHERE id = ?", LocalDate.class, aday);

            JsonNode adaylar = json(t.get("/api/atama/sefer/" + aday + "/adaylar").andReturn().getResponse().getContentAsString());
            boolean uygun = false;
            for (JsonNode a : adaylar) {
                if (a.get("makinistId").asLong() == m) {
                    uygun = a.get("uygun").asBoolean();
                }
            }
            if (!uygun) {
                uygunDegil++;
                for (JsonNode a : adaylar) {
                    if (a.get("makinistId").asLong() == m && a.get("ihlaller").toString().contains("\"K-23\"")) {
                        k23Red++;
                    }
                }
                t.post("/api/atama/sefer/" + aday, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()); // aday listesi ile POST tutarlı
                continue;
            }
            String govde = AtamaVeri.ataGovdesi(m).replace("}", ",\"gerekce\":\"deneme\"}");
            t.post("/api/atama/sefer/" + aday, govde).andExpect(status().isCreated());
            kabul++;
            JsonNode liste = json(t.get("/api/atama/gun?tarih=" + gun).andReturn().getResponse().getContentAsString());
            for (JsonNode s : liste.get("satirlar")) {
                if (s.get("seferId").asLong() == aday) {
                    for (JsonNode g : s.get("makinistler")) {
                        assertThat(g.get("kirmizi").asBoolean()).as("tur %d: kabul edilen atama kırmızı: %s", tur, g.get("kirmiziNedenler")).isFalse();
                    }
                }
            }
        }
        assertThat(kabul).as("kabul edilen senaryo sayısı (testin anlamlı olması için)").isGreaterThan(enAzKabul);
        assertThat(uygunDegil).isGreaterThan(enAzRed);
    }

    /** Kalkış 30–180 dk, varış en az kalkış kadar gecikir (BUG-11 kısıtı). */
    private void gecikme(long sefer, Random r) {
        int k = 30 * (1 + r.nextInt(6));
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = ?, varis_gecikme_dk = ? WHERE id = ?", k, k + 30 * r.nextInt(3), sefer);
    }

    private long rastgeleSefer(AtamaVeri v, Random r, String ad) {
        LocalDate gun = BAS.plusDays(r.nextInt(21));
        String kalkis = SAATLER[r.nextInt(SAATLER.length)];
        java.time.LocalTime k = java.time.LocalTime.parse(kalkis);
        String varis = k.plusMinutes(SURELER[r.nextInt(SURELER.length)]).toString();
        return v.seferEv(ad, gun, kalkis, varis);
    }

    private static JsonNode json(String s) {
        return new JsonMapper().readTree(s);
    }
}
