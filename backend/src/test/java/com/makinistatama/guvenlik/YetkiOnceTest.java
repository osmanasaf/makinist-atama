package com.makinistatama.guvenlik;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** Yetki denetimi gövde doğrulamasından ÖNCE çalışır: yetkisiz kullanıcı geçersiz gövdeyle de 400 değil 403 alır (doğrulama mesajı sızmaz). */
class YetkiOnceTest extends TestVeritabani {

    @Test
    void atamaci_gecersizGovdeyle_yoneticiUclarinda_403() throws Exception {
        Tarayici a = atamaci();
        a.post("/api/ozel-kural", "{\"ad\":\"\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("YETKISIZ"));
        a.put("/api/ozel-kural/1", "{\"ad\":\"\"}").andExpect(status().isForbidden());
        a.post("/api/ozel-kural/dene", "{\"ad\":\"\"}").andExpect(status().isForbidden());
        a.post("/api/tanimlar", "{}").andExpect(status().isForbidden());
        a.put("/api/tanimlar/1", "{}").andExpect(status().isForbidden());
        a.put("/api/parametre/gorev.hazirlik_dk", "{}").andExpect(status().isForbidden());
        a.put("/api/kural/K-13/siddet", "{}").andExpect(status().isForbidden());
        a.post("/api/kullanicilar", "{}").andExpect(status().isForbidden());
        a.put("/api/kullanicilar/1", "{}").andExpect(status().isForbidden());
        a.post("/api/kullanicilar/1/parola", "{}").andExpect(status().isForbidden());
    }

    @Test
    void yonetici_gecersizGovdeyle_hala400() throws Exception {
        yonetici().post("/api/ozel-kural", "{\"ad\":\"\"}").andExpect(status().isBadRequest());
    }
}
