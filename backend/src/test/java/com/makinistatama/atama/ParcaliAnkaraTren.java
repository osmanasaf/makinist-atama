package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.makinistatama.sefer.SeferYuklemeServisi;
import com.makinistatama.sefer.TestExcel;

/**
 * F2c tur 3 test ön verisi: Ankara -> Ankara iki parçalı tren (K-20 nötr; {@link AtamaVeri} gar/hat/araç tanımlarını kullanır). Excel yoluyla yüklenir.
 */
public final class ParcaliAnkaraTren {

    private ParcaliAnkaraTren() {
    }

    /** {@code gun}: yyyy-MM-dd; saatler HH:mm (parça 1 {@code kalkis}-{@code araVaris}, parça 2 {@code araKalkis}-{@code varis}). Döner [parça 1, parça 2] sefer kimlikleri. */
    public static long[] yukle(SeferYuklemeServisi yukleme, JdbcTemplate jdbc, String tren, String gun, String kalkis, String araVaris, String araKalkis, String varis) {
        List<Long> idler = yukleGunler(yukleme, jdbc, tren, List.of(gun), kalkis, araVaris, araKalkis, varis);
        return new long[] { idler.get(0), idler.get(1) };
    }

    /** Aynı tren no, her gün için ayrı zincir (ayrı {@code tren_gunu}); döner gün sırasıyla [parça 1, parça 2, parça 1, parça 2, ...]. */
    public static List<Long> yukleGunler(SeferYuklemeServisi yukleme, JdbcTemplate jdbc, String tren, List<String> gunler, String kalkis, String araVaris, String araKalkis,
            String varis) {
        new AtamaVeri(jdbc); // gar / hat / araç tipi tanımları
        List<String[]> satirlar = new ArrayList<>();
        for (String gun : gunler) {
            String[] g = gun.split("-");
            satirlar.add(new String[] { g[2] + "." + g[1] + "." + g[0], tren, "Ankara", kalkis, "Ankara", varis, "YHT", "Ankara–Eskişehir", "1000" });
        }
        byte[] dosya = TestExcel.sefer(TestExcel.BASLIK, satirlar, List.<String[]>of(new String[] { tren, "1", "Ankara", araVaris, araKalkis, "600", "Ankara–Eskişehir", "" }));
        assertThat(yukleme.yukle(dosya, false).eklenen()).isEqualTo(2 * gunler.size());
        return jdbc.queryForList("SELECT id FROM sefer WHERE tren_no = ? ORDER BY tren_gunu, parca_no", Long.class, tren);
    }
}
