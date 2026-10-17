package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.ARAC;
import static com.makinistatama.kural.TestVerisi.ESKISEHIR;
import static com.makinistatama.kural.TestVerisi.HAT;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kod;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.ZamanHesabi.Aralik;

/**
 * F2b MK-39/FR-03..FR-05: yolculuk konumu taşır (K-20), çakışmaya ve dinlenmeye girer (K-10, K-11/K-12), çalışma/gece süresinden
 * {@code sureSayilir} ile sayılır ya da sayılmaz; nöbet zinciri her durumda yolculuğu görür.
 */
class YolculukSureKuralTest {

    private static Gorev yolculuk(long id, long kalkisGar, long varisGar, String bas, String bit, boolean sayilir) {
        return Gorev.yolculukOlustur(id, kalkisGar, varisGar, HAT, ARAC, an(bas), an(bas), an(bit), sayilir);
    }

    private static Gorev e1(String bas, String bit) {
        return yolculuk(1, ANKARA, ESKISEHIR, bas, bit, true);
    }

    private static List<Ihlal> deger(Gorev aday, List<Gorev> diger) {
        return KuralMotoru.degerlendir(baglam(aday, diger));
    }

    // ---- FR-03 konum ve çakışma

    @Test
    void k20_yolculukVarisiKonumSayilir_ikiYonlu_nobetBirlesir() {
        Gorev yolcu = e1("2026-10-12T08:00", "2026-10-12T09:30");
        Gorev s2 = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T10:30", "2026-10-12T15:00");

        List<Ihlal> sefer = deger(s2, List.of(yolcu));
        List<Ihlal> tersi = deger(yolcu, List.of(s2));

        assertTrue(kod(sefer, "K-20").isEmpty(), sefer.toString());
        assertTrue(kod(sefer, "K-10").isEmpty(), sefer.toString());
        assertTrue(kod(tersi, "K-20").isEmpty(), tersi.toString());
        assertTrue(kod(tersi, "K-10").isEmpty(), tersi.toString());
        List<Nobet> nobetler = NobetHesabi.grupla(List.of(yolcu, s2), 240);
        assertEquals(1, nobetler.size());
        assertEquals(an("2026-10-12T08:00"), nobetler.get(0).baslangic());
        assertEquals(an("2026-10-12T15:00"), nobetler.get(0).bitis());
        // yolculuk olmasaydı Ankara'dan Eskişehir'de başlayan sefer: K-20
        assertFalse(kod(deger(s2, List.of()), "K-20").isEmpty());
    }

    @Test
    void k10_yolculukSeferleCakisirsa_engel() {
        Gorev yolcu = e1("2026-10-12T09:00", "2026-10-12T10:35");
        Gorev s2 = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T10:30", "2026-10-12T15:00");

        List<Ihlal> i = kod(deger(s2, List.of(yolcu)), "K-10");

        assertEquals(1, i.size());
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
        assertTrue(i.get(0).metin().contains("5 dk örtüşüyor"), i.get(0).metin());
    }

    // ---- FR-04 çalışma süresi

    @Test
    void nobetCalismasi_yolculukSayilirsa6s_sayilmazsa4s30() {
        Gorev s2 = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T10:30", "2026-10-12T15:00");

        assertEquals(360, toplam(NobetHesabi.calismaAraliklari(List.of(e1("2026-10-12T08:00", "2026-10-12T09:30"), s2), 240, false)));
        assertEquals(270, toplam(NobetHesabi.calismaAraliklari(List.of(yolculuk(1, ANKARA, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T09:30", false), s2), 240, false)));
    }

    @Test
    void beklemeSayilirken_nobetAraligi_yalnizSayilanGorevlerin_enErkenEnGecAraligi() {
        Gorev s2 = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T10:30", "2026-10-12T15:00");
        Gorev sayilan = e1("2026-10-12T08:00", "2026-10-12T09:30");
        Gorev sayilmayan = yolculuk(1, ANKARA, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T09:30", false);

        assertEquals(List.of(new Aralik(an("2026-10-12T08:00"), an("2026-10-12T15:00"))), NobetHesabi.calismaAraliklari(List.of(sayilan, s2), 240, true));
        assertEquals(List.of(new Aralik(an("2026-10-12T10:30"), an("2026-10-12T15:00"))), NobetHesabi.calismaAraliklari(List.of(sayilmayan, s2), 240, true));
        assertTrue(NobetHesabi.calismaAraliklari(List.of(sayilmayan), 240, true).isEmpty(), "yalnız sayılmayan yolculuk: aralık yok");
        assertEquals(1, NobetHesabi.grupla(List.of(sayilmayan, s2), 240).size(), "zincir sayılmayan yolculuğu yine görür");
    }

    @Test
    void k13_gunlukSinir_660uygun_661engel() {
        Gorev yolcu = yolculuk(1, ANKARA, ESKISEHIR, "2026-10-12T06:00", "2026-10-12T11:00", true); // 300 dk
        Gorev tamSinir = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T11:00", "2026-10-12T17:00"); // 360 dk: toplam 660
        Gorev bir_dk_fazla = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T11:00", "2026-10-12T17:01");

        assertTrue(kod(deger(tamSinir, List.of(yolcu)), "K-13").isEmpty());
        List<Ihlal> i = kod(deger(bir_dk_fazla, List.of(yolcu)), "K-13");
        assertEquals(1, i.size());
        assertEquals(Siddet.ENGEL, i.get(0).siddet());
        assertTrue(i.get(0).metin().contains("11 s 1 dk > 11 s"), i.get(0).metin());
        // sayılmayan yolculukla aynı gün toplamı yalnız sefer
        Gorev sayilmayan = yolculuk(1, ANKARA, ESKISEHIR, "2026-10-12T06:00", "2026-10-12T11:00", false);
        assertTrue(kod(deger(bir_dk_fazla, List.of(sayilmayan)), "K-13").isEmpty());
    }

    // ---- FR-05 dinlenme ve gece

    @Test
    void k12_disGardaYolculuk21deBaslarsa_dinlenme9s30_22_30daBaslarsaYok() {
        Gorev onceki = gorev(1, ANKARA, ESKISEHIR, "2026-10-12T08:00", "2026-10-12T11:30"); // Eskişehir (dış merkez) 11:30'da biter
        Gorev erken = yolculuk(2, ESKISEHIR, ANKARA, "2026-10-12T21:00", "2026-10-13T02:30", true);
        Gorev gec = yolculuk(2, ESKISEHIR, ANKARA, "2026-10-12T22:30", "2026-10-13T04:00", true);

        List<Ihlal> a = kod(deger(erken, List.of(onceki)), "K-12");
        assertEquals(1, a.size());
        assertTrue(a.get(0).metin().startsWith("Dinlenme 9 s 30 dk < 11 s"), a.get(0).metin());
        assertTrue(kod(deger(gec, List.of(onceki)), "K-12").isEmpty());
        assertTrue(kod(deger(gec, List.of(onceki)), "K-16").isEmpty(), "gece nöbeti 5 s 30 ≤ 7 s 30");
    }

    @Test
    void k16_geceNobetiSuresi_yolculukSayilirsaEngel_sayilmazsaYok() {
        Gorev geceSefer = gorev(3, ESKISEHIR, ESKISEHIR, "2026-10-13T03:30", "2026-10-13T05:30"); // 2 s gece
        Gorev sayilan = yolculuk(2, ANKARA, ESKISEHIR, "2026-10-12T21:00", "2026-10-13T03:00", true); // 6 s gece
        Gorev sayilmayan = yolculuk(2, ANKARA, ESKISEHIR, "2026-10-12T21:00", "2026-10-13T03:00", false);

        List<Ihlal> var = kod(deger(geceSefer, List.of(sayilan)), "K-16");
        assertEquals(1, var.size());
        assertEquals(Siddet.ENGEL, var.get(0).siddet());
        assertTrue(kod(deger(geceSefer, List.of(sayilmayan)), "K-16").isEmpty(), "sayılmayan yolculuk gece süresine girmez");
        assertTrue(kod(deger(geceSefer, List.of(sayilmayan)), "K-20").isEmpty(), "ama konumu yine taşır");
    }

    @Test
    void bekleme_sayilirParametresi_yolculuklaDaTutarli() {
        Gorev s2 = gorev(2, ESKISEHIR, ESKISEHIR, "2026-10-12T10:30", "2026-10-12T15:00");
        Parametreler bekleme = param("nobet.bekleme_sayilir", "1");
        List<Ihlal> i = KuralMotoru.degerlendir(baglam(s2, List.of(e1("2026-10-12T08:00", "2026-10-12T09:30")), bekleme));

        assertFalse(kodlar(i).contains("K-13"), i.toString());
    }

    private static long toplam(List<Aralik> l) {
        return l.stream().mapToLong(Aralik::dakika).sum();
    }
}
