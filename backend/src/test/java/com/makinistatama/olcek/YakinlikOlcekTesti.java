package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F3 / AK-3.9: günde 100 sefer, 68 makinist, 6 aylık atama geçmişi (18.000 atama). (a) aday paneli: yakınlık+yorgunluk ucu ≤ 300 ms ve {@code adaylar} süresinin ≤ 1,5 katı;
 * (b) masa: günün yakınlık ucu, aynı günün {@code /api/atama/gun} yanıtına EKLENEN maliyet olarak ölçülür (ölçüm 04-gelistirme.md "Tur 3"). Sürelerin medyanı (5 koşu, 2 ısınma).
 * Çalıştırma: {@code mvnw test -Pyavas "-Dtest=YakinlikOlcekTesti"}.
 */
@Tag("yavas")
class YakinlikOlcekTesti extends TestVeritabani {

    static final int MAKINIST = 68;
    static final int GUN = 180;
    static final int GUNLUK_SEFER = 100;
    static final LocalDate ILK = LocalDate.of(2026, 4, 15);

    private static long medyan(java.util.function.LongSupplier olcum) {
        long[] s = new long[5];
        for (int i = 0; i < 2; i++) {
            olcum.getAsLong();
        }
        for (int i = 0; i < s.length; i++) {
            s[i] = olcum.getAsLong();
        }
        Arrays.sort(s);
        return s[s.length / 2];
    }

    private long sure(Tarayici t, String yol) {
        try {
            long b = System.nanoTime();
            t.get(yol).andExpect(status().isOk());
            return (System.nanoTime() - b) / 1_000_000;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void altmisSekizMakinist_yuzSeferGunu_yakinlikMaliyeti() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] makinistler = new long[MAKINIST];
        for (int i = 0; i < MAKINIST; i++) {
            makinistler[i] = v.makinist(String.format("M%03d", i));
        }
        List<Object[]> seferSatirlari = new ArrayList<>();
        for (int g = 0; g < GUN; g++) {
            LocalDate gun = ILK.plusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                seferSatirlari.add(new Object[] { String.format("T%03d", i), gun, v.ankara, v.eskisehir, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
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
        long aday = v.sefer("ADAY", LocalDate.of(2026, 10, 12), "08:00", "10:00");
        LocalDate masaGunu = ILK.plusDays(100);
        Tarayici t = atamaci();

        long adaylar = medyan(() -> sure(t, "/api/atama/sefer/" + aday + "/adaylar"));
        long yakinlik = medyan(() -> sure(t, "/api/atama/sefer/" + aday + "/yakinlik"));
        long gun = medyan(() -> sure(t, "/api/atama/gun?tarih=" + masaGunu));
        long gunYakinlik = medyan(() -> sure(t, "/api/atama/yakinlik?tarih=" + masaGunu));

        System.out.println("OLCEK yakinlik: " + MAKINIST + " makinist, " + seferler.size() + " atama -> adaylar " + adaylar + " ms, sefer/yakinlik " + yakinlik + " ms; masa /gun " + gun
                + " ms, /yakinlik?tarih " + gunYakinlik + " ms");
        assertThat(yakinlik).as("68 aday yakınlık+yorgunluk (AK-3.9)").isLessThanOrEqualTo(300);
        assertThat(yakinlik).as("aday listesi süresinin ≤ 1,5 katı (AK-3.9)").isLessThanOrEqualTo(Math.round(adaylar * 1.5));
    }
}
