package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.TestVeritabani;

/** F2b MK-38 (V20): bir seferde birden çok YOLCU olur; ASIL/YARDIMCI hâlâ rol başına tek; aynı makinist aynı seferde tek satır; rol CHECK'i; yolculuk ayarları. */
class YolculukSemaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    long sefer;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        sefer = v.sefer("E1", GUN, "08:00", "09:30");
    }

    @Test
    void kisitlar() {
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long c = v.makinist("D003");
        long d = v.makinist("D004");

        v.atama(sefer, a, "YOLCU");
        v.atama(sefer, b, "YOLCU"); // aynı seferde ikinci YOLCU geçer
        v.atama(sefer, c, "ASIL");
        v.atama(sefer, d, "YARDIMCI");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND rol = 'YOLCU'", Integer.class, sefer)).isEqualTo(2);

        long e = v.makinist("D005");
        assertThatThrownBy(() -> v.atama(sefer, e, "ASIL")).isInstanceOf(DataIntegrityViolationException.class); // ikinci ASIL
        assertThatThrownBy(() -> v.atama(sefer, e, "YARDIMCI")).isInstanceOf(DataIntegrityViolationException.class); // ikinci YARDIMCI
        assertThatThrownBy(() -> v.atama(sefer, a, "YOLCU")).isInstanceOf(DataIntegrityViolationException.class); // aynı makinist iki kez
        assertThatThrownBy(() -> v.atama(sefer, c, "YOLCU")).isInstanceOf(DataIntegrityViolationException.class); // ASIL makinist aynı seferde yolcu da olamaz
        assertThatThrownBy(() -> v.atama(sefer, e, "X")).isInstanceOf(DataIntegrityViolationException.class); // rol CHECK
    }

    @Test
    void yolculukAyarlariTohumlu_altUstIle() {
        Map<String, Object[]> beklenen = Map.of(
                "yolculuk.calisma_sayilir", new Object[] { 1, 0, 1 },
                "yolculuk.yuk_trenine_binilir", new Object[] { 0, 0, 1 },
                "yolculuk.aktarma_dk", new Object[] { 15, 0, 240 },
                "yolculuk.arama_saat", new Object[] { 12, 1, 48 },
                "yolculuk.oksuz_saat", new Object[] { 24, 1, 168 });
        beklenen.forEach((anahtar, b) -> {
            List<Map<String, Object>> satir = jdbc.queryForList("SELECT deger, alt, ust FROM uygulama_ayar WHERE anahtar = ?", anahtar);
            assertThat(satir).as(anahtar).hasSize(1);
            assertThat(((Number) satir.getFirst().get("deger")).intValue()).as(anahtar).isEqualTo(b[0]);
            assertThat(((Number) satir.getFirst().get("alt")).intValue()).as(anahtar).isEqualTo(b[1]);
            assertThat(((Number) satir.getFirst().get("ust")).intValue()).as(anahtar).isEqualTo(b[2]);
        });
    }
}
