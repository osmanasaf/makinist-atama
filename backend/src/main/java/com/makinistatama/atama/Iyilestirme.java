package com.makinistatama.atama;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * İyileştirme döngüsü (F2d, MK-66/MK-69): "gıcırdayan tekerlek". Tur 0 bugünkü plandır (öncelik boş). Sonraki her turda önceki turlarda boş kalan seferler,
 * birikimli ağırlık × kaydırma dakikası kadar kalkış sırasında öne alınır ve plan yeniden kurulur; en az boşlu plan döner (eşitte İLK, yani tur 0 yenilmez: sonuç
 * tabandan kötü olamaz). Saf ve durumsuz: planlayıcı ve saat dışarıdan verilir (DB'siz, sahte saatle test edilir); aynı girdi aynı bulguyu üretir.
 */
final class Iyilestirme {

    /**
     * Kaydırma programı (dakika): tur 1 geniş, sonraki turlar ince. Ölçüm (T5.2, 04-gelistirme.md): {240, 480, 60, 120} döngüsü demo 31 günde 12 turda en iyi 53 verdi; tek
     * 240 demoda 34'e iner ama 8 ay geçmişli ölçekte tur başına ≈ 4 sn olduğundan bütçeye 2-3 tur sığar ve 240 ile 276 -> 243 kalır; geniş ilk tur (1440) orada 276 -> 54'e, demoda
     * sonraki ince turlar 37'ye iner. MK-69 aşım sırasının 1.-2. adımı.
     * ponytail: sabit program; yeni veri biçimi başka bir kaydırma gerektirirse değerler burada değişir.
     */
    private static final long ILK_KAYDIRMA_DK = 1440;
    private static final long KAYDIRMA_DK = 240;

    /**
     * @param tabanBos {@code tur 0}'daki (bugünkü plan) boş sefer sayısı
     * @param tur      koşulan toplam tur sayısı (tur 0 dahil)
     * @param durma    arama neden bitti: BOS_YOK (tur 0'da boş yok), BOS_SIFIR (denemelerle boş kalmadı), TUR_SINIRI (tur ayarı doldu; tur=1 ise hiç ek deneme yok), SURE (bütçe doldu; tur=1 ise
     *                 tur 0 bütçeyi doldurdu, ek deneme yapılmadı)
     * @param oncelik  sonucu veren turun GİRDİSİ (kaydırmayla birlikte "anahtar"); tur 0 en iyiyse boş
     */
    record Bulgu(OtomatikAtama.Sonuc sonuc, int tabanBos, int tur, long kaydirDk, Map<Long, Integer> oncelik, long sureMs, String durma) {
    }

    private Iyilestirme() {
    }

    /**
     * @param tur        (öncelik, kaydırma dakikası) -> plan
     * @param enCokTur   tur 0 dahil en çok tur
     * @param butceMs    süre bütçesi: "geçen + son tur süresi" bunu aşacaksa yeni tur BAŞLAMAZ (tur ortasında kesme yok)
     * @param nanoSaat   monoton saat (nanosaniye)
     */
    static Bulgu ara(BiFunction<Map<Long, Integer>, Long, OtomatikAtama.Sonuc> tur, int enCokTur, long butceMs, LongSupplier nanoSaat) {
        long bas = nanoSaat.getAsLong();
        OtomatikAtama.Sonuc enIyi = tur.apply(Map.of(), 0L);
        int tabanBos = enIyi.bosKalanlar().size();
        long enIyiKaydir = 0;
        Map<Long, Integer> enIyiOncelik = Map.of();
        long sonTurMs = (nanoSaat.getAsLong() - bas) / 1_000_000;
        Map<Long, Integer> agirlik = new HashMap<>();
        enIyi.bosKalanlar().forEach(b -> agirlik.merge(b.seferId(), 1, Integer::sum));
        int kosulan = 1;
        while (kosulan < enCokTur && enIyi.bosKalanlar().size() > 0 && (nanoSaat.getAsLong() - bas) / 1_000_000 + sonTurMs <= butceMs) {
            long kaydir = kosulan == 1 ? ILK_KAYDIRMA_DK : KAYDIRMA_DK;
            Map<Long, Integer> oncelik = Map.copyOf(agirlik);
            long t0 = nanoSaat.getAsLong();
            OtomatikAtama.Sonuc r = tur.apply(oncelik, kaydir);
            sonTurMs = (nanoSaat.getAsLong() - t0) / 1_000_000;
            kosulan++;
            if (r.bosKalanlar().size() < enIyi.bosKalanlar().size()) {
                enIyi = r;
                enIyiKaydir = kaydir;
                enIyiOncelik = oncelik;
            }
            r.bosKalanlar().forEach(b -> agirlik.merge(b.seferId(), 1, Integer::sum));
        }
        String durma = tabanBos == 0 ? "BOS_YOK" : enIyi.bosKalanlar().isEmpty() ? "BOS_SIFIR" : kosulan >= enCokTur ? "TUR_SINIRI" : "SURE";
        return new Bulgu(enIyi, tabanBos, kosulan, enIyiKaydir, enIyiOncelik, (nanoSaat.getAsLong() - bas) / 1_000_000, durma);
    }
}
