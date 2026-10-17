package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.degerlendir;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.param;
import static com.makinistatama.kural.TestVerisi.kod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QA (P6): takvim sınırlarında (yıl dönümü, ay sonu) kural davranışı. Haftalar Pzt-Paz; 28 Ara 2026 Pazartesi,
 * 4 Oca 2027 Pazartesi. Hepsi KuralMotoru uzerinden, DB'siz.
 */
class KuralTakvimSinirTest {

    private static List<Gorev> haftaIci(String gun1, String gun2, String gun3, String gun4, String baslangicSaat, String bitisSaat) {
        List<Gorev> g = new ArrayList<>();
        long id = 1;
        for (String gun : List.of(gun1, gun2, gun3, gun4)) {
            g.add(gorev(id++, gun + "T" + baslangicSaat, gun + "T" + bitisSaat));
        }
        return g;
    }

    @Test
    void K18_ardisikGeceHaftasi_yilDonumunuAsar_28Ara_haftasi_ile_4Oca_haftasi_ardisik() {
        List<Gorev> diger = List.of(gorev(1, "2026-12-29T22:00", "2026-12-30T05:00")); // 28 Ara - 3 Oca haftasi
        List<Ihlal> i = kod(degerlendir(gorev(2, "2027-01-05T23:00", "2027-01-06T05:00"), diger), "K-18"); // 4-10 Oca haftasi

        assertEquals(1, i.size(), "yıl dönümünde ardışık gece haftası kaçırıldı");
        assertEquals("2. ardışık gece haftası > 1", i.get(0).metin());
    }

    @Test
    void K18_yilDonumundeAyniHaftayaDusenGeceGorevleri_ardisikSayilmaz() {
        // 31 Ara ve 1 Oca ayni takvim haftasi (28 Ara - 3 Oca): ikinci gece gorevi yeni ihlal uretmez
        List<Gorev> diger = List.of(gorev(1, "2026-12-31T22:00", "2027-01-01T05:00"));
        assertTrue(kod(degerlendir(gorev(2, "2027-01-02T22:00", "2027-01-03T05:00"), diger), "K-18").isEmpty());
    }

    @Test
    void K14_haftaYilDonumuneTasiyor_40s_arti_5s_45_uygun_6s_46_engel() {
        List<Gorev> diger = haftaIci("2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "06:00", "16:00"); // 4 x 10 s
        Parametreler p = param(java.util.Map.of("K-14", Siddet.ENGEL)); // OK-24: varsayılan KAPALI; kuralın kendisi sınanır
        assertTrue(kod(degerlendir(gorev(9, "2027-01-01T06:00", "2027-01-01T11:00"), diger, p), "K-14").isEmpty());
        List<Ihlal> i = kod(degerlendir(gorev(9, "2027-01-01T06:00", "2027-01-01T12:00"), diger, p), "K-14");
        assertEquals(1, i.size());
        assertEquals(46.0, i.get(0).olculen());
    }

    @Test
    void K14_ertesiHaftaToplamiaEklenmez_4Oca_haftasi_bos() {
        List<Gorev> diger = haftaIci("2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "06:00", "16:00");
        assertTrue(kod(degerlendir(gorev(9, "2027-01-04T06:00", "2027-01-04T16:00"), diger), "K-14").isEmpty());
    }

    @Test
    void K19_haftaTatili_yilDonumundeki_Pzt_Cmt_dolu_Pazar_3Oca_engel() {
        List<Gorev> diger = new ArrayList<>();
        String[] gunler = { "2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02" };
        for (int i = 0; i < gunler.length; i++) {
            diger.add(gorev(i + 1, gunler[i] + "T06:00", gunler[i] + "T14:00"));
        }
        List<Ihlal> k19 = kod(degerlendir(gorev(9, "2027-01-03T06:00", "2027-01-03T14:00"), diger), "K-19");

        assertEquals(1, k19.size(), "Pazar da çalışılırsa haftada 24 saat kesintisiz boşluk kalmaz");
    }

    @Test
    void K13_ayinSonundakiGeceGorevi_gunSinirindaBolunur_1Kasim_toplamina_6s_yazilir() {
        List<Gorev> diger = List.of(gorev(1, "2026-10-31T22:00", "2026-11-01T06:00")); // 31 Eki'ne 2 s, 1 Kas'a 6 s
        assertTrue(kod(degerlendir(gorev(2, "2026-11-01T12:00", "2026-11-01T17:00"), diger), "K-13").isEmpty()); // 6 + 5 = 11 s
        List<Ihlal> i = kod(degerlendir(gorev(2, "2026-11-01T12:00", "2026-11-01T17:01"), diger), "K-13"); // 11 s 1 dk
        assertEquals(1, i.size());
    }

    @Test
    void K13_ayinSonundakiGeceGorevi_31Eki_toplamina_yalniz_2s_yazilir() {
        List<Gorev> diger = List.of(gorev(1, "2026-10-31T22:00", "2026-11-01T06:00"));
        // 31 Eki: 2 s + 9 s (08:00-17:00) = 11 s uygun; K-11 bu testin konusu degil
        assertTrue(kod(degerlendir(gorev(2, "2026-10-31T08:00", "2026-10-31T17:00"), diger), "K-13").isEmpty());
        assertEquals(1, kod(degerlendir(gorev(2, "2026-10-31T08:00", "2026-10-31T17:01"), diger), "K-13").size());
    }

    // ------------------------------------------------------------------ performans review W1 (MK-55 sınır vakaları): hafta/gün sınırı mutantlarını yakalar
    // 12 Eki 2026 Pazartesi. Gece penceresi 20:00-06:00, gece.ard_hafta_max = 1 (varsayılan test parametreleri).

    /** Önceki hafta (5-11 Eki) gece haftası: Çarşamba gecesi nöbeti. */
    private static Gorev oncekiHaftaGecesi() {
        return gorev(1, "2026-10-07T22:00", "2026-10-08T05:00");
    }

    @Test
    void K18_haftaninIlkAni_PazartesiSifirSifir_buHaftayaSayilir_oncekiHaftayaDegil() {
        // aday nöbeti Pzt 00:00:00'da başlıyor: hafta 12 Eki; önceki hafta da gece haftası => 2 ardışık
        List<Ihlal> i = kod(degerlendir(gorev(2, "2026-10-12T00:00", "2026-10-12T06:00"), List.of(oncekiHaftaGecesi())), "K-18");

        assertEquals(1, i.size(), "Pzt 00:00'da başlayan nöbet haftanın dışına düştü");
        assertEquals("2. ardışık gece haftası > 1", i.get(0).metin());
    }

    @Test
    void K18_haftaninSonAni_Pazar_23_59_59_999999999_buHaftayaSayilir() {
        List<Ihlal> i = kod(degerlendir(gorev(2, "2026-10-18T23:59:59.999999999", "2026-10-19T06:00"), List.of(oncekiHaftaGecesi())), "K-18");

        assertEquals(1, i.size(), "Pazar 23:59:59.999999999'da başlayan nöbet haftanın dışına düştü");
        assertEquals("2. ardışık gece haftası > 1", i.get(0).metin());
    }

    /** Mutant M8 (GeceKurallari {@code isBefore(son)} -> {@code !isAfter(son)}): bir SONRAKİ haftanın Pzt 00:00 nöbeti ara haftayı gece haftası yapmamalı. */
    @Test
    void K18_sonrakiHaftaninIlkAni_PazartesiSifirSifir_araHaftayiGeceHaftasiYapmaz() {
        // aday: Çar 7 Eki gecesi (hafta 5 Eki); diğer: Pzt 19 Eki 00:00 (hafta 19 Eki). Ara hafta (12 Eki) boş: ardışık gece haftası YOK
        List<Gorev> diger = List.of(gorev(1, "2026-10-19T00:00", "2026-10-19T06:00"));
        assertTrue(kod(degerlendir(gorev(2, "2026-10-07T22:00", "2026-10-08T05:00"), diger), "K-18").isEmpty(), "19 Eki 00:00 nöbeti 12 Eki haftasına sayıldı");
    }

    @Test
    void K18_sinirdakiNobetlerAyniHaftadaToplanir_hafta_min_gorev_2() {
        // Pzt 00:00 ve Pazar 23:59:59.999999999 nöbetleri aynı haftanın iki gece nöbeti; min 2 ile hafta yalnız ikisi birlikte sayılırsa gece haftasıdır
        List<Gorev> diger = List.of(gorev(1, "2026-10-05T22:00", "2026-10-06T05:00"), gorev(2, "2026-10-07T22:00", "2026-10-08T05:00"), // önceki hafta: 2 nöbet
                gorev(3, "2026-10-12T00:00", "2026-10-12T06:00"));
        List<Ihlal> i = kod(degerlendir(gorev(4, "2026-10-18T23:59:59.999999999", "2026-10-19T06:00"), diger, param("gece.hafta_min_gorev", "2")), "K-18");

        assertEquals(1, i.size());
        // tek nöbet (ikisinden biri eksik) haftayı gece haftası yapmaz
        assertTrue(kod(degerlendir(gorev(4, "2026-10-18T23:59:59.999999999", "2026-10-19T06:00"), diger.subList(0, 2), param("gece.hafta_min_gorev", "2")), "K-18").isEmpty());
    }

    private static MakinistBilgisi eskiSurusluMakinist() {
        TestVerisi.M m = new TestVerisi.M();
        m.sonSurus = LocalDate.of(2025, 1, 1);
        m.hat.put(TestVerisi.HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)));
        return m.yap();
    }

    @Test
    void K07_K08_gecmisGorevVarisiTamAdayGununSifirSifirIse_gunSayilmaz_birOncekiNanosaniyeSayilir() {
        Gorev aday = gorev(1, "2026-10-12T08:00", "2026-10-12T12:00"); // varış günü 12 Eki; son sürüş 2025-01-01 (> 18 ay): K-07/K-08 ihlali
        // varış tam 12 Eki 00:00: ONCE bitmiş değil (gün 12 Eki) -> son sürüşü yenilemez
        Gorev gunBasindaBiten = gorev(2, "2026-10-11T20:00", "2026-10-12T00:00");
        List<Ihlal> ihlal = BelgeKurallari.kontrol(TestVerisi.baglam(aday, List.of(gunBasindaBiten), eskiSurusluMakinist(), param()));
        assertEquals(1, kod(ihlal, "K-07").size(), "varış == gün başı olan görev son sürüş sayıldı");
        assertEquals(1, kod(ihlal, "K-08").size());

        // varış 11 Eki 23:59:59.999999999: önceki günde biter -> son sürüş 11 Eki, ihlal yok
        Gorev birOnceki = gorev(2, "2026-10-11T20:00", "2026-10-11T23:59:59.999999999");
        List<Ihlal> yok = BelgeKurallari.kontrol(TestVerisi.baglam(aday, List.of(birOnceki), eskiSurusluMakinist(), param()));
        assertTrue(kod(yok, "K-07").isEmpty());
        assertTrue(kod(yok, "K-08").isEmpty());
    }
}
