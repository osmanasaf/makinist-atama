package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * F2b MK-42/FR-08: otomatik atama servisi, yolculuk bayrağı. Bayrak açıkken "S2 → D007 (ASIL) + E1 yolcu"; uygula iki satır yazar (önce YOLCU); ikinci çalıştırma yeni satır
 * üretmez; bayrak kapalıyken ya da istekte hiç yokken çıktı bugünkü gibidir (S2 boş).
 */
class OtomatikAtamaYolculukServisTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long d007;
    long e1;
    long s2;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        d007 = v.makinist("D007"); // bağlı gar Ankara
        v.makinist("D009");
        long pasif = v.makinist("D005", true, "PASIF"); // E1'in asılı: aktif listede değil (S2'yi garda alamaz)
        e1 = v.sefer("E1", GUN, "08:00", "09:30"); // Ankara -> Eskişehir
        s2 = v.sefer("S2", GUN, "10:30", "15:00", v.eskisehir, v.ankara); // Eskişehir -> Ankara (hedef)
        v.atama(e1, pasif, "ASIL");
        t = atamaci();
    }

    private String istek(boolean uygula, String yolculuk) {
        return "{\"bas\":\"2026-10-14\",\"bit\":\"2026-10-14\",\"uygula\":" + uygula + (yolculuk == null ? "" : ",\"yolculuk\":" + yolculuk) + "}";
    }

    @Test
    void onizleme_yolculukAcik_S2_D007AsilVeE1Yolcu() throws Exception {
        t.post("/api/atama/otomatik", istek(false, "true")).andExpect(status().isOk()).andExpect(jsonPath("$.atananSayisi").value(1)).andExpect(jsonPath("$.bosSayisi").value(0))
                .andExpect(jsonPath("$.atananlar[0].seferId").value(s2)).andExpect(jsonPath("$.atananlar[0].sicil").value("D007"))
                .andExpect(jsonPath("$.atananlar[0].yolculuk.seferId").value(e1)).andExpect(jsonPath("$.atananlar[0].yolculuk.trenNo").value("E1"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(1); // önizleme yazmaz
    }

    @Test
    void uygula_ikiSatir_onceYolcuSonraAsil_tekrarUygulaYeniSatirYok_kayitYolculukAdedi() throws Exception {
        t.post("/api/atama/otomatik", istek(true, "true")).andExpect(status().isOk()).andExpect(jsonPath("$.uygulandi").value(true));

        assertThat(jdbc.queryForObject("SELECT rol FROM atama WHERE sefer_id = ? AND makinist_id = ?", String.class, e1, d007)).isEqualTo("YOLCU");
        assertThat(jdbc.queryForObject("SELECT rol FROM atama WHERE sefer_id = ? AND makinist_id = ?", String.class, s2, d007)).isEqualTo("ASIL");
        List<String> kayitlar = jdbc.queryForList("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'ATAMA' ORDER BY id", String.class);
        assertThat(kayitlar).containsExactly("YOLCU:D007", "ASIL:D007");
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE yeni = 'YOLCU:D007'", String.class)).contains("\"otomatik\":true").contains("\"yolculuk\":true");
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'OTOMATIK_ATAMA'", String.class)).contains("\"yolculuk\":1");
        int once = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);

        t.post("/api/atama/otomatik", istek(true, "true")).andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosSayisi").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(once);
        // yazılan iki satır canlı hesapta kırmızı değil (E1'in pasif asılı fixture gereği kırmızıdır, D007 değil)
        t.get("/api/atama/gun?tarih=2026-10-14").andExpect(jsonPath("$.satirlar[*].makinistler[?(@.sicil=='D007')].kirmizi", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.satirlar[*].makinistler[?(@.sicil=='D007')]", hasSize(2)));
    }

    @Test
    void bayrakKapali_ya_daIstekteYok_eskiSonuc_S2Bos() throws Exception {
        t.post("/api/atama/otomatik", istek(false, "false")).andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosKalanlar", hasSize(1)))
                .andExpect(jsonPath("$.bosKalanlar[0].seferId").value(s2)).andExpect(jsonPath("$.bosKalanlar[0].neden").value("0/2 uygun: konum 2"));
        t.post("/api/atama/otomatik", istek(false, null)).andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosKalanlar[0].seferId").value(s2));
        t.post("/api/atama/otomatik", istek(true, null)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(1);
    }

    @Test
    void yukTrenineBinilmez_ayarKapaliyken_yolculukSecilmez() throws Exception {
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", e1);

        t.post("/api/atama/otomatik", istek(false, "true")).andExpect(jsonPath("$.atananSayisi").value(0)).andExpect(jsonPath("$.bosKalanlar", hasSize(1)));
    }
}
