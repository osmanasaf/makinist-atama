package com.makinistatama.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T5.1: demo veri (dev profili). Prod'da uç yok: {@link DemoProdTest}. */
@ActiveProfiles("dev")
class DemoVeriTest extends TestVeritabani {

    @Test
    void yukle_68makinist_3binSefer_sentinelSicil_hassasAlanYok() throws Exception {
        Tarayici y = yonetici();

        y.post("/api/demo/yukle", "").andExpect(status().isCreated()).andExpect(jsonPath("$.makinist").value(68));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class)).isEqualTo(68);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).isGreaterThanOrEqualTo(3000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE sicil !~ '^D[0-9]{3}$'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE ad_soyad !~ '^Demo Makinist [0-9]{2}$'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tanim WHERE tip = 'GAR'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tanim WHERE tip = 'HAT'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tanim WHERE tip = 'ARAC_TIPI'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin", Integer.class)).isPositive();
        // UX-2: seferler yükleme gününden başlar (90 genel + 10 dizel)
        assertThat(jdbc.queryForObject("SELECT min(tarih) FROM sefer", LocalDate.class)).isEqualTo(LocalDate.now());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer WHERE tarih = (SELECT min(tarih) FROM sefer)", Integer.class)).isEqualTo(100);

        // TC kimlik / tanı / telefon alanı şemada da yok
        List<String> sutunlar = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name IN ('makinist','izin','brove_hat','brove_arac_tipi')", String.class);
        assertThat(sutunlar).noneMatch(s -> s.contains("tc") || s.contains("tani") || s.contains("telefon") || s.contains("kimlik"));
    }

    /**
     * Demo veri kapasitesi (QA M3: 40 makinistle 700 seferin 225'i boş kalıyordu; Ankara–Sivas'a yetkili 12 kişi vardı): yarından 7 gün için otomatik atama önizlemesi
     * seferlerin büyük çoğunluğunu doldurur ama hepsini değil; boş kalanlar nedenini taşır.
     */
    @Test
    void tipikHafta_otomatikAtama_cogunlukDolar_birkacSeferNedenleBosKalir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        LocalDate bas = LocalDate.now().plusDays(1);
        int sefer = jdbc.queryForObject("SELECT count(*) FROM sefer WHERE tarih BETWEEN ? AND ?", Integer.class, bas, bas.plusDays(6));
        assumeThat(sefer).as("ay sonuna yakın: haftalık pencere yok").isGreaterThanOrEqualTo(600);

        String yanit = y.post("/api/atama/otomatik", "{\"bas\":\"" + bas + "\",\"bit\":\"" + bas.plusDays(6) + "\",\"uygula\":false}").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        int atanan = JsonPath.read(yanit, "$.atananSayisi");
        int bos = JsonPath.read(yanit, "$.bosSayisi");
        assertThat(atanan + bos).isEqualTo(sefer);
        assertThat(atanan).as("büyük çoğunluk dolmalı").isGreaterThanOrEqualTo((int) (sefer * 0.90));
        assertThat(bos).as("birkaç sefer gerçekçi nedenle boş kalmalı").isBetween(1, (int) (sefer * 0.10));
        List<String> nedenler = JsonPath.read(yanit, "$.bosKalanlar[*].neden");
        assertThat(nedenler).allMatch(n -> n.startsWith("0/") && n.contains(" uygun"));
    }

    /**
     * UX-3 / OK-24: varsayılan K-14 KAPALI olduğundan (haftalık 45 s üstü engel değil, fazla mesai sayılır) demo hiçbir ayara dokunmadan dizel ekibi (8 kişi, günde 10 sefer)
     * 45 saati aşar. Dört haftalık otomatik atamadan sonra puantajda fazla mesaisi olan makinistler vardır.
     */
    @Test
    void dizelEkibi_fazlaMesaiUretir_demoAyarlaraDokunmaz() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'haftalik.max_saat'", String.class)).isEqualTo("45");
        LocalDate bas = LocalDate.now();
        for (int hafta = 0; hafta < 4; hafta++) {
            y.post("/api/atama/otomatik", "{\"bas\":\"" + bas.plusDays(7L * hafta) + "\",\"bit\":\"" + bas.plusDays(7L * hafta + 6) + "\",\"uygula\":true}").andExpect(status().isOk());
        }
        int fazlaMesaili = 0;
        for (java.time.YearMonth ay : new java.time.YearMonth[] { java.time.YearMonth.from(bas), java.time.YearMonth.from(bas.plusDays(27)) }) {
            String yanit = y.get("/api/rapor/puantaj?ay=" + ay).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Double> fazla = JsonPath.read(yanit, "$.satirlar[?(@.fazlaMesaiSaat > 0)].fazlaMesaiSaat");
            fazlaMesaili += fazla.size();
        }
        assertThat(fazlaMesaili).as("bazı makinistlerde fazla mesai görünmeli").isGreaterThanOrEqualTo(3);

        assertThat(demoAyarKaydi()).as("demo yükleme ayar kaydı üretmez").isZero();
        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(demoAyarKaydi()).as("demo sıfırlama da ayar kaydı üretmez").isZero();
    }

    /** MK-100: demo yüklemesi kural_siddet / kural_parametre / uygulama_ayar üzerinde yalnız puantaj oranlarını yazar; ayar kaydı (haftalık sınır, K-14, K-23) YOK. */
    @Test
    void demoYukleme_kuralAyarlarinaDokunmaz_varsayilanlarYerindeKalir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT siddet FROM kural_siddet WHERE kod = 'K-14'", String.class)).isEqualTo("KAPALI");
        assertThat(jdbc.queryForObject("SELECT siddet FROM kural_siddet WHERE kod = 'K-23'", String.class)).isEqualTo("ENGEL");
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'nobet.asgari_aktarma_dk'", String.class)).isEqualTo("20");
        assertThat(demoAyarKaydi()).isZero();
    }

    /** Haftalık sınır, kural şiddeti ve aktarma eşiği için kayıt (puantaj oranları hariç). */
    private int demoAyarKaydi() {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND (ayrinti LIKE '%haftalik%' OR ayrinti LIKE '%K-14%' OR ayrinti LIKE '%K-23%' OR ayrinti LIKE '%asgari_aktarma%')", Integer.class);
    }

    /** T14.2: puantaj oranları gerçek veri değildir; yalnız demo yüklemesinde dolar (200 TL, 0,40 TL), sıfırlamada boşalır; puantaj ekranı DEMO etiketi taşır. */
    @Test
    void puantajOranlari_yalnizDemoYuklemesindeDolu_sifirlamadaBosalir() throws Exception {
        Tarayici y = yonetici();
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'puantaj.saat_ucreti'", String.class)).isEmpty();

        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'puantaj.saat_ucreti'", String.class)).isEqualTo("200");
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'puantaj.km_birim_tutari'", String.class)).isEqualTo("0.4");
        y.get("/api/rapor/puantaj?ay=" + java.time.YearMonth.now()).andExpect(status().isOk()).andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.saatUcretiGirildi").value(true));

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'puantaj.saat_ucreti'", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'puantaj.km_birim_tutari'", String.class)).isEmpty();
    }

    @Test
    void ikinciYukleme_409_sifirlaSonrasiTekrarYuklenir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());

        y.post("/api/demo/yukle", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("DEMO_DOLU"));

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sefer", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tanim", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici", Integer.class)).isPositive(); // kullanıcı kalır
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kural_parametre", Integer.class)).isEqualTo(32);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isPositive(); // kayıt kalır

        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
    }

    @Test
    void durum_yukleOncesiYuklenebilir_sonrasiYuklu_UX1() throws Exception {
        Tarayici y = yonetici();
        y.get("/api/demo/durum").andExpect(status().isOk()).andExpect(jsonPath("$.yuklu").value(false));
        y.post("/api/demo/yukle", "").andExpect(status().isCreated());
        y.get("/api/demo/durum").andExpect(jsonPath("$.yuklu").value(true));
        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        y.get("/api/demo/durum").andExpect(jsonPath("$.yuklu").value(false));
    }

    /** F2a: demo ilk 3 makinist için hesap açar (geçici parola yalnız yanıtta, ilk girişte değiştirilmeli); sıfırlama hesapları siler, personel hesapları kalır. */
    @Test
    void sifirlaMakinistHesaplariniSiler() throws Exception {
        Tarayici y = yonetici();
        String yanit = y.post("/api/demo/yukle", "").andExpect(status().isCreated()).andExpect(jsonPath("$.hesaplar.length()").value(3))
                .andExpect(jsonPath("$.hesaplar[0].sicil").value("D001")).andReturn().getResponse().getContentAsString();
        String gecici = JsonPath.read(yanit, "$.hesaplar[0].geciciParola");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici WHERE rol = 'MAKINIST' AND parola_degistirmeli", Integer.class)).isEqualTo(3);
        Tarayici m = new Tarayici(mvc);
        m.giris("d001", gecici).andExpect(status().isOk());
        m.get("/api/auth/ben").andExpect(jsonPath("$.parolaDegistirmeli").value(true));

        y.post("/api/demo/sifirla", "").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici WHERE rol = 'MAKINIST'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kullanici WHERE rol = 'YONETICI'", Integer.class)).isPositive();
        y.post("/api/demo/yukle", "").andExpect(status().isCreated()); // yeniden yüklemede hesaplar çakışmaz
    }

    @Test
    void atamaci403() throws Exception {
        atamaci().get("/api/demo/durum").andExpect(status().isForbidden());
        atamaci().post("/api/demo/yukle", "").andExpect(status().isForbidden());
        atamaci().post("/api/demo/sifirla", "").andExpect(status().isForbidden());
    }
}
