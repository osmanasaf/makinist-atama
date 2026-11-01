package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.Siddet;
import com.makinistatama.ortak.UygulamaHatasi;

/** T1.4 (MK-164): toplu yazma tüm-ya-hiç; nihai durum denetimi; değişmeyen için kayıt yok; kayıt biçimi tekli yazmayla aynı. */
class ParametreTopluUygulaTest extends TestVeritabani {

    @Autowired
    ParametreServisi parametreler;

    @Autowired
    AyarServisi ayarlar;

    private int kayitSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);
    }

    @Test
    void biriGecersiz_hicbiriYazilmaz() {
        assertThatThrownBy(() -> parametreler.topluUygula(Map.of("dinlenme.ev_saat", "12", "gece.gorev_orani", "1.5"), Map.of(), "n")).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> parametreler.topluUygula(Map.of("dinlenme.ev_saat", "12", "yok.anahtar", "1"), Map.of(), "n")).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> parametreler.topluUygula(Map.of("dinlenme.ev_saat", "12"), Map.of("K-10", Siddet.KAPALI), "n")).isInstanceOf(UygulamaHatasi.class)
                .hasMessageContaining("K-10");

        assertThat(parametreler.yukle().dakika("dinlenme.ev_saat")).isEqualTo(660);
        assertThat(kayitSayisi()).isZero();
    }

    @Test
    void bagimliIkiDegisiklikBirlikteKabul_ikiKayit_eskiYeniNeden() {
        // varsayılan: ayırma 4 s, dış merkez 11 s. Ayırma 12 > 11 tek başına reddedilirdi; dış merkez 12 ile birlikte tutarlı.
        var sonuc = parametreler.topluUygula(Map.of("nobet.ayirma_saat", "12", "dinlenme.dis_merkez_saat", "12"), Map.of(), "Profil: Deneme — nedenim");

        assertThat(sonuc.parametre()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'nobet.ayirma_saat'", String.class)).isEqualTo("12");
        var kayitlar = jdbc.queryForList("SELECT * FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' ORDER BY id");
        assertThat(kayitlar).hasSize(2);
        assertThat(kayitlar.get(0)).containsEntry("varlik_tipi", "PARAMETRE").containsEntry("neden_notu", "Profil: Deneme — nedenim");
        assertThat(kayitlar).anySatisfy(k -> assertThat(k).containsEntry("eski", "4").containsEntry("yeni", "12"));
    }

    @Test
    void aktarma20dk_ayirma0_2saat_PROFIL_GECERSIZ_hicbiriYazilmaz() {
        assertThatThrownBy(() -> parametreler.topluUygula(Map.of("nobet.ayirma_saat", "0.2", "gece.max_saat", "6"), Map.of(), "n")).isInstanceOfSatisfying(UygulamaHatasi.class, h -> {
            assertThat(h.kod()).isEqualTo("PROFIL_GECERSIZ");
            assertThat(h.durum().value()).isEqualTo(422);
            assertThat(h.getMessage()).contains("altında olmalı");
        });

        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'gece.max_saat'", String.class)).isEqualTo("7.5");
        assertThat(kayitSayisi()).isZero();
    }

    @Test
    void siddetVeDegerBirlikte_nihaiDurum_K14Acilirken() {
        // K-14 KAPALI iken normal 50 > üst sınır 45 aranmaz; ENGEL'e açılınca çelişir; üst sınır da yükseltilirse birlikte kabul
        assertThatThrownBy(() -> parametreler.topluUygula(Map.of("haftalik.normal_saat", "50"), Map.of("K-14", Siddet.ENGEL), "n")).isInstanceOf(UygulamaHatasi.class);
        var sonuc = parametreler.topluUygula(Map.of("haftalik.normal_saat", "50", "haftalik.max_saat", "55"), Map.of("K-14", Siddet.ENGEL), "n");

        assertThat(sonuc.parametre()).isEqualTo(2);
        assertThat(sonuc.siddet()).isEqualTo(1);
        assertThat(parametreler.yukle().siddet("K-14")).isEqualTo(Siddet.ENGEL);
    }

    @Test
    void degismeyenAnahtarIcinKayitYok_ayniSayiFarkliYazim() {
        var sonuc = parametreler.topluUygula(Map.of("dinlenme.ev_saat", "11", "yedek.oran", "0.1"), Map.of("K-20", Siddet.UYARI), "n");

        assertThat(sonuc.parametre()).isZero();
        assertThat(sonuc.siddet()).isZero();
        assertThat(kayitSayisi()).isZero();
    }

    @Test
    void oncedenVarOlanTutarsizlikProfileYuklenmez() {
        jdbc.update("UPDATE kural_parametre SET deger = '5' WHERE anahtar = 'dinlenme.dis_merkez_saat'");
        jdbc.update("UPDATE kural_parametre SET deger = '6' WHERE anahtar = 'nobet.ayirma_saat'"); // elle tutarsız: ayırma 6 > dış merkez 5

        var sonuc = parametreler.topluUygula(Map.of("dinlenme.ev_saat", "12"), Map.of(), "n"); // bağlı olmayan değişiklik engellenmez

        assertThat(sonuc.parametre()).isEqualTo(1);
    }

    @Test
    void ayarlarToplu_araligiAsanVarsaHicbiriYazilmaz_degisenIcinKayit() {
        assertThatThrownBy(() -> ayarlar.topluUygula(Map.of("gordum.kalem_gun", 10, "makinist.gorunur_gun", 9999), "n")).isInstanceOf(UygulamaHatasi.class);
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'gordum.kalem_gun'", Integer.class)).isEqualTo(7);
        assertThat(kayitSayisi()).isZero();

        int degisen = ayarlar.topluUygula(Map.of("gordum.kalem_gun", 10, "makinist.gorunur_gun", 7), "Profil: Deneme — n");

        assertThat(degisen).isEqualTo(1);
        var k = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi");
        assertThat(k).containsEntry("varlik_tipi", "AYAR").containsEntry("eski", "7").containsEntry("yeni", "10");
    }
}
