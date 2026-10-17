package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.Gorev;

/**
 * Serbest intikal (MK-105, FR-03): rapor tüketicileri (adalet, puantaj, yedek, izin etkisi, konaklama için {@code gorevler}) intikal satırını kendiliğinden yorumlar: aynı
 * {@code yolculuk.calisma_sayilir} anahtarı, km 0, "yolculuk" sayısı; intikal sürüş sayılmaz; izin etkisinde {@code atamaId = null}.
 */
class IntikalRaporTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    @Autowired
    AtamaServisi atamaServisi;

    AtamaVeri v;
    Tarayici t;
    long m;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00"); // 120 dk, servis
        long s = v.sefer("S1", GUN, "09:00", "11:00", v.eskisehir, v.ankara); // 120 dk, km 250
        v.atama(s, m, "ASIL");
        t = atamaci();
    }

    @Test
    void adalet_intikalYolculukSayisinaVeCalismaSayilirsaSureyeGirer_kmSifir() throws Exception {
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].gorevSaat").value(4.0))
                .andExpect(jsonPath("$.satirlar[0].km").value(250.0)).andExpect(jsonPath("$.satirlar[0].seferSayisi").value(1)).andExpect(jsonPath("$.satirlar[0].yolculukSayisi").value(1))
                .andExpect(jsonPath("$.satirlar[0].kmBilinmeyenSefer").value(0));

        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[0].gorevSaat").value(2.0)).andExpect(jsonPath("$.satirlar[0].yolculukSayisi").value(1));
    }

    @Test
    void puantaj_adaletleAyniSayi() throws Exception {
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(4.0)).andExpect(jsonPath("$.satirlar[0].km").value(250.0))
                .andExpect(jsonPath("$.satirlar[0].kmBilinmeyenSefer").value(0));
        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(2.0));
    }

    @Test
    void puantaj_yalnizIntikalYapanMakinist_calismaSayilirsaGunVarSayilmazsaYok() throws Exception {
        long y = v.makinist("D002");
        v.intikal(y, v.ankara, v.eskisehir, GUN, "07:00", "09:00");

        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + y).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(2.0)).andExpect(jsonPath("$.satirlar[0].calisilanGun").value(1))
                .andExpect(jsonPath("$.satirlar[0].km").value(0.0));
        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + y).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(0.0)).andExpect(jsonPath("$.satirlar[0].calisilanGun").value(0));
    }

    @Test
    void yedek_yalnizIntikalYapanMakinistGorevli() throws Exception {
        long y = v.makinist("D002");
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[13].gorevli").value(1)).andExpect(jsonPath("$.gunler[13].mevcut").value(1));
        v.intikal(y, v.ankara, v.eskisehir, GUN, "20:00", "21:30");
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[13].gorevli").value(2)).andExpect(jsonPath("$.gunler[13].mevcut").value(0));
    }

    @Test
    void izinEtkisi_intikalSatiriAtamaIdSiz() throws Exception {
        t.get("/api/atama/makinist/" + m + "/izin-etkisi?bas=2026-10-14&bit=2026-10-14").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trenNo").value("İntikal")).andExpect(jsonPath("$[0].rol").value("YOLCU")).andExpect(jsonPath("$[0].atamaId").doesNotExist())
                .andExpect(jsonPath("$[0].seferId").value(-1)).andExpect(jsonPath("$[1].trenNo").value("S1"));
    }

    @Test
    void gorevler_konaklamaOnerisininKaynagi_intikalYolculukGorevi() {
        List<Gorev> gorevler = atamaServisi.gorevler(List.of(m), GUN, GUN).get(m);

        assertThat(gorevler).hasSize(2);
        assertThat(gorevler.get(0).yolculuk()).isTrue();
        assertThat(gorevler.get(0).seferId()).isEqualTo(-1L);
        assertThat(gorevler.get(0).sureDakika()).isEqualTo(120);
        assertThat(gorevler.get(1).yolculuk()).isFalse();
    }
}
