package com.makinistatama.hesap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** FR-M01 / FR-M17: makinist hesabı açma, sıfırlama, kilit açma (MK-26, MK-36). */
class HesapTest extends TestVeritabani {

    private int denetim(String tur) {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = ?", Integer.class, tur);
    }

    @Test
    void hesapAc_201_geciciParolaYalnizYanitta_ikinciHesap409() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici y = yonetici();

        var yanit = y.post("/api/makinist/" + id + "/hesap", "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.hesap.var").value(true)).andExpect(jsonPath("$.hesap.girisAdi").value("d001"))
                .andExpect(jsonPath("$.hesap.parolaDegistirmeli").value(true)).andReturn().getResponse();
        String gecici = JsonPath.read(yanit.getContentAsString(), "$.geciciParola");
        assertThat(gecici).hasSize(12).matches("[A-HJ-NP-Za-km-z2-9]{12}");
        assertThat(yanit.getHeader("Cache-Control")).contains("no-store");
        assertThat(denetim("HESAP_ACILDI")).isEqualTo(1);

        y.post("/api/makinist/" + id + "/hesap", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("HESAP_VAR"));
        // okuma ucunda parola yok
        String oku = y.get("/api/makinist/" + id + "/hesap").andExpect(status().isOk()).andExpect(jsonPath("$.var").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(oku).doesNotContain(gecici).doesNotContain("geciciParola").doesNotContain("karma");
        // geçici parolayla giriş çalışır, bayrak açık
        Tarayici m = Tarayici.girisYapmis(mvc, "d001", gecici);
        m.get("/api/auth/ben").andExpect(jsonPath("$.parolaDegistirmeli").value(true)).andExpect(jsonPath("$.rol").value("MAKINIST"));
    }

    @Test
    void hesapYokkenOkuma_varYanitiFalse_olmayanMakinist404() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici y = yonetici();
        y.get("/api/makinist/" + id + "/hesap").andExpect(status().isOk()).andExpect(jsonPath("$.var").value(false));
        y.get("/api/makinist/9999/hesap").andExpect(status().isNotFound());
        y.post("/api/makinist/9999/hesap", "").andExpect(status().isNotFound());
        y.post("/api/makinist/" + id + "/hesap/parola-sifirla", "").andExpect(status().isNotFound()); // hesap yok
        y.post("/api/makinist/" + id + "/hesap/kilit-ac", "").andExpect(status().isNotFound());
    }

    @Test
    void atamaciDaHesapAcar_pasifMakinist409_askidaOlabilir() throws Exception {
        var v = new AtamaVeri(jdbc);
        long pasif = v.makinist("D001", true, "PASIF");
        long askida = v.makinist("D002", true, "ASKIDA");
        Tarayici a = atamaci();
        a.post("/api/makinist/" + pasif + "/hesap", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("MAKINIST_PASIF"));
        a.post("/api/makinist/" + askida + "/hesap", "").andExpect(status().isCreated());
    }

    @Test
    void esZamanliIkiHesapAcma_biri201_biri409() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici y = yonetici();
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        CountDownLatch baslat = new CountDownLatch(1);
        try {
            List<Future<Integer>> sonuc = java.util.stream.IntStream.range(0, 2).mapToObj(i -> havuz.submit(() -> {
                baslat.await();
                return y.post("/api/makinist/" + id + "/hesap", "").andReturn().getResponse().getStatus();
            })).toList();
            baslat.countDown();
            List<Integer> kodlar = List.of(sonuc.get(0).get(), sonuc.get(1).get());
            assertThat(kodlar).containsExactlyInAnyOrder(201, 409);
        } finally {
            havuz.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici WHERE makinist_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(denetim("HESAP_ACILDI")).isEqualTo(1);
    }

    @Test
    void parolaSifirla_eskiOturumVeParolaGecersiz_bayrakAcik() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");
        m.get("/api/auth/ben").andExpect(status().isOk());

        Tarayici y = yonetici();
        y.post("/api/makinist/" + id + "/hesap/parola-sifirla", "").andExpect(status().isOk()).andExpect(jsonPath("$.hesap.parolaDegistirmeli").value(true));

        m.get("/api/auth/ben").andExpect(status().isUnauthorized()); // açık oturum düştü
        new Tarayici(mvc).giris("d001", Tarayici.PAROLA).andExpect(status().isUnauthorized());
    }

    @Test
    void parolaSifirla_eskiParolaReddedilir_yeniGeciciParolaGirer() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        makinistGirisli(id, "D001");
        Tarayici y = yonetici();
        String gecici = JsonPath.read(y.post("/api/makinist/" + id + "/hesap/parola-sifirla", "").andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.geciciParola");

        new Tarayici(mvc).giris("d001", Tarayici.PAROLA).andExpect(status().isUnauthorized());
        Tarayici yeni = new Tarayici(mvc);
        yeni.giris("d001", gecici).andExpect(status().isOk());
        yeni.get("/api/auth/ben").andExpect(jsonPath("$.parolaDegistirmeli").value(true));
        assertThat(denetim("HESAP_PAROLA_SIFIRLANDI")).isEqualTo(1);
    }

    @Test
    void kilitAc_besHataSonrasiGirisYineAcilir() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        makinistGirisli(id, "D001");
        Tarayici y = yonetici();
        Tarayici anonim = new Tarayici(mvc);
        for (int i = 0; i < 5; i++) {
            anonim.giris("d001", "yanlis-parola-xyz").andExpect(status().isUnauthorized());
        }
        anonim.giris("d001", Tarayici.PAROLA).andExpect(status().isTooManyRequests());
        y.get("/api/makinist/" + id + "/hesap").andExpect(jsonPath("$.kilitli").value(true));

        y.post("/api/makinist/" + id + "/hesap/kilit-ac", "").andExpect(status().isOk()).andExpect(jsonPath("$.kilitli").value(false));
        anonim.giris("d001", Tarayici.PAROLA).andExpect(status().isOk());
        assertThat(denetim("HESAP_KILIT_ACILDI")).isEqualTo(1);
    }

    @Test
    void yoneticiGenelParolaUcuMakinistHesabinda400() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        makinistGirisli(id, "D001");
        long hesap = jdbc.queryForObject("SELECT id FROM kullanici WHERE makinist_id = ?", Long.class, id);
        yonetici().post("/api/kullanicilar/" + hesap + "/parola", "{\"yeniParola\":\"yeni-uzun-parola-12\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("MAKINIST_HESABI"));
    }
}
