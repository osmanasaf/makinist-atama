package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.Siddet;

/** T7.1: parametre ve şiddet yazma (YONETICI, neden zorunlu, sınır doğrulaması, kayıt). Tohum her testten önce geri yüklenir (TestVeritabani). */
class ParametreTest extends TestVeritabani {

    @Autowired
    ParametreServisi servis;

    private static String govde(String deger, String neden) {
        return "{\"deger\":\"" + deger + "\",\"neden\":\"" + neden + "\"}";
    }

    @Test
    void degerDegisir_kayitEskiYeniKimNeden_veSonrakiYukleGorur() throws Exception {
        yonetici().put("/api/parametre/dinlenme.ev_saat", govde("8", "TİS değişti")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deger").value("8")).andExpect(jsonPath("$.kaynak").isNotEmpty());

        assertThat(servis.yukle().dakika("dinlenme.ev_saat")).isEqualTo(480);
        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI'");
        assertThat(kayit).containsEntry("eski", "11").containsEntry("yeni", "8").containsEntry("kullanici_adi", "Yöne Tici")
                .containsEntry("neden_notu", "TİS değişti").containsEntry("varlik_tipi", "PARAMETRE");
        assertThat((String) kayit.get("ayrinti")).contains("dinlenme.ev_saat");
    }

    @Test
    void ayniDeger_kayitUretmez() throws Exception {
        yonetici().put("/api/parametre/dinlenme.ev_saat", govde("11", "aynı")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    /** Review W2 (sunucu yanı): tohum "0.10" iken "0.1" yazmak sayısal olarak aynı değerdir; sahte "0.10 -> 0.1" denetim kaydı üretmez. */
    @Test
    void ayniSayiFarkliYazim_kayitUretmez() throws Exception {
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'yedek.oran'", String.class)).isEqualTo("0.10");
        yonetici().put("/api/parametre/yedek.oran", govde("0.1", "aynı sayı")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    @Test
    void atamaci_yazamaz_403() throws Exception {
        atamaci().put("/api/parametre/dinlenme.ev_saat", govde("8", "x")).andExpect(status().isForbidden());
        atamaci().put("/api/kural/K-20/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"x\"}").andExpect(status().isForbidden());

        assertThat(servis.yukle().siddet("K-20")).isEqualTo(Siddet.UYARI);
    }

    @Test
    void nedenBos_400_degerDegismez() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/dinlenme.ev_saat", govde("8", "  ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.neden").exists());
        y.put("/api/kural/K-20/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.neden").exists());

        assertThat(servis.yukle().dakika("dinlenme.ev_saat")).isEqualTo(660);
    }

    @Test
    void gecersizDegerler_400() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/gece.gorev_orani", govde("1.5", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.deger").exists());
        y.put("/api/parametre/dinlenme.ev_saat", govde("-1", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/dinlenme.ev_saat", govde("abc", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/dinlenme.ev_saat", govde("", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/gece.baslangic", govde("25:00", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/gece.baslangic", govde("8:00", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/belge.sinav_gecerlilik_ay", govde("60.5", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/nobet.bekleme_sayilir", govde("2", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/adalet.olcut", govde("RASTGELE", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/yok.boyle.bir.anahtar", govde("1", "x")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }

    @Test
    void gecerliDegerler_kanonikSaklanir() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/gece.baslangic", govde("21:30", "x")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value("21:30"));
        y.put("/api/parametre/gece.gorev_orani", govde("0,75", "x")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value("0.75"));
        y.put("/api/parametre/gece.max_saat", govde("8.0", "x")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value("8"));
        y.put("/api/parametre/puantaj.saat_ucreti", govde("", "boş bırak")).andExpect(status().isOk());
        y.put("/api/parametre/puantaj.saat_ucreti", govde("120.50", "x")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value("120.5"));
        y.put("/api/parametre/adalet.olcut", govde("km", "x")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value("KM"));

        assertThat(servis.yukle().saat("gece.baslangic").toString()).isEqualTo("21:30");
    }

    @Test
    void nobetAyirma_disMerkezDinlenmeyiAsamaz_veTersi() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/nobet.ayirma_saat", govde("12", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.deger").exists());
        y.put("/api/parametre/dinlenme.dis_merkez_saat", govde("3", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/nobet.ayirma_saat", govde("11", "x")).andExpect(status().isOk());
    }

    @Test
    void haftalikUstSinir_normalSureninAltinaDusemez_veTersi() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kural/K-14/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"kuralın kendisi sınanıyor\"}").andExpect(status().isOk()); // OK-24: varsayılan KAPALI iken bu çapraz denetim aranmaz
        y.put("/api/parametre/haftalik.max_saat", govde("40", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.deger").exists());
        y.put("/api/parametre/haftalik.normal_saat", govde("50", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/haftalik.max_saat", govde("50", "x")).andExpect(status().isOk());
        y.put("/api/parametre/haftalik.normal_saat", govde("50", "x")).andExpect(status().isOk());
    }

    /** OK-24 / MK-96: aktarma süresi nöbet ayırma süresinin dakikasından küçük olmalı (4 s -> 239 dk kabul, 240 ve 241 red). */
    @Test
    void aktarma_ayirmaSaatinAltinda_239Kabul_240Red() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/parametre/nobet.asgari_aktarma_dk", govde("239", "x")).andExpect(status().isOk());
        y.put("/api/parametre/nobet.asgari_aktarma_dk", govde("240", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.deger").value("Aktarma süresi nöbet ayırma süresinin (240 dk) altında olmalı."));
        y.put("/api/parametre/nobet.asgari_aktarma_dk", govde("241", "x")).andExpect(status().isBadRequest());
        y.put("/api/parametre/nobet.ayirma_saat", govde("3", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.deger").exists()); // 239 dk >= 180
        y.put("/api/parametre/nobet.asgari_aktarma_dk", govde("0", "x")).andExpect(status().isOk());
        y.put("/api/parametre/nobet.ayirma_saat", govde("3", "x")).andExpect(status().isOk());
    }

    /** Review INFO: K-23 KAPALIyken aktarma ↔ ayırma çapraz denetimi aranmaz; K-23 açılırken çelişki varsa reddedilir. */
    @Test
    void aktarma_K23Kapaliyken_aranmaz_acilirkenReddedilir() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kural/K-23/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"aktarma aranmasın\"}").andExpect(status().isOk());
        y.put("/api/parametre/nobet.asgari_aktarma_dk", govde("240", "x")).andExpect(status().isOk()); // ayırma 4 s = 240 dk: K-23 açıkken red
        y.put("/api/kural/K-23/siddet", "{\"siddet\":\"UYARI\",\"neden\":\"geri\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.siddet").value(org.hamcrest.Matchers.containsString("Önce aktarma süresini düşürün")));
        assertThat(servis.yukle().siddet("K-23")).isEqualTo(Siddet.KAPALI);
        y.put("/api/parametre/nobet.asgari_aktarma_dk", govde("20", "x")).andExpect(status().isOk());
        y.put("/api/kural/K-23/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"geri\"}").andExpect(status().isOk());
    }

    /** AC-7: K-14 açılırken haftalık üst sınır normal süreden kısaysa reddedilir; kapalıyken normal süre üst sınırı aşabilir. */
    @Test
    void k14_kapaliyken_normalUstSiniriAsabilir_acilirkenReddedilir() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kural/K-14/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"fazla mesai serbest\"}").andExpect(status().isOk());
        y.put("/api/parametre/haftalik.normal_saat", govde("48", "x")).andExpect(status().isOk());

        y.put("/api/kural/K-14/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"geri\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.siddet").value(org.hamcrest.Matchers.containsString("normal haftalık süreden (48 s) kısa olamaz")));
        assertThat(servis.yukle().siddet("K-14")).isEqualTo(Siddet.KAPALI);
        y.put("/api/parametre/haftalik.max_saat", govde("48", "x")).andExpect(status().isOk());
        y.put("/api/kural/K-14/siddet", "{\"siddet\":\"ENGEL\",\"neden\":\"geri\"}").andExpect(status().isOk());
    }

    @Test
    void siddet_degisir_kayitlanir_k10Degismez() throws Exception {
        Tarayici y = yonetici();
        y.put("/api/kural/K-20/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"konum verisi güvenilmez\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.siddet").value("KAPALI"));
        y.put("/api/kural/K-10/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"x\"}").andExpect(status().isBadRequest());
        y.put("/api/kural/K-99/siddet", "{\"siddet\":\"UYARI\",\"neden\":\"x\"}").andExpect(status().isNotFound());
        y.put("/api/kural/K-20/siddet", "{\"siddet\":\"BILINMEZ\",\"neden\":\"x\"}").andExpect(status().isBadRequest());

        assertThat(servis.yukle().siddet("K-20")).isEqualTo(Siddet.KAPALI);
        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI'");
        assertThat(kayit).containsEntry("eski", "UYARI").containsEntry("yeni", "KAPALI").containsEntry("varlik_tipi", "KURAL_SIDDET")
                .containsEntry("neden_notu", "konum verisi güvenilmez");
        assertThat((String) kayit.get("ayrinti")).contains("K-20");
    }

    /** M2 review W1: üst sınırı olmayan sayılarda (TL alanları) büyüklük denetlenir; 1E40000000 40 MB metin olarak saklanıyordu. */
    @Test
    void ustSinirsizSayilar_asiriBuyukVeAsiriOndalik_400_degerDegismez() throws Exception {
        Tarayici y = yonetici();
        for (String asiri : new String[] { "1E40000000", "99999999999999999999", "1000000001", "1E-40000000", "0.0000001" }) {
            y.put("/api/parametre/puantaj.saat_ucreti", govde(asiri, "x")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.alanlar.deger").exists());
            y.put("/api/parametre/puantaj.km_birim_tutari", govde(asiri, "x")).andExpect(status().isBadRequest());
        }
        y.put("/api/parametre/puantaj.saat_ucreti", govde("1000000000", "üst sınır dahil")).andExpect(status().isOk());
        y.put("/api/parametre/puantaj.km_birim_tutari", govde("0,000001", "alt çözünürlük")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT length(deger) FROM kural_parametre WHERE anahtar = 'puantaj.saat_ucreti'", Integer.class)).isEqualTo(10);
    }

    /** Sınırlı alanda da üstel yazım kabul edilmez (büyüklük önce denetlenir, metin üretilmez). */
    @Test
    void sinirliAlanda_ustelYazim_400() throws Exception {
        yonetici().put("/api/parametre/dinlenme.ev_saat", govde("1E40000000", "x")).andExpect(status().isBadRequest());
        yonetici().put("/api/parametre/dinlenme.ev_saat", govde("1E-40000000", "x")).andExpect(status().isBadRequest());

        assertThat(servis.yukle().dakika("dinlenme.ev_saat")).isEqualTo(660);
    }

    /** "Mevcut aşırı değer de reddedilmeli": veritabanında zaten duran aşırı değer aynen yeniden gönderilince kabul edilmez, düzeltme geçer. */
    @Test
    void mevcutAsiriDeger_aynenYenidenKabulEdilmez_duzeltmeGecer() throws Exception {
        jdbc.update("UPDATE kural_parametre SET deger = '99999999999999999999' WHERE anahtar = 'puantaj.saat_ucreti'");
        Tarayici y = yonetici();

        y.put("/api/parametre/puantaj.saat_ucreti", govde("99999999999999999999", "aynısı")).andExpect(status().isBadRequest());
        y.put("/api/parametre/puantaj.saat_ucreti", govde("250", "düzeltme")).andExpect(status().isOk()).andExpect(jsonPath("$.deger").value("250"));

        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'puantaj.saat_ucreti'", String.class)).isEqualTo("250");
    }
}
