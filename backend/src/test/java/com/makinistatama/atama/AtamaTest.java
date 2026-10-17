package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T8.3: kural kontrollü atama/değiştirme/kaldırma, UYARI gerekçesi, anlık görüntü. */
class AtamaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12); // Pazartesi

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    @Test
    void ata_201_satirYazilir_kayitVeAnlikGoruntuDolu() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ASIL")).andExpect(jsonPath("$.sicil").value("D001")).andExpect(jsonPath("$.atayan").value("Ata Maci"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ? AND rol = 'ASIL'", Integer.class, s, m)).isEqualTo(1);
        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'ATAMA'");
        assertThat(kayit).containsEntry("makinist_id", m).containsEntry("sefer_id", s).containsEntry("yeni", "ASIL:D001").containsEntry("kullanici_adi", "Ata Maci");
        String json = (String) kayit.get("ayrinti");
        assertThat(json).contains("\"kontrolEdilenKodlar\"").contains("K-01").contains("\"sonuc\":\"UYGUN\"").contains("\"ehliyetBitis\":\"2030-01-01\"")
                .contains("\"dinlenme.ev_saat\":\"11\"");
        assertThat(json.substring(0, json.indexOf("\"sonuc\""))).doesNotContain("K-21"); // K-21 KAPALI: kontrol edilen kodlarda yok
    }

    @Test
    void anlikGoruntu_parametreSonradanDegisseDeEskiKayitDegismez() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());
        String once = jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class);

        yonetici().put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"8\",\"neden\":\"deneme\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA'", String.class)).isEqualTo(once)
                .contains("\"dinlenme.ev_saat\":\"11\"");
    }

    @Test
    void engelliAtama_422_nedenlerle_satirYazilmaz() throws Exception {
        long m = v.makinist("D001", false, "AKTIF"); // hat yetkisi yok
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.kod").value("KURAL_IHLALI")).andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-06")))
                .andExpect(jsonPath("$.ihlaller[0].metin").value(containsString("Hat yetkisi yok")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur LIKE 'ATAMA%'", Integer.class)).isZero();
    }

    @Test
    void askidaMakinist_atanamaz() throws Exception {
        long m = v.makinist("D001", true, "ASKIDA");
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-01")));
    }

    @Test
    void uyari_K20_gerekcesiz400_gerekceli201_gerekceKayitta() throws Exception {
        long m = v.makinist("D001");
        long s1 = v.sefer("T1", GUN, "08:00", "10:00"); // Ankara -> Eskişehir
        long s2 = v.sefer("T2", GUN, "13:00", "15:00"); // Ankara -> Eskişehir: önceki varış Eskişehir, kalkış Ankara -> konum uyuşmazlığı
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s1, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + s2, AtamaVeri.ataGovdesi(m)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.gerekce").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, s2)).isZero();

        t.post("/api/atama/sefer/" + s2, "{\"makinistId\":" + m + ",\"gerekce\":\"Boş tren dönüşü\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.uyarilar[0].kod").value("K-20")).andExpect(jsonPath("$.uyarilar[0].siddet").value("UYARI"));
        assertThat(jdbc.queryForObject("SELECT gerekce FROM atama WHERE sefer_id = ?", String.class, s2)).contains("Boş tren");
        String json = jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'ATAMA' AND sefer_id = ?", String.class, s2);
        assertThat(json).contains("\"sonuc\":\"UYARI\"").contains("Boş tren").contains("K-20");
    }

    @Test
    void degistir_nedenZorunlu_digerdeNotZorunlu_eskiSilinirKayitYazilir() throws Exception {
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m2)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.neden").exists());
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"neden\":\"DIGER\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.nedenNotu").exists());
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE sefer_id = ?", Long.class, s)).isEqualTo(m1);

        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk()).andExpect(jsonPath("$.sicil").value("D002"));

        assertThat(jdbc.queryForList("SELECT makinist_id FROM atama WHERE sefer_id = ?", Long.class, s)).containsExactly(m2);
        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'ATAMA_DEGISTIR'");
        assertThat(kayit).containsEntry("eski", "ASIL:D001").containsEntry("yeni", "ASIL:D002").containsEntry("neden", "HASTALIK");
    }

    @Test
    void kaldir_nedenZorunlu_204_satirSilinir_kayitYazilir() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.delete("/api/atama/sefer/" + s + "?rol=ASIL").andExpect(status().isBadRequest());
        t.delete("/api/atama/sefer/" + s + "?rol=ASIL&neden=IZIN").andExpect(status().isNoContent());
        t.delete("/api/atama/sefer/" + s + "?rol=ASIL&neden=IZIN").andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'ATAMA_KALDIR'")).containsEntry("eski", "ASIL:D001").containsEntry("neden", "IZIN");
    }

    @Test
    void asilVeYardimci_ikisiAtanir_ayniMakinistIkinciRolde409() throws Exception {
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m1)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m2 + ",\"rol\":\"YARDIMCI\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("YARDIMCI"));
        t.post("/api/atama/sefer/" + s, "{\"makinistId\":" + m1 + ",\"rol\":\"YARDIMCI\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));
        t.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m1)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].makinistler", hasSize(2)));
    }

    @Test
    void ayniMakinistCakisanIkiSefer_ikinci422_K10() throws Exception {
        long m = v.makinist("D001");
        long s1 = v.sefer("T1", GUN, "08:00", "10:00");
        long s2 = v.sefer("T2", GUN, "09:00", "11:00");
        Tarayici t = atamaci();
        t.post("/api/atama/sefer/" + s1, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + s2, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[*].kod", hasItem("K-10")));
    }

    @Test
    void iptalSefereAtanamaz_olmayanSeferVeMakinist404_409() throws Exception {
        long m = v.makinist("D001");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", s);

        atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SEFER_IPTAL"));
        atamaci().post("/api/atama/sefer/999", AtamaVeri.ataGovdesi(m)).andExpect(status().isNotFound());
        long s2 = v.sefer("T2", GUN, "12:00", "14:00");
        atamaci().post("/api/atama/sefer/" + s2, AtamaVeri.ataGovdesi(9999)).andExpect(status().isNotFound());
    }

    /** M2 QA BUG-10: 422 başlığı nedenleri yinelemez; nedenler yalnız ihlaller[] içindedir (ekranda iki kez görünüyordu). */
    @Test
    void engelliAtama_422_detailNedenleriTekrarlamaz() throws Exception {
        long m = v.makinist("D001", false, "AKTIF");
        long s = v.sefer("T1", GUN, "08:00", "10:00");

        String detail = atamaci().post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.ihlaller[0].metin").value(containsString("Hat yetkisi yok"))).andReturn().getResponse().getContentAsString();

        assertThat(detail).contains("\"detail\":\"Kural ihlali: D001 bu sefere atanamaz.\"");
    }
}
