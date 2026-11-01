package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.Siddet;

/** YENİ-2: özdeş makinistler sınıflandığında ağırlıklı özet, her makinist ayrı sayılan özetle BİREBİR aynıdır (birincil neden ve sayılar). */
class NedenOzetiAgirlikTest {

    private static Ihlal ihlal(String kod, Siddet s) {
        return new Ihlal(kod, s, 0, 0, "", "");
    }

    @Test
    void agirlikliOzet_herMakinistAyriSayilanOzetleAyni() {
        // sınıf A (5 kişi): hat yetkisi + dinlenme UYARI; sınıf B (2 kişi): yalnız dinlenme ENGEL; sınıf C (1 kişi): günlük süre + hat yetkisi
        List<List<Ihlal>> siniflar = List.of(List.of(ihlal("K-06", Siddet.ENGEL), ihlal("K-11", Siddet.UYARI)), List.of(ihlal("K-11", Siddet.ENGEL)),
                List.of(ihlal("K-06", Siddet.ENGEL), ihlal("K-13", Siddet.ENGEL)));
        int[] adet = { 5, 2, 1 };
        List<List<Ihlal>> acik = new ArrayList<>();
        for (int c = 0; c < siniflar.size(); c++) {
            for (int k = 0; k < adet[c]; k++) {
                acik.add(siniflar.get(c));
            }
        }

        String tekTek = NedenOzeti.ozet(acik.size(), NedenOzeti.birincil(acik), null);
        List<String> kodlar = NedenOzeti.birincil(siniflar, adet);
        String agirlikli = NedenOzeti.ozet(acik.size(), kodlar, adet, null);

        assertThat(agirlikli).isEqualTo(tekTek).isEqualTo("0/8 uygun (yetkili 7, yetkisiz 1): dinlenme 7, hat yetkisi 1");
    }
}
