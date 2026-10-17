package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * 05-review WARNING 2 / 07-qa BUG-2, BUG-3: makinistin izin talebinde girdi sınırları (değerler uygulama_ayar'da), aşırı tarih 500 değil 400, açık talep sınırı,
 * çakışma hatası makinist diliyle ve çakışan izni göstererek.
 */
class BenimIzinSinirTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);

    private static String govde(LocalDate bas, LocalDate bit) {
        return "{\"baslangic\":\"" + bas + "\",\"bitis\":\"" + bit + "\",\"tur\":\"YILLIK\"}";
    }

    private Tarayici makinist() throws Exception {
        return makinistGirisli(new AtamaVeri(jdbc).makinist("D001"), "D001");
    }

    @Test
    void asiriTarih_500DegilBadRequest_hemMakinistHemPersonelUcunda() throws Exception {
        Tarayici t = makinist();
        t.post("/api/benim/izinler", "{\"baslangic\":\"" + BUGUN.plusDays(1) + "\",\"bitis\":\"+999999999-12-31\",\"tur\":\"YILLIK\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.bitis").exists());
        t.post("/api/benim/izinler", "{\"baslangic\":\"+999999999-01-01\",\"bitis\":\"+999999999-12-31\",\"tur\":\"YILLIK\"}").andExpect(status().isBadRequest());
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(1), LocalDate.of(9999, 12, 31))).andExpect(status().isBadRequest());
        long m = jdbc.queryForObject("SELECT id FROM makinist", Long.class);
        atamaci().post("/api/izin", "{\"makinistId\":" + m + ",\"baslangic\":\"" + BUGUN + "\",\"bitis\":\"+999999999-12-31\",\"tur\":\"YILLIK\"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin", Integer.class)).isZero();
    }

    @Test
    void baslangicEnCokBirYilIleri_sureEnCokAltmisGun() throws Exception {
        Tarayici t = makinist();
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(366), BUGUN.plusDays(367))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.baslangic").exists());
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(365), BUGUN.plusDays(366))).andExpect(status().isCreated());
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(10), BUGUN.plusDays(10 + 60))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.bitis").exists()); // 61 gün
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(10), BUGUN.plusDays(10 + 59))).andExpect(status().isCreated()); // 60 gün
    }

    @Test
    void ayarlarDegisirseSinirDegisir() throws Exception {
        Tarayici t = makinist();
        jdbc.update("UPDATE uygulama_ayar SET deger = 5 WHERE anahtar = 'izin.en_uzun_gun'");
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(10), BUGUN.plusDays(15))).andExpect(status().isBadRequest()); // 6 gün
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(10), BUGUN.plusDays(14))).andExpect(status().isCreated());
    }

    @Test
    void acikTalepSiniri_besTane_altinciIcin409_kararSonrasiYenidenAcilir() throws Exception {
        Tarayici t = makinist();
        for (int i = 1; i <= 5; i++) {
            t.post("/api/benim/izinler", govde(BUGUN.plusDays(i * 3), BUGUN.plusDays(i * 3))).andExpect(status().isCreated());
        }
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(30), BUGUN.plusDays(30))).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ACIK_TALEP_SINIRI"))
                .andExpect(jsonPath("$.detail").value(containsString("5")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin", Integer.class)).isEqualTo(5);

        long ilk = jdbc.queryForObject("SELECT min(id) FROM izin", Long.class);
        atamaci().post("/api/izin/" + ilk + "/red", "").andExpect(status().isOk());
        t.post("/api/benim/izinler", govde(BUGUN.plusDays(30), BUGUN.plusDays(30))).andExpect(status().isCreated()); // RED açık talep sayılmaz
    }

    @Test
    void cakismaMesaji_makinistDiliyle_cakisanIzniGosterir() throws Exception {
        Tarayici t = makinist();
        long m = jdbc.queryForObject("SELECT id FROM makinist", Long.class);
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, ?, ?, 'YILLIK', 'ONAY', 'ata')", m, BUGUN.plusDays(22), BUGUN.plusDays(28));

        t.post("/api/benim/izinler", govde(BUGUN.plusDays(26), BUGUN.plusDays(30))).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("IZIN_CAKISMASI"))
                .andExpect(jsonPath("$.detail").value(containsString("izninizle çakışıyor")))
                .andExpect(jsonPath("$.detail").value(containsString(tr(BUGUN.plusDays(22)) + " – " + tr(BUGUN.plusDays(28)))))
                .andExpect(jsonPath("$.detail").value(not(containsString("Bu makinistin"))));
    }

    private static String tr(LocalDate d) {
        return d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }
}
