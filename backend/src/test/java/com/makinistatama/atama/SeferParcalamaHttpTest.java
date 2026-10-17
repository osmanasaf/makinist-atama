package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.ZamanAyari;
import com.jayway.jsonpath.JsonPath;

/**
 * {@code POST /api/atama/sefer/{id}/bol} uçtan uca (T3.3/T3.4; FR-05, 06, 07, 09, 11). Seferler gerçek bugünden 3 gün sonra (gördüm ufku ve takas süre sınırı gerçek saate bağlıdır;
 * bölmenin "başlamış mı" ölçütü sabit {@code makinist.atama.simdi} ile). Örnek: Ankara 08:00 -> Kars 18:10, Sivas 13:30/13:40, ara km 603 (sefer km 1103).
 */
class SeferParcalamaHttpTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.now(ZamanAyari.BOLGE).plusDays(3);

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
        long id = v.sefer(tren, GUN, "08:00", "18:10", v.ankara, kars); // 10 s 10 dk: gunluk.max_saat (11) altında, bölmeden önce kırmızı değil
        jdbc.update("UPDATE sefer SET km = 1103 WHERE id = ?", id);
        return id;
    }

    long versiyon(long id) {
        return jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id);
    }

    String govde(long id, boolean onizle) {
        return "{\"versiyon\":" + versiyon(id) + ",\"noktalar\":[{\"garId\":" + sivas + ",\"varisSaat\":\"13:30\",\"kalkisSaat\":\"13:40\",\"km\":603}],\"onizle\":" + onizle + "}";
    }

    int sayi(String sql, Object... arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    long parca2(long parca1) {
        return jdbc.queryForObject("SELECT id FROM sefer WHERE parca_no = 2 AND tren_no = (SELECT tren_no FROM sefer WHERE id = ?)", Long.class, parca1);
    }

    String gecikme(long id) {
        return jdbc.queryForObject("SELECT kalkis_gecikme_dk || '/' || varis_gecikme_dk FROM sefer WHERE id = ?", String.class, id);
    }

    // ------------------------------------------------------------------ FR-11 yetki, biçim, hatalar

    @Test
    void yetki_makinist403_atamaciVeYonetici200_jsonSaatHHmm_yeniKirmiziListeDoner() throws Exception {
        long s1 = sefer("T1");
        long s2 = sefer("T2");
        long m = v.makinist("D001");
        Tarayici makinist = makinistGirisli(m, "D001");

        makinist.post("/api/atama/sefer/" + s1 + "/bol", govde(s1, false)).andExpect(status().isForbidden());
        assertThat(sayi("SELECT count(*) FROM sefer WHERE parca_no IS NOT NULL")).isZero();
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL'")).isZero();

        atamaci().post("/api/atama/sefer/" + s1 + "/bol", govde(s1, false)).andExpect(status().isOk()).andExpect(jsonPath("$.parcalar", hasSize(2)))
                .andExpect(jsonPath("$.parcalar[0].id").value(s1)).andExpect(jsonPath("$.parcalar[0].parcaNo").value(1)).andExpect(jsonPath("$.parcalar[0].varisGar").value("Sivas"))
                .andExpect(jsonPath("$.parcalar[1].parcaNo").value(2)).andExpect(jsonPath("$.parcalar[1].kalkisGar").value("Sivas")).andExpect(jsonPath("$.parcalar[1].varisGar").value("Kars"))
                .andExpect(jsonPath("$.yeniKirmizi", hasSize(0)));
        yonetici().post("/api/atama/sefer/" + s2 + "/bol", govde(s2, false)).andExpect(status().isOk());
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL'")).as("her başarılı bölme tek kayıt").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT kullanici_adi FROM degisiklik_kaydi WHERE sefer_id = ?", String.class, s1)).isEqualTo("Ata Maci");
    }

    @Test
    void hatalar_400_404_409_422_hicbirSeyYazilmaz_kayitYok() throws Exception {
        long s1 = sefer("T1");
        Tarayici t = atamaci();
        String nokta = "{\"garId\":" + sivas + ",\"varisSaat\":\"13:30\",\"kalkisSaat\":\"13:40\",\"km\":603}";

        t.post("/api/atama/sefer/" + s1 + "/bol", "{\"versiyon\":0,\"noktalar\":[],\"onizle\":false}").andExpect(status().isBadRequest());
        t.post("/api/atama/sefer/" + s1 + "/bol", "{\"versiyon\":0,\"noktalar\":[" + String.join(",", java.util.Collections.nCopies(6, nokta)) + "]}").andExpect(status().isBadRequest());
        t.post("/api/atama/sefer/" + s1 + "/bol", "{\"versiyon\":0,\"noktalar\":[{\"garId\":" + sivas + ",\"varisSaat\":\"13:30\"}]}").andExpect(status().isBadRequest());
        t.post("/api/atama/sefer/" + s1 + "/bol", "{\"versiyon\":0,\"noktalar\":[{\"garId\":" + v.ankara + ",\"varisSaat\":\"13:30\",\"kalkisSaat\":\"13:40\",\"km\":603}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("DOGRULAMA"));
        t.post("/api/atama/sefer/99999/bol", govde(s1, false)).andExpect(status().isNotFound());
        t.post("/api/atama/sefer/" + s1 + "/bol", "{\"versiyon\":77,\"noktalar\":[" + nokta + "]}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        assertThat(sayi("SELECT count(*) FROM sefer WHERE parca_no IS NOT NULL")).isZero();

        long gecmis = v.sefer("GECMIS", LocalDate.of(2026, 10, 9), "08:00", "18:10", v.ankara, kars); // simdi 10.10 10:00: başlamış
        jdbc.update("UPDATE sefer SET km = 1103 WHERE id = ?", gecmis);
        t.post("/api/atama/sefer/" + gecmis + "/bol", govde(gecmis, false)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("SEFER_BASLAMIS"));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).as("başarısız bölme 0 kayıt").isZero();

        t.post("/api/atama/sefer/" + s1 + "/bol", "{\"versiyon\":" + versiyon(s1) + ",\"noktalar\":[" + nokta + "]}").andExpect(status().isOk()); // onizle yok: varsayılan false (yazar)
        t.post("/api/atama/sefer/" + s1 + "/bol", govde(s1, false)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_ZATEN_PARCALI"));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).as("yalnız başarılı olan").isEqualTo(1);
    }

    // ------------------------------------------------------------------ FR-07

    @Test
    void yeniKirmizi_yanitta_veGunListesiKirmiziSayisiyla_tutarli() throws Exception {
        yonetici().put("/api/kural/K-20/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(status().isOk()); // kırmızı = ENGEL; K-20 varsayılan UYARI
        long id = sefer("T1");
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        long sonrakiAtama = v.atama(v.sefer("KARS-ANK", GUN.plusDays(1), "10:00", "12:00", kars, kars), a, "ASIL");
        Tarayici t = atamaci();
        String kirmiziSayisi = "/api/atama/kirmizi-sayisi?bas=" + GUN + "&bit=" + GUN.plusDays(1);
        t.get(kirmiziSayisi).andExpect(jsonPath("$.kirmiziAtama").value(0));

        t.post("/api/atama/sefer/" + id + "/bol", govde(id, false)).andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(1)))
                .andExpect(jsonPath("$.yeniKirmizi[0].atamaId").value(sonrakiAtama)).andExpect(jsonPath("$.yeniKirmizi[0].trenNo").value("KARS-ANK"))
                .andExpect(jsonPath("$.yeniKirmizi[0].nedenler[0]").value(containsString("Konum uyuşmazlığı")));

        t.get(kirmiziSayisi).andExpect(jsonPath("$.kirmiziAtama").value(1)); // yanıttaki uyarı gün listesiyle tutarlı
    }

    // ------------------------------------------------------------------ FR-05

    @Test
    void gordum_bolunceDEGISTI_bayatEkrandanGordum409_yenidenOnayGORULDU() throws Exception {
        long id = sefer("T1");
        long m = v.makinist("D001");
        long atama = v.atama(id, m, "ASIL");
        Tarayici mk = makinistGirisli(m, "D001");
        String eskiOzet = JsonPath.read(mk.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString(), "$.gorevler[0].ozet");
        GordumYardimci.gordum(mk, atama, eskiOzet).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));

        atamaci().post("/api/atama/sefer/" + id + "/bol", govde(id, false)).andExpect(status().isOk());

        mk.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("DEGISTI")).andExpect(jsonPath("$.gorevler[0].ozet").value(containsString("Sivas")))
                .andExpect(jsonPath("$.gorevler[0].parcaNo").value(1)).andExpect(jsonPath("$.gorevler[0].parcaSayisi").value(2));
        GordumYardimci.gordum(mk, atama, eskiOzet).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("OZET_DEGISTI"));
        GordumYardimci.gordum(mk, atama).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        mk.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].gordu").value("GORULDU"));
    }

    // ------------------------------------------------------------------ FR-06

    @Test
    void acikBirakmaTalebi_bolunceBAYAT_onay409TalepBayat_atamaDegismez() throws Exception {
        long id = sefer("T1");
        long m = v.makinist("D001");
        long atama = v.atama(id, m, "ASIL");
        Tarayici mk = makinistGirisli(m, "D001");
        long talep = ((Number) JsonPath.read(mk.post("/api/benim/takas", "{\"atamaId\":" + atama + ",\"neden\":\"IZIN\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id")).longValue();
        Tarayici atamaci = atamaci();
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("ATAMACI_ONAYI_BEKLIYOR"));

        atamaci.post("/api/atama/sefer/" + id + "/bol", govde(id, false)).andExpect(status().isOk());

        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("BAYAT"));
        mk.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("BAYAT"));
        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE id = ?", Long.class, atama)).as("bayat talep atamayı değiştirmedi").isEqualTo(m);
    }

    @Test
    void acikTakasTalebi_bolunceBAYAT_karsiTarafKabul409TalepBayat() throws Exception {
        long id = sefer("T1");
        long diger = v.sefer("T2", GUN.plusDays(1), "08:00", "09:00", v.ankara, v.ankara);
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        long ataA = v.atama(id, a, "ASIL");
        long ataB = v.atama(diger, b, "ASIL");
        Tarayici ta = makinistGirisli(a, "D001");
        Tarayici tb = makinistGirisli(b, "D002");
        long talep = ((Number) JsonPath.read(ta.post("/api/benim/takas", "{\"atamaId\":" + ataA + ",\"hedefAtamaId\":" + ataB + ",\"neden\":\"IZIN\"}").andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.id")).longValue();

        atamaci().post("/api/atama/sefer/" + id + "/bol", govde(id, false)).andExpect(status().isOk());

        tb.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("BAYAT"));
        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
    }

    // ------------------------------------------------------------------ FR-09

    @Test
    void gecikmeDevamTrue_parca2Yukselir_iptalDevamTrue_hepsiIptal() throws Exception {
        long p1 = sefer("T1");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p1 + "/bol", govde(p1, false)).andExpect(status().isOk());
        long p2 = parca2(p1);

        t.post("/api/sefer/" + p1 + "/gecikme", "{\"kalkisGecikmeDk\":40,\"varisGecikmeDk\":40,\"neden\":\"GECIKME\",\"devam\":true}").andExpect(status().isOk());
        assertThat(gecikme(p1)).isEqualTo("40/40");
        assertThat(gecikme(p2)).as("parça 2 yükselir").isEqualTo("40/40");

        t.post("/api/sefer/" + p1 + "/iptal", "{\"neden\":\"IPTAL\",\"devam\":true}").andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT durum FROM sefer WHERE id IN (?, ?) ORDER BY id", String.class, p1, p2)).containsExactly("IPTAL", "IPTAL");
    }

    // ------------------------------------------------------------------ önizleme

    @Test
    void onizle_httpSonucuGercekleAyni_tablolarDegismez() throws Exception {
        yonetici().put("/api/kural/K-20/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        long id = sefer("T1");
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        v.atama(v.sefer("KARS-ANK", GUN.plusDays(1), "10:00", "12:00", kars, kars), a, "ASIL");
        Tarayici t = atamaci();
        int sefer = sayi("SELECT count(*) FROM sefer");
        int atama = sayi("SELECT count(*) FROM atama");
        int kayit = sayi("SELECT count(*) FROM degisiklik_kaydi");
        List<Map<String, Object>> seferOnce = jdbc.queryForList("SELECT * FROM sefer ORDER BY id");

        String onizleme = t.post("/api/atama/sefer/" + id + "/bol", govde(id, true)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(sefer);
        assertThat(sayi("SELECT count(*) FROM atama")).isEqualTo(atama);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).isEqualTo(kayit);
        assertThat(jdbc.queryForList("SELECT * FROM sefer ORDER BY id")).isEqualTo(seferOnce);

        String gercek = t.post("/api/atama/sefer/" + id + "/bol", govde(id, false)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Object>read(gercek, "$.yeniKirmizi")).isEqualTo(JsonPath.<Object>read(onizleme, "$.yeniKirmizi"));
        assertThat(JsonPath.<List<String>>read(gercek, "$.parcalar[*].planlananVaris")).isEqualTo(JsonPath.<List<String>>read(onizleme, "$.parcalar[*].planlananVaris"));
        assertThat(JsonPath.<List<String>>read(gercek, "$.parcalar[*].kalkisGar")).isEqualTo(JsonPath.<List<String>>read(onizleme, "$.parcalar[*].kalkisGar"));
        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(sefer + 1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL'")).isEqualTo(1);
    }
}
