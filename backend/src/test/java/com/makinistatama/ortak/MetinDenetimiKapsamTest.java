package com.makinistatama.ortak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.makinist.MakinistExcelOkuyucu;
import com.makinistatama.makinist.MakinistExcelOkuyucu.Sonuc;
import com.makinistatama.makinist.MakinistExcelOkuyucu.Tanimlar;
import com.makinistatama.makinist.TestMakinistExcel;

/**
 * QA tur 2 YENİ-3: yön denetim (bidi) / sıfır genişlik karakter reddi yalnız kurum ve tatil adındaydı; aynı sınıf risk makinist adı/sicili (elle ve Excel), tanım adı ve özel kural adında da
 * vardı (çizelge/baskıda ad ters görünür, sıfır genişlikli karakter görsel olarak aynı iki ayrı sicil üretir). Normal Türkçe metin etkilenmez.
 */
class MetinDenetimiKapsamTest extends TestVeritabani {

    private static final String BIDI = "‮"; // RIGHT-TO-LEFT OVERRIDE
    private static final String SIFIR = "​"; // ZERO WIDTH SPACE

    private String makinistGovde(String sicil, String ad, long gar) {
        return """
                {"sicil":"%s","adSoyad":"%s","bagliGarId":%d,"durum":"AKTIF","ehliyetBitis":"2030-01-01","saglikBitis":"2030-01-01","psikoteknikBitis":"2030-01-01"}""".formatted(sicil, ad, gar);
    }

    @Test
    void makinistElleEkle_bidiVeSifirGenislik_400_normalTurkceKabul() throws Exception {
        long gar = TestTanimlari.gar(jdbc, "Ankara");
        Tarayici t = atamaci();

        t.post("/api/makinist", makinistGovde("BD001", "Ters" + BIDI + "Ad", gar)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.adSoyad").exists());
        t.post("/api/makinist", makinistGovde("BD002", "Sıfır" + SIFIR + "Genişlik", gar)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.adSoyad").exists());
        t.post("/api/makinist", makinistGovde("BD003" + SIFIR, "Ayşe Yılmaz", gar)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.sicil").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class)).isZero();

        t.post("/api/makinist", makinistGovde("BD004", "Şükrü Çağlayan-İnce", gar)).andExpect(status().isCreated());
    }

    @Test
    void makinistGuncelle_bidiAd_400() throws Exception {
        long gar = TestTanimlari.gar(jdbc, "Ankara");
        Tarayici t = atamaci();
        String olusan = t.post("/api/makinist", makinistGovde("BD010", "Ali Veli", gar)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.<Number>read(olusan, "$.id").longValue();
        int versiyon = com.jayway.jsonpath.JsonPath.<Number>read(olusan, "$.versiyon").intValue();

        t.put("/api/makinist/" + id, makinistGovde("BD010", "Ters" + BIDI + "Ad", gar).replace("}", ",\"versiyon\":" + versiyon + "}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.adSoyad").exists());
        assertThat(jdbc.queryForObject("SELECT ad_soyad FROM makinist WHERE id = ?", String.class, id)).isEqualTo("Ali Veli");
    }

    @Test
    void makinistExcel_bidiAdVeSifirGenislikSicil_satirHatasi() {
        Tanimlar tanimlar = new Tanimlar(Map.of(AramaMetni.katla("Ankara"), 1L), Map.of(), Map.of());
        byte[] dosya = TestMakinistExcel.kitap(List.of(TestMakinistExcel.makinist("BD020", "Ters" + BIDI + "Ad", "Ankara"), TestMakinistExcel.makinist("BD021" + SIFIR, "Sıfır Sicil", "Ankara"),
                TestMakinistExcel.makinist("BD022", "Normal Ad", "Ankara")), List.of(), List.of());

        Sonuc s = MakinistExcelOkuyucu.oku(dosya, tanimlar, LocalDate.of(2026, 11, 1));

        assertThat(s.gecerli()).extracting(g -> g.istek().sicil()).containsExactly("BD022");
        assertThat(s.hatalar()).hasSize(2);
        assertThat(s.hatalar()).extracting(h -> h.neden()).allSatisfy(n -> assertThat(n).contains("yön denetim"));
    }

    @Test
    void tanimAdi_bidiVeSifirGenislik_400_ekleVeYenidenAdlandir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Ters" + BIDI + "Gar\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.ad").exists());
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Sıfır" + SIFIR + "Gar\"}").andExpect(status().isBadRequest());
        y.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Eskişehir\"}").andExpect(status().isCreated());

        y.put("/api/tanimlar/1", "{\"ad\":\"Ters" + BIDI + "Gar\"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT ad FROM tanim WHERE id = 1", String.class)).isEqualTo("Eskişehir");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tanim", Integer.class)).isEqualTo(1);
    }

    @Test
    void ozelKuralAdi_bidi_400() throws Exception {
        String govde = "{\"ad\":\"Gece" + BIDI + "kural\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"AY\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":200,\"siddet\":\"ENGEL\",\"gerekce\":\"TİS\",\"gecerlilikBas\":\"2026-01-01\"}";

        yonetici().post("/api/ozel-kural", govde).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.ad").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural", Integer.class)).isZero();
    }
}
