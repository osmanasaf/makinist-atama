package com.makinistatama.kural;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Özel kuralın kapsamı (MK-16): hepsi isteğe bağlı, farklı alanlar VE ile, aynı alanda çoklu seçim VEYA ile birleşir; boş küme = süzme yok.
 * Aynı kayıt hem API gövdesi hem {@code ozel_kural.kapsam} JSON'udur. Anotasyonsuz (kural paketi saf): {@code null} küme boş, {@code null}
 * vardiya HEPSI olur. Kümeler sıralı saklanır (JSON ve denetim kaydı kararlı). {@code trenTurleri}: "YUK"/"YOLCU"; seferin tren türü boşsa o sefer tür kapsamına girmez.
 * {@code personelTurleri} (F3b, MK-85): kural yalnız bu türlerdeki personele uygulanır; boş = tümü (JSON'da alan yoksa boş: eski kurallar aynı davranır).
 */
public record Kapsam(Set<Long> hatlar, Set<Long> aracTipleri, Set<Long> kalkisGarlari, Set<Long> varisGarlari, Set<String> trenTurleri, Vardiya vardiya,
        Set<MakinistBilgisi.Tur> personelTurleri) {

    /** Sefer.tren_turu ile aynı değerler ({@code TrenTuruSabitleriTest}, MK-23: kural paketi başka paketi göremez, bu yüzden String). */
    public static final Set<String> TREN_TURLERI = Set.of("YUK", "YOLCU");

    public static final Kapsam TUMU = new Kapsam(null, null, null, null, null, null);

    public Kapsam {
        hatlar = sirali(hatlar);
        aracTipleri = sirali(aracTipleri);
        kalkisGarlari = sirali(kalkisGarlari);
        varisGarlari = sirali(varisGarlari);
        trenTurleri = sirali(trenTurleri);
        vardiya = vardiya == null ? Vardiya.HEPSI : vardiya;
        personelTurleri = sirali(personelTurleri);
    }

    /** Eski 6 bileşenli kurucu: personel türü süzmesi yok (F3b öncesi çağıranlar aynı kalır). */
    public Kapsam(Set<Long> hatlar, Set<Long> aracTipleri, Set<Long> kalkisGarlari, Set<Long> varisGarlari, Set<String> trenTurleri, Vardiya vardiya) {
        this(hatlar, aracTipleri, kalkisGarlari, varisGarlari, trenTurleri, vardiya, null);
    }

    private static <T extends Comparable<T>> Set<T> sirali(Set<T> s) {
        if (s == null) {
            return Set.of();
        }
        Set<T> t = new TreeSet<>();
        for (T e : s) {
            if (e != null) { // JSON'da [null] gelebilir
                t.add(e);
            }
        }
        return Collections.unmodifiableSet(t);
    }

    /** Hiçbir süzme yok. */
    public boolean tumu() {
        return hatlar.isEmpty() && aracTipleri.isEmpty() && kalkisGarlari.isEmpty() && varisGarlari.isEmpty() && trenTurleri.isEmpty()
                && vardiya == Vardiya.HEPSI && personelTurleri.isEmpty();
    }

    /** Kişi düzeyi kapı (MK-85): kural bu türdeki personele uygulanır mı. Ölçüden bağımsızdır; görev düzeyindeki {@link #gorevKapsar} ile karıştırılmaz. */
    boolean turKapsar(MakinistBilgisi.Tur tur) {
        return personelTurleri.isEmpty() || personelTurleri.contains(tur);
    }

    /**
     * Vardiya dışındaki alanlar görevi kapsıyor mu (vardiya nöbet bilgisi ister: {@link OzelKuralDegerlendirici}). Yolculuk görevi (F2b, MK-40) hat / araç tipi / tren türü
     * kapsamlı kurala girmez (sürüş değildir; kapsamsız genel kural ve gar kapsamı uygulanır).
     */
    boolean gorevKapsar(Gorev g) {
        return (hatlar.isEmpty() || (!g.yolculuk() && hatlar.contains(g.hatId())))
                && (aracTipleri.isEmpty() || (!g.yolculuk() && aracTipleri.contains(g.aracTipiId())))
                && (kalkisGarlari.isEmpty() || kalkisGarlari.contains(g.kalkisGarId()))
                && (varisGarlari.isEmpty() || varisGarlari.contains(g.varisGarId()))
                && (trenTurleri.isEmpty() || (!g.yolculuk() && g.trenTuru() != null && trenTurleri.contains(g.trenTuru()))); // tren türü boş sefer hiçbir tür kapsamına girmez [V7]
    }
}
