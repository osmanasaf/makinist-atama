package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.ZamanAyari;

/** FR-M10: atamacı "gördü ✓ / görmedi / hesap yok" ve üst çubuk sayaçları; yalnız kalkışı geçmemiş atamalar. */
class GordumOzetiTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    /** Yeni makinist + ona ait tek seferlik atama. {@code hesap}: hesap açılsın mı. Dönen: atama kimliği. */
    private long gorev(AtamaVeri v, String sicil, String tren, LocalDate gun, String kalkis, String varis, boolean hesap) {
        long m = v.makinist(sicil);
        if (hesap) {
            kullanicilar.makinistHesabiAc(m, sicil, "Demo Makinist " + sicil, Tarayici.PAROLA);
            jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE makinist_id = ?", m);
        }
        return v.atama(v.sefer(tren, gun, kalkis, varis), m, "ASIL");
    }

    @Test
    void onAtamaAltiGordu_dortGorulmeyen_ucHesapsiz_ayriSayac() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate gun = BUGUN.plusDays(3);
        List<Long> atamalar = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            atamalar.add(gorev(v, String.format("D%03d", i), "H" + i, gun, String.format("%02d:00", 5 + i), String.format("%02d:30", 5 + i), true));
        }
        for (int i = 11; i <= 13; i++) {
            gorev(v, String.format("D%03d", i), "H" + i, gun, String.format("%02d:00", 5 + i), String.format("%02d:30", 5 + i), false);
        }
        gorev(v, "D099", "GECMIS", BUGUN.minusDays(1), "08:00", "09:00", true); // kalkışı geçmiş: sayılmaz
        for (int i = 1; i <= 6; i++) {
            Tarayici t = Tarayici.girisYapmis(mvc, String.format("d%03d", i), Tarayici.PAROLA);
            GordumYardimci.gordum(t, atamalar.get(i - 1)).andExpect(status().isOk());
        }
        Tarayici a = atamaci();

        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN.minusDays(1) + "&bit=" + gun).andExpect(status().isOk()).andExpect(jsonPath("$.toplam").value(13))
                .andExpect(jsonPath("$.gorulen").value(6)).andExpect(jsonPath("$.gorulmeyen").value(4)).andExpect(jsonPath("$.hesapsiz").value(3))
                .andExpect(jsonPath("$.uyari").value(0));

        a.get("/api/atama/gun?tarih=" + gun).andExpect(jsonPath("$.satirlar[?(@.trenNo=='H1')].makinistler[0].gordu").value("GORDU"))
                .andExpect(jsonPath("$.satirlar[?(@.trenNo=='H1')].makinistler[0].gorduZamani").exists())
                .andExpect(jsonPath("$.satirlar[?(@.trenNo=='H7')].makinistler[0].gordu").value("GORMEDI"))
                .andExpect(jsonPath("$.satirlar[?(@.trenNo=='H12')].makinistler[0].gordu").value("HESAP_YOK"));
        // geçmiş günün listesinde bilgi yok (null)
        a.get("/api/atama/gun?tarih=" + BUGUN.minusDays(1)).andExpect(jsonPath("$.satirlar[0].makinistler[0].gordu").doesNotExist());
    }

    @Test
    void kalkisaOnIkiSaattenAzKala_gorulmeyen_uyariVeVurgu() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDateTime yakin = LocalDateTime.now(ZamanAyari.BOLGE).plusHours(3);
        LocalDateTime uzak = LocalDateTime.now(ZamanAyari.BOLGE).plusHours(30);
        String yakinSaat = String.format("%02d:%02d", yakin.getHour(), yakin.getMinute());
        String uzakSaat = String.format("%02d:%02d", uzak.getHour(), uzak.getMinute());
        gorev(v, "D001", "YAKIN", yakin.toLocalDate(), yakinSaat, "23:59".compareTo(yakinSaat) > 0 ? "23:59" : "00:30", true);
        gorev(v, "D002", "UZAK", uzak.toLocalDate(), uzakSaat, "23:59".compareTo(uzakSaat) > 0 ? "23:59" : "00:30", true);
        Tarayici a = atamaci();

        a.get("/api/atama/gordum-ozeti?bas=" + yakin.toLocalDate() + "&bit=" + uzak.toLocalDate()).andExpect(jsonPath("$.gorulmeyen").value(2)).andExpect(jsonPath("$.uyari").value(1));
        a.get("/api/atama/gun?tarih=" + yakin.toLocalDate()).andExpect(jsonPath("$.satirlar[?(@.trenNo=='YAKIN')].makinistler[0].gorduUyari").value(true));
        a.get("/api/atama/gun?tarih=" + uzak.toLocalDate()).andExpect(jsonPath("$.satirlar[?(@.trenNo=='UZAK')].makinistler[0].gorduUyari").value(false));
    }

    /** FR-05 (OK-30): makinistın görmesi gereken intikal de sayaçlara girer (gördü / görmedi / hesapsız; kalkışı geçmiş sayılmaz). */
    @Test
    void intikalDeSayilir_gordu_gormedi_hesapsiz_gecmisHaric() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate gun = BUGUN.plusDays(3);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long m3 = v.makinist("D003");
        long m4 = v.makinist("D004");
        makinistGirisli(m1, "D001"); // hesabı olan ama görmeyen
        Tarayici t2 = makinistGirisli(m2, "D002");
        v.intikal(m1, v.ankara, v.eskisehir, gun, "07:00", "09:00"); // id 1: görmedi
        v.intikal(m2, v.ankara, v.eskisehir, gun, "07:00", "09:00"); // id 2: gördü
        v.intikal(m3, v.ankara, v.eskisehir, gun, "07:00", "09:00"); // id 3: hesapsız
        v.intikal(m1, v.ankara, v.eskisehir, BUGUN.minusDays(1), "07:00", "09:00"); // id 4: geçmiş, sayılmaz
        String liste = t2.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString();
        String ozet = com.jayway.jsonpath.JsonPath.read(liste, "$.intikaller[0].ozet");
        t2.post("/api/benim/intikal/2/gordum", "{\"ozet\":" + new tools.jackson.databind.json.JsonMapper().writeValueAsString(ozet) + "}").andExpect(status().isOk());
        Tarayici a = atamaci();

        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN.minusDays(1) + "&bit=" + gun).andExpect(jsonPath("$.toplam").value(3)).andExpect(jsonPath("$.gorulen").value(1))
                .andExpect(jsonPath("$.gorulmeyen").value(1)).andExpect(jsonPath("$.hesapsiz").value(1));

        // iptal edilen intikal makinistte "İPTAL" kartıyla yeniden onay ister (OK-9 ile aynı): görülmeyene girer
        makinistGirisli(m4, "D004");
        v.intikal(m4, v.ankara, v.eskisehir, gun, "10:00", "11:30"); // id 5
        a.delete("/api/intikal/5?neden=IZIN&versiyon=0").andExpect(status().isOk());
        a.get("/api/atama/gordum-ozeti?bas=" + gun + "&bit=" + gun).andExpect(jsonPath("$.toplam").value(4)).andExpect(jsonPath("$.gorulmeyen").value(2));
    }

    @Test
    void aralikSiniri_62Gun_ters_aralik400() throws Exception {
        Tarayici a = atamaci();
        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + BUGUN.minusDays(1)).andExpect(status().isBadRequest());
        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + BUGUN.plusDays(63)).andExpect(status().isBadRequest());
        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + BUGUN.plusDays(62)).andExpect(status().isOk()).andExpect(jsonPath("$.toplam").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gorev_gordum", Integer.class)).isZero();
    }

    @Test
    void makinistGunListesiVeSayaciGormez_403() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");
        t.get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + BUGUN).andExpect(status().isForbidden());
        t.get("/api/atama/gun?tarih=" + BUGUN).andExpect(status().isForbidden());
    }
}
