package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.ESKISEHIR;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.param;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * F3 / MK-172 / AK-3.1, AK-3.3: sınıra yakınlık sınır matrisi (saf, DB'siz). Her kural için marj-1 dk / marj / sınır / sınır+1 dk; KAPALI; ölçülen == {@code Ihlal.olculen}
 * eşitliği (sınır oynatılarak aynı ölçü ihlal olur).
 */
class YakinlikKontroluTest {

    /** marj: günlük 60 dk, gece 30 dk, dinlenme 60 dk, hafta tatili 120 dk, yıllık 1 saat, aktarma 10 dk, gece hafta 1. */
    static final YakinlikAyarlari AYAR = new YakinlikAyarlari(true, 60, 30, 60, 120, 1, 10, 1);

    private static List<Yakinlik> yak(Gorev aday, List<Gorev> diger, Parametreler p) {
        return YakinlikKontrolu.kontrol(baglam(aday, diger, p), AYAR);
    }

    private static List<Yakinlik> kod(List<Yakinlik> l, String kod) {
        return l.stream().filter(y -> y.kod().equals(kod)).toList();
    }

    private static List<Ihlal> ihlal(Gorev aday, List<Gorev> diger, Parametreler p, String kod) {
        return TestVerisi.kod(KuralMotoru.degerlendir(baglam(aday, diger, p)), kod);
    }

    /** Tek bir ölçü için matris: bitişler (aday 06:00'da başlar) -> beklenen kalan dk ya da -1 (işaret yok). */
    private static void gunlukMatris(String kod, Parametreler p, List<Gorev> diger, String gun, Map<String, Long> bitisKalan) {
        bitisKalan.forEach((bitis, kalan) -> {
            Gorev aday = gorev(99, gun + "T06:00", gun + "T" + bitis);
            List<Yakinlik> y = kod(yak(aday, diger, p), kod);
            if (kalan < 0) {
                assertThat(y).as(kod + " bitiş " + bitis).isEmpty();
            } else {
                assertThat(y).as(kod + " bitiş " + bitis).hasSize(1);
                assertThat(y.getFirst().kalan()).as(kod + " kalan " + bitis).isEqualTo((double) kalan);
            }
        });
    }

    // ---------- K-13 ----------

    @Test
    void K13_matris_9s59_yok_10s_var60_11s_var0_11s01_ihlalYakinlikYok() {
        Parametreler p = param();
        gunlukMatris("K-13", p, List.of(), "2026-10-12", Map.of("15:59", -1L, "16:00", 60L, "17:00", 0L));
        Gorev ihlalli = gorev(99, "2026-10-12T06:00", "2026-10-12T17:01");
        assertThat(kod(yak(ihlalli, List.of(), p), "K-13")).as("sınır aşıldı: yakınlık değil ihlal").isEmpty();
        assertThat(ihlal(ihlalli, List.of(), p, "K-13")).hasSize(1);
        Yakinlik y = kod(yak(gorev(99, "2026-10-12T06:00", "2026-10-12T16:20"), List.of(), p), "K-13").getFirst();
        assertThat(y.olculen()).isEqualTo(620.0);
        assertThat(y.sinir()).isEqualTo(660.0);
        assertThat(y.birim()).isEqualTo("dk");
        assertThat(y.metin()).isEqualTo("Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s");
    }

    @Test
    void K13_kapaliKuralYok_marjAktifDegilYok_marjSifirSinirdaVar() {
        Gorev aday = gorev(99, "2026-10-12T06:00", "2026-10-12T16:30");
        assertThat(kod(yak(aday, List.of(), param(Map.of("K-13", Siddet.KAPALI))), "K-13")).isEmpty();
        assertThat(YakinlikKontrolu.kontrol(baglam(aday, List.of(), param()), YakinlikAyarlari.kapali())).isEmpty();
        YakinlikAyarlari sifir = new YakinlikAyarlari(true, 0, 0, 0, 0, 0, 0, 0);
        assertThat(kod(YakinlikKontrolu.kontrol(baglam(aday, List.of(), param()), sifir), "K-13")).as("marj 0: yalnız tam sınırda").isEmpty();
        assertThat(kod(YakinlikKontrolu.kontrol(baglam(gorev(99, "2026-10-12T06:00", "2026-10-12T17:00"), List.of(), param()), sifir), "K-13")).hasSize(1);
    }

    @Test
    void yolculukSureSayilirFalse_gunToplamaGirmez_sayilirsaGirer() {
        Gorev surus = gorev(2, "2026-10-12T12:00", "2026-10-12T15:00");
        Gorev yolSayilmaz = Gorev.yolculukOlustur(1, ANKARA, ANKARA, 10, 20, an("2026-10-12T04:00"), an("2026-10-12T04:00"), an("2026-10-12T11:30"), false);
        Gorev yolSayilir = Gorev.yolculukOlustur(1, ANKARA, ANKARA, 10, 20, an("2026-10-12T04:00"), an("2026-10-12T04:00"), an("2026-10-12T11:30"), true);
        assertThat(kod(yak(surus, List.of(yolSayilmaz), param()), "K-13")).as("yolculuk 7,5 s sayılmaz: 3 s").isEmpty();
        assertThat(kod(yak(surus, List.of(yolSayilir), param()), "K-13")).as("sayılırsa 10,5 s").hasSize(1);
    }

    // ---------- K-14 ----------

    @Test
    void K14_varsayilanKapali_yok_acikken_matris_43s59_yok_44s_var60_45s_var0_45s01_ihlal() {
        List<Gorev> hafta = List.of(gorev(1, "2026-10-12T06:00", "2026-10-12T15:00"), gorev(2, "2026-10-13T06:00", "2026-10-13T15:00"),
                gorev(3, "2026-10-14T06:00", "2026-10-14T15:00"), gorev(4, "2026-10-15T06:00", "2026-10-15T15:00")); // 36 s
        Parametreler engel = param(Map.of("K-14", Siddet.ENGEL));
        Gorev kapaliYok = gorev(99, "2026-10-16T06:00", "2026-10-16T15:00");
        assertThat(kod(yak(kapaliYok, hafta, param()), "K-14")).as("K-14 varsayılan KAPALI").isEmpty();
        gunlukMatris("K-14", engel, hafta, "2026-10-16", Map.of("13:59", -1L, "14:00", 60L, "15:00", 0L));
        assertThat(kod(yak(gorev(99, "2026-10-16T06:00", "2026-10-16T15:01"), hafta, engel), "K-14")).isEmpty();
        assertThat(ihlal(gorev(99, "2026-10-16T06:00", "2026-10-16T15:01"), hafta, engel, "K-14")).hasSize(1);
    }

    // ---------- K-15 ----------

    @Test
    void K15_yillikFazlaMesai_matris_yok_var60_var0_ihlalYakinlikYok() {
        Parametreler p = param("haftalik.normal_saat", "2", "yillik.fazla_mesai_max_saat", "8"); // fazla mesai = süre - 2 s; sınır 8 s; marj 1 s
        gunlukMatris("K-15", p, List.of(), "2026-10-12", Map.of("14:59", -1L, "15:00", 60L, "16:00", 0L)); // 8 s 59 dk: 6 s 59 dk (kalan 61), 9 s: 7 s, 10 s: 8 s
        Gorev ihlalli = gorev(99, "2026-10-12T06:00", "2026-10-12T16:01");
        assertThat(kod(yak(ihlalli, List.of(), p), "K-15")).isEmpty();
        assertThat(ihlal(ihlalli, List.of(), p, "K-15")).hasSize(1);
    }

    @Test
    void K15_ihlalVeyaFazlaMesaiArtisiYoksaYok() {
        Parametreler p = param("haftalik.normal_saat", "2", "yillik.fazla_mesai_max_saat", "8");
        Gorev once = gorev(1, "2026-10-12T06:00", "2026-10-12T16:00"); // 8 s fazla mesai zaten var
        Gorev aday = gorev(2, "2026-10-14T06:00", "2026-10-14T06:30"); // 30 dk daha: artış var, 8 s 30 dk > 8 s: ihlal
        assertThat(kod(yak(aday, List.of(once), p), "K-15")).isEmpty();
        Parametreler normal45 = param("yillik.fazla_mesai_max_saat", "8");
        assertThat(kod(yak(aday, List.of(once), normal45), "K-15")).as("hafta 45 s altında: fazla mesai artmaz").isEmpty();
    }

    // ---------- K-16 / K-18 ----------

    @Test
    void K16_geceNobeti_matris_6s59_yok_7s_var30_7s30_var0_7s31_ihlal() {
        Parametreler p = param();
        for (var e : Map.of("04:59", -1L, "05:00", 30L, "05:30", 0L).entrySet()) {
            Gorev aday = gorev(99, "2026-10-12T22:00", "2026-10-13T" + e.getKey());
            List<Yakinlik> y = kod(yak(aday, List.of(), p), "K-16");
            if (e.getValue() < 0) {
                assertThat(y).as("bitiş " + e.getKey()).isEmpty();
            } else {
                assertThat(y).as("bitiş " + e.getKey()).hasSize(1);
                assertThat(y.getFirst().kalan()).isEqualTo((double) e.getValue());
            }
        }
        Gorev ihlalli = gorev(99, "2026-10-12T22:00", "2026-10-13T05:31");
        assertThat(kod(yak(ihlalli, List.of(), p), "K-16")).isEmpty();
        assertThat(ihlal(ihlalli, List.of(), p, "K-16")).hasSize(1);
    }

    @Test
    void K16_geceYarisiAsanNobet_toplamNobetSuresiOlculur_gunduzNobetiYok() {
        Gorev gece = gorev(99, "2026-10-12T22:00", "2026-10-13T05:10");
        assertThat(kod(yak(gece, List.of(), param()), "K-16").getFirst().olculen()).isEqualTo(430.0); // iki güne bölünse de nöbet süresi
        Gorev gunduz = gorev(99, "2026-10-12T08:00", "2026-10-12T15:10");
        assertThat(kod(yak(gunduz, List.of(), param()), "K-16")).as("gündüz nöbeti gece sayılmaz").isEmpty();
    }

    @Test
    void K18_ardisikGeceHaftasi_matris() {
        Parametreler p = param("gece.ard_hafta_max", "3");
        Gorev aday = gorev(99, "2026-10-12T22:00", "2026-10-13T02:00");
        Gorev h1 = gorev(1, "2026-10-06T22:00", "2026-10-07T02:00");
        Gorev h2 = gorev(2, "2026-09-29T22:00", "2026-09-30T02:00");
        Gorev h3 = gorev(3, "2026-09-22T22:00", "2026-09-23T02:00");
        assertThat(kod(yak(aday, List.of(), p), "K-18")).as("seri 1, sınır 3, kalan 2 > marj 1").isEmpty();
        assertThat(kod(yak(aday, List.of(h1), p), "K-18").getFirst().kalan()).as("seri 2").isEqualTo(1.0);
        assertThat(kod(yak(aday, List.of(h1, h2), p), "K-18").getFirst().kalan()).as("seri 3 = sınır").isEqualTo(0.0);
        assertThat(kod(yak(aday, List.of(h1, h2, h3), p), "K-18")).as("seri 4 > 3: ihlal").isEmpty();
        assertThat(ihlal(aday, List.of(h1, h2, h3), p, "K-18")).hasSize(1);
        Yakinlik y = kod(yak(aday, List.of(h1, h2), p), "K-18").getFirst();
        assertThat(y.birim()).isEqualTo("hafta");
        assertThat(y.olculen()).isEqualTo(3.0);
    }

    /** OK-50b: tek gece haftası "ardışık" değildir. {@code gece.ard_hafta_max=1} iken her gece görevi "1 / 1" işaretleniyordu (demo masada satırların %56'sı); seri ≥ 2 olmadıkça yakınlık üretilmez. */
    @Test
    void K18_tekGeceHaftasi_ardisikDegil_sinir1iken_yakinlikYok_seri2ihlal() {
        Parametreler p = param("gece.ard_hafta_max", "1");
        Gorev aday = gorev(99, "2026-10-12T22:00", "2026-10-13T02:00");
        Gorev h1 = gorev(1, "2026-10-06T22:00", "2026-10-07T02:00");
        assertThat(kod(yak(aday, List.of(), p), "K-18")).as("seri 1, sınır 1: ardışık değil").isEmpty();
        assertThat(ihlal(aday, List.of(), p, "K-18")).as("ihlal de yok").isEmpty();
        assertThat(kod(yak(aday, List.of(h1), p), "K-18")).as("seri 2 > 1: ihlal, işaret yok").isEmpty();
        assertThat(ihlal(aday, List.of(h1), p, "K-18")).hasSize(1);
        Parametreler iki = param("gece.ard_hafta_max", "2");
        assertThat(kod(yak(aday, List.of(h1), iki), "K-18").getFirst().kalan()).as("seri 2 = sınır 2: gerçek ardışıklık, işaret kalır").isEqualTo(0.0);
    }

    @Test
    void K18_haftaZatenGeceHaftasiysaYeniSeriYok() {
        Parametreler p = param("gece.ard_hafta_max", "1");
        Gorev aday = gorev(99, "2026-10-14T22:00", "2026-10-15T02:00");
        Gorev ayniHafta = gorev(1, "2026-10-12T22:00", "2026-10-13T02:00");
        assertThat(kod(yak(aday, List.of(ayniHafta), p), "K-18")).as("aday haftayı yeni gece haftası yapmıyor").isEmpty();
    }

    // ---------- K-11 / K-12 ----------

    @Test
    void K11_evDinlenmesi_matris_marjIci_var_esikteVar0_altiIhlalYakinlikYok_ustuYok() {
        Parametreler p = param();
        Gorev once = gorev(1, "2026-10-12T06:00", "2026-10-12T10:00"); // Ankara'da biter (bağlı gar)
        // aday başlangıcı: 10:00 + 11 s = 21:00 (eşik); +60 dk = 22:00 (marj sonu)
        String[][] durum = { { "20:59", "ihlal" }, { "21:00", "0" }, { "22:00", "60" }, { "22:01", "yok" } };
        for (String[] d : durum) {
            Gorev aday = gorev(99, "2026-10-12T" + d[0], "2026-10-12T23:30");
            List<Yakinlik> y = kod(yak(aday, List.of(once), p), "K-11");
            if (d[1].equals("ihlal") || d[1].equals("yok")) {
                assertThat(y).as("aday " + d[0]).isEmpty();
            } else {
                assertThat(y).as("aday " + d[0]).hasSize(1);
                assertThat(y.getFirst().kalan()).isEqualTo(Double.parseDouble(d[1]));
            }
        }
        assertThat(ihlal(gorev(99, "2026-10-12T20:59", "2026-10-12T23:30"), List.of(once), p, "K-11")).hasSize(1);
    }

    @Test
    void K11_sonrakiGorevleDinlenme_ve_K12_disMerkez() {
        Parametreler p = param();
        Gorev sonra = gorev(2, "2026-10-13T06:00", "2026-10-13T10:00");
        Gorev aday = gorev(99, "2026-10-12T14:00", "2026-10-12T19:30"); // 19:30 -> 06:00 = 10 s 30 dk < 11 s: ihlal
        assertThat(kod(yak(aday, List.of(sonra), p), "K-11")).isEmpty();
        Gorev aday2 = gorev(99, "2026-10-12T14:00", "2026-10-12T19:00"); // tam 11 s: sınırda
        assertThat(kod(yak(aday2, List.of(sonra), p), "K-11").getFirst().metin()).startsWith("Sonraki görevle dinlenme");
        // dış merkez: önceki görev Eskişehir'de biter (bağlı gar Ankara)
        Gorev once = gorev(1, ANKARA, ESKISEHIR, "2026-10-12T06:00", "2026-10-12T10:00");
        Gorev adayDis = gorev(99, ESKISEHIR, ANKARA, "2026-10-12T21:30", "2026-10-12T23:30");
        List<Yakinlik> y = yak(adayDis, List.of(once), p);
        assertThat(kod(y, "K-12")).hasSize(1);
        assertThat(kod(y, "K-11")).isEmpty();
        assertThat(kod(yak(adayDis, List.of(once), param(Map.of("K-12", Siddet.KAPALI))), "K-12")).isEmpty();
    }

    // ---------- K-19 ----------

    @Test
    void K19_haftaTatili_matris_enUzunBosluk_esigin120dkIcinde() {
        Parametreler p = param("hafta_tatili.min_saat", "108");
        // Çarşamba 06:00 başlar; bitişe göre en uzun boşluk = bitiş -> sonraki Pazartesi 00:00 (110 s - sapma); eşik 108 s
        String[][] durum = { { "09:59", "yok" }, { "10:00", "120" }, { "12:00", "0" }, { "12:01", "ihlal" } };
        for (String[] d : durum) {
            Gorev aday = gorev(99, "2026-10-14T06:00", "2026-10-14T" + d[0]);
            List<Yakinlik> y = kod(yak(aday, List.of(), p), "K-19");
            if (d[1].equals("ihlal") || d[1].equals("yok")) {
                assertThat(y).as("bitiş " + d[0]).isEmpty();
            } else {
                assertThat(y).as("bitiş " + d[0]).hasSize(1);
                assertThat(y.getFirst().kalan()).isEqualTo(Double.parseDouble(d[1]));
            }
        }
        assertThat(ihlal(gorev(99, "2026-10-14T06:00", "2026-10-14T12:01"), List.of(), p, "K-19")).hasSize(1);
    }

    // ---------- K-23 ----------

    @Test
    void K23_aktarma_matris_31dk_yok_30dk_var10_20dk_var0_19dk_ihlal() {
        Parametreler p = param();
        Gorev once = gorev(1, "2026-10-12T08:00", "2026-10-12T10:00");
        String[][] durum = { { "10:31", "yok" }, { "10:30", "10" }, { "10:20", "0" }, { "10:19", "ihlal" } };
        for (String[] d : durum) {
            Gorev aday = gorev(99, "2026-10-12T" + d[0], "2026-10-12T12:00");
            List<Yakinlik> y = kod(yak(aday, List.of(once), p), "K-23");
            if (d[1].equals("ihlal") || d[1].equals("yok")) {
                assertThat(y).as("aday " + d[0]).isEmpty();
            } else {
                assertThat(y).as("aday " + d[0]).hasSize(1);
                assertThat(y.getFirst().kalan()).isEqualTo(Double.parseDouble(d[1]));
            }
        }
        assertThat(ihlal(gorev(99, "2026-10-12T10:19", "2026-10-12T12:00"), List.of(once), p, "K-23")).hasSize(1);
    }

    @Test
    void K23_kapaliSifirEsikYolculukVeDevamEden_yok() {
        Gorev once = gorev(1, "2026-10-12T08:00", "2026-10-12T10:00");
        Gorev aday = gorev(99, "2026-10-12T10:25", "2026-10-12T12:00");
        assertThat(kod(yak(aday, List.of(once), param(Map.of("K-23", Siddet.KAPALI))), "K-23")).isEmpty();
        assertThat(kod(yak(aday, List.of(once), param("nobet.asgari_aktarma_dk", "0")), "K-23")).isEmpty();
        Gorev yol = Gorev.yolculukOlustur(1, ANKARA, ANKARA, 10, 20, an("2026-10-12T08:00"), an("2026-10-12T08:00"), an("2026-10-12T10:00"), true);
        assertThat(kod(yak(aday, List.of(yol), param()), "K-23")).as("yolculuk geçişini yolculuk.aktarma_dk yönetir").isEmpty();
        Gorev devam = new Gorev(99, ANKARA, ANKARA, 10, 20, an("2026-10-12T10:25"), an("2026-10-12T12:00"), an("2026-10-12T10:25"), an("2026-10-12T12:00"), 100, null, false, true, true);
        assertThat(kod(yak(devam, List.of(once), param()), "K-23")).as("aynı trenin devam eden parçası muaf").isEmpty();
    }

    // ---------- ölçülen == Ihlal.olculen (W3 / K-3) ----------

    @Test
    void olculenDeger_sinirOynatilincaAyniOlcuIhlalOlculeniyleEsit() {
        // K-13: 10 s 20 dk ölçüldü; sınır 10 s'ye çekilince ihlalin ölçülen değeri aynı
        Gorev g13 = gorev(99, "2026-10-12T06:00", "2026-10-12T16:20");
        double k13 = kod(yak(g13, List.of(), param()), "K-13").getFirst().olculen();
        assertThat(ihlal(g13, List.of(), param("gunluk.max_saat", "10"), "K-13").getFirst().olculen()).isEqualTo(k13 / 60.0);
        // K-16
        Gorev g16 = gorev(99, "2026-10-12T22:00", "2026-10-13T05:10");
        double k16 = kod(yak(g16, List.of(), param()), "K-16").getFirst().olculen();
        assertThat(ihlal(g16, List.of(), param("gece.max_saat", "7"), "K-16").getFirst().olculen()).isEqualTo(k16 / 60.0);
        // K-11
        Gorev once = gorev(1, "2026-10-12T06:00", "2026-10-12T10:00");
        Gorev g11 = gorev(99, "2026-10-12T21:30", "2026-10-12T23:30");
        double k11 = kod(yak(g11, List.of(once), param()), "K-11").getFirst().olculen();
        assertThat(ihlal(g11, List.of(once), param("dinlenme.ev_saat", "12"), "K-11").getFirst().olculen()).isEqualTo(k11 / 60.0);
        // K-19
        Gorev g19 = gorev(99, "2026-10-14T06:00", "2026-10-14T11:00");
        double k19 = kod(yak(g19, List.of(), param("hafta_tatili.min_saat", "108")), "K-19").getFirst().olculen();
        assertThat(ihlal(g19, List.of(), param("hafta_tatili.min_saat", "110"), "K-19").getFirst().olculen()).isEqualTo(k19 / 60.0);
        // K-23 (dakika)
        Gorev g23 = gorev(99, "2026-10-12T10:25", "2026-10-12T12:00");
        Gorev oncekiAktarma = gorev(2, "2026-10-12T08:00", "2026-10-12T10:00");
        double k23 = kod(yak(g23, List.of(oncekiAktarma), param()), "K-23").getFirst().olculen();
        assertThat(ihlal(g23, List.of(oncekiAktarma), param("nobet.asgari_aktarma_dk", "30"), "K-23").getFirst().olculen()).isEqualTo(k23);
        // K-15 (fazla mesai toplamı)
        Parametreler p15 = param("haftalik.normal_saat", "2", "yillik.fazla_mesai_max_saat", "8");
        Gorev g15 = gorev(99, "2026-10-12T06:00", "2026-10-12T15:30");
        double k15 = kod(yak(g15, List.of(), p15), "K-15").getFirst().olculen();
        assertThat(ihlal(g15, List.of(), param("haftalik.normal_saat", "2", "yillik.fazla_mesai_max_saat", "7"), "K-15").getFirst().olculen()).isEqualTo(k15 / 60.0);
        // K-18 (hafta)
        Gorev g18 = gorev(99, "2026-10-12T22:00", "2026-10-13T02:00");
        Gorev h1 = gorev(1, "2026-10-06T22:00", "2026-10-07T02:00");
        double k18 = kod(yak(g18, List.of(h1), param("gece.ard_hafta_max", "2")), "K-18").getFirst().olculen();
        assertThat(ihlal(g18, List.of(h1), param("gece.ard_hafta_max", "1"), "K-18").getFirst().olculen()).isEqualTo(k18);
        // K-14
        List<Gorev> hafta = List.of(gorev(1, "2026-10-12T06:00", "2026-10-12T15:00"), gorev(2, "2026-10-13T06:00", "2026-10-13T15:00"));
        Gorev g14 = gorev(99, "2026-10-14T06:00", "2026-10-14T15:00");
        Parametreler e14 = param(Map.of("K-14", Siddet.ENGEL), "haftalik.max_saat", "28");
        double k14 = kod(yak(g14, hafta, e14), "K-14").getFirst().olculen();
        assertThat(ihlal(g14, hafta, param(Map.of("K-14", Siddet.ENGEL), "haftalik.max_saat", "26"), "K-14").getFirst().olculen()).isEqualTo(k14 / 60.0);
    }

    @Test
    void sonuclarKararliSirali_veTekrarCagriAyniSonuc() {
        Gorev aday = gorev(99, "2026-10-12T22:00", "2026-10-13T05:10");
        List<Yakinlik> a = yak(aday, List.of(), param());
        assertThat(a).extracting(Yakinlik::kod).isSorted();
        assertThat(yak(aday, List.of(), param())).isEqualTo(a);
    }
}
