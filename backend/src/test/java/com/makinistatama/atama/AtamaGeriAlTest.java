package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** OK-37: Geri al yarışı; {@code DELETE /api/atama/sefer/{id}?atamaId=} güncel atama o kimlik değilse 409 verir ve hiçbir şey silmez. */
class AtamaGeriAlTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);
    static final String SIL = "?rol=ASIL&neden=DIGER&nedenNotu=geri";

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private long atamaId(long sefer) {
        return jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, sefer);
    }

    @Test
    void atamaId_eslesirse_204_atamaKaldirilir() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.delete("/api/atama/sefer/" + s + SIL + "&atamaId=" + atamaId(s)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }

    @Test
    void arayaBaskaAtamaGirdi_atamaIdFarkli_409_dogruAtamaYerinde() throws Exception {
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated());
        long eskiId = atamaId(s);
        // başka atamacı rolü değiştirir: yeni satır, yeni kimlik
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk());
        long yeniId = atamaId(s);
        assertThat(yeniId).isNotEqualTo(eskiId);
        int kayitOnce = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);

        t.delete("/api/atama/sefer/" + s + SIL + "&atamaId=" + eskiId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("ATAMA_DEGISTI")).andExpect(jsonPath("$.detail").value(containsString("değiş")));

        assertThat(jdbc.queryForList("SELECT makinist_id FROM atama WHERE sefer_id = ?", Long.class, s)).containsExactly(m2);
        assertThat(atamaId(s)).isEqualTo(yeniId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(kayitOnce);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA_KALDIR'", Integer.class)).isZero();
    }

    @Test
    void atamaId_verildiAmaAtamaYok_409() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        long id = atamaId(s);
        t.delete("/api/atama/sefer/" + s + SIL).andExpect(status().isNoContent()); // başkası zaten kaldırdı

        t.delete("/api/atama/sefer/" + s + SIL + "&atamaId=" + id).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ATAMA_DEGISTI"));
    }

    @Test
    void atamaIdYoksa_davranisAyni_204() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.delete("/api/atama/sefer/" + s + SIL).andExpect(status().isNoContent());
    }

    /** B-03: sınıf {@code readOnly} olduğundan, yazan her herkese açık {@code kaldir} aşırı yüklemesi kendi {@code @Transactional}'ını taşımalı (yoksa DELETE salt-okunur işlemde patlar). */
    @Test
    void kaldirAsiriYuklemeleri_hepsiYazilabilirTransactional() {
        var kaldirlar = java.util.Arrays.stream(AtamaServisi.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("kaldir") && java.lang.reflect.Modifier.isPublic(m.getModifiers())).toList();
        assertThat(kaldirlar).hasSize(2); // 4 argümanlı (TakasServisi) ve 6 argümanlı (denetleyici); ölü 5 argümanlı yok
        for (var m : kaldirlar) {
            var tx = m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertThat(tx).as(m.toString()).isNotNull();
            assertThat(tx.readOnly()).as(m.toString()).isFalse();
        }
    }
}
