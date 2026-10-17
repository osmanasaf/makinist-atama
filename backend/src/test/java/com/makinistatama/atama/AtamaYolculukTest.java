package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F2b MK-38 / FR-01 / FR-11: YOLCU atama ve kaldırma uçları, çoklu yolcu, yük treni kuralı, iptal sefer, "BOŞ" durumu değişmez, yetki sorulmaz. */
class AtamaYolculukTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long e1;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        e1 = v.sefer("E1", GUN, "08:00", "09:30"); // Ankara -> Eskişehir
        t = atamaci();
    }

    private static String yolcu(long makinistId) {
        return "{\"makinistId\":" + makinistId + ",\"rol\":\"YOLCU\"}";
    }

    @Test
    void yolcuEkle_yetkisizMakinistle201_seferBosKalir_denetimEtiketiYolcu() throws Exception {
        long yetkisiz = v.makinist("D009", false, "AKTIF"); // hat yetkisi yok: ASIL olamazdı

        t.post("/api/atama/sefer/" + e1, yolcu(yetkisiz)).andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("YOLCU"))
                .andExpect(jsonPath("$.sicil").value("D009"));

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].rol").value("YOLCU"))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Integer.class, e1)).isZero(); // "BOŞ" kalır
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class)).isEqualTo("YOLCU:D009");
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class)).contains("\"yolculuk\":true");
    }

    @Test
    void ikinciYolcu201_ayniMakinistAsilOlarak409_YolcuIkinciKez409() throws Exception {
        long a = v.makinist("D001");
        long b = v.makinist("D002");

        t.post("/api/atama/sefer/" + e1, yolcu(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + e1, yolcu(b)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + e1, AtamaVeri.ataGovdesi(a)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));
        t.post("/api/atama/sefer/" + e1, yolcu(a)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, e1)).isEqualTo(2);
    }

    @Test
    void yukTreni_ayarKapaliysa422_acikOlursa201_bosTurBinilebilir_iptalSefer409() throws Exception {
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long c = v.makinist("D003");
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", e1);

        t.post("/api/atama/sefer/" + e1, yolcu(a)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("YOLCU_SEFER_UYGUN_DEGIL"));
        // review W: aday listesi de aynı denetimi yapar (ata ile aynı sonuç)
        t.get("/api/atama/sefer/" + e1 + "/adaylar?rol=YOLCU").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("YOLCU_SEFER_UYGUN_DEGIL"));
        t.get("/api/atama/sefer/" + e1 + "/adaylar?rol=ASIL").andExpect(status().isOk());
        yonetici().put("/api/ayar/yolculuk.yuk_trenine_binilir", "{\"deger\":1,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + e1, yolcu(a)).andExpect(status().isCreated());

        jdbc.update("UPDATE sefer SET tren_turu = NULL WHERE id = ?", e1); // boş tür binilebilir
        yonetici().put("/api/ayar/yolculuk.yuk_trenine_binilir", "{\"deger\":0,\"neden\":\"geri\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + e1, yolcu(b)).andExpect(status().isCreated());

        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", e1);
        t.post("/api/atama/sefer/" + e1, yolcu(c)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_IPTAL"));
    }

    @Test
    void kaldir_makinistIdZorunlu_digerYolcuEtkilenmez_kayitDuser() throws Exception {
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        t.post("/api/atama/sefer/" + e1, yolcu(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + e1, yolcu(b)).andExpect(status().isCreated());

        t.delete("/api/atama/sefer/" + e1 + "?rol=YOLCU&neden=IZIN").andExpect(status().isBadRequest());
        t.delete("/api/atama/sefer/" + e1 + "?rol=YOLCU&makinistId=999&neden=IZIN").andExpect(status().isNotFound());
        t.delete("/api/atama/sefer/" + e1 + "?rol=YOLCU&makinistId=" + a + "&neden=IZIN").andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT makinist_id FROM atama WHERE sefer_id = ?", Long.class, e1)).containsExactly(b);
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'ATAMA_KALDIR'", String.class)).isEqualTo("YOLCU:D001");
    }

    @Test
    void asilMakinistiYolcuSatiriOlarakKaldirilamaz() throws Exception {
        long a = v.makinist("D001");
        t.post("/api/atama/sefer/" + e1, AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());

        t.delete("/api/atama/sefer/" + e1 + "?rol=YOLCU&makinistId=" + a + "&neden=IZIN").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, e1)).isEqualTo(1);
    }

    @Test
    void cokluYolcuLuSeferde_asilAta_kaldir_gunListesi_uygunSeferler_adaylar_500Vermez() throws Exception {
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long asil = v.makinist("D003");
        t.post("/api/atama/sefer/" + e1, yolcu(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + e1, yolcu(b)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + e1, AtamaVeri.ataGovdesi(asil)).andExpect(status().isCreated());
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].makinistler", hasSize(3)))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].rol").value("ASIL")).andExpect(jsonPath("$.satirlar[0].makinistler[2].rol").value("YOLCU"));
        t.get("/api/atama/sefer/" + e1 + "/adaylar?rol=YOLCU").andExpect(status().isOk());
        t.get("/api/atama/sefer/" + e1 + "/adaylar").andExpect(status().isOk());
        t.get("/api/atama/makinist/" + v.makinist("D004") + "/uygun-seferler?gun=" + GUN).andExpect(status().isOk());
        t.delete("/api/atama/sefer/" + e1 + "?rol=ASIL&neden=IZIN").andExpect(status().isNoContent());
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].makinistler", hasSize(2)));
        t.get("/api/atama/sefer/" + e1 + "/oneri").andExpect(status().isOk());
    }

    @Test
    void adayListesi_rolYolcuIken_belgeVeYetkiSorulmaz_konumSeferinKalkisGarinaGore() throws Exception {
        long yetkisiz = v.makinist("D009", false, "AKTIF");
        v.makinist("D001");

        String asil = t.get("/api/atama/sefer/" + e1 + "/adaylar?rol=ASIL").andReturn().getResponse().getContentAsString();
        String yolcu = t.get("/api/atama/sefer/" + e1 + "/adaylar?rol=YOLCU").andReturn().getResponse().getContentAsString();

        assertThat(asil).contains("\"sicil\":\"D009\",\"adSoyad\":\"Demo Makinist D009\",\"bagliGar\":\"Ankara\",\"uygun\":false");
        assertThat(yolcu).contains("\"sicil\":\"D009\",\"adSoyad\":\"Demo Makinist D009\",\"bagliGar\":\"Ankara\",\"uygun\":true");
        assertThat(yetkisiz).isPositive();
    }

    @Test
    void yolcuOlarakKonumDisinda_K20UyarisiGerekceIster_gerekceyleYazilir() throws Exception {
        long a = v.makinist("D001"); // bağlı gar Ankara
        long eskisehirKalkis = v.sefer("S2", GUN, "10:30", "13:00", v.eskisehir, v.ankara);

        t.post("/api/atama/sefer/" + eskisehirKalkis, yolcu(a)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        t.post("/api/atama/sefer/" + eskisehirKalkis, "{\"makinistId\":" + a + ",\"rol\":\"YOLCU\",\"gerekce\":\"intikal\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.uyarilar[0].kod").value("K-20"));
    }
}
