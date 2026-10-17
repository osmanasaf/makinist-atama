package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * M2 QA: kabul örneklerinin API düzeyinde sayısal sınırları (dinlenme 11 s eşiği, belge bitiş günü, gece 7,5 s), parametre değişince aday
 * sonucunun değişmesi, ASIL/YARDIMCI etkileşimi, ENGEL'in gerekçeyle aşılamaması ve uç doğrulamaları.
 */
class AtamaQaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12); // Pazartesi
    static final LocalDate SONRAKI = GUN.plusDays(1);

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private String atamaIstegi(long makinist, String ekstra) {
        return "{\"makinistId\":" + makinist + ekstra + "}";
    }

    @Test
    void dinlenme_evGarinda_10s59dkEngel_11sUygun_metinSayisalDogru() throws Exception {
        long m = v.makinist("D001");
        long onceki = v.seferEv("T0", GUN, "14:00", "22:00"); // Ankara'da (ev) 22:00'de biter
        long erken = v.sefer("T1", SONRAKI, "08:59", "10:00");
        long tam = v.sefer("T2", SONRAKI, "09:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + onceki, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + erken, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[0].kod").value("K-11")).andExpect(jsonPath("$.ihlaller[0].olculen").value(org.hamcrest.Matchers.closeTo(10.98, 0.001)))
                .andExpect(jsonPath("$.ihlaller[0].esik").value(11.0)).andExpect(jsonPath("$.ihlaller[0].metin").value(containsString("< 11 s")));
        t.post("/api/atama/sefer/" + tam, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
    }

    @Test
    void dinlenme_disMerkezde_K12_sekizSaatEngel() throws Exception {
        long m = v.makinist("D001");
        long onceki = v.sefer("T0", GUN, "14:00", "22:00"); // Ankara -> Eskişehir: 22:00'de dış merkezde biter
        long sonraki = v.sefer("T1", SONRAKI, "06:00", "08:00", v.eskisehir, v.ankara);
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + onceki, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + sonraki, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-12"))).andExpect(jsonPath("$.ihlaller[0].metin").value(containsString("Dinlenme 8 s < 11 s")))
                .andExpect(jsonPath("$.ihlaller[0].metin").value(containsString("dış merkez")));
    }

    @Test
    void dinlenmeParametresi11den12ye_ayniAdayUygundanUygunDegileDoner_ve8deUygunOlur() throws Exception {
        long m = v.makinist("D001");
        long onceki = v.seferEv("T0", GUN, "14:00", "22:00");
        long s = v.sefer("T1", SONRAKI, "09:00", "10:00"); // tam 11 s sonra
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + onceki, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        String adaylar = "/api/atama/sefer/" + s + "/adaylar";
        t.get(adaylar).andExpect(jsonPath("$[0].uygun").value(true));

        yonetici().put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"12\",\"neden\":\"QA deneme\"}").andExpect(status().isOk());
        t.get(adaylar).andExpect(jsonPath("$[0].uygun").value(false)).andExpect(jsonPath("$[0].ihlaller[0].kod").value("K-11"))
                .andExpect(jsonPath("$[0].ihlaller[0].esik").value(12.0)).andExpect(jsonPath("$[0].ihlaller[0].metin").value(containsString("11 s < 12 s")));
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent());

        yonetici().put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"8\",\"neden\":\"QA deneme\"}").andExpect(status().isOk());
        t.get(adaylar).andExpect(jsonPath("$[0].uygun").value(true));
    }

    @Test
    void siddetKapali_ihlalKalkar_uyariyaCekilince_gerekceIster() throws Exception {
        long m = v.makinist("D001");
        long onceki = v.seferEv("T0", GUN, "14:00", "22:00");
        long s = v.sefer("T1", SONRAKI, "08:00", "10:00"); // 10 s dinlenme
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + onceki, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent());

        yonetici().put("/api/kural/K-11/siddet", "{\"siddet\":\"UYARI\",\"neden\":\"QA deneme\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        t.post("/api/atama/sefer/" + s, atamaIstegi(m, ",\"gerekce\":\"Acil\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.uyarilar[0].kod").value("K-11"));
    }

    @Test
    void engel_gerekceyleAsilamaz_ve_arkaPlanaYazilmaz() throws Exception {
        long m = v.makinist("D001", false, "AKTIF");
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().post("/api/atama/sefer/" + s, atamaIstegi(m, ",\"gerekce\":\"Yönetici onayladı\""))
                .andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }

    @Test
    void saglikRaporu_bitisGunuDahil_varisGunuBitisiAsarsaEngel() throws Exception {
        long m = v.makinist("D001");
        jdbc.update("UPDATE makinist SET saglik_bitis = ? WHERE id = ?", GUN, m);
        long gunduz = v.sefer("T1", GUN, "08:00", "10:00");
        long gece = v.sefer("T2", GUN, "22:00", "03:00"); // varış günü 13 Eki > bitiş 12 Eki
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + gece, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-03"))).andExpect(jsonPath("$.ihlaller[?(@.kod=='K-03')].olculen", contains(1.0)));
        t.post("/api/atama/sefer/" + gunduz, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        jdbc.update("UPDATE makinist SET saglik_bitis = ? WHERE id = ?", GUN.minusDays(1), m);
        long baska = v.sefer("T3", GUN, "12:00", "14:00");
        t.post("/api/atama/sefer/" + baska, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[0].kod").value("K-03"));
    }

    @Test
    void broveEksikVeyaSinavSuresiDolmus_K05_K06_ayriAyriNedenle() throws Exception {
        long yok = v.makinist("D001");
        jdbc.update("DELETE FROM brove_arac_tipi WHERE makinist_id = ?", yok);
        long eski = v.makinist("D002");
        jdbc.update("UPDATE brove_arac_tipi SET sinav_tarihi = '2021-03-10' WHERE makinist_id = ?", eski); // +60 ay = 2026-03-10
        long iyi = v.makinist("D003");
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().get("/api/atama/sefer/" + s + "/adaylar").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sicil").value("D003")).andExpect(jsonPath("$[0].uygun").value(true))
                .andExpect(jsonPath("$[?(@.sicil=='D001')].ihlaller[0].metin", contains("Araç tipi yetkisi yok: YHT")))
                .andExpect(jsonPath("$[?(@.sicil=='D002')].ihlaller[0].kod", contains("K-05")))
                .andExpect(jsonPath("$[?(@.sicil=='D002')].uygun", contains(false)));
        assertThat(iyi).isPositive();
    }

    @Test
    void geceNobeti_8saatEngel_7bucukUygun() throws Exception {
        long m = v.makinist("D001");
        long uzun = v.sefer("T1", GUN, "21:00", "05:00");
        long sinirda = v.sefer("T2", GUN, "22:00", "05:30");
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + uzun, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-16"))).andExpect(jsonPath("$.ihlaller[?(@.kod=='K-16')].olculen", contains(8.0)))
                .andExpect(jsonPath("$.ihlaller[?(@.kod=='K-16')].esik", contains(7.5)));
        t.post("/api/atama/sefer/" + sinirda, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
    }

    @Test
    void yardimci_kaldirilinca_asilKalir_ikinciKaldirma404() throws Exception {
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + s, atamaIstegi(m2, ",\"rol\":\"YARDIMCI\"")).andExpect(status().isCreated());

        t.delete("/api/atama/sefer/" + s + "?rol=YARDIMCI&neden=IZIN").andExpect(status().isNoContent());
        t.delete("/api/atama/sefer/" + s + "?rol=YARDIMCI&neden=IZIN").andExpect(status().isNotFound());

        assertThat(jdbc.queryForList("SELECT rol FROM atama WHERE sefer_id = ?", String.class, s)).containsExactly("ASIL");
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].sicil").value("D001"));
    }

    @Test
    void yardimciRolundeKiMakinist_cakisanSefereAsilAtanamaz_K10() throws Exception {
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long s1 = v.sefer("T1", GUN, "08:00", "10:00");
        long s2 = v.sefer("T2", GUN, "09:00", "11:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s1, atamaIstegi(m2, ",\"rol\":\"YARDIMCI\"")).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + s1, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + s2, AtamaVeri.ataGovdesi(m2)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-10")));
    }

    @Test
    void ucDogrulamalari_gunGecersiz400_izinEtkisiTersAralik400_olmayanMakinist404_rolGecersiz400() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();

        t.get("/api/atama/gun?tarih=2026-02-31").andExpect(status().isBadRequest());
        t.get("/api/atama/gun").andExpect(status().isBadRequest());
        t.get("/api/atama/makinist/" + m + "/izin-etkisi?bas=2026-10-24&bit=2026-10-20").andExpect(status().isBadRequest());
        t.get("/api/atama/makinist/99999/izin-etkisi?bas=2026-10-20&bit=2026-10-24").andExpect(status().isNotFound());
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m + ",\"rol\":\"BASMAKINIST\"}").andExpect(status().isBadRequest());
        t.post("/api/atama/sefer/" + s, "{}").andExpect(status().isBadRequest());
        t.delete("/api/atama/sefer/" + s + "?rol=ASIL&neden=YOKBOYLE").andExpect(status().isBadRequest());
    }

    @Test
    void anonim_atamaBelgeVeParametreUclari401_atamaciParametreYazamaz() throws Exception {
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici a = new Tarayici(mvc);

        a.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isUnauthorized());
        a.get("/api/atama/sefer/" + s + "/adaylar").andExpect(status().isUnauthorized());
        a.post("/api/atama/sefer/" + s, "{\"makinistId\":1}").andExpect(status().isUnauthorized());
        a.get("/api/rapor/belge-durumu").andExpect(status().isUnauthorized());
        a.get("/api/parametre").andExpect(status().isUnauthorized());
        atamaci().get("/api/rapor/belge-durumu").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }
}
