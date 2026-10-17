package com.makinistatama.kural;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.RastgeleKuralVeri.Senaryo;

/**
 * MK-5 / MK-54, K-23 açık modu (DB'siz): aynı rastgele senaryolarda asgari aktarma 20 dk ENGEL iken {@code degerlendirYazma} eski gövdeyle aynıdır (K-23 tüm komşuları görür,
 * sorumlu komşu süzgeci yeni ENGEL eklemez ya da eklediğini eski gövde de ekler) ve kural gerçekten tetiklenir.
 */
class AsgariAktarmaYazmaEsdegerTest {

    @Test
    void degerlendirYazma_K23_acikken_eskiGovdeyleAyni() {
        int k23 = 0;
        for (Senaryo s : KuralMotoruYazmaEsdegerTest.senaryolar()) {
            for (KuralBaglami b : s.baglamlar()) {
                KuralBaglami k = acik(b);
                assertThat(KuralMotoru.degerlendirYazma(k)).as("senaryo " + s.no() + " aday " + b.aday().seferId()).isEqualTo(KuralMotoruYazmaEsdegerTest.eskiYazma(k));
                k23 += TestVerisi.kod(KuralMotoru.degerlendirYazma(k), "K-23").size();
            }
        }
        assertThat(k23).as("duyarlılık: K-23 üretiliyor").isGreaterThan(20);
    }

    private static KuralBaglami acik(KuralBaglami b) {
        Parametreler p = b.parametreler();
        Map<String, String> d = new HashMap<>(p.degerler());
        d.put("nobet.asgari_aktarma_dk", "20");
        Map<String, Siddet> s = new HashMap<>(p.siddetler());
        s.put("K-23", Siddet.ENGEL);
        return new KuralBaglami(b.aday(), b.digerGorevler(), b.makinist(), b.pencereOncesiHatSonSurus(), b.pencereOncesiGenelSonSurus(), b.tanimAdlari(),
                Parametreler.olustur(d, s, List.copyOf(p.ozelKurallar())), b.simdi());
    }
}
