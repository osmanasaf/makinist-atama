package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** FR-M07 / MK-30: aylık özet adalet raporunun KENDİ satırıdır (ikinci formül yok); başkasının satırı ve ücret alanı yok. */
class BenimOzetTest extends TestVeritabani {

    @Test
    void ozet_adaletRaporununKendiSatiriylaAyni_baskasininSatiriYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).withDayOfMonth(15);
        v.atama(v.sefer("T1", gun, "08:00", "10:00"), m, "ASIL");
        v.atama(v.sefer("T2", gun.plusDays(1), "09:00", "12:00"), m, "ASIL");
        v.atama(v.sefer("T3", gun, "09:00", "10:30"), baska, "ASIL");
        v.atama(v.sefer("T4", gun, "11:00", "20:00"), baska, "ASIL");
        v.atama(v.sefer("T5", gun, "12:00", "15:00"), baska, "ASIL");
        Tarayici t = makinistGirisli(m, "D001");
        YearMonth ay = YearMonth.from(gun);

        String ozet = t.get("/api/benim/ozet?ay=" + ay).andExpect(status().isOk()).andExpect(jsonPath("$.adaleteDahil").value(true)).andExpect(jsonPath("$.seferSayisi").value(2))
                .andExpect(jsonPath("$.planlananDahil").value(true)).andReturn().getResponse().getContentAsString();
        String rapor = atamaci().get("/api/rapor/adalet?ay=" + ay).andReturn().getResponse().getContentAsString();
        List<Number> adaletSaat = JsonPath.read(rapor, "$.satirlar[?(@.sicil=='D001')].gorevSaat");
        List<Object> adaletFark = JsonPath.read(rapor, "$.satirlar[?(@.sicil=='D001')].farkYuzde");
        assertThat(((Number) JsonPath.read(ozet, "$.gorevSaat")).doubleValue()).isEqualTo(adaletSaat.getFirst().doubleValue()).isPositive();
        assertThat(JsonPath.<Object>read(ozet, "$.farkYuzde")).isEqualTo(adaletFark.getFirst());
        assertThat(((Number) JsonPath.read(ozet, "$.ortalamaSaat")).doubleValue()).isEqualTo(((Number) JsonPath.read(rapor, "$.ortalamaSaat")).doubleValue());
        assertThat(ozet).doesNotContain("D002").doesNotContain("satirlar").doesNotContainIgnoringCase("ucret").doesNotContainIgnoringCase("tutar");
    }

    @Test
    void ayAraligiDisiYok400_ayVerilmezseBuAy() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");
        YearMonth buAy = YearMonth.now(ZamanAyari.BOLGE);
        t.get("/api/benim/ozet").andExpect(status().isOk()).andExpect(jsonPath("$.ay").value(buAy.toString()));
        t.get("/api/benim/ozet?ay=" + buAy.minusMonths(12)).andExpect(status().isOk());
        t.get("/api/benim/ozet?ay=" + buAy.plusMonths(1)).andExpect(status().isOk());
        t.get("/api/benim/ozet?ay=" + buAy.minusMonths(13)).andExpect(status().isBadRequest());
        t.get("/api/benim/ozet?ay=" + buAy.plusMonths(2)).andExpect(status().isBadRequest());
        t.get("/api/benim/ozet?ay=2026-13").andExpect(status().isBadRequest());
    }

    @Test
    void askidaMakinist_adaletDisi_sifirSatir() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001", true, "AKTIF");
        Tarayici t = makinistGirisli(m, "D001");
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", m);
        t.get("/api/benim/ozet").andExpect(status().isOk()).andExpect(jsonPath("$.adaleteDahil").value(false)).andExpect(jsonPath("$.seferSayisi").value(0));
    }
}
