package com.makinistatama.atama;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * M4 review WARNING-1 / OK-6: görevin başlangıcı atamanın yapıldığı andaki bilgiye bağlıdır. Atama gecikme girilmeden ÖNCE yapıldıysa makinist planlanan
 * saatte görevdeydi (OK-1: bekleme çalışmadır); gecikme girildikten SONRA yapıldıysa görev güncel kalkıştan başlar (aksi halde gecikmesi bilinen seferde
 * aynı makinist ENGEL yüzünden seçilemez, K-16/K-11 yanlış ihlal verir).
 */
class GecikmeSonrasiAtamaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    long m;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        t = atamaci();
    }

    private void gecikme(long sefer, int kalkisDk, int varisDk) {
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = ?, varis_gecikme_dk = ? WHERE id = ?", kalkisDk, varisDk, sefer);
    }

    private void adayUygun(long sefer, boolean uygun) throws Exception {
        t.get("/api/atama/sefer/" + sefer + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$[?(@.makinistId==" + m + ")].uygun").value(uygun));
    }

    private void atamaKabulVeKirmiziDegil(long sefer) throws Exception {
        t.post("/api/atama/sefer/" + sefer, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        t.get("/api/atama/kirmizi-sayisi?bas=" + GUN.minusDays(1) + "&bit=" + GUN.plusDays(1)).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void reviewOrnegi_Y06_09_atanmis_X08_12_gecikme120_gecikmedenSonraAyniMakinistAtanabilir() throws Exception {
        v.atama(v.seferEv("Y", GUN, "06:00", "09:00"), m, "ASIL");
        long x = v.seferEv("X", GUN, "08:00", "12:00");
        gecikme(x, 120, 120); // fiilen 10:00-14:00; atama gecikmeden SONRA

        adayUygun(x, true);
        atamaKabulVeKirmiziDegil(x);
    }

    @Test
    void gecikmedenOnceAtanmisMakinist_seferiPlanlananSaatten_08den_baslar_OK1() throws Exception {
        long x = v.seferEv("X", GUN, "08:00", "12:00");
        t.post("/api/atama/sefer/" + x, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()); // gecikme henüz yok
        gecikme(x, 120, 120);
        long y = v.seferEv("Y", GUN, "06:00", "09:00");

        adayUygun(y, false); // makinist X için 08:00'de görevde: Y (06-09) ile çakışır
        t.post("/api/atama/sefer/" + y, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void K16_gecikmeliGeceSeferi_gecikmedenSonraAtanabilir() throws Exception {
        long g = v.seferEv("G", GUN, "18:00", "03:00"); // 18:00 -> 21:00'e ertelendi; planlanandan sayılsa nöbet 9 s > 7,5 s
        gecikme(g, 180, 0);

        adayUygun(g, true);
        atamaKabulVeKirmiziDegil(g);
    }

    @Test
    void K11_gecikmeliSefer_dinlenmeGuncelKalkistanSayilir() throws Exception {
        v.atama(v.seferEv("P", GUN.minusDays(1), "14:00", "20:00"), m, "ASIL");
        long x = v.seferEv("X", GUN, "06:00", "10:00"); // 06:00 -> 08:00: dinlenme 12 s (planlanandan 10 s < 11 s)
        gecikme(x, 120, 120);

        adayUygun(x, true);
        atamaKabulVeKirmiziDegil(x);
    }
}
