package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.sefer.SeferBolIstegi;
import com.makinistatama.sefer.SeferBolIstegi.Nokta;
import com.makinistatama.sefer.TestExcel;

/**
 * Seferi birleştirme (geri alma; T5.1-T5.3, MK-122..128, FR-10/FR-11): çekirdek + orkestratör, doğrudan servis çağrısı ve uç. Örnek: DE-1503 Ankara 08:00 -> Kars 18:10 (km 1103), Sivas 13:30/13:40,
 * ara km 603 (bölme sonrası 603 + 500). Seferler gerçek bugünden 3 gün sonra (takas/gördüm gerçek saate bağlı); bölme/birleştirmenin "başlamış mı" ölçütü sabit {@code makinist.atama.simdi}.
 * Her ihlal testi: doğru HTTP durumu + kod, hiçbir tablo değişmez, sıfır denetim kaydı.
 */
class SeferBirlestirTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.now(ZamanAyari.BOLGE).plusDays(3);

    @Autowired
    SeferParcalamaServisi servis;

    @Autowired
    com.makinistatama.atama.GordumServisi gordumServisi;

    AtamaVeri v;
    long sivas;
    long kars;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        kars = TestTanimlari.gar(jdbc, "Kars");
    }

    // ------------------------------------------------------------------ yardımcılar

    long sefer(String tren) {
        long id = v.sefer(tren, GUN, "08:00", "18:10", v.ankara, kars);
        jdbc.update("UPDATE sefer SET km = 1103 WHERE id = ?", id);
        return id;
    }

    long versiyon(long id) {
        return jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id);
    }

    int sayi(String sql, Object... arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    SeferBolIstegi bolIstegi(long id) {
        return new SeferBolIstegi(versiyon(id), List.of(new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), null, null)), false);
    }

    /** Seferi böler (parça 1 = verilen kimlik); parça 2'nin kimliğini döndürür. */
    long bol(long id) {
        return servis.bol(id, bolIstegi(id)).parcalar().get(1).id();
    }

    long parca2(long parca1) {
        return jdbc.queryForObject("SELECT id FROM sefer WHERE parca_no = 2 AND tren_no = (SELECT tren_no FROM sefer WHERE id = ?)", Long.class, parca1);
    }

    BirlestirSonucuDto birlestir(long id) {
        return servis.birlestir(id, new SeferBirlestirIstegi(versiyon(id), false));
    }

    BirlestirSonucuDto onizle(long id) {
        return servis.birlestir(id, new SeferBirlestirIstegi(versiyon(id), true));
    }

    /** Birleştirmenin okuduğu/yazdığı tablolar: satır içeriği (id sırasıyla). */
    Map<String, List<Map<String, Object>>> anlik() {
        return Map.of("sefer", jdbc.queryForList("SELECT * FROM sefer ORDER BY id"), "atama", jdbc.queryForList("SELECT * FROM atama ORDER BY id"),
                "gordum", jdbc.queryForList("SELECT * FROM gorev_gordum ORDER BY id"), "olay", jdbc.queryForList("SELECT * FROM atama_olayi ORDER BY id"),
                "kayit", jdbc.queryForList("SELECT * FROM degisiklik_kaydi ORDER BY id"));
    }

    /** Birleştirme reddedilir: durum + kod, hiçbir tablo değişmez (denetim kaydı dahil). Mesajı döndürür. */
    String reddedilir(long id, long versiyon, HttpStatus durum, String kod) {
        var once = anlik();
        UygulamaHatasi hata = (UygulamaHatasi) catchThrowable(() -> servis.birlestir(id, new SeferBirlestirIstegi(versiyon, false)));
        assertThat(hata).as("birleştirme " + kod + " ile reddedilmeli").isNotNull();
        assertThat(hata.durum()).isEqualTo(durum);
        assertThat(hata.kod()).isEqualTo(kod);
        assertThat(anlik()).as("hiçbir şey değişmez").isEqualTo(once);
        return hata.getMessage();
    }

    static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable c) {
        return org.assertj.core.api.Assertions.catchThrowable(c);
    }

    // ------------------------------------------------------------------ başarı: alanlar, silinenler

    @Test
    void birlestir_basarili_parca1IdKorunur_alanlarGeriYazilir_parca2VeAtamalariSilinir_gordumTemizlenir() {
        long id = sefer("T1");
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 20, varis_gecikme_dk = 30 WHERE id = ?", id);
        long a = v.makinist("D001");
        long c = v.makinist("D002");
        long ataA = v.atama(id, a, "ASIL");
        long ataC = v.atama(id, c, "YARDIMCI");
        long p2 = bol(id);
        long ataA2 = v.atama(p2, a, "ASIL"); // devam eden aynı makinist: birleşince parça 1'deki ataya katılır
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (?, ?, ?, 'ozet', now())", ataA2, a, p2);
        jdbc.update("INSERT INTO gorev_gordum (atama_id, makinist_id, sefer_id, ozet, zaman) VALUES (?, ?, ?, 'ozet', now())", ataA, a, id);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 5, varis_gecikme_dk = 7 WHERE id = ?", id);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 11, varis_gecikme_dk = 30 WHERE id = ?", p2);
        long planlananKalkis = jdbc.queryForObject("SELECT extract(epoch FROM planlanan_kalkis)::bigint FROM sefer WHERE id = ?", Long.class, id);
        long planlananVaris = jdbc.queryForObject("SELECT extract(epoch FROM planlanan_varis)::bigint FROM sefer WHERE id = ?", Long.class, p2);
        long eskiVersiyon = versiyon(id);
        int kayitOnce = sayi("SELECT count(*) FROM degisiklik_kaydi");

        BirlestirSonucuDto s = birlestir(id);

        assertThat(s.sefer().id()).as("parça 1 kimliği korunur").isEqualTo(id);
        assertThat(s.silinenSeferIdler()).containsExactly(p2);
        assertThat(s.yeniKirmizi()).isNotNull().isEmpty();
        assertThat(s.sefer().parcaNo()).isNull();
        assertThat(s.sefer().varisGar()).isEqualTo("Kars");
        assertThat(s.sefer().km()).isEqualByComparingTo("1103");
        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM sefer WHERE id = ?", id);
        assertThat(r.get("parca_no")).isNull();
        assertThat(r.get("parca_sayisi")).isNull();
        assertThat(r.get("tren_gunu")).isNull();
        assertThat(r.get("varis_gar_id")).isEqualTo(kars);
        assertThat(r.get("kalkis_gar_id")).isEqualTo(v.ankara);
        assertThat(((BigDecimal) r.get("km"))).isEqualByComparingTo("1103");
        assertThat(r.get("kalkis_gecikme_dk")).as("kalkış gecikmesi = parça 1").isEqualTo(5);
        assertThat(r.get("varis_gecikme_dk")).as("varış gecikmesi = SON parça").isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM planlanan_kalkis)::bigint FROM sefer WHERE id = ?", Long.class, id)).isEqualTo(planlananKalkis);
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM planlanan_varis)::bigint FROM sefer WHERE id = ?", Long.class, id)).as("varış = son parçanınki").isEqualTo(planlananVaris);
        assertThat(versiyon(id)).isGreaterThan(eskiVersiyon);

        assertThat(sayi("SELECT count(*) FROM sefer")).as("parça 2 satırı silinir").isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", p2)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM atama ORDER BY id", Long.class)).as("parça 1 atamaları aynı kimlikle kalır; parça 2'ninki silinir").containsExactly(ataA, ataC);
        assertThat(jdbc.queryForList("SELECT atama_id FROM gorev_gordum", Long.class)).as("silinen atamanın öksüz onayı temizlenir; kalan atamanınki durur").containsExactly(ataA);

        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi") - kayitOnce).as("tek denetim kaydı").isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'")).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA_KALDIR'")).as("silinen atamalar için ayrı ATAMA_KALDIR yok").isZero();
        Map<String, Object> k = jdbc.queryForMap("SELECT varlik_tipi, varlik_id, sefer_id, makinist_id, eski, yeni, ayrinti FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'");
        assertThat(k.get("varlik_tipi")).isEqualTo("SEFER");
        assertThat(k.get("varlik_id")).isEqualTo(id);
        assertThat(k.get("sefer_id")).isEqualTo(id);
        assertThat(k.get("makinist_id")).isNull();
        assertThat((String) k.get("eski")).startsWith("1/2").contains("Sivas").contains(" | 2/2");
        assertThat((String) k.get("yeni")).contains("T1").contains("Ankara").contains("Kars");
        assertThat((String) k.get("ayrinti")).contains("\"silinenSeferIdler\":[" + p2 + "]").contains("\"atamaId\":" + ataA2).contains("\"sicil\":\"D001\"").contains("\"rol\":\"ASIL\"")
                .contains("\"parcaNo\":2").contains("\"kalkisGecikmeDk\":11").contains("\"varisGecikmeDk\":7");
    }

    @Test
    void birlestir_zincirinHerhangiBirParcasindanCagrilir_parca1Doner() {
        long id = sefer("T1");
        long p2 = bol(id);

        BirlestirSonucuDto s = servis.birlestir(p2, new SeferBirlestirIstegi(versiyon(p2), false)); // yoldaki {id} = parça 2, versiyon = parça 2'nin

        assertThat(s.sefer().id()).isEqualTo(id);
        assertThat(s.silinenSeferIdler()).containsExactly(p2);
        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM sefer WHERE id = ? AND parca_no IS NULL", id)).isEqualTo(1);
    }

    @Test
    void birlestir_ucParca_ikiSatirSilinir_kmToplam_kmBosIseBos() {
        long id = sefer("T1");
        servis.bol(id, new SeferBolIstegi(versiyon(id), List.of(new Nokta(sivas, LocalTime.of(11, 0), LocalTime.of(11, 10), new BigDecimal("400"), null, null),
                new Nokta(TestTanimlari.gar(jdbc, "Erzurum"), LocalTime.of(15, 0), LocalTime.of(15, 10), new BigDecimal("900"), null, null)), false));
        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(3);

        BirlestirSonucuDto s = birlestir(id);

        assertThat(s.silinenSeferIdler()).hasSize(2);
        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(1);
        assertThat(s.sefer().km()).isEqualByComparingTo("1103");

        long id2 = v.sefer("T2", GUN, "08:00", "18:10", v.ankara, kars); // km null
        jdbc.update("UPDATE sefer SET km = NULL WHERE id = ?", id2);
        servis.bol(id2, new SeferBolIstegi(versiyon(id2), List.of(new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), null, null, null)), false));
        assertThat(birlestir(id2).sefer().km()).isNull();
    }

    // ------------------------------------------------------------------ yeniKirmizi

    /** A: parça 1 (08:00-13:30 Sivas'ta biter); aynı gün 17:00-18:00 Sivas görevi (parça 1'e uygun). Birleşince A'nın görevi 18:10'a uzar: yeni görevle çakışır (kırmızı). */
    @Test
    void birlestir_birlesikUzunGorev_baskaGorevleCakisir_yeniKirmiziListelenir() throws Exception {
        long id = sefer("T1");
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        long diger = v.atama(v.sefer("SIV-SIV", GUN, "17:00", "18:00", sivas, sivas), a, "ASIL");
        bol(id);

        BirlestirSonucuDto s = birlestir(id);

        assertThat(s.yeniKirmizi()).anySatisfy(e -> {
            assertThat(e.atamaId()).isEqualTo(diger);
            assertThat(e.trenNo()).isEqualTo("SIV-SIV");
            assertThat(e.nedenler()).isNotEmpty();
        });
    }

    // ------------------------------------------------------------------ onizleme

    @Test
    void onizle_sonucGercegeAyni_hicbirTabloDegismez() throws Exception {
        long id = sefer("T1");
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        v.atama(v.sefer("SIV-SIV", GUN, "17:00", "18:00", sivas, sivas), a, "ASIL");
        long p2 = bol(id);
        v.atama(p2, a, "ASIL");
        var once = anlik();
        int sefer = sayi("SELECT count(*) FROM sefer");
        int atama = sayi("SELECT count(*) FROM atama");
        int kayit = sayi("SELECT count(*) FROM degisiklik_kaydi");

        BirlestirSonucuDto onizleme = onizle(id);

        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(sefer);
        assertThat(sayi("SELECT count(*) FROM atama")).isEqualTo(atama);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).as("MANDATORY kayıt da geri gitti").isEqualTo(kayit);
        assertThat(anlik()).as("parça 1 satırı sürüm dahil özgün").isEqualTo(once);
        assertThat(onizleme.silinenSeferIdler()).containsExactly(p2);

        BirlestirSonucuDto gercek = birlestir(id);

        assertThat(gercek).usingRecursiveComparison().isEqualTo(onizleme);
        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(sefer - 1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ atama_olayi kalemi

    @Test
    void parca2ninOlayKalemi_birlesinceMakinistEkraninda_seferTire() {
        long id = sefer("T1");
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        v.atama(id, a, "ASIL");
        long p2 = bol(id);
        long ataB = v.atama(p2, b, "ASIL");
        jdbc.update("INSERT INTO atama_olayi (makinist_id, sefer_id, tur, ozet, neden_etiketi, zaman) VALUES (?, ?, 'KALDIRILDI', 'eski özet', 'IZIN', now())", b, p2);
        assertThat(gordumServisi.kaldirilanlar(b)).singleElement().satisfies(o -> {
            assertThat(o.seferId()).isEqualTo(p2);
            assertThat(o.trenNo()).isEqualTo("T1");
        });
        jdbc.update("DELETE FROM atama WHERE id = ?", ataB); // B parça 2'den çıkarılmış olmalı ki birleşsin (bkz. atama koşulları)

        birlestir(id);

        assertThat(sayi("SELECT count(*) FROM atama_olayi")).as("olay kalemi FK'siz kalır").isEqualTo(1);
        assertThat(gordumServisi.kaldirilanlar(b)).singleElement().satisfies(o -> {
            assertThat(o.seferId()).isEqualTo(p2);
            assertThat(o.trenNo()).as("sefer silindi: ekranda '—'").isNull();
            assertThat(o.tarih()).isNull();
            assertThat(o.ozet()).isEqualTo("eski özet");
        });
    }

    // ------------------------------------------------------------------ FR-10 koşul matrisi (her ihlal: kod + hiçbir şey değişmez)

    @Test
    void ihlal_PARCA_IPTAL() {
        long id = sefer("T1");
        long p2 = bol(id);
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", p2);

        reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "PARCA_IPTAL");
    }

    @Test
    void ihlal_SEFER_BASLAMIS_422() {
        long id = sefer("T1");
        bol(id);
        int kaydir = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2026, 10, 8), GUN); // zincir 08.10'a
        jdbc.update("UPDATE sefer SET tarih = tarih - ?, planlanan_kalkis = planlanan_kalkis - ? * interval '1 day', planlanan_varis = planlanan_varis - ? * interval '1 day', tren_gunu = tren_gunu - ?", kaydir, kaydir, kaydir, kaydir); // bütün zincir sabit şimdiden (10.10 10:00) önceye: parça 1 başlamış
        assertThat(sayi("SELECT count(*) FROM sefer WHERE planlanan_kalkis < TIMESTAMPTZ '2026-10-10T10:00:00+03'")).isEqualTo(2);

        reddedilir(id, versiyon(id), HttpStatus.UNPROCESSABLE_ENTITY, "SEFER_BASLAMIS");
    }

    /** Excel "Değişim Noktaları" ile yüklenmiş 27 saatlik tren (parça başı 16 s sınırında, toplam > 24 s): birleşik satır sefer_sure_ck'yi (24 s) ihlal ederdi. */
    @Test
    void ihlal_BIRLESTIRME_SURE_ASIYOR_excelden27SaatlikZincir() throws Exception {
        TestTanimlari.hat(jdbc, "Ankara–Kars");
        TestTanimlari.gar(jdbc, "Erzincan");
        byte[] xlsx = TestExcel.sefer(TestExcel.BASLIK,
                List.<String[]>of(new String[] { GUN.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")), "UZUN-1", "Ankara", "08:00", "Kars", "11:00", "YHT", "Ankara–Kars", "1500" }),
                List.<String[]>of(new String[] { "UZUN-1", "1", "Sivas", "20:00", "20:10", "600", null, null }, new String[] { "UZUN-1", "2", "Erzincan", "09:00", "09:10", "1000", null, null }));
        yonetici().istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle").file(new MockMultipartFile("dosya", "s.xlsx", "application/octet-stream", xlsx)).param("onizle", "false"))
                .andExpect(status().isOk());
        assertThat(sayi("SELECT count(*) FROM sefer WHERE tren_no = 'UZUN-1' AND parca_sayisi = 3")).as("Excel 3 parça yükledi").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM max(planlanan_varis) - min(planlanan_kalkis)) / 3600 FROM sefer WHERE tren_no = 'UZUN-1'", Double.class)).isEqualTo(27.0);
        long p1 = jdbc.queryForObject("SELECT id FROM sefer WHERE tren_no = 'UZUN-1' AND parca_no = 1", Long.class);

        String mesaj = reddedilir(p1, versiyon(p1), HttpStatus.CONFLICT, "BIRLESTIRME_SURE_ASIYOR");

        assertThat(mesaj).contains("24");
    }

    @Test
    void ihlal_BIRLESTIRME_ATAMA_FARKLI_mesajRolParcaSicilSoyler() {
        long id = sefer("T1");
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        v.atama(id, a, "ASIL");
        long p2 = bol(id);
        v.atama(p2, b, "ASIL");

        String mesaj = reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "BIRLESTIRME_ATAMA_FARKLI");

        assertThat(mesaj).contains("ASIL").contains("2. parça").contains("D002").contains("D001");
    }

    @Test
    void ihlal_BIRLESTIRME_ATAMA_FARKLI_yolcuParca1deYok() {
        long id = sefer("T1");
        long a = v.makinist("D001");
        long y1 = v.makinist("D003");
        long y2 = v.makinist("D004");
        v.atama(id, a, "ASIL");
        v.atama(id, y1, "YOLCU");
        long p2 = bol(id);
        v.atama(p2, y1, "YOLCU"); // ⊆ parça 1: serbest
        v.atama(p2, y2, "YOLCU"); // y2 parça 1'de yolcu değil -> ihlal (parça 1'de başka yolcu var: FARKLI)

        String mesaj = reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "BIRLESTIRME_ATAMA_FARKLI");

        assertThat(mesaj).contains("YOLCU").contains("2. parça").contains("D004");
    }

    @Test
    void ihlal_BIRLESTIRME_ATAMA_PARCA1_BOS() {
        long id = sefer("T1");
        long a = v.makinist("D001");
        long c = v.makinist("D002");
        v.atama(id, a, "ASIL"); // parça 1'de YARDIMCI boş
        long p2 = bol(id);
        v.atama(p2, c, "YARDIMCI");

        String mesaj = reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "BIRLESTIRME_ATAMA_PARCA1_BOS");

        assertThat(mesaj).contains("YARDIMCI").contains("2. parça").contains("D002");
    }

    @Test
    void ihlal_BIRLESTIRME_HAT_FARKLI() {
        long id = sefer("T1");
        long p2 = bol(id);
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, p2);

        String mesaj = reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "BIRLESTIRME_HAT_FARKLI");

        assertThat(mesaj).containsIgnoringCase("hat");
    }

    @Test
    void ihlal_BIRLESTIRME_GECIKME_UYUMSUZ_varisKalkistanOnceDuser() {
        long id = sefer("T1");
        long p2 = bol(id);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 800, varis_gecikme_dk = 800 WHERE id = ?", id);
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 0, varis_gecikme_dk = 0 WHERE id = ?", p2);

        String mesaj = reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "BIRLESTIRME_GECIKME_UYUMSUZ");

        assertThat(mesaj).containsIgnoringCase("gecikme");
    }

    @Test
    void ihlal_SEFER_PARCALI_DEGIL() {
        long id = sefer("T1");

        reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "SEFER_PARCALI_DEGIL");
    }

    @Test
    void ihlal_ZINCIR_EKSIK() {
        long id = sefer("T1");
        long p2 = bol(id);
        jdbc.update("DELETE FROM sefer WHERE id = ?", p2); // Excel/elle silme: parca_sayisi 2 ama tek satır

        reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "ZINCIR_EKSIK");
    }

    @Test
    void ihlal_VERSIYON_CAKISMASI_ve_404() {
        long id = sefer("T1");
        bol(id);

        reddedilir(id, versiyon(id) + 5, HttpStatus.CONFLICT, "VERSIYON_CAKISMASI");

        var once = anlik();
        assertThatThrownBy(() -> servis.birlestir(99999, new SeferBirlestirIstegi(0, false))).isInstanceOfSatisfying(UygulamaHatasi.class, e -> assertThat(e.durum()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(anlik()).isEqualTo(once);
    }

    @Test
    void ihlal_oncelik_atamaKosuluSeferKosulundanSonraGelir_iptalParcaVeAtamaFarkiBirlikte_PARCA_IPTAL() {
        long id = sefer("T1");
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        v.atama(id, a, "ASIL");
        long p2 = bol(id);
        v.atama(p2, b, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", p2);

        reddedilir(id, versiyon(id), HttpStatus.CONFLICT, "PARCA_IPTAL");
    }

    // ------------------------------------------------------------------ uç: yetki, biçim, takas

    @Test
    void uc_yetki_makinist403_atamaciVeYonetici200_biçim_onizleVarsayilanYazar() throws Exception {
        long id = sefer("T1");
        long id2 = sefer("T2");
        long m = v.makinist("D001");
        long p2 = bol(id);
        long p22 = bol(id2);
        Tarayici makinist = makinistGirisli(m, "D001");

        makinist.post("/api/atama/sefer/" + id + "/birlestir", "{\"versiyon\":" + versiyon(id) + "}").andExpect(status().isForbidden());
        assertThat(sayi("SELECT count(*) FROM sefer WHERE id = ?", p2)).isEqualTo(1);

        atamaci().post("/api/atama/sefer/" + id + "/birlestir", "{\"versiyon\":" + versiyon(id) + "}") // onizle yok: varsayılan false (yazar)
                .andExpect(status().isOk()).andExpect(jsonPath("$.sefer.id").value(id)).andExpect(jsonPath("$.sefer.parcaNo").doesNotExist()).andExpect(jsonPath("$.sefer.varisGar").value("Kars"))
                .andExpect(jsonPath("$.silinenSeferIdler", hasSize(1))).andExpect(jsonPath("$.silinenSeferIdler[0]").value(p2)).andExpect(jsonPath("$.yeniKirmizi", hasSize(0)));
        assertThat(sayi("SELECT count(*) FROM sefer WHERE id = ?", p2)).isZero();

        yonetici().post("/api/atama/sefer/" + p22 + "/birlestir", "{\"versiyon\":" + versiyon(p22) + ",\"onizle\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.sefer.id").value(id2));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'")).as("her başarılı birleştirme tek kayıt").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT kullanici_adi FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR' AND sefer_id = ?", String.class, id)).isEqualTo("Ata Maci");
    }

    @Test
    void uc_hatalar_409govdesindeKod() throws Exception {
        long id = sefer("T1");
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + id + "/birlestir", "{\"versiyon\":" + versiyon(id) + "}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_PARCALI_DEGIL"));
        t.post("/api/atama/sefer/99999/birlestir", "{\"versiyon\":0}").andExpect(status().isNotFound());
        bol(id);
        t.post("/api/atama/sefer/" + id + "/birlestir", "{\"versiyon\":" + (versiyon(id) + 9) + "}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BIRLESTIR'")).isZero();
    }

    @Test
    void acikBirakmaTalebi_birlesinceBAYAT_onay409TalepBayat() throws Exception {
        long id = sefer("T1");
        long m = v.makinist("D001");
        long atama = v.atama(id, m, "ASIL");
        bol(id);
        Tarayici mk = makinistGirisli(m, "D001");
        long talep = ((Number) JsonPath.read(mk.post("/api/benim/takas", "{\"atamaId\":" + atama + ",\"neden\":\"IZIN\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id")).longValue();
        Tarayici atamaci = atamaci();
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("ATAMACI_ONAYI_BEKLIYOR"));

        atamaci.post("/api/atama/sefer/" + id + "/birlestir", "{\"versiyon\":" + versiyon(id) + "}").andExpect(status().isOk());

        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("BAYAT"));
        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE id = ?", Long.class, atama)).as("bayat talep atamayı değiştirmedi").isEqualTo(m);
    }

    @Test
    void uc_yeniKirmizi_jsondaListeOlarakGelir() throws Exception {
        long id = sefer("T1");
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        long diger = v.atama(v.sefer("SIV-SIV", GUN, "17:00", "18:00", sivas, sivas), a, "ASIL");
        bol(id);

        atamaci().post("/api/atama/sefer/" + id + "/birlestir", "{\"versiyon\":" + versiyon(id) + ",\"onizle\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.yeniKirmizi[?(@.atamaId==" + diger + ")].trenNo").value(org.hamcrest.Matchers.hasItem("SIV-SIV")))
                .andExpect(jsonPath("$.yeniKirmizi[?(@.atamaId==" + diger + ")].nedenler[0]").isNotEmpty());
        assertThat(sayi("SELECT count(*) FROM sefer WHERE parca_no IS NOT NULL")).as("önizleme yazmadı").isEqualTo(2);
    }
}
