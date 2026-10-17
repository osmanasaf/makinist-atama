package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.guvenlik.BenimUclari;

/** NFR-M01: ATAMACI ve YONETICI {@code /api/benim} uçlarına giremez (dış çit + {@code @SadeceMakinist}); anonim 401. */
class BenimYetkiTest extends TestVeritabani {

    @Test
    void atamaciVeYoneticiBenimUclarinda403_anonim401() throws Exception {
        List<String> sorunlar = new ArrayList<>();
        for (Tarayici t : List.of(atamaci(), yonetici())) {
            for (String uc : BenimUclari.tumu()) {
                int durum = t.istek(BenimUclari.istek(uc)).andReturn().getResponse().getStatus();
                if (durum != 403) {
                    sorunlar.add(uc + " -> " + durum);
                }
            }
        }
        Tarayici anonim = new Tarayici(mvc);
        for (String uc : BenimUclari.tumu()) {
            int durum = anonim.istek(BenimUclari.istek(uc)).andReturn().getResponse().getStatus();
            if (durum != 401) {
                sorunlar.add("anonim " + uc + " -> " + durum);
            }
        }
        assertThat(sorunlar).isEmpty();
        assertThat(anonim.get("/api/benim/yok").andReturn().getResponse().getStatus()).as("var olmayan benim ucu da anonimde 401").isEqualTo(401);
    }
}
