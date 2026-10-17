package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F2b NFR (V-P5): "Yolculuk öner" ucu aday listesinden AYRIDIR (aday listesi ölçeği etkilenmez); 31 günlük sefer yükünde (3.100 + 62 sefer, 40 makinist, makinistin 8 aylık
 * geçmişi) tek öneri çağrısı ≤ 200 ms. Çalıştırma: {@code mvnw test -Pyavas "-Dtest=YolculukOneriOlcekTesti"}.
 */
@Tag("yavas")
class YolculukOneriOlcekTesti extends TestVeritabani {

    static final LocalDate ILK = LocalDate.of(2026, 10, 12);

    @Test
    void tekOneriCagrisi_ikiYuzMilisaniyedeAz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        for (int i = 2; i <= 40; i++) {
            v.makinist(String.format("D%03d", i));
        }
        List<Object[]> satirlar = new ArrayList<>();
        for (int g = -240; g < 31; g++) { // 8 ay geçmiş + 31 gün
            LocalDate gun = ILK.plusDays(g);
            for (int i = 0; i < 100; i++) {
                int dk = 4 * 60 + i * 10;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                satirlar.add(new Object[] { String.format("T%03d", i), gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
            }
        }
        long hedefGun = 5;
        for (int g = 0; g < 31; g++) {
            LocalDate gun = ILK.plusDays(g);
            var k1 = gun.atTime(5, 0).atZone(AtamaVeri.ISTANBUL).toInstant();
            var k2 = gun.atTime(8, 0).atZone(AtamaVeri.ISTANBUL).toInstant();
            satirlar.add(new Object[] { "YA" + g, gun, v.ankara, v.eskisehir, Timestamp.from(k1), Timestamp.from(k1.plusSeconds(60 * 90)), v.yht, v.hat });
            satirlar.add(new Object[] { "YB" + g, gun, v.eskisehir, v.ankara, Timestamp.from(k2), Timestamp.from(k2.plusSeconds(60 * 90)), v.yht, v.hat });
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", satirlar);
        // makinistin geçmişi: her gün bir sefer (8 ay)
        jdbc.update("""
                INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk)
                SELECT id, ?, 'ASIL', 'test', 0 FROM sefer WHERE tren_no = 'T020' AND tarih < '2026-10-12'""", m);
        long hedef = jdbc.queryForObject("SELECT id FROM sefer WHERE tren_no = ?", Long.class, "YB" + hedefGun);
        Tarayici t = atamaci();
        String url = "/api/atama/sefer/" + hedef + "/yolculuk-oner?makinistId=" + m;

        t.get(url).andExpect(status().isOk()); // ısınma
        long b = System.nanoTime();
        t.get(url).andExpect(status().isOk()).andExpect(jsonPath("$.oneriler[0].trenNo").value("YA" + hedefGun));
        long ms = (System.nanoTime() - b) / 1_000_000;

        System.out.println("OLCEK yolculuk öneri ucu: tek çağrı " + ms + " ms (8 ay geçmiş + 31 gün, " + satirlar.size() + " sefer)");
        assertThat(ms).as("tek öneri çağrısı").isLessThanOrEqualTo(200);
    }
}
