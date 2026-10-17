package com.makinistatama.atama;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * F2b MK-41/FR-06/NFR-03: "Yolculukla ata" tek işlem: önce YOLCU, sonra ASIL (iki kayıt, bu sırayla); herhangi bir ayak reddedilirse hiçbir satır yazılmaz;
 * UYARI gerekçesi iki satıra; ikinci çağrı ZATEN_ATANMIS; eşzamanlı iki istek tek başarı.
 */
class YolculuklaAtaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long e1;
    long s2;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        e1 = v.sefer("E1", GUN, "08:00", "09:30"); // Ankara -> Eskişehir (yolculuk)
        s2 = v.sefer("S2", GUN, "10:30", "15:00", v.eskisehir, v.ankara); // Eskişehir -> Ankara (hedef)
        t = atamaci();
    }

    private String govde(long makinist, long yolculukSefer, String ek) {
        return "{\"makinistId\":" + makinist + ",\"yolculukSeferId\":" + yolculukSefer + (ek.isEmpty() ? "" : "," + ek) + "}";
    }

    private int satir() {
        return jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);
    }

    @Test
    void basari_201_ikiSatirIkiKayit_onceYolcuSonraAsil() throws Exception {
        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "")).andExpect(status().isCreated()).andExpect(jsonPath("$.yolcu.rol").value("YOLCU"))
                .andExpect(jsonPath("$.yolcu.seferId").value(e1)).andExpect(jsonPath("$.asil.rol").value("ASIL")).andExpect(jsonPath("$.asil.seferId").value(s2));

        assertThat(satir()).isEqualTo(2);
        List<String> kayitlar = jdbc.queryForList("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'ATAMA' ORDER BY id", String.class);
        assertThat(kayitlar).containsExactly("YOLCU:D001", "ASIL:D001");
        // yolculuk olmasaydı ASIL için K-20 UYARI çıkardı: şimdi uyarı yok
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE yeni = 'ASIL:D001'", String.class)).contains("\"sonuc\":\"UYGUN\"");
        // canlı hesap: kırmızı yok
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-14&bit=2026-10-14").andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void ikinciCagri_ZATEN_ATANMIS() throws Exception {
        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "")).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "")).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));
        assertThat(satir()).isEqualTo(2);
    }

    @Test
    void asilAyagiEngelli_yolcuSatiriDaYazilmaz() throws Exception {
        long yetkisiz = v.makinist("D009", false, "AKTIF"); // hat yetkisi yok: ASIL K-06 ENGEL, yolculuk serbest

        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(yetkisiz, e1, "")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"));

        assertThat(satir()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA'", Integer.class)).isZero();
    }

    @Test
    void yolcuAyagiEngelli_hicbiriYazilmaz() throws Exception {
        v.atama(v.sefer("CAKISAN", GUN, "08:30", "09:00"), m, "ASIL"); // yolculukla çakışır (K-10)
        int once = satir();

        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"));

        assertThat(satir()).isEqualTo(once);
    }

    @Test
    void uyariGerekcesiz400_gerekceyleIkiSatiraAyniGerekce() throws Exception {
        long eskisehirKalkis = v.sefer("E5", GUN, "07:00", "08:30", v.eskisehir, v.eskisehir); // makinist Ankara'da: yolculuk satırında K-20 UYARI

        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, eskisehirKalkis, "")).andExpect(status().isBadRequest());
        assertThat(satir()).isZero();

        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, eskisehirKalkis, "\"gerekce\":\"araç ile geldi\"")).andExpect(status().isCreated());
        assertThat(jdbc.queryForList("SELECT gerekce FROM atama ORDER BY id", String.class)).containsExactly("araç ile geldi", "araç ile geldi");
    }

    @Test
    void ayniSefer400_hedefAsilDoluysaNedenZorunlu_geriAlinir() throws Exception {
        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, s2, "")).andExpect(status().isBadRequest());

        long baska = v.makinist("D002");
        v.atama(s2, baska, "ASIL"); // konumu uyuşmasa da (K-20) SQL ile yazılır: yalnız "ASIL dolu" durumu kurulur
        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "")).andExpect(status().isBadRequest()); // değiştirme nedeni yok

        assertThat(satir()).isEqualTo(1); // yalnız D002'nin satırı: yolcu satırı geri alındı
        t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "\"neden\":\"HASTALIK\"")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s2)).isEqualTo(m);
    }

    @Test
    void eszamanliIkiIstek_birBasari_digeri409() throws Exception {
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier bariyer = new CyclicBarrier(2);
            List<Future<Integer>> sonuclar = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                sonuclar.add(havuz.submit(() -> {
                    bariyer.await();
                    return t.post("/api/atama/sefer/" + s2 + "/yolculukla", govde(m, e1, "")).andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> durumlar = new ArrayList<>();
            for (Future<Integer> f : sonuclar) {
                durumlar.add(f.get());
            }
            assertThat(durumlar).containsExactlyInAnyOrder(201, 409);
            assertThat(satir()).isEqualTo(2);
        } finally {
            havuz.shutdownNow();
        }
    }
}
