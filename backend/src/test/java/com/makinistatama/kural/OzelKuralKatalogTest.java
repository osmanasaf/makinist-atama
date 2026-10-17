package com.makinistatama.kural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T1.1: ölçü kataloğu doğrulaması (FR-01): izinsiz birleşim ve eşik aralığı. */
class OzelKuralKatalogTest {

    @Test
    void izinsiz_birlesim_reddedilir() {
        assertNotNull(Olcu.TOPLAM_GOREV_SURESI.pencereHatasi(Pencere.SEFER)); // toplam süre x sefer penceresi
        assertNotNull(Olcu.TEK_SEFER_SURESI.pencereHatasi(Pencere.GUN));
        assertNull(Olcu.TEK_SEFER_SURESI.pencereHatasi(Pencere.SEFER));
        assertNotNull(Olcu.GECE_SURESI.pencereHatasi(Pencere.YIL));
        assertNotNull(Olcu.GOREV_SAYISI.karsilastirmaHatasi(Karsilastirma.EN_AZ)); // görev sayısı x en az
        assertNull(Olcu.GOREV_SAYISI.karsilastirmaHatasi(Karsilastirma.EN_FAZLA));
        assertTrue(Olcu.TOPLAM_GOREV_SURESI.pencereHatasi(Pencere.SEFER).contains("sefer"));
    }

    @Test
    void esik_araligi_gunluk_0_ve_25_reddi_24_kabul() {
        assertNotNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.GUN, BigDecimal.ZERO));
        assertNotNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.GUN, new BigDecimal("25")));
        assertNotNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.GUN, new BigDecimal("-1")));
        assertNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.GUN, new BigDecimal("24")));
        assertNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.GUN, new BigDecimal("0.5")));
        assertNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.HAFTA, new BigDecimal("168")));
        assertNotNull(Olcu.TOPLAM_GOREV_SURESI.esikHatasi(Pencere.HAFTA, new BigDecimal("169")));
        assertNotNull(Olcu.TEK_SEFER_SURESI.esikHatasi(Pencere.SEFER, new BigDecimal("49")));
    }

    @Test
    void sure_dakika_duyarliginda_sayi_tam_sayi() {
        assertNull(Olcu.TEK_SEFER_SURESI.esikHatasi(Pencere.SEFER, new BigDecimal("7.5")));
        assertNull(Olcu.TEK_SEFER_SURESI.esikHatasi(Pencere.SEFER, new BigDecimal("8.25")));
        assertNotNull(Olcu.TEK_SEFER_SURESI.esikHatasi(Pencere.SEFER, new BigDecimal("8.01"))); // 480,6 dk
        assertNotNull(Olcu.TEK_SEFER_SURESI.esikHatasi(Pencere.SEFER, new BigDecimal("8.123")));
        assertNotNull(Olcu.GOREV_SAYISI.esikHatasi(Pencere.GUN, new BigDecimal("2.5")));
        assertNull(Olcu.GOREV_SAYISI.esikHatasi(Pencere.GUN, new BigDecimal("2.0")));
        assertNull(Olcu.GECE_NOBETI_SAYISI.esikHatasi(Pencere.HAFTA, BigDecimal.ZERO)); // gece nöbeti: 0 serbest ("hiç gece nöbeti verme")
        assertNotNull(Olcu.GECE_NOBETI_SAYISI.esikHatasi(Pencere.HAFTA, new BigDecimal("15")));
    }

    @Test
    void anlamsiz_esikler_olcuye_gore_alt_sinirla_reddedilir() { // Tur 2 BUG-3
        assertNotNull(Olcu.GOREV_SAYISI.esikHatasi(Pencere.GUN, BigDecimal.ZERO)); // "görev sayısı en fazla 0" her atamayı kırmızı yapardı
        assertNull(Olcu.GOREV_SAYISI.esikHatasi(Pencere.GUN, BigDecimal.ONE));
        assertNotNull(Olcu.IZIN_GUNU.esikHatasi(Pencere.AY, BigDecimal.ZERO)); // "en az 0 gün izin" hep sağlanır
        assertNotNull(Olcu.IZIN_GUNU.esikHatasi(Pencere.HAFTA, new BigDecimal("8"))); // haftada 7 gün var
        assertNull(Olcu.IZIN_GUNU.esikHatasi(Pencere.AY, new BigDecimal("28")));
        assertNotNull(Olcu.IZIN_GUNU.esikHatasi(Pencere.AY, new BigDecimal("29")));
        assertNotNull(Olcu.ARDISIK_GECE_GUNU.esikHatasi(Pencere.SERI, BigDecimal.ZERO));
        assertNotNull(Olcu.ARDISIK_GECE_GUNU.esikHatasi(Pencere.SERI, new BigDecimal("32")));
        assertNull(Olcu.ARDISIK_GECE_GUNU.esikHatasi(Pencere.SERI, new BigDecimal("3")));
        assertNotNull(Olcu.GOREVSIZ_ARALIK.esikHatasi(Pencere.GUN, BigDecimal.ZERO)); // süre: 0'dan büyük
        assertNull(Olcu.GECE_NOBETI_SAYISI.esikHatasi(Pencere.HAFTA, BigDecimal.ZERO)); // "hiç gece nöbeti verme" anlamlı
        assertNull(Olcu.IZIN_GUNU.esikHatasi(Pencere.KAYAN, new BigDecimal("2"), 10));
        assertNotNull(Olcu.IZIN_GUNU.esikHatasi(Pencere.KAYAN, new BigDecimal("11"), 10)); // pencere 10 gün
        assertNotNull(Olcu.kayanGunHatasi(Pencere.KAYAN, 1));
        assertNotNull(Olcu.kayanGunHatasi(Pencere.KAYAN, 367));
        assertNull(Olcu.kayanGunHatasi(Pencere.KAYAN, 366));
        assertNull(Olcu.kayanGunHatasi(Pencere.KAYAN, 2));
        assertNotNull(Olcu.kayanGunHatasi(Pencere.KAYAN, null));
        assertNotNull(Olcu.kayanGunHatasi(Pencere.AY, 7));
        assertNull(Olcu.kayanGunHatasi(Pencere.AY, null));
    }

    @Test
    void azalan_olculer_yalniz_en_az_artanlar_yalniz_en_fazla() {
        assertNull(Olcu.IZIN_GUNU.karsilastirmaHatasi(Karsilastirma.EN_AZ));
        assertNotNull(Olcu.IZIN_GUNU.karsilastirmaHatasi(Karsilastirma.EN_FAZLA));
        assertNotNull(Olcu.TOPLAM_GOREV_SURESI.karsilastirmaHatasi(Karsilastirma.EN_AZ)); // "ayda en az 100 saat" hedef, kısıt değil [V2]
        assertNull(Olcu.NOBETLER_ARASI_DINLENME.karsilastirmaHatasi(Karsilastirma.EN_AZ));
    }

    @Test
    void kod_ve_kapsam_normalizasyonu() {
        assertEquals("Ö-007", OzelKural.kod(7));
        assertEquals("Ö-123", OzelKural.kod(123));
        assertEquals("Ö-1000", OzelKural.kod(1000));
        assertTrue(OzelKural.ozelMi("Ö-001"));
        assertTrue(!OzelKural.ozelMi("K-01"));
        Kapsam k = new Kapsam(null, null, null, null, null, null);
        assertTrue(k.tumu());
        assertEquals(Vardiya.HEPSI, k.vardiya());
        assertEquals(Set.of(), k.hatlar());
        assertTrue(!new Kapsam(Set.of(3L, 1L), null, null, null, null, null).tumu());
        assertEquals("[1, 3]", new Kapsam(Set.of(3L, 1L), null, null, null, null, null).hatlar().toString()); // sıralı: JSON kararlı
        assertEquals(Kapsam.TUMU, new Kapsam(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Vardiya.HEPSI));
    }

    @Test
    void her_olcunun_her_penceresi_icin_esik_araligi_tanimli() {
        for (Olcu o : Olcu.values()) {
            for (Pencere p : o.izinliPencereler()) {
                assertNotNull(o.esikAraligi(p).ust());
                assertTrue(o.esikAraligi(p).ust().signum() > 0);
            }
            assertEquals(1, o.izinliKarsilastirmalar().size());
            assertTrue(o.izinliKarsilastirmalar().contains(o.azalan() ? Karsilastirma.EN_AZ : Karsilastirma.EN_FAZLA));
        }
    }
}
