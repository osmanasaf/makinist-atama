package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** T10.1: sefer iptal / gecikme (FR-07): neden zorunlu, planlanan saat saklı, varış gecikmesi boşsa kalkışa eşit, kayıt eski -> yeni. */
class SeferDegisiklikTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    AtamaVeri v;
    long sefer;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        sefer = v.sefer("T1", GUN, "08:00", "09:30");
    }

    @Test
    void gecikme_nedensiz400_varisBosKalkisaEsit_planlananSaatDegismez_kayitEskiYeni() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":90}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.neden").exists());
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":90,\"neden\":\"DIGER\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.nedenNotu").exists());
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":-1,\"neden\":\"GECIKME\"}").andExpect(status().isBadRequest());
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"neden\":\"GECIKME\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.kalkisGecikmeDk").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();

        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":90,\"neden\":\"GECIKME\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.kalkisGecikmeDk").value(90)).andExpect(jsonPath("$.varisGecikmeDk").value(90))
                .andExpect(jsonPath("$.planlananKalkis").value(containsString("T08:00:00+03:00"))).andExpect(jsonPath("$.guncelKalkis").value(containsString("T09:30:00+03:00")))
                .andExpect(jsonPath("$.guncelVaris").value(containsString("T11:00:00+03:00"))).andExpect(jsonPath("$.tarih").value("2026-10-12"));

        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'SEFER_GECIKME'");
        assertThat(kayit).containsEntry("eski", "08:00-09:30").containsEntry("yeni", "09:30-11:00").containsEntry("neden", "GECIKME")
                .containsEntry("kullanici_adi", "Ata Maci").containsEntry("sefer_id", sefer);
        assertThat(jdbc.queryForObject("SELECT planlanan_varis = planlanan_kalkis + interval '90 minutes' FROM sefer WHERE id = ?", Boolean.class, sefer)).isTrue();
    }

    @Test
    void gecikme_varisAyriGirilir_veSifirlaYenidenYazar() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":60,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.varisGecikmeDk").value(30));
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":0,\"neden\":\"DIGER\",\"nedenNotu\":\"toparlandı\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.kalkisGecikmeDk").value(0)).andExpect(jsonPath("$.varisGecikmeDk").value(0));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'SEFER_GECIKME'", Integer.class)).isEqualTo(2);
    }

    /** BUG-11 (kök: yalnız kalkış gecikmesi büyük girilince güncel varış kalkıştan önceye düşüyordu). 90 dk sefer, 30/30 gecikmeli, kalkış 150 -> 400. */
    @Test
    void gecikme_guncelVarisKalkistenSonraOlmali_400_seferVeKayitDegismez() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":30,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isOk());
        int kayit = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);

        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":150,\"varisGecikmeDk\":30,\"neden\":\"GECIKME\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.varisGecikmeDk").value(containsString("en az 61 dk")));
        // sınır: 90 dk sefer, kalkış 150 -> varış en az 61 dk; 60 dk reddedilir, 61 dk kabul edilir
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":150,\"varisGecikmeDk\":60,\"neden\":\"GECIKME\"}").andExpect(status().isBadRequest());
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":150,\"varisGecikmeDk\":61,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT kalkis_gecikme_dk || '/' || varis_gecikme_dk FROM sefer WHERE id = ?", String.class, sefer)).isEqualTo("150/61");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(kayit + 1);
    }

    /** Planlanan saat düzenlemesi mevcut gecikmeyle varışı kalkıştan öne düşürürse 400 (DB kısıtına düşüp yanlış "sefer tekrarı" olmasın). */
    @Test
    void seferGuncelle_mevcutGecikmeVarisiOneDusururse_400_dbKisitiDaKorur() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":100,\"varisGecikmeDk\":60,\"neden\":\"GECIKME\"}").andExpect(status().isOk()); // 90 dk sefer: 50 dk kalır
        var s = jdbc.queryForMap("SELECT kalkis_gar_id, varis_gar_id, arac_tipi_id, hat_id, versiyon FROM sefer WHERE id = ?", sefer);
        String govde = "{\"trenNo\":\"T1\",\"tarih\":\"2026-10-12\",\"kalkisSaat\":\"08:00\",\"varisSaat\":\"%s\",\"kalkisGarId\":" + s.get("kalkis_gar_id")
                + ",\"varisGarId\":" + s.get("varis_gar_id") + ",\"aracTipiId\":" + s.get("arac_tipi_id") + ",\"hatId\":" + s.get("hat_id") + ",\"versiyon\":" + s.get("versiyon") + "}";

        t.put("/api/sefer/" + sefer, govde.formatted("08:30")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.varisSaat").exists()); // 30 dk - 40 dk < 0
        t.put("/api/sefer/" + sefer, govde.formatted("09:00")).andExpect(status().isOk()); // 60 dk - 40 dk > 0

        // hangi yoldan yazılırsa yazılsın bozuk kayıt olmaz
        assertThatThrownBy(() -> jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 600, varis_gecikme_dk = 0 WHERE id = ?", sefer)).hasMessageContaining("sefer_guncel_sure_ck");
    }

    @Test
    void iptal_nedenZorunlu_planlananSaatKorunur_kayitVar_ikinciIptal409_gecikmeGirilemez() throws Exception {
        Tarayici t = atamaci();
        t.post("/api/sefer/" + sefer + "/iptal", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.neden").exists());
        t.post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"DIGER\",\"nedenNotu\":\" \"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT durum FROM sefer WHERE id = ?", String.class, sefer)).isEqualTo("PLANLI");

        t.post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"DIGER\",\"nedenNotu\":\"hat kapalı\"}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("IPTAL"))
                .andExpect(jsonPath("$.planlananKalkis").value(containsString("T08:00:00+03:00")));
        t.post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_IPTAL"));
        t.post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":10,\"neden\":\"GECIKME\"}").andExpect(status().isConflict());

        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'SEFER_IPTAL'");
        assertThat(kayit).containsEntry("eski", "PLANLI").containsEntry("yeni", "IPTAL").containsEntry("neden", "DIGER").containsEntry("neden_notu", "hat kapalı")
                .containsEntry("kullanici_adi", "Ata Maci");
    }

    @Test
    void olmayanSefer404_anonim401() throws Exception {
        atamaci().post("/api/sefer/9999/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isNotFound());
        new Tarayici(mvc).post("/api/sefer/" + sefer + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isUnauthorized());
    }
}
