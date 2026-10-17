package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F2b MK-41/FR-06: {@code GET /api/atama/sefer/{id}/yolculuk-oner}: en çok 3 öneri, hedef garına varan, kuralları geçen; yetki ve 404'ler; yazmaz. */
class YolculukOneriUcTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long s2;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        s2 = v.sefer("S2", GUN, "10:30", "15:00", v.eskisehir, v.ankara); // hedef: Eskişehir -> Ankara
        t = atamaci();
    }

    private String url(long sefer, long makinist) {
        return "/api/atama/sefer/" + sefer + "/yolculuk-oner?makinistId=" + makinist;
    }

    @Test
    void oneri_hedefGarinaVaranSeferler_enAzBeklemeSirasiyla_enCok3() throws Exception {
        long e1 = v.sefer("E1", GUN, "08:00", "09:30"); // bekle 60
        long e2 = v.sefer("E2", GUN, "07:00", "08:30"); // bekle 120
        long e3 = v.sefer("E3", GUN, "06:00", "07:30"); // bekle 180
        long e4 = v.sefer("E4", GUN, "05:00", "06:30"); // bekle 240
        v.sefer("YANLIS_YON", GUN, "08:30", "09:30", v.eskisehir, v.ankara); // Eskişehir'e varmaz
        assertThat(e4).isPositive();

        t.get(url(s2, m)).andExpect(status().isOk()).andExpect(jsonPath("$.oneriler", hasSize(3))).andExpect(jsonPath("$.oneriler[0].seferId").value(e1))
                .andExpect(jsonPath("$.oneriler[1].seferId").value(e2)).andExpect(jsonPath("$.oneriler[2].seferId").value(e3)).andExpect(jsonPath("$.oneriler[0].aktarmaDk").value(60))
                .andExpect(jsonPath("$.oneriler[0].trenNo").value("E1")).andExpect(jsonPath("$.oneriler[0].kalkisGar").value("Ankara")).andExpect(jsonPath("$.oneriler[0].varisGar").value("Eskişehir"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero(); // yazmaz
    }

    @Test
    void aktarmaAyari_kisaAktarmaElenir_ayarDegisinceGirer() throws Exception {
        long e = v.sefer("E1", GUN, "08:50", "10:20"); // bekle 10 dk < 15
        t.get(url(s2, m)).andExpect(jsonPath("$.oneriler", hasSize(0)));

        yonetici().put("/api/ayar/yolculuk.aktarma_dk", "{\"deger\":10,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get(url(s2, m)).andExpect(jsonPath("$.oneriler", hasSize(1))).andExpect(jsonPath("$.oneriler[0].seferId").value(e));
    }

    @Test
    void yukTreni_ayarKapaliysaOnerilmez() throws Exception {
        long e = v.sefer("E1", GUN, "08:00", "09:30");
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", e);

        t.get(url(s2, m)).andExpect(jsonPath("$.oneriler", hasSize(0)));
        yonetici().put("/api/ayar/yolculuk.yuk_trenine_binilir", "{\"deger\":1,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get(url(s2, m)).andExpect(jsonPath("$.oneriler", hasSize(1)));
    }

    @Test
    void makinistHedefteZatenGorevli_veyaYolculukSeferiIptal_oneriYok() throws Exception {
        long e = v.sefer("E1", GUN, "08:00", "09:30");
        v.atama(s2, m, "YARDIMCI");
        t.get(url(s2, m)).andExpect(jsonPath("$.oneriler", hasSize(0)));

        jdbc.update("DELETE FROM atama");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", e);
        t.get(url(s2, m)).andExpect(jsonPath("$.oneriler", hasSize(0)));
    }

    @Test
    void yetkiVe404() throws Exception {
        v.sefer("E1", GUN, "08:00", "09:30");

        makinistGirisli(m, "D001").get(url(s2, m)).andExpect(status().isForbidden());
        Tarayici anonim = new Tarayici(mvc);
        anonim.get(url(s2, m)).andExpect(status().isUnauthorized());
        t.get(url(999, m)).andExpect(status().isNotFound());
        t.get(url(s2, 999)).andExpect(status().isNotFound());
        t.get("/api/atama/sefer/" + s2 + "/yolculuk-oner").andExpect(status().isBadRequest());
    }
}
