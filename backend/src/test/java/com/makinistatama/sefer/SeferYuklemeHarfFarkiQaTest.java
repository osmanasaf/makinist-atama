package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;

/** F2c Tur 3 madde 3, HTTP düzeyi (07-qa.md): yalnız harf farkıyla ayrışan iki tren + Değişim Noktaları -> yükleme engellenir, hiçbir sefer yazılmaz, ekran metni anlaşılır. */
class SeferYuklemeHarfFarkiQaTest extends TestVeritabani {

    @BeforeEach
    void tanimlar() {
        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.gar(jdbc, "Sivas");
        TestTanimlari.gar(jdbc, "Kars");
        TestTanimlari.hat(jdbc, "Ankara–Kars");
        TestTanimlari.aracTipi(jdbc, "YHT");
    }

    private static byte[] dosya(boolean degisimVar) {
        List<String[]> ana = List.<String[]>of(SeferYuklemeParcaTest.ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"),
                SeferYuklemeParcaTest.ana("12.10.2026", "de-1503", "09:00", "21:10", "1103"));
        List<String[]> degisim = degisimVar ? List.<String[]>of(new String[] { "DE-1503", "1", "Sivas", "13:30", "13:40", "603" }) : List.<String[]>of();
        return TestExcel.sefer(TestExcel.BASLIK, ana, degisim);
    }

    private ResultActions yukle(Tarayici t, byte[] d, boolean onizle) throws Exception {
        return t.istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle").file(new MockMultipartFile("dosya", "seferler.xlsx", "application/octet-stream", d))
                .param("onizle", Boolean.toString(onizle)));
    }

    @Test
    void harfFarkliIkiTren_degisimVerisiVarken_onizlemeVeYuklemeEngellenir_hicSeferYazilmaz() throws Exception {
        Tarayici t = atamaci();
        yukle(t, dosya(true), true).andExpect(status().isOk()).andExpect(jsonPath("$.engelli").value(true))
                .andExpect(jsonPath("$.hatalar[*].neden", hasItem(containsString("aynı tren farklı yazılmış olabilir"))));
        yukle(t, dosya(true), false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).isZero();
    }

    @Test
    void harfFarkliIkiTren_degisimVerisiYokken_bugunkuDavranis_engelYok() throws Exception {
        yukle(atamaci(), dosya(false), true).andExpect(status().isOk()).andExpect(jsonPath("$.engelli").value(false)).andExpect(jsonPath("$.eklenen").value(2));
    }
}
