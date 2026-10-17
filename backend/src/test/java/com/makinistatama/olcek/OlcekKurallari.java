package com.makinistatama.olcek;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ölçek testleri için açık özel kural tohumu (T3.5, T6.7): 10 kural (tek sefer, gün, hafta, ay, yıl, kayan 30 gün, ardışık gün serisi, izin günü, görevsiz
 * aralık, nöbetler arası dinlenme). Eşikler gevşektir (hiçbiri atamayı engellemez), yük ölçülür: yıl, ay ve kayan 30 kuralları yükleme penceresini büyütür (MK-19).
 */
final class OlcekKurallari {

    /** olcu, pencere, karşılaştırma, eşik, kayan gün (0 = yok). */
    private static final String[][] KURALLAR = {
        { "TEK_SEFER_SURESI", "SEFER", "EN_FAZLA", "9", "0" },
        { "TOPLAM_GOREV_SURESI", "GUN", "EN_FAZLA", "11.5", "0" },
        { "TOPLAM_GOREV_SURESI", "HAFTA", "EN_FAZLA", "46", "0" },
        { "TOPLAM_GOREV_SURESI", "AY", "EN_FAZLA", "190", "0" },
        { "TOPLAM_GOREV_SURESI", "YIL", "EN_FAZLA", "2300", "0" },
        { "TOPLAM_GOREV_SURESI", "KAYAN", "EN_FAZLA", "400", "30" },
        { "ARDISIK_CALISMA_GUNU", "SERI", "EN_FAZLA", "31", "0" },
        { "IZIN_GUNU", "AY", "EN_AZ", "1", "0" },
        { "GOREVSIZ_ARALIK", "GUN", "EN_AZ", "0.5", "0" },
        { "NOBETLER_ARASI_DINLENME", "NOBET", "EN_AZ", "1", "0" } };

    private OlcekKurallari() {
    }

    static void yaz(JdbcTemplate jdbc) {
        yaz(jdbc, KURALLAR.length);
    }

    /** İlk {@code adet} kural (5: Faz K1 kümesi). */
    static void yaz(JdbcTemplate jdbc, int adet) {
        for (int i = 0; i < adet; i++) {
            String[] k = KURALLAR[i];
            jdbc.update("INSERT INTO ozel_kural (ad, olcu, pencere, kayan_gun, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan) "
                    + "VALUES (?, ?, ?, NULLIF(?, 0), ?, ?, '{}', 'ENGEL', '2026-01-01', 'olcek', 'test')", "Olcek " + i, k[0], k[1], Integer.parseInt(k[4]), k[2],
                    new java.math.BigDecimal(k[3]));
        }
    }
}
