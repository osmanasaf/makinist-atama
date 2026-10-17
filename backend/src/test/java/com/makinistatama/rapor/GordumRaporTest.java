package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** V10: "gördüm" kayıtları denetim raporunda varsayılan GİZLİ; yalnız tur=GORDUM süzgeciyle ayrı bölümde, kim = makinist hesabı. */
class GordumRaporTest extends TestVeritabani {

    @Test
    void varsayilanRaporda_yok_turSuzgeciyleAyriBolum() throws Exception {
        var v = new AtamaVeri(jdbc);
        LocalDate bugun = LocalDate.now(ZamanAyari.BOLGE);
        long m = v.makinist("D001");
        long atama = v.atama(v.sefer("T1", bugun.plusDays(1), "08:00", "10:00"), m, "ASIL");
        GordumYardimci.gordum(makinistGirisli(m, "D001"), atama).andExpect(status().isOk());
        Tarayici a = atamaci();
        a.post("/api/sefer/" + jdbc.queryForObject("SELECT sefer_id FROM atama", Long.class) + "/gecikme", "{\"kalkisGecikmeDk\":15,\"neden\":\"GECIKME\"}").andExpect(status().isOk());
        String aralik = "bas=" + bugun.minusDays(1) + "&bit=" + bugun.plusDays(1);

        // varsayılan: GORDUM yok, bölüm listesi eskisi gibi 6; kayıt sayısı = bölüm toplamı
        a.get("/api/degisiklik?" + aralik).andExpect(jsonPath("$.bolumler.length()").value(6)).andExpect(jsonPath("$.kayitSayisi").value(1))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='GORDUM')]").isEmpty());
        // süzgeçle ayrı bölüm
        a.get("/api/degisiklik?" + aralik + "&tur=GORDUM").andExpect(jsonPath("$.kayitSayisi").value(1)).andExpect(jsonPath("$.bolumler[?(@.kod=='GORDUM')].sayi").value(1))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='GORDUM')].kim").value("Demo Makinist D001"))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='GORDUM')].turAdi").value("Görev görüldü (gördüm)"))
                .andExpect(jsonPath("$..satirlar[?(@.tur=='GORDUM')].sicil").value("D001"));
        assertThat(a.get("/api/degisiklik/excel?" + aralik + "&tur=GORDUM").andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
