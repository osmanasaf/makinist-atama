package com.makinistatama.konaklama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F2b V21: konaklama kısıtları (yer tipi, çıkış > giriş, FK) ve ayar tohumu. */
class KonaklamaSemaTest extends TestVeritabani {

    private void ekle(long makinist, long gar, String tip, String giris, String cikis) {
        jdbc.update("INSERT INTO konaklama (makinist_id, gar_id, yer_tipi, giris, cikis) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz)", makinist, gar, tip, giris, cikis);
    }

    @Test
    void kisitlar() {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");

        ekle(m, v.eskisehir, "OTEL", "2026-10-14T08:00:00Z", "2026-10-14T20:00:00Z");
        assertThatThrownBy(() -> ekle(m, v.eskisehir, "OTEL", "2026-10-14T08:00:00Z", "2026-10-14T08:00:00Z")).isInstanceOf(DataIntegrityViolationException.class); // çıkış = giriş
        assertThatThrownBy(() -> ekle(m, v.eskisehir, "OTEL", "2026-10-14T08:00:00Z", "2026-10-14T07:00:00Z")).isInstanceOf(DataIntegrityViolationException.class); // çıkış < giriş
        assertThatThrownBy(() -> ekle(m, v.eskisehir, "CADIR", "2026-10-14T08:00:00Z", "2026-10-14T20:00:00Z")).isInstanceOf(DataIntegrityViolationException.class); // yer tipi
        assertThatThrownBy(() -> ekle(999, v.eskisehir, "OTEL", "2026-10-14T08:00:00Z", "2026-10-14T20:00:00Z")).isInstanceOf(DataIntegrityViolationException.class); // FK makinist
        assertThatThrownBy(() -> ekle(m, 999, "OTEL", "2026-10-14T08:00:00Z", "2026-10-14T20:00:00Z")).isInstanceOf(DataIntegrityViolationException.class); // FK gar
        assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isEqualTo(1);
    }

    @Test
    void oneriAyariTohumlu_8Saat_alt1_ust24() {
        Map<String, Object> satir = jdbc.queryForMap("SELECT deger, alt, ust, birim FROM uygulama_ayar WHERE anahtar = 'konaklama.oneri_min_saat'");

        assertThat(((Number) satir.get("deger")).intValue()).isEqualTo(8);
        assertThat(((Number) satir.get("alt")).intValue()).isEqualTo(1);
        assertThat(((Number) satir.get("ust")).intValue()).isEqualTo(24);
        assertThat(satir.get("birim")).isEqualTo("saat");
    }
}
