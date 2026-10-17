package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;

/** MK-10: tohum sayıyı sabit yazmaz, analiz kataloğuyla ({@link VarsayilanParametreler}) karşılaştırılır. */
class ParametreTohumTest extends TestVeritabani {

    @Autowired
    ParametreServisi servis;

    @Test
    void tohumKatalogaEsit_32parametre_22siddet() {
        Map<String, String> db = jdbc.queryForList("SELECT anahtar, deger FROM kural_parametre").stream()
                .collect(Collectors.toMap(r -> (String) r.get("anahtar"), r -> (String) r.get("deger")));
        Map<String, Siddet> siddet = jdbc.queryForList("SELECT kod, siddet FROM kural_siddet").stream()
                .collect(Collectors.toMap(r -> (String) r.get("kod"), r -> Siddet.valueOf((String) r.get("siddet"))));

        assertThat(db).isEqualTo(VarsayilanParametreler.DEGERLER).hasSize(32);
        assertThat(siddet).isEqualTo(VarsayilanParametreler.SIDDETLER).hasSize(22).doesNotContainKey("K-10");
        assertThat(siddet).containsEntry("K-20", Siddet.UYARI).containsEntry("K-21", Siddet.KAPALI).containsEntry("K-22", Siddet.UYARI).containsEntry("K-23", Siddet.ENGEL).containsEntry("K-14", Siddet.KAPALI); // V30 (OK-24)
        assertThat(db).containsEntry("dinlenme.ev_saat", "11").containsEntry("gece.baslangic", "20:00")
                .containsEntry("nobet.ayirma_saat", "4").containsEntry("nobet.bekleme_sayilir", "0").containsEntry("nobet.asgari_aktarma_dk", "20");
    }

    /** F3a WARNING: kadro iş parçacığı ayarı tohumda 0 (= otomatik formül), 0..32 aralığında. */
    @Test
    void kadroIsParcacigiAyari_tohumda_otomatik() {
        assertThat(jdbc.queryForMap("SELECT deger, alt, ust FROM uygulama_ayar WHERE anahtar = 'kadro.is_parcacigi'")).containsEntry("deger", 0).containsEntry("alt", 0).containsEntry("ust", 32);
    }

    @Test
    void yukle_saf_parametrelere_cevirir() {
        Parametreler p = servis.yukle();

        assertThat(p.siddet("K-20")).isEqualTo(Siddet.UYARI);
        assertThat(p.tamsayi("belge.sinav_gecerlilik_ay")).isEqualTo(60);
        assertThat(p.dakika("gece.max_saat")).isEqualTo(450);
    }

    @Test
    void api_okuma_atamaci_gorur_kaynakVeGuvenilirlikVar() throws Exception {
        atamaci().get("/api/parametre").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(32))
                .andExpect(jsonPath("$[0].kaynak").isNotEmpty())
                .andExpect(jsonPath("$[0].guvenilirlik").isNotEmpty());
        atamaci().get("/api/kural").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(22));
    }
}
