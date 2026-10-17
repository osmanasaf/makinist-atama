package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.rapor.BelgeDurumuDto;
import com.makinistatama.rapor.BelgeDurumuServisi;

/** FR-M05 / NFR-M02: makinistin kendi belgeleri; sınıflama belge panosuyla AYNI koddan gelir. */
class BenimBelgeTest extends TestVeritabani {

    @Autowired
    BelgeDurumuServisi pano;

    @Test
    void yirmiGunKaldiYaklasiyor_bitisDunseDoldu_baskasininBelgesiYok() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long baska = v.makinist("D002");
        LocalDate bugun = BenimGorevTest.BUGUN;
        jdbc.update("UPDATE makinist SET ehliyet_bitis = ?, saglik_bitis = ? WHERE id = ?", bugun.plusDays(20), bugun.minusDays(1), m);
        jdbc.update("UPDATE makinist SET ehliyet_bitis = ? WHERE id = ?", bugun.plusDays(1), baska);
        Tarayici t = makinistGirisli(m, "D001");

        String govde = t.get("/api/benim/belgeler").andExpect(status().isOk()).andExpect(jsonPath("$.enKotu").value("DOLDU")).andReturn().getResponse().getContentAsString();
        List<String> durumlar = JsonPath.read(govde, "$.belgeler[?(@.tur=='EHLIYET')].durum");
        assertThat(durumlar).containsExactly("YAKLASIYOR");
        List<Integer> ehliyetKalan = JsonPath.read(govde, "$.belgeler[?(@.tur=='EHLIYET')].kalanGun");
        List<Integer> saglikKalan = JsonPath.read(govde, "$.belgeler[?(@.tur=='SAGLIK')].kalanGun");
        assertThat(ehliyetKalan).containsExactly(20);
        assertThat(saglikKalan).containsExactly(-1);
        assertThat(govde).doesNotContain("D002").doesNotContain("makinistler");
    }

    @Test
    void sinflama_belgePanosuylaAyni() throws Exception {
        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        jdbc.update("UPDATE makinist SET ehliyet_bitis = ?, psikoteknik_bitis = ? WHERE id = ?", BenimGorevTest.BUGUN, BenimGorevTest.BUGUN.minusDays(40), m);
        Tarayici t = makinistGirisli(m, "D001");

        String govde = t.get("/api/benim/belgeler").andReturn().getResponse().getContentAsString();
        BelgeDurumuDto.MakinistBelgeleri panoda = pano.rapor(BenimGorevTest.BUGUN).makinistler().stream().filter(x -> x.makinistId() == m).findFirst().orElseThrow();
        List<String> benimAd = JsonPath.read(govde, "$.belgeler[*].ad");
        List<String> benimDurum = JsonPath.read(govde, "$.belgeler[*].durum");
        assertThat(benimAd).isEqualTo(panoda.belgeler().stream().map(BelgeDurumuDto.Belge::ad).toList());
        assertThat(benimDurum).isEqualTo(panoda.belgeler().stream().map(b -> b.durum().name()).toList());
        assertThat((String) JsonPath.read(govde, "$.enKotu")).isEqualTo(panoda.enKotu().name());
    }
}
