package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

/** F2c: Excel "Değişim Noktaları" ile parçalı yükleme ve R1 (gece aşan trende mevcut-kayıt anahtarı, MK-60/61). */
class SeferYuklemeParcaTest extends TestVeritabani {

    @BeforeEach
    void tanimlar() {
        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.gar(jdbc, "Sivas");
        TestTanimlari.gar(jdbc, "Kars");
        TestTanimlari.hat(jdbc, "Ankara–Kars");
        TestTanimlari.aracTipi(jdbc, "YHT");
    }

    static String[] ana(String tarih, String tren, String kalkis, String varis, String km) {
        return new String[] { tarih, tren, "Ankara", kalkis, "Kars", varis, "YHT", "Ankara–Kars", km };
    }

    /** DE-1601 Ankara 20:00 → Kars 09:10 (ertesi gün); değişim noktası Sivas 02:30 | 02:40. */
    static byte[] gunAsanDosya(String... tarihler) {
        List<String[]> ana = Arrays.stream(tarihler).map(t -> ana(t, "DE-1601", "20:00", "09:10", "1103")).toList();
        return TestExcel.sefer(TestExcel.BASLIK, ana, List.<String[]>of(new String[] { "DE-1601", "1", "Sivas", "02:30", "02:40", "603" }));
    }

    /** DE-1503 Ankara 08:00 → Sivas 13:30 / 13:40 → Kars 20:10. */
    static byte[] ankaraKars() {
        return TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")),
                List.<String[]>of(new String[] { "DE-1503", "1", "Sivas", "13:30", "13:40", "603" }));
    }

    ResultActions yukle(Tarayici t, byte[] dosya, boolean onizle) throws Exception {
        return t.istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle")
                .file(new MockMultipartFile("dosya", "seferler.xlsx", "application/octet-stream", dosya)).param("onizle", Boolean.toString(onizle)));
    }

    int sayi() {
        return jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class);
    }

    @Test
    void ankaraSivasKars_ikiParcaSatiri_kmKumulatif_onizlemeYazmaz() throws Exception {
        Tarayici t = atamaci();

        yukle(t, ankaraKars(), true).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.hataSayisi").value(0))
                .andExpect(jsonPath("$.satirlar[0].parcaNo").value(1)).andExpect(jsonPath("$.satirlar[0].parcaSayisi").value(2))
                .andExpect(jsonPath("$.satirlar[0].kalkisGar").value("Ankara")).andExpect(jsonPath("$.satirlar[0].varisGar").value("Sivas"))
                .andExpect(jsonPath("$.satirlar[1].parcaNo").value(2)).andExpect(jsonPath("$.satirlar[1].kalkisGar").value("Sivas"))
                .andExpect(jsonPath("$.satirlar[1].varisGar").value("Kars")).andExpect(jsonPath("$.satirlar[1].durum").value("YENI"));
        assertThat(sayi()).isZero();

        yukle(t, ankaraKars(), false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2));
        assertThat(jdbc.queryForList("SELECT parca_no, tren_gunu::text AS gun, km, to_char(planlanan_kalkis AT TIME ZONE 'Europe/Istanbul', 'HH24:MI') AS k, "
                + "to_char(planlanan_varis AT TIME ZONE 'Europe/Istanbul', 'HH24:MI') AS v FROM sefer ORDER BY parca_no"))
                .extracting(m -> m.get("parca_no") + " " + m.get("gun") + " " + m.get("km") + " " + m.get("k") + "-" + m.get("v"))
                .containsExactly("1 2026-10-12 603.0 08:00-13:30", "2 2026-10-12 500.0 13:40-20:10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_YUKLE'", Integer.class)).isEqualTo(1);
    }

    /** R1 [MK-60]: parça 2 ertesi güne taşar; ertesi günün kendi DE-1601 ana satırı ZATEN_VAR sayılmamalı. */
    @Test
    void r1_gunAsanTren_ertesiGununAyniTreni_YENI_sonraZatenVar() throws Exception {
        Tarayici t = atamaci();

        yukle(t, gunAsanDosya("12.10.2026"), false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2));
        assertThat(jdbc.queryForObject("SELECT tarih::text FROM sefer WHERE parca_no = 2", String.class)).isEqualTo("2026-10-13");

        yukle(t, gunAsanDosya("13.10.2026"), false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.zatenVar").value(0))
                .andExpect(jsonPath("$.satirlar[0].durum").value("YENI")).andExpect(jsonPath("$.satirlar[1].durum").value("YENI"));
        assertThat(sayi()).isEqualTo(4);

        yukle(t, gunAsanDosya("13.10.2026"), false).andExpect(jsonPath("$.eklenen").value(0)).andExpect(jsonPath("$.zatenVar").value(2))
                .andExpect(jsonPath("$.satirlar[0].durum").value("ZATEN_VAR")).andExpect(jsonPath("$.satirlar[1].durum").value("ZATEN_VAR"));
        yukle(t, gunAsanDosya("12.10.2026"), false).andExpect(jsonPath("$.eklenen").value(0)).andExpect(jsonPath("$.zatenVar").value(2));
        assertThat(sayi()).isEqualTo(4);
    }

    @Test
    void ardisikIkiGunAyniDosyada_dortParca_dosyaIciTekrarDegil() throws Exception {
        yukle(atamaci(), gunAsanDosya("12.10.2026", "13.10.2026"), false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(4))
                .andExpect(jsonPath("$.hataSayisi").value(0));

        assertThat(jdbc.queryForList("SELECT tren_gunu::text AS g, parca_no FROM sefer ORDER BY tren_gunu, parca_no")).extracting(m -> m.get("g") + "/" + m.get("parca_no"))
                .containsExactly("2026-10-12/1", "2026-10-12/2", "2026-10-13/1", "2026-10-13/2");
    }

    @Test
    void ayniTrenDosyadaIkiKez_ikinciHata_digerParcalarAyniDurumu() throws Exception {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK,
                List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"), ana("12.10.2026", "DE-1503", "09:00", "21:10", "1103")),
                List.<String[]>of(new String[] { "DE-1503", "1", "Sivas", "13:30", "13:40", "603" }));

        yukle(atamaci(), d, false).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.hataSayisi").value(1)).andExpect(jsonPath("$.hatalar[0].satirNo").value(3));
    }

    @Test
    void mevcutParcasizTren_parcaliDosyayla_ezilmez_hepsiZatenVar() throws Exception {
        Tarayici t = atamaci();
        yukle(t, TestExcel.sefer(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"))), false).andExpect(jsonPath("$.eklenen").value(1));

        yukle(t, ankaraKars(), false).andExpect(jsonPath("$.eklenen").value(0)).andExpect(jsonPath("$.zatenVar").value(2));
        assertThat(sayi()).isEqualTo(1);
    }

    @Test
    void degisimNoktasiHatasi_trenParcasizYuklenmez() throws Exception {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK,
                List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"), ana("12.10.2026", "T9", "08:00", "09:00", "10")),
                List.<String[]>of(new String[] { "DE-1503", "2", "Sivas", "13:30", "13:40", "603" })); // sıra 1 yok

        yukle(atamaci(), d, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(1)).andExpect(jsonPath("$.hataSayisi").value(greaterThan(0)))
                .andExpect(jsonPath("$.hatalar[*].neden").value(hasItem(containsString("Değişim Noktaları satır 2"))));
        assertThat(sayi()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tren_no FROM sefer", String.class)).isEqualTo("T9");
    }

    @Test
    void trenNoYazimFarki_parcaliYuklenir_seferlerYazimiyla() throws Exception {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")),
                List.<String[]>of(new String[] { "de-1503 ", "1", "Sivas", "13:30", "13:40", "603" }));

        yukle(atamaci(), d, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.hataSayisi").value(0))
                .andExpect(jsonPath("$.engelli").value(false));
        assertThat(jdbc.queryForList("SELECT tren_no || ' ' || parca_no FROM sefer ORDER BY parca_no", String.class)).containsExactly("DE-1503 1", "DE-1503 2");
    }

    /** W: karşılığı Seferler'de olmayan nokta satırı engelleyici; önizleme engelli + sayfa/tren no bildirir, onay hiçbir şey yazmaz. */
    @Test
    void karsiligiOlmayanNoktaSatiri_onizlemeEngelli_onayReddedilir_hicbirSeyYazilmaz() throws Exception {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK,
                List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"), ana("12.10.2026", "T9", "08:00", "09:00", "10")),
                List.<String[]>of(new String[] { "DE-1530", "1", "Sivas", "13:30", "13:40", "603" })); // yazım hatası: DE-1530
        Tarayici t = atamaci();

        yukle(t, d, true).andExpect(status().isOk()).andExpect(jsonPath("$.engelli").value(true)).andExpect(jsonPath("$.hatalar[0].sayfa").value("Değişim Noktaları"))
                .andExpect(jsonPath("$.hatalar[0].trenNo").value("DE-1530")).andExpect(jsonPath("$.hatalar[0].satirNo").value(2));
        yukle(t, d, false).andExpect(status().isBadRequest());
        assertThat(sayi()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_YUKLE'", Integer.class)).isZero();
    }
    /** Parça süre sınırı ayardan (varsayılan 16 s) okunur: 23 s 30 dk'lık parça onayı engeller; ayar yükseltilince aynı dosya yüklenir. */
    @Test
    void parcaSuresiAyardan_varsayilanUstuEngeller_ayarYukselinceYuklenir() throws Exception {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")),
                List.<String[]>of(new String[] { "DE-1503", "1", "Sivas", "07:30", "13:40", "603" })); // 1. parça 23 s 30 dk
        Tarayici t = atamaci();
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'sefer.parca_azami_saat'", Integer.class)).isEqualTo(16);

        yukle(t, d, true).andExpect(jsonPath("$.engelli").value(true)).andExpect(jsonPath("$.hatalar[0].neden").value(containsString("sefer.parca_azami_saat")));
        yukle(t, d, false).andExpect(status().isBadRequest());
        assertThat(sayi()).isZero();

        jdbc.update("UPDATE uygulama_ayar SET deger = 24 WHERE anahtar = 'sefer.parca_azami_saat'");
        yukle(t, d, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.engelli").value(false));
    }
}
