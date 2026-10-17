package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * Serbest intikal canlı durumu (MK-107, FR-04, OK-28): kırmızı/uyarı SAKLANMAZ, her okumada hesaplanır; iptal / saat düzeltmesi komşu atamaları yeni kırmızı yapabilir (yanıtta
 * {@code yeniKirmizi}); intikal kırmızısı atama kırmızı sayacına katılmaz ama {@code kirmiziIntikal} ile ayrı görünür; öksüz intikal uyarısı (eve dönüşte aranmaz).
 */
class IntikalCanliDurumTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);
    static final String OKSUZ = "Yolculuktan sonra bu garda görev yok";

    AtamaVeri v;
    Tarayici t;
    long m;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        t = atamaci();
        yonetici().put("/api/kural/K-20/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(status().isOk());
    }

    private String govde(long makinist, long kalkisGar, long varisGar, String kalkis, String varis, String ek) {
        return "{\"makinistId\":" + makinist + ",\"kalkisGarId\":" + kalkisGar + ",\"varisGarId\":" + varisGar + ",\"kalkis\":\"" + GUN + "T" + kalkis + ":00+03:00\",\"varis\":\"" + GUN + "T"
                + varis + ":00+03:00\",\"aracTuru\":\"SERVIS\"" + ek + "}";
    }

    /** Ankara -> Eskişehir intikali (07:00–09:00) ve ardından Eskişehir -> Ankara seferi (10:00–11:30): intikal olmadan sefer K-20 kırmızı olur. */
    private long intikalVeSefer() {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        long s = v.sefer("S1", GUN, "10:00", "11:30", v.eskisehir, v.ankara);
        v.atama(s, m, "ASIL");
        return s;
    }

    @Test
    void iptal_komsuSeferinKonumuGeriGelir_yeniKirmiziDoner() throws Exception {
        long s = intikalVeSefer();
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));

        t.delete("/api/intikal/1?neden=IZIN&versiyon=0").andExpect(status().isOk()).andExpect(jsonPath("$.intikal.durum").value("IPTAL"))
                .andExpect(jsonPath("$.yeniKirmizi", hasSize(1))).andExpect(jsonPath("$.yeniKirmizi[0].seferId").value(s)).andExpect(jsonPath("$.yeniKirmizi[0].trenNo").value("S1"))
                .andExpect(jsonPath("$.yeniKirmizi[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("Konum uyuşmazlığı")));

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true)); // atama kırmızı sayacına doğal olarak girer
    }

    @Test
    void saatDuzeltmesi_intikalSeferdenSonraya_seferKirmiziOlur() throws Exception {
        long s = intikalVeSefer();

        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, "12:00", "13:00", ",\"versiyon\":0")).andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(1)))
                .andExpect(jsonPath("$.yeniKirmizi[0].seferId").value(s));
    }

    @Test
    void saatDuzeltmesi_etkisizse_yeniKirmiziBos() throws Exception {
        intikalVeSefer();

        t.put("/api/intikal/1", govde(m, v.ankara, v.eskisehir, "06:30", "08:30", ",\"versiyon\":0")).andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(0)));
    }

    @Test
    void oncekiSeferGecikmesiyleIntikalKirmizi_K10_gecikmeKalkincaDuzelir() throws Exception {
        long p = v.sefer("P1", GUN, "07:00", "09:00"); // Ankara -> Eskişehir
        v.atama(p, m, "ASIL");
        v.intikal(m, v.eskisehir, v.ankara, GUN, "09:10", "10:30");
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].kirmizi").value(false));

        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 30, varis_gecikme_dk = 30 WHERE id = ?", p); // varış 09:30: intikal 09:10'da başlıyor

        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].kirmizi").value(true)).andExpect(jsonPath("$[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("Çakışma")));
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$.kirmiziIntikal").value(1));

        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 0, varis_gecikme_dk = 0 WHERE id = ?", p);
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$.kirmiziIntikal").value(0)); // saklanmaz: gecikme kalkınca kendiliğinden düzelir
    }

    @Test
    void kirmiziIntikal_atamaSayacinaKatilmaz_ayriSayilir_K11() throws Exception {
        long ev = v.seferEv("E1", GUN, "06:00", "08:00");
        v.atama(ev, m, "ASIL"); // evde biter
        v.intikal(m, v.ankara, v.eskisehir, GUN, "14:00", "15:30"); // ayrı nöbet, dinlenme 6 s < 11 s: intikal kırmızı (K-11), önceki sefer sorumlu değil

        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].kirmizi").value(true)).andExpect(jsonPath("$[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("Dinlenme")));
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$.kirmiziAtama").value(0)).andExpect(jsonPath("$.kirmiziSefer").value(0))
                .andExpect(jsonPath("$.kirmiziIntikal").value(1));
    }

    @Test
    void oksuzIntikal_uyariYalnizBaglıGarDisiVarista_eveDonuseVeSonrakiSefereAranmaz() throws Exception {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].uyarilar", hasItem(OKSUZ))).andExpect(jsonPath("$[0].kirmizi").value(false));

        long s = v.sefer("S1", GUN, "10:00", "11:30", v.eskisehir, v.ankara); // varış garından kalkan sürüş var
        v.atama(s, m, "ASIL");
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].uyarilar", not(hasItem(OKSUZ))));

        long m2 = v.makinist("D002");
        v.intikal(m2, v.eskisehir, v.ankara, GUN, "07:00", "09:00"); // eve dönüş: varış = bağlı gar
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN + "&makinistId=" + m2).andExpect(jsonPath("$[0].uyarilar", not(hasItem(OKSUZ))));
    }

    @Test
    void iptalEdilenIntikalinDurumuYok_vePlanliKirmiziDegilse_sayacSifir() throws Exception {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        t.delete("/api/intikal/1?neden=IZIN&versiyon=0").andExpect(status().isOk());

        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].durum").value("IPTAL")).andExpect(jsonPath("$[0].kirmizi").value(false))
                .andExpect(jsonPath("$[0].uyarilar", hasSize(0)));
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$.kirmiziIntikal").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM intikal WHERE durum = 'IPTAL'", Integer.class)).isEqualTo(1);
    }

    /** Review W2: atama kaldırılınca konum geri gelir ve AYNI makinistin intikali kırmızı olur; yanıt (204 değil, 200) `yeniKirmiziIntikal` ile bildirir. */
    @Test
    void atamaKaldirma_intikaliKirmiziYaparsa_yeniKirmiziIntikalDoner() throws Exception {
        long p = v.sefer("P1", GUN, "07:00", "09:00"); // Ankara -> Eskişehir
        v.atama(p, m, "ASIL");
        v.intikal(m, v.eskisehir, v.ankara, GUN, "10:00", "11:30"); // konum: Eskişehir (K-20 yeşil)
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].kirmizi").value(false));

        t.delete("/api/atama/sefer/" + p + "?rol=ASIL&neden=IZIN").andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(0)))
                .andExpect(jsonPath("$.yeniKirmiziIntikal", hasSize(1))).andExpect(jsonPath("$.yeniKirmiziIntikal[0].intikalId").value(1))
                .andExpect(jsonPath("$.yeniKirmiziIntikal[0].ozet").value(org.hamcrest.Matchers.containsString("10:00 → Ankara")))
                .andExpect(jsonPath("$.yeniKirmiziIntikal[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("Konum uyuşmazlığı")));
        t.get("/api/intikal?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$[0].kirmizi").value(true));
    }

    /** İntikalle ilgisiz kaldırma 204 kalır. */
    @Test
    void atamaKaldirma_intikalEtkilenmezse_204() throws Exception {
        long p = v.sefer("P1", GUN, "07:00", "09:00");
        v.atama(p, m, "ASIL");
        v.intikal(m, v.ankara, v.eskisehir, GUN, "04:00", "05:30"); // seferden ÖNCE: kaldırma onu etkilemez
        t.delete("/api/atama/sefer/" + p + "?rol=ASIL&neden=IZIN").andExpect(status().isNoContent());
    }
}
