package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * F3b MK-92 (E2, FR-11): kondüktör kartları ve atamaları yüklüyken makinistin aday listesi ve otomatik atama çıktısı kondüktörsüz koşuyla BİREBİR aynıdır;
 * demo + otomatik + kondüktör koşularının ardından rol-tür SQL değişmezi 0'dır.
 */
@ActiveProfiles("dev")
class KonduktorEsdegerTest extends TestVeritabani {

    static final String DEGISMEZ = """
            SELECT count(*) FROM atama a JOIN makinist m ON m.id = a.makinist_id
            WHERE a.rol <> 'YOLCU' AND (a.rol = 'KONDUKTOR') <> (m.tur = 'KONDUKTOR')""";

    private String cikti(Tarayici t, long sefer, LocalDate bas) throws Exception {
        String adaylar = t.get("/api/atama/sefer/" + sefer + "/adaylar?rol=ASIL").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oto = t.post("/api/atama/otomatik", "{\"bas\":\"" + bas + "\",\"bit\":\"" + bas.plusDays(2) + "\",\"uygula\":false}").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return adaylar + "\n" + oto;
    }

    @Test
    void konduktorOrnegiYukluyken_makinistAdaylariVeOtomatigi_ayni_degismezSifir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        LocalDate bas = jdbc.queryForObject("SELECT min(tarih) + 1 FROM sefer", LocalDate.class);
        long sefer = jdbc.queryForObject("SELECT id FROM sefer WHERE tarih = ? ORDER BY planlanan_kalkis, id LIMIT 1", Long.class, bas);
        String once = cikti(y, sefer, bas);

        y.post("/api/demo/konduktor-ornegi", "").andExpect(status().isCreated());
        assertThat(cikti(y, sefer, bas)).isEqualTo(once);

        y.post("/api/atama/otomatik", "{\"bas\":\"" + bas + "\",\"bit\":\"" + bas.plusDays(2) + "\",\"uygula\":true}").andExpect(status().isOk());
        y.post("/api/atama/otomatik", "{\"bas\":\"" + bas + "\",\"bit\":\"" + bas.plusDays(2) + "\",\"uygula\":true,\"personelTuru\":\"KONDUKTOR\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject(DEGISMEZ, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'KONDUKTOR'", Integer.class)).isPositive();
    }
}
