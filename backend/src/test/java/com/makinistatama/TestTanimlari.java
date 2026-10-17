package com.makinistatama;

import org.springframework.jdbc.core.JdbcTemplate;

/** Testlerde ön veri: tanım satırlarını doğrudan veritabanına yazar ve kimliğini döner (uç testlerinden bağımsız). */
public final class TestTanimlari {

    private TestTanimlari() {
    }

    public static long gar(JdbcTemplate jdbc, String ad) {
        return ekle(jdbc, "GAR", ad);
    }

    public static long hat(JdbcTemplate jdbc, String ad) {
        return ekle(jdbc, "HAT", ad);
    }

    public static long aracTipi(JdbcTemplate jdbc, String ad) {
        return ekle(jdbc, "ARAC_TIPI", ad);
    }

    private static long ekle(JdbcTemplate jdbc, String tip, String ad) {
        return jdbc.queryForObject("INSERT INTO tanim (tip, ad) VALUES (?, ?) ON CONFLICT (tip, ad) DO UPDATE SET ad = EXCLUDED.ad RETURNING id",
                Long.class, tip, ad);
    }
}
