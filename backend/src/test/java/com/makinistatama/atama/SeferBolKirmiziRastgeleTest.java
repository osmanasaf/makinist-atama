package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MK-5 / MK-124 rastgele modu (T3.6, FR-07): sabit tohumlu rastgele çözümde (gece ağırlıklı, gecikmeli seferler, çoğu kural dışı atamalar) rastgele sefer rastgele geçerli noktalarla bölünür;
 * her bölmede, BAĞIMSIZ hesapla ({@code /api/atama/gun} kırmızı bayrağı = ENGEL düzeyi ihlal, tüm günler):
 * (i) yanıttaki {@code yeniKirmizi} = bölme öncesi/sonrası kırmızı farkı; bölmenin kırmızı yapmadığı atama kırmızı olmaz;
 * (ii) hiçbir atama satırı değişmez (id, sefer, rol, makinist, kalkis_gecikme_dk, atayan, gerekçe);
 * (iii) bölünmüş zincirin parça 2'sine aday "uygun" + POST kabul => ardından kırmızı değil (MK-5; parça 1'in makinistinin devamı = OK-17 zincir bağı dahil).
 */
class SeferBolKirmiziRastgeleTest extends TestVeritabani {

    static final LocalDate BAS = LocalDate.of(2026, 10, 12); // "şimdi" 10.10 10:00: tüm seferler gelecekte
    static final int GUN_SAYISI = 5;
    static final int MAKINIST = 16;
    static final int SEFER = 80;
    static final int BOLME = 60;
    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    final JsonMapper json = new JsonMapper();

    // sayaçlar (testin anlamlı olduğunu kanıtlar)
    int bolme;
    int yeniKirmiziToplam;
    int bolmeYeniKirmiziLi;
    int kabul;
    int kabulDevam;
    int reddedilen;

    @Test
    void k23Kapali_gecikmeli() throws Exception {
        dene(false, true, 101);
    }

    /** K-23 (asgari aktarma 20 dk, ENGEL) açık: kısalan görev sonrası görevle aktarmayı kısaltıp/uzatıp kırmızıyı değiştirir. */
    @Test
    void k23Acik_gecikmeli() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '20' WHERE anahtar = 'nobet.asgari_aktarma_dk'");
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-23'");
        dene(true, true, 103);
    }

    private void dene(boolean k23, boolean gecikmeli, long tohum) throws Exception {
        jdbc.update("UPDATE kural_siddet SET siddet = 'ENGEL' WHERE kod = 'K-20'"); // konum uyuşmazlığı varsayılan UYARI: kırmızı = ENGEL (testler arası tohuma döner)
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] noktaGar = { TestTanimlari.gar(jdbc, "Sivas"), TestTanimlari.gar(jdbc, "Kars"), TestTanimlari.gar(jdbc, "Konya"), TestTanimlari.gar(jdbc, "Erzurum") };
        Random r = new Random(tohum);
        long[] makinistler = new long[MAKINIST];
        for (int i = 0; i < MAKINIST; i++) {
            makinistler[i] = v.makinist("R" + i);
        }
        List<Long> seferler = new ArrayList<>();
        for (int i = 0; i < SEFER; i++) {
            long s = rastgeleSefer(v, r, "S" + i);
            seferler.add(s);
            if (gecikmeli && r.nextInt(100) < 35 && r.nextBoolean()) {
                gecikme(s, r); // atamadan ÖNCE: atama anındaki gecikme kaydedilir (OK-6)
            }
            atamalar(v, r, s, makinistler);
            if (gecikmeli && r.nextInt(100) < 35) {
                gecikme(s, r); // atamadan SONRA (çoğu seferde ikinci kez: üzerine yazar)
            }
        }
        Tarayici t = atamaci();
        Map<Long, List<String>> once = kirmizilar(t);
        int hamKirmizi = once.size();
        assertThat(hamKirmizi).as("çözümde kırmızı atama var (anlamlı)").isGreaterThan(0);

        List<Long> bolunecek = new ArrayList<>(seferler);
        java.util.Collections.shuffle(bolunecek, r);
        for (int tur = 0; tur < BOLME; tur++) {
            long sefer = bolunecek.get(tur);
            List<Map<String, Object>> atamaOnce = jdbc.queryForList("SELECT * FROM atama ORDER BY id");

            String govde = bolGovdesi(r, sefer, noktaGar);
            String yanit = t.post("/api/atama/sefer/" + sefer + "/bol", govde).andReturn().getResponse().getContentAsString();
            JsonNode y = json.readTree(yanit);
            assertThat(y.has("parcalar")).as("tur %d bölme 200 beklenir: %s | %s", tur, yanit, govde).isTrue();
            bolme++;

            // (ii) hiçbir atama satırı değişmez
            assertThat(jdbc.queryForList("SELECT * FROM atama ORDER BY id")).as("tur %d: atama tablosu değişmez", tur).isEqualTo(atamaOnce);

            // (i) bağımsız fark
            Map<Long, List<String>> sonra = kirmizilar(t);
            Set<Long> beklenen = new TreeSet<>(sonra.keySet());
            beklenen.removeAll(once.keySet());
            Set<Long> yanitKumesi = new TreeSet<>();
            for (JsonNode e : y.get("yeniKirmizi")) {
                assertThat(yanitKumesi.add(e.get("atamaId").asLong())).as("tur %d: yeniKirmizi tekrarsız", tur).isTrue();
                assertThat(e.get("nedenler").size()).isGreaterThan(0);
            }
            if (!yanitKumesi.equals(beklenen)) { // tanı yalnız uyuşmazlıkta hesaplanır
                org.assertj.core.api.Assertions.fail("tur %d (sefer %d): yeniKirmizi = gün listesi kırmızı farkı\nyanıt kümesi=%s\ngün listesi farkı=%s\nyanıt=%s\natamalar=%s", tur, sefer, yanitKumesi, beklenen, y.get("yeniKirmizi"),
                        jdbc.queryForList("SELECT a.id, a.sefer_id, a.rol, a.makinist_id, a.kalkis_gecikme_dk AS a_gec, s.tren_no, s.tarih, s.parca_no, s.planlanan_kalkis, s.planlanan_varis, s.kalkis_gecikme_dk, s.varis_gecikme_dk "
                                + "FROM atama a JOIN sefer s ON s.id = a.sefer_id WHERE a.makinist_id IN (SELECT makinist_id FROM atama WHERE id = ANY(?)) ORDER BY a.makinist_id, s.planlanan_kalkis",
                                (Object) java.util.stream.Stream.concat(yanitKumesi.stream(), beklenen.stream()).toArray(Long[]::new)));
            }
            yeniKirmiziToplam += yanitKumesi.size();
            if (!yanitKumesi.isEmpty()) {
                bolmeYeniKirmiziLi++;
            }
            once = sonra;

            // (iii) bölünmüş zincirin parça 2'sine aday uygun + POST kabul => kırmızı değil; devam makinisti (OK-17) %50
            JsonNode parcalar = y.get("parcalar");
            JsonNode parca2 = parcalar.get(1 + r.nextInt(parcalar.size() - 1));
            long p2 = parca2.get("id").asLong();
            List<Long> p1Asil = jdbc.queryForList("SELECT makinist_id FROM atama WHERE sefer_id = ? AND rol = 'ASIL'", Long.class, sefer);
            boolean devamIstendi = !p1Asil.isEmpty() && r.nextBoolean();
            JsonNode adaylar = json.readTree(t.get("/api/atama/sefer/" + p2 + "/adaylar").andReturn().getResponse().getContentAsString());
            List<Long> uygunlar = new ArrayList<>();
            for (JsonNode a : adaylar) {
                if (a.get("uygun").asBoolean()) {
                    uygunlar.add(a.get("makinistId").asLong());
                }
            }
            List<Long> uygunDegiller = new ArrayList<>();
            for (JsonNode a : adaylar) {
                if (!a.get("uygun").asBoolean()) {
                    uygunDegiller.add(a.get("makinistId").asLong());
                }
            }
            if (!uygunDegiller.isEmpty()) { // aday listesi ile POST tutarlı: uygun değil -> 422, hiçbir şey yazılmaz
                long red = uygunDegiller.get(r.nextInt(uygunDegiller.size()));
                assertThat(t.post("/api/atama/sefer/" + p2, AtamaVeri.ataGovdesi(red)).andReturn().getResponse().getStatus()).as("tur %d: uygun değil -> 422", tur).isEqualTo(422);
                reddedilen++;
            }
            boolean uygun = !uygunlar.isEmpty();
            boolean devam = devamIstendi && uygunlar.contains(p1Asil.getFirst());
            long m = devam ? p1Asil.getFirst() : uygun ? uygunlar.get(r.nextInt(uygunlar.size())) : -1;
            if (!uygun) {
                continue; // hiç uygun aday yok (yukarıda 422 doğrulandı)
            }
            String ata = t.post("/api/atama/sefer/" + p2, AtamaVeri.ataGovdesi(m).replace("}", ",\"gerekce\":\"deneme\"}")).andReturn().getResponse().getContentAsString();
            long yeniAtama = json.readTree(ata).get("atamaId").asLong();
            kabul++;
            if (devam) {
                kabulDevam++;
            }
            sonra = kirmizilar(t);
            assertThat(sonra).as("tur %d: kabul edilen atama (%d, parça 2 = %d, makinist %d, devam=%s) kırmızı olmamalı", tur, yeniAtama, p2, m, devam).doesNotContainKey(yeniAtama);
            once = sonra;
        }
        System.out.printf("SONUC k23=%s gecikmeli=%s: bolme=%d yeniKirmizi(toplam)=%d yeniKirmiziLi bolme=%d | kabul=%d (devam=%d) red=%d | ham kirmizi atama=%d%n", k23, gecikmeli, bolme, yeniKirmiziToplam,
                bolmeYeniKirmiziLi, kabul, kabulDevam, reddedilen, hamKirmizi);
        assertThat(bolme).isGreaterThanOrEqualTo(60);
        assertThat(kabul).as("kabul edilen parça-2 ataması (anlamlı)").isGreaterThan(5);
        assertThat(reddedilen).isGreaterThan(5);
    }

    /** Gün listesinden (tüm günler) kırmızı atamalar: atamaId -> nedenler. */
    private Map<Long, List<String>> kirmizilar(Tarayici t) throws Exception {
        Map<Long, List<String>> sonuc = new TreeMap<>();
        for (int g = 0; g <= GUN_SAYISI; g++) { // +1 gün: gece yarısını aşan parça 2 ertesi günde listelenir
            JsonNode liste = json.readTree(t.get("/api/atama/gun?tarih=" + BAS.plusDays(g)).andReturn().getResponse().getContentAsString());
            for (JsonNode s : liste.get("satirlar")) {
                for (JsonNode gorevli : s.get("makinistler")) {
                    if (gorevli.get("kirmizi").asBoolean()) {
                        List<String> nedenler = new ArrayList<>();
                        gorevli.get("kirmiziNedenler").forEach(n -> nedenler.add(n.asString()));
                        sonuc.put(gorevli.get("atamaId").asLong(), nedenler);
                    }
                }
            }
        }
        return sonuc;
    }

    private void atamalar(AtamaVeri v, Random r, long sefer, long[] makinistler) {
        int sayi = r.nextInt(100);
        if (sayi < 20) {
            return; // atamasız
        }
        Set<Long> kullanilan = new HashSet<>();
        long asil = makinistler[r.nextInt(MAKINIST)];
        kullanilan.add(asil);
        v.atama(sefer, asil, "ASIL");
        if (r.nextInt(100) < 30) {
            long y = makinistler[r.nextInt(MAKINIST)];
            if (kullanilan.add(y)) {
                v.atama(sefer, y, "YARDIMCI");
            }
        }
        if (r.nextInt(100) < 10) {
            long yolcu = makinistler[r.nextInt(MAKINIST)];
            if (kullanilan.add(yolcu)) {
                v.atama(sefer, yolcu, "YOLCU");
            }
        }
    }

    /** Kalkış 30–180 dk, varış en az kalkış kadar gecikir (BUG-11 kısıtı). */
    private void gecikme(long sefer, Random r) {
        int k = 30 * (1 + r.nextInt(6));
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = ?, varis_gecikme_dk = ? WHERE id = ?", k, k + 30 * r.nextInt(3), sefer);
    }

    /** Çoğu Ankara-Ankara (makinist evinde biter), bir kısmı Ankara-Eskişehir; gece ağırlıklı saatler. */
    private long rastgeleSefer(AtamaVeri v, Random r, String ad) {
        LocalDate gun = BAS.plusDays(r.nextInt(GUN_SAYISI));
        String kalkis = SAATLER[r.nextInt(SAATLER.length)];
        String varis = LocalTime.parse(kalkis).plusMinutes(SURELER[r.nextInt(SURELER.length)]).toString();
        return r.nextInt(100) < 75 ? v.seferEv(ad, gun, kalkis, varis) : v.sefer(ad, gun, kalkis, varis);
    }

    /** 1–3 geçerli ara nokta: sefer süresini eşit dilimlere böler, her dilimde rastgele konum ve bekleme; km seferin 250 km'sine orantılı, kesin artan. */
    private String bolGovdesi(Random r, long sefer, long[] garlar) {
        Map<String, Object> s = jdbc.queryForMap("SELECT versiyon, planlanan_kalkis, planlanan_varis FROM sefer WHERE id = ?", sefer);
        Instant k = ((java.sql.Timestamp) s.get("planlanan_kalkis")).toInstant();
        long toplam = ChronoUnit.MINUTES.between(k, ((java.sql.Timestamp) s.get("planlanan_varis")).toInstant());
        LocalTime kalkis = k.atZone(AtamaVeri.ISTANBUL).toLocalTime();
        int n = 1 + r.nextInt(toplam >= 180 ? 3 : toplam >= 100 ? 2 : 1);
        double dilim = (toplam - 40.0) / n;
        Set<Integer> kullanilanGar = new HashSet<>();
        List<String> noktalar = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int bekleme = dilim < 40 ? 5 : 5 + r.nextInt(11);
            int konum = (int) (20 + i * dilim + dilim * 0.1 + r.nextDouble() * (dilim * 0.8 - bekleme - 1));
            if (kalkis.plusMinutes(konum + bekleme).isBefore(kalkis.plusMinutes(konum))) {
                bekleme = 0; // API: noktada gece yarısını aşan bekleme kabul edilmez (kalkış < varış = yazım hatası)
            }
            int gar = r.nextInt(garlar.length);
            while (!kullanilanGar.add(gar)) {
                gar = r.nextInt(garlar.length);
            }
            noktalar.add("{\"garId\":" + garlar[gar] + ",\"varisSaat\":\"" + kalkis.plusMinutes(konum) + "\",\"kalkisSaat\":\"" + kalkis.plusMinutes(konum + bekleme) + "\",\"km\":" + Math.round(250.0 * konum / toplam)
                    + "}");
        }
        return "{\"versiyon\":" + s.get("versiyon") + ",\"noktalar\":[" + String.join(",", noktalar) + "],\"onizle\":false}";
    }
}
