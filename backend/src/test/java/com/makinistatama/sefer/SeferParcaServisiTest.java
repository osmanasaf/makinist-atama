package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.sefer.SeferBolIstegi.Nokta;

/**
 * Seferi ekrandan bölme çekirdeği (MK-120/121/125, FR-01/02/04/08): atamasız, DB'li. Ankara -> Kars (YHT, hat Ankara-Kars) örnek sefer; her red durumunda
 * sefer tablosu DEĞİŞMEZ. Tek paylaşılan Spring bağlamı ({@link TestVeritabani}).
 */
class SeferParcaServisiTest extends TestVeritabani {

    static final ZoneId IST = ZoneId.of("Europe/Istanbul");
    static final LocalDate GUN12 = LocalDate.of(2026, 10, 12);
    static final LocalDate GUN13 = LocalDate.of(2026, 10, 13);
    /** Testlerdeki sabit "şimdi" (makinist.atama.simdi ile aynı): 12.10 seferleri başlamamış. */
    static final Instant SIMDI = LocalDateTime.of(2026, 10, 10, 10, 0).atZone(IST).toInstant();

    @Autowired
    SeferParcaServisi servis;

    long ankara;
    long sivas;
    long erzincan;
    long kars;
    long hat;
    long yht;

    @BeforeEach
    void tanimlar() {
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        erzincan = TestTanimlari.gar(jdbc, "Erzincan");
        kars = TestTanimlari.gar(jdbc, "Kars");
        hat = TestTanimlari.hat(jdbc, "Ankara–Kars");
        yht = TestTanimlari.aracTipi(jdbc, "YHT");
    }

    // ------------------------------------------------------------------ yardımcılar

    long ekle(String tren, LocalDate tarih, LocalDateTime kalkis, LocalDateTime varis, String km, int kalkisGecikme, int varisGecikme) {
        return jdbc.queryForObject("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km, kalkis_gecikme_dk, varis_gecikme_dk, tren_turu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'YOLCU') RETURNING id""", Long.class, tren, tarih, ankara, kars, Timestamp.from(kalkis.atZone(IST).toInstant()),
                Timestamp.from(varis.atZone(IST).toInstant()), yht, hat, km == null ? null : new BigDecimal(km), kalkisGecikme, varisGecikme);
    }

    /** DE-1503 12.10 Ankara 08:00 -> Kars 20:10, km 1103. */
    long de1503() {
        return ekle("DE-1503", GUN12, GUN12.atTime(8, 0), GUN12.atTime(20, 10), "1103", 0, 0);
    }

    static Nokta nokta(long gar, String varis, String kalkis, String km) {
        return new Nokta(gar, LocalTime.parse(varis), LocalTime.parse(kalkis), km == null ? null : new BigDecimal(km), null, null);
    }

    SeferBolIstegi istek(long id, Nokta... noktalar) {
        return new SeferBolIstegi(jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id), List.of(noktalar), false);
    }

    BolSonucu bol(long id, Nokta... noktalar) {
        return servis.bol(id, istek(id, noktalar), SIMDI);
    }

    /** Satırın özeti: parça no/sayısı, tarih, tren günü, garlar, planlanan saatler (İstanbul), km, gecikmeler. */
    String satir(long id) {
        return jdbc.queryForObject("""
                SELECT coalesce(s.parca_no::text, '-') || '/' || coalesce(s.parca_sayisi::text, '-') || ' tarih=' || s.tarih || ' tg=' || coalesce(s.tren_gunu::text, '-')
                       || ' ' || kg.ad || '>' || vg.ad || ' ' || to_char(s.planlanan_kalkis AT TIME ZONE 'Europe/Istanbul', 'MM-DD HH24:MI')
                       || ' ' || to_char(s.planlanan_varis AT TIME ZONE 'Europe/Istanbul', 'MM-DD HH24:MI') || ' km=' || coalesce(s.km::text, '-')
                       || ' gec=' || s.kalkis_gecikme_dk || '/' || s.varis_gecikme_dk
                FROM sefer s JOIN tanim kg ON kg.id = s.kalkis_gar_id JOIN tanim vg ON vg.id = s.varis_gar_id WHERE s.id = ?""", String.class, id);
    }

    List<Map<String, Object>> tablo() {
        return jdbc.queryForList("SELECT * FROM sefer ORDER BY id");
    }

    /** Hata çıkar ve tablo değişmez. */
    void reddedilir(long id, SeferBolIstegi istek, HttpStatus durum, String kod) {
        List<Map<String, Object>> once = tablo();
        assertThatThrownBy(() -> servis.bol(id, istek, SIMDI)).isInstanceOfSatisfying(UygulamaHatasi.class, e -> {
            assertThat(e.durum()).isEqualTo(durum);
            assertThat(e.kod()).isEqualTo(kod);
        });
        assertThat(tablo()).isEqualTo(once);
    }

    void dogrulamaHatasi(long id, SeferBolIstegi istek, String mesajParcasi) {
        List<Map<String, Object>> once = tablo();
        assertThatThrownBy(() -> servis.bol(id, istek, SIMDI)).isInstanceOfSatisfying(UygulamaHatasi.class, e -> {
            assertThat(e.durum()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.kod()).isEqualTo("DOGRULAMA");
            assertThat(e.getMessage()).contains(mesajParcasi);
        });
        assertThat(tablo()).isEqualTo(once);
    }

    // ------------------------------------------------------------------ FR-01

    @Test
    void ankaraSivasKars_ikiParca_kmFarki_mevcutSatirParca1Olur() {
        long id = de1503();

        BolSonucu s = bol(id, nokta(sivas, "13:30", "13:40", "603"));

        assertThat(s.parca1().id()).isEqualTo(id);
        assertThat(s.parcalar()).hasSize(2);
        assertThat(s.parcalar().get(0).id()).isEqualTo(id);
        assertThat(s.yeniIdler()).hasSize(1).containsExactly(s.parcalar().get(1).id());
        assertThat(s.parcalar()).extracting(p -> p.parcaNo() + "/" + p.parcaSayisi()).containsExactly("1/2", "2/2");
        assertThat(s.parcalar().get(0).versiyon()).isGreaterThan(0);
        assertThat(s.eskiOzet()).contains("DE-1503").contains("Ankara").contains("Kars");
        assertThat(s.yeniOzet()).contains("Sivas");

        assertThat(satir(id)).isEqualTo("1/2 tarih=2026-10-12 tg=2026-10-12 Ankara>Sivas 10-12 08:00 10-12 13:30 km=603.0 gec=0/0");
        long yeni = s.yeniIdler().getFirst();
        assertThat(yeni).isNotEqualTo(id);
        assertThat(satir(yeni)).isEqualTo("2/2 tarih=2026-10-12 tg=2026-10-12 Sivas>Kars 10-12 13:40 10-12 20:10 km=500.0 gec=0/0");
        Map<String, Object> p2 = jdbc.queryForMap("SELECT tren_no, tren_turu, arac_tipi_id, hat_id, durum FROM sefer WHERE id = ?", yeni);
        assertThat(p2).containsEntry("tren_no", "DE-1503").containsEntry("tren_turu", "YOLCU").containsEntry("arac_tipi_id", yht).containsEntry("hat_id", hat)
                .containsEntry("durum", "PLANLI");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).isEqualTo(2);
    }

    @Test
    void ucParca_ikiNokta() {
        long id = de1503();

        BolSonucu s = bol(id, nokta(sivas, "13:30", "13:40", "603"), nokta(erzincan, "16:00", "16:10", "800"));

        assertThat(s.parcalar()).hasSize(3);
        assertThat(s.yeniIdler()).hasSize(2);
        assertThat(satir(id)).isEqualTo("1/3 tarih=2026-10-12 tg=2026-10-12 Ankara>Sivas 10-12 08:00 10-12 13:30 km=603.0 gec=0/0");
        assertThat(satir(s.yeniIdler().get(0))).isEqualTo("2/3 tarih=2026-10-12 tg=2026-10-12 Sivas>Erzincan 10-12 13:40 10-12 16:00 km=197.0 gec=0/0");
        assertThat(satir(s.yeniIdler().get(1))).isEqualTo("3/3 tarih=2026-10-12 tg=2026-10-12 Erzincan>Kars 10-12 16:10 10-12 20:10 km=303.0 gec=0/0");
    }

    @Test
    void hatVeAracTipiNoktadaDoluysaOBaslangictaKullanilir() {
        long id = de1503();
        long hat2 = TestTanimlari.hat(jdbc, "Sivas–Kars");
        long dm = TestTanimlari.aracTipi(jdbc, "DM");

        BolSonucu s = bol(id, new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), hat2, dm));

        assertThat(jdbc.queryForMap("SELECT arac_tipi_id, hat_id FROM sefer WHERE id = ?", id)).containsEntry("arac_tipi_id", yht).containsEntry("hat_id", hat);
        assertThat(jdbc.queryForMap("SELECT arac_tipi_id, hat_id FROM sefer WHERE id = ?", s.yeniIdler().getFirst())).containsEntry("arac_tipi_id", dm).containsEntry("hat_id", hat2);
    }

    @Test
    void kmYoksa_parcalardaDaNull() {
        long id = ekle("DE-1503", GUN12, GUN12.atTime(8, 0), GUN12.atTime(20, 10), null, 0, 0);

        BolSonucu s = bol(id, nokta(sivas, "13:30", "13:40", null));

        assertThat(satir(id)).contains("km=-");
        assertThat(satir(s.yeniIdler().getFirst())).contains("km=-");
    }

    // ------------------------------------------------------------------ FR-08 gece aşan

    @Test
    void geceAsan_parca2ErtesiGunTarihli_trenGunuAyni() {
        long id = ekle("DE-1601", GUN12, GUN12.atTime(20, 0), GUN13.atTime(9, 10), "1103", 0, 0);

        BolSonucu s = bol(id, nokta(sivas, "02:30", "02:40", "603"));

        assertThat(satir(id)).isEqualTo("1/2 tarih=2026-10-12 tg=2026-10-12 Ankara>Sivas 10-12 20:00 10-13 02:30 km=603.0 gec=0/0");
        assertThat(satir(s.yeniIdler().getFirst())).isEqualTo("2/2 tarih=2026-10-13 tg=2026-10-12 Sivas>Kars 10-13 02:40 10-13 09:10 km=500.0 gec=0/0");
    }

    @Test
    void geceAsan_ertesiGununKendiBolunmusTreniyleCakisir_sefertekrari() {
        long id = ekle("DE-1601", GUN12, GUN12.atTime(20, 0), GUN13.atTime(9, 10), "1103", 0, 0);
        // 13.10'un KENDİ DE-1601'i zaten bölünmüş: (13.10, DE-1601, parça 2) dolu
        jdbc.update("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km, parca_no, parca_sayisi, tren_gunu)
                VALUES ('DE-1601', ?, ?, ?, ?, ?, ?, ?, 600, 1, 2, ?), ('DE-1601', ?, ?, ?, ?, ?, ?, ?, 503, 2, 2, ?)""", GUN13, ankara, sivas,
                Timestamp.from(GUN13.atTime(5, 0).atZone(IST).toInstant()), Timestamp.from(GUN13.atTime(8, 0).atZone(IST).toInstant()), yht, hat, GUN13, GUN13, sivas, kars,
                Timestamp.from(GUN13.atTime(8, 10).atZone(IST).toInstant()), Timestamp.from(GUN13.atTime(12, 0).atZone(IST).toInstant()), yht, hat, GUN13);

        reddedilir(id, istek(id, nokta(sivas, "02:30", "02:40", "603")), HttpStatus.CONFLICT, "SEFER_TEKRARI");
    }

    // ------------------------------------------------------------------ FR-04 gecikme dağıtımı

    @Test
    void gecikme20_30_parca1Ve2_20_20Ve20_30() {
        long id = ekle("DE-1503", GUN12, GUN12.atTime(8, 0), GUN12.atTime(20, 10), "1103", 20, 30);

        BolSonucu s = bol(id, nokta(sivas, "13:30", "13:40", "603"));

        assertThat(satir(id)).endsWith("gec=20/20");
        assertThat(satir(s.yeniIdler().getFirst())).endsWith("gec=20/30");
        assertThat(s.parcalar().get(0).guncelKalkis().toLocalTime()).isEqualTo(LocalTime.of(8, 20));
        assertThat(s.parcalar().get(0).guncelVaris().toLocalTime()).isEqualTo(LocalTime.of(13, 50));
        assertThat(s.parcalar().get(1).guncelKalkis().toLocalTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(s.parcalar().get(1).guncelVaris().toLocalTime()).isEqualTo(LocalTime.of(20, 40));
    }

    @Test
    void gecikme_araParcalarKalkisVeVarisKalkisGecikmesi() {
        long id = ekle("DE-1503", GUN12, GUN12.atTime(8, 0), GUN12.atTime(20, 10), "1103", 20, 30);

        BolSonucu s = bol(id, nokta(sivas, "13:30", "13:40", "603"), nokta(erzincan, "16:00", "16:10", "800"));

        assertThat(satir(id)).endsWith("gec=20/20");
        assertThat(satir(s.yeniIdler().get(0))).endsWith("gec=20/20");
        assertThat(satir(s.yeniIdler().get(1))).endsWith("gec=20/30");
    }

    @Test
    void gecikmeYok_hepsi0_0() {
        long id = de1503();

        BolSonucu s = bol(id, nokta(sivas, "13:30", "13:40", "603"));

        assertThat(satir(id)).endsWith("gec=0/0");
        assertThat(satir(s.yeniIdler().getFirst())).endsWith("gec=0/0");
    }

    @Test
    void gecikme_sonParcaninGuncelSuresiBozulursa_400_hicbirSeyYazilmaz() {
        // sefer geçerli (12 s 10 dk - 500 dk > 0) ama 13:40-20:10 son parçası (390 dk) 500 dk kalkış gecikmesi + 0 varış gecikmesiyle bozulur
        long id = ekle("DE-1503", GUN12, GUN12.atTime(8, 0), GUN12.atTime(20, 10), "1103", 500, 0);

        dogrulamaHatasi(id, istek(id, nokta(sivas, "13:30", "13:40", "603")), "gecikme");
    }

    // ------------------------------------------------------------------ FR-02 doğrulamalar (400)

    @Test
    void pencereDisi_ertesiGunSaatiSeferAraliginiAsar_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(sivas, "07:00", "07:10", "603")), "zaman aralığında");
    }

    @Test
    void sonKalkisSefereVarisindanSonra_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(sivas, "20:00", "20:20", "603")), "zaman aralığında");
    }

    @Test
    void kalkisVarisdanOnce_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(sivas, "13:30", "13:20", "603")), "Kalkış saati varış saatinden önce olamaz");
    }

    @Test
    void garTipiYanlisYaDaYok_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(hat, "13:30", "13:40", "603")), "Seçilen kayıt bulunamadı");
        dogrulamaHatasi(id, istek(id, nokta(987654, "13:30", "13:40", "603")), "Seçilen kayıt bulunamadı");
    }

    @Test
    void hatVeAracTipiTipiYanlis_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), yht, null)), "Seçilen kayıt bulunamadı");
        dogrulamaHatasi(id, istek(id, new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), null, hat)), "Seçilen kayıt bulunamadı");
    }

    @Test
    void garNoktalarArasiYinelenir_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(sivas, "11:00", "11:10", "400"), nokta(sivas, "16:00", "16:10", "800")), "yinelen");
    }

    @Test
    void garSefereninUcGariyleAyni_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(ankara, "13:30", "13:40", "603")), "farklı olmalı");
        dogrulamaHatasi(id, istek(id, nokta(kars, "13:30", "13:40", "603")), "farklı olmalı");
    }

    @Test
    void besNoktadanFazla_400() {
        long id = de1503();
        List<Nokta> altiNokta = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            altiNokta.add(nokta(sivas + 100 + i, String.format("%02d:00", 9 + i), String.format("%02d:10", 9 + i), String.valueOf(100 * (i + 1))));
        }
        SeferBolIstegi altiliIstek = new SeferBolIstegi(0, altiNokta, false);

        dogrulamaHatasi(id, altiliIstek, "en çok");
    }

    @Test
    void notasizIstek_400() {
        long id = de1503();

        dogrulamaHatasi(id, new SeferBolIstegi(0, List.of(), false), "ara istasyon");
    }

    @Test
    void seferKmDoluyken_noktaKmEksik_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(sivas, "13:30", "13:40", null)), "KM");
    }

    @Test
    void seferKmBosken_noktaKmGirilemez_400() {
        long id = ekle("DE-1503", GUN12, GUN12.atTime(8, 0), GUN12.atTime(20, 10), null, 0, 0);

        dogrulamaHatasi(id, istek(id, nokta(sivas, "13:30", "13:40", "603")), "KM");
    }

    @Test
    void sonParcaKmNegatif_400() {
        long id = de1503();

        dogrulamaHatasi(id, istek(id, nokta(sivas, "13:30", "13:40", "1200")), "negatif");
    }

    // ------------------------------------------------------------------ önkoşullar (404 / 409 / 422) ve sırası

    @Test
    void seferYok_404() {
        assertThatThrownBy(() -> servis.bol(999_999, new SeferBolIstegi(0, List.of(nokta(sivas, "13:30", "13:40", "603")), false), SIMDI))
                .isInstanceOfSatisfying(UygulamaHatasi.class, e -> assertThat(e.durum()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void versiyonCakismasi_409() {
        long id = de1503();
        SeferBolIstegi eski = new SeferBolIstegi(jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id) + 1,
                List.of(nokta(sivas, "13:30", "13:40", "603")), false);

        reddedilir(id, eski, HttpStatus.CONFLICT, "VERSIYON_CAKISMASI");
    }

    @Test
    void seferIptal_409() {
        long id = de1503();
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", id);

        reddedilir(id, istek(id, nokta(sivas, "13:30", "13:40", "603")), HttpStatus.CONFLICT, "SEFER_IPTAL");
    }

    @Test
    void seferZatenParcali_409() {
        long id = de1503();
        jdbc.update("UPDATE sefer SET parca_no = 1, parca_sayisi = 2, tren_gunu = tarih WHERE id = ?", id);

        reddedilir(id, istek(id, nokta(sivas, "13:30", "13:40", "603")), HttpStatus.CONFLICT, "SEFER_ZATEN_PARCALI");
    }

    @Test
    void seferBaslamis_422() {
        long id = ekle("DE-0901", LocalDate.of(2026, 10, 10), LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 15, 0), "600", 0, 0);

        reddedilir(id, istek(id, nokta(sivas, "11:00", "11:10", "300")), HttpStatus.UNPROCESSABLE_ENTITY, "SEFER_BASLAMIS");
    }

    @Test
    void baslamaOlcutuGuncelKalkistir_gecikmeliSeferHenuzBaslamamisSayilir() {
        // planlanan 09:00 (şimdi 10:00'dan önce) ama 120 dk gecikmeyle güncel kalkış 11:00: bölünebilir
        long id = ekle("DE-0901", LocalDate.of(2026, 10, 10), LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 15, 0), "600", 120, 120);

        BolSonucu s = bol(id, nokta(sivas, "11:00", "11:10", "300"));

        assertThat(s.parcalar()).hasSize(2);
    }

    @Test
    void onkosulSirasi_versiyon_iptal_parcali_baslamis_dogrulamadanOnce() {
        long id = ekle("DE-0901", LocalDate.of(2026, 10, 10), LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 15, 0), "600", 0, 0);
        jdbc.update("UPDATE sefer SET durum = 'IPTAL', parca_no = 1, parca_sayisi = 2, tren_gunu = tarih WHERE id = ?", id);
        Nokta bozuk = nokta(hat, "01:00", "00:50", null); // doğrulama olsaydı 400
        long v = jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id);

        reddedilir(id, new SeferBolIstegi(v + 1, List.of(bozuk), false), HttpStatus.CONFLICT, "VERSIYON_CAKISMASI");
        reddedilir(id, new SeferBolIstegi(v, List.of(bozuk), false), HttpStatus.CONFLICT, "SEFER_IPTAL");
        jdbc.update("UPDATE sefer SET durum = 'PLANLI' WHERE id = ?", id);
        reddedilir(id, new SeferBolIstegi(v, List.of(bozuk), false), HttpStatus.CONFLICT, "SEFER_ZATEN_PARCALI");
        jdbc.update("UPDATE sefer SET parca_no = NULL, parca_sayisi = NULL, tren_gunu = NULL WHERE id = ?", id);
        reddedilir(id, new SeferBolIstegi(v, List.of(bozuk), false), HttpStatus.UNPROCESSABLE_ENTITY, "SEFER_BASLAMIS");
    }
}
