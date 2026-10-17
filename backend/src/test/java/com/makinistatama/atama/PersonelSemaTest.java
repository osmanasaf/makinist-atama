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

/** F3b MK-82/86/87 (V26): tür sütunu varsayılanı ve CHECK'i, KONDUKTOR rolü seferde en çok bir, kondüktör ayarı tohumu. */
class PersonelSemaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    long sefer;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        sefer = v.sefer("E1", GUN, "08:00", "09:30");
    }

    @Test
    void turVarsayilanMakinist_checkYanlisDegeriReddeder() {
        long m = v.makinist("D001");
        long k = v.konduktor("K001");
        assertThat(jdbc.queryForObject("SELECT tur FROM makinist WHERE id = ?", String.class, m)).isEqualTo("MAKINIST");
        assertThat(jdbc.queryForObject("SELECT tur FROM makinist WHERE id = ?", String.class, k)).isEqualTo("KONDUKTOR");
        assertThatThrownBy(() -> jdbc.update("UPDATE makinist SET tur = 'MANEVRACI' WHERE id = ?", m)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void konduktorRoluSefereBirTane_asilVeYardimciyla_ayriSatir() {
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long k1 = v.konduktor("K001");
        long k2 = v.konduktor("K002");
        v.atama(sefer, a, "ASIL");
        v.atama(sefer, b, "YARDIMCI");
        v.atama(sefer, k1, "KONDUKTOR");
        assertThatThrownBy(() -> v.atama(sefer, k2, "KONDUKTOR")).isInstanceOf(DataIntegrityViolationException.class); // ikinci kondüktör
        assertThatThrownBy(() -> v.atama(sefer, k1, "YOLCU")).isInstanceOf(DataIntegrityViolationException.class); // aynı kişi iki satır
        long yolcu = v.konduktor("K003");
        v.atama(sefer, yolcu, "YOLCU"); // kondüktör yolcu olarak da gidebilir
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, sefer)).isEqualTo(4);
    }

    @Test
    void kondukturAyariTohumlu_varsayilanKapali() {
        List<Map<String, Object>> satir = jdbc.queryForList("SELECT deger, alt, ust, aciklama FROM uygulama_ayar WHERE anahtar = 'konduktor.yolcu_trenine_gerekli'");
        assertThat(satir).hasSize(1);
        assertThat(((Number) satir.get(0).get("deger")).intValue()).isZero();
        assertThat(((Number) satir.get(0).get("alt")).intValue()).isZero();
        assertThat(((Number) satir.get(0).get("ust")).intValue()).isEqualTo(1);
        assertThat((String) satir.get(0).get("aciklama")).isNotBlank();
    }
}
