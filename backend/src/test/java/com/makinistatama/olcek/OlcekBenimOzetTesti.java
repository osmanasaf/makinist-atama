package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * NFR-M06 / MK-30: 300 makinist x bu ayın tüm günleri (günde 100 sefer) için makinistin aylık özeti ({@code rapor(ay)} sonucundan kendi satırı) ≤ 1 sn.
 * Aşılırsa MK-30 sırası: önce 60 sn tek-giriş önbellek, sonra tek satır hesabı. Çalıştırma: {@code mvnw test -Pyavas "-Dtest=OlcekBenimOzetTesti"}.
 */
@Tag("yavas")
class OlcekBenimOzetTesti extends TestVeritabani {

    static final int MAKINIST = 300;
    static final int GUNLUK_SEFER = 100;

    @Test
    void ucyuzMakinist_ayinTumGunleri_benimOzet_birSaniyedeAz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] makinistler = new long[MAKINIST];
        for (int i = 0; i < MAKINIST; i++) {
            makinistler[i] = v.makinist(String.format("M%03d", i));
        }
        YearMonth ay = YearMonth.now(ZamanAyari.BOLGE);
        List<Object[]> seferSatirlari = new ArrayList<>();
        for (int g = 1; g <= ay.lengthOfMonth(); g++) {
            LocalDate gun = ay.atDay(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                seferSatirlari.add(new Object[] { String.format("T%03d", i), gun, v.ankara, v.eskisehir, Timestamp.from(kalkis),
                        Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", seferSatirlari);
        List<Long> seferler = jdbc.queryForList("SELECT id FROM sefer ORDER BY id", Long.class);
        List<Object[]> atamaSatirlari = new ArrayList<>();
        for (int i = 0; i < seferler.size(); i++) {
            atamaSatirlari.add(new Object[] { seferler.get(i), makinistler[i % MAKINIST] });
        }
        jdbc.batchUpdate("INSERT INTO atama (sefer_id, makinist_id, rol, atayan) VALUES (?, ?, 'ASIL', 'olcek')", atamaSatirlari);
        Tarayici t = makinistGirisli(makinistler[0], "M000");

        t.get("/api/benim/ozet").andExpect(status().isOk()); // ısınma
        long baslangic = System.nanoTime();
        t.get("/api/benim/ozet").andExpect(status().isOk()).andExpect(jsonPath("$.adaleteDahil").value(true));
        long ms = (System.nanoTime() - baslangic) / 1_000_000;

        System.out.println("OLCEK benim/ozet: " + MAKINIST + " makinist, " + seferler.size() + " atama -> " + ms + " ms");
        assertThat(ms).as("makinist aylık özeti (NFR-M06)").isLessThanOrEqualTo(1000);
    }
}
