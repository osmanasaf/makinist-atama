package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

/**
 * MK-123 (T3.5): bölme yarışları, gerçek PostgreSQL + iki iş parçacığı + ortak bariyer; her senaryo 20 kez art arda (yeni veriyle). Kilit sırası makinist -> yazma;
 * sefer satırı için kilit yok ({@code @Version} + tekillik). Her yarışta: kilitlenme/500 yok, ISLEM_SURUYOR (kilit zaman aşımı/deadlock) yok, sonuç iki sıradan birine denk.
 * Bir taraf 0..100 ms geç başlatılır (tur 0..7 birinci, 8..15 ikinci geç; iki sıra da denenir).
 */
class SeferParcalamaEszamanliTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);
    static final int TEKRAR = 20;

    record Yanit(int durum, String govde) {
        String kod() {
            return durum == 409 ? JsonPath.read(govde, "$.kod") : null;
        }

        @Override
        public String toString() {
            return durum + (durum >= 400 ? " " + govde : "");
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager islemYoneticisi;

    AtamaVeri v;
    long sivas;
    long kars;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        kars = TestTanimlari.gar(jdbc, "Kars");
    }

    long sefer(String tren) {
        long id = v.sefer(tren, GUN, "08:00", "18:10", v.ankara, kars);
        jdbc.update("UPDATE sefer SET km = 1103 WHERE id = ?", id);
        return id;
    }

    String bolGovdesi(long id) {
        long versiyon = jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id);
        return "{\"versiyon\":" + versiyon + ",\"noktalar\":[{\"garId\":" + sivas + ",\"varisSaat\":\"13:30\",\"kalkisSaat\":\"13:40\",\"km\":603}],\"onizle\":false}";
    }

    int sayi(String sql, Object... arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    /** Aynı anda iki istek; {@code tur} ile her iki sırada da birkaç yüz mikrosaniye başlangıç farkı denenir. */
    List<Yanit> yaris(int tur, Callable<Yanit> birinci, Callable<Yanit> ikinci) throws Exception {
        CyclicBarrier bariyer = new CyclicBarrier(2);
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        try {
            long[] gecikmeUs = { 0, 300, 1500, 4000, 15_000, 30_000, 60_000, 100_000 };
            long g = gecikmeUs[tur % 8];
            boolean birinciGecikir = (tur / 8) % 2 == 0;
            Future<Yanit> f1 = havuz.submit(() -> {
                bariyer.await();
                bekle(birinciGecikir ? g : 0);
                return birinci.call();
            });
            Future<Yanit> f2 = havuz.submit(() -> {
                bariyer.await();
                bekle(birinciGecikir ? 0 : g);
                return ikinci.call();
            });
            List<Yanit> sonuc = List.of(f1.get(), f2.get());
            sonuc.forEach(y -> assertThat(y.durum()).as("500 (kilitlenme/çökme) olmamalı: %s", sonuc).isLessThan(500));
            sonuc.stream().filter(y -> y.durum() == 409).forEach(y -> assertThat(y.kod()).as("kilit zaman aşımı/deadlock değil: %s", sonuc).isNotEqualTo("ISLEM_SURUYOR"));
            return sonuc;
        } finally {
            havuz.shutdownNow();
        }
    }

    private static void bekle(long mikrosaniye) throws InterruptedException {
        if (mikrosaniye > 0) {
            Thread.sleep(mikrosaniye / 1000, (int) (mikrosaniye % 1000) * 1000);
        }
    }

    Callable<Yanit> post(Tarayici t, String url, String govde) {
        return () -> yanit(t.post(url, govde).andReturn().getResponse());
    }

    private static Yanit yanit(org.springframework.mock.web.MockHttpServletResponse r) throws Exception {
        return new Yanit(r.getStatus(), r.getContentAsString());
    }

    void seferTutarli(long p1, boolean parcali, String tren) {
        List<Integer> parcaNolar = jdbc.queryForList("SELECT coalesce(parca_no, 0) FROM sefer WHERE tren_no = ? AND tarih = ? ORDER BY 1", Integer.class, tren, GUN);
        assertThat(parcaNolar).isEqualTo(parcali ? List.of(1, 2) : List.of(0));
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id IN (SELECT id FROM sefer WHERE tren_no = ? AND parca_no = 2)", tren)).as("atama parça 2'ye yazılmaz").isZero();
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL' AND sefer_id = ?", p1)).isEqualTo(parcali ? 1 : 0);
    }

    // ------------------------------------------------------------------ (a) kaldir / ata ∥ bol

    @Test
    void kaldir_ile_bol_iki_siradan_biri() throws Exception {
        int bolunen = 0;
        int atamaDegisti = 0;
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "K" + i;
            long s = sefer(tren);
            long m = v.makinist("K" + i);
            v.atama(s, m, "ASIL");
            Tarayici tk = atamaci();
            Tarayici tb = atamaci();
            String bol = bolGovdesi(s);

            List<Yanit> y = yaris(i, () -> yanit(tk.delete("/api/atama/sefer/" + s + "?rol=ASIL&neden=HASTALIK").andReturn().getResponse()), post(tb, "/api/atama/sefer/" + s + "/bol", bol));

            assertThat(y.get(0).durum()).as("kaldır: %s", y).isIn(200, 204);
            boolean bolundu = y.get(1).durum() == 200;
            if (!bolundu) {
                assertThat(y.get(1).durum()).as("bölme: %s", y).isEqualTo(409);
                assertThat(y.get(1).kod()).isEqualTo("ATAMA_DEGISTI");
                atamaDegisti++;
            } else {
                bolunen++;
            }
            assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", s)).as("kaldırma her iki sırada da yazılır").isZero();
            seferTutarli(s, bolundu, tren);
        }
        assertThat(bolunen + atamaDegisti).isEqualTo(TEKRAR);
        System.out.println("SONUC kaldir-bol: bolundu=" + bolunen + " ATAMA_DEGISTI=" + atamaDegisti);
    }

    @Test
    void ata_yeniMakinist_ile_bol_yeniAtamaParca1de() throws Exception {
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "Y" + i;
            long s = sefer(tren);
            long m1 = v.makinist("YA" + i);
            long m2 = v.makinist("YB" + i);
            v.atama(s, m1, "ASIL");
            Tarayici ta = atamaci();
            Tarayici tb = atamaci();
            String bol = bolGovdesi(s);

            List<Yanit> y = yaris(i, post(ta, "/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"rol\":\"YARDIMCI\"}"), post(tb, "/api/atama/sefer/" + s + "/bol", bol));

            assertThat(y.get(0).durum()).as("ata: %s", y).isEqualTo(201);
            boolean bolundu = y.get(1).durum() == 200;
            if (!bolundu) {
                assertThat(y.get(1).kod()).as("bölme: %s", y).isEqualTo("ATAMA_DEGISTI");
            }
            assertThat(jdbc.queryForList("SELECT makinist_id FROM atama WHERE sefer_id = ? ORDER BY makinist_id", Long.class, s)).as("yeni atama parça 1'de: %s", y).containsExactly(m1, m2);
            seferTutarli(s, bolundu, tren);
        }
    }

    @Test
    void ata_degistir_ile_bol_ya_serilesir_ya_ATAMA_DEGISTI() throws Exception {
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "D" + i;
            long s = sefer(tren);
            long m1 = v.makinist("DA" + i);
            long m2 = v.makinist("DB" + i);
            v.atama(s, m1, "ASIL");
            Tarayici ta = atamaci();
            Tarayici tb = atamaci();
            String bol = bolGovdesi(s);

            List<Yanit> y = yaris(i, post(ta, "/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"rol\":\"ASIL\",\"neden\":\"IZIN\"}"), post(tb, "/api/atama/sefer/" + s + "/bol", bol));

            assertThat(y.get(0).durum()).as("değiştir: %s", y).isEqualTo(200);
            boolean bolundu = y.get(1).durum() == 200;
            if (!bolundu) {
                assertThat(y.get(1).kod()).as("bölme: %s", y).isEqualTo("ATAMA_DEGISTI");
            }
            assertThat(jdbc.queryForList("SELECT makinist_id FROM atama WHERE sefer_id = ?", Long.class, s)).as("tek ASIL: yeni makinist, parça 1'de").containsExactly(m2);
            seferTutarli(s, bolundu, tren);
        }
    }

    /** Zamanlamaya bırakmadan: bölme makinist kilidinde beklerken atamalar değişir -> kilit alınınca yeniden okuma farkı yakalar (409 ATAMA_DEGISTI, hiçbir şey yazılmaz). */
    @Test
    void bol_makinistKilidindeBeklerkenAtamalarDegisirse_ATAMA_DEGISTI() throws Exception {
        for (String degisiklik : List.of("kaldir", "ata")) {
            String tren = "W" + degisiklik;
            long s = sefer(tren);
            long m1 = v.makinist("W1" + degisiklik);
            long m2 = v.makinist("W2" + degisiklik);
            v.atama(s, m1, "ASIL");
            Tarayici tb = atamaci();
            Tarayici td = atamaci();
            String bol = bolGovdesi(s);
            ExecutorService havuz = Executors.newSingleThreadExecutor();
            try {
                Future<Yanit> bolme = new TransactionTemplate(islemYoneticisi).execute(durum -> {
                    jdbc.queryForObject("SELECT id FROM makinist WHERE id = ? FOR UPDATE", Long.class, m1); // bölmenin kilitleyeceği makinist (MK-7)
                    Future<Yanit> f = havuz.submit(post(tb, "/api/atama/sefer/" + s + "/bol", bol));
                    try {
                        Thread.sleep(500); // bölme kilitte bekliyor (kilit zaman aşımı 5 sn)
                        assertThat(f.isDone()).as("bölme kilit beklemeli").isFalse();
                        Yanit y = degisiklik.equals("kaldir") ? yanit(td.delete("/api/atama/sefer/" + s + "?rol=ASIL&neden=HASTALIK").andReturn().getResponse())
                                : yanit(td.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"rol\":\"YARDIMCI\"}").andReturn().getResponse());
                        assertThat(y.durum()).as(degisiklik).isIn(200, 201, 204);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return f;
                });
                Yanit y = bolme.get();
                assertThat(y.durum()).as(degisiklik + ": %s", y).isEqualTo(409);
                assertThat(y.kod()).isEqualTo("ATAMA_DEGISTI");
                seferTutarli(s, false, tren);
            } finally {
                havuz.shutdownNow();
            }
        }
    }

    // ------------------------------------------------------------------ (b) bol ∥ bol

    @Test
    void bol_ile_bol_ayniSefer_biri200_digeri409_tamIkiParca() throws Exception {
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "B" + i;
            long s = sefer(tren);
            if (i % 2 == 0) { // atamalı: makinist kilidi sırası da devrede
                v.atama(s, v.makinist("B" + i), "ASIL");
            }
            Tarayici t1 = atamaci();
            Tarayici t2 = atamaci();
            String bol = bolGovdesi(s);

            List<Yanit> y = yaris(i, post(t1, "/api/atama/sefer/" + s + "/bol", bol), post(t2, "/api/atama/sefer/" + s + "/bol", bol));

            assertThat(y.stream().map(Yanit::durum)).as("%s", y).containsExactlyInAnyOrder(200, 409);
            assertThat(y.stream().filter(r -> r.durum() == 409).findFirst().orElseThrow().kod()).isIn("VERSIYON_CAKISMASI", "SEFER_ZATEN_PARCALI", "SEFER_TEKRARI", "ATAMA_DEGISTI");
            seferTutarli(s, true, tren);
        }
    }

    // ------------------------------------------------------------------ (c) bol ∥ gecikmeGir

    @Test
    void bol_ile_gecikmeGir_biri409_ya_da_gecikme_zincire_dagitilir() throws Exception {
        int ikisiDe = 0;
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "G" + i;
            long s = sefer(tren);
            if (i % 2 == 0) {
                v.atama(s, v.makinist("G" + i), "ASIL");
            }
            Tarayici tg = atamaci();
            Tarayici tb = atamaci();
            String bol = bolGovdesi(s);
            String gecikme = "{\"kalkisGecikmeDk\":40,\"varisGecikmeDk\":40,\"neden\":\"GECIKME\",\"devam\":true}";

            List<Yanit> y = yaris(i, post(tg, "/api/sefer/" + s + "/gecikme", gecikme), post(tb, "/api/atama/sefer/" + s + "/bol", bol));

            boolean gecikmeTamam = y.get(0).durum() == 200;
            boolean bolundu = y.get(1).durum() == 200;
            assertThat(gecikmeTamam || bolundu).as("en az biri kazanır: %s", y).isTrue();
            if (!gecikmeTamam) {
                assertThat(y.get(0).kod()).as("%s", y).isEqualTo("VERSIYON_CAKISMASI");
            }
            if (!bolundu) {
                assertThat(y.get(1).kod()).as("%s", y).isEqualTo("VERSIYON_CAKISMASI");
            }
            seferTutarli(s, bolundu, tren);
            List<String> gecikmeler = jdbc.queryForList("SELECT kalkis_gecikme_dk || '/' || varis_gecikme_dk FROM sefer WHERE tren_no = ? AND tarih = ? ORDER BY coalesce(parca_no, 0)", String.class, tren, GUN);
            String beklenen = gecikmeTamam ? "40/40" : "0/0";
            assertThat(gecikmeler).as("gecikme tüm parçalara dağıtılmış (ya da hiç yazılmamış): %s", y).containsOnly(beklenen);
            if (gecikmeTamam && bolundu) {
                ikisiDe++; // bölme önce, gecikme sonra: zincire dağıtıldı
            }
        }
        System.out.println("SONUC bol-gecikme: ikisi de basarili (bolme sonra gecikme, zincire dagitildi)=" + ikisiDe + "/" + TEKRAR);
    }

    // ------------------------------------------------------------------ (d) birlestir ∥ ata (parça 2'ye) — T5.5, MK-123 (parça satırı kilidi)

    /** Bölünmüş zincir kurar (HTTP, tek seferlik); parça 2'nin kimliğini döndürür. */
    long bolunmus(long s) throws Exception {
        atamaci().post("/api/atama/sefer/" + s + "/bol", bolGovdesi(s)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        return jdbc.queryForObject("SELECT id FROM sefer WHERE parca_no = 2 AND tren_no = (SELECT tren_no FROM sefer WHERE id = ?)", Long.class, s);
    }

    String birlestirGovdesi(long id) {
        return "{\"versiyon\":" + jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id) + ",\"onizle\":false}";
    }

    /**
     * Üç varyant (tur % 3): (0) başka makinist ASIL -> {@code BIRLESTIRME_ATAMA_FARKLI}; (1) başka makinist YARDIMCI -> {@code BIRLESTIRME_ATAMA_PARCA1_BOS}; (2) AYNI makinist (A) ASIL ->
     * birleştirmeye engel değil (ata A'nın kilidinde serileşir; ya ata önce biter ve birleşince silinir ya da parça silinmiştir). Değişmez: ya birleştirme 200 ve ata yazılmadı/silindi, ya birleştirme 409 ve ata
     * parça 2'de duruyor; hiçbir yarışta kilitlenme/500, yetim atama yok (FK).
     */
    @Test
    void birlestir_ile_ata_parca2ye_ya_birlesir_ya_ataYazilir_tutarli() throws Exception {
        int birlesti = 0;
        int reddedildi = 0;
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "BA" + i;
            long s = sefer(tren);
            long a = v.makinist("BA" + i);
            long m2 = i % 3 == 2 ? a : v.makinist("BB" + i);
            String rol = i % 3 == 1 ? "YARDIMCI" : "ASIL";
            v.atama(s, a, "ASIL");
            long p2 = bolunmus(s);
            Tarayici ta = atamaci();
            Tarayici tb = atamaci();
            String birlestir = birlestirGovdesi(s);

            List<Yanit> y = yaris(i, post(tb, "/api/atama/sefer/" + s + "/birlestir", birlestir), post(ta, "/api/atama/sefer/" + p2, "{\"makinistId\":" + m2 + ",\"rol\":\"" + rol + "\",\"gerekce\":\"yarış testi\"}"));

            boolean birlesen = y.get(0).durum() == 200;
            boolean ataYazildi = y.get(1).durum() == 201;
            if (!birlesen) {
                assertThat(y.get(0).durum()).as("birleştirme: %s", y).isEqualTo(409);
                assertThat(y.get(0).kod()).as("%s", y).isIn("BIRLESTIRME_ATAMA_FARKLI", "BIRLESTIRME_ATAMA_PARCA1_BOS", "ATAMA_DEGISTI");
                reddedildi++;
            } else {
                birlesti++;
            }
            if (!ataYazildi) {
                assertThat(y.get(1).durum()).as("ata: %s", y).isIn(404, 409, 422); // parça silinmiş (404), FK/yinelenen (409) ya da birleşik satırla çakışma okuması (422: ata, birleştirme commit olduktan sonra A'nın birleşik görevini okur; hiçbir şey yazılmaz)
            }
            if (i % 3 != 2) { // farklı makinist: ya birleşir (ata yazılamaz) ya reddedilir (ata yazılmıştır)
                assertThat(birlesen ^ ataYazildi).as("tur " + i + ": ikisi birden ya da hiçbiri olmaz: %s", y).isTrue();
            }
            if (birlesen) {
                assertThat(jdbc.queryForList("SELECT coalesce(parca_no, 0) FROM sefer WHERE tren_no = ?", Integer.class, tren)).as("tek satır, parçasız: %s", y).containsExactly(0);
                assertThat(jdbc.queryForList("SELECT makinist_id || ':' || rol FROM atama WHERE sefer_id = ?", String.class, s)).as("yalnız A (ASIL): %s", y).containsExactly(a + ":ASIL");
                assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR' AND sefer_id = ?", s)).isEqualTo(1);
            } else {
                assertThat(jdbc.queryForList("SELECT parca_no FROM sefer WHERE tren_no = ? ORDER BY parca_no", Integer.class, tren)).as("zincir sağlam: %s", y).containsExactly(1, 2);
                assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", p2)).as("ata parça 2'de durur: %s", y).isEqualTo(ataYazildi ? 1 : 0);
                assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR' AND sefer_id = ?", s)).isZero();
            }
            assertThat(sayi("SELECT count(*) FROM atama a WHERE NOT EXISTS (SELECT 1 FROM sefer x WHERE x.id = a.sefer_id)")).as("yetim atama yok").isZero();
        }
        System.out.println("SONUC birlestir-ata: birlesti=" + birlesti + " reddedildi=" + reddedildi + " / " + TEKRAR);
    }

    /** Zamanlamaya bırakmadan: birleştirme makinist kilidinde beklerken parça 2'ye atama yazılıp işlem biter -> kilit alınınca yeniden okuma farkı yakalar (409 ATAMA_DEGISTI, hiçbir şey yazılmaz). */
    @Test
    void birlestir_makinistKilidindeBeklerkenParca2yeAtamaGelirse_ATAMA_DEGISTI() throws Exception {
        String tren = "BW";
        long s = sefer(tren);
        long a = v.makinist("BWA");
        v.atama(s, a, "ASIL");
        long p2 = bolunmus(s);
        Tarayici tb = atamaci();
        String birlestir = birlestirGovdesi(s);
        ExecutorService havuz = Executors.newSingleThreadExecutor();
        try {
            Future<Yanit> birlestirme = new TransactionTemplate(islemYoneticisi).execute(durum -> {
                jdbc.queryForObject("SELECT id FROM makinist WHERE id = ? FOR UPDATE", Long.class, a); // birleştirmenin kilitleyeceği makinist (MK-7)
                Future<Yanit> f = havuz.submit(post(tb, "/api/atama/sefer/" + s + "/birlestir", birlestir));
                try {
                    Thread.sleep(500); // birleştirme kilitte bekliyor (kilit zaman aşımı 5 sn)
                    assertThat(f.isDone()).as("birleştirme kilit beklemeli").isFalse();
                    v.atama(p2, a, "ASIL"); // bu işlemin commit'iyle görünür olur
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return f;
            });
            Yanit y = birlestirme.get();
            assertThat(y.durum()).as("%s", y).isEqualTo(409);
            assertThat(y.kod()).isEqualTo("ATAMA_DEGISTI");
            assertThat(jdbc.queryForList("SELECT parca_no FROM sefer WHERE tren_no = ? ORDER BY parca_no", Integer.class, tren)).containsExactly(1, 2);
            assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", p2)).isEqualTo(1);
            assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'")).isZero();
        } finally {
            havuz.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ (e) birlestir ∥ bol

    /**
     * Çift tur: (çift) zincir VAR: bölme hiçbir sıralamada kazanamaz (409), birleştirme 200; (tek) sefer parçasız: bölme (v) ile birleştirme (v+1, bölmenin vereceği sürüm) yarışır: bölme hep 200; birleştirme
     * ya bölme bitmeden okur (sürüm uyuşmaz: 409) ya bölmeden sonra çalışır (200, tur dönüşü). İkisinde de kilitlenme/500 yok, son durum iki sıradan birine denk.
     */
    @Test
    void birlestir_ile_bol_tutarliSonuc() throws Exception {
        int turDonusu = 0;
        for (int i = 0; i < TEKRAR; i++) {
            String tren = "BB" + i;
            long s = sefer(tren);
            if (i % 4 < 2) {
                v.atama(s, v.makinist("BO" + i), "ASIL"); // makinist kilidi sırası da devrede
            }
            Tarayici tb = atamaci();
            Tarayici tl = atamaci();
            if (i % 2 == 0) {
                bolunmus(s);
                String bol = bolGovdesi(s);
                String birlestir = birlestirGovdesi(s);

                List<Yanit> y = yaris(i, post(tl, "/api/atama/sefer/" + s + "/birlestir", birlestir), post(tb, "/api/atama/sefer/" + s + "/bol", bol));

                assertThat(y.get(0).durum()).as("birleştirme: %s", y).isEqualTo(200);
                assertThat(y.get(1).durum()).as("bölme: %s", y).isEqualTo(409);
                assertThat(y.get(1).kod()).isIn("VERSIYON_CAKISMASI", "SEFER_ZATEN_PARCALI", "SEFER_TEKRARI", "ATAMA_DEGISTI");
                assertThat(jdbc.queryForList("SELECT coalesce(parca_no, 0) FROM sefer WHERE tren_no = ?", Integer.class, tren)).containsExactly(0);
                assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL' AND sefer_id = ?", s)).as("yalnız kurulumdaki bölme").isEqualTo(1);
                assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR' AND sefer_id = ?", s)).isEqualTo(1);
            } else {
                long v0 = jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, s);
                String bol = bolGovdesi(s);
                String birlestir = "{\"versiyon\":" + (v0 + 1) + ",\"onizle\":false}";

                List<Yanit> y = yaris(i, post(tl, "/api/atama/sefer/" + s + "/birlestir", birlestir), post(tb, "/api/atama/sefer/" + s + "/bol", bol));

                assertThat(y.get(1).durum()).as("bölme hep kazanır: %s", y).isEqualTo(200);
                boolean birlesti = y.get(0).durum() == 200;
                if (!birlesti) {
                    assertThat(y.get(0).kod()).as("%s", y).isIn("VERSIYON_CAKISMASI", "SEFER_PARCALI_DEGIL");
                }
                assertThat(jdbc.queryForList("SELECT coalesce(parca_no, 0) FROM sefer WHERE tren_no = ? ORDER BY 1", Integer.class, tren)).as("%s", y).isEqualTo(birlesti ? List.of(0) : List.of(1, 2));
                assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR' AND sefer_id = ?", s)).isEqualTo(birlesti ? 1 : 0);
                if (birlesti) {
                    turDonusu++;
                }
            }
            assertThat(sayi("SELECT count(*) FROM atama a WHERE NOT EXISTS (SELECT 1 FROM sefer x WHERE x.id = a.sefer_id)")).as("yetim atama yok").isZero();
        }
        System.out.println("SONUC birlestir-bol: tek turlarda birlesti (bolme sonra birlestirme)=" + turDonusu + "/" + TEKRAR / 2);
    }

    // ------------------------------------------------------------------ (f) birlestir ∥ ata (ince tarama, BUG-1) ve birlestir ∥ gecikme/iptal(devam) (kilit sırası)

    /** İnce tarama / kilit sırası turu: kalıcı 60; kapanış doğrulamasında {@code -DyarisTur=200+} ile artırılır. */
    static final int INCE_TUR = Integer.getInteger("yarisTur", 60);

    /**
     * QA (BUG-1): ata(B -> parça 2, YARDIMCI) birleştirmeden ÖNCE başlar, birleştirme 1..45 ms sonra gelir (ince tarama). Hibernate'in PESSIMISTIC_WRITE'ı PostgreSQL'de {@code FOR NO KEY UPDATE} yazar;
     * bu ata'nın FK {@code KEY SHARE}'iyle çakışmaz. Birleştirme parça satırlarını gerçek {@code FOR UPDATE} ile kilitler. Değişmez: birleştirme 200 ise ata 201 OLAMAZ (A'dan farklı makinistin ataması
     * sessizce silinirdi) ve tersi; atama yazıldıysa zincir ve atama yerinde durur.
     */
    @Test
    void birlestir_ile_ata_ince_tarama_ikisiBirdenBasariliOlmaz() throws Exception {
        int ihlal = 0;
        StringBuilder rapor = new StringBuilder();
        for (int i = 0; i < INCE_TUR; i++) {
            long gecikmeUs = (1 + i % 45) * 1000L;
            String tren = "QS" + i;
            long s = sefer(tren);
            long a = v.makinist("QSA" + i);
            long b = v.makinist("QSB" + i);
            v.atama(s, a, "ASIL");
            long p2 = bolunmus(s);
            Tarayici ta = atamaci();
            Tarayici tb = atamaci();
            String birlestir = birlestirGovdesi(s);
            List<Yanit> y = yaris(0, post(ta, "/api/atama/sefer/" + p2, "{\"makinistId\":" + b + ",\"rol\":\"YARDIMCI\",\"gerekce\":\"qa\"}"), () -> {
                Thread.sleep(gecikmeUs / 1000);
                return post(tb, "/api/atama/sefer/" + s + "/birlestir", birlestir).call();
            });
            boolean ataYazildi = y.get(0).durum() == 201;
            boolean birlesen = y.get(1).durum() == 200;
            if (ataYazildi && birlesen) {
                ihlal++;
                rapor.append("tur ").append(i).append(" gecikme=").append(gecikmeUs / 1000).append("ms y=").append(y).append(" atamaB_kalan=").append(sayi("SELECT count(*) FROM atama WHERE makinist_id = ?", b)).append('\n');
            }
            if (ataYazildi) {
                assertThat(sayi("SELECT count(*) FROM atama WHERE makinist_id = ?", b)).as("yazılan atama kaybolmaz: %s", y).isEqualTo(1);
            }
            assertThat(sayi("SELECT count(*) FROM atama a WHERE NOT EXISTS (SELECT 1 FROM sefer x WHERE x.id = a.sefer_id)")).as("yetim atama yok: %s", y).isZero();
        }
        assertThat(ihlal).as("ata 201 + birleştirme 200 (B'nin ataması sessizce silindi):%n%s", rapor).isZero();
    }

    /** Birleştir ∥ gecikme(devam) / iptal(devam): her ikisi de zinciri parça sırasıyla (parça 1 dahil) kilitler -> kilitlenme (409 ISLEM_SURUYOR) yok; son durum iki sıradan birine denk. */
    @Test
    void birlestir_ile_gecikmeDevam_kilitlenmeYok() throws Exception {
        for (int i = 0; i < INCE_TUR; i++) {
            String tren = "KG" + i;
            long s = sefer(tren);
            if (i % 2 == 0) {
                v.atama(s, v.makinist("KG" + i), "ASIL");
            }
            bolunmus(s);
            Tarayici tg = atamaci();
            Tarayici tb = atamaci();
            List<Yanit> y = yaris(i, post(tg, "/api/sefer/" + s + "/gecikme", "{\"kalkisGecikmeDk\":20,\"varisGecikmeDk\":20,\"neden\":\"GECIKME\",\"devam\":true}"),
                    post(tb, "/api/atama/sefer/" + s + "/birlestir", birlestirGovdesi(s)));
            zincirTutarli(tren, y);
        }
    }

    @Test
    void birlestir_ile_iptalDevam_kilitlenmeYok() throws Exception {
        for (int i = 0; i < INCE_TUR; i++) {
            String tren = "KI" + i;
            long s = sefer(tren);
            if (i % 2 == 0) {
                v.atama(s, v.makinist("KI" + i), "ASIL");
            }
            bolunmus(s);
            Tarayici ti = atamaci();
            Tarayici tb = atamaci();
            List<Yanit> y = yaris(i, post(ti, "/api/sefer/" + s + "/iptal", "{\"neden\":\"IPTAL\",\"devam\":true}"), post(tb, "/api/atama/sefer/" + s + "/birlestir", birlestirGovdesi(s)));
            zincirTutarli(tren, y);
        }
    }

    /** Birleşik tek satır ya da bozulmamış 2 parça; atama yetimi yok. */
    private void zincirTutarli(String tren, List<Yanit> y) {
        List<Integer> parcaNolar = jdbc.queryForList("SELECT coalesce(parca_no, 0) FROM sefer WHERE tren_no = ? ORDER BY 1", Integer.class, tren);
        assertThat(parcaNolar).as("%s", y).isIn(List.of(0), List.of(1, 2));
        assertThat(sayi("SELECT count(*) FROM atama a WHERE NOT EXISTS (SELECT 1 FROM sefer x WHERE x.id = a.sefer_id)")).as("yetim atama yok: %s", y).isZero();
    }
}
