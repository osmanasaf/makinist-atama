package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.TestVeritabani;
import com.makinistatama.Tarayici;
import com.makinistatama.kullanici.KullaniciIstekleri;
import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.kullanici.Rol;

class AuthTest extends TestVeritabani {

    static final String EPOSTA = "yonetici@ornek.test";
    static final String PAROLA = Tarayici.PAROLA;

    @Autowired
    KullaniciServisi kullanicilar;

    long yoneticiId;

    @BeforeEach
    void yoneticiOlustur() {
        yoneticiId = kullanicilar.olustur(new KullaniciIstekleri.Olustur(EPOSTA, "Yöne Tici", Rol.YONETICI, PAROLA)).id();
    }

    // ------------------------------------------------------------------ giriş

    @Test
    void dogruParola_200_oturumAcilir_sozlesmeAnahtarlari() throws Exception {
        Tarayici t = new Tarayici(mvc);

        t.giris(EPOSTA, PAROLA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(yoneticiId))
                .andExpect(jsonPath("$.eposta").value(EPOSTA))
                .andExpect(jsonPath("$.adSoyad").value("Yöne Tici"))
                .andExpect(jsonPath("$.rol").value("YONETICI"))
                .andExpect(jsonPath("$.aktif").value(true))
                .andExpect(jsonPath("$.parola").doesNotExist())
                .andExpect(jsonPath("$.parolaKarma").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))));

        t.get("/api/auth/ben")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(yoneticiId))
                .andExpect(jsonPath("$.eposta").value(EPOSTA))
                .andExpect(jsonPath("$.adSoyad").value("Yöne Tici"))
                .andExpect(jsonPath("$.rol").value("YONETICI"))
                .andExpect(jsonPath("$.aktif").value(true))
                .andExpect(jsonPath("$.parolaKarma").doesNotExist());
    }

    @Test
    void giris_epostaBuyukKucukHarfeDuyarsiz() throws Exception {
        new Tarayici(mvc).giris("  YONETICI@Ornek.TEST ", PAROLA).andExpect(status().isOk());
    }

    @Test
    void giris_oturumKimligiDegisir() throws Exception {
        Tarayici t = new Tarayici(mvc);
        t.oturum = new MockHttpSession(); // girişten önce var olan (saldırganın verdiği) oturum
        String eski = t.oturum.getId();

        t.giris(EPOSTA, PAROLA).andExpect(status().isOk());

        assertThat(t.oturum).isNotNull();
        assertThat(t.oturum.getId()).isNotEqualTo(eski);
    }

    @Test
    void yanlisParola_401_genelMesaj_varOlmayanEpostaylaAyniYanit() throws Exception {
        Tarayici t = new Tarayici(mvc);
        String yanlis = t.giris(EPOSTA, "yanlis-parola-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.kod").value("GIRIS_HATALI"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn().getResponse().getContentAsString();
        String yok = t.giris("yok@ornek.test", "yanlis-parola-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.kod").value("GIRIS_HATALI"))
                .andReturn().getResponse().getContentAsString();

        assertThat(yok).isEqualTo(yanlis);
        t.get("/api/auth/ben").andExpect(status().isUnauthorized());
    }

    @Test
    void bosAlanlar_400_dogrulama() throws Exception {
        new Tarayici(mvc).post("/api/auth/giris", "{\"eposta\":\"\",\"parola\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kod").value("DOGRULAMA"))
                .andExpect(jsonPath("$.alanlar.eposta").value("E-posta boş olamaz."))
                .andExpect(jsonPath("$.alanlar.parola").value("Parola boş olamaz."));
    }

    @Test
    void pasifKullanici_giremez() throws Exception {
        long id = kullanicilar.olustur(new KullaniciIstekleri.Olustur("pasif@ornek.test", "Pasif", Rol.ATAMACI, PAROLA)).id();
        kullanicilar.guncelle(id, new KullaniciIstekleri.Guncelle("Pasif", Rol.ATAMACI, false));

        new Tarayici(mvc).giris("pasif@ornek.test", PAROLA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.kod").value("GIRIS_HATALI"));
    }

    @Test
    void parola72BaytKabulEdilir() throws Exception {
        String uzun = "a".repeat(72);
        kullanicilar.olustur(new KullaniciIstekleri.Olustur("uzun@ornek.test", "Uzun", Rol.ATAMACI, uzun));

        new Tarayici(mvc).giris("uzun@ornek.test", uzun).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ kaba kuvvet kilidi

    @Test
    void besHatadanSonra_dogruParolaBile429_kilitSuresiDolunca_acilir() throws Exception {
        Tarayici t = new Tarayici(mvc);
        for (int i = 0; i < 5; i++) {
            t.giris(EPOSTA, "yanlis-parola-1").andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE id = ?", Integer.class, yoneticiId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT kilit_bitis > now() FROM kullanici WHERE id = ?", Boolean.class, yoneticiId)).isTrue();

        t.giris(EPOSTA, PAROLA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.kod").value("COK_FAZLA_DENEME"));
        t.get("/api/auth/ben").andExpect(status().isUnauthorized());

        // kilit süresi doldu: doğru parola girer ve sayaç sıfırlanır
        jdbc.update("UPDATE kullanici SET kilit_bitis = now() - interval '1 minute' WHERE id = ?", yoneticiId);
        t.giris(EPOSTA, PAROLA).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE id = ?", Integer.class, yoneticiId)).isZero();
        assertThat(jdbc.queryForObject("SELECT kilit_bitis FROM kullanici WHERE id = ?", java.sql.Timestamp.class, yoneticiId)).isNull();
    }

    @Test
    void kilitSuresiDolduktanSonra_yanlisParola_sayaciBastanBaslatir() throws Exception {
        jdbc.update("UPDATE kullanici SET basarisiz_giris = 5, kilit_bitis = now() - interval '1 minute' WHERE id = ?", yoneticiId);

        new Tarayici(mvc).giris(EPOSTA, "yanlis-parola-1").andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE id = ?", Integer.class, yoneticiId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT kilit_bitis FROM kullanici WHERE id = ?", java.sql.Timestamp.class, yoneticiId)).isNull();
    }

    @Test
    void basariliGiris_sayaciSifirlar() throws Exception {
        Tarayici t = new Tarayici(mvc);
        for (int i = 0; i < 4; i++) {
            t.giris(EPOSTA, "yanlis-parola-1").andExpect(status().isUnauthorized());
        }
        t.giris(EPOSTA, PAROLA).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE id = ?", Integer.class, yoneticiId)).isZero();

        Tarayici t2 = new Tarayici(mvc);
        for (int i = 0; i < 4; i++) {
            t2.giris(EPOSTA, "yanlis-parola-1").andExpect(status().isUnauthorized());
        }
        t2.giris(EPOSTA, PAROLA).andExpect(status().isOk()); // 8 toplam hata, ama ardışık değil: kilit yok
    }

    @Test
    void varOlmayanEposta_sayilmaz_baskasininSayacinaDokunmaz() throws Exception {
        Tarayici t = new Tarayici(mvc);
        for (int i = 0; i < 7; i++) {
            t.giris("yok@ornek.test", "yanlis-parola-1").andExpect(status().isUnauthorized()); // hiç 429 olmaz
        }
        assertThat(jdbc.queryForObject("SELECT basarisiz_giris FROM kullanici WHERE id = ?", Integer.class, yoneticiId)).isZero();
    }

    // ------------------------------------------------------------------ anonim erişim, CSRF, çıkış

    @Test
    void anonimBen_401_xsrfCerezi_oturumAcilmaz_wwwAuthenticateYok() throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/auth/ben"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.kod").value("OTURUM_YOK"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("HttpOnly")))) // JS okuyabilmeli
                .andReturn();

        assertThat(r.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotBlank();
        assertThat(r.getRequest().getSession(false)).as("anonim yoklama oturum açmamalı").isNull();
    }

    @Test
    void anonim_apiAltindakiHerYol401_gizlilikSizmaz() throws Exception {
        for (String yol : new String[] { "/api/yok", "/api/kullanicilar", "/api/sefer" }) {
            mvc.perform(MockMvcRequestBuilders.get(yol))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.kod").value("OTURUM_YOK"));
        }
    }

    @Test
    void saglik_anonimAcik_baskaActuatorUcNoktasiYok() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist()); // ayrıntı yok
        mvc.perform(MockMvcRequestBuilders.get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test
    void csrfsizPost_403_guvenlikTokeni() throws Exception {
        // belirteç hiç yok
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/giris").contentType("application/json")
                .content("{\"eposta\":\"%s\",\"parola\":\"%s\"}".formatted(EPOSTA, PAROLA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.kod").value("GUVENLIK_TOKENI"));

        // çerez var, başlık yanlış
        Tarayici t = new Tarayici(mvc);
        t.istek(MockMvcRequestBuilders.post("/api/auth/giris").contentType("application/json")
                .content("{\"eposta\":\"%s\",\"parola\":\"%s\"}".formatted(EPOSTA, PAROLA))
                .header("X-XSRF-TOKEN", "baska-bir-deger"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.kod").value("GUVENLIK_TOKENI"));
    }

    @Test
    void girisYapmisKullanici_csrfsizMutasyon_403_guvenlikTokeni() throws Exception {
        Tarayici t = Tarayici.girisYapmis(mvc, EPOSTA, PAROLA);

        t.istek(MockMvcRequestBuilders.post("/api/kullanicilar").contentType("application/json").content("{}")
                .header("X-XSRF-TOKEN", "yanlis"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.kod").value("GUVENLIK_TOKENI"));
    }

    @Test
    void cikis_204_oturumGecersiz_benArtik401() throws Exception {
        Tarayici t = Tarayici.girisYapmis(mvc, EPOSTA, PAROLA);
        MockHttpSession eski = t.oturum;

        t.post("/api/auth/cikis", "").andExpect(status().isNoContent());

        assertThat(eski.isInvalid()).isTrue();
        t.get("/api/auth/ben").andExpect(status().isUnauthorized());
        // eski oturum nesnesi geri verilse de geçersiz
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/ben").session(eski)).andExpect(status().isUnauthorized());
    }

    @Test
    void atamaci_yoneticiUcunda403_yonetici200() throws Exception {
        kullanicilar.olustur(new KullaniciIstekleri.Olustur("atamaci@ornek.test", "Ata Maci", Rol.ATAMACI, PAROLA));

        Tarayici atamaci = Tarayici.girisYapmis(mvc, "atamaci@ornek.test", PAROLA);
        atamaci.get("/api/kullanicilar").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("YETKISIZ"));

        Tarayici.girisYapmis(mvc, EPOSTA, PAROLA).get("/api/kullanicilar").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ açık oturumun iptali

    @Test
    void pasiflestirilenKullanicinin_acikOturumu_sonrakiIstekteDuser() throws Exception {
        long id = kullanicilar.olustur(new KullaniciIstekleri.Olustur("personel@ornek.test", "Per Sonel", Rol.ATAMACI, PAROLA)).id();
        Tarayici t = Tarayici.girisYapmis(mvc, "personel@ornek.test", PAROLA);
        t.get("/api/auth/ben").andExpect(status().isOk());

        kullanicilar.guncelle(id, new KullaniciIstekleri.Guncelle("Per Sonel", Rol.ATAMACI, false));

        t.get("/api/auth/ben").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.kod").value("OTURUM_YOK"));
    }

    @Test
    void parolaSifirlaninca_ayniKullanicininAcikOturumlariDuser_yeniParolaylaGirilir_digerKullanicilarEtkilenmez_tur2BEI6() throws Exception {
        // parola ele geçirildi diye yönetici sıfırladığında saldırganın açık oturumu 8 saat yaşamamalı
        long id = kullanicilar.olustur(new KullaniciIstekleri.Olustur("personel@ornek.test", "Per Sonel", Rol.ATAMACI, PAROLA)).id();
        kullanicilar.olustur(new KullaniciIstekleri.Olustur("baska@ornek.test", "Baska Kisi", Rol.ATAMACI, PAROLA));
        Tarayici sahibi = Tarayici.girisYapmis(mvc, "personel@ornek.test", PAROLA);
        Tarayici saldirgan = Tarayici.girisYapmis(mvc, "personel@ornek.test", PAROLA); // aynı hesapta ikinci açık oturum
        Tarayici baska = Tarayici.girisYapmis(mvc, "baska@ornek.test", PAROLA);
        sahibi.get("/api/auth/ben").andExpect(status().isOk());
        saldirgan.get("/api/auth/ben").andExpect(status().isOk());

        kullanicilar.parolaSifirla(id, "yeni-parola-12345");

        sahibi.get("/api/auth/ben").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.kod").value("OTURUM_YOK"));
        saldirgan.get("/api/auth/ben").andExpect(status().isUnauthorized());
        baska.get("/api/auth/ben").andExpect(status().isOk());
        new Tarayici(mvc).giris("personel@ornek.test", PAROLA).andExpect(status().isUnauthorized()); // eski parola geçmez
        Tarayici yeni = Tarayici.girisYapmis(mvc, "personel@ornek.test", "yeni-parola-12345");
        yeni.get("/api/auth/ben").andExpect(status().isOk()); // yeni parolayla açılan oturum sonraki istekte düşmez
        yeni.get("/api/auth/ben").andExpect(status().isOk());
    }

    @Test
    void rolDusurulunce_acikOturumdaYetkiHemenDegisir() throws Exception {
        long ikinci = kullanicilar.olustur(new KullaniciIstekleri.Olustur("ikinci@ornek.test", "Ikinci Yonetici", Rol.YONETICI, PAROLA)).id();
        Tarayici t = Tarayici.girisYapmis(mvc, "ikinci@ornek.test", PAROLA);
        t.get("/api/kullanicilar").andExpect(status().isOk());

        kullanicilar.guncelle(ikinci, new KullaniciIstekleri.Guncelle("Ikinci Yonetici", Rol.ATAMACI, true));

        t.get("/api/kullanicilar").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("YETKISIZ"));
        t.get("/api/auth/ben").andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ATAMACI"));
    }

    // ------------------------------------------------------------------ ilk yönetici tohumu

    @Test
    void baslangicYoneticisi_tabloBossa_olusturur_doluysaDokunmaz() throws Exception {
        jdbc.execute("TRUNCATE kullanici RESTART IDENTITY CASCADE");

        new BaslangicYoneticisi(kullanicilar, "  ILK@Ornek.Test ", "ilk-yonetici-parola-1").tohumla();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rol FROM kullanici WHERE eposta = 'ilk@ornek.test'", String.class)).isEqualTo("YONETICI");
        new Tarayici(mvc).giris("ilk@ornek.test", "ilk-yonetici-parola-1").andExpect(status().isOk());

        new BaslangicYoneticisi(kullanicilar, "baska@ornek.test", "baska-parola-12345").tohumla(); // tablo dolu
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici", Integer.class)).isEqualTo(1);
    }

    @Test
    void baslangicYoneticisi_tanimsizsaHicbirSeyYapmaz() {
        jdbc.execute("TRUNCATE kullanici RESTART IDENTITY CASCADE");

        new BaslangicYoneticisi(kullanicilar, "", "").tohumla();
        new BaslangicYoneticisi(kullanicilar, "a@b.test", "").tohumla();
        new BaslangicYoneticisi(kullanicilar, "a@b.test", "kisa").tohumla(); // geçersiz parola: oluşmaz, uygulama çökmez

        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici", Integer.class)).isZero();
    }
}
