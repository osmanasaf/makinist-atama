package com.makinistatama.kullanici;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** MK-26: MAKINIST rolü kullanıcı yönetiminden açılamaz; makinist hesabının rolü değişmez, personel hesabı makinist yapılamaz. */
class KullaniciRolTest extends TestVeritabani {

    @Test
    void makinistRoluYonetimUcuylaAcilamaz() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/kullanicilar", "{\"eposta\":\"d001\",\"adSoyad\":\"Mak Inist\",\"rol\":\"MAKINIST\",\"parola\":\"uzun-parola-12345\"}")
                .andExpect(status().isBadRequest()); // sicil e-posta biçiminde değil: gövde doğrulaması
        y.post("/api/kullanicilar", "{\"eposta\":\"d001@ornek.test\",\"adSoyad\":\"Mak Inist\",\"rol\":\"MAKINIST\",\"parola\":\"uzun-parola-12345\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("MAKINIST_ROLU"));
    }

    @Test
    void personelHesabiMakinistYapilamaz_makinistHesabininRoluDegismez_aktiflikDegisir() throws Exception {
        Tarayici y = yonetici();
        long personel = ((Number) com.jayway.jsonpath.JsonPath.read(
                y.post("/api/kullanicilar", "{\"eposta\":\"ata2@ornek.test\",\"adSoyad\":\"Ata Ci\",\"rol\":\"ATAMACI\",\"parola\":\"uzun-parola-12345\"}")
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        y.put("/api/kullanicilar/" + personel, "{\"adSoyad\":\"Ata Ci\",\"rol\":\"MAKINIST\",\"aktif\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("MAKINIST_ROLU"));

        long makinist = new AtamaVeri(jdbc).makinist("D001");
        kullanicilar.makinistHesabiAc(makinist, "d001", "Demo Makinist D001", "gecici-parola-123");
        long hesap = jdbc.queryForObject("SELECT id FROM kullanici WHERE makinist_id = ?", Long.class, makinist);
        y.put("/api/kullanicilar/" + hesap, "{\"adSoyad\":\"Demo Makinist D001\",\"rol\":\"ATAMACI\",\"aktif\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("MAKINIST_ROLU"));
        y.put("/api/kullanicilar/" + hesap, "{\"adSoyad\":\"Demo Makinist D001\",\"rol\":\"MAKINIST\",\"aktif\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("MAKINIST")).andExpect(jsonPath("$.aktif").value(false));
    }
}
