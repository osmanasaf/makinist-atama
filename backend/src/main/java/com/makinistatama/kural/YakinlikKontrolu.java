package com.makinistatama.kural;

import static com.makinistatama.kural.ZamanHesabi.sure;
import static com.makinistatama.kural.ZamanHesabi.tarih;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sınıra yakınlık (F3, MK-172): kural motorunun DIŞINDA, saf. Motorla AYNI ölçü yardımcılarından ({@link Onhesap}, {@link CalismaSuresiKurallari}, {@link GeceKurallari},
 * {@link ZamanHesabi}) aynı ölçülen değeri üretir; motor dosyalarına dokunmaz ve motoru çağırmaz. Kural: (1) KAPALI kural için üretilmez; (2) ölçülen sınırı AŞTIYSA (ihlal) o
 * kod için hiç üretilmez; (3) {@code kalan = sınır - ölçülen >= 0} ve {@code kalan <= marj} ise üretilir (dinlenme/hafta tatili/aktarma: {@code ölçülen - eşik}).
 * K-14 için ayrı marj ayarı yoktur; günlük marj ({@code marj.gunluk_dk}) kullanılır.
 */
public final class YakinlikKontrolu {

    private YakinlikKontrolu() {
    }

    public static List<Yakinlik> kontrol(KuralBaglami b, YakinlikAyarlari a) {
        if (!a.aktif()) {
            return List.of();
        }
        Onhesap h = Onhesap.of(b);
        List<Yakinlik> o = new ArrayList<>();
        dinlenme(o, b, h, a);
        aktarma(o, b, h, a);
        gunluk(o, b, h, a);
        haftalik(o, b, h, a);
        yillik(o, b, h, a);
        haftaTatili(o, b, h, a);
        gece(o, b, h, a);
        o.sort(Comparator.comparing(Yakinlik::kod)); // kararlı: aynı kodda üretim sırası korunur
        return o;
    }

    private static boolean kapali(Parametreler p, String kod) {
        return p.siddet(kod) == Siddet.KAPALI;
    }

    /** Aynı kod için tüm ölçümler: biri sınırı aştıysa (ihlal) kodun işareti yok. */
    private static final class Aday {
        private final List<Yakinlik> yakin = new ArrayList<>();
        private boolean ihlal;

        void ekle(Yakinlik y) {
            yakin.add(y);
        }

        void ihlal() {
            ihlal = true;
        }

        void yaz(List<Yakinlik> hedef) {
            if (!ihlal) {
                hedef.addAll(yakin);
            }
        }
    }

    /** Üst sınır kuralı (ölçülen sınırı aşamaz): {@code olculen <= sinir} ve {@code sinir - olculen <= marj} ise işaret. Dakika cinsinden. */
    private static void ustSinir(Aday k, String kod, long olculenDk, long sinirDk, long marjDk, String ad) {
        if (olculenDk > sinirDk) {
            k.ihlal();
        } else if (sinirDk - olculenDk <= marjDk) {
            k.ekle(new Yakinlik(kod, olculenDk, sinirDk, sinirDk - olculenDk, "dk", ad + ": " + sure(olculenDk) + " / " + sure(sinirDk)));
        }
    }

    /** Alt sınır kuralı (ölçülen eşiğin altına inemez): {@code olculen >= esik} ve {@code olculen - esik <= marj} ise işaret. */
    private static void altSinir(Aday k, String kod, long olculenDk, long esikDk, long marjDk, String ad) {
        if (olculenDk < esikDk) {
            k.ihlal();
        } else if (olculenDk - esikDk <= marjDk) {
            k.ekle(new Yakinlik(kod, olculenDk, esikDk, olculenDk - esikDk, "dk", ad + ": " + sure(olculenDk) + " / en az " + sure(esikDk)));
        }
    }

    // ---------- K-13 / K-14 ----------

    private static void gunluk(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        if (kapali(b.parametreler(), "K-13")) {
            return;
        }
        long max = b.parametreler().dakika("gunluk.max_saat");
        Aday k = new Aday();
        for (LocalDate gun : ZamanHesabi.gunler(h.alan().bas(), h.alan().bit())) {
            ustSinir(k, "K-13", CalismaSuresiKurallari.gunToplam(h.araliklar(), gun), max, a.gunlukDk(), "Günlük süre (" + tarih(gun) + ")");
        }
        k.yaz(o);
    }

    private static void haftalik(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        if (kapali(b.parametreler(), "K-14")) { // varsayılan KAPALI (V4): haftalık işaret yok
            return;
        }
        long max = b.parametreler().dakika("haftalik.max_saat");
        Aday k = new Aday();
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            ustSinir(k, "K-14", CalismaSuresiKurallari.haftaToplam(h.araliklar(), hafta), max, a.gunlukDk(), "Haftalık süre (" + CalismaSuresiKurallari.hafta(hafta) + ")");
        }
        k.yaz(o);
    }

    // ---------- K-15 ----------

    /** K-15 ile aynı koşul: aday haftanın fazla mesaisini ARTIRIYORSA, o yılın (haftanın Pazar gününe göre) toplam fazla mesaisi sınıra bakılır. */
    private static void yillik(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        Parametreler p = b.parametreler();
        if (kapali(p, "K-15")) {
            return;
        }
        long max = p.dakika("yillik.fazla_mesai_max_saat");
        Map<LocalDate, Long> haftaDk = null;
        Set<Integer> gorulen = new HashSet<>();
        Aday k = new Aday();
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            long artis = CalismaSuresiKurallari.fazlaMesaiDakika(CalismaSuresiKurallari.haftaToplam(h.araliklar(), hafta), p)
                    - CalismaSuresiKurallari.fazlaMesaiDakika(CalismaSuresiKurallari.haftaToplam(h.adaysiz(), hafta), p);
            int yil = hafta.plusDays(6).getYear();
            if (artis <= 0 || !gorulen.add(yil)) {
                continue;
            }
            if (haftaDk == null) {
                haftaDk = haftaToplamlari(h.araliklar());
            }
            long toplam = 0;
            for (Map.Entry<LocalDate, Long> w : haftaDk.entrySet()) {
                if (w.getKey().plusDays(6).getYear() == yil) {
                    toplam += CalismaSuresiKurallari.fazlaMesaiDakika(w.getValue(), p);
                }
            }
            ustSinir(k, "K-15", toplam, max, a.yillikSaat() * 60L, yil + " yılı fazla mesai");
        }
        k.yaz(o);
    }

    private static Map<LocalDate, Long> haftaToplamlari(List<Aralik> araliklar) {
        Map<LocalDate, Long> haftaDk = new HashMap<>();
        for (Aralik ar : araliklar) {
            for (LocalDate w : ZamanHesabi.haftalar(ar.bas(), ar.bit())) {
                haftaDk.merge(w, ZamanHesabi.haftaDakika(ar, w), Long::sum);
            }
        }
        return haftaDk;
    }

    // ---------- K-19 ----------

    private static void haftaTatili(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        if (kapali(b.parametreler(), "K-19")) {
            return;
        }
        long min = b.parametreler().dakika("hafta_tatili.min_saat");
        Aday k = new Aday();
        for (LocalDate hafta : ZamanHesabi.haftalar(h.alan().bas(), h.alan().bit())) {
            altSinir(k, "K-19", CalismaSuresiKurallari.enUzunBosluk(h.araliklar(), hafta), min, a.haftaTatiliDk(), "Haftalık dinlenme (" + CalismaSuresiKurallari.hafta(hafta) + ")");
        }
        k.yaz(o);
    }

    // ---------- K-11 / K-12 ----------

    private static void dinlenme(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        Parametreler p = b.parametreler();
        Map<String, Aday> kodlar = new TreeMap<>();
        List<Nobet> nobetler = h.tumNobet();
        Nobet n = h.adayNobet();
        int i = 0;
        while (nobetler.get(i) != n) {
            i++;
        }
        if (i > 0) {
            Nobet onceki = nobetler.get(i - 1);
            bosluk(kodlar, b, a, onceki.sonVarisGarId(), Duration.between(onceki.bitis(), n.baslangic()).toMinutes(), "Önceki görevle dinlenme");
        }
        if (i < nobetler.size() - 1) {
            Nobet sonraki = nobetler.get(i + 1);
            bosluk(kodlar, b, a, n.sonVarisGarId(), Duration.between(n.bitis(), sonraki.baslangic()).toMinutes(), "Sonraki görevle dinlenme");
        }
        kodlar.entrySet().stream().filter(e -> !kapali(p, e.getKey())).forEach(e -> e.getValue().yaz(o));
    }

    private static void bosluk(Map<String, Aday> kodlar, KuralBaglami b, YakinlikAyarlari a, long konumGar, long boslukDk, String ad) {
        boolean ev = konumGar == b.makinist().bagliGarId();
        long esik = b.parametreler().dakika(ev ? "dinlenme.ev_saat" : "dinlenme.dis_merkez_saat");
        String kod = ev ? "K-11" : "K-12";
        altSinir(kodlar.computeIfAbsent(kod, x -> new Aday()), kod, boslukDk, esik, a.dinlenmeDk(), ad);
    }

    // ---------- K-23 ----------

    private static void aktarma(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        Parametreler p = b.parametreler();
        if (kapali(p, "K-23")) {
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
        Aday k = new Aday();
        if (i > 0) {
            aktarmaCifti(k, grup.get(i - 1), b.aday(), esik, a.aktarmaDk());
        }
        if (i < grup.size() - 1) {
            aktarmaCifti(k, b.aday(), grup.get(i + 1), esik, a.aktarmaDk());
        }
        k.yaz(o);
    }

    private static void aktarmaCifti(Aday k, Gorev once, Gorev sonra, long esikDk, long marjDk) {
        if (once.yolculuk() || sonra.yolculuk() || sonra.devamEden()) {
            return;
        }
        long dk = Duration.between(once.varis(), sonra.kalkis()).toMinutes();
        if (dk >= 0) { // dk < 0: sefer saatleri örtüşüyor, bu K-10 (çakışma) konusudur
            altSinir(k, "K-23", dk, esikDk, marjDk, "Aktarma süresi");
        }
    }

    // ---------- K-16 / K-18 ----------

    private static void gece(List<Yakinlik> o, KuralBaglami b, Onhesap h, YakinlikAyarlari a) {
        Parametreler p = b.parametreler();
        long[] oz = GeceKurallari.ozet(h.adayNobet(), p);
        if (!GeceKurallari.geceMi(oz, p)) {
            return;
        }
        if (!kapali(p, "K-16")) {
            Aday k = new Aday();
            ustSinir(k, "K-16", oz[0], p.dakika("gece.max_saat"), a.geceDk(), "Gece nöbeti süresi");
            k.yaz(o);
        }
        if (!kapali(p, "K-18")) {
            ardisikHafta(o, p, h, a);
        }
    }

    /** K-18 ile aynı ön koşul: aday gece nöbeti YENİ bir gece haftası yaratıyorsa ardışık hafta sayısı (iki yönlü) sınıra bakılır. */
    private static void ardisikHafta(List<Yakinlik> o, Parametreler p, Onhesap h, YakinlikAyarlari a) {
        LocalDate w = ZamanHesabi.haftaBasi(ZamanHesabi.gun(h.adayNobet().baslangic()));
        int min = (int) Math.max(1, p.tamsayi("gece.hafta_min_gorev"));
        List<Nobet> tum = h.tumNobet();
        if (!geceHaftasi(tum, w, p, min) || geceHaftasi(h.digerNobet(), w, p, min)) {
            return;
        }
        int geri = 0;
        while (geceHaftasi(tum, w.minusWeeks(geri + 1), p, min)) {
            geri++;
        }
        int ileri = 0;
        while (geceHaftasi(tum, w.plusWeeks(ileri + 1), p, min)) {
            ileri++;
        }
        long max = p.tamsayi("gece.ard_hafta_max");
        int seri = geri + 1 + ileri;
        if (seri <= max && max - seri <= a.geceHafta()) { // seri > max ihlaldir: işaret yok
            o.add(new Yakinlik("K-18", seri, max, max - seri, "hafta", "Ardışık gece haftası: " + seri + " / " + max));
        }
    }

    // ponytail: doğrusal tarama (haftada birkaç nöbet); motordaki ikili arama gerekirse buraya taşınır
    private static boolean geceHaftasi(List<Nobet> nobetler, LocalDate hafta, Parametreler p, int min) {
        Instant bas = ZamanHesabi.gunBasi(hafta);
        Instant son = ZamanHesabi.gunBasi(hafta.plusDays(7));
        int adet = 0;
        for (Nobet n : nobetler) {
            if (!n.baslangic().isBefore(bas) && n.baslangic().isBefore(son) && GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p) && ++adet >= min) {
                return true;
            }
        }
        return false;
    }
}
