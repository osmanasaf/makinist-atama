package com.makinistatama.hesap;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** MK-26: geçici parolayla açılan hesap yalnız kimlik / çıkış / parola uçlarını kullanır; parolayı değiştirince tam erişim. */
class ParolaKapisiTest extends TestVeritabani {

    @Test
    void bayrakliHesapYalnizBenCikisParola() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        kullanicilar.makinistHesabiAc(m, "d001", "Demo Makinist D001", "gecici-parola-123"); // bayrak açık
        Tarayici t = Tarayici.girisYapmis(mvc, "d001", "gecici-parola-123");

        t.get("/api/auth/ben").andExpect(status().isOk()).andExpect(jsonPath("$.parolaDegistirmeli").value(true));
        t.get("/api/benim/gorevler").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("PAROLA_DEGISTIR"));
        t.get("/api/benim/belgeler").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("PAROLA_DEGISTIR"));
        t.post("/api/benim/izinler", "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("PAROLA_DEGISTIR"));
        t.get("/api/makinist").andExpect(status().isForbidden()); // personel ucu: dış çit

        t.post("/api/auth/parola", "{\"eskiParola\":\"gecici-parola-123\",\"yeniParola\":\"benim-yeni-parolam-9\"}").andExpect(status().isNoContent());
        Tarayici yeni = Tarayici.girisYapmis(mvc, "d001", "benim-yeni-parolam-9");
        yeni.get("/api/benim/gorevler").andExpect(status().isOk());
        yeni.post("/api/auth/cikis", "").andExpect(status().isNoContent());
    }

    @Test
    void sifirlamaSonrasiAcikOturumDuserYeniGirisYeniKisitli() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");
        t.get("/api/benim/gorevler").andExpect(status().isOk());
        String govde = yonetici().post("/api/makinist/" + m + "/hesap/parola-sifirla", "").andReturn().getResponse().getContentAsString();
        String gecici = com.jayway.jsonpath.JsonPath.read(govde, "$.geciciParola");
        t.get("/api/benim/gorevler").andExpect(status().isUnauthorized());
        Tarayici yeni = Tarayici.girisYapmis(mvc, "d001", gecici);
        yeni.get("/api/benim/gorevler").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("PAROLA_DEGISTIR"));
    }
}
