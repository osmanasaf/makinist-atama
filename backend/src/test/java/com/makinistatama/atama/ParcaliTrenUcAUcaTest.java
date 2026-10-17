package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.sefer.SeferYuklemeServisi;
import com.makinistatama.sefer.TestExcel;

/**
 * F2c Faz 2 (FR-01, FR-04, FR-05, FR-09): Ankara 08:00 → Sivas 13:30 / 13:40 → Kars 20:10 örneği; parça = sıradan sefer satırı. Yöntemler birbirinden BAĞIMSIZDIR
 * (her biri kendi trenini kurar, MK-64); ortak kurulum {@link Kurulum}, {@code ParcaliTrenIsletmeUcAUcaTest} ile paylaşılır.
 */
class ParcaliTrenUcAUcaTest extends TestVeritabani {

    static final String GUN = "2026-10-12";
    static final String TREN = "DE-1503";

    @Autowired
    SeferYuklemeServisi yukleme;

    Kurulum k;

    @BeforeEach
    void kur() {
        k = new Kurulum(jdbc, yukleme);
    }

    /** Ortak kurulum (ParcaliTrenIsletmeUcAUcaTest de kullanır): tanımlar, makinist ekleme, örnek treni Excel yoluyla yükleme. */
    static final class Kurulum {
        final JdbcTemplate jdbc;
        final SeferYuklemeServisi yukleme;
        final long ankara;
        final long sivas;
        final long kars;
        final long hatAnkaraKars;
        final long hatSivasKars;
        final long yht;

        Kurulum(JdbcTemplate jdbc, SeferYuklemeServisi yukleme) {
            this.jdbc = jdbc;
            this.yukleme = yukleme;
            ankara = TestTanimlari.gar(jdbc, "Ankara");
            sivas = TestTanimlari.gar(jdbc, "Sivas");
            kars = TestTanimlari.gar(jdbc, "Kars");
            hatAnkaraKars = TestTanimlari.hat(jdbc, "Ankara–Kars");
            hatSivasKars = TestTanimlari.hat(jdbc, "Sivas–Kars");
            yht = TestTanimlari.aracTipi(jdbc, "YHT");
        }

        /** Belgeleri geçerli AKTIF makinist; YHT + verilen hat bröveleri. */
        long makinist(String sicil, long bagliGar, long... hatlar) {
            long id = jdbc.queryForObject("""
                    INSERT INTO makinist (sicil, ad_soyad, bagli_gar_id, durum, ehliyet_bitis, saglik_bitis, psikoteknik_bitis, son_surus)
                    VALUES (?, ?, ?, 'AKTIF', '2030-01-01', '2030-01-01', '2030-01-01', '2026-09-01') RETURNING id""", Long.class, sicil, "Demo Makinist " + sicil, bagliGar);
            jdbc.update("INSERT INTO brove_arac_tipi (makinist_id, arac_tipi_id, sinav_tarihi) VALUES (?, ?, '2024-01-01')", id, yht);
            for (long hat : hatlar) {
                jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-09-01')", id, hat);
            }
            return id;
        }

        /** {@code gun} günü Ankara 08:00 → Sivas 13:30 / 13:40 → Kars 20:10 (1103 km; parça 2 hattı Sivas–Kars) trenini Excel yoluyla yükler; döner [parça 1, parça 2] sefer kimlikleri. */
        long[] ankaraSivasKars(String tren, String gun) {
            return parcali(tren, gun, "08:00", "13:30", "13:40", "20:10");
        }

        /** Aynı yapı, verilen saatlerle (km sabit 603 + 500). */
        long[] parcali(String tren, String gun, String kalkis, String sivasVaris, String sivasKalkis, String varis) {
            String[] g = gun.split("-");
            String tarih = g[2] + "." + g[1] + "." + g[0];
            byte[] dosya = TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(new String[] { tarih, tren, "Ankara", kalkis, "Kars", varis, "YHT", "Ankara–Kars", "1103" }),
                    List.<String[]>of(new String[] { tren, "1", "Sivas", sivasVaris, sivasKalkis, "603", "Sivas–Kars", "" }));
            assertThat(yukleme.yukle(dosya, false).eklenen()).isEqualTo(2);
            List<Long> idler = jdbc.queryForList("SELECT id FROM sefer WHERE tren_no = ? ORDER BY parca_no", Long.class, tren);
            return new long[] { idler.get(0), idler.get(1) };
        }
    }

    @Test
    void yukleme_gunListesindeIkiParcaSatiri_ikisiDeBos() throws Exception {
        k.ankaraSivasKars(TREN, GUN);

        atamaci().get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar", hasSize(2)))
                .andExpect(jsonPath("$.satirlar[0].parcaNo").value(1)).andExpect(jsonPath("$.satirlar[0].parcaSayisi").value(2))
                .andExpect(jsonPath("$.satirlar[0].kalkisGar").value("Ankara")).andExpect(jsonPath("$.satirlar[0].varisGar").value("Sivas"))
                .andExpect(jsonPath("$.satirlar[0].hat").value("Ankara–Kars")).andExpect(jsonPath("$.satirlar[0].km").value(603.0))
                .andExpect(jsonPath("$.satirlar[1].parcaNo").value(2)).andExpect(jsonPath("$.satirlar[1].kalkisGar").value("Sivas"))
                .andExpect(jsonPath("$.satirlar[1].varisGar").value("Kars")).andExpect(jsonPath("$.satirlar[1].hat").value("Sivas–Kars"))
                .andExpect(jsonPath("$.satirlar[1].planlananKalkis").value("2026-10-12T13:40:00+03:00")).andExpect(jsonPath("$.satirlar[1].km").value(500.0))
                .andExpect(jsonPath("$.satirlar[*].makinistler", hasItem(hasSize(0))));
    }

    @Test
    void parcaBasinaAtama_ihlalsiz_veKonumK20ParcaninKalkisGarinaGore() throws Exception {
        long[] p = k.ankaraSivasKars(TREN, GUN);
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars);
        long b = k.makinist("D-B", k.sivas, k.hatSivasKars);
        long c = k.makinist("D-C", k.ankara, k.hatAnkaraKars, k.hatSivasKars); // Ankara'ya bağlı: Sivas'tan kalkan parçaya yerleşemez (K-20)
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + p[1] + "/adaylar").andExpect(jsonPath("$[?(@.sicil=='D-B')].uygun").value(contains(true)))
                .andExpect(jsonPath("$[?(@.sicil=='D-B')].ihlaller[*].kod").value(not(hasItem("K-20"))))
                .andExpect(jsonPath("$[?(@.sicil=='D-C')].ihlaller[*].kod").value(hasItem("K-20")));
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(c)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists()); // K-20 UYARI: gerekçe ister
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler[0].sicil").value("D-A")).andExpect(jsonPath("$.satirlar[1].makinistler[0].sicil").value("D-B"))
                .andExpect(jsonPath("$.satirlar[*].kirmizi", not(hasItem(true))));
    }

    @Test
    void gorevOzeti_makinistEkraninda_parcaninKendiGarVeSaatleri() throws Exception {
        java.time.LocalDate yarin = java.time.LocalDate.now(com.makinistatama.ortak.ZamanAyari.BOLGE).plusDays(1); // "benim" ekranı gerçek saatle çalışır (ufuk bugün..+6)
        long[] p = k.ankaraSivasKars(TREN, yarin.toString());
        long b = k.makinist("D-B", k.sivas, k.hatSivasKars);
        atamaci().post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());
        String gg = java.time.format.DateTimeFormatter.ofPattern("dd.MM").format(yarin);

        makinistGirisli(b, "D-B").get("/api/benim/gorevler").andExpect(status().isOk()).andExpect(jsonPath("$.gorevler", hasSize(1)))
                .andExpect(jsonPath("$.gorevler[0].ozet").value("Asıl · Sivas " + gg + " 13:40 → Kars " + gg + " 20:10")).andExpect(jsonPath("$.gorevler[0].parcaNo").value(2))
                .andExpect(jsonPath("$.gorevler[0].parcaSayisi").value(2)).andExpect(jsonPath("$.gorevler[0].trenNo").value(TREN));
    }

    @Test
    void yardimci_parcaBasina_digerParcadaGorevliSayilmaz() throws Exception {
        long[] p = k.ankaraSivasKars(TREN, GUN);
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars);
        long c = k.makinist("D-C", k.ankara, k.hatAnkaraKars);
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[0], "{\"makinistId\":" + c + ",\"rol\":\"YARDIMCI\"}").andExpect(status().isCreated());

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler", hasSize(2))).andExpect(jsonPath("$.satirlar[1].makinistler", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE makinist_id = ? AND sefer_id = ?", Integer.class, c, p[1])).isZero();
        // C'nin parça 2'deki rolü boş: aday listesinde bu seferde görevli değil
        t.get("/api/atama/sefer/" + p[1] + "/adaylar?rol=YARDIMCI").andExpect(jsonPath("$[?(@.sicil=='D-C')].buSeferdeRol").value(contains((Object) null)));
    }

    /** R2: aynı makinist ardışık iki parçayı alabilir; kısa trende (toplam 5 s) hazırlık/alıkoyma 0 iken ENGEL yoktur. */
    @Test
    void ayniMakinistArdisikIkiParca_hazirlikAlikoymaSifirken_engelsiz() throws Exception {
        long[] p = k.parcali("DE-1504", GUN, "08:00", "10:00", "10:10", "13:00");
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated()); // 10 dk bekleme: çakışma yok
    }

    /** R2 (tur 3'te tersine çevrildi, review INFO): makinist trende kalır, aynı makinistin ardışık parçaları arasında hazırlık/alıkoyma uygulanmaz; ayrıntı {@code ParcaliTrenTur3Test}. */
    @Test
    void ayniMakinistArdisikIkiParca_hazirlikVarken_cakismaYok() throws Exception {
        long[] p = k.parcali("DE-1504", GUN, "08:00", "10:00", "10:10", "13:00");
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        yonetici().put("/api/parametre/gorev.hazirlik_dk", "{\"deger\":\"20\",\"neden\":\"R2 denemesi\"}").andExpect(status().isOk());
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
    }

    /** Parçalar kendi başına ayrı sefer olsa da günlük çalışma toplamı makinist başınadır: 12 saatlik tren tek makinistte K-13'e takılır (kural değişmez). */
    @Test
    void ayniMakinistTumTreni_gunlukCalismaSiniriniAsar_K13() throws Exception {
        long[] p = k.ankaraSivasKars(TREN, GUN); // 5 s 30 dk + 6 s 30 dk = 12 s > 11 s
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-13")));
    }

    @Test
    void hatBroveParcaBasina_SivasKarsBrovesiYalnizParca2yeYeter() throws Exception {
        long[] p = k.ankaraSivasKars(TREN, GUN);
        long sivasEkibi = k.makinist("D-B", k.sivas, k.hatSivasKars);
        long yalnizAnkaraKars = k.makinist("D-X", k.sivas, k.hatAnkaraKars);
        Tarayici t = atamaci();

        t.get("/api/atama/sefer/" + p[1] + "/adaylar").andExpect(jsonPath("$[?(@.sicil=='D-B')].uygun").value(contains(true)))
                .andExpect(jsonPath("$[?(@.sicil=='D-X')].ihlaller[*].kod").value(hasItem("K-06")));
        t.get("/api/atama/sefer/" + p[0] + "/adaylar").andExpect(jsonPath("$[?(@.sicil=='D-B')].ihlaller[*].kod").value(hasItem("K-06")));
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(yalnizAnkaraKars)).andExpect(status().isUnprocessableContent());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(sivasEkibi)).andExpect(status().isCreated());
    }

    @Test
    void adaletRaporu_kmVeSaatParcaBasina_trenToplaminaEsit() throws Exception {
        long[] p = k.ankaraSivasKars(TREN, GUN);
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars);
        long b = k.makinist("D-B", k.sivas, k.hatSivasKars);
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(b)).andExpect(status().isCreated());

        t.get("/api/rapor/adalet?ay=2026-10").andExpect(status().isOk())
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-A')].gorevSaat").value(contains(5.5))).andExpect(jsonPath("$.satirlar[?(@.sicil=='D-A')].km").value(contains(603.0)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-A')].seferSayisi").value(contains(1)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-B')].gorevSaat").value(contains(6.5))).andExpect(jsonPath("$.satirlar[?(@.sicil=='D-B')].km").value(contains(500.0)))
                .andExpect(jsonPath("$.satirlar[?(@.sicil=='D-B')].seferSayisi").value(contains(1)));
    }

    @Test
    void ozelKural_tekSeferSuresi_parcaninSuresiyleOlculur() throws Exception {
        long[] p = k.ankaraSivasKars(TREN, GUN);
        long a = k.makinist("D-A", k.ankara, k.hatAnkaraKars, k.hatSivasKars);
        String kural = "{\"ad\":\"Tek sefer 6 s\",\"olcu\":\"TEK_SEFER_SURESI\",\"pencere\":\"SEFER\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":6,\"siddet\":\"ENGEL\","
                + "\"gerekce\":\"F2c denemesi\",\"gecerlilikBas\":\"2026-01-01\"}";
        yonetici().post("/api/ozel-kural", kural).andExpect(status().isCreated());
        Tarayici t = atamaci();

        // tren bütünü 11 s 10 dk, parçalar 5 s 30 dk ve 6 s 30 dk: yalnız parça 2 sınırı aşar
        t.get("/api/atama/sefer/" + p[0] + "/adaylar").andExpect(jsonPath("$[?(@.sicil=='D-A')].ihlaller[*].kod").value(not(hasItem("Ö-001"))));
        t.get("/api/atama/sefer/" + p[1] + "/adaylar").andExpect(jsonPath("$[?(@.sicil=='D-A')].ihlaller[*].kod").value(hasItem("Ö-001")));
        t.post("/api/atama/sefer/" + p[0], AtamaVeri.ataGovdesi(a)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + p[1], AtamaVeri.ataGovdesi(a)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("Ö-001")));
    }
}
