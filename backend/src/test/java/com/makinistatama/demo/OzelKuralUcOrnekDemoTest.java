package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * Kullanıcının üç örneği AYNI ANDA, demo verisiyle ve otomatik atama UYGULANARAK (QA Tur 2): yazma anı kontrolü (MK-5) uçtan uca, kuralın bağımsız bir
 * hesapla da tutulduğu. Yavaş (~30-50 sn): {@code mvnw test -Pyavas "-Dtest=OzelKuralUcOrnekDemoTest"}.
 * Bağımsız kontrol (oracle): gece nöbeti = aralarındaki boşluk 4 s'ten kısa görevler tek nöbet, gece (20:00-06:00) kesişimi nöbet süresinin yarısından
 * fazlaysa gece nöbeti; seri = gecenin BAŞLADIĞI takvim günlerinin aralıksız dizisi (gece yarısından sonra başlayan nöbet önceki geceye yazılır: başlangıç − 06:00).
 */
@Tag("yavas")
@ActiveProfiles("dev")
class OzelKuralUcOrnekDemoTest extends TestVeritabani {

    static final ZoneId TR = ZoneId.of("Europe/Istanbul");
    static final long NOBET_AYIRMA_DK = 4 * 60;
    static final int GUN = 28;

    @Test
    void ucOrnekBirlikte_demoVerisiOtomatikAtama_kirmiziYok_geceSerisiEnFazla3_izinEnAz8() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        LocalDate bas = LocalDate.now().plusDays(1);
        LocalDate bit = bas.plusDays(GUN - 1);
        String aralik = "{\"bas\":\"" + bas + "\",\"bit\":\"" + bit + "\",\"uygula\":true}";

        // duyarlılık: kuralsız plan gece serisini 3'ün üstüne çıkarıyor (aksi hâlde aşağıdaki doğrulama bir şey kanıtlamaz)
        y.post("/api/atama/otomatik", aralik).andExpect(status().isOk());
        assertThat(enUzunGeceSerisi()).as("kuralsız demo planında 4+ günlük gece serisi var").isGreaterThan(3);
        jdbc.update("DELETE FROM atama");

        long dizel = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'ARAC_TIPI' AND ad ILIKE 'Dizel%'", Long.class);
        y.post("/api/ozel-kural", govde("Gece nöbeti üst üste 3 gün", "ARDISIK_GECE_GUNU", "SERI", "EN_FAZLA", "3", "{}")).andExpect(status().isCreated());
        y.post("/api/ozel-kural", govde("Ayda en az 8 gün izin", "IZIN_GUNU", "AY", "EN_AZ", "8", "{}")).andExpect(status().isCreated());
        y.post("/api/ozel-kural", govde("Dizel sefer 8 saat", "TEK_SEFER_SURESI", "SEFER", "EN_FAZLA", "8", "{\"aracTipleri\":[" + dizel + "]}")).andExpect(status().isCreated());

        y.post("/api/atama/otomatik", aralik).andExpect(status().isOk()).andExpect(jsonPath("$.uygulandi").value(true));

        y.get("/api/atama/kirmizi-sayisi?bas=" + bas + "&bit=" + bit).andExpect(status().isOk()).andExpect(jsonPath("$.kirmiziAtama").value(0));
        assertThat(enUzunGeceSerisi()).as("özel kural açıkken gece nöbeti serisi").isLessThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).as("kural planı boşaltmamalı").isGreaterThan(GUN * 90 * 8 / 10);
    }

    private static String govde(String ad, String olcu, String pencere, String kars, String esik, String kapsam) {
        return "{\"ad\":\"" + ad + "\",\"olcu\":\"" + olcu + "\",\"pencere\":\"" + pencere + "\",\"karsilastirma\":\"" + kars + "\",\"esik\":" + esik + ",\"kapsam\":" + kapsam
                + ",\"siddet\":\"ENGEL\",\"gerekce\":\"QA\",\"gecerlilikBas\":\"2026-01-01\"}";
    }

    /** Veritabanındaki atamalardan makinist başına en uzun gece nöbeti serisi (gün). */
    private int enUzunGeceSerisi() {
        Map<Long, List<long[]>> gorevler = new TreeMap<>();
        jdbc.query("SELECT a.makinist_id, s.planlanan_kalkis, s.planlanan_varis FROM atama a JOIN sefer s ON s.id = a.sefer_id ORDER BY 1, 2", rs -> {
            gorevler.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new long[] { rs.getTimestamp(2).toInstant().getEpochSecond() / 60, rs.getTimestamp(3).toInstant().getEpochSecond() / 60 });
        });
        int en = 0;
        for (List<long[]> liste : gorevler.values()) {
            Set<LocalDate> geceGunleri = new TreeSet<>();
            long nBas = -1;
            long nBit = 0;
            long sure = 0;
            long gece = 0;
            for (long[] g : liste) {
                if (nBas >= 0 && g[0] - nBit >= NOBET_AYIRMA_DK) {
                    nobetiKapat(geceGunleri, nBas, sure, gece);
                    nBas = -1;
                }
                if (nBas < 0) {
                    nBas = g[0];
                    nBit = g[1];
                    sure = 0;
                    gece = 0;
                }
                nBit = Math.max(nBit, g[1]);
                sure += g[1] - g[0];
                gece += geceDakika(g[0], g[1]);
            }
            if (nBas >= 0) {
                nobetiKapat(geceGunleri, nBas, sure, gece);
            }
            int seri = 0;
            LocalDate onceki = null;
            for (LocalDate g : geceGunleri) {
                seri = onceki != null && g.equals(onceki.plusDays(1)) ? seri + 1 : 1;
                onceki = g;
                en = Math.max(en, seri);
            }
        }
        return en;
    }

    private static void nobetiKapat(Set<LocalDate> geceGunleri, long baslangicDk, long sure, long gece) {
        if (gece * 2 > sure) {
            geceGunleri.add(Instant.ofEpochSecond(baslangicDk * 60 - 6 * 3600).atZone(TR).toLocalDate()); // gecenin başladığı gün: başlangıç − 06:00 (W4)
        }
    }

    /** [kalkis, varis) dakikasının 20:00-06:00 penceresiyle kesişimi. */
    private static long geceDakika(long kalkis, long varis) {
        long toplam = 0;
        LocalDate ilk = Instant.ofEpochSecond(kalkis * 60).atZone(TR).toLocalDate().minusDays(1);
        LocalDate son = Instant.ofEpochSecond(varis * 60).atZone(TR).toLocalDate();
        Set<LocalDate> gunler = new HashSet<>();
        for (LocalDate d = ilk; !d.isAfter(son); d = d.plusDays(1)) {
            gunler.add(d);
        }
        for (LocalDate d : gunler) { // d günü 20:00 -> d+1 günü 06:00
            long a = d.atTime(20, 0).atZone(TR).toEpochSecond() / 60;
            long b = d.plusDays(1).atTime(6, 0).atZone(TR).toEpochSecond() / 60;
            toplam += Math.max(0, Math.min(varis, b) - Math.max(kalkis, a));
        }
        return toplam;
    }
}
