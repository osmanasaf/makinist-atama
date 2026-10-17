package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T10.3: "yerine ata" önerisi: mevcut makinist hariç ilk 3 ENGEL'siz aday (bu ay saat sırası), gerekçeli; aday yoksa neden özeti. */
class YerineAtaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    @Test
    void ucOneri_mevcutHaric_buAySaatSirasiyla_gerekceli() throws Exception {
        long mevcut = v.makinist("D001");
        long[] diger = { v.makinist("D002"), v.makinist("D003"), v.makinist("D004"), v.makinist("D005") };
        long s = v.sefer("T1", GUN, "08:00", "12:00");
        v.atama(s, mevcut, "ASIL");
        // bu ay önceden çalışılan saatler: D002 8 s, D003 2 s, D004 4 s, D005 yok
        int[] saat = { 8, 2, 4 };
        for (int i = 0; i < saat.length; i++) {
            v.atama(v.seferEv("G" + i, GUN.minusDays(5 + i), "08:00", String.format("%02d:00", 8 + saat[i])), diger[i], "ASIL");
        }

        atamaci().get("/api/atama/sefer/" + s + "/oneri").andExpect(status().isOk()).andExpect(jsonPath("$.uygunAdayYok").value(false)).andExpect(jsonPath("$.oneriler", hasSize(3)))
                .andExpect(jsonPath("$.oneriler[0].sicil").value("D005")).andExpect(jsonPath("$.oneriler[1].sicil").value("D003"))
                .andExpect(jsonPath("$.oneriler[2].sicil").value("D004"))
                .andExpect(jsonPath("$.oneriler[0].gerekce").value(containsString("Bu ay 0 saat"))).andExpect(jsonPath("$.oneriler[0].gerekce").value(containsString("kural ihlali yok")))
                .andExpect(jsonPath("$.oneriler[1].gerekce").value(containsString("Bu ay 2 saat")));
    }

    @Test
    void uyarisizAdaylarOnce_uyariliAdayArkadan() throws Exception {
        long mevcut = v.makinist("D001");
        long uyarili = v.makinist("D002"); // dünkü sefer Eskişehir'de bitti: konum uyuşmazlığı (UYARI)
        long temiz = v.makinist("D003");
        v.atama(v.sefer("G", GUN.minusDays(1), "06:00", "10:00", v.ankara, v.eskisehir), uyarili, "ASIL");
        v.atama(v.seferEv("H", GUN.minusDays(3), "06:00", "12:00"), temiz, "ASIL"); // temiz daha çok çalışmış ama uyarısız
        long s = v.sefer("T1", GUN, "08:00", "12:00");
        v.atama(s, mevcut, "ASIL");

        atamaci().get("/api/atama/sefer/" + s + "/oneri").andExpect(jsonPath("$.oneriler[0].sicil").value("D003")).andExpect(jsonPath("$.oneriler[1].sicil").value("D002"))
                .andExpect(jsonPath("$.oneriler[1].uyarilar", hasSize(1))).andExpect(jsonPath("$.oneriler[1].gerekce").value(containsString("uyarı:")));
    }

    @Test
    void adayYok_mesajdaIhlalOzeti() throws Exception {
        long mevcut = v.makinist("D001");
        v.makinist("D002", false, "AKTIF");
        v.makinist("D003", false, "AKTIF");
        long s = v.sefer("T1", GUN, "08:00", "12:00");
        v.atama(s, mevcut, "ASIL");

        atamaci().get("/api/atama/sefer/" + s + "/oneri").andExpect(jsonPath("$.uygunAdayYok").value(true)).andExpect(jsonPath("$.oneriler", hasSize(0)))
                .andExpect(jsonPath("$.mesaj").value(containsString("Uygun aday yok"))).andExpect(jsonPath("$.mesaj").value(containsString("0/2 uygun: hat yetkisi 2")));
    }

    @Test
    void oneriDisiEngelliMakinist_degistirYolundan_422_oneriliOlan_200() throws Exception {
        long mevcut = v.makinist("D001");
        long yetkisiz = v.makinist("D002", false, "AKTIF");
        long iyi = v.makinist("D003");
        long s = v.sefer("T1", GUN, "08:00", "12:00");
        v.atama(s, mevcut, "ASIL");
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + yetkisiz + ",\"neden\":\"GECIKME\"}").andExpect(status().isUnprocessableContent());
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + iyi + ",\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, s)).isEqualTo(iyi);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA_DEGISTIR'", Integer.class)).isEqualTo(1);
    }

    @Test
    void iptalSefer_oneriYok409() throws Exception {
        long s = v.sefer("T1", GUN, "08:00", "12:00");
        atamaci().post("/api/sefer/" + s + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());

        atamaci().get("/api/atama/sefer/" + s + "/oneri").andExpect(status().isConflict());
    }
}
