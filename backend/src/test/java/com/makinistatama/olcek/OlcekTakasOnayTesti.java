package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * NFR-M06 / T8.7: 300 makinist, makinistin görünürlük ufku (bugün+2 .. bugün+6, 5 gün) x günde 100 sefer; karşılıklı takas akışında hedef listesi, açma ve ATAMACI ONAYI (iki yönlü kural kontrolü + çift taraflı yazım)
 * ≤ 2 sn. Çalıştırma: {@code mvnw test -Pyavas "-Dtest=OlcekTakasOnayTesti"}.
 */
@Tag("yavas")
class OlcekTakasOnayTesti extends TestVeritabani {

    static final int MAKINIST = 300;
    static final int GUNLUK_SEFER = 100;
    static final int GUN = 5;

    private long ms(long baslangicNs) {
        return (System.nanoTime() - baslangicNs) / 1_000_000;
    }

    private long atamaId(long makinist, int sira) {
        return jdbc.queryForObject("SELECT a.id FROM atama a JOIN sefer s ON s.id = a.sefer_id WHERE a.makinist_id = ? ORDER BY s.planlanan_kalkis OFFSET ? LIMIT 1", Long.class, makinist, sira);
    }

    @Test
    void ucyuzMakinistBesGun_takasHedeflerAcmaVeOnay_ikiSaniyedeAz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] makinistler = new long[MAKINIST];
        for (int i = 0; i < MAKINIST; i++) {
            makinistler[i] = v.makinist(String.format("M%03d", i));
        }
        List<Object[]> seferSatirlari = new ArrayList<>();
        for (int g = 2; g <= 1 + GUN; g++) { // bugün+2 .. bugün+6: ufuk içi ve 12 saatten uzak
            LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).plusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 6 * 60 + i * 6; // 06:00-15:54: gece penceresi (20:00-06:00) dışında; aksi hâlde hafta sınırına göre K-18 "2 ardışık gece haftası" haftanın gününe bağlı kırılırdı
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                seferSatirlari.add(new Object[] { String.format("T%03d", i), gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
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
        for (int i = 0; i < 4; i++) {
            kullanicilar.makinistHesabiAc(makinistler[i], String.format("M%03d", i), "Demo Makinist " + i, Tarayici.PAROLA);
            jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE makinist_id = ?", makinistler[i]);
        }
        Tarayici atamaci = atamaci();

        long toplamOnay = 0;
        for (int tur = 0; tur < 2; tur++) { // ilk tur ısınma
            int a = tur * 2;
            Tarayici ta = Tarayici.girisYapmis(mvc, String.format("m%03d", a), Tarayici.PAROLA);
            Tarayici tb = Tarayici.girisYapmis(mvc, String.format("m%03d", a + 1), Tarayici.PAROLA);
            long ataA = atamaId(makinistler[a], 0);
            long ataB = atamaId(makinistler[a + 1], 0);

            long t0 = System.nanoTime();
            ta.get("/api/benim/takas/hedefler?atamaId=" + ataA).andExpect(status().isOk());
            long hedefMs = ms(t0);
            t0 = System.nanoTime();
            var acma = ta.post("/api/benim/takas", "{\"atamaId\":" + ataA + ",\"hedefAtamaId\":" + ataB + ",\"neden\":\"IZIN\"}").andExpect(status().isCreated());
            long acMs = ms(t0);
            long talep = ((Number) JsonPath.read(acma.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
            tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
            t0 = System.nanoTime();
            atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());
            long onayMs = ms(t0);
            System.out.println("OLCEK takas tur " + tur + ": " + MAKINIST + " makinist, " + seferler.size() + " atama -> hedefler " + hedefMs + " ms, açma " + acMs + " ms, onay " + onayMs + " ms");
            toplamOnay = onayMs;
        }
        assertThat(toplamOnay).as("takas atamacı onayı (NFR-M06)").isLessThanOrEqualTo(2000);
    }
}
