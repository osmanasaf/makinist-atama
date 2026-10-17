package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.sefer.SeferBolIstegi;
import com.makinistatama.sefer.SeferBolIstegi.Nokta;

/**
 * T5.4 (FR-10, NFR-02): böl -> birleştir TUR DÖNÜŞÜ. Sabit tohumlu 40 rastgele sefer (gece aşan, km boş/dolu, gecikmeli, atamalı/atamasız, YOLCU'lu, tren türü/hat çeşitli): her seferin satırı
 * özgünle BİREBİR aynı (yalnız sürüm artar), atama satırları aynı, tüm günlerin gün listesi eşit. Ayrıca gözlenen davranış sabitlenir: gördüm özeti eski onayla eşleşip GORULDU'ya döner;
 * bölmeyle BAYAT olan açık talep birleştirmeyle yeniden ETKIN (ATAMACI_ONAYI_BEKLIYOR) okunur (etkinDurum canlı hesap; özet eski haline döndü).
 */
class SeferBolBirlestirTurDonusuTest extends TestVeritabani {

    static final int SEFER_SAYISI = 40;
    static final String SEFER_SUTUNLARI = "id, tarih, tren_no, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, kalkis_gecikme_dk, varis_gecikme_dk, arac_tipi_id, hat_id, km, "
            + "tren_turu, durum, parca_no, parca_sayisi, tren_gunu, versiyon";

    @Autowired
    SeferParcalamaServisi servis;

    @Autowired
    GunListesiServisi gunListesi;

    AtamaVeri v;
    long sivas;
    long kars;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        kars = TestTanimlari.gar(jdbc, "Kars");
    }

    record Uretilen(long id, List<Nokta> noktalar) {
    }

    /** 40 sefer: kalkış rastgele (gece aşanlar dahil), süre 2,5..22,5 s, 1-2 ara nokta (bekleme gece yarısını geçmez), gecikme >= 0, dörtte biri atamasız. */
    List<Uretilen> seferleriUret() {
        Random r = new Random(20261015L);
        List<Long> havuz = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            havuz.add(v.makinist("R00" + i));
        }
        long arac2 = TestTanimlari.aracTipi(jdbc, "DM");
        List<Uretilen> sonuc = new ArrayList<>();
        for (int i = 0; i < SEFER_SAYISI; i++) {
            LocalDate gun = LocalDate.of(2026, 10, 12).plusDays(i % 3);
            LocalTime kalkis = LocalTime.of(r.nextInt(24), r.nextInt(60));
            int dk = 150 + r.nextInt(1200);
            var kalkisAn = gun.atTime(kalkis).atZone(AtamaVeri.ISTANBUL).toInstant();
            var varisAn = kalkisAn.plusSeconds(60L * dk);
            BigDecimal km = r.nextInt(3) == 0 ? null : BigDecimal.valueOf(2000 + r.nextInt(8000), 1);
            int kg = r.nextInt(4) == 0 ? 0 : r.nextInt(31);
            int vg = kg + (r.nextInt(3) == 0 ? 0 : r.nextInt(31));
            long id = jdbc.queryForObject("""
                    INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, kalkis_gecikme_dk, varis_gecikme_dk, arac_tipi_id, hat_id, km, tren_turu)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, "R-" + i, gun, v.ankara, kars, Timestamp.from(kalkisAn), Timestamp.from(varisAn), kg, vg,
                    r.nextBoolean() ? v.yht : arac2, r.nextBoolean() ? v.hat : v.hat2, km, new String[] { "YUK", "YOLCU", null }[r.nextInt(3)]);

            int n = 1 + r.nextInt(2);
            List<Nokta> noktalar = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                int varisDk = (k + 1) * dk / (n + 1) + r.nextInt(21) - 10;
                int bekleme = r.nextInt(16);
                LocalTime varisSaat = kalkis.plusMinutes(varisDk);
                if (varisSaat.plusMinutes(bekleme).isBefore(varisSaat)) {
                    bekleme = 0; // nokta beklemesi gece yarısını geçemez (kalkış saati varıştan önce olamaz)
                }
                BigDecimal nokmKm = km == null ? null : km.multiply(BigDecimal.valueOf(k + 1)).divide(BigDecimal.valueOf(n + 1), 1, java.math.RoundingMode.HALF_UP);
                noktalar.add(new Nokta(k == 0 ? sivas : TestTanimlari.gar(jdbc, "Erzurum"), varisSaat, varisSaat.plusMinutes(bekleme), nokmKm, null, null));
            }
            // atama: dörtte biri boş; ASIL / ASIL+YARDIMCI / +YOLCU (gecikme yukarıda yazıldı: atama.kalkis_gecikme_dk = seferin o anki kalkış gecikmesi)
            int tur = r.nextInt(4);
            List<Long> karisik = new ArrayList<>(havuz);
            Collections.shuffle(karisik, r);
            if (tur >= 1) {
                v.atama(id, karisik.get(0), "ASIL");
            }
            if (tur >= 2) {
                v.atama(id, karisik.get(1), "YARDIMCI");
            }
            if (tur >= 3) {
                v.atama(id, karisik.get(2), "YOLCU");
            }
            sonuc.add(new Uretilen(id, noktalar));
        }
        return sonuc;
    }

    Map<String, Object> seferSatiri(long id) {
        return jdbc.queryForMap("SELECT " + SEFER_SUTUNLARI + " FROM sefer WHERE id = ?", id);
    }

    List<GunListesiDto.GunSatiriDto> gunler() {
        List<GunListesiDto.GunSatiriDto> hepsi = new ArrayList<>();
        for (int g = 12; g <= 15; g++) {
            hepsi.addAll(gunListesi.gunListesi(LocalDate.of(2026, 10, g)).satirlar());
        }
        return hepsi;
    }

    @Test
    void bolSonraBirlestir_40RastgeleSefer_satirAtamaVeGunListesiOzgunleBirebir() {
        List<Uretilen> seferler = seferleriUret();
        assertThat(seferler.stream().filter(s -> jdbc.queryForObject("SELECT planlanan_varis::date > planlanan_kalkis::date FROM sefer WHERE id = ?", Boolean.class, s.id())).count())
                .as("kümede gece aşan sefer var").isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE km IS NULL", Integer.class)).as("km boş olanlar var").isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE km IS NOT NULL", Integer.class)).as("km dolu olanlar var").isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'YOLCU'", Integer.class)).as("YOLCU'lu sefer var").isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE kalkis_gecikme_dk > 0", Integer.class)).as("gecikmeli sefer var").isPositive();
        var gunOnce = gunler();
        assertThat(gunOnce).as("gün listesi dolu").hasSizeGreaterThanOrEqualTo(SEFER_SAYISI);
        List<Map<String, Object>> atamaTablosuOnce = jdbc.queryForList("SELECT * FROM atama ORDER BY id");

        for (Uretilen s : seferler) {
            Map<String, Object> ozgun = seferSatiri(s.id());
            long versiyon = (long) ozgun.get("versiyon");

            BolSonucuDto bolunen = servis.bol(s.id(), new SeferBolIstegi(versiyon, s.noktalar(), false));
            assertThat(bolunen.parcalar()).as("sefer " + ozgun.get("tren_no")).hasSize(s.noktalar().size() + 1);
            BirlestirSonucuDto birlesen = servis.birlestir(s.id(), new SeferBirlestirIstegi(bolunen.parcalar().getFirst().versiyon(), false));

            Map<String, Object> sonra = seferSatiri(s.id());
            assertThat(birlesen.silinenSeferIdler()).hasSize(s.noktalar().size());
            assertThat(sonra.get("km") == null).as("km boşluğu korunur: " + ozgun.get("tren_no")).isEqualTo(ozgun.get("km") == null);
            if (ozgun.get("km") != null) {
                assertThat((BigDecimal) sonra.get("km")).as("km: " + ozgun.get("tren_no")).isEqualByComparingTo((BigDecimal) ozgun.get("km"));
            }
            Map<String, Object> a = new HashMap<>(ozgun);
            Map<String, Object> b = new HashMap<>(sonra);
            for (Map<String, Object> m : List.of(a, b)) {
                m.remove("km");
                m.remove("versiyon");
            }
            assertThat(b).as("sefer satırı özgünle birebir (km ve sürüm hariç): " + ozgun.get("tren_no")).isEqualTo(a);
            assertThat((long) sonra.get("versiyon")).as("yalnız sürüm artar").isGreaterThan(versiyon);
            assertThat(birlesen.sefer()).as("yanıt satırla tutarlı").satisfies(d -> {
                assertThat(d.id()).isEqualTo(s.id());
                assertThat(d.parcaNo()).isNull();
                assertThat(d.versiyon()).isEqualTo((long) sonra.get("versiyon"));
            });
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).as("parça satırı kalmadı").isEqualTo(SEFER_SAYISI);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE parca_no IS NOT NULL OR parca_sayisi IS NOT NULL OR tren_gunu IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM atama ORDER BY id")).as("atama satırları aynı id/rol/makinist/kalkis_gecikme_dk (ve sürüm)").isEqualTo(atamaTablosuOnce);
        assertThat(gunler()).as("gün listesi JSON eşit (12-15 Ekim)").usingRecursiveComparison().withComparatorForType(Comparator.<BigDecimal>naturalOrder(), BigDecimal.class).isEqualTo(gunOnce);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL'", Integer.class)).isEqualTo(SEFER_SAYISI);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'", Integer.class)).isEqualTo(SEFER_SAYISI);
    }

    // ------------------------------------------------------------------ gözlenen davranış: gördüm ve takas

    /** Örnek: Ankara 08:00 -> Kars 18:10 (km 1103), Sivas 13:30/13:40 (ara km 603); gerçek bugünden 3 gün sonra (gördüm ufku / takas süresi gerçek saate bağlı). */
    @Test
    void gordumOzeti_birlestirinceEskiOnaylaEslesirGORULDU_acikTakasBolunceBAYAT_birlesinceYenidenEtkin() throws Exception {
        LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).plusDays(3);
        long id = v.sefer("T1", gun, "08:00", "18:10", v.ankara, kars);
        jdbc.update("UPDATE sefer SET km = 1103 WHERE id = ?", id);
        long m = v.makinist("D001");
        long atama = v.atama(id, m, "ASIL");
        Tarayici mk = makinistGirisli(m, "D001");
        String eskiOzet = JsonPath.read(mk.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString(), "$.gorevler[0].ozet");
        GordumYardimci.gordum(mk, atama, eskiOzet).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        long talep = ((Number) JsonPath.read(mk.post("/api/benim/takas", "{\"atamaId\":" + atama + ",\"neden\":\"IZIN\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id")).longValue();
        Tarayici atamaci = atamaci();
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("ATAMACI_ONAYI_BEKLIYOR"));

        long versiyon = jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id);
        BolSonucuDto bolunen = servis.bol(id, new SeferBolIstegi(versiyon, List.of(new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), null, null)), false));

        mk.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI")).andExpect(jsonPath("$.gorevler[0].ozet").value(containsString("Sivas")));
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("BAYAT"));

        servis.birlestir(id, new SeferBirlestirIstegi(bolunen.parcalar().getFirst().versiyon(), false));

        mk.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].ozet").value(eskiOzet)).andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU"))
                .andExpect(jsonPath("$.gorevler[0].parcaNo").doesNotExist());
        GordumYardimci.gordum(mk, atama, eskiOzet).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU")); // bayat değil: ekrandaki özet güncel özetle aynı
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("ATAMACI_ONAYI_BEKLIYOR"));
        mk.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("ATAMACI_ONAYI_BEKLIYOR"));
    }
}
