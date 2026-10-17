package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.atama.AtamaVeri;

/**
 * F2b MK-40/FR-04/FR-12: rapor tüketicileri YOLCU satırını doğru yorumlar. Adalet "Sefer" yalnız sürüş, yolculuk ayrı sayı; görev saati puantajla aynı (yolculuk
 * çalışma sayılırsa 1,5 s, sayılmazsa 0); km yalnız sefer km'si (yolculuk 0, "km bilinmeyen" artmaz); son sürüş türetmesi yolculuğu saymaz; yedek ve izin etkisi
 * yolculuktaki makinistin görevli olduğunu görür.
 */
class YolculukRaporTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    @Autowired
    AtamaServisi atamaServisi;

    AtamaVeri v;
    Tarayici t;
    long m;
    long e1;
    long s2;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        e1 = v.sefer("E1", GUN, "08:00", "09:30"); // Ankara -> Eskişehir
        s2 = v.sefer("S2", GUN, "10:30", "15:00", v.eskisehir, v.ankara); // km 250
        v.atama(e1, m, "YOLCU");
        v.atama(s2, m, "ASIL");
        t = atamaci();
    }

    @Test
    void adalet() throws Exception {
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].gorevSaat").value(6.0))
                .andExpect(jsonPath("$.satirlar[0].km").value(250.0)).andExpect(jsonPath("$.satirlar[0].kmBilinmeyenSefer").value(0))
                .andExpect(jsonPath("$.satirlar[0].seferSayisi").value(1)).andExpect(jsonPath("$.satirlar[0].yolculukSayisi").value(1));
        // yolculuk çalışma sayılmıyorsa süre yalnız sefer; yolculuk sayısı yine görünür
        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[0].gorevSaat").value(4.5)).andExpect(jsonPath("$.satirlar[0].yolculukSayisi").value(1))
                .andExpect(jsonPath("$.satirlar[0].km").value(250.0));
    }

    @Test
    void adalet_kmBilinmeyenSeferSayisiYolculukla_artmaz() throws Exception {
        jdbc.update("UPDATE sefer SET km = NULL WHERE id = ?", e1);

        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[0].kmBilinmeyenSefer").value(0)).andExpect(jsonPath("$.satirlar[0].km").value(250.0));
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].kmBilinmeyenSefer").value(0));
    }

    @Test
    void puantaj_yolcuYolcu_gunVeAsgariGuvence_sayilmazsaSatirHicGelmez() throws Exception {
        long yalnizYolcu = v.makinist("D002");
        v.atama(v.sefer("E3", LocalDate.of(2026, 10, 15), "08:00", "09:30"), yalnizYolcu, "YOLCU");

        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + yalnizYolcu).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(1.5))
                .andExpect(jsonPath("$.satirlar[0].ucretlendirilenSaat").value(7.5)).andExpect(jsonPath("$.satirlar[0].calisilanGun").value(1))
                .andExpect(jsonPath("$.satirlar[0].km").value(0.0)).andExpect(jsonPath("$.satirlar[0].kmBilinmeyenSefer").value(0));
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(6.0)).andExpect(jsonPath("$.satirlar[0].km").value(250.0));

        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + yalnizYolcu).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(0.0))
                .andExpect(jsonPath("$.satirlar[0].calisilanGun").value(0)).andExpect(jsonPath("$.satirlar[0].ucretlendirilenSaat").value(0.0));
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(4.5));
    }

    @Test
    void puantajVeAdalet_ayniSayi_yolculuklu() throws Exception {
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + m).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(6.0));
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(jsonPath("$.satirlar[0].gorevSaat").value(6.0));
    }

    @Test
    void sonSurusTuretmesi_yolculuguSaymaz_belgeDurumuK07Degismez() {
        long y = v.makinist("D007");
        v.atama(v.sefer("GE1", LocalDate.of(2026, 9, 20), "08:00", "09:30"), y, "YOLCU");
        v.atama(v.sefer("GE2", LocalDate.of(2026, 9, 25), "08:00", "09:30"), m, "ASIL");

        var sonSurus = atamaServisi.turetilmisSonSurus(Instant.parse("2026-10-10T07:00:00Z"));

        assertThat(sonSurus).doesNotContainKey(y); // yalnız yolculuk: sürüş kaydı yok
        assertThat(sonSurus).containsKey(m);
    }

    @Test
    void yedek_yolculuktakiMakinistGorevli_sayilir() throws Exception {
        long bos = v.makinist("D003");
        assertThat(bos).isPositive();

        // iki aktif makinist (D001 yolcu+asıl, D003 boşta); 14 Eki'nde görevli 1
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[13].gorevli").value(1)).andExpect(jsonPath("$.gunler[13].mevcut").value(1));
        // yalnız yolculuk yapan makinist de görevli
        long yalnizYolcu = v.makinist("D002");
        v.atama(v.sefer("E3", GUN, "20:00", "21:30"), yalnizYolcu, "YOLCU");
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[13].gorevli").value(2)).andExpect(jsonPath("$.gunler[13].mevcut").value(1));
    }

    @Test
    void izinEtkisi_yolculukSatiriniKapsar() throws Exception {
        t.get("/api/atama/makinist/" + m + "/izin-etkisi?bas=2026-10-14&bit=2026-10-14").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.rol=='YOLCU')].trenNo").value("E1"));
    }
}
