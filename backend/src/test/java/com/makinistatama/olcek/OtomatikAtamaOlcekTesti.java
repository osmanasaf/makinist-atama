package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * NFR-02: 40 makinist x 31 gün x günde 100 sefer (3.100 sefer = 124.000 değerlendirme) için otomatik atama ÖNİZLEMESİ ≤ 30 sn (tek gün ≤ 3 sn).
 * Çalıştırma: {@code mvnw test -Pyavas "-Dtest=OtomatikAtamaOlcekTesti"}.
 */
@Tag("yavas")
class OtomatikAtamaOlcekTesti extends TestVeritabani {

    static final int MAKINIST = 40;
    static final int GUN = 31;
    static final int GUNLUK_SEFER = 100;
    static final LocalDate ILK = LocalDate.of(2026, 10, 12);

    @Test
    void kirkMakinist_otuzBirGun_onizleme_otuzSaniyedeAz_tekGunUcSaniyedeAz() throws Exception {
        olc(false, false, false);
    }

    /** T3.5 (NFR-02): aynı yük, 10 açık özel kuralla (tek sefer, gün, hafta, ay, yıl, kayan 30, seri, izin günü, görevsiz aralık, nöbetler arası dinlenme); hedef 15 sn (yumuşak, çıktıya yazılır), tavan 30 sn, tek gün 3 sn. */
    @Test
    void kirkMakinist_otuzBirGun_onOzelKuralla_onizleme_otuzSaniyedeAz() throws Exception {
        olc(true, false, false);
    }

    /**
     * Tur 2 (MK-5 düzeltmesi): yıl penceresi olan tek kural + ARALIK ayında 3.100 sefer zaten atanmış. Yıl kuralı ileri yüklemeyi yıl sonuna uzattığı için bu
     * atamalar her adayın penceresindedir; maliyet ölçülür (hedef 15 sn yumuşak, tavan 30 sn).
     */
    @Test
    void kirkMakinist_otuzBirGun_yilKuraliVeAralikAtamalari_onizleme_otuzSaniyedeAz() throws Exception {
        olc(false, true, false);
    }

    /**
     * Tur 3 (W3): Faz 1'den kalan K-15 yıl yükü. K-15 varsayılan ENGEL olduğundan her aday yıl başından beri tüm atamalarla değerlendirilir; gerçek kullanımda geçmiş
     * DOLUDUR. 8 ay geçmiş (günde 100 sefer, 40 makinist, ≈ 24.000 atama) ile 31 gün önizleme ≤ 30 sn (reviewer prototipi 22 sn; tembel doğrulamadan önce 68 sn).
     */
    @Test
    void kirkMakinist_sekizAyGecmisAtamali_onizleme_otuzSaniyedeAz() throws Exception {
        olc(false, false, true);
    }

    /** Tur 3 (W3): en kötü bileşim: 8 ay geçmiş + yıl kuralı + Aralık tamamı atanmış (yıl kuralı ileri yüklemeyi yıl sonuna uzatır). */
    @Test
    void kirkMakinist_sekizAyGecmisVeYilKurali_onizleme_otuzSaniyedeAz() throws Exception {
        olc(false, true, true);
    }

    /**
     * F2b (MK-42, NFR-02): aynı 8 ay geçmiş yükü, her gün 2 ek sefer (Ankara -> Eskişehir 05:00, Eskişehir -> Ankara 08:00: garda bekleyen yok, hedefler yalnız yolculukla
     * doldurulabilir) ve yolculuk bayrağı AÇIK. Ölçüt: toplam <= 30 sn VE aşama maliyeti (açık - kapalı, aynı çalışmada) <= 5 sn; aşama gerçekten tetiklenmeli.
     */
    @Test
    void kirkMakinist_sekizAyGecmisAtamali_yolculukAcik_onizleme_otuzSaniyedeAz_asamaBesSaniyedenAz() throws Exception {
        olc(false, false, true, true);
    }

    /** F2b: en kötü bileşim (8 ay geçmiş + yıl kuralı + Aralık atamaları) yolculuk açıkken de <= 30 sn, aşama maliyeti <= 5 sn. */
    @Test
    void kirkMakinist_sekizAyGecmisVeYilKurali_yolculukAcik_onizleme_otuzSaniyedeAz_asamaBesSaniyedenAz() throws Exception {
        olc(false, true, true, true);
    }

    /**
     * F2d (MK-66..69, T5.2): opt-in "iyileştir". Geçmişsiz ölçek (ölçülen 268 -> 3): boş <= 30 (plan kapısı 100), önizleme <= 20 sn, anahtarlı uygula <= 1,25x düz uygula, yeni kırmızı yok.
     */
    @Test
    void iyilestir_kirkMakinist_otuzBirGun_gecmissiz() throws Exception {
        iyilestirVerisi(false, false);
        IyilestirOlcum.olc(atamaci(), jdbc, "ölçek geçmişsiz", "2026-10-12", "2026-11-11", new IyilestirOlcum.Esik(30, 20_000, 1_500, 2.5, 1.25), true);
    }

    /** F2d: 8 ay geçmişli ölçek (taban 276 boş; ölçülen 54, tur başına ≈ 4 sn): boş <= 80 (plan kapısı 150, ilk ölçümden sonra sıkılaştırıldı), önizleme <= 20 sn. */
    @Test
    void iyilestir_kirkMakinist_sekizAyGecmisAtamali() throws Exception {
        iyilestirVerisi(true, false);
        IyilestirOlcum.olc(atamaci(), jdbc, "ölçek 8 ay geçmiş", "2026-10-12", "2026-11-11", new IyilestirOlcum.Esik(80, 20_000, 5_000, 2.5, 1.25), true);
    }

    /** F2d [V3]: 8 ay geçmiş + yıl kuralı + dolu Aralık: tur 0 tek başına bütçeyi doldurur (≈ 14 sn), kazanç yok (276 -> 276); yalnız kötüleşme yok + kırmızı 0 + süre. */
    @Test
    void iyilestir_kirkMakinist_sekizAyGecmisVeYilKuraliVeAralik() throws Exception {
        iyilestirVerisi(true, true);
        IyilestirOlcum.olc(atamaci(), jdbc, "ölçek 8 ay + yıl + Aralık", "2026-10-12", "2026-11-11", new IyilestirOlcum.Esik(300, 30_000, 15_000, 100, 1.25), true);
    }

    private void iyilestirVerisi(boolean gecmis, boolean yilVeIleri) {
        if (yilVeIleri) {
            jdbc.update("INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan) "
                    + "VALUES ('Olcek yil', 'TOPLAM_GOREV_SURESI', 'YIL', 'EN_FAZLA', 2300, '{}', 'ENGEL', '2026-01-01', 'olcek', 'test')");
        }
        AtamaVeri v = new AtamaVeri(jdbc);
        for (int i = 0; i < MAKINIST; i++) {
            v.makinist(String.format("D%03d", i + 1));
        }
        List<Object[]> satirlar = new ArrayList<>();
        for (int g = 0; g < GUN; g++) {
            LocalDate gun = ILK.plusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                satirlar.add(new Object[] { String.format("T%03d", i), gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", satirlar);
        if (yilVeIleri) {
            ileriAtamalar(v);
        }
        if (gecmis) {
            gecmisAtamalar(v);
        }
    }

    private void olc(boolean ozelKurallar, boolean yilVeIleri, boolean gecmis) throws Exception {
        olc(ozelKurallar, yilVeIleri, gecmis, false);
    }

    private void olc(boolean ozelKurallar, boolean yilVeIleri, boolean gecmis, boolean yolculuk) throws Exception {
        if (ozelKurallar) {
            OlcekKurallari.yaz(jdbc);
        }
        if (yilVeIleri) {
            jdbc.update("INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan) "
                    + "VALUES ('Olcek yil', 'TOPLAM_GOREV_SURESI', 'YIL', 'EN_FAZLA', 2300, '{}', 'ENGEL', '2026-01-01', 'olcek', 'test')");
        }
        AtamaVeri v = new AtamaVeri(jdbc);
        for (int i = 0; i < MAKINIST; i++) {
            v.makinist(String.format("D%03d", i + 1));
        }
        List<Object[]> satirlar = new ArrayList<>();
        for (int g = 0; g < GUN; g++) {
            LocalDate gun = ILK.plusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10; // 04:00'dan 10 dk arayla, her biri 90 dk, Ankara -> Ankara
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                satirlar.add(new Object[] { String.format("T%03d", i), gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)),
                        v.yht, v.hat });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", satirlar);
        if (yolculuk) {
            yolculukSeferleri(v);
        }
        if (yilVeIleri) {
            ileriAtamalar(v);
        }
        if (gecmis) {
            gecmisAtamalar(v);
        }
        Tarayici t = atamaci();
        String tekGun = "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-12\",\"uygula\":false}";
        String ay = "{\"bas\":\"2026-10-12\",\"bit\":\"2026-11-11\",\"uygula\":false}";

        t.post("/api/atama/otomatik", tekGun).andExpect(status().isOk()); // ısınma (JIT, bağlantı havuzu)
        // Tek gün tavanı yük altında tek ölçüme duyarlıdır (performans review INFO): en iyi 3 ölçüm
        String tek = null;
        long tekMs = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long b1 = System.nanoTime();
            tek = t.post("/api/atama/otomatik", tekGun).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            tekMs = Math.min(tekMs, (System.nanoTime() - b1) / 1_000_000);
        }

        long b2 = System.nanoTime();
        String sonuc = t.post("/api/atama/otomatik", ay).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long ayMs = (System.nanoTime() - b2) / 1_000_000;

        System.out.println("OLCEK otomatik atama önizleme (" + (gecmis ? "8 ay geçmiş atamalı, " : "") + (yilVeIleri ? "yıl kuralı + Aralık (31 gün) atamaları" : ozelKurallar ? "10 özel kural" : "özel kural yok") + "): tek gün " + tekMs + " ms; 31 gün (" + (GUN * GUNLUK_SEFER) + " sefer x " + MAKINIST + " makinist) "
                + ayMs + " ms; yanıt " + sonuc.length() / 1024 + " KB; ilk 120 karakter: " + sonuc.substring(0, Math.min(120, sonuc.length())) + " | tek gün: "
                + tek.substring(0, Math.min(100, tek.length())));
        if (gecmis) { // performans işi (MK-57 E3): 8 ay geçmişli planın özeti değişmemeli
            altinOzet((yilVeIleri ? "8ay-yil" : "8ay") + (yolculuk ? "-yolculuk-kapali" : ""), sonuc);
        }
        // Performans işi sonrası sıkılaştırma (06-refactor.md): ölçülen 8 ay ≈ 4 sn, yıl kuralı + Aralık ≈ 12 sn, tek gün ≈ 0,25 / 0,5 sn; yüklü makinede ≈ 2x pay bırakıldı.
        // 8 ay geçmişsiz varyantlarda (10 özel kural, yıl kuralı geçmişsiz) eski tavan korunur; 30 sn mutlak tavan her koşulda geçerlidir.
        long ayTavanMs = gecmis ? (yilVeIleri ? 26_000 : 10_000) : 30_000;
        long tekTavanMs = gecmis ? 1_500 : 3_000;
        assertThat(tekMs).as("tek gün önizleme (NFR-02)").isLessThanOrEqualTo(tekTavanMs);
        assertThat(ayMs).as("31 gün önizleme (NFR-02)").isLessThanOrEqualTo(ayTavanMs);
        if (yolculuk) {
            String ayAcik = ay.replace("\"uygula\":false", "\"uygula\":false,\"yolculuk\":true");
            String tekAcik = tekGun.replace("\"uygula\":false", "\"uygula\":false,\"yolculuk\":true");
            t.post("/api/atama/otomatik", tekAcik).andExpect(status().isOk());
            long tekAcikMs = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                long b3 = System.nanoTime();
                t.post("/api/atama/otomatik", tekAcik).andExpect(status().isOk());
                tekAcikMs = Math.min(tekAcikMs, (System.nanoTime() - b3) / 1_000_000);
            }
            // Yüke dayanıklı fark: kapalı ve açık ölçüm AYNI koşuda üçer kez alınır, her ikisinin en iyisi (min) karşılaştırılır (tek ölçüm paralel yükte 9,9 sn fark verebiliyordu)
            long b4 = System.nanoTime();
            String acik = t.post("/api/atama/otomatik", ayAcik).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long acikMs = (System.nanoTime() - b4) / 1_000_000;
            long kapaliEnIyiMs = ayMs;
            long acikEnIyiMs = acikMs;
            for (int i = 0; i < 2; i++) {
                long k1 = System.nanoTime();
                t.post("/api/atama/otomatik", ay).andExpect(status().isOk());
                kapaliEnIyiMs = Math.min(kapaliEnIyiMs, (System.nanoTime() - k1) / 1_000_000);
                long k2 = System.nanoTime();
                t.post("/api/atama/otomatik", ayAcik).andExpect(status().isOk());
                acikEnIyiMs = Math.min(acikEnIyiMs, (System.nanoTime() - k2) / 1_000_000);
            }
            altinOzet((yilVeIleri ? "8ay-yil" : "8ay") + "-yolculuk-acik", acik);
            int yolculuklu = com.jayway.jsonpath.JsonPath.<java.util.List<Object>>read(acik, "$.atananlar[?(@.yolculuk != null)]").size();
            int atananAcik = com.jayway.jsonpath.JsonPath.<Integer>read(acik, "$.atananSayisi");
            int atananKapali = com.jayway.jsonpath.JsonPath.<Integer>read(sonuc, "$.atananSayisi");
            System.out.println("OLCEK yolculuk aşaması (" + (yilVeIleri ? "8 ay geçmiş + yıl kuralı + Aralık" : "8 ay geçmiş") + "): kapalı " + ayMs + " ms, açık " + acikMs + " ms, fark " + (acikMs - ayMs) + " ms (en iyi 3: kapalı " + kapaliEnIyiMs + ", açık " + acikEnIyiMs + ", fark "
                    + (acikEnIyiMs - kapaliEnIyiMs) + " ms); tek gün açık " + tekAcikMs + " ms; yolculuklu atama " + yolculuklu + "; atanan açık " + atananAcik + " / kapalı " + atananKapali);
            assertThat(yolculuklu).as("yolculuk aşaması gerçekten tetiklenmeli").isPositive();
            int hedefAcik = com.jayway.jsonpath.JsonPath.<java.util.List<Object>>read(acik, "$.atananlar[?(@.trenNo =~ /YB.*/)]").size();
            int hedefKapali = com.jayway.jsonpath.JsonPath.<java.util.List<Object>>read(sonuc, "$.atananlar[?(@.trenNo =~ /YB.*/)]").size();
            assertThat(hedefAcik).as("yolculuk yalnız garda adayı olmayan hedefleri (Eskişehir -> Ankara) doldurur").isGreaterThan(hedefKapali);
            assertThat(acikEnIyiMs).as("31 gün önizleme, yolculuk açık (NFR-02: toplam <= 30 sn; sıkı tavan + 5 sn aşama payı)").isLessThanOrEqualTo(Math.min(30_000, ayTavanMs + 5_000));
            assertThat(acikEnIyiMs - kapaliEnIyiMs).as("yolculuk aşaması maliyeti (açık - kapalı, en iyi 3) <= 5 sn").isLessThanOrEqualTo(5_000);
            assertThat(tekAcikMs).as("tek gün, yolculuk açık").isLessThanOrEqualTo(tekTavanMs);
        }
    }

    /**
     * Planın kanonik özeti ({@code golden/olcek-plan-<ad>.json}): atananlar (gün, tren no, sicil, yolculuk tren no) ve boş kalanlar (gün, tren no, neden, örnekler) SHA-256. Kimlik
     * (veritabanı sırası) yerine tren no + gün kullanılır; böylece özet koşudan koşuya aynıdır.
     */
    private static void altinOzet(String ad, String govde) throws Exception {
        com.jayway.jsonpath.DocumentContext j = com.jayway.jsonpath.JsonPath.parse(govde);
        List<java.util.Map<String, Object>> atananlar = j.read("$.atananlar");
        List<java.util.Map<String, Object>> bos = j.read("$.bosKalanlar");
        java.security.MessageDigest a = java.security.MessageDigest.getInstance("SHA-256");
        int yolculuklu = 0;
        for (java.util.Map<String, Object> x : atananlar) {
            Object y = x.get("yolculuk") == null ? null : ((java.util.Map<?, ?>) x.get("yolculuk")).get("trenNo");
            yolculuklu += y == null ? 0 : 1;
            a.update((x.get("tarih") + "|" + x.get("trenNo") + "|" + x.get("sicil") + "|" + y + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        java.security.MessageDigest b = java.security.MessageDigest.getInstance("SHA-256");
        for (java.util.Map<String, Object> x : bos) {
            b.update((x.get("tarih") + "|" + x.get("trenNo") + "|" + x.get("neden") + "|" + x.get("ornekler") + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        java.util.Map<String, Object> ozet = new java.util.LinkedHashMap<>();
        ozet.put("atananSayisi", atananlar.size());
        ozet.put("bosSayisi", bos.size());
        ozet.put("gecmisSayisi", j.read("$.gecmisSayisi"));
        ozet.put("yolculukluAtama", yolculuklu);
        ozet.put("atananlarSha256", java.util.HexFormat.of().formatHex(a.digest()));
        ozet.put("bosKalanlarSha256", java.util.HexFormat.of().formatHex(b.digest()));
        com.makinistatama.atama.AltinDosya.kontrol("olcek-plan-" + ad, ozet);
    }

    /**
     * Her gün Ankara -> Eskişehir 05:00 (asılı PASIF bir makinist: aktif listede değil, yalnız yolculuk adayı) ve Eskişehir -> Ankara 08:00 (hedef: garda bekleyen yok, yalnız
     * yolculukla doldurulabilir; yoksa Ankara -> Eskişehir seferini doldurandan devam eder ve aşama tetiklenmezdi).
     */
    private void yolculukSeferleri(AtamaVeri v) {
        long pasif = v.makinist("P001", true, "PASIF"); // kimliği 40'ın ötesinde: geçmiş atama döngüsüne girmez
        List<Object[]> satirlar = new ArrayList<>();
        for (int g = 0; g < GUN; g++) {
            LocalDate gun = ILK.plusDays(g);
            var k1 = gun.atTime(5, 0).atZone(AtamaVeri.ISTANBUL).toInstant();
            var k2 = gun.atTime(8, 0).atZone(AtamaVeri.ISTANBUL).toInstant();
            satirlar.add(new Object[] { "YA" + g, gun, v.ankara, v.eskisehir, Timestamp.from(k1), Timestamp.from(k1.plusSeconds(60 * 90)), v.yht, v.hat });
            satirlar.add(new Object[] { "YB" + g, gun, v.eskisehir, v.ankara, Timestamp.from(k2), Timestamp.from(k2.plusSeconds(60 * 90)), v.yht, v.hat });
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", satirlar);
        jdbc.update("INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk) SELECT id, ?, 'ASIL', 'test', 0 FROM sefer WHERE tren_no LIKE 'YA%'", pasif);
    }

    private static final int GECMIS_GUN = 242; // 12 Şubat – 11 Ekim 2026 (≈ 8 ay)

    /** 8 ay geçmiş: her gün 100 sefer, 40 makinist döngüyle atanmış (ASIL). */
    private void gecmisAtamalar(AtamaVeri v) {
        List<Object[]> satirlar = new ArrayList<>();
        for (int g = 1; g <= GECMIS_GUN; g++) {
            LocalDate gun = ILK.minusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                satirlar.add(new Object[] { String.format("P%03d", i), gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", satirlar);
        jdbc.update("""
                INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk)
                SELECT s.id, m.id, 'ASIL', 'test', 0 FROM (SELECT id, row_number() OVER (ORDER BY id) AS sira FROM sefer WHERE tarih < '2026-10-12') s
                JOIN (SELECT id, row_number() OVER (ORDER BY id) AS sira FROM makinist) m ON m.sira = 1 + (s.sira - 1) % 40""");
    }

    private static final int ILERI_GUN = 31; // Tur 3: tam Aralık (reviewer: 15 gün tavana yakınlığı gizliyordu)

    /** Aralık 2026 tüm gününde (31) gün başına 100 sefer, 40 makinisti döngüyle atanmış (yıl kuralının ileri penceresi bunları görür; 31 günün tamamı dolu olunca sakin makinede ≈ 25 sn, tavana çok yakın: ölçüm 04-gelistirme.md). */
    private void ileriAtamalar(AtamaVeri v) {
        List<Object[]> satirlar = new ArrayList<>();
        for (int g = 0; g < ILERI_GUN; g++) {
            LocalDate gun = LocalDate.of(2026, 12, 1).plusDays(g);
            for (int i = 0; i < GUNLUK_SEFER; i++) {
                int dk = 4 * 60 + i * 10;
                var kalkis = gun.atTime(dk / 60, dk % 60).atZone(AtamaVeri.ISTANBUL).toInstant();
                satirlar.add(new Object[] { String.format("A%03d", i), gun, v.ankara, v.ankara, Timestamp.from(kalkis), Timestamp.from(kalkis.plusSeconds(60 * 90)), v.yht, v.hat });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250)""", satirlar);
        jdbc.update("""
                INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk)
                SELECT s.id, m.id, 'ASIL', 'test', 0 FROM (SELECT id, row_number() OVER (ORDER BY id) AS sira FROM sefer WHERE tarih >= '2026-12-01') s
                JOIN (SELECT id, row_number() OVER (ORDER BY id) AS sira FROM makinist) m ON m.sira = 1 + (s.sira - 1) % 40""");
    }
}
