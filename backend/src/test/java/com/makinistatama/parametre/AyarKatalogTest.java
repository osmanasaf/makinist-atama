package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** MK-93/94: ayar kataloğu (GET /api/ayar, YONETICI) ve uygulama ayarı yazma doğrulamaları (FR-04, NFR-03). */
class AyarKatalogTest extends TestVeritabani {

    static final Set<String> GRUPLAR = Set.of("CALISMA", "GECE", "BELGE", "PUANTAJ", "TAKAS_IZIN", "YOLCULUK", "OTOMATIK", "KADRO", "PERSONEL", "MAKINIST", "MARJ");

    private static String govde(String deger, String neden) {
        return "{\"deger\":" + deger + (neden == null ? "" : ",\"neden\":\"" + neden + "\"") + "}";
    }

    @Test
    void liste_yonetici34Satir_atamaci403_anonim401() throws Exception {
        yonetici().get("/api/ayar").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(34)))
                .andExpect(jsonPath("$[?(@.anahtar=='gordum.kalem_gun')].deger").value(7))
                .andExpect(jsonPath("$[?(@.anahtar=='gordum.kalem_gun')].varsayilan").value(7))
                .andExpect(jsonPath("$[?(@.anahtar=='gordum.kalem_gun')].grup").value("MAKINIST"));
        atamaci().get("/api/ayar").andExpect(status().isForbidden());
        new Tarayici(mvc).get("/api/ayar").andExpect(status().isUnauthorized());
    }

    @Test
    void katalog_ucTabloda_varsayilanGrupSiraDolu_bilinenGrupKumesi() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uygulama_ayar WHERE varsayilan IS NULL OR grup IS NULL OR sira IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT DISTINCT grup FROM kural_parametre UNION SELECT DISTINCT grup FROM kural_siddet UNION SELECT DISTINCT grup FROM uygulama_ayar", String.class))
                .isSubsetOf(GRUPLAR);
        atamaci().get("/api/parametre").andExpect(jsonPath("$[0].varsayilan").isNotEmpty()).andExpect(jsonPath("$[0].grup").isNotEmpty()).andExpect(jsonPath("$[0].sira").isNumber());
        atamaci().get("/api/kural").andExpect(jsonPath("$[0].varsayilan").isNotEmpty()).andExpect(jsonPath("$[0].grup").isNotEmpty());
    }

    /** QA UX-3: her satırın okunur adı var (anahtar metni değil); API `ad` döner. */
    @Test
    void herSatirinOkunurAdiVar() throws Exception {
        var anahtarlar = jdbc.queryForList("SELECT anahtar FROM kural_parametre UNION ALL SELECT kod FROM kural_siddet UNION ALL SELECT anahtar FROM uygulama_ayar", String.class);
        assertThat(anahtarlar).hasSize(88).allSatisfy(a -> assertThat(AyarAdlari.var(a)).as(a).isTrue());
        assertThat(anahtarlar.stream().map(AyarAdlari::ad).distinct()).as("adlar ayırt edilebilir").hasSize(88);
        yonetici().get("/api/ayar").andExpect(jsonPath("$[?(@.anahtar=='gordum.kalem_gun')].ad").value("Elinden alınan görev bildiriminin ekranda kalma süresi"));
        atamaci().get("/api/parametre").andExpect(jsonPath("$[?(@.anahtar=='nobet.asgari_aktarma_dk')].ad").value("Nöbet içinde asgari aktarma süresi"));
        atamaci().get("/api/kural").andExpect(jsonPath("$[?(@.kod=='K-14')].ad").value("Haftalık görev süresi"));
    }

    @Test
    void tazeDb_varsayilanDegereEsit_34ayar() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uygulama_ayar", Integer.class)).isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uygulama_ayar WHERE varsayilan <> deger", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kural_parametre WHERE varsayilan <> deger", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kural_siddet WHERE varsayilan <> siddet", Integer.class)).isZero();
    }

    @Test
    void yazma_gecerliDeger_tekDenetimKaydi_sonraDegerVarsayilanaDoner() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/ayar/gordum.kalem_gun", govde("10", "uzun süre görünsün")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value(10)).andExpect(jsonPath("$.varsayilan").value(7));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND varlik_tipi = 'AYAR' AND eski = '7' AND yeni = '10' AND ayrinti LIKE '%gordum.kalem_gun%'",
                Integer.class)).isEqualTo(1);

        y.put("/api/ayar/gordum.kalem_gun", govde("7", "Varsayılana döndürüldü")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).as("iki yazma iki kayıt").isEqualTo(2);
    }

    @Test
    void yazma_ayniDeger_kayitUretmez() throws Exception {
        yonetici().put("/api/ayar/gordum.kalem_gun", govde("7", "aynı")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    @Test
    void yazma_gecersiz_400_degerDegismez() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/ayar/gordum.kalem_gun", govde("5.5", "x")).andExpect(status().isBadRequest());
        y.put("/api/ayar/gordum.kalem_gun", govde("\"yedi\"", "x")).andExpect(status().isBadRequest());
        y.put("/api/ayar/gordum.kalem_gun", "{\"neden\":\"x\"}").andExpect(status().isBadRequest()); // deger eksik
        y.put("/api/ayar/gordum.kalem_gun", govde("10", "  ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.neden").exists());
        y.put("/api/ayar/gordum.kalem_gun", govde("61", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.deger").value("Değer 1 ile 60 arasında olmalı."));
        y.put("/api/ayar/gordum.kalem_gun", govde("0", "x")).andExpect(status().isBadRequest());
        atamaci().put("/api/ayar/gordum.kalem_gun", govde("10", "x")).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'gordum.kalem_gun'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }
}
