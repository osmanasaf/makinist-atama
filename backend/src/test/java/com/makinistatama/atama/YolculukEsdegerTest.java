package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.ResultActions;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.demo.SabitTarihliDemo;
import com.makinistatama.rapor.BelgeDurumuServisi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F2b EK-1 (YOLCU yokken birebir eşdeğerlik): sabit tarihli demo veri üstünde bugünkü çıktılar altın dosyayla karşılaştırılır; her faz kapısında koşar.
 * (a) 31 günlük otomatik atama planı (yolculuk bayrağı kapalı ve istekte hiç yok), (b) bir aylık adalet / puantaj / yedek / belge durumu (12 günlük uygulamadan sonra),
 * (c) bir günün gün listesi + kırmızı sayısı. YOLCU satırı ve yolculuk bayrağı bu testte YOKTUR; yalnız kimlik/sayı alanları saklanır (sonradan eklenen alanlar girmez).
 * Bilinçli yenileme (demo veri ya da kural bilinçli değişti): {@code -Dgolden.yaz=true}.
 */
class YolculukEsdegerTest extends TestVeritabani {

    private static final JsonMapper JSON = new JsonMapper();

    @Autowired
    ApplicationContext ctx;

    @Autowired
    BelgeDurumuServisi belgeDurumu;

    /** Kararlı sıralı iki alanlı harita (Map.of sırası belirsizdir). */
    private static Map<String, Object> sirali(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static JsonNode oku(ResultActions r) throws Exception {
        return JSON.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static String d(JsonNode n) {
        return n == null || n.isNull() ? "-" : n.toString().replace("\"", "");
    }

    private static String satir(JsonNode n, String... alanlar) {
        List<String> p = new ArrayList<>();
        for (String a : alanlar) {
            p.add(d(n.get(a)));
        }
        return String.join("|", p);
    }

    @Test
    void yolcuYokken_ciktilar_altinDosyayla_birebirAyni() throws Exception {
        SabitTarihliDemo.yukle(ctx);
        Tarayici t = atamaci();

        // (a) plan: 31 gün önizleme
        JsonNode plan = oku(t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"bit\":\"2026-11-11\",\"uygula\":false}"));
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("atananSayisi", plan.get("atananSayisi").asInt());
        a.put("bosSayisi", plan.get("bosSayisi").asInt());
        a.put("gecmisSayisi", plan.get("gecmisSayisi").asInt());
        Map<String, List<String>> atananlar = new TreeMap<>();
        for (JsonNode x : plan.get("atananlar")) {
            atananlar.computeIfAbsent(d(x.get("tarih")), k -> new ArrayList<>()).add(satir(x, "seferId", "makinistId"));
        }
        a.put("atananlar", atananlar.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue()), (p, q) -> p, TreeMap::new)));
        List<String> bos = new ArrayList<>();
        for (JsonNode x : plan.get("bosKalanlar")) {
            bos.add(satir(x, "seferId", "neden"));
        }
        a.put("bosKalanlar", bos);
        AltinDosya.kontrol("otomatik-plan", a);

        // 12 günü uygula; sonraki raporlar bu atamalardan beslenir
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-23\",\"uygula\":true}");
        Map<String, Object> b = new LinkedHashMap<>();

        JsonNode adalet = oku(t.get("/api/rapor/adalet?ay=2026-10"));
        Map<String, Object> ad = new LinkedHashMap<>();
        ad.put("genel", satir(adalet, "makinistSayisi", "ortalamaSaat", "ortalamaKm", "kmBilinmeyenSefer"));
        List<String> adSatir = new ArrayList<>();
        for (JsonNode x : adalet.get("satirlar")) {
            adSatir.add(satir(x, "makinistId", "seferSayisi", "gorevSaat", "km", "kmBilinmeyenSefer", "geceSaat", "izinGunu", "farkYuzde", "sifirSaat"));
        }
        ad.put("satirlar", adSatir);
        b.put("adalet", ad);

        JsonNode puantaj = oku(t.get("/api/rapor/puantaj?ay=2026-10"));
        List<String> pSatir = new ArrayList<>();
        for (JsonNode x : puantaj.get("satirlar")) {
            pSatir.add(satir(x, "makinistId", "calisilanGun", "fiiliSaat", "ucretlendirilenSaat", "fazlaMesaiSaat", "geceSaat", "km", "kmBilinmeyenSefer"));
        }
        b.put("puantaj", pSatir);

        JsonNode yedek = oku(t.get("/api/rapor/yedek?ay=2026-10"));
        List<String> yGun = new ArrayList<>();
        for (JsonNode x : yedek.get("gunler")) {
            yGun.add(satir(x, "tarih", "planliSefer", "ihtiyac", "mevcut", "acik", "izinli", "gorevli"));
        }
        b.put("yedek", sirali("genel", satir(yedek, "aktifMakinist", "eksikGunSayisi", "toplamAcik"), "gunler", yGun));

        List<String> belge = new ArrayList<>();
        for (var m : belgeDurumu.rapor(LocalDate.of(2026, 10, 12)).makinistler()) {
            belge.add(m.makinistId() + "|" + m.enKotu() + "|" + m.belgeler().stream().map(x -> x.tur() + "=" + x.durum()).collect(java.util.stream.Collectors.joining(",")));
        }
        b.put("belgeDurumu", belge);
        AltinDosya.kontrol("raporlar", b);

        // (c) bir günün gün listesi + kırmızı sayısı
        JsonNode gun = oku(t.get("/api/atama/gun?tarih=2026-10-14"));
        List<String> g = new ArrayList<>();
        for (JsonNode s : gun.get("satirlar")) {
            StringBuilder sb = new StringBuilder(satir(s, "seferId", "durum", "kirmizi", "bosNeden"));
            for (JsonNode m : s.get("makinistler")) {
                sb.append(" ; ").append(satir(m, "rol", "makinistId", "kirmizi")).append('|').append(m.get("kirmiziNedenler").size()).append('|').append(m.get("uyarilar").size());
            }
            g.add(sb.toString());
        }
        JsonNode kirmizi = oku(t.get("/api/atama/kirmizi-sayisi?bas=2026-10-12&bit=2026-10-23"));
        assertThat(g).isNotEmpty();
        AltinDosya.kontrol("gun-listesi", sirali("kirmiziSayisi", satir(kirmizi, "kirmiziSefer", "kirmiziAtama"), "satirlar", g));

        // (d) olaylı gün (YOLCU yokken): bir gecikmeli, bir iptal, bir kırmızı (onaylı izin) sefer; gün listesi + kırmızı sayısı birebir aynı kalmalı
        JsonNode satirlar = gun.get("satirlar");
        t.post("/api/sefer/" + satirlar.get(3).get("seferId").asLong() + "/gecikme", "{\"kalkisGecikmeDk\":90,\"varisGecikmeDk\":90,\"neden\":\"GECIKME\"}");
        t.post("/api/sefer/" + satirlar.get(5).get("seferId").asLong() + "/iptal", "{\"neden\":\"IPTAL\"}");
        long izinli = satirlar.get(8).get("makinistler").get(0).get("makinistId").asLong();
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, '2026-10-14', '2026-10-14', 'YILLIK', 'ONAY', 'test')", izinli);
        JsonNode olayli = oku(t.get("/api/atama/gun?tarih=2026-10-14"));
        List<String> o = new ArrayList<>();
        for (JsonNode s : olayli.get("satirlar")) {
            StringBuilder sb = new StringBuilder(satir(s, "seferId", "durum", "kalkisGecikmeDk", "kirmizi", "bosNeden"));
            for (JsonNode m : s.get("makinistler")) {
                sb.append(" ; ").append(satir(m, "rol", "makinistId", "kirmizi")).append('|').append(m.get("kirmiziNedenler").size()).append('|').append(m.get("uyarilar").size());
            }
            o.add(sb.toString());
        }
        assertThat(o.stream().filter(x -> x.contains("|true|")).count()).as("olaylı günde kırmızı sefer var").isGreaterThanOrEqualTo(2); // iptal + izinli
        AltinDosya.kontrol("gun-listesi-olayli", sirali("kirmiziSayisi", satir(oku(t.get("/api/atama/kirmizi-sayisi?bas=2026-10-12&bit=2026-10-23")), "kirmiziSefer", "kirmiziAtama"), "satirlar", o));
    }
}
