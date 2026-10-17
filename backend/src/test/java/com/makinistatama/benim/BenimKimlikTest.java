package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.guvenlik.BenimUclari;

/** MK-28: hesap durum kapısı ({@code BenimKapisi}) her {@code /api/benim} ucunu kapsar; yeni uç tabloya eklenince otomatik taranır. */
class BenimKimlikTest extends TestVeritabani {

    @Test
    void pasifMakinist403_askidaYazma403() throws Exception {
        var veri = new AtamaVeri(jdbc);
        long m = veri.makinist("D001");
        veri.atama(veri.sefer("T1", java.time.LocalDate.now(com.makinistatama.ortak.ZamanAyari.BOLGE).plusDays(1), "08:00", "10:00"), m, "ASIL"); // kimlik 1: atamaId=1 isteyen okuma uçları (takas/hedefler) 200 verir
        Tarayici t = makinistGirisli(m, "D001");
        assertThat(BenimUclari.tumu()).hasSizeGreaterThanOrEqualTo(5);

        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", m);
        List<String> sorunlar = new ArrayList<>();
        for (String uc : BenimUclari.tumu()) {
            var yanit = t.istek(BenimUclari.istek(uc)).andReturn().getResponse();
            if (BenimUclari.okuma(uc)) {
                if (yanit.getStatus() != 200) {
                    sorunlar.add("ASKIDA okuma " + uc + " -> " + yanit.getStatus());
                }
            } else {
                String kod = yanit.getStatus() == 403 ? JsonPath.read(yanit.getContentAsString(), "$.kod") : "?";
                if (yanit.getStatus() != 403 || !"HESAP_ASKIDA".equals(kod)) {
                    sorunlar.add("ASKIDA yazma " + uc + " -> " + yanit.getStatus() + " " + kod);
                }
            }
        }

        jdbc.update("UPDATE makinist SET durum = 'PASIF' WHERE id = ?", m);
        for (String uc : BenimUclari.tumu()) {
            var yanit = t.istek(BenimUclari.istek(uc)).andReturn().getResponse();
            String kod = yanit.getStatus() == 403 ? JsonPath.read(yanit.getContentAsString(), "$.kod") : "?";
            if (yanit.getStatus() != 403 || !"HESAP_KAPALI".equals(kod)) {
                sorunlar.add("PASIF " + uc + " -> " + yanit.getStatus() + " " + kod);
            }
        }
        assertThat(sorunlar).isEmpty();
    }

    @Test
    void makinistKartiSilinmisHesap_hesapKapali() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");
        jdbc.update("DELETE FROM brove_arac_tipi WHERE makinist_id = ?", m);
        jdbc.update("DELETE FROM brove_hat WHERE makinist_id = ?", m);
        jdbc.update("DELETE FROM makinist WHERE id = ?", m); // demo sıfırlama gibi: hesap kartsız kalır
        var yanit = t.get("/api/benim/gorevler").andReturn().getResponse();
        assertThat(yanit.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(yanit.getContentAsString(), "$.kod")).isEqualTo("HESAP_KAPALI");
    }

    @Test
    void csrfBelirteciOlmadan_benimYazmasi403() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        Tarayici t = makinistGirisli(m, "D001");
        var yanit = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/benim/izinler").session(t.oturum).contentType("application/json")
                .content("{\"baslangic\":\"2030-01-01\",\"bitis\":\"2030-01-02\",\"tur\":\"YILLIK\"}")).andReturn().getResponse();
        assertThat(yanit.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(yanit.getContentAsString(), "$.kod")).isEqualTo("GUVENLIK_TOKENI");
    }
}
