package com.makinistatama.kural;

import static com.makinistatama.kural.ZamanHesabi.deEk;
import static com.makinistatama.kural.ZamanHesabi.sure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * K-10 cakisma, K-11/K-12 NOBETLER arasi dinlenme (onceki ve sonraki nobetle, MK-2), K-20 konum surekliligi (ilk gorevde baslangic konumu bagli gardir, OK-5).
 * K-23 asgari aktarma. K-11 ev merkezi (nobetin son varis gari makinistin bagli gari), K-12 dis merkez.
 */
public final class DinlenmeKurallari {

    private DinlenmeKurallari() {
    }

    public static List<Ihlal> kontrol(KuralBaglami b) {
        return kontrol(b, Onhesap.of(b));
    }

    static List<Ihlal> kontrol(KuralBaglami b, Onhesap h) {
        List<Ihlal> o = new ArrayList<>();
        cakisma(o, b);
        dinlenme(o, b, h);
        konum(o, b, h);
        aktarma(o, b, h);
        return o;
    }

    private static void cakisma(List<Ihlal> o, KuralBaglami b) {
        Gorev a = b.aday();
        for (Gorev g : b.digerGorevler()) {
            long dk = ZamanHesabi.kesisim(a.baslangic(), a.bitis(), g.baslangic(), g.bitis());
            if (dk > 0) {
                Ihlal.ekle(o, b.parametreler(), "K-10", dk / 60.0, 0, "s", "Çakışma: bu görev, " + ZamanHesabi.an(g.baslangic()) + " – " + ZamanHesabi.an(g.bitis())
                        + " arasındaki başka bir görevle " + sure(dk) + " örtüşüyor");
            }
        }
    }

    private static void dinlenme(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        List<Nobet> nobetler = h.tumNobet();
        Nobet n = h.adayNobet();
        int i = 0;
        while (nobetler.get(i) != n) { // nobetiBul ayni nesneyi dondurur
            i++;
        }
        if (i > 0) {
            Nobet onceki = nobetler.get(i - 1);
            bosluk(o, b, onceki.sonVarisGarId(), Duration.between(onceki.bitis(), n.baslangic()).toMinutes(),
                    "", "önceki görev " + deEk(onceki.bitis()) + " bitti");
        }
        if (i < nobetler.size() - 1) {
            Nobet sonraki = nobetler.get(i + 1);
            bosluk(o, b, n.sonVarisGarId(), Duration.between(n.bitis(), sonraki.baslangic()).toMinutes(),
                    " (sonraki görev)", "sonraki görev " + deEk(sonraki.baslangic()) + " başlıyor");
        }
    }

    private static void bosluk(List<Ihlal> o, KuralBaglami b, long konumGar, long boslukDk, String etiket, String aciklama) {
        Parametreler p = b.parametreler();
        boolean ev = konumGar == b.makinist().bagliGarId();
        long esikDk = p.dakika(ev ? "dinlenme.ev_saat" : "dinlenme.dis_merkez_saat");
        if (boslukDk < esikDk) {
            Ihlal.ekle(o, p, ev ? "K-11" : "K-12", boslukDk / 60.0, esikDk / 60.0, "s",
                    "Dinlenme " + sure(boslukDk) + " < " + sure(esikDk) + etiket + "; " + aciklama
                            + (ev ? "" : " (" + b.ad(konumGar) + " bağlı garı değil: dış merkez dinlenmesi uygulanır)"));
        }
    }

    /**
     * K-23: adayın aynı nöbetteki hemen önceki / sonraki sürüş görevi ile arasında asgari aktarma (önceki varış -> sonraki kalkış, sefer saatleri). Muaf: aynı trenin
     * ardışık parçası (OK-17, {@code devamEden}) ve yolculuk geçişleri (onları {@code yolculuk.aktarma_dk} yönetir). Kapalıyken ya da 0 dk iken hiçbir şey yapmaz; örtüşen seferler K-10'a bırakılır.
     */
    private static void aktarma(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        Parametreler p = b.parametreler();
        if (p.siddet("K-23") == Siddet.KAPALI) {
            return;
        }
        long esik = p.tamsayi("nobet.asgari_aktarma_dk");
        if (esik <= 0) {
            return;
        }
        List<Gorev> grup = h.adayNobet().gorevler();
        int i = 0;
        while (grup.get(i) != b.aday()) {
            i++;
        }
        if (i > 0) {
            aktarmaCifti(o, p, grup.get(i - 1), b.aday(), esik);
        }
        if (i < grup.size() - 1) {
            aktarmaCifti(o, p, b.aday(), grup.get(i + 1), esik);
        }
    }

    private static void aktarmaCifti(List<Ihlal> o, Parametreler p, Gorev once, Gorev sonra, long esikDk) {
        if (once.yolculuk() || sonra.yolculuk() || sonra.devamEden()) {
            return;
        }
        long dk = Duration.between(once.varis(), sonra.kalkis()).toMinutes();
        if (dk >= 0 && dk < esikDk) { // dk < 0: sefer saatleri örtüşüyor, bu K-10 (çakışma) konusudur
            Ihlal.ekle(o, p, "K-23", dk, esikDk, "dk", "Aktarma süresi " + sure(dk) + " < " + sure(esikDk) + "; önceki görev " + deEk(once.varis()) + " bitiyor, sonraki " + deEk(sonra.kalkis()) + " kalkıyor");
        }
    }

    /** K-20: aday ile hemen onceki ve sonraki gorev arasinda varis gari = kalkis gari. */
    private static void konum(List<Ihlal> o, KuralBaglami b, Onhesap h) {
        List<Gorev> sirali = h.siraliTum();
        int i = 0;
        while (sirali.get(i) != b.aday()) {
            i++;
        }
        Gorev a = b.aday();
        if (i > 0 && sirali.get(i - 1).varisGarId() != a.kalkisGarId()) {
            uyumsuz(o, b, "", sirali.get(i - 1).varisGarId(), a.kalkisGarId());
        } else if (i == 0 && b.makinist().bagliGarId() != a.kalkisGarId()) {
            // OK-5: ilk görevde makinist bağlı garındadır (pencere öncesi son konum bilinmez; ceiling: pencereden eski görevler "ev garı" varsayılır)
            uyumsuz(o, b, " (ilk görev, bağlı gar)", b.makinist().bagliGarId(), a.kalkisGarId());
        }
        if (i < sirali.size() - 1 && a.varisGarId() != sirali.get(i + 1).kalkisGarId()) {
            uyumsuz(o, b, " (sonraki görev)", a.varisGarId(), sirali.get(i + 1).kalkisGarId());
        }
    }

    private static void uyumsuz(List<Ihlal> o, KuralBaglami b, String etiket, long varis, long kalkis) {
        Ihlal.ekle(o, b.parametreler(), "K-20", 0, 0, "",
                "Konum uyuşmazlığı" + etiket + ": " + b.ad(varis) + " ≠ " + b.ad(kalkis));
    }
}
