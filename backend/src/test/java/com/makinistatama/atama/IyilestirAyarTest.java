package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;

/** F2d MK-69 (V25): iyileştirme bütçesi ayarları tohumlu, alt/üst sınırlı ve açıklamalı. */
class IyilestirAyarTest extends TestVeritabani {

    @Test
    void iyilestirAyarlariTohumlu_altUstIle() {
        Map<String, Object[]> beklenen = Map.of(
                "iyilestir.tur", new Object[] { 20, 1, 40 },
                "iyilestir.sure_sn", new Object[] { 15, 1, 60 });
        beklenen.forEach((anahtar, b) -> {
            List<Map<String, Object>> satir = jdbc.queryForList("SELECT deger, alt, ust, aciklama FROM uygulama_ayar WHERE anahtar = ?", anahtar);
            assertThat(satir).as(anahtar).hasSize(1);
            assertThat(((Number) satir.getFirst().get("deger")).intValue()).as(anahtar).isEqualTo(b[0]);
            assertThat(((Number) satir.getFirst().get("alt")).intValue()).as(anahtar).isEqualTo(b[1]);
            assertThat(((Number) satir.getFirst().get("ust")).intValue()).as(anahtar).isEqualTo(b[2]);
            assertThat((String) satir.getFirst().get("aciklama")).as(anahtar).isNotBlank();
        });
    }
}
