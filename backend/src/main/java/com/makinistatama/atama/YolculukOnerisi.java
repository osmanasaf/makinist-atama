package com.makinistatama.atama;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.ZamanHesabi;

/**
 * Yolculuk önerisi çekirdeği (F2b, MK-41): SAF (Spring/JPA/IO yok), DB'siz, belirleyici. Bir makinist için bir hedef görevden ÖNCE, hedefin kalkış garına
 * varan tek atlamalı bir seferi (yolcu olarak) bulur. İki çağıran paylaşır: manuel uç ({@code YolculukServisi}) ve otomatik atamanın yolculuk aşaması
 * ({@link OtomatikAtama}). Strateji/arayüz yok: tek algoritma.
 * <p>
 * Ucuz ön süzgeç (kural motoru ÖNCESİ): aday kalkışı şimdiden sonra, varış garı = hedef kalkış garı, makinistin yolculuk kalkışındaki konumu = aday kalkış garı,
 * {@code varis ≤ hedef.baslangic − aktarmaDk}, {@code kalkis ≥ hedef.baslangic − aramaSaat}, yük treni değil (ayar kapalıyken), makinist o seferde zaten görevli değil.
 * Sonra sırayla (en az bekleme, kısa süre, sefer kimliği) {@link KuralMotoru#degerlendirYazma} ile (1) yolculuk, (2) yolculuk eklenmiş listeyle hedef denetlenir;
 * ilk {@code sinir} geçen döner. {@code aktarmaDk} yalnız burada uygulanır (elle atamada K-10 yeter).
 */
public final class YolculukOnerisi {

    /** Yolculuğa binilebilecek sefer: {@code gun} planlanan günü (pencere için), {@code yuk} yük treni mi. */
    public record YolculukAdayi(long seferId, LocalDate gun, Gorev gorev, boolean yuk) {
    }

    /** Önerilen yolculuk: {@code bekleDk} yolculuk varışı ile hedef başlangıcı arası; ihlaller ENGEL'siz (temiz istenmişse boş) yolculuk ve hedef ihlalleri. */
    public record Oneri(long seferId, Gorev gorev, long bekleDk, List<Ihlal> yolculukIhlalleri, List<Ihlal> hedefIhlalleri) {
    }

    /** Makinistin kural bağlamı için sabit girdi (hedef ve adaydan bağımsız kısım). */
    public record MakinistBaglami(MakinistBilgisi bilgi, Map<Long, LocalDate> hatSon, LocalDate genelSon, Map<Long, String> adlar) {
    }

    private static final Comparator<Gorev> SIRA = Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId);

    private YolculukOnerisi() {
    }

    /**
     * @param sirali      makinistin mevcut görevleri, {@code baslangic}'a göre sıralı (hedef ve adaylar içinde DEĞİL); değiştirilmez
     * @param hedefGun    hedef görevin planlanan günü
     * @param temiz       true: yolculuk ve hedef HİÇ ihlal (UYARI dahil) taşımamalı (otomatik atama); false: yalnız ENGEL'siz (manuel; UYARI'lar döner)
     * @param pencereGun  K-18'in iki yönde baktığı pencere
     * @param yillik      K-15 açık mı (pencerenin geri sınırı yıl başına uzar)
     * @param simdi       null ise "aday kalkışı gelecekte" süzgeci uygulanmaz
     */
    public static List<Oneri> oner(List<Gorev> sirali, MakinistBaglami mb, Gorev hedef, LocalDate hedefGun, List<YolculukAdayi> adaylar, Parametreler p, Instant simdi,
            int aktarmaDk, int aramaSaat, boolean yukBinilir, int sinir, boolean temiz, int pencereGun, boolean yillik) {
        Instant enGec = hedef.baslangic().minusSeconds(60L * aktarmaDk);
        Instant enErken = hedef.baslangic().minusSeconds(3600L * aramaSaat);
        List<YolculukAdayi> uygun = new ArrayList<>();
        for (YolculukAdayi a : adaylar) {
            Gorev g = a.gorev();
            if (a.seferId() == hedef.seferId() || (simdi != null && !g.kalkis().isAfter(simdi)) || g.varisGarId() != hedef.kalkisGarId() || g.varis().isAfter(enGec)
                    || g.kalkis().isBefore(enErken) || (a.yuk() && !yukBinilir)) {
                continue;
            }
            if (konumGar(sirali, mb.bilgi().bagliGarId(), g.baslangic()) != g.kalkisGarId() || gorevli(sirali, a.seferId())) {
                continue;
            }
            uygun.add(a);
        }
        uygun.sort(Comparator.comparingLong((YolculukAdayi a) -> bekle(hedef, a.gorev())).thenComparingLong(a -> sure(a.gorev()))
                .thenComparingLong(YolculukAdayi::seferId));

        List<Oneri> sonuc = new ArrayList<>();
        for (YolculukAdayi a : uygun) {
            if (sonuc.size() >= sinir) {
                break;
            }
            // temiz (otomatik atama): yolculuk, hedefle birlikte yazılacağı nihai durumda değerlendirilir (hedef yolculuğun hemen sonraki görevidir; aksi halde makinistin ileri
            // tarihli başka gardaki görevi "sonraki görev" konum uyarısı (K-20) üretir). Manuel: tek başına (yolculukla ata önce YOLCU'yu yazar, uyarıyı o an görür).
            List<Gorev> yolculukKomsulari = temiz ? ekle(sirali, hedef) : sirali;
            List<Ihlal> y = KuralMotoru.degerlendirYazma(baglam(mb, a.gorev(), pencere(yolculukKomsulari, a.gun(), pencereGun, yillik, p), p, simdi));
            if (kabul(y, temiz)) {
                continue;
            }
            List<Ihlal> h = KuralMotoru.degerlendirYazma(baglam(mb, hedef, pencere(ekle(sirali, a.gorev()), hedefGun, pencereGun, yillik, p), p, simdi));
            if (kabul(h, temiz)) {
                continue;
            }
            sonuc.add(new Oneri(a.seferId(), a.gorev(), bekle(hedef, a.gorev()), y, h));
        }
        return sonuc;
    }

    /** Başlangıca göre sıralı listenin kopyasına {@code g}'yi sırasında ekler. */
    private static List<Gorev> ekle(List<Gorev> sirali, Gorev g) {
        List<Gorev> kopya = new ArrayList<>(sirali);
        int yer = Collections.binarySearch(kopya, g, SIRA);
        kopya.add(yer < 0 ? -yer - 1 : yer, g);
        return kopya;
    }

    /** {@code true}: ihlal kümesi reddedilir (temiz isteniyorsa herhangi bir ihlal, değilse ENGEL). */
    private static boolean kabul(List<Ihlal> ihlaller, boolean temiz) {
        return temiz ? !ihlaller.isEmpty() : KuralMotoru.engelVarMi(ihlaller);
    }

    private static long bekle(Gorev hedef, Gorev yolculuk) {
        return java.time.Duration.between(yolculuk.varis(), hedef.baslangic()).toMinutes();
    }

    private static long sure(Gorev g) {
        return java.time.Duration.between(g.kalkis(), g.varis()).toMinutes();
    }

    private static KuralBaglami baglam(MakinistBaglami mb, Gorev aday, List<Gorev> diger, Parametreler p, Instant simdi) {
        return new KuralBaglami(aday, diger, mb.bilgi(), mb.hatSon(), mb.genelSon(), mb.adlar(), p, simdi);
    }

    /** Makinist bu seferde zaten görevli mi (aynı makinist aynı seferde tek satır: atama_sefer_makinist_uk). */
    private static boolean gorevli(List<Gorev> sirali, long seferId) {
        for (Gorev g : sirali) {
            if (g.seferId() == seferId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Makinistin {@code an}'daki konumu (gar kimliği): {@code an}'dan ÖNCE başlayan son görevin varış garı; yoksa bağlı gar. Başlangıca göre sıralı listede ikili arama
     * (otomatik atamanın yolculuk aşaması da bunu kullanır).
     */
    public static long konumGar(List<Gorev> sirali, long bagliGar, Instant an) {
        int a = 0;
        int b = sirali.size();
        while (a < b) {
            int o = (a + b) >>> 1;
            if (sirali.get(o).baslangic().isBefore(an)) {
                a = o + 1;
            } else {
                b = o;
            }
        }
        return a == 0 ? bagliGar : sirali.get(a - 1).varisGarId();
    }

    /** Sefer gününün K-18/K-15 penceresindeki görevler ({@code OtomatikAtama} ile aynı pencere tanımı). */
    private static List<Gorev> pencere(List<Gorev> sirali, LocalDate gun, int pencereGun, boolean yillik, Parametreler p) {
        Instant alt = ZamanHesabi.pencereBasi(gun, pencereGun, yillik).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
        Instant ust = ZamanHesabi.pencereSonu(gun, pencereGun, p.ozelYillik()).plusDays(1).atStartOfDay(ZamanHesabi.ISTANBUL).toInstant();
        int lo = ilkIndeks(sirali, alt);
        int hi = ilkIndeks(sirali, ust);
        return new ArrayList<>(sirali.subList(lo, Math.max(lo, hi)));
    }

    private static int ilkIndeks(List<Gorev> sirali, Instant an) {
        int a = 0;
        int b = sirali.size();
        while (a < b) {
            int o = (a + b) >>> 1;
            if (sirali.get(o).baslangic().isBefore(an)) {
                a = o + 1;
            } else {
                b = o;
            }
        }
        return a;
    }
}
