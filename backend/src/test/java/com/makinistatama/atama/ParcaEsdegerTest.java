package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.ParcaliTrenUcAUcaTest.Kurulum;
import com.makinistatama.sefer.SeferYuklemeServisi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * F2c T2.5 (FR-09, FR-10): "parça = sıradan sefer satırı" iddiasının kanıtı. Aynı iki sefer bir kez Excel'in "Değişim Noktaları" sayfasıyla (parça alanlı), bir kez elle
 * {@code POST /api/sefer} ile (parçasız, iki bağımsız sefer) kurulur; aynı atamalar için aday listeleri, ihlaller, otomatik plan önizlemesi, gün listesi (kırmızı, boş nedeni),
 * kırmızı sayısı, adalet ve puantaj raporları birebir eşit olmalıdır (parça alanları ve tren no hariç; kimlikler {@code RESTART IDENTITY} ile aynıdır).
 */
class ParcaEsdegerTest extends TestVeritabani {

    static final String GUN = "2026-10-12";
    static final JsonMapper JSON = new JsonMapper();
    /** Parça etiketi + tren no: bilerek farklı olan alanlar; zaman damgaları koşuya göre değişir. */
    static final Set<String> FARKLI = Set.of("parcaNo", "parcaSayisi", "trenNo", "uretimZamani", "atamaZamani");

    @Autowired
    SeferYuklemeServisi yukleme;

    static String norm(String json) throws Exception {
        JsonNode n = JSON.readTree(json);
        sil(n);
        return n.toString();
    }

    static void sil(JsonNode n) {
        if (n instanceof ObjectNode o) {
            FARKLI.forEach(o::remove);
            o.forEach(ParcaEsdegerTest::sil);
        } else if (n.isArray()) {
            n.forEach(ParcaEsdegerTest::sil);
        }
    }

    static String govde(String tren, long kalkisGar, String kalkis, long varisGar, String varis, long arac, long hat, int km) {
        return "{\"trenNo\":\"" + tren + "\",\"tarih\":\"" + GUN + "\",\"kalkisGarId\":" + kalkisGar + ",\"kalkisSaat\":\"" + kalkis + "\",\"varisGarId\":" + varisGar
                + ",\"varisSaat\":\"" + varis + "\",\"aracTipiId\":" + arac + ",\"hatId\":" + hat + ",\"km\":" + km + ",\"versiyon\":0}";
    }

    /** Aynı atamalar ve sorgular; yanıtların normalleştirilmiş birleşimi. */
    String senaryo(Supplier<long[]> kur, long a, long b, long c) throws Exception {
        long[] p = kur.get();
        Tarayici t = atamaci();
        StringBuilder o = new StringBuilder();
        o.append(norm(t.get("/api/atama/sefer/" + p[0] + "/adaylar").andReturn().getResponse().getContentAsString())).append('\n');
        o.append(norm(t.get("/api/atama/sefer/" + p[1] + "/adaylar").andReturn().getResponse().getContentAsString())).append('\n');
        o.append(norm(t.get("/api/atama/sefer/" + p[1] + "/adaylar?rol=YARDIMCI").andReturn().getResponse().getContentAsString())).append('\n');
        o.append(norm(t.post("/api/atama/otomatik", "{\"bas\":\"" + GUN + "\",\"bit\":\"" + GUN + "\",\"uygula\":false}").andReturn().getResponse().getContentAsString())).append('\n');
        // C (Ankara'ya bağlı) parça 2'ye: K-20 uyarısı -> gerekçesiz 400, B'den sonra değiştirme
        o.append(t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andReturn().getResponse().getStatus()).append(' ');
        o.append(t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(c)).andReturn().getResponse().getStatus()).append(' ');
        o.append(t.post("/api/atama/sefer/" + p[1], "{\"makinistId\":" + c + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus()).append(' ');
        o.append(t.post("/api/atama/sefer/" + p[1], "{\"makinistId\":" + b + ",\"neden\":\"DIGER\",\"nedenNotu\":\"deneme\"}").andReturn().getResponse().getStatus()).append(' ');
        o.append(t.post("/api/atama/sefer/" + p[0], "{\"makinistId\":" + c + ",\"rol\":\"YARDIMCI\"}").andReturn().getResponse().getStatus()).append('\n');
        o.append(norm(t.get("/api/atama/gun?tarih=" + GUN).andReturn().getResponse().getContentAsString())).append('\n');
        o.append(norm(t.get("/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN).andReturn().getResponse().getContentAsString())).append('\n');
        o.append(norm(t.get("/api/rapor/adalet?ay=2026-10").andReturn().getResponse().getContentAsString())).append('\n');
        o.append(norm(yonetici().get("/api/rapor/puantaj?ay=2026-10").andReturn().getResponse().getContentAsString())).append('\n');
        return o.toString();
    }

    @Test
    void parcaliExcelVeElleOlusturulanIkiSefer_birebirAyniSonucu_verir() throws Exception {
        Kurulum k = new Kurulum(jdbc, yukleme);
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars);
        long b = k.makinist("D-B", k.sivas, k.hatSivasKars);
        long c = k.makinist("D-C", k.ankara, k.hatAnkaraKars, k.hatSivasKars);

        String excel = senaryo(() -> k.ankaraSivasKars("DE-1503", GUN), a, b, c);

        jdbc.execute("TRUNCATE atama, sefer RESTART IDENTITY CASCADE"); // makinist/tanım kalır; kimlikler baştan: karşılaştırma kimlik farkı taşımaz
        String elle = senaryo(() -> {
            try {
                Tarayici t = atamaci();
                t.post("/api/sefer", govde("DE-1503-1", k.ankara, "08:00", k.sivas, "13:30", k.yht, k.hatAnkaraKars, 603)).andReturn();
                t.post("/api/sefer", govde("DE-1503-2", k.sivas, "13:40", k.kars, "20:10", k.yht, k.hatSivasKars, 500)).andReturn();
                List<Long> idler = jdbc.queryForList("SELECT id FROM sefer ORDER BY planlanan_kalkis", Long.class);
                return new long[] { idler.get(0), idler.get(1) };
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, a, b, c);

        assertThat(elle).isEqualTo(excel);
        assertThat(excel).contains("\"kirmizi\"").contains("K-20"); // senaryo gerçekten ihlal/uyarı içeriyor (boş karşılaştırma değil)
    }
}
