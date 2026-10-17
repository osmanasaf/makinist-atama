package com.makinistatama.atama;

import static com.makinistatama.atama.OtomatikAtamaTest.ADLAR;
import static com.makinistatama.atama.OtomatikAtamaTest.ANKARA;
import static com.makinistatama.atama.OtomatikAtamaTest.ARAC;
import static com.makinistatama.atama.OtomatikAtamaTest.HAT;
import static com.makinistatama.atama.OtomatikAtamaTest.an;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.kural.ZamanHesabi;

/**
 * F2d review INFO-1 (OK-17 varyantı, {@code OtomatikAtama.varyant}): iyileştirme önceliği parça 2'yi parça 1'den ÖNCE yerleştirebilir. Aday ÖNCEKİ parça, çalışmada yerleşmiş komşu SONRAKİ
 * parça iken komşunun hazırlığı geri verilmezse (mutant {@code komsular.add(sonraki)}) parça 1 sahte çakışmayla boş kalır; tersi yönde (aday sonraki, komşu önceki) alıkoyma için de aynı.
 * Tek makinist, hazırlık = alıkoyma = 15 dk.
 */
class OtomatikAtamaParcaSiraTest {

    private static final Parametreler P15 = hazirlikli();

    private static Parametreler hazirlikli() {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        d.put("gorev.hazirlik_dk", "15");
        d.put("gorev.alikoyma_dk", "15");
        return Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER);
    }

    private static SeferGirdisi parca(long id, String bas, String bit, Long onceki, Long sonraki) {
        Gorev g = Gorev.olustur(id, ANKARA, ANKARA, HAT, ARAC, an(bas), an(bas), an(bit), 100, null, P15, true, true);
        return new SeferGirdisi(id, ZamanHesabi.gun(g.kalkis()), YearMonth.from(ZamanHesabi.gun(g.kalkis())), g, onceki, sonraki);
    }

    private static Sonuc planla(Map<Long, Integer> oncelik) {
        List<SeferGirdisi> seferler = List.of(parca(101, "2026-10-12T08:00", "2026-10-12T10:00", null, 102L), parca(102, "2026-10-12T10:10", "2026-10-12T13:00", 101L, null));
        return OtomatikAtama.planla(seferler, List.of(OtomatikAtamaTest.makinist(1, "A", 0, 0)), ADLAR, P15, an("2026-10-10T10:00"), 15, true, true, List.of(), 0, 0, oncelik, 1440);
    }

    @Test
    void parca2_parca1den_once_yerlesir_adayOncekiParca_komsuSonrakiParca() {
        Sonuc s = planla(Map.of(102L, 1));

        assertThat(s.bosKalanlar()).as("parça 1 sahte çakışmayla boş kalmamalı").isEmpty();
        assertThat(s.atananlar()).containsExactlyInAnyOrder(new Atandi(101, 1), new Atandi(102, 1));
    }

    @Test
    void parca1_once_yerlesir_adaySonrakiParca_komsuOncekiParca() {
        Sonuc s = planla(Map.of());

        assertThat(s.bosKalanlar()).isEmpty();
        assertThat(s.atananlar()).containsExactly(new Atandi(101, 1), new Atandi(102, 1));
    }
}
