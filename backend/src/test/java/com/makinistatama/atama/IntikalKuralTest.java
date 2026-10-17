package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * Serbest intikal (MK-105, FR-02/FR-03/FR-08): DB'ye yazılmış bir intikal, F2b yolculuğuyla AYNI görev olarak kural motoruna girer; yeni kural kodu yok. K-20 konum (iki yönlü),
 * aktarma 5 dk ihlalsiz (K-23 yolculuk muaf), örtüşme K-10, K-13 sınır 660/661 dk (yolculuk.calisma_sayilir), K-11 dinlenme sınırı, otomatik atamanın "konum" olarak kullanması.
 */
class IntikalKuralTest extends TestVeritabani {

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

    /** K-20 varsayılanı UYARI; konum uyuşmazlığının ENGEL (kırmızı) olduğu işletme ayarı. */
    private void k20Engel() throws Exception {
        yonetici().put("/api/kural/K-20/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(status().isOk());
    }

    private long donusSeferi(String kalkis, String varis) {
        return v.sefer("S1", GUN, kalkis, varis, v.eskisehir, v.ankara); // Eskişehir -> Ankara
    }

    @Test
    void k20_intikalOlmadanKonumUyusmaz422_intikalVarsaKalkar201() throws Exception {
        k20Engel();
        long s = donusSeferi("10:00", "11:30");

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-20")));

        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00"); // Ankara -> Eskişehir (servis)
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()).andExpect(jsonPath("$.uyarilar", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, s)).isEqualTo(1);
    }

    @Test
    void k20_varsayilanUyari_gerekceIster_intikalVarsaUyariYok() throws Exception {
        long s = donusSeferi("10:00", "11:30");

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists()); // K-20 UYARI: gerekçe zorunlu

        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()).andExpect(jsonPath("$.uyarilar", hasSize(0)));
    }

    @Test
    void k20_tersiSira_atamaVarkenIntikalEklenince_kirmiziKalkar() throws Exception {
        k20Engel();
        long s = donusSeferi("10:00", "11:30");
        v.atama(s, m, "ASIL"); // SQL: kural kontrolsüz; Ankara'dan Eskişehir'e konum uyuşmazlığı

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true));
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void aktarma_besDakika_ihlalsiz_yolculukMuaf() throws Exception {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        long s = donusSeferi("09:05", "10:30"); // intikal varışından 5 dk sonra kalkış: K-23 yolculuk geçişini aramaz

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()).andExpect(jsonPath("$.uyarilar", hasSize(0)));
    }

    @Test
    void ortusme_K10() throws Exception {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        long s = donusSeferi("08:55", "10:30"); // intikal bitmeden kalkıyor

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-10")));
    }

    @Test
    void k13_gunlukSinir_660Gecer_661Engel_calismaSayilmazsaGecer() throws Exception {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "06:00", "09:00"); // 180 dk (yolculuk çalışma sayılır)
        long sinir = donusSeferi("09:00", "17:00"); // 480 dk -> toplam 660
        t.post("/api/atama/sefer/" + sinir, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        jdbc.update("DELETE FROM atama");

        jdbc.update("UPDATE sefer SET planlanan_varis = planlanan_varis + interval '1 minute' WHERE id = ?", sinir); // 661 dk
        t.post("/api/atama/sefer/" + sinir, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-13")));

        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk()); // intikal çalışma sayılmaz: 481 dk
        t.post("/api/atama/sefer/" + sinir, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
    }

    @Test
    void k11_dinlenmeSiniri_intikalNobetinBaslangicidir() throws Exception {
        long ev = v.seferEv("E1", GUN, "06:00", "08:00");
        v.atama(ev, m, "ASIL"); // nöbet 06:00–08:00, evde biter

        t.post("/api/intikal", intikal(m, v.ankara, v.eskisehir, "18:59", "19:50")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-11"))); // 10 s 59 dk
        t.post("/api/intikal", intikal(m, v.ankara, v.eskisehir, "19:00", "19:50")).andExpect(status().isCreated()); // tam 11 s
    }

    @Test
    void otomatikAtama_intikalVarisiniKonumSayar() throws Exception {
        long s = donusSeferi("10:00", "11:30");
        String govde = "{\"bas\":\"" + GUN + "\",\"bit\":\"" + GUN + "\",\"uygula\":true}";

        t.post("/api/atama/otomatik", govde).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, s)).as("konum yok: atanamaz").isZero();

        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        t.post("/api/atama/otomatik", govde).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s)).isEqualTo(m);
    }

    @Test
    void iptalIntikalKuralMotoruna_girmez() throws Exception {
        k20Engel();
        long s = donusSeferi("10:00", "11:30");
        long i = v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        jdbc.update("UPDATE intikal SET durum = 'IPTAL' WHERE id = ?", i);

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-20")));
    }

    private String intikal(long makinist, long kalkisGar, long varisGar, String kalkis, String varis) {
        return "{\"makinistId\":" + makinist + ",\"kalkisGarId\":" + kalkisGar + ",\"varisGarId\":" + varisGar + ",\"kalkis\":\"" + GUN + "T" + kalkis + ":00+03:00\",\"varis\":\""
                + GUN + "T" + varis + ":00+03:00\",\"aracTuru\":\"SERVIS\"}";
    }
}
