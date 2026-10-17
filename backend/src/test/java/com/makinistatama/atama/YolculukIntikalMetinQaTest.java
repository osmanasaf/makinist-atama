package com.makinistatama.atama;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** QA eki (BUG-1): kullanıcıya görünen intikal metinleri doğru Türkçe kodlamayla gelmeli (çift kodlanmış Türkçe bozulması yok). */
class YolculukIntikalMetinQaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long hedef;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        hedef = v.sefer("H", GUN, "09:00", "10:30", v.eskisehir, v.ankara);
        t = atamaci();
    }

    @Test
    void oneriGerekcesi_okDogruKodlanmis() throws Exception {
        yonetici().put("/api/tanimlar/intikal-sure", "{\"garAId\":" + v.ankara + ",\"garBId\":" + v.eskisehir + ",\"dakika\":90}").andExpect(status().isOk());

        t.get("/api/atama/sefer/" + hedef + "/yolculuk-oner?makinistId=" + m).andExpect(status().isOk())
                .andExpect(jsonPath("$.intikal.gerekce").value("Ankara → Eskişehir serbest intikal (1 s 30 dk); 15 dk aktarma"));
    }

    @Test
    void oneriYokHatasi_turkceMetinBozulmamis() throws Exception {
        jdbc.update("DELETE FROM gar_intikal_suresi");

        t.post("/api/atama/sefer/" + hedef + "/intikalle", "{\"makinistId\":" + m + ",\"aracTuru\":\"SERVIS\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", not(containsString("\u00C3")))).andExpect(jsonPath("$.detail", containsString("Bu makinist için süre tablosuna göre")));
    }
}
