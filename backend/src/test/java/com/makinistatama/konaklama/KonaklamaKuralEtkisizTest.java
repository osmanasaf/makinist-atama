package com.makinistatama.konaklama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F2b MK-43: konaklama kural motorunu ETKİLEMEZ: kayıt ekleyip silmek aday listesini, gün listesini ve atama sonucunu değiştirmez. */
class KonaklamaKuralEtkisizTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    @Test
    void konaklamaEkleSil_adaylarVeGunListesiAynidir() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long sivas = TestTanimlari.gar(jdbc, "Sivas");
        long sefer = v.seferEv("S1", GUN, "08:00", "10:00");
        v.atama(v.seferEv("S0", GUN.minusDays(1), "08:00", "10:00"), m, "ASIL");
        Tarayici t = atamaci();

        String adaylarOnce = t.get("/api/atama/sefer/" + sefer + "/adaylar").andReturn().getResponse().getContentAsString();
        String gunOnce = t.get("/api/atama/gun?tarih=" + GUN).andReturn().getResponse().getContentAsString().replaceAll("\"uretimZamani\":\"[^\"]*\"", "");

        // görevle ÇAKIŞAN bir konaklama bile kural sonucunu değiştirmez
        t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + sivas + ",\"yerTipi\":\"OTEL\",\"giris\":\"2026-10-14T06:00:00+03:00\",\"cikis\":\"2026-10-14T23:00:00+03:00\"}")
                .andExpect(status().isCreated());

        String adaylarSonra = t.get("/api/atama/sefer/" + sefer + "/adaylar").andReturn().getResponse().getContentAsString();
        String gunSonra = t.get("/api/atama/gun?tarih=" + GUN).andReturn().getResponse().getContentAsString().replaceAll("\"uretimZamani\":\"[^\"]*\"", "");
        assertThat(adaylarSonra).isEqualTo(adaylarOnce);
        assertThat(gunSonra).isEqualTo(gunOnce);
        t.post("/api/atama/sefer/" + sefer, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

    }
}
