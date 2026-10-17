package com.makinistatama.tanim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * Gar çifti intikal süre tablosu (MK-109, FR-06): Tanımlar verisi (yönsüz, tohumsuz); yönetici yazar, personel okur; doğrulama 400, sürüm 409, denetim kaydı (eski -> yeni dakika),
 * silme. Ayar kataloğuna dokunmaz.
 */
class GarIntikalSureTest extends TestVeritabani {

    @Autowired
    GarIntikalSureServisi servis;

    AtamaVeri v;
    Tarayici yonetici;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        yonetici = yonetici();
    }

    private String govde(long a, long b, Object dakika, String ek) {
        return "{\"garAId\":" + a + ",\"garBId\":" + b + ",\"dakika\":" + dakika + ek + "}";
    }

    @Test
    void tohumsuz_personelOkur_yoneticiYazar_atamaciYazamaz() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gar_intikal_suresi", Integer.class)).as("migration tohumlamaz").isZero();
        atamaci().get("/api/tanimlar/intikal-sure").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        atamaci().put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 90, "")).andExpect(status().isForbidden());
        atamaci().delete("/api/tanimlar/intikal-sure?garAId=" + v.ankara + "&garBId=" + v.eskisehir).andExpect(status().isForbidden());
        new Tarayici(mvc).get("/api/tanimlar/intikal-sure").andExpect(status().isUnauthorized());

        yonetici.put("/api/tanimlar/intikal-sure", govde(v.eskisehir, v.ankara, 90, "")).andExpect(status().isOk()) // ters sıra: yönsüz, normalize
                .andExpect(jsonPath("$.garAId").value(Math.min(v.ankara, v.eskisehir))).andExpect(jsonPath("$.garBId").value(Math.max(v.ankara, v.eskisehir)))
                .andExpect(jsonPath("$.dakika").value(90)).andExpect(jsonPath("$.versiyon").value(0));
        atamaci().get("/api/tanimlar/intikal-sure").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].garA").value("Ankara")).andExpect(jsonPath("$[0].garB").value("Eskişehir"));
        assertThat(servis.sure(v.eskisehir, v.ankara).getAsInt()).isEqualTo(90);
        assertThat(servis.sure(v.ankara, v.eskisehir).getAsInt()).isEqualTo(90);
        assertThat(servis.sure(v.ankara, v.ankara)).isEmpty();
    }

    @Test
    void dogrulama400_ayniGarGarDegilAralikDisi() throws Exception {
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.ankara, 90, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.garBId").exists());
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.hat, 90, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.garBId").exists());
        yonetici.put("/api/tanimlar/intikal-sure", govde(999, v.ankara, 90, "")).andExpect(status().isBadRequest());
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 0, "")).andExpect(status().isBadRequest());
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 1441, "")).andExpect(status().isBadRequest());
        yonetici.put("/api/tanimlar/intikal-sure", "{\"garAId\":" + v.ankara + ",\"garBId\":" + v.eskisehir + "}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gar_intikal_suresi", Integer.class)).isZero();
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 1, "")).andExpect(status().isOk());
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 1440, ",\"versiyon\":0")).andExpect(status().isOk());
    }

    @Test
    void guncelle_surumZorunlu_bayat409_degisiklikKaydiEskiYeni() throws Exception {
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 90, "")).andExpect(status().isOk());

        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 120, "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.versiyon").exists());
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 120, ",\"versiyon\":4")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("VERSIYON_CAKISMASI"));
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 120, ",\"versiyon\":0")).andExpect(status().isOk()).andExpect(jsonPath("$.dakika").value(120))
                .andExpect(jsonPath("$.versiyon").value(1));
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 120, ",\"versiyon\":1")).andExpect(status().isOk()).andExpect(jsonPath("$.versiyon").value(1)); // aynı değer: işlem yok

        assertThat(jdbc.queryForList("SELECT eski, yeni FROM degisiklik_kaydi WHERE tur = 'INTIKAL_SURESI_DEGISTI' ORDER BY id")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'INTIKAL_SURESI_DEGISTI' ORDER BY id LIMIT 1", String.class)).isEqualTo("Ankara – Eskişehir: 90 dk");
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'INTIKAL_SURESI_DEGISTI' ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("Ankara – Eskişehir: 90 dk");
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'INTIKAL_SURESI_DEGISTI' ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("Ankara – Eskişehir: 120 dk");
    }

    @Test
    void sil_204_ikinciSilme404_kayitYeniBos() throws Exception {
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 90, "")).andExpect(status().isOk());

        yonetici.delete("/api/tanimlar/intikal-sure?garAId=" + v.eskisehir + "&garBId=" + v.ankara).andExpect(status().isNoContent());
        yonetici.delete("/api/tanimlar/intikal-sure?garAId=" + v.eskisehir + "&garBId=" + v.ankara).andExpect(status().isNotFound());

        assertThat(servis.sure(v.ankara, v.eskisehir)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'INTIKAL_SURESI_DEGISTI' ORDER BY id DESC LIMIT 1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'INTIKAL_SURESI_DEGISTI' ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("Ankara – Eskişehir: 90 dk");
    }

    @Test
    void ayarKataloguDegismez_tanimYoluTanimControllerIleCakismaz() throws Exception {
        int ayarSayisi = jdbc.queryForObject("SELECT count(*) FROM uygulama_ayar", Integer.class);
        yonetici.put("/api/tanimlar/intikal-sure", govde(v.ankara, v.eskisehir, 90, "")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM uygulama_ayar", Integer.class)).isEqualTo(ayarSayisi);
        yonetici.put("/api/tanimlar/" + v.ankara, "{\"ad\":\"Ankara Gar\"}").andExpect(status().isOk()); // /{id} yolu hâlâ çalışır
        yonetici.get("/api/tanimlar?tip=GAR").andExpect(status().isOk());
    }
}
