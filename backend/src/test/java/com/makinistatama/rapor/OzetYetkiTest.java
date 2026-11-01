package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.guvenlik.BenimUclari;

/**
 * F4 / AK-4.6 / OK-35 / MK-180: yönetim özeti ve denetim paketi YALNIZ yönetici (atamacı 403, makinist 403, anonim 401). {@code ay} biçimi ve aralığı (±36 ay) sunucuda doğrulanır. Makinist özet/ortalama/dağılım
 * verisine hiçbir yoldan (/api/benim/** dahil) ulaşamaz.
 */
class OzetYetkiTest extends TestVeritabani {

    static final YearMonth BUGUN = YearMonth.now(AtamaVeri.ISTANBUL); // uygulama saati gerçek saattir

    @Test
    void yoneticiOkur_atamaci403_makinist403_anonim401_ikiUcta() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici makinist = makinistGirisli(m, "D001");
        for (String yol : new String[] { "/api/rapor/ozet?ay=" + BUGUN, "/api/rapor/denetim-paketi/excel?ay=" + BUGUN }) {
            yonetici().get(yol).andExpect(status().isOk());
            atamaci().get(yol).andExpect(status().isForbidden());
            makinist.get(yol).andExpect(status().isForbidden());
            new Tarayici(mvc).get(yol).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void ayDogrulama_bozukBicim400_yokluk400_37AyOncesiVeSonrasi400_36AyTamam() throws Exception {
        Tarayici y = yonetici();
        for (String yol : new String[] { "/api/rapor/ozet", "/api/rapor/denetim-paketi/excel" }) {
            y.get(yol).andExpect(status().isBadRequest()); // ay yok
            y.get(yol + "?ay=abc").andExpect(status().isBadRequest());
            y.get(yol + "?ay=2026-13").andExpect(status().isBadRequest());
            y.get(yol + "?ay=" + BUGUN.minusMonths(37)).andExpect(status().isBadRequest());
            y.get(yol + "?ay=" + BUGUN.plusMonths(37)).andExpect(status().isBadRequest());
            y.get(yol + "?ay=" + BUGUN.minusMonths(36)).andExpect(status().isOk());
            y.get(yol + "?ay=" + BUGUN.plusMonths(36)).andExpect(status().isOk());
        }
    }

    @Test
    void saltOkunur_yazmaYontemleriYok() throws Exception {
        Tarayici y = yonetici();
        for (String yol : new String[] { "/api/rapor/ozet", "/api/rapor/denetim-paketi/excel" }) {
            for (var sonuc : new org.springframework.test.web.servlet.ResultActions[] { y.post(yol, "{}"), y.put(yol, "{}"), y.delete(yol) }) {
                assertThat(sonuc.andReturn().getResponse().getStatus()).isIn(404, 405);
            }
        }
    }

    /** OK-35: makinistin hiçbir uç yanıtı özet/dağılım/ortalama alanı taşımaz (arayüzde gizlemek yetmez; ağ yanıtı kanıtı). */
    @Test
    void makinistUclari_ozetDagilimVeOrtalamaAlaniTasimaz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        v.makinist("D002");
        v.atama(v.seferEv("T1", LocalDate.of(2026, 10, 12), "08:00", "10:00"), m, "ASIL");
        Tarayici mk = makinistGirisli(m, "D001");
        int denenen = 0;
        for (String anahtar : BenimUclari.tumu()) {
            if (!BenimUclari.okuma(anahtar)) {
                continue;
            }
            String govde = mk.istek(BenimUclari.istek(anahtar)).andReturn().getResponse().getContentAsString();
            assertThat(govde).as(anahtar).doesNotContainIgnoringCase("standartSapma").doesNotContainIgnoringCase("doluluk").doesNotContainIgnoringCase("dagilim")
                    .doesNotContainIgnoringCase("ortalama").doesNotContainIgnoringCase("fazlaMesai").doesNotContainIgnoringCase("yonetim");
            denenen++;
        }
        assertThat(denenen).isGreaterThanOrEqualTo(5);
        assertThat(mk.get("/api/benim/ozet?ay=" + BUGUN).andReturn().getResponse().getContentAsString()).doesNotContainIgnoringCase("doluluk");
    }
}
