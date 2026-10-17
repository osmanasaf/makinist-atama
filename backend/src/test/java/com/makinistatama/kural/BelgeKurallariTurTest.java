package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ARAC;
import static com.makinistatama.kural.TestVerisi.HAT;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** F3b MK-84 (FR-02): K-05..K-08 yalnız MAKINIST; kondüktörde K-02 "Kişisel emniyet belgesi"; K-01/K-03/K-04/K-09 ve zaman kuralları türden bağımsız. */
class BelgeKurallariTurTest {

    private static MakinistBilgisi kisi(MakinistBilgisi.Tur tur, LocalDate ehliyet, boolean brove, MakinistBilgisi.Durum durum, List<MakinistBilgisi.Izin> izin) {
        Map<Long, LocalDate> arac = brove ? Map.of(ARAC, LocalDate.of(2024, 1, 1)) : Map.of();
        Map<Long, MakinistBilgisi.HatBrove> hat = brove ? Map.of(HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1))) : Map.of();
        return new MakinistBilgisi(1, TestVerisi.ANKARA, durum, ehliyet, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1), arac, hat, null, izin, tur);
    }

    private static MakinistBilgisi konduktor() {
        return kisi(MakinistBilgisi.Tur.KONDUKTOR, LocalDate.of(2030, 1, 1), false, MakinistBilgisi.Durum.AKTIF, List.of());
    }

    private static List<Ihlal> belge(Gorev aday, MakinistBilgisi m) {
        return BelgeKurallari.kontrol(baglam(aday, List.of(), m, param()));
    }

    private static Gorev ogle(String gun) {
        return gorev(1, gun + "T08:00", gun + "T14:00");
    }

    @Test
    void konduktor_broveYok_K05_K06_K07_K08_uretilmez() {
        assertTrue(belge(ogle("2026-10-12"), konduktor()).isEmpty());
    }

    @Test
    void makinist_broveYok_K05_K06_ENGEL_regresyon() {
        MakinistBilgisi m = kisi(MakinistBilgisi.Tur.MAKINIST, LocalDate.of(2030, 1, 1), false, MakinistBilgisi.Durum.AKTIF, List.of());
        List<Ihlal> i = belge(ogle("2026-10-12"), m);
        assertEquals(List.of("K-05", "K-06"), kodlar(i));
        assertTrue(i.stream().allMatch(x -> x.siddet() == Siddet.ENGEL));
    }

    @Test
    void konduktor_K02_etiketi_kisiselEmniyetBelgesi_makinistte_ehliyet() {
        MakinistBilgisi k = kisi(MakinistBilgisi.Tur.KONDUKTOR, LocalDate.of(2026, 10, 11), false, MakinistBilgisi.Durum.AKTIF, List.of());
        List<Ihlal> i = belge(gorev(1, "2026-10-12T08:00", "2026-10-12T14:00"), k);
        assertEquals(List.of("K-02"), kodlar(i));
        assertTrue(i.get(0).metin().startsWith("Kişisel emniyet belgesi geçerlilik bitişi"), i.get(0).metin());
        assertEquals(Siddet.ENGEL, i.get(0).siddet());

        MakinistBilgisi m = kisi(MakinistBilgisi.Tur.MAKINIST, LocalDate.of(2026, 10, 11), true, MakinistBilgisi.Durum.AKTIF, List.of());
        List<Ihlal> im = belge(gorev(1, "2026-10-12T08:00", "2026-10-12T14:00"), m);
        assertEquals(List.of("K-02"), kodlar(im));
        assertTrue(im.get(0).metin().startsWith("Ehliyet geçerlilik bitişi"), im.get(0).metin());
    }

    @Test
    void konduktor_K02_bitis11Eki_varis11Eki_gecerli() {
        MakinistBilgisi k = kisi(MakinistBilgisi.Tur.KONDUKTOR, LocalDate.of(2026, 10, 11), false, MakinistBilgisi.Durum.AKTIF, List.of());
        assertTrue(belge(gorev(1, "2026-10-11T08:00", "2026-10-11T14:00"), k).isEmpty());
    }

    @Test
    void konduktor_K03_K04_mesaji_saglik_psikoteknik_ayni() {
        MakinistBilgisi k = new MakinistBilgisi(1, TestVerisi.ANKARA, MakinistBilgisi.Durum.AKTIF, LocalDate.of(2030, 1, 1), LocalDate.of(2026, 10, 1), null,
                Map.of(), Map.of(), null, List.of(), MakinistBilgisi.Tur.KONDUKTOR);
        List<Ihlal> i = belge(ogle("2026-10-12"), k);
        assertEquals(List.of("K-03", "K-04"), kodlar(i));
        assertTrue(i.get(0).metin().startsWith("Sağlık raporu"));
        assertEquals("Psikoteknik raporu bitiş tarihi girilmemiş", i.get(1).metin());
    }

    @Test
    void konduktor_K01_pasif_ve_K09_izin_ayni() {
        MakinistBilgisi pasif = kisi(MakinistBilgisi.Tur.KONDUKTOR, LocalDate.of(2030, 1, 1), false, MakinistBilgisi.Durum.PASIF, List.of());
        List<Ihlal> i = belge(ogle("2026-10-12"), pasif);
        assertEquals(List.of("K-01"), kodlar(i));
        assertEquals("Kondüktör durumu PASIF (AKTIF değil)", i.get(0).metin());

        MakinistBilgisi izinli = kisi(MakinistBilgisi.Tur.KONDUKTOR, LocalDate.of(2030, 1, 1), false, MakinistBilgisi.Durum.AKTIF,
                List.of(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), MakinistBilgisi.IzinDurumu.ONAY)));
        assertEquals(List.of("K-09"), kodlar(belge(ogle("2026-10-12"), izinli)));
    }

    @Test
    void zamanKurallari_turdenBagimsiz_ayniIhlal() {
        // haftada 40 s + 6 s = 46 s > 45 s (K-14) ve 22:00'da biten görev + ertesi gün 06:00 (K-11): kondüktör ve makinist birebir aynı ihlaller
        List<Gorev> diger = List.of(
                gorev(10, "2026-10-12T14:00", "2026-10-12T22:00"), gorev(11, "2026-10-13T06:00", "2026-10-13T14:00"), gorev(12, "2026-10-14T06:00", "2026-10-14T14:00"),
                gorev(13, "2026-10-15T06:00", "2026-10-15T14:00"), gorev(14, "2026-10-16T06:00", "2026-10-16T14:00"));
        Gorev aday = gorev(1, "2026-10-17T06:00", "2026-10-17T12:00");
        MakinistBilgisi k = konduktor();
        MakinistBilgisi m = kisi(MakinistBilgisi.Tur.MAKINIST, LocalDate.of(2030, 1, 1), true, MakinistBilgisi.Durum.AKTIF, List.of());
        // OK-24: varsayılan K-14 KAPALI; bu test K-14 ihlalinin türden bağımsızlığını sınar, kural açıkça kurulur
        List<Ihlal> ik = KuralMotoru.degerlendir(baglam(aday, diger, k, param(java.util.Map.of("K-14", Siddet.ENGEL))));
        List<Ihlal> im = KuralMotoru.degerlendir(baglam(aday, diger, m, param(java.util.Map.of("K-14", Siddet.ENGEL))));
        assertEquals(im, ik);
        assertTrue(kodlar(ik).contains("K-14"), ik.toString());
        List<Ihlal> gece = KuralMotoru.degerlendir(baglam(gorev(2, "2026-10-13T06:00", "2026-10-13T10:00"), List.of(gorev(3, "2026-10-12T14:00", "2026-10-12T22:00")), k, param()));
        assertTrue(kodlar(gece).contains("K-11"), gece.toString());
    }
}
