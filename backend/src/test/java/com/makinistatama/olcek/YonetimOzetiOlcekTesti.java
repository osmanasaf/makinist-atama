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
 * F4 / AK-4.8 / MK-180: bir aylık demo ölçeği (3.100 sefer, 68 makinist, ~2.800 atama) için {@code /api/rapor/ozet} ≤ 3 sn; denetim paketi Excel'i de ölçülür ve yazdırılır (bütçe yok). Sürelerin medyanı
 * (5 koşu, 2 ısınma). Çalıştırma: {@code mvnw test -Pyavas "-Dtest=YonetimOzetiOlcekTesti"}.
 */
@Tag("yavas")
class YonetimOzetiOlcekTesti extends TestVeritabani {

    static final int MAKINIST = 68;
    static final int SEFER = 3100;
    static final LocalDate EKIM = LocalDate.of(2026, 10, 1);

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

    private static long sure(Tarayici t, String yol) {
        try {
            long b = System.nanoTime();
            t.get(yol).andExpect(status().isOk());
            return (System.nanoTime() - b) / 1_000_000;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void ucBinYuzSefer_altmisSekizMakinist_birAy_ozetUcSaniyeyiAsmaz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] makinistler = new long[MAKINIST];
        for (int i = 0; i < MAKINIST; i++) {
            makinistler[i] = v.makinist(String.format("M%03d", i));
        }
        // 31 güne dağıtık 3.100 sefer (günde 100), her biri 2,5 saat; yüzde 90 atanır
        List<Object[]> seferSatirlari = new ArrayList<>();
        for (int i = 0; i < SEFER; i++) {
            LocalDate gun = EKIM.plusDays(i % 31);
            int dk = 4 * 60 + ((i / 31) % 16) * 60;
            var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
            seferSatirlari.add(new Object[] { "T" + i, gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 150)), v.yht, v.hat });
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", seferSatirlari);
        List<Long> seferler = jdbc.queryForList("SELECT id FROM sefer ORDER BY id", Long.class);
        List<Object[]> atamaSatirlari = new ArrayList<>();
        for (int i = 0; i < seferler.size(); i++) {
            if (i % 10 != 9) {
                atamaSatirlari.add(new Object[] { seferler.get(i), makinistler[i % MAKINIST] });
            }
        }
        jdbc.batchUpdate("INSERT INTO atama (sefer_id, makinist_id, rol, atayan) VALUES (?, ?, 'ASIL', 'olcek')", atamaSatirlari);
        Tarayici y = yonetici();

        long ms = medyan(() -> sure(y, "/api/rapor/ozet?ay=2026-10"));
        long msPaket = medyan(() -> sure(y, "/api/rapor/denetim-paketi/excel?ay=2026-10"));
        long msAdalet = medyan(() -> sure(y, "/api/rapor/adalet?ay=2026-10"));
        long msKirmizi = medyan(() -> sure(y, "/api/atama/kirmizi-sayisi?bas=2026-10-01&bit=2026-10-31"));

        System.out.println("OLCEK ozet: " + MAKINIST + " makinist, " + SEFER + " sefer, " + atamaSatirlari.size() + " atama -> ozet " + ms + " ms, denetim paketi " + msPaket
                + " ms (karşılaştırma: adalet " + msAdalet + " ms, kırmızı sayısı " + msKirmizi + " ms)");
        assertThat(ms).as("bir aylık özet (AK-4.8)").isLessThanOrEqualTo(3000);
    }
}
