package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.ZamanAyari;

/**
 * QA (F2a-2): makinist-atamacı DÖNGÜSÜ uçtan uca. Atamacı değiştirince / geciktirince / iptal edince atamacı ekranı (gün listesi + sayaç) "görmedi"ye döner ve
 * makinist yeniden "gördüm" deyince "gördü"ye döner; iki görevli bir seferde bağımsız; eski onay yeni makinist için "gördü" sayılmaz.
 */
class GordumAtamaciQaTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    private long hesapliMakinist(AtamaVeri v, String sicil) {
        long m = v.makinist(sicil);
        kullanicilar.makinistHesabiAc(m, sicil, "Demo Makinist " + sicil, Tarayici.PAROLA);
        jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE makinist_id = ?", m);
        return m;
    }

    private String gun(String tren, int alan) {
        return "$.satirlar[?(@.trenNo=='" + tren + "')].makinistler[" + alan + "]";
    }

    @Test
    void gecikme_atamaciEkranindaGormediyeDoner_yenidenGordumlaGorduyeDoner() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate g = BUGUN.plusDays(2);
        long m = hesapliMakinist(v, "D001");
        long sefer = v.sefer("T1", g, "08:10", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA);
        Tarayici a = atamaci();
        String ozet = "/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + g;
        GordumYardimci.gordum(t, atama).andExpect(status().isOk());
        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORDU"));
        a.get(ozet).andExpect(jsonPath("$.gorulen").value(1)).andExpect(jsonPath("$.gorulmeyen").value(0));

        a.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":30,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORMEDI")).andExpect(jsonPath("$.satirlar[0].makinistler[0].gorduZamani").doesNotExist());
        a.get(ozet).andExpect(jsonPath("$.gorulen").value(0)).andExpect(jsonPath("$.gorulmeyen").value(1));

        GordumYardimci.gordum(t, atama).andExpect(status().isOk());
        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORDU"));
        a.get(ozet).andExpect(jsonPath("$.gorulen").value(1)).andExpect(jsonPath("$.gorulmeyen").value(0));
    }

    @Test
    void baskasinaDegisince_atamaciEkraninda_yeniMakinistGormedi_eskiOnayGordusayilmaz() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate g = BUGUN.plusDays(2);
        long m1 = hesapliMakinist(v, "D001");
        long m2 = hesapliMakinist(v, "D017");
        long sefer = v.sefer("T1", g, "08:10", "10:00");
        long atama = v.atama(sefer, m1, "ASIL");
        Tarayici t1 = Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA);
        Tarayici a = atamaci();
        GordumYardimci.gordum(t1, atama).andExpect(status().isOk());

        a.post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + m2 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk());

        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".sicil").value("D017")).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORMEDI"));
    }

    /** OK-9 (QA BUG-4): iptal edilen seferde de atamacı masasında makinistin iptali gördü / görmedi bilgisi görünür; makinist yeniden "gördüm" deyince "gördü"ye döner. */
    @Test
    void iptal_makinistteDegistiOlur_atamaciEkranindaGormediyeDonerGordumlaGorduyeDoner() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate g = BUGUN.plusDays(2);
        long m = hesapliMakinist(v, "D001");
        long sefer = v.sefer("T1", g, "08:10", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA);
        Tarayici a = atamaci();
        GordumYardimci.gordum(t, atama).andExpect(status().isOk());
        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORDU"));

        a.post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"));
        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORMEDI"));
        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + g).andExpect(jsonPath("$.toplam").value(1)).andExpect(jsonPath("$.gorulmeyen").value(1));

        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        a.get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("GORDU"));
        a.get("/api/atama/gordum-ozeti?bas=" + BUGUN + "&bit=" + g).andExpect(jsonPath("$.gorulen").value(1)).andExpect(jsonPath("$.gorulmeyen").value(0));
    }

    @Test
    void asilVeYardimci_birSeferdeBagimsizOnay() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate g = BUGUN.plusDays(2);
        long asil = hesapliMakinist(v, "D001");
        long yardimci = hesapliMakinist(v, "D002");
        long sefer = v.sefer("T1", g, "08:10", "10:00");
        long a1 = v.atama(sefer, asil, "ASIL");
        v.atama(sefer, yardimci, "YARDIMCI");
        Tarayici t1 = Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA);
        GordumYardimci.gordum(t1, a1).andExpect(status().isOk());

        String yanit = atamaci().get("/api/atama/gun?tarih=" + g).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(yanit).contains("\"sicil\":\"D001\"").contains("\"sicil\":\"D002\"");
        atamaci().get("/api/atama/gordum-ozeti?bas=" + g + "&bit=" + g).andExpect(jsonPath("$.toplam").value(2)).andExpect(jsonPath("$.gorulen").value(1))
                .andExpect(jsonPath("$.gorulmeyen").value(1));
    }

    @Test
    void kapaliHesap_atamaciEkraninda_hesapYok_gorulmeyenDegil() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate g = BUGUN.plusDays(2);
        long m = hesapliMakinist(v, "D001");
        v.atama(v.sefer("T1", g, "08:10", "10:00"), m, "ASIL");
        jdbc.update("UPDATE kullanici SET aktif = FALSE WHERE makinist_id = ?", m);

        atamaci().get("/api/atama/gordum-ozeti?bas=" + g + "&bit=" + g).andExpect(jsonPath("$.hesapsiz").value(1)).andExpect(jsonPath("$.gorulmeyen").value(0));
        atamaci().get("/api/atama/gun?tarih=" + g).andExpect(jsonPath(gun("T1", 0) + ".gordu").value("HESAP_YOK"));
    }
}
