package com.makinistatama.kural;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test yardimcisi ve tohum KATALOGU: analizdeki (01-analiz.md) tum kural/diger parametre anahtarlari
 * (29) + {@code nobet.*} (3) = 32 anahtar ve varsayilan degerleri; K-01..K-23 (K-10 haric) 22 siddet.
 * Bos deger ("") = "bilinmiyor" (puantaj.saat_ucreti, puantaj.km_birim_tutari).
 * Main kodda sabit esik YOKTUR; varsayilanlar yalniz burada (ve tohum migration'inda) yasar.
 */
public final class VarsayilanParametreler {

    public static final Map<String, String> DEGERLER;
    public static final Map<String, Siddet> SIDDETLER;

    static {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("belge.sinav_gecerlilik_ay", "60");
        d.put("hat.ara_max_ay", "18");
        d.put("brove.surus_ara_max_ay", "18");
        d.put("dinlenme.ev_saat", "11");
        d.put("dinlenme.dis_merkez_saat", "11");
        d.put("gunluk.max_saat", "11");
        d.put("haftalik.max_saat", "45");
        d.put("haftalik.normal_saat", "45");
        d.put("yillik.fazla_mesai_max_saat", "270");
        d.put("gece.max_saat", "7.5");
        d.put("gece.baslangic", "20:00");
        d.put("gece.bitis", "06:00");
        d.put("gece.gorev_orani", "0.5");
        d.put("gece.ard_hafta_max", "1");
        d.put("gece.hafta_min_gorev", "1");
        d.put("hafta_tatili.min_saat", "24");
        d.put("sefer.max_saat", "9");
        d.put("gorev.hazirlik_dk", "0");
        d.put("gorev.alikoyma_dk", "0");
        d.put("belge.uyari_gun", "30");
        d.put("adalet.olcut", "SAAT");
        d.put("yedek.oran", "0.10");
        d.put("yedek.min", "1");
        d.put("puantaj.gece_bitis", "07:00");
        d.put("puantaj.gece_tazminat_orani", "0.15");
        d.put("puantaj.asgari_gunluk_saat", "7.5");
        d.put("puantaj.saat_ucreti", "");
        d.put("puantaj.fazla_mesai_carpani", "1.5");
        d.put("puantaj.km_birim_tutari", "");
        d.put("nobet.ayirma_saat", "4");
        d.put("nobet.bekleme_sayilir", "0");
        d.put("nobet.asgari_aktarma_dk", "20"); // V30 (OK-24): aynı nöbette ardışık görevler arası en az 20 dk
        DEGERLER = Map.copyOf(d);

        Map<String, Siddet> s = new LinkedHashMap<>();
        for (int i = 1; i <= 23; i++) {
            if (i == 10) {
                continue;
            }
            // V30 (OK-24): K-14 KAPALI (haftalık 45 s üstü engel değil, fazla mesai sayılır), K-23 ENGEL
            s.put(String.format("K-%02d", i), i == 20 || i == 22 ? Siddet.UYARI : i == 21 || i == 14 ? Siddet.KAPALI : Siddet.ENGEL);
        }
        SIDDETLER = Map.copyOf(s);
    }

    private VarsayilanParametreler() {
    }

    public static Parametreler varsayilan() {
        return Parametreler.olustur(DEGERLER, SIDDETLER);
    }
}
