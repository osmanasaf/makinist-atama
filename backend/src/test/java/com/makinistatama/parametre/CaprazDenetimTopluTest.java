package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Siddet;

/** T1.4 (MK-164, saf): nihai durum denetimi. Bağımlı iki değişiklik BİRLİKTE kabul edilir; tek tek sırayla yazılsa ilk adım yanlış reddedilirdi. */
class CaprazDenetimTopluTest {

    /** Başlangıç: ayırma 4 s, dış merkez 8 s, normal 45, üst sınır 45, aktarma 20 dk, gece 7,5 s, günlük 11 s. */
    private static Map<String, Double> deger(Map<String, Double> degisen) {
        Map<String, Double> d = new HashMap<>(Map.of("nobet.ayirma_saat", 4.0, "dinlenme.dis_merkez_saat", 8.0, "haftalik.normal_saat", 45.0, "haftalik.max_saat", 45.0,
                "nobet.asgari_aktarma_dk", 20.0, "gece.max_saat", 7.5, "gunluk.max_saat", 11.0));
        d.putAll(degisen);
        return d;
    }

    private static final Map<String, Siddet> HEPSI_ACIK = Map.of("K-14", Siddet.ENGEL, "K-23", Siddet.ENGEL);

    @Test
    void bagimliIkiDegisiklikBirlikteKabul_tekTekSiraylaIlkAdimRedderdi() {
        Map<String, Double> nihai = deger(Map.of("nobet.ayirma_saat", 10.0, "dinlenme.dis_merkez_saat", 12.0));

        assertThat(CaprazDenetim.tumu(nihai, HEPSI_ACIK)).as("4->10 ve 8->12 birlikte tutarlı").isEmpty();
        // aynı iki değişiklik sırayla yazılsaydı: önce ayırma 10 (dış merkez hâlâ 8) tek-anahtar denetimince reddedilirdi
        assertThat(CaprazDenetim.parametre("nobet.ayirma_saat", 10, deger(Map.of())::get, k -> Siddet.ENGEL)).contains("dış merkez dinlenme süresini (8 s) aşamaz");
    }

    @Test
    void aktarma20dk_ayirma0_2saat_cakisir() {
        var cakismalar = CaprazDenetim.tumu(deger(Map.of("nobet.ayirma_saat", 0.2)), HEPSI_ACIK);

        assertThat(cakismalar).hasSize(1);
        assertThat(cakismalar.get(0).kucuk()).isEqualTo("nobet.asgari_aktarma_dk");
        assertThat(cakismalar.get(0).buyuk()).isEqualTo("nobet.ayirma_saat");
        assertThat(cakismalar.get(0).sart()).isEqualTo("K-23");
        assertThat(cakismalar.get(0).mesaj()).contains("nöbet ayırma süresinin (12 dk) altında olmalı");
    }

    @Test
    void sartKapaliyken_aranmaz_sifirAktarmaAranmaz() {
        Map<String, Double> d = deger(Map.of("nobet.ayirma_saat", 0.2));
        assertThat(CaprazDenetim.tumu(d, Map.of("K-14", Siddet.ENGEL, "K-23", Siddet.KAPALI))).as("K-23 kapalı").isEmpty();
        assertThat(CaprazDenetim.tumu(deger(Map.of("nobet.ayirma_saat", 0.2, "nobet.asgari_aktarma_dk", 0.0)), HEPSI_ACIK)).as("0 = aranmaz").isEmpty();
        assertThat(CaprazDenetim.tumu(deger(Map.of("haftalik.max_saat", 40.0)), Map.of("K-14", Siddet.KAPALI, "K-23", Siddet.ENGEL))).as("K-14 kapalı").isEmpty();
        assertThat(CaprazDenetim.tumu(deger(Map.of("haftalik.max_saat", 40.0)), Map.of("K-14", Siddet.UYARI, "K-23", Siddet.ENGEL))).as("K-14 UYARI: kural devrede").hasSize(1);
    }

    @Test
    void tekAnahtarDenetimiyleAyniKurallar_sinirDegerleri() {
        assertThat(CaprazDenetim.tumu(deger(Map.of("nobet.ayirma_saat", 8.0)), HEPSI_ACIK)).as("ayırma = dış merkez: tutarlı").isEmpty();
        assertThat(CaprazDenetim.tumu(deger(Map.of("nobet.ayirma_saat", 8.5)), HEPSI_ACIK)).hasSize(1);
        assertThat(CaprazDenetim.tumu(deger(Map.of("nobet.asgari_aktarma_dk", 240.0)), HEPSI_ACIK)).as("aktarma = ayırma dakikası: kesin küçük olmalı").hasSize(1);
        assertThat(CaprazDenetim.tumu(deger(Map.of("nobet.asgari_aktarma_dk", 239.0)), HEPSI_ACIK)).isEmpty();
        assertThat(CaprazDenetim.tumu(deger(Map.of("gece.max_saat", 11.5)), HEPSI_ACIK)).as("gece > günlük").hasSize(1);
        assertThat(CaprazDenetim.tumu(Map.of(), HEPSI_ACIK)).as("haritada olmayan ayarın kuralı atlanır").isEmpty();
    }
}
