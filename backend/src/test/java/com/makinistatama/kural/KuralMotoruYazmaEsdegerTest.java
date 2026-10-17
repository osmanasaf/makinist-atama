package com.makinistatama.kural;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.AltinDosya;
import com.makinistatama.kural.RastgeleKuralVeri.Senaryo;

/**
 * Performans işi (MK-57, E1) "önce/sonra aynı değerlendirme" kanıtı, DB'siz: (a) kural özeti altın dosyası, (b) eski {@code degerlendirYazma} gövdesi test içinde referans
 * ({@code simdi=null}'ın tek farkı K-22 olduğu varsayımını da bekçiler: bir kural {@code simdi}'yi okursa kırılır).
 */
public class KuralMotoruYazmaEsdegerTest {

    /** {@code degerlendirYazma}'nın optimizasyon ÖNCESİ gövdesi (birebir). */
    static List<Ihlal> eskiYazma(KuralBaglami b) {
        List<Ihlal> hepsi = KuralMotoru.degerlendir(b);
        List<Gorev> sorumlu = NobetHesabi.sorumluKomsular(b.digerGorevler(), b.aday(), b.parametreler().dakika("nobet.ayirma_saat"));
        List<Ihlal> canli = KuralMotoru.degerlendir(new KuralBaglami(b.aday(), sorumlu, b.makinist(), b.pencereOncesiHatSonSurus(), b.pencereOncesiGenelSonSurus(), b.tanimAdlari(),
                b.parametreler(), null));
        List<Ihlal> sonuc = new ArrayList<>(hepsi);
        for (Ihlal i : canli) {
            if (i.siddet() == Siddet.ENGEL && hepsi.stream().noneMatch(h -> h.siddet() == Siddet.ENGEL && h.kod().equals(i.kod()))) {
                sonuc.add(i);
            }
        }
        sonuc.sort(Comparator.comparing(Ihlal::kod));
        return sonuc;
    }

    static List<Senaryo> senaryolar() {
        return senaryolar(false);
    }

    /** {@code k14Engel}: eski varsayılan (K-14 ENGEL) varyantı; MK-5 eşdeğerliği her iki yapılandırmada da geçerli olmalı (OK-24). */
    static List<Senaryo> senaryolar(boolean k14Engel) {
        return RastgeleKuralVeri.uret(2026_10_14L, 160, 24, k14Engel);
    }

    @Test
    void degerlendirYazma_eskiGovdeyleAyniSonucuUretir() {
        eskiGovdeyleAyni(false);
    }

    @Test
    void degerlendirYazma_eskiGovdeyleAyniSonucuUretir_K14Engel() {
        eskiGovdeyleAyni(true);
    }

    private void eskiGovdeyleAyni(boolean k14Engel) {
        int bos = 0;
        int dolu = 0;
        int canliEkli = 0;
        for (Senaryo s : senaryolar(k14Engel)) {
            for (KuralBaglami b : s.baglamlar()) {
                List<Ihlal> beklenen = eskiYazma(b);
                assertThat(KuralMotoru.degerlendirYazma(b)).as("senaryo " + s.no() + " aday " + b.aday().seferId()).isEqualTo(beklenen);
                if (beklenen.isEmpty()) {
                    bos++;
                } else {
                    dolu++;
                }
                if (beklenen.size() > KuralMotoru.degerlendir(b).size()) {
                    canliEkli++;
                }
            }
        }
        assertThat(bos).as("duyarlılık: ihlalsiz adaylar var").isGreaterThan(100);
        assertThat(dolu).as("duyarlılık: ihlalli adaylar var").isGreaterThan(1000);
        assertThat(canliEkli).as("duyarlılık: canlı ENGEL ekleme dalı da sınanıyor").isGreaterThan(0);
        if (k14Engel) {
            assertThat(senaryolar(true).stream().flatMap(s -> s.baglamlar().stream()).flatMap(b -> KuralMotoru.degerlendir(b).stream()).anyMatch(i -> i.kod().equals("K-14") && i.siddet() == Siddet.ENGEL))
                    .as("duyarlılık: K-14 ENGEL üretiliyor").isTrue();
        }
    }

    /** Kural motorunun bugünkü çıktısının özeti: senaryo başına SHA-256 + kod sayacı (S2 kural içi değişiklikleri için bayt bayt kanıt). */
    @Test
    void kuralOzeti_altinDosya() throws Exception {
        Map<String, Integer> kodSayilari = new TreeMap<>();
        List<String> ozetler = new ArrayList<>();
        int degerlendirme = 0;
        for (Senaryo s : senaryolar()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (KuralBaglami b : s.baglamlar()) {
                for (List<Ihlal> l : List.of(KuralMotoru.degerlendir(b), KuralMotoru.degerlendirYazma(b))) {
                    md.update(("#" + l.size() + "\n").getBytes(StandardCharsets.UTF_8));
                    for (Ihlal i : l) {
                        md.update((i.kod() + "|" + i.siddet() + "|" + i.olculen() + "|" + i.esik() + "|" + i.birim() + "|" + i.metin() + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
                for (Ihlal i : KuralMotoru.degerlendir(b)) {
                    kodSayilari.merge(i.kod(), 1, Integer::sum);
                }
                degerlendirme++;
            }
            ozetler.add(HexFormat.of().formatHex(md.digest()).substring(0, 16));
        }
        for (String kod : List.of("K-02", "K-07", "K-08", "K-10", "K-11", "K-12", "K-13", "K-15", "K-16", "K-17", "K-18", "K-19", "K-20", "K-22", "K-23")) {
            assertThat(kodSayilari).as("duyarlılık: " + kod + " üretiliyor").containsKey(kod);
        }
        Map<String, Object> altin = new LinkedHashMap<>();
        altin.put("senaryoSayisi", ozetler.size());
        altin.put("degerlendirmeSayisi", degerlendirme);
        altin.put("kodSayilari", kodSayilari);
        altin.put("senaryoOzetleri", ozetler);
        AltinDosya.kontrol("kural-ozeti", altin);
    }
}
