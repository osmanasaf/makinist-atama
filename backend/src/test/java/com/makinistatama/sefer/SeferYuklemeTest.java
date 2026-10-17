package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;

class SeferYuklemeTest extends TestVeritabani {

    @BeforeEach
    void tanimlar() {
        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.gar(jdbc, "Eskişehir");
        TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        TestTanimlari.aracTipi(jdbc, "YHT");
    }

    static byte[] yuzYirmiSatir() {
        List<String[]> satirlar = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            satirlar.add(new String[] { "12.10.2026", "T" + i, "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245" });
        }
        satirlar.set(4, new String[] { "12.10.2026", "T5", "Ankara", "08:00", "Eskisehr", "09:30", "YHT", "Ankara–Eskişehir", "245" });
        satirlar.set(49, new String[] { "12.10.2026", "T50", "Ankara", "25:70", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245" });
        satirlar.set(99, new String[] { "12.10.2026", "T100", "Ankara", "08:00", "Eskişehir", "09:30", "", "Ankara–Eskişehir", "245" });
        return TestExcel.sefer(satirlar);
    }

    ResultActions yukle(Tarayici t, byte[] dosya, boolean onizle) throws Exception {
        return t.istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle")
                .file(new MockMultipartFile("dosya", "seferler.xlsx", "application/octet-stream", dosya)).param("onizle", Boolean.toString(onizle)));
    }

    int sayi() {
        return jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class);
    }

    @Test
    void onizleme_117Gecerli_3Hata_dbyeYazmaz() throws Exception {
        Tarayici t = atamaci();

        yukle(t, yuzYirmiSatir(), true).andExpect(status().isOk())
                .andExpect(jsonPath("$.onizleme").value(true))
                .andExpect(jsonPath("$.eklenen").value(117))
                .andExpect(jsonPath("$.zatenVar").value(0))
                .andExpect(jsonPath("$.hataSayisi").value(3))
                .andExpect(jsonPath("$.hatalar[0].satirNo").value(6))
                .andExpect(jsonPath("$.hatalar[1].neden").value(containsString("25:70")))
                .andExpect(jsonPath("$.satirlar[0].kalkisGar").value("Ankara"))
                .andExpect(jsonPath("$.satirlar[0].durum").value("YENI"));
        assertThat(sayi()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    @Test
    void onay_117Eklenir_ayniDosyaIkinciKez_0Eklenir_117ZatenVar() throws Exception {
        Tarayici t = atamaci();

        yukle(t, yuzYirmiSatir(), false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(117)).andExpect(jsonPath("$.hataSayisi").value(3));
        assertThat(sayi()).isEqualTo(117);

        yukle(t, yuzYirmiSatir(), false).andExpect(status().isOk())
                .andExpect(jsonPath("$.eklenen").value(0)).andExpect(jsonPath("$.zatenVar").value(117))
                .andExpect(jsonPath("$.satirlar[0].durum").value("ZATEN_VAR"));
        assertThat(sayi()).isEqualTo(117);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_YUKLE'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'SEFER_YUKLE' ORDER BY id LIMIT 1", String.class))
                .contains("\"eklenen\":117").contains("\"hata\":3");
    }

    @Test
    void varOlanIptalliSeferEzilmez_planlananSaatDegismez() throws Exception {
        Tarayici t = atamaci();
        yukle(t, yuzYirmiSatir(), false).andExpect(status().isOk());
        jdbc.update("UPDATE sefer SET durum = 'IPTAL', kalkis_gecikme_dk = 45 WHERE tren_no = 'T1'");

        yukle(t, yuzYirmiSatir(), false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(0));

        assertThat(jdbc.queryForObject("SELECT durum FROM sefer WHERE tren_no = 'T1'", String.class)).isEqualTo("IPTAL");
        assertThat(jdbc.queryForObject("SELECT kalkis_gecikme_dk FROM sefer WHERE tren_no = 'T1'", Integer.class)).isEqualTo(45);
    }

    @Test
    void dosyadaAyniAnahtarIkiKez_ikinciHata() throws Exception {
        byte[] d = TestExcel.sefer(List.<String[]>of(
                new String[] { "12.10.2026", "T1", "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir" },
                new String[] { "12.10.2026", "T1", "Ankara", "10:00", "Eskişehir", "11:30", "YHT", "Ankara–Eskişehir" }));

        yukle(atamaci(), d, false).andExpect(jsonPath("$.eklenen").value(1)).andExpect(jsonPath("$.hataSayisi").value(1))
                .andExpect(jsonPath("$.hatalar[0].satirNo").value(3));
    }

    @Test
    void gecersizDosya400_veSablonIndirilebilir() throws Exception {
        Tarayici t = atamaci();
        yukle(t, "xlsx degil".getBytes(), true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));

        t.get("/api/sefer/sablon").andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("sefer-sablonu.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void sablonOrnekSatiriYuklenebilir() throws Exception {
        Tarayici t = atamaci();
        byte[] sablon = t.get("/api/sefer/sablon").andReturn().getResponse().getContentAsByteArray();

        yukle(t, sablon, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(1)).andExpect(jsonPath("$.hataSayisi").value(0));
    }

    @Test
    void trenTuruKolonu_yuklenir_seferDtoveSqlde_gorunur() throws Exception {
        Tarayici t = atamaci();
        List<String> baslik = new java.util.ArrayList<>(TestExcel.BASLIK);
        baslik.add("Tren Türü");
        byte[] dosya = TestExcel.sefer(baslik, List.of(new String[] { "12.10.2026", "T1", "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245", "Yük" },
                new String[] { "12.10.2026", "T2", "Ankara", "10:00", "Eskişehir", "11:30", "YHT", "Ankara–Eskişehir", "245", "" }));

        yukle(t, dosya, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.hataSayisi").value(0));

        assertThat(jdbc.queryForList("SELECT tren_no, tren_turu FROM sefer ORDER BY tren_no")).extracting(m -> m.get("tren_turu")).containsExactly("YUK", null);
        t.get("/api/sefer?gun=2026-10-12").andExpect(status().isOk()).andExpect(jsonPath("$[?(@.trenNo=='T1')].trenTuru").value(org.hamcrest.Matchers.hasItem("YUK")));
    }
}
