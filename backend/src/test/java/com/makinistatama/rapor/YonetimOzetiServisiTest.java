package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F4 / AK-4.1, 4.3, 4.4 / MK-180 / OK-44d: yönetim özeti YENİ FORMÜL İÇERMEZ; her sayı kaynak raporla (adalet, puantaj, kırmızı sayısı, denetim raporu, yedek, belge panosu, gün listesi) AYNI veri üzerinde
 * eşittir. Ay = gerçek bugünün ayı (denetim kayıtlarının zamanı gerçek saattir).
 */
class YonetimOzetiServisiTest extends TestVeritabani {

    static final YearMonth AY = YearMonth.from(LocalDate.now(AtamaVeri.ISTANBUL));

    AtamaVeri v;
    Tarayici y;
    Tarayici t;
    String ayMetni = AY.toString();
    String aralik = "bas=" + AY.atDay(1) + "&bit=" + AY.atEndOfMonth();

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        y = yonetici();
        t = atamaci();
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long m3 = v.makinist("D003");
        long m4 = v.makinist("D004");
        LocalDate d1 = AY.atDay(1);
        v.atama(v.seferEv("T1", d1, "06:00", "08:00"), m1, "ASIL");
        v.atama(v.seferEv("T2", d1, "09:00", "11:00"), m2, "ASIL");
        v.seferEv("T3", d1, "12:00", "14:00"); // boş
        long iptal = v.seferEv("T4", d1, "15:00", "17:00");
        v.atama(iptal, m3, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptal);
        v.atama(v.seferEv("CA", d1.plusDays(1), "08:00", "12:00"), m1, "ASIL"); // çakışma: kırmızı
        v.atama(v.seferEv("CB", d1.plusDays(1), "10:00", "13:00"), m1, "ASIL");
        for (int g = 2; g < 16; g++) { // m4: 14 gün x 8 sa -> haftalık 45 sa aşımı = fazla mesai
            v.atama(v.seferEv("U" + g, d1.plusDays(g), "08:00", "16:00"), m4, "ASIL");
        }
        // uyarılı atamalar (geçmiş sefer -> K-22 UYARI): 2'si aynı gerekçe
        String[] gerekce = { "gerekçe A", "gerekçe A", "gerekçe B" };
        long[] kisi = { m1, m2, m3 };
        for (int i = 0; i < 3; i++) {
            long sf = v.seferEv("G" + i, LocalDate.of(2026, 10, 5 + i), "08:00", "10:00");
            t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + kisi[i] + ",\"gerekce\":\"" + gerekce[i] + "\"}").andExpect(status().isCreated());
        }
        long sy = v.seferEv("Y1", d1.plusDays(20), "09:00", "11:00");
        t.post("/api/atama/sefer/" + sy, "{\"makinistId\":" + m2 + "}").andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + sy, "{\"makinistId\":" + m3 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk()); // yerine atama, neden HASTALIK
        y.put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"10\",\"neden\":\"TİS\"}").andExpect(status().isOk());
    }

    private String ozet() throws Exception {
        return al("/api/rapor/ozet?ay=" + ayMetni);
    }

    private String al(String yol) throws Exception {
        return y.get(yol).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static double sayi(String json, String yol) {
        return JsonPath.<Number>read(json, yol).doubleValue();
    }

    private static int tam(String json, String yol) {
        return JsonPath.<Number>read(json, yol).intValue();
    }

    @Test
    void adalet_kaynakRaporlaAyni_ve_standartSapmaPopulasyon() throws Exception {
        String o = ozet();
        String adalet = al("/api/rapor/adalet?ay=" + ayMetni);
        List<Double> saatler = JsonPath.<List<Number>>read(adalet, "$.satirlar[*].gorevSaat").stream().map(Number::doubleValue).toList();
        assertThat(tam(o, "$.adalet.n")).isEqualTo(tam(adalet, "$.makinistSayisi"));
        assertThat(sayi(o, "$.adalet.ortalamaSaat")).isEqualTo(sayi(adalet, "$.ortalamaSaat")); // aynı sayı, ikinci hesap değil
        assertThat(sayi(o, "$.adalet.minSaat")).isEqualTo(saatler.stream().mapToDouble(Double::doubleValue).min().getAsDouble());
        assertThat(sayi(o, "$.adalet.maxSaat")).isEqualTo(saatler.stream().mapToDouble(Double::doubleValue).max().getAsDouble());
        double ort = saatler.stream().mapToDouble(Double::doubleValue).average().getAsDouble(); // bağımsız (test) hesabı: n'e bölünen popülasyon sapması
        double std = Math.sqrt(saatler.stream().mapToDouble(x -> (x - ort) * (x - ort)).sum() / saatler.size());
        assertThat(sayi(o, "$.adalet.standartSapmaSaat")).isCloseTo(std, within(0.006));
        assertThat(std).isGreaterThan(0); // test anlamlı: görev dağılımı eşit değil
    }

    @Test
    void fazlaMesai_puantajToplamiyla_kirmizi_kirmiziSayisiyla_yedek_yedekRaporuyla() throws Exception {
        String o = ozet();
        String puantaj = al("/api/rapor/puantaj?ay=" + ayMetni);
        List<Double> fm = JsonPath.<List<Number>>read(puantaj, "$.satirlar[*].fazlaMesaiSaat").stream().map(Number::doubleValue).toList();
        double toplam = fm.stream().mapToDouble(Double::doubleValue).sum();
        assertThat(toplam).isGreaterThan(0);
        assertThat(sayi(o, "$.fazlaMesai.toplamSaat")).isCloseTo(toplam, within(0.005));
        assertThat(tam(o, "$.fazlaMesai.makinistSayisi")).isEqualTo((int) fm.stream().filter(x -> x > 0).count());

        String kirmizi = al("/api/atama/kirmizi-sayisi?" + aralik);
        assertThat(tam(kirmizi, "$.kirmiziAtama")).isGreaterThanOrEqualTo(2); // çakışan iki atama
        assertThat(tam(o, "$.kirmizi.atama")).isEqualTo(tam(kirmizi, "$.kirmiziAtama"));
        assertThat(tam(o, "$.kirmizi.sefer")).isEqualTo(tam(kirmizi, "$.kirmiziSefer"));
        assertThat(tam(o, "$.kirmizi.intikal")).isEqualTo(tam(kirmizi, "$.kirmiziIntikal"));

        String yedek = al("/api/rapor/yedek?ay=" + ayMetni);
        assertThat(tam(o, "$.yedek.acikGunSayisi")).isEqualTo(tam(yedek, "$.eksikGunSayisi"));
        assertThat(tam(o, "$.yedek.toplamAcik")).isEqualTo(tam(yedek, "$.toplamAcik"));
    }

    @Test
    void doluluk_gunListesiTanimiyla_gunGunToplamaEsit_iptalHaric() throws Exception {
        String o = ozet();
        int dolu = 0;
        int bos = 0;
        for (LocalDate g = AY.atDay(1); !g.isAfter(AY.atEndOfMonth()); g = g.plusDays(1)) {
            String gun = al("/api/atama/gun?tarih=" + g);
            int n = JsonPath.<List<Object>>read(gun, "$.satirlar").size();
            for (int i = 0; i < n; i++) {
                if ("IPTAL".equals(JsonPath.read(gun, "$.satirlar[" + i + "].durum"))) {
                    continue;
                }
                List<String> roller = JsonPath.read(gun, "$.satirlar[" + i + "].makinistler[*].rol");
                if (roller.contains("ASIL")) {
                    dolu++;
                } else {
                    bos++;
                }
            }
        }
        assertThat(bos).isGreaterThanOrEqualTo(1); // T3
        assertThat(tam(o, "$.doluluk.dolu")).isEqualTo(dolu);
        assertThat(tam(o, "$.doluluk.bos")).isEqualTo(bos);
        assertThat(tam(o, "$.doluluk.toplamSefer")).isEqualTo(dolu + bos);
        assertThat(sayi(o, "$.doluluk.yuzde")).isCloseTo(Math.round(dolu * 1000.0 / (dolu + bos)) / 10.0, within(0.0001));
    }

    @Test
    void degisiklikSayisiVeNedenler_denetimRaporuyla_uyariliVeGerekceler_aynaRapordan() throws Exception {
        String o = ozet();
        String rapor = al("/api/degisiklik?" + aralik);
        assertThat(tam(o, "$.degisiklik.kayitSayisi")).isEqualTo(tam(rapor, "$.kayitSayisi"));
        Map<String, Integer> neden = new TreeMap<>();
        for (String n : JsonPath.<List<String>>read(rapor, "$.bolumler[*].satirlar[?(@.neden != null)].neden")) {
            neden.merge(n, 1, Integer::sum);
        }
        assertThat(neden).containsEntry("HASTALIK", 1);
        Map<String, Integer> ozetNeden = new TreeMap<>();
        for (Map<String, Object> e : JsonPath.<List<Map<String, Object>>>read(o, "$.degisiklik.nedenler")) {
            ozetNeden.put((String) e.get("ad"), (Integer) e.get("adet"));
        }
        assertThat(ozetNeden).isEqualTo(neden);
        // AK-4.3: 3 uyarılı atama, 2'si aynı gerekçe; sayı denetim raporundaki uyarılı satır sayısıyla aynı
        assertThat(tam(o, "$.uyarili.atama")).isEqualTo(3);
        assertThat(tam(o, "$.uyarili.atama")).isEqualTo(JsonPath.<List<Object>>read(rapor, "$.bolumler[?(@.kod=='UYARILI_ATAMALAR')].satirlar[*]").size());
        assertThat(JsonPath.<List<String>>read(o, "$.uyarili.gerekceler[*].ad")).containsExactly("gerekçe A", "gerekçe B");
        assertThat(JsonPath.<List<Integer>>read(o, "$.uyarili.gerekceler[*].adet")).containsExactly(2, 1);
    }

    @Test
    void belge_bugunkuDurumdan_etiketli_demoEtiketi_ureten() throws Exception {
        jdbc.update("UPDATE makinist SET saglik_bitis = '2020-01-01' WHERE sicil = 'D001'"); // dolmuş
        String o = ozet();
        String belge = al("/api/rapor/belge-durumu");
        List<String> kotu = JsonPath.read(belge, "$.makinistler[?(@.durum=='AKTIF')].enKotu");
        assertThat(tam(o, "$.belge.dolmus")).isEqualTo((int) kotu.stream().filter("DOLDU"::equals).count()).isEqualTo(1);
        assertThat(tam(o, "$.belge.yaklasan")).isEqualTo((int) kotu.stream().filter("YAKLASIYOR"::equals).count());
        assertThat(tam(o, "$.belge.gecerli")).isEqualTo((int) kotu.stream().filter("GECERLI"::equals).count());
        assertThat(tam(o, "$.belge.girilmemis")).isEqualTo((int) kotu.stream().filter("GIRILMEMIS"::equals).count());
        assertThat((String) JsonPath.read(o, "$.belge.etiket")).isEqualTo("Bugünkü durum");
        assertThat((boolean) JsonPath.read(o, "$.demo")).isTrue(); // test makinistleri "Demo Makinist ..."
        assertThat((String) JsonPath.read(o, "$.ureten")).isNotBlank();
        assertThat((String) JsonPath.read(o, "$.ay")).isEqualTo(ayMetni);
        jdbc.update("UPDATE makinist SET ad_soyad = 'Gerçek Kişi ' || sicil");
        assertThat((boolean) JsonPath.read(ozet(), "$.demo")).isFalse();
    }

    @Test
    void salt_okunur_denetimKaydiUretmez() throws Exception {
        int once = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);
        ozet();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(once);
    }

    @Test
    void veriYokAy_sifirlar_adaletEkibiYoksaNull() throws Exception {
        jdbc.update("DELETE FROM atama");
        jdbc.update("DELETE FROM sefer");
        jdbc.update("UPDATE makinist SET durum = 'PASIF'");
        y.get("/api/rapor/ozet?ay=" + AY.minusMonths(20)).andExpect(status().isOk()).andExpect(jsonPath("$.doluluk.toplamSefer").value(0)).andExpect(jsonPath("$.doluluk.yuzde").doesNotExist())
                .andExpect(jsonPath("$.adalet.n").value(0)).andExpect(jsonPath("$.adalet.ortalamaSaat").doesNotExist()).andExpect(jsonPath("$.adalet.standartSapmaSaat").doesNotExist())
                .andExpect(jsonPath("$.adalet.minSaat").doesNotExist()).andExpect(jsonPath("$.adalet.maxSaat").doesNotExist()).andExpect(jsonPath("$.kirmizi.atama").value(0))
                .andExpect(jsonPath("$.fazlaMesai.toplamSaat").value(0.0)).andExpect(jsonPath("$.uyarili.atama").value(0)).andExpect(jsonPath("$.uyarili.gerekceler").isEmpty());
    }
}
