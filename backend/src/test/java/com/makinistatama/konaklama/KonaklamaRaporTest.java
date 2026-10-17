package com.makinistatama.konaklama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F2b MK-44/FR-10: aylık gece raporu: gece = ceil(süre / 24 s), ay = giriş günü; makinist x gar; "kaydı eksik öneri"; Excel. */
class KonaklamaRaporTest extends TestVeritabani {

    AtamaVeri v;
    Tarayici t;
    long m;
    long sivas;
    long konya;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D020");
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        konya = TestTanimlari.gar(jdbc, "Konya");
        t = atamaci();
    }

    private void kaydet(long makinist, long gar, String giris, String cikis) throws Exception {
        t.post("/api/konaklama", "{\"makinistId\":" + makinist + ",\"garId\":" + gar + ",\"yerTipi\":\"OTEL\",\"giris\":\"" + giris + "\",\"cikis\":\"" + cikis + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void sivas18s30_1Gece_40s_2Gece_konya1_toplam4_garKirilimi() throws Exception {
        kaydet(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00"); // 18 s 30 dk: 1
        kaydet(m, sivas, "2026-10-20T08:00:00+03:00", "2026-10-22T00:00:00+03:00"); // 40 s: 2
        kaydet(m, konya, "2026-10-25T22:00:00+03:00", "2026-10-26T05:00:00+03:00"); // 1

        t.get("/api/konaklama/rapor?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.ay").value("2026-10")).andExpect(jsonPath("$.toplamGece").value(4))
                .andExpect(jsonPath("$.makinistler", hasSize(1))).andExpect(jsonPath("$.makinistler[0].sicil").value("D020")).andExpect(jsonPath("$.makinistler[0].gece").value(4))
                .andExpect(jsonPath("$.makinistler[0].konaklamaSayisi").value(3)).andExpect(jsonPath("$.makinistler[0].garlar", hasSize(2)))
                .andExpect(jsonPath("$.makinistler[0].garlar[0].gar").value("Konya")).andExpect(jsonPath("$.makinistler[0].garlar[0].gece").value(1))
                .andExpect(jsonPath("$.makinistler[0].garlar[1].gar").value("Sivas")).andExpect(jsonPath("$.makinistler[0].garlar[1].gece").value(3))
                .andExpect(jsonPath("$.makinistler[0].garlar[1].konaklamaSayisi").value(2));
    }

    @Test
    void ayGirisGununeGore_ekimSonuGirisliKayitEkimeYazilir_kasimaDegil() throws Exception {
        kaydet(m, sivas, "2026-10-31T22:00:00+03:00", "2026-11-01T08:00:00+03:00");

        t.get("/api/konaklama/rapor?ay=2026-10").andExpect(jsonPath("$.toplamGece").value(1));
        t.get("/api/konaklama/rapor?ay=2026-11").andExpect(jsonPath("$.toplamGece").value(0)).andExpect(jsonPath("$.makinistler", hasSize(0)));
    }

    @Test
    void kaydiEksikOneri_oneriVarKayitYoksa_sayilir_kaydedilinceSifirlanir() throws Exception {
        v.atama(v.sefer("A", LocalDate.of(2026, 10, 14), "06:00", "11:30", v.ankara, sivas), m, "ASIL");
        v.atama(v.sefer("B", LocalDate.of(2026, 10, 15), "06:00", "12:00", sivas, v.ankara), m, "ASIL");

        t.get("/api/konaklama/rapor?ay=2026-10").andExpect(jsonPath("$.toplamEksikOneri").value(1)).andExpect(jsonPath("$.makinistler[0].eksikOneri").value(1))
                .andExpect(jsonPath("$.makinistler[0].gece").value(0));

        kaydet(m, sivas, "2026-10-14T12:00:00+03:00", "2026-10-15T05:00:00+03:00");
        t.get("/api/konaklama/rapor?ay=2026-10").andExpect(jsonPath("$.toplamEksikOneri").value(0)).andExpect(jsonPath("$.makinistler[0].eksikOneri").value(0))
                .andExpect(jsonPath("$.makinistler[0].gece").value(1));
    }

    @Test
    void excel_200_xlsx_dosyasi() throws Exception {
        kaydet(m, sivas, "2026-10-14T11:30:00+03:00", "2026-10-15T06:00:00+03:00");

        byte[] icerik = t.get("/api/konaklama/rapor/excel?ay=2026-10").andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.containsString("konaklama-raporu-2026-10.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(icerik).hasSizeGreaterThan(1000);
        assertThat(new String(icerik, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK"); // xlsx = zip
    }
}
