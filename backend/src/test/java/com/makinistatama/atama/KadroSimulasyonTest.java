package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.demo.SabitTarihliDemo;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.NobetHesabi;
import com.makinistatama.kural.ZamanHesabi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * F3a-2 uçtan uca (demo 28 gün, 12 Ekim 2026) — OK-22 sonrası: doluluk eğrisi, iki K-20 modu, GENEL sanal makinist, DB değişmez, bütçeden bağımsız belirleyicilik, temiz sayfa,
 * DB'siz bağımsız kural denetimi. Sayılar ölçülür (04-gelistirme.md): testte yalnız eşitsizlik/aralık/tutarlılık.
 */
class KadroSimulasyonTest extends TestVeritabani {

    static final LocalDate BAS = LocalDate.of(2026, 10, 12);
    static final List<String> TABLOLAR = List.of("atama", "sefer", "makinist", "izin", "degisiklik_kaydi", "kural_parametre", "uygulama_ayar", "tanim");

    @Autowired
    ApplicationContext ctx;
    @Autowired
    ObjectMapper json;
    @Autowired
    KadroTahminServisi servis;

    @BeforeEach
    void kur() {
        SabitTarihliDemo.yukle(ctx);
    }

    private static KadroIstegi istek(int hafta) {
        return new KadroIstegi(BAS, hafta, 13, true, false, 30, false, null);
    }

    private List<Object> durum() {
        List<Object> s = new ArrayList<>();
        for (String t : TABLOLAR) {
            s.add(jdbc.queryForObject("SELECT count(*) FROM " + t, Integer.class));
            s.add(jdbc.queryForObject("SELECT md5(coalesce(string_agg(x::text, '|' ORDER BY x::text), '')) FROM (SELECT * FROM " + t + ") x", String.class));
        }
        return s;
    }

    /** Süre alanları (makineye bağlı) çıkarılmış JSON. */
    private String sabitJson(KadroRaporuDto r) {
        ObjectNode n = (ObjectNode) json.valueToTree(r);
        sureleriSil(n);
        return n.toString();
    }

    private static void sureleriSil(JsonNode n) {
        if (n instanceof ObjectNode o) {
            o.remove("sureMs");
            o.properties().forEach(e -> sureleriSil(e.getValue()));
        } else if (n.isArray()) {
            n.forEach(KadroSimulasyonTest::sureleriSil);
        }
    }

    @Test
    void demo_dolulukEgrisi_hedefler_dagilim_toplamlarTutar() throws Exception {
        long t0 = System.nanoTime();
        String ham = yonetici().post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"hafta\":4,\"sureSn\":30}").andExpect(status().isOk()).andExpect(jsonPath("$.simulasyon").exists())
                .andReturn().getResponse().getContentAsString();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        JsonNode r = json.readTree(ham);
        JsonNode s = r.get("simulasyon");
        System.out.println("KADRO demo 28 gün: mevcut " + s.get("mevcut") + " | konumsuz " + s.get("konumsuz") + " | iyileştirmeli " + s.get("iyilestirmeli") + " | hedefler " + s.get("hedefler")
                + " | eğri " + s.get("egri") + " | durma " + s.get("egriDurma") + " | dağılım (" + s.get("dagilimEtiketi") + ") " + s.get("dagilim") + " | " + ms + " ms");

        assertThat(ham).as("raporda sanal kimlik/sicil yok").doesNotContain("SANAL");
        assertThat(ham).as("tek 'toplam kadro' / 'eklenecek' başlık sayısı yok (OK-22)").doesNotContain("toplamKadro").doesNotContain("ustSinir");
        assertThat(s.get("mevcutAsil").asInt()).isEqualTo(66);
        int n = s.get("mevcut").get("seferSayisi").asInt();
        assertThat(n).isEqualTo(r.get("varsayim").get("seferSayisi").asInt());

        // mevcut kadro = eğrinin 0. noktası; konumsuz (K-20 kapalı) aynı kadroyla daha az boş
        JsonNode egri = s.get("egri");
        assertThat(egri.get(0).get("ek").asInt()).isZero();
        assertThat(egri.get(0).get("bos").asInt()).isEqualTo(s.get("mevcut").get("bos").asInt());
        assertThat(s.get("mevcut").get("dolulukYuzde").asDouble()).isBetween(100.0 * (n - egri.get(0).get("bos").asInt()) / n - 0.001, 100.0 * (n - egri.get(0).get("bos").asInt()) / n + 0.001);
        assertThat(s.get("konumsuz").get("bos").asInt()).as("K-20 kapalı aynı kadro: konumun payı (kapatma etkisiz kalırsa eşit çıkardı)").isLessThan(s.get("mevcut").get("bos").asInt());

        // eğri: ek artar, boş KESİN azalır (zarf), marjinal = önceki noktaya göre kişi başı kapanan
        for (int i = 1; i < egri.size(); i++) {
            JsonNode e = egri.get(i);
            JsonNode o = egri.get(i - 1);
            assertThat(e.get("ek").asInt()).isGreaterThan(o.get("ek").asInt());
            assertThat(e.get("bos").asInt()).isLessThan(o.get("bos").asInt());
            assertThat(e.get("marjinal").asDouble()).isEqualTo((o.get("bos").asInt() - e.get("bos").asInt()) / (double) (e.get("ek").asInt() - o.get("ek").asInt()));
        }

        // hedefler: %98 / %99 / %100; ulaşılanlarda boş izinli sınırın altında, gevşek hedef ≤ sıkı hedef; ulaşılamayan "ek" null
        JsonNode hedefler = s.get("hedefler");
        assertThat(hedefler).hasSize(3);
        int onceki = -1;
        for (JsonNode h : hedefler) {
            int izinli = (int) Math.floor(n * (100 - h.get("yuzde").asInt()) / 100.0 + 1e-9);
            if (h.get("ulasildi").asBoolean()) {
                assertThat(h.get("bos").asInt()).isLessThanOrEqualTo(izinli);
                assertThat(h.get("ek").asInt()).isGreaterThanOrEqualTo(onceki);
                onceki = h.get("ek").asInt();
            } else {
                assertThat(h.get("ek").isNull()).isTrue();
                assertThat(h.get("bos").asInt()).as("ulaşılamayan hedefte en iyi nokta hedefin üstünde").isGreaterThan(izinli);
            }
        }
        assertThat(hedefler.get(0).get("yuzde").asInt()).isEqualTo(98);

        // kalan boşlar: eğrinin son (en iyi) noktasında, nedenleriyle
        int kalan = s.get("kalanBosToplam").asInt();
        assertThat(kalan).isEqualTo(egri.get(egri.size() - 1).get("bos").asInt());
        assertThat(s.get("kalanBoslar").size()).isEqualTo(Math.min(kalan, 100));
        for (JsonNode b : s.get("kalanBoslar")) {
            assertThat(b.get("neden").asText()).isNotBlank();
        }
        int nedenToplam = 0;
        for (JsonNode x : s.get("kalanNedenler")) {
            nedenToplam += x.get("adet").asInt();
        }
        assertThat(nedenToplam).isEqualTo(kalan);

        // dağılım: kişi toplamı = dağılım noktası; gar tablosundaki eklenen aynı
        int kisi = 0;
        for (JsonNode d : s.get("dagilim")) {
            kisi += d.get("kisi").asInt();
        }
        assertThat(kisi).isEqualTo(s.get("dagilimEk").asInt());
        int garEklenen = 0;
        for (JsonNode g : r.get("garlar")) {
            garEklenen += g.get("eklenen").asInt();
        }
        assertThat(garEklenen).isEqualTo(s.get("dagilimEk").asInt());

        // küme kullanımı gerçek plandan: kişi toplamı 66; kullanılan saat toplamı ≈ mevcut planda atanan işin haftalık saati
        assertThat(r.get("kumeKaynagi").asText()).isEqualTo("PLAN");
        int mevcut = 0;
        double kullanilan = 0;
        for (JsonNode k : r.get("kumeler")) {
            mevcut += k.get("mevcut").asInt();
            kullanilan += k.get("kullanilanSaat").asDouble();
        }
        assertThat(mevcut).isEqualTo(66);
        assertThat(kullanilan).isGreaterThan(0).isLessThanOrEqualTo(r.get("varsayim").get("haftalikTalepSaat").asDouble() + 0.01);
        assertThat(ms).as("eğri + ek hesaplar makul sürede").isLessThan(60_000);
    }

    @Test
    void dbDegismez_ayniIstekIkiKez_surelerHaricJsonBirebir() {
        List<Object> once = durum();

        KadroRaporuDto a = servis.hesapla(istek(2));
        KadroRaporuDto b = servis.hesapla(istek(2));

        assertThat(durum()).as("NFR-01: veritabanı değişmez").isEqualTo(once);
        assertThat(sabitJson(b)).as("NFR-02").isEqualTo(sabitJson(a));
    }

    /** QA BUG-1: başlık (eğri) süre bütçesine bağlı değildir: 5 sn ve 30 sn bütçeli aynı istek aynı eğriyi/hedefleri/dağılımı verir; bütçe yalnız ek hesapları etkiler. */
    @Test
    void baslikSureButcesindenBagimsiz_ayniEgriAyniHedefler() throws Exception {
        KadroRaporuDto.Simulasyon kisa = servis.hesapla(new KadroIstegi(BAS, 2, 13, true, false, 5, false, null)).simulasyon();
        KadroRaporuDto.Simulasyon uzun = servis.hesapla(new KadroIstegi(BAS, 2, 13, true, false, 30, false, null)).simulasyon();

        assertThat(json.writeValueAsString(kisa.egri())).isEqualTo(json.writeValueAsString(uzun.egri()));
        assertThat(json.writeValueAsString(kisa.hedefler())).isEqualTo(json.writeValueAsString(uzun.hedefler()));
        assertThat(json.writeValueAsString(kisa.dagilim())).isEqualTo(json.writeValueAsString(uzun.dagilim()));
        assertThat(kisa.mevcut().bos()).isEqualTo(uzun.mevcut().bos());
        assertThat(kisa.kalanBosToplam()).isEqualTo(uzun.kalanBosToplam());
        assertThat(kisa.konumsuz().bos()).isEqualTo(uzun.konumsuz().bos());
    }

    /**
     * CRITICAL-2: temiz sayfa aralık SONRASI atamaları da dışlar. Demoyu aralığın ötesine kadar planlayıp uygulayınca (aralıkta ve sonrasında atamalar var) sonuç, atamasız
     * demoyla birebir aynıdır (önceden: kalan gelecek görevler K-20 ile tüm yerleştirmeleri eliyor, taban 122 → 1.439 boş).
     */
    @Test
    void temizSayfa_aralikSonrasiAtamalarSonucuDegistirmez() throws Exception {
        KadroRaporuDto atamasiz = servis.hesapla(istek(2));

        yonetici().post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"bit\":\"2026-11-12\",\"uygula\":true}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama a JOIN sefer s ON s.id = a.sefer_id WHERE s.tarih > DATE '2026-10-25'", Integer.class))
                .as("aralık (2 hafta) sonrasında atama var").isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama a JOIN sefer s ON s.id = a.sefer_id WHERE s.tarih BETWEEN DATE '2026-10-12' AND DATE '2026-10-25'", Integer.class))
                .as("aralıkta atama var").isPositive();

        KadroRaporuDto atamali = servis.hesapla(istek(2));
        assertThat(json.writeValueAsString(atamali.simulasyon().egri())).isEqualTo(json.writeValueAsString(atamasiz.simulasyon().egri()));
        assertThat(json.writeValueAsString(atamali.simulasyon().hedefler())).isEqualTo(json.writeValueAsString(atamasiz.simulasyon().hedefler()));
        assertThat(atamali.simulasyon().mevcut().bos()).isEqualTo(atamasiz.simulasyon().mevcut().bos());
    }

    /** QA BUG-3: aynı anda ikinci simülasyonlu hesap reddedilir (429); birincisi etkilenmez. */
    @Test
    void esZamanliIkinciIstek_429() throws Exception {
        CompletableFuture<KadroRaporuDto> ilk = CompletableFuture.supplyAsync(() -> servis.hesapla(istek(4)));
        Thread.sleep(1500); // ilk hesap sürüyor (demo 4 hafta onlarca saniye)
        yonetici().post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"hafta\":1}").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.kod").value("KADRO_HESABI_SURUYOR"));
        assertThat(ilk.get().simulasyon()).isNotNull();
        yonetici().post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"hafta\":1}").andExpect(status().isOk()); // kilit serbest
    }

    @Test
    void bagimsizKuralDenetimi_dagilimNoktasiPlani_kirmiziYok_seferEnCokBirKez() {
        KadroTahminServisi.Ic ic = servis.hesaplaIc(istek(2));

        Map<Long, SeferGirdisi> sefer = new HashMap<>();
        ic.seferler().forEach(g -> sefer.put(g.seferId(), g));
        Map<Long, MakinistGirdisi> mak = new HashMap<>();
        Map<Long, List<Gorev>> son = new HashMap<>();
        for (MakinistGirdisi m : ic.baslikKadro()) {
            mak.put(m.id(), m);
            son.put(m.id(), new ArrayList<>(m.gorevler()));
        }
        Set<Long> atanan = new HashSet<>();
        for (Atandi a : ic.baslikSonuc().atananlar()) {
            assertThat(atanan.add(a.seferId())).as("sefer " + a.seferId() + " en çok bir kez").isTrue();
            son.get(a.makinistId()).add(sefer.get(a.seferId()).gorev());
        }
        assertThat(atanan).isNotEmpty();
        List<String> kirmizi = new ArrayList<>();
        for (Atandi a : ic.baslikSonuc().atananlar()) {
            SeferGirdisi g = sefer.get(a.seferId());
            MakinistGirdisi m = mak.get(a.makinistId());
            Instant alt = ZamanHesabi.pencereBasi(g.gun(), ic.pencereGun(), ic.yillik()).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
            Instant ust = ZamanHesabi.pencereSonu(g.gun(), ic.pencereGun(), ic.baslikParametreleri().ozelYillik()).plusDays(1).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
            List<Gorev> diger = son.get(m.id()).stream().filter(x -> x.seferId() != g.seferId() && !x.baslangic().isBefore(alt) && x.baslangic().isBefore(ust))
                    .sorted(Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId)).toList();
            List<Gorev> sorumlu = NobetHesabi.sorumluKomsular(diger, g.gorev(), ic.baslikParametreleri().dakika("nobet.ayirma_saat"));
            if (KuralMotoru.engelVarMi(KuralMotoru.degerlendir(new KuralBaglami(g.gorev(), sorumlu, m.bilgi(), m.hatSon(), m.genelSon(), ic.adlar(), ic.baslikParametreleri(), null)))) {
                kirmizi.add(m.sicil() + "/" + g.seferId());
            }
        }
        assertThat(kirmizi).as("NFR-05: bağımsız denetimde ENGEL'li atama yok").isEmpty();
        assertThat(ic.baslikSonuc().atananlar().size() + ic.baslikSonuc().bosKalanlar().size()).isEqualTo(ic.seferler().size());
    }

    @Test
    void yolculukSatiri_istekleAcilir_ayriSatir_egriyiDegistirmez() {
        KadroRaporuDto kapali = servis.hesapla(istek(2));
        assertThat(kapali.simulasyon().yolculuk()).as("varsayılan kapalı").isNull();

        KadroRaporuDto acik = servis.hesapla(new KadroIstegi(BAS, 2, 13, true, false, 30, true, null));
        KadroRaporuDto.Simulasyon s = acik.simulasyon();
        System.out.println("KADRO yolculuk (2 hafta): " + s.yolculuk() + " mevcut " + s.mevcut());
        assertThat(s.yolculuk()).isNotNull();
        assertThat(s.yolculuk().ad()).isEqualTo("yolculuk");
        assertThat(s.yolculuk().bos()).as("yolculukla mevcut kadroda boş, yolculuksuzdan çok değil").isLessThanOrEqualTo(s.mevcut().bos() + 2);
        assertThat(json.valueToTree(s.egri()).toString()).as("eğri yolculuktan etkilenmez").isEqualTo(json.valueToTree(kapali.simulasyon().egri()).toString());
    }

    @Test
    void gecersizGirdi_400() throws Exception {
        var y = yonetici();
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"hafta\":9}").andExpect(status().isBadRequest()); // 63 gün
        y.post("/api/rapor/kadro", "{\"bas\":\"2026-10-12\",\"sureSn\":31}").andExpect(status().isBadRequest());
        // review W6: tarih taşması 500 vermemeli
        y.post("/api/rapor/kadro", "{\"bas\":\"+999999999-12-31\"}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"bas\":\"-999999999-01-01\"}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"bas\":\"1999-12-31\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.bas").exists());
        y.post("/api/rapor/kadro", "{\"bas\":\"2101-01-01\",\"yalnizAnalitik\":true}").andExpect(status().isBadRequest());
        y.post("/api/rapor/kadro", "{\"bas\":\"2100-12-31\",\"yalnizAnalitik\":true}").andExpect(status().isOk());
    }

    @Test
    void sureKoruma_kisaButceEkHesaplariAtlar_baslikAyni_dbDegismez() {
        List<Object> once = durum();
        KadroRaporuDto.Simulasyon s = servis.hesapla(new KadroIstegi(BAS, 4, 13, true, false, 5, false, null)).simulasyon();
        assertThat(durum()).isEqualTo(once);
        assertThat(s.mevcut().bos()).isEqualTo(s.egri().get(0).bos());
        assertThat(s.egri().size()).isGreaterThanOrEqualTo(1);
        assertThat(s.kalanBosToplam()).isGreaterThanOrEqualTo(0);
        assertThat(s.tamamlandi() || !s.atlananlar().isEmpty()).as("bütçe yetmediyse atlanan ek hesap açıkça listelenir").isTrue();
    }
}
