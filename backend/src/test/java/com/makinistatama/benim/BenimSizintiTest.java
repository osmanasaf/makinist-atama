package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** NFR-M01 / NFR-M02: iki makinist hesabıyla her {@code /api/benim} okuması karşılıklı; yanıtlarda başka sicil/ad ve kişisel veri alanı yok. */
class BenimSizintiTest extends TestVeritabani {

    @Test
    void ikiMakinistBirbirininVerisiniGormez() throws Exception {
        var v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        LocalDate bugun = LocalDate.now(ZamanAyari.BOLGE);
        v.atama(v.sefer("TA1", bugun, "08:00", "10:00"), a, "ASIL");
        v.atama(v.sefer("TB1", bugun, "12:00", "14:00"), b, "ASIL");
        v.atama(v.sefer("TB2", bugun.plusDays(1), "12:00", "14:00"), b, "ASIL");
        Tarayici ta = makinistGirisli(a, "D001");
        Tarayici tb = makinistGirisli(b, "D002");
        ta.post("/api/benim/izinler", "{\"baslangic\":\"" + bugun.plusDays(3) + "\",\"bitis\":\"" + bugun.plusDays(4) + "\",\"tur\":\"YILLIK\"}");
        jdbc.update("UPDATE makinist SET ehliyet_bitis = ? WHERE id = ?", bugun.plusDays(3), b);

        String[] uclar = { "/api/benim/gorevler", "/api/benim/belgeler", "/api/benim/izinler", "/api/benim/ozet?ay=" + YearMonth.from(bugun) };
        for (String uc : uclar) {
            String govdeA = ta.get(uc).andReturn().getResponse().getContentAsString();
            String govdeB = tb.get(uc).andReturn().getResponse().getContentAsString();
            assertThat(govdeA).as("A %s", uc).doesNotContain("D002").doesNotContain("TB1").doesNotContain("TB2").doesNotContain("@");
            assertThat(govdeB).as("B %s", uc).doesNotContain("D001").doesNotContain("TA1").doesNotContain("@");
        }
        assertThat(tb.get("/api/benim/izinler").andReturn().getResponse().getContentAsString()).isEqualTo("[]");
        assertThat(ta.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString()).contains("TA1");
        assertThat(tb.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString()).contains("TB1").contains("TB2");
    }
}
