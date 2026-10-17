package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.sefer.SeferBolIstegi;
import com.makinistatama.sefer.SeferBolIstegi.Nokta;

/**
 * Seferi bölme orkestratörü (T3.3/T3.4; MK-121..128), doğrudan servis çağrısı, tek paylaşılan bağlam: FR-03 (atamalar parça 1'de), FR-07 (yeni kırmızı), FR-11 (tek denetim kaydı),
 * önizleme (hiçbir tablo değişmez). Örnek: DE-1503 12.10 Ankara 08:00 -> Kars 20:10 (km 1103), Sivas 13:30/13:40, ara km 603.
 */
class SeferParcalamaServisiTest extends TestVeritabani {

    static final LocalDate GUN12 = LocalDate.of(2026, 10, 12);
    static final LocalDate GUN13 = LocalDate.of(2026, 10, 13);

    @Autowired
    SeferParcalamaServisi servis;

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

    /** Varış 20:10 = FR-01 örneği (12 s 10 dk, gunluk.max_saat 11 üstünde: kendi başına kırmızı); kırmızı testlerinde 18:10 (10 s 10 dk). */
    long de1503() {
        return de1503("20:10");
    }

    long de1503(String varis) {
        long id = v.sefer("DE-1503", GUN12, "08:00", varis, v.ankara, kars);
        jdbc.update("UPDATE sefer SET km = 1103 WHERE id = ?", id);
        return id;
    }

    Nokta sivasNoktasi() {
        return new Nokta(sivas, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), null, null);
    }

    SeferBolIstegi istek(long id, boolean onizle) {
        return new SeferBolIstegi(jdbc.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, id), List.of(sivasNoktasi()), onizle);
    }

    /** "Kırmızı" = ENGEL düzeyi ihlal; K-20 (konum) varsayılan UYARI olduğundan FR-07 senaryosunda ENGEL yapılır (parametre testler arasında tohuma döner). */
    void k20Engel() throws Exception {
        yonetici().put("/api/kural/K-20/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"deneme\"}").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    int sayi(String sql, Object... arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    List<Map<String, Object>> atamaTablosu() {
        return jdbc.queryForList("SELECT * FROM atama ORDER BY id");
    }

    /** Atamanın bağlı olduğu seferin planlanan süresi (dk). */
    int gorevDakika(long atamaId) {
        return jdbc.queryForObject("SELECT (extract(epoch FROM s.planlanan_varis - s.planlanan_kalkis) / 60)::int FROM atama a JOIN sefer s ON s.id = a.sefer_id WHERE a.id = ?", Integer.class, atamaId);
    }

    // ------------------------------------------------------------------ FR-03

    @Test
    void bol_atamalarParca1deKalir_degismez_parca2Bos_gorevAraligiKisalir() {
        long id = de1503();
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 20, varis_gecikme_dk = 30 WHERE id = ?", id);
        long a = v.makinist("D001");
        long c = v.makinist("D002");
        long ataA = v.atama(id, a, "ASIL"); // kalkis_gecikme_dk = 20 (atama anındaki gecikme, OK-6)
        long ataC = v.atama(id, c, "YARDIMCI");
        jdbc.update("UPDATE atama SET atayan = 'Ata Maci', gerekce = 'gerekçe' WHERE id = ?", ataA);
        List<Map<String, Object>> once = atamaTablosu();
        assertThat(gorevDakika(ataA)).isEqualTo(12 * 60 + 10);

        BolSonucuDto s = servis.bol(id, istek(id, false));

        assertThat(atamaTablosu()).as("id, makinist, rol, atayan, gerekçe, kalkis_gecikme_dk: hiçbiri değişmez").isEqualTo(once);
        assertThat(jdbc.queryForObject("SELECT kalkis_gecikme_dk FROM atama WHERE id = ?", Integer.class, ataA)).isEqualTo(20);
        assertThat(s.parcalar()).hasSize(2);
        assertThat(s.parcalar().get(0).id()).as("mevcut satır parça 1").isEqualTo(id);
        assertThat(s.parcalar().get(0).parcaNo()).isEqualTo(1);
        assertThat(s.parcalar().get(1).parcaNo()).isEqualTo(2);
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", s.parcalar().get(1).id())).as("parça 2 BOŞ").isZero();
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", id)).isEqualTo(2);
        assertThat(gorevDakika(ataA)).as("A'nın görevi 08:00-13:30").isEqualTo(5 * 60 + 30);
        assertThat(gorevDakika(ataC)).isEqualTo(5 * 60 + 30);
        assertThat(s.parcalar().get(0).km()).isEqualByComparingTo("603");
        assertThat(s.parcalar().get(1).km()).isEqualByComparingTo("500");
        assertThat(s.yeniKirmizi()).isNotNull().isEmpty();
    }

    @Test
    void bol_atamasizSefer_calisir_tekKayit() {
        long id = de1503();

        BolSonucuDto s = servis.bol(id, istek(id, false));

        assertThat(s.parcalar()).hasSize(2);
        assertThat(s.yeniKirmizi()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL'", String.class)).contains("\"atamadaKalanMakinist\":0");
    }

    // ------------------------------------------------------------------ FR-07

    /** A: DE-1503 sonrası 13.10'da Kars'tan kalkan görev (önce uygun: A Kars'ta). Bölününce A Sivas'ta biter: "Konum uyuşmazlığı". C'nin sonraki görevi Eskişehir'den (zaten kırmızı): listelenmez. */
    @Test
    void bol_sonrakiGorevKonumUyusmazligiOlur_yeniKirmiziListelenir_zatenKirmiziListelenmez() throws Exception {
        k20Engel();
        long id = de1503("18:10");
        long a = v.makinist("D001");
        long c = v.makinist("D002");
        v.atama(id, a, "ASIL");
        v.atama(id, c, "YARDIMCI");
        long sonrakiA = v.atama(v.sefer("KARS-ANK", GUN13, "10:00", "12:00", kars, kars), a, "ASIL");
        v.atama(v.sefer("ESK-ESK", GUN13, "10:00", "12:00", v.eskisehir, v.eskisehir), c, "ASIL"); // Kars ≠ Eskişehir: bölmeden ÖNCE de kırmızı
        assertThat(sayi("SELECT count(*) FROM atama")).isEqualTo(4);

        BolSonucuDto s = servis.bol(id, istek(id, false));

        assertThat(s.yeniKirmizi()).hasSize(1);
        assertThat(s.yeniKirmizi().getFirst().atamaId()).isEqualTo(sonrakiA);
        assertThat(s.yeniKirmizi().getFirst().trenNo()).isEqualTo("KARS-ANK");
        assertThat(s.yeniKirmizi().getFirst().nedenler()).anySatisfy(n -> assertThat(n).contains("Konum uyuşmazlığı").contains("Sivas"));
    }

    // ------------------------------------------------------------------ FR-11

    @Test
    void bol_basarili_tekSeferBolKaydi_ayrintiVeOzetler() {
        long id = de1503();
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        v.atama(id, v.makinist("D002"), "YARDIMCI");

        BolSonucuDto s = servis.bol(id, istek(id, false));

        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).as("yalnız tek kayıt (atama/gecikme kaydı yok)").isEqualTo(1);
        Map<String, Object> k = jdbc.queryForMap("SELECT tur, varlik_tipi, varlik_id, sefer_id, makinist_id, eski, yeni, ayrinti FROM degisiklik_kaydi");
        assertThat(k.get("tur")).isEqualTo("SEFER_BOL");
        assertThat(k.get("varlik_tipi")).isEqualTo("SEFER");
        assertThat(k.get("varlik_id")).isEqualTo(id);
        assertThat(k.get("sefer_id")).isEqualTo(id);
        assertThat(k.get("makinist_id")).isNull();
        assertThat((String) k.get("eski")).contains("DE-1503").contains("Ankara").contains("Kars");
        assertThat((String) k.get("yeni")).startsWith("1/2").contains("Sivas");
        String ayrinti = (String) k.get("ayrinti");
        assertThat(ayrinti).contains("\"yeniIdler\":[" + s.parcalar().get(1).id() + "]").contains("\"garId\":" + sivas).contains("\"varisSaat\":\"13:30\"").contains("\"kalkisSaat\":\"13:40\"")
                .contains("\"km\":\"603\"").contains("\"atamadaKalanMakinist\":2").contains("\"kalkisGecikmeDk\":0");
    }

    @Test
    void bol_basarisiz_sifirKayit_tablolarDegismez() {
        long id = de1503();
        v.atama(id, v.makinist("D001"), "ASIL");
        List<Map<String, Object>> seferOnce = jdbc.queryForList("SELECT * FROM sefer ORDER BY id");
        List<Map<String, Object>> atamaOnce = atamaTablosu();
        SeferBolIstegi bayat = new SeferBolIstegi(99, List.of(sivasNoktasi()), false);

        assertThatThrownBy(() -> servis.bol(id, bayat)).isInstanceOfSatisfying(UygulamaHatasi.class, e -> {
            assertThat(e.durum()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.kod()).isEqualTo("VERSIYON_CAKISMASI");
        });
        assertThatThrownBy(() -> servis.bol(9999, istek(id, false))).isInstanceOfSatisfying(UygulamaHatasi.class, e -> assertThat(e.durum()).isEqualTo(HttpStatus.NOT_FOUND));
        SeferBolIstegi hataliGar = new SeferBolIstegi(0, List.of(new Nokta(v.ankara, LocalTime.of(13, 30), LocalTime.of(13, 40), new BigDecimal("603"), null, null)), false);
        assertThatThrownBy(() -> servis.bol(id, hataliGar)).isInstanceOfSatisfying(UygulamaHatasi.class, e -> assertThat(e.durum()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM sefer ORDER BY id")).isEqualTo(seferOnce);
        assertThat(atamaTablosu()).isEqualTo(atamaOnce);
    }

    // ------------------------------------------------------------------ önizleme

    @Test
    void onizle_sonucGercegeAyni_amaSeferAtamaVeKayitTablolariDegismez() throws Exception {
        k20Engel();
        long id = de1503("18:10");
        long a = v.makinist("D001");
        v.atama(id, a, "ASIL");
        v.atama(v.sefer("KARS-ANK", GUN13, "10:00", "12:00", kars, kars), a, "ASIL");
        int sefer = sayi("SELECT count(*) FROM sefer");
        int atama = sayi("SELECT count(*) FROM atama");
        int kayit = sayi("SELECT count(*) FROM degisiklik_kaydi"); // K-20 şiddet değişikliği kaydı dahil
        List<Map<String, Object>> seferOnce = jdbc.queryForList("SELECT * FROM sefer ORDER BY id");
        List<Map<String, Object>> atamaOnce = atamaTablosu();

        BolSonucuDto onizleme = servis.bol(id, istek(id, true));

        assertThat(sayi("SELECT count(*) FROM sefer")).isEqualTo(sefer);
        assertThat(sayi("SELECT count(*) FROM atama")).isEqualTo(atama);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).as("MANDATORY kayıt da geri gitti").isEqualTo(kayit);
        assertThat(jdbc.queryForList("SELECT * FROM sefer ORDER BY id")).as("parça 1 satırı da özgün (versiyon dahil)").isEqualTo(seferOnce);
        assertThat(atamaTablosu()).isEqualTo(atamaOnce);
        assertThat(onizleme.parcalar()).hasSize(2);
        assertThat(onizleme.yeniKirmizi()).hasSize(1);

        BolSonucuDto gercek = servis.bol(id, istek(id, false));

        assertThat(gercek.yeniKirmizi()).isEqualTo(onizleme.yeniKirmizi());
        assertThat(gercek.parcalar()).hasSameSizeAs(onizleme.parcalar());
        for (int i = 0; i < gercek.parcalar().size(); i++) {
            var g = gercek.parcalar().get(i);
            var o = onizleme.parcalar().get(i);
            assertThat(g).as("parça " + (i + 1) + " (kimlik/sürüm hariç aynı)").usingRecursiveComparison().ignoringFields("id", "versiyon").isEqualTo(o);
        }
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_BOL'")).isEqualTo(1);
    }
}
