package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
 * Serbest intikal yazma uçları (MK-106, FR-01): ekleme / düzeltme / iptal; alan doğrulaması 400, ENGEL 422 (satır kalmaz), UYARI gerekçe, sürüm 409, silme yok, denetim kaydı, yetki,
 * aynı makinistte eşzamanlı örtüşen iki intikal.
 */
class IntikalYazmaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        t = atamaci();
    }

    private String govde(long makinist, long kalkisGar, long varisGar, String kalkis, String varis, String ek) {
        return "{\"makinistId\":" + makinist + ",\"kalkisGarId\":" + kalkisGar + ",\"varisGarId\":" + varisGar + ",\"kalkis\":\"" + kalkis + "\",\"varis\":\"" + varis
                + "\",\"aracTuru\":\"SERVIS\"" + ek + "}";
    }

    private String govde(String kalkisSaat, String varisSaat) {
        return govde(m, v.ankara, v.eskisehir, GUN + "T" + kalkisSaat + ":00+03:00", GUN + "T" + varisSaat + ":00+03:00", "");
    }

    private int intikalSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class);
    }

    @Test
    void ekle_201_dtoVeDenetimKaydi() throws Exception {
        t.post("/api/intikal", govde(m, v.ankara, v.eskisehir, GUN + "T07:00:00+03:00", GUN + "T09:00:00+03:00", ",\"notu\":\"  servis kapısı  \"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.intikal.id").value(1)).andExpect(jsonPath("$.intikal.sicil").value("D001")).andExpect(jsonPath("$.intikal.kalkisGar").value("Ankara"))
                .andExpect(jsonPath("$.intikal.varisGar").value("Eskişehir")).andExpect(jsonPath("$.intikal.durum").value("PLANLI")).andExpect(jsonPath("$.intikal.versiyon").value(0))
                .andExpect(jsonPath("$.intikal.notu").value("servis kapısı")).andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:00:00+03:00"))
                .andExpect(jsonPath("$.intikal.kirmizi").value(false)).andExpect(jsonPath("$.yeniKirmizi", hasSize(0)));

        assertThat(jdbc.queryForObject("SELECT kullanici_adi FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", String.class)).isEqualTo("Ata Maci");
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", String.class)).isEqualTo("İntikal · Ankara 14.10 07:00 → Eskişehir 14.10 09:00 · Servis");
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", String.class)).contains("\"yolculuk\":true");
        assertThat(jdbc.queryForObject("SELECT varlik_tipi FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", String.class)).isEqualTo("INTIKAL");
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].adSoyad").value("Demo Makinist D001"));
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN + "&makinistId=999").andExpect(jsonPath("$", hasSize(0)));
        t.get("/api/intikal?bas=" + GUN.plusDays(1) + "&bit=" + GUN.plusDays(1)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dogrulama400_ayniGarVarisKalkistanOnceYirmiDortSaatNotAracGarDegil() throws Exception {
        String k = GUN + "T07:00:00+03:00";
        t.post("/api/intikal", govde(m, v.ankara, v.ankara, k, GUN + "T09:00:00+03:00", "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.varisGarId").exists());
        t.post("/api/intikal", govde("09:00", "09:00")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.varis").exists());
        t.post("/api/intikal", govde("09:00", "08:00")).andExpect(status().isBadRequest());
        t.post("/api/intikal", govde(m, v.ankara, v.eskisehir, k, GUN.plusDays(1) + "T07:01:00+03:00", "")).andExpect(status().isBadRequest()) // 24 s 1 dk
                .andExpect(jsonPath("$.alanlar.varis").exists());
        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk()); // 24 saatlik intikal K-13 süre kuralına takılmasın
        t.post("/api/intikal", govde(m, v.ankara, v.eskisehir, k, GUN.plusDays(1) + "T07:00:00+03:00", "")).andExpect(status().isCreated()); // tam 24 saat serbest
        t.post("/api/intikal", govde(m, v.hat, v.eskisehir, k, GUN + "T09:00:00+03:00", "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.kalkisGarId").exists());
        t.post("/api/intikal", govde(m, v.ankara, v.eskisehir, "1999-01-01T07:00:00+03:00", "1999-01-01T09:00:00+03:00", "")).andExpect(status().isBadRequest());
        t.post("/api/intikal", govde(m, v.ankara, v.eskisehir, k, GUN + "T09:00:00+03:00", ",\"notu\":\"" + "x".repeat(201) + "\"")).andExpect(status().isBadRequest());
        t.post("/api/intikal", "{\"makinistId\":" + m + ",\"kalkisGarId\":" + v.ankara + ",\"varisGarId\":" + v.eskisehir + ",\"kalkis\":\"" + k + "\",\"varis\":\"" + GUN
                + "T09:00:00+03:00\",\"aracTuru\":\"ROKET\"}").andExpect(status().isBadRequest());
        t.post("/api/intikal", govde(999, v.ankara, v.eskisehir, k, GUN + "T09:00:00+03:00", "")).andExpect(status().isNotFound());
        assertThat(intikalSayisi()).isEqualTo(1); // yalnız tam 24 saatlik
    }

    @Test
    void engel_422_satirKalmaz_izinK09_ortusmeK10() throws Exception {
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, ?, ?, 'YILLIK', 'ONAY', 'test')", m, GUN, GUN);
        t.post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-09")));
        assertThat(intikalSayisi()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", Integer.class)).isZero();

        jdbc.update("DELETE FROM izin");
        t.post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isCreated());
        t.post("/api/intikal", govde("08:00", "10:00")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-10")));
        assertThat(intikalSayisi()).isEqualTo(1);
    }

    @Test
    void pasifMakinist_K01_422() throws Exception {
        long pasif = v.makinist("D002", true, "PASIF");
        t.post("/api/intikal", govde(pasif, v.ankara, v.eskisehir, GUN + "T07:00:00+03:00", GUN + "T09:00:00+03:00", "")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-01")));
        assertThat(intikalSayisi()).isZero();
    }

    @Test
    void uyari_gerekceZorunlu_k20Varsayilan_gerekceAnlikGoruntude() throws Exception {
        // makinist Ankara'da; Eskişehir'den kalkan intikal: K-20 (varsayılan UYARI)
        String ek = govde(m, v.eskisehir, v.ankara, GUN + "T07:00:00+03:00", GUN + "T09:00:00+03:00", "");

        t.post("/api/intikal", ek).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        assertThat(intikalSayisi()).isZero();
        t.post("/api/intikal", govde(m, v.eskisehir, v.ankara, GUN + "T07:00:00+03:00", GUN + "T09:00:00+03:00", ",\"gerekce\":\"servis eve getirdi\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.intikal.uyarilar", hasSize(1)));
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'INTIKAL_EKLE'", String.class)).contains("servis eve getirdi").contains("K-20");
    }

    @Test
    void gecmisTarihli_K22Uyari_gerekceIster() throws Exception {
        String gecmis = govde(m, v.ankara, v.eskisehir, "2026-10-08T07:00:00+03:00", "2026-10-08T09:00:00+03:00", "");
        t.post("/api/intikal", gecmis).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        t.post("/api/intikal", govde(m, v.ankara, v.eskisehir, "2026-10-08T07:00:00+03:00", "2026-10-08T09:00:00+03:00", ",\"gerekce\":\"geçmiş kayıt düzeltmesi\"")).andExpect(status().isCreated());
    }

    @Test
    void guncelle_surumZorunlu_bayat409_makinistDegismez_basarili200() throws Exception {
        t.post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isCreated());
        String yeni = govde(m, v.ankara, v.eskisehir, GUN + "T08:00:00+03:00", GUN + "T10:30:00+03:00", "");

        t.put("/api/intikal/1", yeni).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.versiyon").exists());
        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, GUN + "T08:00:00+03:00", GUN + "T10:30:00+03:00", ",\"versiyon\":7")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        long baska = v.makinist("D002");
        t.put("/api/intikal/1", govde(baska, v.ankara, v.eskisehir, GUN + "T08:00:00+03:00", GUN + "T10:30:00+03:00", ",\"versiyon\":0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.makinistId").exists());
        t.put("/api/intikal/99", govde(m, v.ankara, v.eskisehir, GUN + "T08:00:00+03:00", GUN + "T10:30:00+03:00", ",\"versiyon\":0")).andExpect(status().isNotFound());

        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, GUN + "T08:00:00+03:00", GUN + "T10:30:00+03:00", ",\"versiyon\":0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T08:00:00+03:00")).andExpect(jsonPath("$.intikal.versiyon").value(1));
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'INTIKAL_GUNCELLE'", String.class)).contains("07:00");
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'INTIKAL_GUNCELLE'", String.class)).contains("08:00 → Eskişehir 14.10 10:30");
        // kendi eski haliyle örtüşme bir ihlal değildir (kendisi komşulardan hariç)
        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, GUN + "T08:30:00+03:00", GUN + "T10:30:00+03:00", ",\"versiyon\":1")).andExpect(status().isOk());
    }

    @Test
    void guncelle_engel422_satirEskiHaliyleKalir() throws Exception {
        t.post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isCreated());
        long s = v.sefer("S1", GUN, "11:00", "12:00", v.eskisehir, v.ankara);
        v.atama(s, m, "ASIL");

        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, GUN + "T07:00:00+03:00", GUN + "T11:30:00+03:00", ",\"versiyon\":0")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-10")));

        assertThat(jdbc.queryForObject("SELECT to_char(varis AT TIME ZONE 'Europe/Istanbul', 'HH24:MI') FROM intikal WHERE id = 1", String.class)).isEqualTo("09:00");
        assertThat(jdbc.queryForObject("SELECT versiyon FROM intikal WHERE id = 1", Long.class)).isZero();
    }

    @Test
    void iptal_silmeYok_nedenZorunlu_ikinciIptal409_duzeltilemez409() throws Exception {
        t.post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isCreated());

        t.delete("/api/intikal/1?versiyon=0").andExpect(status().isBadRequest());
        t.delete("/api/intikal/1?neden=DIGER&versiyon=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.nedenNotu").exists());
        t.delete("/api/intikal/1?neden=IZIN").andExpect(status().isBadRequest());
        t.delete("/api/intikal/1?neden=IZIN&versiyon=5").andExpect(status().isConflict());
        t.delete("/api/intikal/99?neden=IZIN&versiyon=0").andExpect(status().isNotFound());
        t.delete("/api/intikal/1?neden=DIGER&nedenNotu=servis_iptal&versiyon=0").andExpect(status().isOk()).andExpect(jsonPath("$.intikal.durum").value("IPTAL"))
                .andExpect(jsonPath("$.intikal.kirmizi").value(false)).andExpect(jsonPath("$.yeniKirmizi", hasSize(0)));

        assertThat(intikalSayisi()).as("silme yok: satır kalır").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT neden FROM degisiklik_kaydi WHERE tur = 'INTIKAL_IPTAL'", String.class)).isEqualTo("DIGER");
        assertThat(jdbc.queryForObject("SELECT neden_notu FROM degisiklik_kaydi WHERE tur = 'INTIKAL_IPTAL'", String.class)).isEqualTo("servis_iptal");
        t.delete("/api/intikal/1?neden=IZIN&versiyon=1").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("INTIKAL_ZATEN_IPTAL"));
        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, GUN + "T08:00:00+03:00", GUN + "T10:30:00+03:00", ",\"versiyon\":1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("INTIKAL_IPTAL"));
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].durum").value("IPTAL"));
        // iptal satırı kuralda görünmez: aynı saate yeni intikal örtüşme vermez
        t.post("/api/intikal", govde("07:30", "09:30")).andExpect(status().isCreated());
    }

    @Test
    void yetki_makinist403_anonim401_yoneticiVeAtamaci200() throws Exception {
        Tarayici makinist = makinistGirisli(m, "D001");
        makinist.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(status().isForbidden());
        makinist.post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isForbidden());
        new Tarayici(mvc).get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(status().isUnauthorized());
        yonetici().post("/api/intikal", govde("07:00", "09:00")).andExpect(status().isCreated());
        assertThat(intikalSayisi()).isEqualTo(1);
    }

    @Test
    void esZamanliOrtusenIkiIntikal_birKabul_birKuralIhlali() throws Exception {
        Tarayici t1 = atamaci();
        Tarayici t2 = atamaci();
        CyclicBarrier baslangic = new CyclicBarrier(2);
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> sonuc = new ArrayList<>();
            for (Tarayici ta : List.of(t1, t2)) {
                sonuc.add(havuz.submit(() -> {
                    baslangic.await();
                    return ta.post("/api/intikal", govde("07:00", "09:00")).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(List.of(sonuc.get(0).get(), sonuc.get(1).get())).containsExactlyInAnyOrder(201, 422);
        } finally {
            havuz.shutdownNow();
        }
        assertThat(intikalSayisi()).isEqualTo(1);
    }
}
