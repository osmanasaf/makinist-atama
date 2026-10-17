package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.atama.ParcaliAnkaraTren;
import com.makinistatama.sefer.SeferYuklemeServisi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MK-5 değişmezi, takas için: kabul edilen (iki yönlü ENGEL'siz) takas hemen ardından HİÇBİR atamayı kırmızı yapmaz. Her tur iki makinistin rastgele (gece ağırlıklı) görevleri atamacı API'siyle
 * yazılır (reddedilenler atlanır: başlangıç durumu kırmızısızdır), sonra rastgele bir görev çifti takas edilir; her başarılı onaydan sonra tüm aralıkta kırmızı atama sayısı 0 olmalıdır.
 * Reddedilen takaslarda (ön kontrol / onay 422) satırlar değişmez.
 */
class TakasMk5Test extends TakasTabani {

    @Autowired
    SeferYuklemeServisi yukleme;

    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    private static JsonNode json(String s) {
        return new JsonMapper().readTree(s);
    }

    private long rastgeleSefer(Random r, String ad) {
        LocalDate gun = gun(2 + r.nextInt(4)); // ufuk içinde, 12 saatten çok ileride
        String kalkis = SAATLER[r.nextInt(SAATLER.length)];
        String varis = LocalTime.parse(kalkis).plusMinutes(SURELER[r.nextInt(SURELER.length)]).toString();
        return v.seferEv(ad, gun, kalkis, varis);
    }

    /** Makinistin 2–4 rastgele görevi, atamacı API'siyle (ENGEL'liler yazılamaz → başlangıç kırmızısız). Döner: yazılan atama kimlikleri. */
    private List<Long> goreviYaz(Tarayici atamaci, Random r, long makinist, String onEk) throws Exception {
        List<Long> yazilan = new ArrayList<>();
        int adet = 2 + r.nextInt(3);
        for (int i = 0; i < adet; i++) {
            long sefer = rastgeleSefer(r, onEk + i);
            int kod = atamaci.post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + makinist + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus();
            if (kod == 201) {
                yazilan.add(jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sefer));
            }
        }
        return yazilan;
    }

    @Test
    void kabulEdilenTakasHicbirAtamayiKirmiziYapmaz_rastgele() throws Exception {
        v = new AtamaVeri(jdbc);
        Tarayici atamaci = atamaci();
        Random r = new Random(41);
        int onaylanan = 0;
        int onKontrolRed = 0;
        int onayRed = 0;
        for (int tur = 0; tur < 40; tur++) {
            String sa = "A" + String.format("%02d", tur);
            String sb = "B" + String.format("%02d", tur);
            long a = hesapli(sa);
            long b = hesapli(sb);
            List<Long> gorevlerA = goreviYaz(atamaci, r, a, "XA" + tur + "_");
            List<Long> gorevlerB = goreviYaz(atamaci, r, b, "XB" + tur + "_");
            if (gorevlerA.isEmpty() || gorevlerB.isEmpty()) {
                continue;
            }
            long ataA = gorevlerA.get(r.nextInt(gorevlerA.size()));
            long ataB = gorevlerB.get(r.nextInt(gorevlerB.size()));
            Tarayici ta = giris(sa);
            Tarayici tb = giris(sb);
            var acma = ta.post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andReturn().getResponse();
            if (acma.getStatus() != 201) {
                assertThat(acma.getStatus()).as("tur %d: açılış yalnız ENGEL ön kontrolüyle ya da çakışma/aynı sefer süzgeciyle reddedilir: %s", tur, acma.getContentAsString()).isIn(422, 400, 404, 409);
                onKontrolRed++;
                continue;
            }
            long talep = json(acma.getContentAsString()).get("id").asLong();
            tb.post("/api/benim/takas/" + talep + "/kabul", "").andReturn();
            String atamalarOnce = jdbc.queryForList("SELECT sefer_id || ':' || makinist_id || ':' || rol FROM atama ORDER BY sefer_id, rol", String.class).toString();
            var onay = atamaci.post("/api/takas/" + talep + "/onay", "{\"gerekce\":\"deneme\"}").andReturn().getResponse();
            if (onay.getStatus() == 422) {
                onayRed++;
                assertThat(jdbc.queryForList("SELECT sefer_id || ':' || makinist_id || ':' || rol FROM atama ORDER BY sefer_id, rol", String.class).toString()).as("tur %d: reddedilen takas satır değiştirmez", tur)
                        .isEqualTo(atamalarOnce);
                continue;
            }
            assertThat(onay.getStatus()).as("tur %d: %s", tur, onay.getContentAsString()).isEqualTo(200);
            onaylanan++;
            JsonNode sayac = json(atamaci.get("/api/atama/kirmizi-sayisi?bas=" + gun(0) + "&bit=" + gun(10)).andReturn().getResponse().getContentAsString());
            assertThat(sayac.get("kirmiziAtama").asInt()).as("tur %d: takas sonrası kırmızı atama (A %s, B %s)", tur, sa, sb).isZero();
        }
        System.out.println("TAKAS-MK5: onaylanan " + onaylanan + ", açılışta reddedilen " + onKontrolRed + ", onayda 422 " + onayRed);
        assertThat(onaylanan).as("onaylanan takas sayısı (testin anlamlı olması için)").isGreaterThanOrEqualTo(5);
        assertThat(onKontrolRed + onayRed).as("reddedilen takas sayısı").isGreaterThanOrEqualTo(3);
    }

    /** W2: köprü senaryosu. A'nın tek nöbetlik 3 görevi (aralar 1,5-3,5 s) zincir kurar; zincirin rastgele bir halkası B'nin sonraki gün görevleriyle takas edilir. Onaylanan her takasta kırmızı 0. */
    @Test
    void kopruGorevTakasi_rastgele_kirmiziUretmez() throws Exception {
        v = new AtamaVeri(jdbc);
        Tarayici atamaci = atamaci();
        Random r = new Random(7);
        int onaylanan = 0;
        int reddedilen = 0;
        for (int tur = 0; tur < 30; tur++) {
            String sa = "KA" + String.format("%02d", tur);
            String sb = "KB" + String.format("%02d", tur);
            long a = hesapli(sa);
            long b = hesapli(sb);
            LocalDate gun = gun(2 + r.nextInt(3));
            LocalTime bas = LocalTime.of(5 + r.nextInt(5), 5 * r.nextInt(12));
            List<Long> zincir = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                LocalTime bit = bas.plusMinutes(60 + r.nextInt(45));
                long sefer = v.seferEv("KZ" + tur + "_" + i, gun, bas.toString(), bit.toString());
                if (atamaci.post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + a + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus() == 201) {
                    zincir.add(jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sefer));
                }
                bas = bit.plusMinutes(90 + r.nextInt(120));
            }
            long sB = v.seferEv("KY" + tur, gun(6 + r.nextInt(2)), SAATLER[r.nextInt(SAATLER.length)], "23:50");
            if (zincir.size() < 2 || atamaci.post("/api/atama/sefer/" + sB, "{\"makinistId\":" + b + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus() != 201) {
                continue;
            }
            long ataB = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sB);
            var acma = giris(sa).post("/api/benim/takas", acGovdesi(zincir.get(r.nextInt(zincir.size())), ataB, "IZIN", null)).andReturn().getResponse();
            if (acma.getStatus() != 201) {
                reddedilen++;
                continue;
            }
            long talep = json(acma.getContentAsString()).get("id").asLong();
            giris(sb).post("/api/benim/takas/" + talep + "/kabul", "").andReturn();
            int kod = atamaci.post("/api/takas/" + talep + "/onay", "{\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus();
            if (kod != 200) {
                reddedilen++;
                continue;
            }
            onaylanan++;
            JsonNode sayac = json(atamaci.get("/api/atama/kirmizi-sayisi?bas=" + gun(0) + "&bit=" + gun(10)).andReturn().getResponse().getContentAsString());
            assertThat(sayac.get("kirmiziAtama").asInt()).as("tur %d: köprü takası sonrası kırmızı atama (A %s, B %s)", tur, sa, sb).isZero();
        }
        System.out.println("TAKAS-MK5-KOPRU: onaylanan " + onaylanan + ", reddedilen " + reddedilen);
        assertThat(onaylanan).as("onaylanan").isGreaterThanOrEqualTo(3);
        assertThat(reddedilen).as("reddedilen (köprü yakalandı)").isGreaterThanOrEqualTo(3);
    }

    /**
     * F2d Tur 3 / W2: parçalı tren modu. A aynı trenin iki parçasını (ara 10 dk) ve bir rastgele görev taşır; hazırlık 15 / alıkoyma 10 dk. Parçalardan biri B'nin rastgele görevi ile takas
     * edilir (parça bırakılınca komşu parçanın hazırlık/alıkoyması geri gelir; parça alınınca bağ kurulur). Onaylanan her takasta kırmızı atama 0.
     */
    @Test
    void parcaliTrenTakasi_rastgele_kirmiziUretmez() throws Exception {
        v = new AtamaVeri(jdbc);
        Tarayici atamaci = atamaci();
        Random r = new Random(23);
        int onaylanan = 0;
        int reddedilen = 0;
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar = 'gorev.hazirlik_dk'"); // sabit: parametre turlar arasında değişirse önceki turların atamaları kırmızıya döner
        jdbc.update("UPDATE kural_parametre SET deger = '10' WHERE anahtar = 'gorev.alikoyma_dk'");
        for (int tur = 0; tur < 30; tur++) {
            String sa = "PA" + String.format("%02d", tur);
            String sb = "PB" + String.format("%02d", tur);
            long a = hesapli(sa);
            long b = hesapli(sb);
            LocalDate gun = gun(2 + r.nextInt(3));
            LocalTime k1 = LocalTime.of(6 + r.nextInt(8), 5 * r.nextInt(12));
            LocalTime v1 = k1.plusMinutes(60 + r.nextInt(90));
            LocalTime k2 = v1.plusMinutes(10);
            LocalTime v2 = k2.plusMinutes(60 + r.nextInt(120));
            long[] p = ParcaliAnkaraTren.yukle(yukleme, jdbc, "PT" + tur, gun.toString(), k1.toString(), v1.toString(), k2.toString(), v2.toString());
            List<Long> parcalar = new ArrayList<>();
            for (long sefer : p) {
                if (atamaci.post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + a + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus() == 201) {
                    parcalar.add(jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sefer));
                }
            }
            LocalTime xk = LocalTime.of(5 + r.nextInt(14), 5 * r.nextInt(12));
            long sX = v.seferEv("PX" + tur, gun, xk.toString(), xk.plusMinutes(45 + r.nextInt(120)).toString());
            if (parcalar.isEmpty() || atamaci.post("/api/atama/sefer/" + sX, "{\"makinistId\":" + b + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus() != 201) {
                continue;
            }
            long ataX = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, sX);
            var acma = giris(sa).post("/api/benim/takas", acGovdesi(parcalar.get(r.nextInt(parcalar.size())), ataX, "IZIN", null)).andReturn().getResponse();
            if (acma.getStatus() != 201) {
                reddedilen++;
                continue;
            }
            long talep = json(acma.getContentAsString()).get("id").asLong();
            giris(sb).post("/api/benim/takas/" + talep + "/kabul", "").andReturn();
            if (atamaci.post("/api/takas/" + talep + "/onay", "{\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus() != 200) {
                reddedilen++;
                continue;
            }
            onaylanan++;
            JsonNode sayac = json(atamaci.get("/api/atama/kirmizi-sayisi?bas=" + gun(0) + "&bit=" + gun(10)).andReturn().getResponse().getContentAsString());
            assertThat(sayac.get("kirmiziAtama").asInt()).as("tur %d: parçalı takas sonrası kırmızı atama (A %s, B %s)", tur, sa, sb).isZero();
        }
        System.out.println("TAKAS-MK5-PARCALI: onaylanan " + onaylanan + ", reddedilen " + reddedilen);
        assertThat(onaylanan).as("onaylanan").isGreaterThanOrEqualTo(3);
    }
}
