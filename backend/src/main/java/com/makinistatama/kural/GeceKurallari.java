package com.makinistatama.kural;

import static com.makinistatama.kural.ZamanHesabi.sure;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gece kurallari, NOBET bazli (MK-2): K-16 gece nobetinin suresi, K-17 gece nobeti haftalik normal saati
 * astiramaz, K-18 ust uste gece haftasi. Nobet "gece nobeti" = calisma suresinin {@code gece.gorev_orani}'ndan
 * FAZLASI gece penceresine dusuyor (esit = gunduz). Nobet haftasi = baslangicinin haftasi.
 */
public final class GeceKurallari {

    private GeceKurallari() {
    }

    static List<Ihlal> kontrol(KuralBaglami b, Onhesap h) {
        List<Ihlal> o = new ArrayList<>();
        Parametreler p = b.parametreler();
        long[] oz = ozet(h.adayNobet(), p);
        if (!geceMi(oz, p)) {
            gunduzAdayiGeceNobetiniKirar(o, p, h);
            return o;
        }
        geceSuresi(o, p, oz);
        haftalikNormal(o, p, h);
        ardisikHafta(o, p, h);
        return o;
    }

    /** {sure, gece} dakika: nobetin calisma araliklari toplami ve bunun gece penceresiyle kesisimi. */
    static long[] ozet(Nobet n, Parametreler p) {
        return ozet(n, p.saat("gece.baslangic"), p.saat("gece.bitis"), p.bayrak("nobet.bekleme_sayilir"));
    }

    /** Parametre ayrıştırması döngü dışına alınabilsin diye (yıllık geçmişte nöbet başına iki saat ayrıştırması baskın maliyetti). */
    private static long[] ozet(Nobet n, LocalTime gb, LocalTime ge, boolean bekleme) {
        long sure = 0;
        long gece = 0;
        for (Aralik a : NobetHesabi.araliklar(n, bekleme)) {
            sure += a.dakika();
            gece += ZamanHesabi.geceDakika(a.bas(), a.bit(), gb, ge);
        }
        return new long[] {sure, gece};
    }

    static boolean geceMi(long[] oz, Parametreler p) {
        return geceMi(oz, p.oran("gece.gorev_orani"));
    }

    private static boolean geceMi(long[] oz, BigDecimal oran) {
        return oz[0] > 0 && BigDecimal.valueOf(oz[1]).compareTo(oran.multiply(BigDecimal.valueOf(oz[0]))) > 0;
    }

    private static void geceSuresi(List<Ihlal> o, Parametreler p, long[] oz) {
        long max = p.dakika("gece.max_saat");
        if (oz[0] > max) {
            Ihlal.ekle(o, p, "K-16", oz[0] / 60.0, max / 60.0, "s", "Gece nöbeti süresi " + sure(oz[0]) + " > " + sure(max));
        }
    }

    private static void haftalikNormal(List<Ihlal> o, Parametreler p, Onhesap h) {
        long normal = p.dakika("haftalik.normal_saat");
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            long dk = CalismaSuresiKurallari.haftaToplam(h.araliklar(), hafta);
            if (dk > normal) {
                Ihlal.ekle(o, p, "K-17", dk / 60.0, normal / 60.0, "s", "Gece nöbeti haftalık normal süreyi aşırıyor ("
                        + CalismaSuresiKurallari.hafta(hafta) + "): " + sure(dk) + " > " + sure(normal));
            }
        }
    }

    /**
     * K-17, GUNDUZ aday: aday, kendisinden SONRA baslayan kayitli bir gece nobetinin haftalik toplamini ilk kez
     * normal sureyi asirtiyorsa ENGEL. MK-5: gece nobeti yalniz kendisinden onceki gorevlerle degerlendirilir; atama
     * aninda bu iliskinin iki yonu de kontrol edilir ki atamaci baskasini farkinda olmadan kirmizi yapmasin.
     * Hafta basina yalniz ilk (en erken) etkilenen gece nobeti bildirilir.
     */
    private static void gunduzAdayiGeceNobetiniKirar(List<Ihlal> o, Parametreler p, Onhesap h) {
        long normal = p.dakika("haftalik.normal_saat");
        boolean bekleme = p.bayrak("nobet.bekleme_sayilir");
        GeceOlcut gece = null; // S2d: parametre ayrıştırması döngü dışında ve yalnız ilk nöbet bakılırken (gündüz adayında çoğu zaman hiç gerekmez)
        List<LocalDate> etkilenenHaftalar = ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit());
        Set<LocalDate> etkilenen = new HashSet<>(etkilenenHaftalar);
        // S2c: nöbetler başlangıca göre sıralı; adaydan SONRA başlayanlardan başlanır ve son etkilenen haftanın bitiminden sonra başlayan nöbetin hiçbir haftası etkilenen olamaz
        Instant sinir = ZamanHesabi.gunBasi(etkilenenHaftalar.getLast().plusDays(7));
        Set<LocalDate> bildirilen = new HashSet<>();
        List<Nobet> tum = h.tumNobet();
        for (int i = ilkIndeks(tum, h.adayNobet().baslangic(), true); i < tum.size(); i++) {
            Nobet g = tum.get(i);
            if (!g.baslangic().isBefore(sinir)) {
                break;
            }
            if (gece == null) {
                gece = GeceOlcut.of(p);
            }
            if (!gece.gece(g)) {
                continue;
            }
            for (LocalDate hafta : ZamanHesabi.haftalar(g.baslangic(), g.bitis())) {
                if (!etkilenen.contains(hafta) || bildirilen.contains(hafta)
                        || CalismaSuresiKurallari.haftaToplam(h.araliklar(), hafta) <= normal) { // ust sinir: hizli eleme
                    continue;
                }
                long ile = CalismaSuresiKurallari.haftaToplam(kadar(h.tumNobet(), g, bekleme), hafta);
                long onceden = CalismaSuresiKurallari.haftaToplam(kadar(h.digerNobet(), g, bekleme), hafta);
                if (ile > normal && onceden <= normal) {
                    bildirilen.add(hafta);
                    Ihlal.ekle(o, p, "K-17", ile / 60.0, normal / 60.0, "s", ZamanHesabi.tarih(ZamanHesabi.gun(g.baslangic()))
                            + " gece nöbetini haftalık normal süreyi aşırır hale getiriyor ("
                            + CalismaSuresiKurallari.hafta(hafta) + "): " + sure(ile) + " > " + sure(normal));
                }
            }
        }
    }

    /** {@code sinir} nobet dahil, ona kadar (baslangica gore) olan nobetlerin calisma araliklari. */
    private static List<Aralik> kadar(List<Nobet> nobetler, Nobet sinir, boolean bekleme) {
        return NobetHesabi.araliklar(nobetler.stream().filter(n -> !n.baslangic().isAfter(sinir.baslangic())).toList(), bekleme);
    }

    /** Aday gece nobeti yeni bir gece haftasi yaratiyorsa, ardisik gece haftasi sayisi (iki yonlu) esigi asmamali. */
    private static void ardisikHafta(List<Ihlal> o, Parametreler p, Onhesap h) {
        LocalDate w = ZamanHesabi.haftaBasi(ZamanHesabi.gun(h.adayNobet().baslangic()));
        GeceOlcut gece = GeceOlcut.of(p);
        List<Nobet> tum = h.tumNobet();
        if (!geceHaftasi(tum, w, gece) || geceHaftasi(h.digerNobet(), w, gece)) {
            return; // aday haftayi gece haftasi yapmiyor (esige ulasmadi) ya da hafta zaten gece haftasi
        }
        int geri = 0;
        while (geceHaftasi(tum, w.minusWeeks(geri + 1), gece)) {
            geri++;
        }
        int ileri = 0;
        while (geceHaftasi(tum, w.plusWeeks(ileri + 1), gece)) {
            ileri++;
        }
        long max = p.tamsayi("gece.ard_hafta_max");
        int seri = geri + 1 + ileri;
        if (seri > max) {
            Ihlal.ekle(o, p, "K-18", seri, max, "hafta", ileri == 0
                    ? seri + ". ardışık gece haftası > " + max
                    : "Sonraki hafta da gece haftası: " + seri + " ardışık gece haftası > " + max);
        }
    }

    /** Gece nobeti olcutu: parametreler dongu disinda BIR kez ayristirilir (yillik gecmiste nobet basina saat/oran ayristirmasi baskin maliyetti, MK-55). */
    private record GeceOlcut(LocalTime gb, LocalTime ge, boolean bekleme, BigDecimal oran, long min) {

        static GeceOlcut of(Parametreler p) {
            return new GeceOlcut(p.saat("gece.baslangic"), p.saat("gece.bitis"), p.bayrak("nobet.bekleme_sayilir"), p.oran("gece.gorev_orani"),
                    Math.max(1, p.tamsayi("gece.hafta_min_gorev")));
        }

        boolean gece(Nobet n) {
            return geceMi(ozet(n, gb, ge, bekleme), oran);
        }
    }

    /**
     * {@code hafta} (Pazartesi) bir gece haftasi mi: baslangici haftaya dusen en az {@code gece.hafta_min_gorev} (en az 1) gece nobeti var. Nobetler baslangica gore sirali oldugundan
     * yalniz [hafta, hafta + 7 gun) araligi ikili aramayla bulunup gezilir (eskiden her cagrida TUM nobetler gezilip haftaya gore sayilirdi; sonuc ayni, MK-55).
     */
    private static boolean geceHaftasi(List<Nobet> nobetler, LocalDate hafta, GeceOlcut olcut) {
        Instant son = ZamanHesabi.gunBasi(hafta.plusDays(7));
        int adet = 0;
        for (int i = ilkIndeks(nobetler, ZamanHesabi.gunBasi(hafta), false); i < nobetler.size() && nobetler.get(i).baslangic().isBefore(son); i++) {
            if (olcut.gece(nobetler.get(i)) && ++adet >= olcut.min()) {
                return true;
            }
        }
        return false;
    }

    /** Baslangica gore sirali nobetlerde, baslangici {@code an}'dan SONRA ({@code sonra}) ya da {@code an} ve sonrasi olan ilk nobetin indeksi. */
    private static int ilkIndeks(List<Nobet> nobetler, Instant an, boolean sonra) {
        int a = 0;
        int b = nobetler.size();
        while (a < b) {
            int o = (a + b) >>> 1;
            Instant bas = nobetler.get(o).baslangic();
            if (sonra ? !bas.isAfter(an) : bas.isBefore(an)) {
                a = o + 1;
            } else {
                b = o;
            }
        }
        return a;
    }
}
