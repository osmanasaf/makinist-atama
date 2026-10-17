package com.makinistatama.atama;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** QA eki (FR-03 gece sınırı): 22:00-06:00 intikal 8 s gece sayılır; yolculuk.calisma_sayilir=1 iken K-16 ENGEL, 0 iken ihlal yok. */
class IntikalGeceQaTest extends TestVeritabani {

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

    private String govde(String kalkis, String varisErtesiGun) {
        return "{\"makinistId\":" + m + ",\"kalkisGarId\":" + v.ankara + ",\"varisGarId\":" + v.eskisehir + ",\"kalkis\":\"" + GUN + "T" + kalkis + ":00+03:00\",\"varis\":\""
                + GUN.plusDays(1) + "T" + varisErtesiGun + ":00+03:00\",\"aracTuru\":\"SERVIS\"}";
    }

    @Test
    void geceIntikali_sekizSaat_calismaSayilirsaK16Engel_sayilmazsaGecer() throws Exception {
        t.post("/api/intikal", govde("22:00", "06:00")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-16")));

        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.post("/api/intikal", govde("22:00", "06:00")).andExpect(status().isCreated());
    }

    @Test
    void geceIntikali_yediBucukSaatSinirdaGecer() throws Exception {
        t.post("/api/intikal", govde("22:30", "06:00")).andExpect(status().isCreated()); // 7 s 30 dk = sınır, ihlal değil
    }
}
