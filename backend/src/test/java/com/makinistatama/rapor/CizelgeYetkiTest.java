package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F5 / AK-5.3 / OK-35 / MK-177: aylık çizelge yalnız personel (atamacı, yönetici) içindir. Makinist kendi ızgarasını da GÖRMEZ (kendi görevleri {@code /api/benim/gorevler}'dedir; çizelge başkalarının
 * satırını ve toplamlarını taşır, OK-35 ortalama/fark gizliliği); anonim 401. Salt okuma: POST/PUT/DELETE tanımsız (405).
 */
class CizelgeYetkiTest extends TestVeritabani {

    @Test
    void personelOkur_makinist403_anonim401() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        v.makinist("D002");
        atamaci().get("/api/rapor/cizelge?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.satirlar.length()").value(2));
        yonetici().get("/api/rapor/cizelge?ay=2026-10").andExpect(status().isOk());
        Tarayici makinist = makinistGirisli(m, "D001");
        makinist.get("/api/rapor/cizelge?ay=2026-10").andExpect(status().isForbidden());
        makinist.get("/api/rapor/cizelge?ay=2026-10&garId=" + v.ankara + "&kontrol=true").andExpect(status().isForbidden());
        new Tarayici(mvc).get("/api/rapor/cizelge?ay=2026-10").andExpect(status().isUnauthorized());
    }

    @Test
    void yazmaYontemleriYok_saltOkunur() throws Exception {
        Tarayici t = atamaci();
        for (var sonuc : new org.springframework.test.web.servlet.ResultActions[] { t.post("/api/rapor/cizelge", "{}"), t.put("/api/rapor/cizelge", "{}"), t.delete("/api/rapor/cizelge") }) {
            int kod = sonuc.andReturn().getResponse().getStatus();
            assertThat(kod).isIn(404, 405);
        }
    }
}
