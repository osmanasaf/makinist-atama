package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** FR-M09: elinden alınan görev (kaldırıldı / başkasına verildi) en az 7 gün kalem olarak görünür; "Gördüm" ile kapanır; neden NOTU hiçbir yerde yok [V9]. */
class GorevOlayiTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    @Test
    void kaldir_eskiMakinistinEkraninda1Kalem_notYok_kapatilincaYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");

        atamaci().delete("/api/atama/sefer/" + sefer + "?rol=ASIL&neden=HASTALIK&nedenNotu=ozel-saglik-bilgisi").andExpect(status().isNoContent());

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(0)).andExpect(jsonPath("$.kaldirilanlar.length()").value(1))
                .andExpect(jsonPath("$.kaldirilanlar[0].tur").value("KALDIRILDI")).andExpect(jsonPath("$.kaldirilanlar[0].nedenEtiketi").value("HASTALIK"))
                .andExpect(jsonPath("$.kaldirilanlar[0].trenNo").value("T1"))
                .andExpect(jsonPath("$.kaldirilanlar[0].ozet").value(org.hamcrest.Matchers.containsString("Ankara")));
        assertThat(t.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString()).doesNotContain("ozel-saglik-bilgisi");
        assertThat(jdbc.queryForList("SELECT * FROM atama_olayi").toString()).doesNotContain("ozel-saglik-bilgisi");

        long olay = jdbc.queryForObject("SELECT id FROM atama_olayi", Long.class);
        t.post("/api/benim/olaylar/" + olay + "/gordum", "").andExpect(status().isNoContent());
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.kaldirilanlar.length()").value(0));
        t.post("/api/benim/olaylar/" + olay + "/gordum", "").andExpect(status().isNoContent()); // idempotent
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM'", Integer.class)).as("kapatma bir kez kayıt yazar").isEqualTo(1);
    }

    @Test
    void degistir_eskiMakinistteBaskasinaVerildi_yeniMakinistteKalemYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D017");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        v.atama(sefer, m1, "ASIL");
        Tarayici t1 = makinistGirisli(m1, "D001");
        Tarayici t2 = makinistGirisli(m2, "D017");

        atamaci().post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + m2 + ",\"neden\":\"IZIN\"}").andExpect(status().isOk());

        t1.get("/api/benim/gorevler").andExpect(jsonPath("$.kaldirilanlar.length()").value(1)).andExpect(jsonPath("$.kaldirilanlar[0].tur").value("DEGISTI_BASKASINA"))
                .andExpect(jsonPath("$.kaldirilanlar[0].nedenEtiketi").value("IZIN"));
        t2.get("/api/benim/gorevler").andExpect(jsonPath("$.kaldirilanlar.length()").value(0));
        String d1 = t1.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString();
        assertThat(d1).doesNotContain("D017"); // yeni makinistin kimliği eski makinistin ekranında yok
    }

    @Test
    void yediGunSonraKalemYok_sekizGunOnceki_gorunmez_altiGunOnceki_gorunur() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        Tarayici t = makinistGirisli(m, "D001");
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, neden_etiketi, zaman) VALUES (?, ?, 'KALDIRILDI', 'eski', 'IZIN', now() - interval '6 days')", m, sefer);
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, neden_etiketi, zaman) VALUES (?, ?, 'KALDIRILDI', 'cok eski', 'IZIN', now() - interval '8 days')", m, sefer);

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.kaldirilanlar.length()").value(1)).andExpect(jsonPath("$.kaldirilanlar[0].ozet").value("eski"));
    }

    /** MK-95: `gordum.kalem_gun` ayardır; 7 -> 10 sekiz günlük kalemi gösterir, 7 -> 5 altı günlük kalemi gizler. */
    @Test
    void kalemGunu_ayardan_okunur() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        Tarayici t = makinistGirisli(m, "D001");
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, neden_etiketi, zaman) VALUES (?, ?, 'KALDIRILDI', 'alti', 'IZIN', now() - interval '6 days')", m, sefer);
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, neden_etiketi, zaman) VALUES (?, ?, 'KALDIRILDI', 'sekiz', 'IZIN', now() - interval '8 days')", m, sefer);

        yonetici().put("/api/ayar/gordum.kalem_gun", "{\"deger\":10,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.kaldirilanlar.length()").value(2));
        yonetici().put("/api/ayar/gordum.kalem_gun", "{\"deger\":5,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.kaldirilanlar.length()").value(0));
    }

    @Test
    void yabanciKalem404_otomatikAtamaVeIptalOlayUretmez() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, neden_etiketi, zaman) VALUES (?, ?, 'KALDIRILDI', 'baskasi', 'IZIN', now())", baska, sefer);
        long olay = jdbc.queryForObject("SELECT id FROM atama_olayi", Long.class);
        Tarayici t = makinistGirisli(m, "D001");

        t.post("/api/benim/olaylar/" + olay + "/gordum", "").andExpect(status().isNotFound());
        t.post("/api/benim/olaylar/99999/gordum", "").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT kapatildi_zaman FROM atama_olayi WHERE id = ?", java.sql.Timestamp.class, olay)).isNull();

        // yeni atama ve otomatik atama kalem üretmez
        Tarayici a = atamaci();
        a.post("/api/atama/sefer/" + sefer, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        a.post("/api/atama/otomatik", "{\"bas\":\"" + BUGUN.plusDays(2) + "\",\"bit\":\"" + BUGUN.plusDays(2) + "\",\"uygula\":true}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama_olayi", Integer.class)).isEqualTo(1);
    }
}
