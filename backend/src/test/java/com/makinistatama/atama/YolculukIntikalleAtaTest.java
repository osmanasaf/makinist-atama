package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * "İntikalle ata" (MK-110, FR-07): tek işlemde serbest intikal + ASIL; süre ve saat SUNUCUDA öneriyle aynı hesaptan (istemci saat göndermez); biri reddedilirse hiçbir satır yazılmaz
 * (hata gövdesinde {@code adim}); öneri yoksa 422 {@code INTIKAL_ONERISI_YOK}.
 */
class YolculukIntikalleAtaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long hedef;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        hedef = v.sefer("H", GUN, "09:00", "10:30", v.eskisehir, v.ankara); // Eskişehir -> Ankara
        t = atamaci();
        yonetici().put("/api/tanimlar/intikal-sure", "{\"garAId\":" + v.ankara + ",\"garBId\":" + v.eskisehir + ",\"dakika\":90}").andExpect(status().isOk());
    }

    private static String govde(long makinist, String ek) {
        return "{\"makinistId\":" + makinist + ",\"aracTuru\":\"SERVIS\"" + ek + "}";
    }

    @Test
    void basarili_201_intikalVeAsilYazilir_saatSunucudan_kirmiziYok() throws Exception {
        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, ",\"notu\":\"kapı 3\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:15:00+03:00")).andExpect(jsonPath("$.intikal.varis").value("2026-10-14T08:45:00+03:00"))
                .andExpect(jsonPath("$.intikal.kalkisGar").value("Ankara")).andExpect(jsonPath("$.intikal.varisGar").value("Eskişehir")).andExpect(jsonPath("$.intikal.notu").value("kapı 3"))
                .andExpect(jsonPath("$.atama.rol").value("ASIL")).andExpect(jsonPath("$.atama.sicil").value("D001"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Integer.class, hedef)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA'", Integer.class)).isEqualTo(1);
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$.kirmiziAtama").value(0)).andExpect(jsonPath("$.kirmiziIntikal").value(0));
    }

    @Test
    void istemciSaatiGonderse_yoksayilir_sunucuHesabiGecerli() throws Exception {
        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, ",\"kalkis\":\"2026-10-14T01:00:00+03:00\",\"varis\":\"2026-10-14T02:00:00+03:00\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:15:00+03:00"));
    }

    @Test
    void oneriYoksa422_hicbirSatirYazilmaz() throws Exception {
        jdbc.update("DELETE FROM gar_intikal_suresi"); // süre tanımsız

        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, "")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("INTIKAL_ONERISI_YOK"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }

    @Test
    void asilZatenDolu_nedenYoksaAtamaAdimi400_intikalDeGeriAlinir_nedenleOlursa201() throws Exception {
        long baska = v.makinist("D002");
        v.atama(hedef, baska, "ASIL");

        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.adim").value("ATAMA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).as("atama adımı reddedildi: intikal de yazılmadı").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", Integer.class)).isZero();

        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, ",\"neden\":\"HASTALIK\"")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, hedef)).isEqualTo(m);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isEqualTo(1);
    }

    @Test
    void kuralEngeli_izinGunu_oneriYoksaBicimindeReddedilir() throws Exception {
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, ?, ?, 'YILLIK', 'ONAY', 'test')", m, GUN, GUN);

        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, "")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("INTIKAL_ONERISI_YOK"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isZero();
    }

    @Test
    void dogrulamaVeYetki_iptalSefer409_makinistYok404_makinist403() throws Exception {
        t.post("/api/atama/sefer/" + hedef + "/intikalle", "{\"makinistId\":" + m + "}").andExpect(status().isBadRequest());
        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(999, "")).andExpect(status().isNotFound());
        t.post("/api/atama/sefer/9999/intikalle", govde(m, "")).andExpect(status().isNotFound());
        makinistGirisli(m, "D001").post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, "")).andExpect(status().isForbidden());
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", hedef);
        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, "")).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_IPTAL"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isZero();
    }

    @Test
    void uyariVarsa_gerekceIster_birlikteGecer() throws Exception {
        // intikal öncesi makinistin sonraki günü görevi Eskişehir'den kalkıyorsa hedefin varışıyla uyuşmaz (K-20 UYARI): gerekçe zorunlu
        long sonraki = v.sefer("N", GUN.plusDays(1), "08:00", "09:30", v.eskisehir, v.ankara);
        v.atama(sonraki, m, "ASIL");
        t.get("/api/atama/sefer/" + hedef + "/yolculuk-oner?makinistId=" + m).andExpect(jsonPath("$.intikal.uyarilar[*].kod", hasItem("K-20")));

        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class)).isZero();
        t.post("/api/atama/sefer/" + hedef + "/intikalle", govde(m, ",\"gerekce\":\"bilinen aktarma\"")).andExpect(status().isCreated());
    }
}
