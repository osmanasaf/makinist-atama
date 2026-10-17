package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;

import tools.jackson.databind.json.JsonMapper;

/**
 * F2d T5.2 ortak ölçüm akışı (yavaş testler): aynı veride bugünkü plan, iyileştirmeli önizleme, anahtarlı yeniden oynatma, iki gerçek {@code uygula} (iyileştirmesiz, sonra
 * eklenen atamalar silinip iyileştirmeli anahtarlı) ve kırmızı sayısı. Kapılar {@link Esik}'tedir; ölçülen değerler {@code OLCEK-IYILESTIR} satırına yazılır.
 */
final class IyilestirOlcum {

    static final JsonMapper JSON = new JsonMapper();

    /** {@code bosTavan}: iyileştirmeli plan boş üst sınırı; {@code onizlemeMs}/{@code tekGunMs}: NFR-01; {@code stdOran}: NFR-02; {@code kilitOran}: NFR-03. */
    record Esik(int bosTavan, long onizlemeMs, long tekGunMs, double stdOran, double kilitOran) {
    }

    private IyilestirOlcum() {
    }

    private static String govde(String bas, String bit, boolean uygula, String ek) {
        return "{\"bas\":\"" + bas + "\",\"bit\":\"" + bit + "\",\"uygula\":" + uygula + ek + "}";
    }

    private record Olcum(String govde, long ms) {
    }

    private static Olcum post(Tarayici t, String govde) throws Exception {
        long b = System.nanoTime();
        String yanit = t.post("/api/atama/otomatik", govde).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Olcum(yanit, (System.nanoTime() - b) / 1_000_000);
    }

    private static int tamsayi(String json, String yol) {
        return JsonPath.<Integer>read(json, yol);
    }

    /** Aktif makinistlerin plan saatlerinin standart sapması (atanmamış = 0 saat). */
    static double saatStd(String json, JdbcTemplate jdbc) {
        Map<String, Double> saat = new HashMap<>();
        jdbc.queryForList("SELECT sicil FROM makinist WHERE durum = 'AKTIF'", String.class).forEach(s -> saat.put(s, 0.0));
        List<Map<String, Object>> atananlar = JsonPath.read(json, "$.atananlar");
        for (Map<String, Object> a : atananlar) {
            double s = Duration.between(OffsetDateTime.parse((String) a.get("kalkis")), OffsetDateTime.parse((String) a.get("varis"))).toMinutes() / 60.0;
            saat.merge((String) a.get("sicil"), s, Double::sum);
        }
        double ort = saat.values().stream().mapToDouble(d -> d).average().orElse(0);
        return Math.sqrt(saat.values().stream().mapToDouble(d -> (d - ort) * (d - ort)).average().orElse(0));
    }

    private static int kirmizi(Tarayici t, String bas, String bit) throws Exception {
        String y = t.get("/api/atama/kirmizi-sayisi?bas=" + bas + "&bit=" + bit).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return tamsayi(y, "$.kirmiziAtama");
    }

    /**
     * @param uygulaKilidi true: iki gerçek {@code uygula} (NFR-03) ve kırmızı denetimi de koşulur
     * @return iyileştirmeli önizleme yanıtı
     */
    static String olc(Tarayici t, JdbcTemplate jdbc, String ad, String bas, String bit, Esik e, boolean uygulaKilidi) throws Exception {
        String tekGun = govde(bas, bas, false, "");
        String tekGunIy = govde(bas, bas, false, ",\"iyilestir\":true");
        post(t, tekGun); // ısınma
        Olcum taban = post(t, govde(bas, bit, false, ""));
        long tekMs = Long.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            tekMs = Math.min(tekMs, post(t, tekGunIy).ms());
        }
        Olcum iy = post(t, govde(bas, bit, false, ",\"iyilestir\":true"));
        int tabanBos = tamsayi(taban.govde(), "$.bosSayisi");
        int iyBos = tamsayi(iy.govde(), "$.bosSayisi");
        assertThat(JsonPath.<Integer>read(iy.govde(), "$.iyilestirme.tabanBosSayisi")).as(ad + ": tur 0 = bugünkü plan").isEqualTo(tabanBos);
        assertThat(iyBos).as(ad + ": tabandan kötü değil").isLessThanOrEqualTo(tabanBos);

        // FR-04: anahtarla tek tur önizlemeyi yeniden üretir (ve kilit altı tek turun maliyeti)
        String anahtar = JSON.readTree(iy.govde()).get("iyilestirme").get("anahtar").toString();
        String anahtarli = govde(bas, bit, false, ",\"iyilestir\":true,\"anahtar\":" + anahtar);
        Olcum tekTur = post(t, anahtarli);
        long tekTurMs = Math.min(tekTur.ms(), post(t, anahtarli).ms());
        assertThat(JSON.readTree(tekTur.govde()).get("atananlar")).as(ad + ": anahtar önizlemeyi yeniden üretir").isEqualTo(JSON.readTree(iy.govde()).get("atananlar"));
        long tabanMs = Math.min(taban.ms(), post(t, govde(bas, bit, false, "")).ms());

        double tabanStd = saatStd(taban.govde(), jdbc);
        double iyStd = saatStd(iy.govde(), jdbc);
        String satir = "OLCEK-IYILESTIR " + ad + ": boş " + tabanBos + " -> " + iyBos + "; tur " + JsonPath.read(iy.govde(), "$.iyilestirme.tur") + ", arama "
                + JsonPath.read(iy.govde(), "$.iyilestirme.sureMs") + " ms; önizleme taban " + tabanMs + " ms, iyileştirmeli " + iy.ms() + " ms; tek tur (anahtarlı) " + tekTurMs
                + " ms; tek gün iyileştirmeli " + tekMs + " ms; saat std " + String.format("%.2f -> %.2f", tabanStd, iyStd) + "; anahtar " + anahtar.length() + " bayt";
        System.out.println(satir);
        // kapılar toplu denetlenir: biri kaçsa da kalan ölçümler yazılır
        SoftAssertions kapi = new SoftAssertions();
        kapi.assertThat(iyBos).as(ad + ": iyileştirmeli boş (FR-03)").isLessThanOrEqualTo(e.bosTavan());
        kapi.assertThat(iy.ms()).as(ad + ": iyileştirmeli önizleme süresi (NFR-01)").isLessThanOrEqualTo(e.onizlemeMs());
        kapi.assertThat(tekMs).as(ad + ": tek gün iyileştirmeli (NFR-01)").isLessThanOrEqualTo(e.tekGunMs());
        if (tabanStd > 0) {
            kapi.assertThat(iyStd).as(ad + ": adalet (NFR-02)").isLessThanOrEqualTo(e.stdOran() * tabanStd);
        }
        if (uygulaKilidi) {
            long enBuyukId = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM atama", Long.class);
            int kirmiziOnce = kirmizi(t, bas, bit);
            // QA BUG-1: tek örnekli duvar saati oranı yük altında salınıyordu (1,47x); 3 tekrarın en iyisi karşılaştırılır (düz ve iyileştirmeli ayrı ayrı), son tekrarın iyileştirmeli yazımı kalır
            Olcum duzUygula = null;
            Olcum iyUygula = null;
            long duzEn = Long.MAX_VALUE;
            long iyEn = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                duzUygula = post(t, govde(bas, bit, true, ""));
                duzEn = Math.min(duzEn, duzUygula.ms());
                jdbc.update("DELETE FROM atama WHERE id > ?", enBuyukId); // aynı durumdan sonraki uygulama
                iyUygula = post(t, govde(bas, bit, true, ",\"iyilestir\":true,\"anahtar\":" + anahtar));
                iyEn = Math.min(iyEn, iyUygula.ms());
                if (i < 2) {
                    jdbc.update("DELETE FROM atama WHERE id > ?", enBuyukId);
                }
            }
            int kirmiziSonra = kirmizi(t, bas, bit);
            satir = "OLCEK-IYILESTIR " + ad + " uygula: düz " + duzEn + " ms (en iyi 3; atanan " + tamsayi(duzUygula.govde(), "$.atananSayisi") + "), iyileştirmeli " + iyEn + " ms (en iyi 3; atanan "
                    + tamsayi(iyUygula.govde(), "$.atananSayisi") + ", boş " + tamsayi(iyUygula.govde(), "$.bosSayisi") + "); kırmızı " + kirmiziOnce + " -> " + kirmiziSonra;
            System.out.println(satir);
            kapi.assertThat(JSON.readTree(iyUygula.govde()).get("atananlar")).as(ad + ": uygulama = önizleme").isEqualTo(JSON.readTree(iy.govde()).get("atananlar"));
            kapi.assertThat(kirmiziSonra).as(ad + ": iyileştirmeli uygulamada yeni kırmızı yok (FR-02)").isEqualTo(kirmiziOnce);
            kapi.assertThat((double) iyEn).as(ad + ": kilit altı süre (NFR-03, 3 tekrarın en iyisi)").isLessThanOrEqualTo(e.kilitOran() * duzEn);
        }
        kapi.assertAll();
        return iy.govde();
    }
}
