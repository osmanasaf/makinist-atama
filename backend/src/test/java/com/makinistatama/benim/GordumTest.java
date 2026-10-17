package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** FR-M08: "gördüm" onayı (tebliğ kanıtı): idempotent, değişince sıfırlanır, yabancı/ufuk dışı 404, çift basış 1 satır. */
class GordumTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    private int satir() {
        return jdbc.queryForObject("SELECT count(*) FROM gorev_gordum", Integer.class);
    }

    private int kayit() {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM'", Integer.class);
    }

    @Test
    void ilkBasis_birSatirBirKayit_ikinciBasis_idempotent() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("YENI")).andExpect(jsonPath("$.gorevler[0].gorduZamani").doesNotExist());

        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU")).andExpect(jsonPath("$.gorduZamani").exists());

        assertThat(satir()).isEqualTo(1);
        assertThat(kayit()).isEqualTo(1);
        var k = jdbc.queryForMap("SELECT kullanici_adi, makinist_id, sefer_id, varlik_id FROM degisiklik_kaydi WHERE tur = 'GORDUM'");
        assertThat(k).containsEntry("kullanici_adi", "Demo Makinist D001").containsEntry("makinist_id", m).containsEntry("sefer_id", sefer).containsEntry("varlik_id", atama);

        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        assertThat(satir()).as("ikinci basış satır eklemez").isEqualTo(1);
        assertThat(kayit()).as("ikinci basış kayıt eklemez").isEqualTo(1);
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU"));
    }

    @Test
    void baskasinaDegisince_yeniMakinistIcinGorulmedi_eskiOnayGecmez() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D017");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long atama = v.atama(sefer, m1, "ASIL");
        Tarayici t1 = makinistGirisli(m1, "D001");
        Tarayici t2 = makinistGirisli(m2, "D017");
        GordumYardimci.gordum(t1, atama).andExpect(status().isOk());

        atamaci().post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + m2 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk());

        t2.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(1)).andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
        t1.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(0));
        // eski atama kimliği artık yok: D001 için 404; D017 yeni atamayı onaylar, D001'in satırı D017 için geçerli sayılmaz
        GordumYardimci.gordum(t1, atama).andExpect(status().isNotFound());
        long yeniAtama = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sefer);
        GordumYardimci.gordum(t2, yeniAtama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM gorev_gordum WHERE atama_id = ?", Long.class, yeniAtama)).isEqualTo(m2);
    }

    @Test
    void gecikme_onayiSifirlar_yenidenGordum_satirTek_kayitIki() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:10", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        GordumYardimci.gordum(t, atama).andExpect(status().isOk());

        atamaci().post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":25,\"varisGecikmeDk\":25,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI"))
                .andExpect(jsonPath("$.gorevler[0].eskiOzet").value(org.hamcrest.Matchers.containsString("08:10")))
                .andExpect(jsonPath("$.gorevler[0].ozet").value(org.hamcrest.Matchers.containsString("08:35")));
        GordumYardimci.gordum(t, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU")).andExpect(jsonPath("$.eskiOzet").doesNotExist());
        assertThat(satir()).isEqualTo(1);
        assertThat(kayit()).isEqualTo(2);
    }

    @Test
    void trenNoVeKmDegisimi_onayiSifirlamaz_iptalSifirlar() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sefer = v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00");
        long atama = v.atama(sefer, m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        GordumYardimci.gordum(t, atama).andExpect(status().isOk());

        jdbc.update("UPDATE sefer SET tren_no = 'X999', km = 999 WHERE id = ?", sefer);
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU"));

        atamaci().post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        t.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI")).andExpect(jsonPath("$.gorevler[0].ozet").value(org.hamcrest.Matchers.containsString("İPTAL")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama_olayi", Integer.class)).as("iptal sefer olay üretmez (canlı İPTAL etiketi)").isZero();
    }

    @Test
    void yabanciUfukDisiVeOlmayanAtama_404_satirYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        long yabanci = v.atama(v.sefer("TB", BUGUN.plusDays(1), "08:00", "10:00"), baska, "ASIL");
        long ufukDisi = v.atama(v.sefer("TU", BUGUN.plusDays(7), "08:00", "10:00"), m, "ASIL"); // ufuk 7 gün: bugün..bugün+6
        Tarayici t = makinistGirisli(m, "D001");

        GordumYardimci.gordum(t, yabanci).andExpect(status().isNotFound());
        GordumYardimci.gordum(t, ufukDisi).andExpect(status().isNotFound());
        GordumYardimci.gordum(t, 987654).andExpect(status().isNotFound());
        assertThat(satir()).isZero();
        assertThat(kayit()).isZero();
    }

    @Test
    void eszamanliCiftBasis_birSatir_birKayit() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long atama = v.atama(v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00"), m, "ASIL");
        Tarayici t1 = makinistGirisli(m, "D001");
        Tarayici t2 = Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA);

        CyclicBarrier baslangic = new CyclicBarrier(2);
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> sonuclar = new ArrayList<>();
            for (Tarayici t : new Tarayici[] { t1, t2 }) {
                sonuclar.add(havuz.submit(() -> {
                    baslangic.await();
                    return GordumYardimci.gordum(t, atama).andReturn().getResponse().getStatus();
                }));
            }
            for (Future<Integer> f : sonuclar) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            havuz.shutdownNow();
        }
        assertThat(satir()).isEqualTo(1);
        assertThat(kayit()).isEqualTo(1);
    }

    @Test
    void askidaHesap_gordumYazamaz_okuyabilir() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long atama = v.atama(v.sefer("T1", BUGUN.plusDays(1), "08:00", "10:00"), m, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", m);

        GordumYardimci.gordum(t, atama).andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("HESAP_ASKIDA"));
        t.get("/api/benim/gorevler").andExpect(status().isOk());
        assertThat(satir()).isZero();
    }
}
