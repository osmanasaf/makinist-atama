package com.makinistatama.atama;

import static com.makinistatama.atama.OtomatikAtamaTest.ADLAR;
import static com.makinistatama.atama.OtomatikAtamaTest.an;
import static com.makinistatama.atama.OtomatikAtamaTest.makinist;
import static com.makinistatama.atama.OtomatikAtamaTest.sefer;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;

/** K-23 otomatik atamada: tek makinist, 10 dk arayla iki sefer. K-23 KAPALI/0 (eski varsayılan; açık ön koşul) ikisini de atar; 20 dk ENGEL ikincisini nedeniyle boş bırakır; aynı trenin parçaları muaf (OK-17). */
class AsgariAktarmaOtomatikAtamaTest {

    private static Parametreler p(boolean acik) {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        Map<String, Siddet> s = new HashMap<>(VarsayilanParametreler.SIDDETLER);
        d.put("nobet.asgari_aktarma_dk", acik ? "20" : "0"); // OK-24: varsayılan artık 20 dk ENGEL; kapalı hâl açıkça kurulur
        s.put("K-23", acik ? Siddet.ENGEL : Siddet.KAPALI);
        return Parametreler.olustur(d, s);
    }

    private static Sonuc planla(List<SeferGirdisi> seferler, boolean acik) {
        return OtomatikAtama.planla(seferler, List.of(makinist(1, "A", 0, 0)), ADLAR, p(acik), an("2026-10-10T10:00"), 15, true);
    }

    private static final List<SeferGirdisi> ARDISIK = List.of(sefer(101, "2026-10-12T08:00", "2026-10-12T09:00"), sefer(102, "2026-10-12T09:10", "2026-10-12T10:00"));

    @Test
    void kapaliyken_ikisi_de_atanir() {
        assertThat(planla(ARDISIK, false).atananlar()).containsExactly(new Atandi(101, 1), new Atandi(102, 1));
    }

    @Test
    void acikken_ikinci_sefer_aktarma_suresi_nedeniyle_bos() {
        Sonuc s = planla(ARDISIK, true);

        assertThat(s.atananlar()).containsExactly(new Atandi(101, 1));
        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> {
            assertThat(b.seferId()).isEqualTo(102);
            assertThat(b.neden()).contains("aktarma süresi");
        });
    }

    @Test
    void acikken_ayni_trenin_ardisik_parcalari_muaf() {
        SeferGirdisi p1 = sefer(101, "2026-10-12T08:00", "2026-10-12T09:00");
        SeferGirdisi p2 = sefer(102, "2026-10-12T09:05", "2026-10-12T10:00");
        List<SeferGirdisi> parcalar = List.of(new SeferGirdisi(p1.seferId(), p1.gun(), p1.ay(), p1.gorev(), null, 102L), new SeferGirdisi(p2.seferId(), p2.gun(), p2.ay(), p2.gorev(), 101L, null));

        Sonuc s = planla(parcalar, true);

        assertThat(s.bosKalanlar()).isEmpty();
        assertThat(s.atananlar()).containsExactly(new Atandi(101, 1), new Atandi(102, 1));
    }
}
