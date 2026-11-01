package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.makinistatama.TestVeritabani;

/**
 * QA tur 2 Gözlem-2: PostgreSQL hazırlanmış ifadeyi ~5. çalıştırmadan sonra GENEL plana geçirir. {@code atama ⨝ sefer} sorgusunda (3.000+ parametreli {@code makinist_id in (...)}, tarih aralığı)
 * genel plan sefer'i dıştan gezip her sefer için diziyi tarar (EXPLAIN ANALYZE: özel plan ~9 ms, genel plan ~160 ms; uygulamada ~900 ms). Plan seçimi veriye bağlı olduğundan davranış değil
 * mekanizma sınanır: satır kaynağı sorguyu bu işlemde ÖZEL planla çalıştırır, işlem bitince ayar kendiliğinden geri döner (SET LOCAL).
 */
class SatirKaynagiPlanTest extends TestVeritabani {

    @Autowired
    SatirKaynagi kaynak;

    @Autowired
    TransactionTemplate islem;

    @Test
    void aralik_islemIcindeOzelPlanZorlar_islemSonundaGeriDoner() {
        long m = new AtamaVeri(jdbc).makinist("P001");
        LocalDate gun = LocalDate.of(2026, 10, 12);

        String icerde = islem.execute(s -> {
            kaynak.aralik(List.of(m), gun, gun);
            return jdbc.queryForObject("select current_setting('plan_cache_mode')", String.class);
        });

        assertThat(icerde).isEqualTo("force_custom_plan");
        assertThat(jdbc.queryForObject("select current_setting('plan_cache_mode')", String.class)).isEqualTo("auto");
    }
}
