package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Serbest intikal (MK-105, NFR-03): makinistin intikali (id 1 ve 2; {@code atamaId = null}, {@code seferId = -1/-2}) varken takas değerlendirmesi ve onayı doğru çalışır. Takas
 * sentetik aday satırı {@code atamaId = -1} üretir; aynı yükte kimlik çakışması olsaydı intikal görevi yanlış görev olarak okunur, K-20/K-10 sapıtırdı.
 */
class TakasIntikalTest extends TakasTabani {

    @Test
    void intikalliMakinistteKarsilikliTakas_onaylanir_kimseKirmiziOlmaz() throws Exception {
        Sahne s = sahne();
        // A (D001), TA'dan ÖNCEKİ günde Ankara -> Eskişehir -> Ankara intikalleri yapar: konumu hep Ankara'da biter
        v.intikal(s.a(), v.ankara, v.eskisehir, gun(2), "10:00", "11:00");
        v.intikal(s.a(), v.eskisehir, v.ankara, gun(2), "12:00", "13:00");
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));

        assertThat(jdbc.queryForObject("SELECT m.sicil FROM atama a JOIN makinist m ON m.id = a.makinist_id WHERE a.sefer_id = ?", String.class, s.sb())).isEqualTo("D001");
        atamaci().get("/api/atama/kirmizi-sayisi?bas=" + gun(0) + "&bit=" + gun(8)).andExpect(jsonPath("$.kirmiziAtama").value(0)).andExpect(jsonPath("$.kirmiziIntikal").value(0));
    }
}
