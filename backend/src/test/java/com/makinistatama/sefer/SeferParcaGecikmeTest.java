package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

/** F2c Faz 3 (MK-60/62/63): parça zinciri, gecikme {@code devam} (ileri yönlü max) ve iptal "bu ve sonrakiler". */
class SeferParcaGecikmeTest extends TestVeritabani {

    @Autowired
    SeferYuklemeServisi yukleme;

    @Autowired
    SeferRepository depo;

    @BeforeEach
    void tanimlar() {
        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.gar(jdbc, "Sivas");
        TestTanimlari.gar(jdbc, "Erzincan");
        TestTanimlari.gar(jdbc, "Kars");
        TestTanimlari.hat(jdbc, "Ankara–Kars");
        TestTanimlari.aracTipi(jdbc, "YHT");
    }

    /** Ankara 08:00 → Sivas 13:30 / 13:40 → Kars 20:10; döner [parça 1, parça 2] sefer kimlikleri. */
    long[] iki() {
        yukleme.yukle(SeferYuklemeParcaTest.ankaraKars(), false);
        return idler("DE-1503");
    }

    /** Ankara 06:00 → Sivas 10:00 / 10:10 → Erzincan 15:00 / 15:10 → Kars 20:00 (3 parça). */
    long[] uc() {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(SeferYuklemeParcaTest.ana("12.10.2026", "DE-1700", "06:00", "20:00", "1103")),
                List.<String[]>of(new String[] { "DE-1700", "1", "Sivas", "10:00", "10:10", "400" }, new String[] { "DE-1700", "2", "Erzincan", "15:00", "15:10", "800" }));
        assertThat(yukleme.yukle(d, false).eklenen()).isEqualTo(3);
        return idler("DE-1700");
    }

    long[] idler(String tren) {
        return jdbc.queryForList("SELECT id FROM sefer WHERE tren_no = ? ORDER BY tren_gunu, parca_no", Long.class, tren).stream().mapToLong(Long::longValue).toArray();
    }

    String gecikme(long id) {
        return jdbc.queryForObject("SELECT kalkis_gecikme_dk || '/' || varis_gecikme_dk FROM sefer WHERE id = ?", String.class, id);
    }

    int kayit(String tur) {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = ?", Integer.class, tur);
    }

    String govde(Integer kalkis, Integer varis, Boolean devam) {
        return "{\"kalkisGecikmeDk\":" + kalkis + (varis == null ? "" : ",\"varisGecikmeDk\":" + varis) + ",\"neden\":\"GECIKME\"" + (devam == null ? "" : ",\"devam\":" + devam) + "}";
    }

    // ------------------------------------------------------------------ zincir (T3.1)

    @Test
    void zincir_gunAsanTrenlerKarismaz_elleSaatDegisinceBozulmaz() throws Exception {
        yukleme.yukle(SeferYuklemeParcaTest.gunAsanDosya("12.10.2026", "13.10.2026"), false);
        long[] id = idler("DE-1601"); // tren günü, parça sırasıyla: 12.10 [1,2], 13.10 [1,2]
        assertThat(depo.findByTrenNoAndTrenGunuOrderByParcaNo("DE-1601", java.time.LocalDate.of(2026, 10, 12))).extracting(Sefer::id).containsExactly(id[0], id[1]);
        assertThat(depo.findByTrenNoAndTrenGunuOrderByParcaNo("DE-1601", java.time.LocalDate.of(2026, 10, 13))).extracting(Sefer::id).containsExactly(id[2], id[3]);
        Tarayici t = atamaci();
        long sivas = jdbc.queryForObject("SELECT id FROM tanim WHERE ad = 'Sivas'", Long.class);
        long kars = jdbc.queryForObject("SELECT id FROM tanim WHERE ad = 'Kars'", Long.class);
        long yht = jdbc.queryForObject("SELECT id FROM tanim WHERE ad = 'YHT'", Long.class);
        long hat = jdbc.queryForObject("SELECT id FROM tanim WHERE ad = 'Ankara–Kars'", Long.class);
        long versiyon = jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id[1]);
        // 12.10 zincirinin parça 2'sinin (tarih 13.10) saati elle değişir: zincir kimliği saatte değil tren_gunu'ndadır
        t.put("/api/sefer/" + id[1], "{\"trenNo\":\"DE-1601\",\"tarih\":\"2026-10-13\",\"kalkisGarId\":" + sivas + ",\"kalkisSaat\":\"02:50\",\"varisGarId\":" + kars + ",\"varisSaat\":\"09:10\",\"aracTipiId\":" + yht
                + ",\"hatId\":" + hat + ",\"km\":500,\"versiyon\":" + versiyon + "}").andExpect(status().isOk()).andExpect(jsonPath("$.parcaNo").value(2));

        t.post("/api/sefer/" + id[0] + "/gecikme", govde(0, 30, null)).andExpect(status().isOk());

        assertThat(gecikme(id[1])).isEqualTo("30/30");
        assertThat(gecikme(id[2])).isEqualTo("0/0"); // ertesi günün aynı tren no'lu başka zinciri
        assertThat(gecikme(id[3])).isEqualTo("0/0");
    }

    // ------------------------------------------------------------------ gecikme devam (T3.2)

    @Test
    void gecikme_parca1VarisKirk_parca2KirkKirk_kalkis1340dan1420ye() throws Exception {
        long[] p = iki();

        atamaci().post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk()).andExpect(jsonPath("$.varisGecikmeDk").value(40));

        assertThat(gecikme(p[0])).isEqualTo("0/40");
        assertThat(gecikme(p[1])).isEqualTo("40/40");
        atamaci().get("/api/sefer/" + p[1]).andExpect(jsonPath("$.guncelKalkis").value("2026-10-12T14:20:00+03:00")).andExpect(jsonPath("$.guncelVaris").value("2026-10-12T20:50:00+03:00"));
        assertThat(kayit("SEFER_GECIKME")).isEqualTo(2); // değişen her sefer için ayrı kayıt
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'SEFER_GECIKME' AND sefer_id = ?", String.class, p[1])).contains("\"yayilan\":true");
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'SEFER_GECIKME' AND sefer_id = ?", String.class, p[0])).doesNotContain("yayilan");
    }

    @Test
    void gecikme_elleGirilenBuyukDegerDusmez_kucukOlanYukselir_ayniDegerDegismezVeKayitYok() throws Exception {
        long[] p = iki();
        Tarayici t = atamaci();

        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 60, varis_gecikme_dk = 60 WHERE id = ?", p[1]);
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());
        assertThat(gecikme(p[1])).isEqualTo("60/60"); // 60 > 40: düşmez
        assertThat(kayit("SEFER_GECIKME")).isEqualTo(1); // yalnız girilen parça

        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 20, varis_gecikme_dk = 20 WHERE id = ?", p[1]);
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());
        assertThat(gecikme(p[1])).isEqualTo("40/40"); // 20 < 40: yükselir

        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 0, varis_gecikme_dk = 0 WHERE id = ?", p[1]);
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());
        assertThat(gecikme(p[1])).isEqualTo("40/40"); // 0 -> 40
        int once = kayit("SEFER_GECIKME");
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());
        assertThat(kayit("SEFER_GECIKME")).isEqualTo(once + 1); // parça 2 zaten 40/40: yeni kayıt yalnız girilen parça için
    }

    @Test
    void gecikme_azaltma_yayilmaz_sonrakiParca40ta_kalir() throws Exception {
        long[] p = iki();
        Tarayici t = atamaci();
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());

        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 10, null)).andExpect(status().isOk());

        assertThat(gecikme(p[0])).isEqualTo("0/10");
        assertThat(gecikme(p[1])).isEqualTo("40/40"); // S2: düzeltme için parça başına devam=false
    }

    @Test
    void gecikme_ucParcada_kademe_iptalParcaAtlanirAmaZincirSurer() throws Exception {
        long[] p = uc();
        Tarayici t = atamaci();
        jdbc.update("UPDATE sefer SET varis_gecikme_dk = 50, kalkis_gecikme_dk = 0 WHERE id = ?", p[1]); // parça 2: kalkış 0, varış 50

        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());

        assertThat(gecikme(p[1])).isEqualTo("40/50"); // kalkış 0->40, varış 50 kalır
        assertThat(gecikme(p[2])).isEqualTo("50/50"); // kademe: V = parça 2'nin varışı (50)

        jdbc.update("UPDATE sefer SET durum = 'IPTAL', kalkis_gecikme_dk = 0, varis_gecikme_dk = 0 WHERE id = ?", p[1]);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 0, varis_gecikme_dk = 0 WHERE id = ?", p[2]);
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, null)).andExpect(status().isOk());
        assertThat(gecikme(p[1])).isEqualTo("0/0"); // iptal parça atlanır
        assertThat(gecikme(p[2])).isEqualTo("40/40"); // ama zincir sürer
    }

    @Test
    void gecikme_devamFalse_veParcasiz_yalnizOSefer() throws Exception {
        long[] p = iki();
        Tarayici t = atamaci();

        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 40, false)).andExpect(status().isOk());
        assertThat(gecikme(p[1])).isEqualTo("0/0");

        // parçasız sefer: devam=true yok sayılır
        jdbc.update("UPDATE sefer SET parca_no = NULL, parca_sayisi = NULL, tren_gunu = NULL WHERE id = ?", p[0]);
        jdbc.update("UPDATE sefer SET tren_no = 'DE-9999', parca_no = NULL, parca_sayisi = NULL, tren_gunu = NULL WHERE id = ?", p[1]);
        t.post("/api/sefer/" + p[0] + "/gecikme", govde(0, 55, true)).andExpect(status().isOk());
        assertThat(gecikme(p[1])).isEqualTo("0/0");
    }

    @Test
    void gecikme_girilenParcaGecersizse_400_hicbirYazimYok() throws Exception {
        long[] p = iki();

        atamaci().post("/api/sefer/" + p[0] + "/gecikme", govde(400, 0, null)).andExpect(status().isBadRequest()); // güncel varış güncel kalkıştan önce (BUG-11)

        assertThat(gecikme(p[0])).isEqualTo("0/0");
        assertThat(gecikme(p[1])).isEqualTo("0/0");
        assertThat(kayit("SEFER_GECIKME")).isZero();
    }

    @Test
    void gecikme_sonrakiParcadanGirilince_oncekineDokunmaz() throws Exception {
        long[] p = iki();

        atamaci().post("/api/sefer/" + p[1] + "/gecikme", govde(25, 25, null)).andExpect(status().isOk());

        assertThat(gecikme(p[0])).isEqualTo("0/0");
        assertThat(gecikme(p[1])).isEqualTo("25/25");
    }

    // ------------------------------------------------------------------ iptal devam (T3.3)

    String iptalGovdesi(Boolean devam) {
        return "{\"neden\":\"IPTAL\"" + (devam == null ? "" : ",\"devam\":" + devam) + "}";
    }

    @Test
    void iptal_parca1den_tumTren_herParcaIcinAyriKayit() throws Exception {
        long[] p = iki();

        atamaci().post("/api/sefer/" + p[0] + "/iptal", iptalGovdesi(null)).andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("IPTAL"));

        assertThat(jdbc.queryForList("SELECT durum FROM sefer ORDER BY id", String.class)).containsExactly("IPTAL", "IPTAL");
        assertThat(kayit("SEFER_IPTAL")).isEqualTo(2);
    }

    @Test
    void iptal_parca2den_yalnizBuVeSonrakiler_oncekiParcaDokunulmaz() throws Exception {
        long[] p = uc();

        atamaci().post("/api/sefer/" + p[1] + "/iptal", iptalGovdesi(true)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT durum FROM sefer ORDER BY parca_no", String.class)).containsExactly("PLANLI", "IPTAL", "IPTAL");
        assertThat(kayit("SEFER_IPTAL")).isEqualTo(2);
    }

    @Test
    void iptal_devamFalse_yalnizOParca_zatenIptalParcaAtlanir() throws Exception {
        long[] p = uc();
        Tarayici t = atamaci();

        t.post("/api/sefer/" + p[1] + "/iptal", iptalGovdesi(false)).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT durum FROM sefer ORDER BY parca_no", String.class)).containsExactly("PLANLI", "IPTAL", "PLANLI");

        t.post("/api/sefer/" + p[0] + "/iptal", iptalGovdesi(null)).andExpect(status().isOk()); // p2 zaten iptal: atlanır, p3 iptal olur
        assertThat(jdbc.queryForList("SELECT durum FROM sefer ORDER BY parca_no", String.class)).containsExactly("IPTAL", "IPTAL", "IPTAL");
        assertThat(kayit("SEFER_IPTAL")).isEqualTo(3);
        t.post("/api/sefer/" + p[1] + "/iptal", iptalGovdesi(null)).andExpect(status().isConflict()); // girilen parça zaten iptal: bugünkü 409
    }

    @Test
    void iptal_gunAsanZincir_ertesiGununZincirineSizmaz() throws Exception {
        yukleme.yukle(SeferYuklemeParcaTest.gunAsanDosya("12.10.2026", "13.10.2026"), false);
        long[] id = idler("DE-1601");

        atamaci().post("/api/sefer/" + id[0] + "/iptal", iptalGovdesi(null)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT durum FROM sefer ORDER BY tren_gunu, parca_no", String.class)).containsExactly("IPTAL", "IPTAL", "PLANLI", "PLANLI");
    }
}
