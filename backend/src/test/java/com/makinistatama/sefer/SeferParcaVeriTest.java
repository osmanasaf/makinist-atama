package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

/** F2c V23: parça sütunları, parça-farkında tekillik, CHECK'ler ve elle oluşturma/güncelleme tekilliği (MK-59, MK-60). */
class SeferParcaVeriTest extends TestVeritabani {

    static final ZoneId IST = ZoneId.of("Europe/Istanbul");
    static final LocalDate GUN12 = LocalDate.of(2026, 10, 12);
    static final LocalDate GUN13 = LocalDate.of(2026, 10, 13);

    long ankara;
    long sivas;
    long kars;
    long hat;
    long yht;

    @BeforeEach
    void tanimlar() {
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        kars = TestTanimlari.gar(jdbc, "Kars");
        hat = TestTanimlari.hat(jdbc, "Ankara–Kars");
        yht = TestTanimlari.aracTipi(jdbc, "YHT");
    }

    /** Ham INSERT; {@code kalkis}/{@code varis} yerel zamanlar (İstanbul). Parça sütunları null olabilir. */
    long ekle(String tren, LocalDate tarih, LocalDateTime kalkis, LocalDateTime varis, Integer parcaNo, Integer parcaSayisi, LocalDate trenGunu) {
        return jdbc.queryForObject("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km, parca_no, parca_sayisi, tren_gunu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250, ?, ?, ?) RETURNING id""", Long.class, tren, tarih, ankara, kars,
                Timestamp.from(kalkis.atZone(IST).toInstant()), Timestamp.from(varis.atZone(IST).toInstant()), yht, hat, parcaNo, parcaSayisi, trenGunu);
    }

    /** 12.10 20:00 -> 13.10 02:30 (parça 1) ve 13.10 02:40 -> 09:10 (parça 2); zincir 12.10. */
    void gunAsanZincir(String tren) {
        ekle(tren, GUN12, GUN12.atTime(20, 0), GUN13.atTime(2, 30), 1, 2, GUN12);
        ekle(tren, GUN13, GUN13.atTime(2, 40), GUN13.atTime(9, 10), 2, 2, GUN12);
    }

    @Test
    void ayniTarihTrenAltindaIkiParca_eklenir_sirayaBakmaz() {
        ekle("DE-1601", GUN13, GUN13.atTime(2, 40), GUN13.atTime(9, 10), 2, 2, GUN12);
        ekle("DE-1601", GUN13, GUN13.atTime(1, 0), GUN13.atTime(2, 30), 1, 2, GUN13);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE tren_no = 'DE-1601'", Integer.class)).isEqualTo(2);
    }

    @Test
    void ayniTarihTrenParca_ikinciKezReddedilir() {
        ekle("DE-1601", GUN13, GUN13.atTime(2, 40), GUN13.atTime(9, 10), 2, 2, GUN12);

        assertThatThrownBy(() -> ekle("DE-1601", GUN13, GUN13.atTime(3, 0), GUN13.atTime(9, 0), 2, 2, GUN13)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ayniZincirdeAyniParca_baskaTarihleReddedilir() {
        gunAsanZincir("DE-1601");

        assertThatThrownBy(() -> ekle("DE-1601", GUN13, GUN13.atTime(5, 0), GUN13.atTime(6, 0), 1, 2, GUN12)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void parcasizVeParcali_ayniGunTren_birlikteVar_amaParcasizlarTekil() {
        gunAsanZincir("DE-1601"); // 13.10 parça 2
        ekle("DE-1601", GUN13, GUN13.atTime(20, 0), GUN13.atTime(23, 0), null, null, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE tarih = '2026-10-13' AND tren_no = 'DE-1601'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> ekle("DE-1601", GUN13, GUN13.atTime(21, 0), GUN13.atTime(23, 30), null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkIhlalleri_reddedilir() {
        LocalDateTime k = GUN12.atTime(8, 0);
        LocalDateTime v = GUN12.atTime(9, 0);
        assertThatThrownBy(() -> ekle("T1", GUN12, k, v, 3, 2, GUN12)).isInstanceOf(DataIntegrityViolationException.class); // no > sayısı
        assertThatThrownBy(() -> ekle("T2", GUN12, k, v, 1, 1, GUN12)).isInstanceOf(DataIntegrityViolationException.class); // sayısı = 1
        assertThatThrownBy(() -> ekle("T3", GUN12, k, v, 1, 7, GUN12)).isInstanceOf(DataIntegrityViolationException.class); // sayısı > 6
        assertThatThrownBy(() -> ekle("T4", GUN12, k, v, 1, null, GUN12)).isInstanceOf(DataIntegrityViolationException.class); // yalnız biri dolu
        assertThatThrownBy(() -> ekle("T5", GUN12, k, v, null, 2, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> ekle("T6", GUN12, k, v, 1, 2, null)).isInstanceOf(DataIntegrityViolationException.class); // tren_gunu eksik
        assertThatThrownBy(() -> ekle("T7", GUN12, k, v, 0, 2, GUN12)).isInstanceOf(DataIntegrityViolationException.class); // no = 0
    }

    // ------------------------------------------------------------------ T1.4: elle oluştur / güncelle (MK-60)

    String govde(String tren, String tarih, String kalkis, String varis, long versiyon) {
        return """
                {"trenNo":"%s","tarih":"%s","kalkisGarId":%d,"kalkisSaat":"%s","varisGarId":%d,"varisSaat":"%s","aracTipiId":%d,"hatId":%d,"km":245,"versiyon":%d}"""
                .formatted(tren, tarih, ankara, kalkis, kars, varis, yht, hat, versiyon);
    }

    @Test
    void elleParcasizSefer_gunAsanZincirinParca2siyleCakismaz_amaKendiTekrariReddedilir() throws Exception {
        gunAsanZincir("DE-1601"); // 13.10 parça 2 var (tren günü 12.10)
        var t = atamaci();

        t.post("/api/sefer", govde("DE-1601", "2026-10-13", "20:00", "23:00", 0)).andExpect(status().isCreated());
        t.post("/api/sefer", govde("DE-1601", "2026-10-13", "21:00", "23:30", 0)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_TEKRARI"));
    }

    @Test
    void elleParcasizSefer_parcaliTrenin_ayniTrenGunune_eklenemez() throws Exception {
        gunAsanZincir("DE-1601"); // zincir 12.10: parça 1 tarih 12.10

        atamaci().post("/api/sefer", govde("DE-1601", "2026-10-12", "08:00", "10:00", 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("SEFER_TEKRARI"));
    }

    @Test
    void parcaliSeferde_trenNoDegisimi_400_digerGuncellemeSerbest() throws Exception {
        gunAsanZincir("DE-1601");
        long id = jdbc.queryForObject("SELECT id FROM sefer WHERE parca_no = 2", Long.class);
        var t = atamaci();

        t.put("/api/sefer/" + id, govde("DE-9999", "2026-10-13", "02:40", "09:10", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.trenNo").value(containsString("Parçalı seferin tren no")));
        t.put("/api/sefer/" + id, govde("DE-1601", "2026-10-13", "02:45", "09:10", 0)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT parca_no || '/' || parca_sayisi || '/' || tren_gunu FROM sefer WHERE id = ?", String.class, id)).isEqualTo("2/2/2026-10-12");
    }
}
