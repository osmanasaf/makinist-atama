package com.makinistatama.kurulum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F1 / AK-1.9, MK-171: kurulum durumu veriden türetilir (bayrak saklanmaz); yalnız yönetici. */
class KurulumDurumuTest extends TestVeritabani {

    private List<Boolean> adimlar(Tarayici t) throws Exception {
        String json = t.get("/api/kurulum/durum").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var kok = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        List<Boolean> sonuc = new java.util.ArrayList<>();
        kok.get("adimlar").forEach(a -> sonuc.add(a.get("yapildi").asBoolean()));
        return sonuc;
    }

    @Test
    void bosKurulum_besAdimHepsiYapilmadi_siralamaSabit() throws Exception {
        Tarayici y = yonetici();
        y.get("/api/kurulum/durum").andExpect(status().isOk()).andExpect(jsonPath("$.adimlar.length()").value(5)).andExpect(jsonPath("$.tamam").value(false))
                .andExpect(jsonPath("$.gizli").value(false)).andExpect(jsonPath("$.adimlar[0].kod").value("KURUM")).andExpect(jsonPath("$.adimlar[1].kod").value("TANIM"))
                .andExpect(jsonPath("$.adimlar[2].kod").value("MAKINIST")).andExpect(jsonPath("$.adimlar[3].kod").value("PROFIL")).andExpect(jsonPath("$.adimlar[4].kod").value("SEFER"));
        assertThat(adimlar(y)).containsExactly(false, false, false, false, false);
    }

    @Test
    void adimlarSirayla_yapildikca_true_olur_veTamamSonunda() throws Exception {
        Tarayici y = yonetici();

        y.put("/api/kurum", "{\"ad\":\"Örnek İşletme (demo)\"}");
        assertThat(adimlar(y)).containsExactly(true, false, false, false, false);

        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        TestTanimlari.aracTipi(jdbc, "YHT");
        assertThat(adimlar(y)).as("2 gar şart: yalnız 1 gar yetmez").containsExactly(true, false, false, false, false);
        TestTanimlari.gar(jdbc, "Eskişehir");
        assertThat(adimlar(y)).containsExactly(true, true, false, false, false);

        var v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        assertThat(adimlar(y)).containsExactly(true, true, true, false, false);
        jdbc.update("UPDATE makinist SET durum = 'PASIF' WHERE id = ?", m);
        assertThat(adimlar(y)).as("yalnız AKTIF makinist sayılır").containsExactly(true, true, false, false, false);
        jdbc.update("UPDATE makinist SET durum = 'AKTIF' WHERE id = ?", m);

        y.post("/api/profil/uygula", "{\"paket\":{\"format\":\"makinist-atama-profil\",\"profilSurumu\":1,\"ad\":\"Deneme\",\"parametreler\":{\"dinlenme.ev_saat\":\"10\"}},"
                + "\"secilen\":[\"PARAMETRE:dinlenme.ev_saat\"],\"neden\":\"deneme\"}").andExpect(status().isOk());
        assertThat(adimlar(y)).containsExactly(true, true, true, true, false);
        y.get("/api/kurulum/durum").andExpect(jsonPath("$.tamam").value(false));

        v.sefer("T1", LocalDate.of(2026, 10, 12), "08:00", "10:00");
        assertThat(adimlar(y)).containsExactly(true, true, true, true, true);
        y.get("/api/kurulum/durum").andExpect(jsonPath("$.tamam").value(true));
    }

    @Test
    void adimAyrintisi_veriyiAciklar_kisiselVeriYok() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kurum", "{\"ad\":\"Örnek İşletme (demo)\"}");
        new AtamaVeri(jdbc).makinist("D001");
        String govde = y.get("/api/kurulum/durum").andReturn().getResponse().getContentAsString();
        assertThat(govde).contains("aktif makinist").doesNotContain("D001").doesNotContain("Demo Makinist");
    }

    @Test
    void sihirbazGizle_kalicidir_veGeriAlinir() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kurulum/sihirbaz-gizle", "{\"gizli\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.gizli").value(true));
        y.get("/api/kurulum/durum").andExpect(jsonPath("$.gizli").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'KURUM_GUNCELLE'", Integer.class)).isEqualTo(1);
        y.put("/api/kurulum/sihirbaz-gizle", "{\"gizli\":false}").andExpect(jsonPath("$.gizli").value(false));
    }

    @Test
    void yetki_atamaci403_makinist403_anonim401() throws Exception {
        Tarayici anonim = new Tarayici(mvc);
        Tarayici atamaci = atamaci();
        Tarayici makinist = makinistGirisli(new AtamaVeri(jdbc).makinist("D001"), "D001");
        for (Tarayici t : new Tarayici[] { anonim, atamaci, makinist }) {
            int beklenen = t == anonim ? 401 : 403;
            assertThat(t.get("/api/kurulum/durum").andReturn().getResponse().getStatus()).isEqualTo(beklenen);
            assertThat(t.put("/api/kurulum/sihirbaz-gizle", "{\"gizli\":true}").andReturn().getResponse().getStatus()).isEqualTo(beklenen);
        }
        assertThat(jdbc.queryForObject("SELECT sihirbaz_gizli FROM kurum", Boolean.class)).as("reddedilen istek tercihi değiştirmez").isFalse();
    }
}
