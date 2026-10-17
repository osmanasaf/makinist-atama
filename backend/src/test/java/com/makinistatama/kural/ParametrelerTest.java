package com.makinistatama.kural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParametrelerTest {

    private final Parametreler p = VarsayilanParametreler.varsayilan();

    @Test
    void katalog_32_anahtar_22_siddet() {
        assertEquals(32, VarsayilanParametreler.DEGERLER.size());
        assertEquals(22, VarsayilanParametreler.SIDDETLER.size());
        assertFalse(VarsayilanParametreler.SIDDETLER.containsKey("K-10"));
    }

    @Test
    void turGuvenliErisim() {
        assertEquals(660, p.dakika("dinlenme.ev_saat"));
        assertEquals(450, p.dakika("gece.max_saat")); // "7.5" saat
        assertEquals(60, p.tamsayi("belge.sinav_gecerlilik_ay"));
        assertEquals(0, new BigDecimal("0.5").compareTo(p.oran("gece.gorev_orani")));
        assertEquals(LocalTime.of(20, 0), p.saat("gece.baslangic"));
        assertFalse(p.bayrak("nobet.bekleme_sayilir"));
        assertEquals(240, p.dakika("nobet.ayirma_saat"));
    }

    @Test
    void virgullu_ondalik_ve_bayrak_1() {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        d.put("gece.max_saat", "7,5");
        d.put("nobet.bekleme_sayilir", "1");
        Parametreler q = Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER);
        assertEquals(450, q.dakika("gece.max_saat"));
        assertTrue(q.bayrak("nobet.bekleme_sayilir"));
    }

    @Test
    void eksik_anahtar_adi_mesajda() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> p.dakika("yok.anahtar"));
        assertTrue(e.getMessage().contains("yok.anahtar"));
        // bos deger (puantaj.saat_ucreti) da eksik sayilir
        assertThrows(IllegalArgumentException.class, () -> p.oran("puantaj.saat_ucreti"));
    }

    @Test
    void siddet_K10_sabit_ENGEL_bilinmeyen_kod_hata() {
        assertEquals(Siddet.ENGEL, p.siddet("K-10"));
        assertEquals(Siddet.UYARI, p.siddet("K-20"));
        assertEquals(Siddet.KAPALI, p.siddet("K-21"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> p.siddet("K-99"));
        assertTrue(e.getMessage().contains("K-99"));
    }

    @Test
    void bozuk_deger_anahtari_mesajda() {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        d.put("gunluk.max_saat", "onbir");
        Parametreler q = Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> q.dakika("gunluk.max_saat"));
        assertTrue(e.getMessage().contains("gunluk.max_saat"));
    }
}
