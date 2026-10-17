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
 * NFR-01: 300 makinist, 6 aylık atama geçmişi (günde 100 sefer = 18.000 atama), bir sefer için aday listesi + nedenler ≤ 1 sn.
 * Çalıştırma: {@code mvnw test -Pyavas "-Dtest=AdayListesiOlcekTesti"}.
 */
@Tag("yavas")
class AdayListesiOlcekTesti extends TestVeritabani {

    static final int MAKINIST = 300;
    static final int GUN = 180;
    static final int GUNLUK_SEFER = 100;
    static final LocalDate ILK = LocalDate.of(2026, 4, 15);

    @Test
    void ucyuzMakinist_altiAyGecmis_adayListesi_birSaniyedeAz() throws Exception {
        olc(false);
    }

    /** T3.5 (NFR-01): aynı yük, 10 açık özel kuralla (tek sefer, gün, hafta, ay, yıl, kayan 30, seri, izin günü, görevsiz aralık, nöbetler arası dinlenme). */
    @Test
    void ucyuzMakinist_altiAyGecmis_onOzelKuralla_adayListesi_birSaniyedeAz() throws Exception {
        olc(true);
    }

    private void olc(boolean ozelKurallar) throws Exception {
        if (ozelKurallar) {
            OlcekKurallari.yaz(jdbc);
        }
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
        long aday = v.sefer("ADAY", LocalDate.of(2026, 10, 12), "08:00", "10:00");
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + aday + "/adaylar").andExpect(status().isOk()); // ısınma (JIT, bağlantı havuzu)
        long baslangic = System.nanoTime();
        t.get("/api/atama/sefer/" + aday + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(MAKINIST));
        long ms = (System.nanoTime() - baslangic) / 1_000_000;

        System.out.println("OLCEK adaylar (" + (ozelKurallar ? "10 özel kural" : "özel kural yok") + "): " + MAKINIST + " makinist, " + seferler.size() + " atama -> " + ms + " ms");
        assertThat(ms).as("300 aday + nedenler (NFR-01)").isLessThanOrEqualTo(1000);
    }
}
