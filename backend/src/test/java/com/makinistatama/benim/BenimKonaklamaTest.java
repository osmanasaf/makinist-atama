package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** F2b OK-10: makinist yalnız KENDİ konaklamasını görür (not alanı yok); başkasınınki, ufuk dışı ve atamacı/anonim erişimi yok; askıdaki okur, pasif okuyamaz. */
class BenimKonaklamaTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    private void kaydet(long makinist, long gar, int gunSonra, int saat, int sureSaat, String not) {
        ZonedDateTime giris = BUGUN.plusDays(gunSonra).atTime(saat, 0).atZone(ZamanAyari.BOLGE);
        jdbc.update("INSERT INTO konaklama (makinist_id, gar_id, yer_tipi, giris, cikis, notu) VALUES (?, ?, 'OTEL', ?::timestamptz, ?::timestamptz, ?)", makinist, gar,
                giris.toOffsetDateTime().toString(), giris.plusHours(sureSaat).toOffsetDateTime().toString(), not);
    }

    @Test
    void kendiKonaklamasi_notYok_baskasininki_ufukDisi_gorunmez() throws Exception {
        var v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long sivas = TestTanimlari.gar(jdbc, "Sivas");
        kaydet(a, sivas, 1, 12, 18, "atamacı iç notu");
        kaydet(a, sivas, 20, 12, 18, "ufuk dışı"); // ufuk 7 gün
        kaydet(b, v.eskisehir, 1, 12, 18, "başkasının notu");
        Tarayici ta = makinistGirisli(a, "D001");
        Tarayici tb = makinistGirisli(b, "D002");

        String govde = ta.get("/api/benim/konaklama").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].gar").value("Sivas"))
                .andExpect(jsonPath("$[0].yerTipi").value("OTEL")).andExpect(jsonPath("$[0].gece").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(govde).doesNotContain("iç notu").doesNotContain("notu").doesNotContain("Eskişehir").doesNotContain("makinistId");
        String govdeB = tb.get("/api/benim/konaklama").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].gar").value("Eskişehir")).andReturn().getResponse().getContentAsString();
        assertThat(govdeB).doesNotContain("Sivas").doesNotContain("başkasının notu").doesNotContain("D001");
    }

    @Test
    void sureDevamEdenKonaklamaGorunur_gecmisBitmisGorunmez() throws Exception {
        var v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        long sivas = TestTanimlari.gar(jdbc, "Sivas");
        kaydet(a, sivas, -1, 20, 30, null); // dün 20:00'de girdi, bugün 02:00'ye kadar: bugünle kesişir
        kaydet(a, sivas, -5, 10, 5, null); // bitmiş

        makinistGirisli(a, "D001").get("/api/benim/konaklama").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].gece").value(2));
    }

    @Test
    void atamaciYoneticiAnonim_erisemez_askidaOkur_pasifOkuyamaz() throws Exception {
        var v = new AtamaVeri(jdbc);
        long a = v.makinist("D001");
        Tarayici t = makinistGirisli(a, "D001");

        atamaci().get("/api/benim/konaklama").andExpect(status().isForbidden());
        yonetici().get("/api/benim/konaklama").andExpect(status().isForbidden());
        new Tarayici(mvc).get("/api/benim/konaklama").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", a);
        t.get("/api/benim/konaklama").andExpect(status().isOk());
        jdbc.update("UPDATE makinist SET durum = 'PASIF' WHERE id = ?", a);
        t.get("/api/benim/konaklama").andExpect(status().isForbidden());
    }
}
