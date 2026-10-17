package com.makinistatama.kural;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Kural motoru tek giris noktasi (MK-4): K-01..K-23 hepsi degerlendirilir (ilk ihlalde durmaz), KAPALI kurallar
 * atlanir, ENGEL/UYARI siddeti {@link Parametreler}'den gelir (K-10 sabit ENGEL). Sonuc kural koduna gore sirali.
 */
public final class KuralMotoru {

    private static final List<String> KODLAR = IntStream.rangeClosed(1, 23).mapToObj(i -> String.format("K-%02d", i)).toList();

    private KuralMotoru() {
    }

    public static List<Ihlal> degerlendir(KuralBaglami b) {
        List<Ihlal> o = new ArrayList<>();
        Onhesap h = Onhesap.of(b); // nobet/aralik onhesabi bir kez
        o.addAll(BelgeKurallari.kontrol(b));
        o.addAll(DinlenmeKurallari.kontrol(b, h));
        o.addAll(CalismaSuresiKurallari.kontrol(b, h));
        o.addAll(GeceKurallari.kontrol(b, h));
        if (!b.parametreler().ozelKurallar().isEmpty()) { // MK-17: özel kural yokken motor hiç çalışmaz (sıfır maliyet, davranış aynı)
            o.addAll(OzelKuralDegerlendirici.kontrol(b, h));
        }
        gecmisSefer(b, o);
        o.sort(Comparator.comparing(Ihlal::kod)); // kararli siralama: ayni kodda uretim sirasi korunur
        return o;
    }

    /** K-22: bitmiş (güncel varışı geçmişte kalmış) sefere atama; yalnız bağlamda {@code simdi} varsa denetlenir. */
    private static void gecmisSefer(KuralBaglami b, List<Ihlal> o) {
        if (b.simdi() != null && b.aday().varis().isBefore(b.simdi())) {
            Ihlal.ekle(o, b.parametreler(), "K-22", 0, 0, "", "Sefer " + ZamanHesabi.deEk(b.aday().varis()) + " bitti; geçmiş tarihli sefere atama");
        }
    }

    /**
     * Atama YAZMA anı değerlendirmesi (aday listesi, atama, otomatik atama): makinistin tüm komşu görevleriyle (iki yönlü) sonuç + canlı kırmızı hesabının
     * (yalnız {@link NobetHesabi#sorumluKomsular sorumlu komşular}, K-22 yok) ENGEL'leri. MK-5 değişmezi: burada ENGEL'siz çıkan atama yazıldıktan sonra
     * kırmızı olamaz; olacaksa daha yazılmadan ihlal olarak görünür. {@code b.digerGorevler()} tüm komşuları taşımalıdır.
     */
    public static List<Ihlal> degerlendirYazma(KuralBaglami b) {
        List<Ihlal> hepsi = degerlendir(b);
        if (b.digerGorevler().stream().allMatch(g -> NobetHesabi.SIRA.compare(g, b.aday()) < 0)) {
            // Tüm komşular adaydan önce başlıyor (otomatik atama kronolojik): sorumlu komşular = tüm komşular; canlı değerlendirme yalnız simdi=null (yalnız K-22) ile ayrılır, yani
            // hepsi'nin alt kümesidir ve eklenecek ENGEL yoktur (MK-54). İkinci geçiş ve sorumlu komşu gruplaması atlanır.
            return hepsi;
        }
        List<Gorev> sorumlu = NobetHesabi.sorumluKomsular(b.digerGorevler(), b.aday(), b.parametreler().dakika("nobet.ayirma_saat"));
        List<Ihlal> canli = degerlendir(new KuralBaglami(b.aday(), sorumlu, b.makinist(), b.pencereOncesiHatSonSurus(), b.pencereOncesiGenelSonSurus(), b.tanimAdlari(),
                b.parametreler(), null));
        List<Ihlal> sonuc = new ArrayList<>(hepsi);
        for (Ihlal i : canli) {
            if (i.siddet() == Siddet.ENGEL && hepsi.stream().noneMatch(h -> h.siddet() == Siddet.ENGEL && h.kod().equals(i.kod()))) {
                sonuc.add(i);
            }
        }
        sonuc.sort(Comparator.comparing(Ihlal::kod));
        return sonuc;
    }

    public static boolean engelVarMi(List<Ihlal> ihlaller) {
        return ihlaller.stream().anyMatch(i -> i.siddet() == Siddet.ENGEL);
    }

    /** Anlik goruntu icin: KAPALI olmayan, gercekten degerlendirilen kural kodlari (K-01..K-22 sirasiyla). */
    public static List<String> kontrolEdilenKodlar(Parametreler p) {
        List<String> kodlar = new ArrayList<>(KODLAR.stream().filter(k -> p.siddet(k) != Siddet.KAPALI).toList());
        p.ozelKurallar().stream().filter(k -> k.siddet() != Siddet.KAPALI).map(OzelKural::kod).forEach(kodlar::add);
        return kodlar;
    }
}
