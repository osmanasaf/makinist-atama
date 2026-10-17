package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ARAC;
import static com.makinistatama.kural.TestVerisi.HAT;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.makinistatama.kural.MakinistBilgisi.HatBrove;
import com.makinistatama.kural.MakinistBilgisi.Izin;
import com.makinistatama.kural.MakinistBilgisi.IzinDurumu;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BelgeKurallariTest {

    private static List<Ihlal> kontrol(Gorev aday, TestVerisi.M m) {
        return BelgeKurallari.kontrol(baglam(aday, List.of(), m.yap(), param()));
    }

    private static Gorev ogle(String gun) {
        return gorev(1, gun + "T08:00", gun + "T14:00");
    }

    @Test
    void varsayilan_makinist_uygun() {
        assertTrue(kontrol(ogle("2026-10-12"), new TestVerisi.M()).isEmpty());
    }

    @Test
    void K01_askida_ve_pasif_engel() {
        TestVerisi.M m = new TestVerisi.M();
        m.durum = MakinistBilgisi.Durum.ASKIDA;
        List<Ihlal> i = kontrol(ogle("2026-10-12"), m);
        assertEquals(List.of("K-01"), kodlar(i));
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
        m.durum = MakinistBilgisi.Durum.PASIF;
        assertEquals(List.of("K-01"), kodlar(kontrol(ogle("2026-10-12"), m)));
    }

    @Test
    void K03_saglik_bitis_15Eki_varis_gunu_ile_karsilastirilir() {
        TestVerisi.M m = new TestVerisi.M();
        m.saglik = LocalDate.of(2026, 10, 15);
        assertTrue(kontrol(gorev(1, "2026-10-15T08:00", "2026-10-15T14:00"), m).isEmpty());
        assertEquals(List.of("K-03"),
                kodlar(kontrol(gorev(1, "2026-10-15T22:00", "2026-10-16T03:00"), m)));
    }

    @Test
    void K02_K04_ehliyet_ve_psikoteknik_ve_eksik_tarih() {
        TestVerisi.M m = new TestVerisi.M();
        m.ehliyet = LocalDate.of(2026, 10, 11);
        m.psikoteknik = null;
        assertEquals(List.of("K-02", "K-04"), kodlar(kontrol(ogle("2026-10-12"), m)));
    }

    @Test
    void K05_arac_tipi_sinavi_60_ay() {
        TestVerisi.M m = new TestVerisi.M();
        m.arac = new java.util.HashMap<>(Map.of(ARAC, LocalDate.of(2021, 3, 10))); // bitis 2026-03-10
        assertEquals(List.of("K-05"), kodlar(kontrol(ogle("2026-10-12"), m)));
        m.arac = new java.util.HashMap<>(Map.of(ARAC, LocalDate.of(2022, 3, 10))); // bitis 2027-03-10
        assertTrue(kontrol(ogle("2026-10-12"), m).isEmpty());
    }

    @Test
    void K05_arac_tipi_hic_yoksa_engel_ve_sinir_gunu_uygun() {
        TestVerisi.M m = new TestVerisi.M();
        m.arac = new java.util.HashMap<>();
        assertEquals(List.of("K-05"), kodlar(kontrol(ogle("2026-10-12"), m)));
        m.arac = new java.util.HashMap<>(Map.of(ARAC, LocalDate.of(2021, 10, 12))); // bitis tam 2026-10-12
        assertTrue(kontrol(ogle("2026-10-12"), m).isEmpty());
        assertEquals(List.of("K-05"), kodlar(kontrol(ogle("2026-10-13"), m)));
    }

    @Test
    void K06_hat_yetkisi_yok_ve_sinav_dolmus_mesaj_hat_adi() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new java.util.HashMap<>();
        List<Ihlal> i = kontrol(ogle("2026-10-12"), m);
        assertEquals(List.of("K-06"), kodlar(i));
        assertEquals("Hat yetkisi yok: Ankara–Eskişehir", i.get(0).metin());

        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2021, 3, 10), LocalDate.of(2026, 9, 1))));
        assertEquals(List.of("K-06"), kodlar(kontrol(ogle("2026-10-12"), m)));
    }

    @Test
    void K07_hat_son_surus_20_ay_engel() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 2, 1))));
        m.sonSurus = LocalDate.of(2026, 9, 1); // genel surus yakin: K-08 uygun
        List<Ihlal> i = kontrol(ogle("2026-10-12"), m);
        assertEquals(List.of("K-07"), kodlar(i));
        assertEquals(20, i.get(0).olculen());
        assertEquals(18, i.get(0).esik());
        assertEquals("ay", i.get(0).birim());
    }

    @Test
    void K07_son_surus_2025_04_12_12Eki_uygun_13Eki_engel() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 4, 12))));
        assertTrue(kontrol(ogle("2026-10-12"), m).isEmpty());
        assertEquals(List.of("K-07"), kodlar(kontrol(ogle("2026-10-13"), m)));
    }

    @Test
    void K07_son_surus_yoksa_hat_sinav_tarihi_sayilir() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2024, 1, 1), null)));
        assertEquals(List.of("K-07"), kodlar(kontrol(ogle("2026-10-12"), m)));
        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2025, 6, 1), null)));
        assertTrue(kontrol(ogle("2026-10-12"), m).isEmpty());
    }

    @Test
    void K07_pencere_oncesi_ve_penceredeki_gorevler_son_surus_sayilir() {
        TestVerisi.M m = new TestVerisi.M();
        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 2, 1))));
        // pencere oncesi toplu deger
        KuralBaglami b1 = new KuralBaglami(ogle("2026-10-12"), List.of(), m.yap(),
                Map.of(HAT, LocalDate.of(2026, 5, 1)), null, TestVerisi.ADLAR, param());
        assertTrue(BelgeKurallari.kontrol(b1).isEmpty());
        // penceredeki (aday seferden onceki) gorev
        KuralBaglami b2 = baglam(ogle("2026-10-12"), List.of(gorev(9, "2026-10-01T08:00", "2026-10-01T10:00")),
                m.yap(), param());
        assertTrue(BelgeKurallari.kontrol(b2).isEmpty());
        // ayni gun veya sonraki gorev son surus sayilmaz
        KuralBaglami b3 = baglam(ogle("2026-10-12"), List.of(gorev(9, "2026-10-20T08:00", "2026-10-20T10:00")),
                m.yap(), param());
        assertEquals(List.of("K-07"), kodlar(BelgeKurallari.kontrol(b3)));
    }

    @Test
    void K08_brove_dusme_hicbir_trende_surus_yok() {
        TestVerisi.M m = new TestVerisi.M();
        m.sonSurus = LocalDate.of(2025, 2, 1);
        m.hat = new java.util.HashMap<>(Map.of(HAT, new HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 2, 1))));
        List<String> k = kodlar(kontrol(ogle("2026-10-12"), m));
        assertEquals(List.of("K-07", "K-08"), k);
        // baska hatta yakin surus K-08'i giderir, K-07 kalir
        m.hat.put(99L, new HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 8, 1)));
        assertEquals(List.of("K-07"), kodlar(kontrol(ogle("2026-10-12"), m)));
    }

    @Test
    void K09_yalniz_onayli_izin_gunu_engel() {
        TestVerisi.M m = new TestVerisi.M();
        m.izin.add(new Izin(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 24), IzinDurumu.ONAY));
        assertEquals(List.of("K-09"), kodlar(kontrol(gorev(1, "2026-10-22T08:00", "2026-10-22T14:00"), m)));
        assertEquals(List.of("K-09"), kodlar(kontrol(gorev(1, "2026-10-24T22:00", "2026-10-25T03:00"), m)));
        assertEquals(List.of("K-09"), kodlar(kontrol(gorev(1, "2026-10-19T22:00", "2026-10-20T03:00"), m)));
        assertTrue(kontrol(gorev(1, "2026-10-25T08:00", "2026-10-25T14:00"), m).isEmpty());
        assertTrue(kontrol(gorev(1, "2026-10-19T08:00", "2026-10-19T14:00"), m).isEmpty());
        // 00:00'da biten gorev ertesi gune dokunmaz
        assertTrue(kontrol(gorev(1, "2026-10-19T20:00", "2026-10-20T00:00"), m).isEmpty());
    }

    @Test
    void K09_talep_ve_red_izin_atamayi_engellemez() {
        TestVerisi.M m = new TestVerisi.M();
        m.izin.add(new Izin(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 24), IzinDurumu.TALEP));
        m.izin.add(new Izin(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 24), IzinDurumu.RED));
        assertTrue(kontrol(gorev(1, "2026-10-22T08:00", "2026-10-22T14:00"), m).isEmpty());
    }
}
