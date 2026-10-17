package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F3b MK-87 (FR-05): gün listesi "kondüktör eksik" bilgi göstergesi; ayar kapalıyken hiçbir sefer eksik sayılmaz; BOŞ ve kırmızı tanımı değişmez. */
class GunListesiKonduktorTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long yolcu;
    long yuk;
    long turBos;
    long iptal;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        yolcu = v.sefer("Y1", GUN, "06:00", "07:00");
        yuk = v.sefer("Y2", GUN, "09:00", "10:00");
        turBos = v.sefer("Y3", GUN, "11:00", "12:00");
        iptal = v.sefer("Y4", GUN, "13:00", "14:00");
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU' WHERE id = ?", yolcu);
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", yuk);
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU', durum = 'IPTAL' WHERE id = ?", iptal);
        t = atamaci();
    }

    private void ayar(int deger) throws Exception {
        yonetici().put("/api/ayar/konduktor.yolcu_trenine_gerekli", "{\"deger\":" + deger + ",\"neden\":\"deneme\"}").andExpect(status().isOk());
    }

    private List<Boolean> eksikler() throws Exception {
        String govde = t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(govde, "$.satirlar[*].kondukturEksik");
    }

    @Test
    void ayarKapali_hicbirSeferEksikDegil_sayacSifir() throws Exception {
        assertThat(eksikler()).containsOnly(false);
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.kondukturEksikSayisi").value(0));
    }

    @Test
    void ayarAcik_yalnizIptalOlmayanYolcuTreniKonduktorsuzEksik() throws Exception {
        ayar(1);
        assertThat(eksikler()).containsExactly(true, false, false, false); // yolcu, yük, tür boş, iptal
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.kondukturEksikSayisi").value(1));

        long c1 = v.konduktor("K001");
        t.post("/api/atama/sefer/" + yolcu, "{\"makinistId\":" + c1 + ",\"rol\":\"KONDUKTOR\"}").andExpect(status().isCreated());
        assertThat(eksikler()).containsOnly(false);
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.kondukturEksikSayisi").value(0))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].rol").value("KONDUKTOR"));
    }

    @Test
    void bosTanimiAsiltaKalir_kirmiziSayilmaz_rolSirasiAsilYardimciKonduktorYolcu() throws Exception {
        ayar(1);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long c1 = v.konduktor("K001");
        long y = v.makinist("D003");
        v.atama(yolcu, y, "YOLCU");
        v.atama(yolcu, c1, "KONDUKTOR");
        v.atama(yolcu, m2, "YARDIMCI");
        // ASIL yok: BOŞ kalır; kondüktör eksik DEĞİL
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].kondukturEksik").value(false)).andExpect(jsonPath("$.satirlar[0].kirmizi").value(false));
        v.atama(yuk, m1, "ASIL");
        String govde = t.get("/api/atama/gun?tarih=" + GUN).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(govde, "$.satirlar[0].makinistler[*].rol")).containsExactly("YARDIMCI", "KONDUKTOR", "YOLCU");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Integer.class, yolcu)).isZero(); // BOŞ tanımı: ASIL yok
    }
}
