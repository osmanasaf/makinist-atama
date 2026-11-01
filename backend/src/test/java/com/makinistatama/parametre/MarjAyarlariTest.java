package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F3 / MK-173 / AK-3.4: 12 marj ve yorgunluk ayarı (V35, grup MARJ): varsayılan, aralık, neden zorunlu, denetim. */
class MarjAyarlariTest extends TestVeritabani {

    /** anahtar -> {varsayılan, alt, ust, birim} (MK-173 tablosu). */
    private static final Map<String, int[]> BEKLENEN = new java.util.LinkedHashMap<>();
    static {
        BEKLENEN.put("marj.aktif", new int[] { 1, 0, 1 });
        BEKLENEN.put("marj.gunluk_dk", new int[] { 60, 0, 720 });
        BEKLENEN.put("marj.gece_dk", new int[] { 30, 0, 240 });
        BEKLENEN.put("marj.dinlenme_dk", new int[] { 60, 0, 720 });
        BEKLENEN.put("marj.hafta_tatili_dk", new int[] { 120, 0, 1440 });
        BEKLENEN.put("marj.yillik_saat", new int[] { 20, 0, 270 });
        BEKLENEN.put("marj.aktarma_dk", new int[] { 10, 0, 120 });
        BEKLENEN.put("marj.gece_hafta", new int[] { 0, 0, 3 });
        BEKLENEN.put("yorgunluk.gun", new int[] { 7, 1, 14 });
        BEKLENEN.put("yorgunluk.hafta_saat", new int[] { 40, 1, 168 });
        BEKLENEN.put("yorgunluk.gece_adet", new int[] { 3, 1, 14 });
        BEKLENEN.put("yorgunluk.dinlenme_saat", new int[] { 12, 1, 72 });
    }

    private static String govde(int deger, String neden) {
        return "{\"deger\":" + deger + (neden == null ? "" : ",\"neden\":\"" + neden + "\"") + "}";
    }

    @Test
    void onikiSatir_grupMarj_varsayilanAralikVeSiraDolu() {
        List<Map<String, Object>> satirlar = jdbc.queryForList("SELECT * FROM uygulama_ayar WHERE grup = 'MARJ' ORDER BY sira");
        assertThat(satirlar).hasSize(12);
        assertThat(satirlar).extracting(s -> s.get("anahtar")).containsExactlyElementsOf(BEKLENEN.keySet());
        assertThat(satirlar).extracting(s -> s.get("sira")).containsExactly(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120);
        for (Map<String, Object> s : satirlar) {
            int[] b = BEKLENEN.get((String) s.get("anahtar"));
            assertThat(s.get("deger")).as(s.get("anahtar") + " değer").isEqualTo(b[0]);
            assertThat(s.get("varsayilan")).as(s.get("anahtar") + " varsayılan").isEqualTo(b[0]);
            assertThat(s.get("alt")).as(s.get("anahtar") + " alt").isEqualTo(b[1]);
            assertThat(s.get("ust")).as(s.get("anahtar") + " üst").isEqualTo(b[2]);
            assertThat((String) s.get("aciklama")).as("kaynaksız yer tutucu etiketi").contains("[V]").contains("bilgi amaçlıdır");
            assertThat(AyarAdlari.var((String) s.get("anahtar"))).as("okunur ad").isTrue();
        }
    }

    @Test
    void ayarlarEkranindaGorunur_yoneticiGorur_atamaci403() throws Exception {
        yonetici().get("/api/ayar").andExpect(status().isOk()).andExpect(jsonPath("$[?(@.anahtar=='marj.gunluk_dk')].grup").value("MARJ"))
                .andExpect(jsonPath("$[?(@.anahtar=='marj.gunluk_dk')].ad").value("Günlük süre sınırına yakınlık"))
                .andExpect(jsonPath("$[?(@.anahtar=='yorgunluk.gun')].varsayilan").value(7));
        atamaci().get("/api/ayar").andExpect(status().isForbidden());
    }

    @Test
    void aralikDisiDeger400_aralikIcindeDegerDenetimKaydiOlur_nedenZorunlu() throws Exception {
        Tarayici y = yonetici();
        for (var e : BEKLENEN.entrySet()) {
            int[] b = e.getValue();
            y.put("/api/ayar/" + e.getKey(), govde(b[2] + 1, "aralık dışı")).andExpect(status().isBadRequest());
            if (b[1] > 0) {
                y.put("/api/ayar/" + e.getKey(), govde(b[1] - 1, "aralık dışı")).andExpect(status().isBadRequest());
            } else {
                y.put("/api/ayar/" + e.getKey(), govde(-1, "aralık dışı")).andExpect(status().isBadRequest());
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).as("geçersizde kayıt yok").isZero();
        y.put("/api/ayar/marj.gunluk_dk", govde(90, null)).andExpect(status().isBadRequest());
        y.put("/api/ayar/marj.gunluk_dk", govde(90, "  ")).andExpect(status().isBadRequest());
        y.put("/api/ayar/marj.gunluk_dk", govde(90, "kurum politikası")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value(90)).andExpect(jsonPath("$.varsayilan").value(60));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND varlik_tipi = 'AYAR' AND eski = '60' AND yeni = '90' AND ayrinti LIKE '%marj.gunluk_dk%'",
                Integer.class)).isEqualTo(1);
    }
}
