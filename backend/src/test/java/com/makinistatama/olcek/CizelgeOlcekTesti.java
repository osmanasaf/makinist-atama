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
 * F5 / AK-5.4 / MK-177: 300 makinist × 31 gün, ~6.000 atama + ~600 izin günü: {@code /api/rapor/cizelge} ≤ 2 sn ve JSON ≤ 800 KB. {@code kontrol=true} (canlı kırmızı hesabı) ayrıca ölçülür ve
 * yazdırılır; varsayılanı zaten {@code false}'tur (aşım durumunda yönteme göre karar 04-gelistirme.md "Tur 4"). Sürelerin medyanı (5 koşu, 2 ısınma).
 * Çalıştırma: {@code mvnw test -Pyavas "-Dtest=CizelgeOlcekTesti"}.
 */
@Tag("yavas")
class CizelgeOlcekTesti extends TestVeritabani {

    static final int MAKINIST = 300;
    static final int ATAMA_BASINA = 20;
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

    private long[] sure(Tarayici t, String yol) {
        try {
            long b = System.nanoTime();
            String govde = t.get(yol).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            return new long[] { (System.nanoTime() - b) / 1_000_000, govde.getBytes(java.nio.charset.StandardCharsets.UTF_8).length };
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void ucYuzMakinist_otuzBirGun_altibinAtama_sureVeBoyut() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] makinistler = new long[MAKINIST];
        for (int i = 0; i < MAKINIST; i++) {
            makinistler[i] = v.makinist(String.format("M%03d", i));
        }
        // her makinist için ayda 20 ayrı güne bir sefer (kalkış saatleri dağıtık): toplam 6.000 sefer + atama
        List<Object[]> seferSatirlari = new ArrayList<>();
        for (int i = 0; i < MAKINIST; i++) {
            for (int g = 0; g < ATAMA_BASINA; g++) {
                LocalDate gun = EKIM.plusDays((g + i) % 31);
                int dk = 4 * 60 + ((i * 7 + g * 13) % 12) * 60;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                seferSatirlari.add(new Object[] { "T" + i + "-" + g, gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 150)), v.yht, v.hat });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", seferSatirlari);
        List<Long> seferler = jdbc.queryForList("SELECT id FROM sefer ORDER BY id", Long.class);
        List<Object[]> atamaSatirlari = new ArrayList<>();
        for (int i = 0; i < seferler.size(); i++) {
            atamaSatirlari.add(new Object[] { seferler.get(i), makinistler[i / ATAMA_BASINA] });
        }
        jdbc.batchUpdate("INSERT INTO atama (sefer_id, makinist_id, rol, atayan) VALUES (?, ?, 'ASIL', 'olcek')", atamaSatirlari);
        List<Object[]> izinler = new ArrayList<>();
        for (int i = 0; i < MAKINIST; i += 2) {
            izinler.add(new Object[] { makinistler[i], LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21) });
        }
        jdbc.batchUpdate("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, ?, ?, 'YILLIK', 'ONAY', 'olcek')", izinler);
        Tarayici t = atamaci();

        long[] varsayilan = {0, 0};
        long ms = medyan(() -> {
            long[] r = sure(t, "/api/rapor/cizelge?ay=2026-10");
            varsayilan[1] = r[1];
            return r[0];
        });
        long msKontrol = medyan(() -> sure(t, "/api/rapor/cizelge?ay=2026-10&kontrol=true")[0]);
        long msAdalet = medyan(() -> sure(t, "/api/rapor/adalet?ay=2026-10")[0]);
        long msGar = medyan(() -> sure(t, "/api/rapor/cizelge?ay=2026-10&garId=" + v.ankara)[0]);

        System.out.println("OLCEK cizelge: " + MAKINIST + " makinist, " + seferler.size() + " atama -> kontrol=false " + ms + " ms (" + varsayilan[1] / 1024 + " KB), kontrol=true " + msKontrol
                + " ms, adalet raporu (karşılaştırma) " + msAdalet + " ms, garId süzgeçli " + msGar + " ms");
        assertThat(ms).as("300 × 31 çizelge (AK-5.4)").isLessThanOrEqualTo(2000);
        assertThat(varsayilan[1]).as("JSON ≤ 800 KB (AK-5.4)").isLessThanOrEqualTo(800L * 1024);
    }
}
