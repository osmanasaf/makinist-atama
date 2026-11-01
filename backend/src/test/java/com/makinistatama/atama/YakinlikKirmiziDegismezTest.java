package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F3 / MK-5 / AK-3.2: yakınlık hiçbir atamayı kırmızı/uyarı yapmaz ve hiçbir şey yazmaz. Aynı veride marj AÇIK ve KAPALI iken gün listesi (kırmızı/uyarı/nedenler) ve kırmızı sayısı
 * birebir aynıdır; yakınlık uçları çağrıldıktan sonra atama ve denetim kayıtları değişmez.
 */
class YakinlikKirmiziDegismezTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);
    static final JsonMapper JSON = new JsonMapper();

    private String gunSatirlari(Tarayici t) throws Exception {
        JsonNode g = JSON.readTree(t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return JSON.writeValueAsString(g.get("satirlar")) + "|" + g.get("kondukturEksikSayisi");
    }

    @Test
    void marjAcikKapali_gunListesiVeKirmiziSayisiAyni_yakinlikHicYazmaz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long d1 = v.makinist("D001");
        long d2 = v.makinist("D002");
        v.atama(v.seferEv("T0", GUN, "06:00", "14:00"), d1, "ASIL");
        v.atama(v.seferEv("T1", GUN, "15:00", "17:40"), d1, "ASIL"); // günün 10 s 40 dk'sı: sınıra yakın ama ihlalsiz
        v.atama(v.seferEv("T2", GUN, "08:00", "12:00"), d2, "ASIL");
        v.atama(v.seferEv("T3", GUN, "10:00", "13:00"), d2, "ASIL"); // çakışma: kırmızı
        Tarayici t = atamaci();

        String acik = gunSatirlari(t);
        String sayiAcik = t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andReturn().getResponse().getContentAsString();
        assertThat(acik).as("duyarlılık: kırmızı atama var").contains("\"kirmizi\":true");
        assertThat(t.get("/api/atama/yakinlik?tarih=" + GUN).andReturn().getResponse().getContentAsString()).as("duyarlılık: yakınlık işareti var").contains("K-13");

        int atamaOnce = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);
        int kayitOnce = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);
        t.get("/api/atama/sefer/" + jdbc.queryForObject("SELECT id FROM sefer WHERE tren_no = 'T1'", Long.class) + "/yakinlik").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(atamaOnce);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(kayitOnce);

        yonetici().put("/api/ayar/marj.aktif", "{\"deger\":0,\"neden\":\"kapat\"}").andExpect(status().isOk());
        assertThat(gunSatirlari(t)).as("marj kapalıyken gün listesi (kırmızı, uyarı, nedenler) birebir aynı").isEqualTo(acik);
        assertThat(t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andReturn().getResponse().getContentAsString()).isEqualTo(sayiAcik);
    }
}
