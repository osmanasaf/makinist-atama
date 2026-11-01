package com.makinistatama.makinist;

import static com.makinistatama.makinist.TestMakinistExcel.makinist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** F1 / AK-1.2, 1.4, 1.6: makinist toplu içe aktarma (önizleme yazmaz, onay tek işlemde, ikinci yükleme atlar, yetki). */
class MakinistIceAktarTest extends TestVeritabani {

    @BeforeEach
    void tanimlar() {
        TestTanimlari.gar(jdbc, "Ankara");
        TestTanimlari.gar(jdbc, "Eskişehir");
        TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        TestTanimlari.aracTipi(jdbc, "YHT");
    }

    static List<Object[]> yuzSatir() {
        List<Object[]> l = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            l.add(makinist(String.format("M%03d", i), "Örnek Personel " + i, "Ankara"));
        }
        return l;
    }

    ResultActions yukle(Tarayici t, byte[] dosya, boolean onizle) throws Exception {
        return t.istek(MockMvcRequestBuilders.multipart("/api/makinist/ice-aktar")
                .file(new MockMultipartFile("dosya", "makinistler.xlsx", "application/octet-stream", dosya)).param("onizle", Boolean.toString(onizle)));
    }

    int makinistSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM makinist", Integer.class);
    }

    int kayit(String tur) {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = ?", Integer.class, tur);
    }

    @Test
    void onizleme_yuzGecerli_dbyeYazmaz() throws Exception {
        Tarayici y = yonetici();
        yukle(y, TestMakinistExcel.kitap(yuzSatir(), List.of(), List.of()), true).andExpect(status().isOk())
                .andExpect(jsonPath("$.onizleme").value(true)).andExpect(jsonPath("$.toplamSatir").value(100)).andExpect(jsonPath("$.eklenen").value(100))
                .andExpect(jsonPath("$.zatenVar").value(0)).andExpect(jsonPath("$.hataSayisi").value(0)).andExpect(jsonPath("$.satirlar[0].sicil").value("M001"))
                .andExpect(jsonPath("$.satirlar[0].durum").value("YENI")).andExpect(jsonPath("$.satirlar[0].bagliGar").value("Ankara"));
        assertThat(makinistSayisi()).as("önizleme yazmaz").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    @Test
    void onay_NmakinistVeTekOzetKaydi_broveYazilir() throws Exception {
        Tarayici y = yonetici();
        List<Object[]> m = new ArrayList<>(yuzSatir().subList(0, 3));
        yukle(y, TestMakinistExcel.kitap(m, List.<Object[]>of(new Object[] { "M001", "YHT", "01.03.2026" }),
                List.<Object[]>of(new Object[] { "M002", "Ankara–Eskişehir", "01.03.2026", "01.09.2026" })), false)
                .andExpect(status().isOk()).andExpect(jsonPath("$.onizleme").value(false)).andExpect(jsonPath("$.eklenen").value(3));

        assertThat(makinistSayisi()).isEqualTo(3);
        assertThat(kayit("MAKINIST_EKLE")).isEqualTo(3);
        assertThat(kayit("MAKINIST_ICE_AKTAR")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'MAKINIST_ICE_AKTAR'", String.class)).contains("\"eklenen\":3").contains("\"zatenVar\":0")
                .contains("\"hata\":0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM brove_arac_tipi", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM brove_hat", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tur FROM makinist WHERE sicil = 'M001'", String.class)).isEqualTo("MAKINIST");
    }

    @Test
    void ikinciYukleme_ayniDosya_eklenenSifir_zatenVarN_yeniKayitYok() throws Exception {
        Tarayici y = yonetici();
        byte[] dosya = TestMakinistExcel.kitap(yuzSatir().subList(0, 5), List.of(), List.of());
        yukle(y, dosya, false).andExpect(jsonPath("$.eklenen").value(5));
        int kayitOnce = jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);

        yukle(y, dosya, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(0)).andExpect(jsonPath("$.zatenVar").value(5))
                .andExpect(jsonPath("$.satirlar[0].durum").value("ZATEN_VAR"));
        assertThat(makinistSayisi()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).as("OK-45 Q1: güncelleme yok, yeni kayıt yok").isEqualTo(kayitOnce);
    }

    @Test
    void mevcutSicil_guncellenmez_bröveleriDeEklenmez() throws Exception {
        Tarayici y = yonetici();
        long mevcut = new AtamaVeri(jdbc).makinist("M001");
        int broveOnce = jdbc.queryForObject("SELECT count(*) FROM brove_arac_tipi", Integer.class);
        String adOnce = jdbc.queryForObject("SELECT ad_soyad FROM makinist WHERE id = ?", String.class, mevcut);

        yukle(y, TestMakinistExcel.kitap(List.<Object[]>of(makinist("M001", "Yeni Ad Soyad", "Eskişehir"), makinist("M002", "Veli", "Ankara")),
                List.<Object[]>of(new Object[] { "M001", "YHT", "01.03.2026" }), List.of()), false)
                .andExpect(jsonPath("$.eklenen").value(1)).andExpect(jsonPath("$.zatenVar").value(1));
        assertThat(jdbc.queryForObject("SELECT ad_soyad FROM makinist WHERE id = ?", String.class, mevcut)).isEqualTo(adOnce);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM brove_arac_tipi", Integer.class)).as("mevcut makinistin bröveleri güncellenmez").isEqualTo(broveOnce);
        assertThat(makinistSayisi()).isEqualTo(2);
    }

    @Test
    void ok46_broveHucresiHatali_makinistHicEklenmez_onizlemeVeSonucHataListeler() throws Exception {
        Tarayici y = yonetici();
        List<Object[]> m = new ArrayList<>(yuzSatir().subList(0, 3));
        byte[] dosya = TestMakinistExcel.kitap(m, List.<Object[]>of(new Object[] { "M001", "YHT", "31.02.2026" }), List.of());
        yukle(y, dosya, true).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.hataSayisi").value(2))
                .andExpect(jsonPath("$.satirlar[?(@.sicil == 'M001')]").isEmpty())
                .andExpect(jsonPath("$.hatalar[?(@.sayfa == 'Makinistler' && @.sicil == 'M001')]").isNotEmpty());
        yukle(y, dosya, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(2)).andExpect(jsonPath("$.hataSayisi").value(2));
        assertThat(makinistSayisi()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM makinist WHERE sicil = 'M001'", Integer.class)).as("hatalı bröve hücreli makinist DB'ye girmez").isZero();
    }

    @Test
    void hataliSatirlarAtlanir_gecerliler_yazilir_toplamTutar() throws Exception {
        Tarayici y = yonetici();
        List<Object[]> m = new ArrayList<>(yuzSatir().subList(0, 4));
        m.set(1, makinist("M002", "Veli", "Sivas")); // bilinmeyen gar
        m.set(3, makinist(null, "Sicilsiz", "Ankara"));
        yukle(y, TestMakinistExcel.kitap(m, List.of(), List.of()), false).andExpect(status().isOk()).andExpect(jsonPath("$.toplamSatir").value(4)).andExpect(jsonPath("$.eklenen").value(2))
                .andExpect(jsonPath("$.hataSayisi").value(2)).andExpect(jsonPath("$.hatalar[0].sayfa").value("Makinistler")).andExpect(jsonPath("$.hatalar[0].satirNo").value(3))
                .andExpect(jsonPath("$.hatalar[0].neden").value(containsString("Sivas"))).andExpect(jsonPath("$.eksikTanimlar[0].tip").value("GAR"))
                .andExpect(jsonPath("$.eksikTanimlar[0].ad").value("Sivas"));
        assertThat(makinistSayisi()).isEqualTo(2);
    }

    @Test
    void belgeUyarisi_satirdaDoner() throws Exception {
        Tarayici y = yonetici();
        yukle(y, TestMakinistExcel.kitap(List.<Object[]>of(makinist("M001", "Ayşe", null, "Ankara", null, "01.01.2026", "31.12.2030", "31.12.2030", null)), List.of(), List.of()), true)
                .andExpect(jsonPath("$.uyariSayisi").value(1)).andExpect(jsonPath("$.satirlar[0].uyarilar[0]").value(containsString("süresi dolmuş")));
    }

    @Test
    void gecersizDosya_400_veSablonIndirilir() throws Exception {
        Tarayici y = yonetici();
        yukle(y, "merhaba".getBytes(), true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        var sablon = y.get("/api/makinist/ice-aktar/sablon").andReturn().getResponse();
        assertThat(sablon.getStatus()).isEqualTo(200);
        assertThat(sablon.getHeader("Content-Disposition")).contains("makinist-sablonu.xlsx");
        assertThat(sablon.getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void yarisSicilTekrari_409_hicbirSeyYazilmaz() throws Exception {
        // onizleme sırasında yok, onay sırasında başkası aynı sicili eklemiş: tüm işlem geri alınır
        Tarayici y = yonetici();
        byte[] dosya = TestMakinistExcel.kitap(yuzSatir().subList(0, 3), List.of(), List.of());
        jdbc.update("CREATE OR REPLACE FUNCTION test_yaris() RETURNS trigger AS $$ BEGIN IF NEW.sicil = 'M003' THEN RAISE unique_violation USING MESSAGE = 'yaris'; END IF; RETURN NEW; END $$ LANGUAGE plpgsql");
        jdbc.update("CREATE TRIGGER test_yaris_tg BEFORE INSERT ON makinist FOR EACH ROW EXECUTE FUNCTION test_yaris()");
        try {
            yukle(y, dosya, false).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("SICIL_TEKRARI"));
        } finally {
            jdbc.update("DROP TRIGGER test_yaris_tg ON makinist");
            jdbc.update("DROP FUNCTION test_yaris()");
        }
        assertThat(makinistSayisi()).isZero();
        assertThat(kayit("MAKINIST_ICE_AKTAR")).isZero();
        assertThat(kayit("MAKINIST_EKLE")).isZero();
    }

    /** AK-1.4 ölçümü (plan T2.11): 500 satırlık dosya (araç tipi + hat bröveli) onayı en çok 10 sn; süre çıktıya yazılır. */
    @Test
    void besYuzSatir_onay_10saniyeyiAsmaz() throws Exception {
        List<Object[]> m = new ArrayList<>();
        List<Object[]> arac = new ArrayList<>();
        List<Object[]> hat = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            String sicil = String.format("P%04d", i);
            m.add(makinist(sicil, "Örnek Personel " + i, "Ankara"));
            arac.add(new Object[] { sicil, "YHT", "01.03.2026" });
            hat.add(new Object[] { sicil, "Ankara–Eskişehir", "01.03.2026", "01.09.2026" });
        }
        byte[] dosya = TestMakinistExcel.kitap(m, arac, hat);
        Tarayici y = yonetici();
        long bas = System.nanoTime();
        yukle(y, dosya, false).andExpect(status().isOk()).andExpect(jsonPath("$.eklenen").value(500));
        long ms = (System.nanoTime() - bas) / 1_000_000;
        System.out.println("MAKINIST-ICE-AKTAR-500: onay " + ms + " ms");
        assertThat(ms).as("500 satır onayı (ms)").isLessThan(10_000);
        assertThat(makinistSayisi()).isEqualTo(500);
        assertThat(kayit("MAKINIST_EKLE")).isEqualTo(500);
    }

    @Test
    void yetki_atamaci403_makinist403_anonim401_ucUcDe() throws Exception {
        byte[] dosya = TestMakinistExcel.kitap(yuzSatir().subList(0, 1), List.of(), List.of());
        Tarayici anonim = new Tarayici(mvc);
        Tarayici atamaci = atamaci();
        Tarayici makinist = makinistGirisli(new AtamaVeri(jdbc).makinist("D001"), "D001");
        int once = makinistSayisi();
        for (boolean onizle : new boolean[] { true, false }) {
            yukle(anonim, dosya, onizle).andExpect(status().isUnauthorized());
            yukle(atamaci, dosya, onizle).andExpect(status().isForbidden());
            yukle(makinist, dosya, onizle).andExpect(status().isForbidden());
        }
        assertThat(anonim.get("/api/makinist/ice-aktar/sablon").andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(atamaci.get("/api/makinist/ice-aktar/sablon").andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(makinist.get("/api/makinist/ice-aktar/sablon").andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(makinistSayisi()).as("reddedilen yükleme yazmaz").isEqualTo(once);
    }
}
