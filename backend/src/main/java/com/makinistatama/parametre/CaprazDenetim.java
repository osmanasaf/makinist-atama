package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import com.makinistatama.kural.Siddet;

/**
 * Parametreler arası tutarlılık (MK-96): saf, DB'siz, tablo güdümlü. Her kural {@code küçük × kÇarpan ≤ (ya da <) büyük × bÇarpan} biçimindedir ve İKİ yönden denetlenir
 * (küçük yükseltilirken de büyük düşürülürken de). Yalnız "sert" ayarlar arası bağlar burada; uyarı düzeyi (gece penceresi, yolculuk aktarması) istemcide notlanır.
 * {@code %1$s} diğer ayarın değeri, {@code %2$s} yazılan ayar için sınır değeri (kendi biriminde).
 */
final class CaprazDenetim {

    private static final double EPS = 1e-9;

    /**
     * @param sart yalnız bu kural KAPALI DEĞİLKEN geçerli (null: her zaman); @param sifirKapatir küçük taraf 0 ise kural aranmaz (K-23: 0 = aranmaz);
     *             @param acilisIpucu kapalı kural açılırken çelişki varsa mesaja eklenen yönlendirme
     */
    private record Kural(String kucuk, double kCarpan, String buyuk, double bCarpan, boolean kesin, String sart, boolean sifirKapatir, String kucukMesaj, String buyukMesaj,
            String acilisIpucu) {
    }

    private static final List<Kural> KURALLAR = List.of(
            new Kural("nobet.ayirma_saat", 1, "dinlenme.dis_merkez_saat", 1, false, null, false,
                    "Nöbet ayırma süresi dış merkez dinlenme süresini (%1$s s) aşamaz.", "Dış merkez dinlenme süresi nöbet ayırma süresinden (%1$s s) kısa olamaz.", ""),
            new Kural("haftalik.normal_saat", 1, "haftalik.max_saat", 1, false, "K-14", false,
                    "Normal haftalık süre haftalık üst sınırı (%1$s s) aşamaz.", "Haftalık üst sınır normal haftalık süreden (%1$s s) kısa olamaz.", " Önce haftalık üst sınırı yükseltin."),
            new Kural("nobet.asgari_aktarma_dk", 1, "nobet.ayirma_saat", 60, true, "K-23", true,
                    "Aktarma süresi nöbet ayırma süresinin (%2$s dk) altında olmalı.", "Nöbet ayırma süresi aktarma süresinin (%1$s dk) üstünde olmalı.",
                    " Önce aktarma süresini düşürün ya da nöbet ayırma süresini yükseltin."),
            new Kural("gece.max_saat", 1, "gunluk.max_saat", 1, false, null, false,
                    "Gece görevi azami süresi günlük üst sınırı (%1$s s) aşamaz.", "Günlük üst sınır gece görevi azami süresinden (%1$s s) kısa olamaz.", ""));

    private CaprazDenetim() {
    }

    /** Yazılacak {@code yeni} değer diğer ayarlarla çelişiyorsa kullanıcı mesajı, tutarlıysa {@code null}. {@code deger}: diğer ayarın güncel sayısal değeri. */
    static String parametre(String anahtar, double yeni, ToDoubleFunction<String> deger, Function<String, Siddet> siddet) {
        for (Kural k : KURALLAR) {
            if (k.sart() != null && siddet.apply(k.sart()) == Siddet.KAPALI) {
                continue;
            }
            boolean kucukYaziliyor = k.kucuk().equals(anahtar);
            if (!kucukYaziliyor && !k.buyuk().equals(anahtar)) {
                continue;
            }
            double diger = deger.applyAsDouble(kucukYaziliyor ? k.buyuk() : k.kucuk());
            double kucuk = kucukYaziliyor ? yeni : diger;
            double buyuk = kucukYaziliyor ? diger : yeni;
            if (k.sifirKapatir() && kucuk == 0) {
                continue;
            }
            double sol = kucuk * k.kCarpan();
            double sag = buyuk * k.bCarpan();
            if (k.kesin() ? sol < sag - EPS : sol <= sag + EPS) {
                continue;
            }
            return String.format(java.util.Locale.ROOT, kucukYaziliyor ? k.kucukMesaj() : k.buyukMesaj(), say(diger), say(kucukYaziliyor ? sag / k.kCarpan() : diger));
        }
        return null;
    }

    /** Toplu denetimde çelişen bir kural: hangi ayarlar (küçük/büyük) ve hangi şiddet koşulu (sart, olmayabilir) ile kullanıcı mesajı. */
    record Cakisma(String kucuk, String buyuk, String sart, String mesaj) {
    }

    /**
     * Toplu uygulama (MK-164): aynı {@link #KURALLAR} tablosunu NİHAİ değer ve şiddet haritaları üzerinde bir kez gezer (yeni kural yok); tek tek sırayla yazılırsa ilk adımda
     * yanlış reddedilecek bağımlı değişiklikler birlikte kabul edilir. {@code degerler}: sayısal ayarlar (anahtar -> değer); haritada olmayan ayarın kuralı atlanır.
     * Çağıran, yalnız DEĞİŞEN ayarlara dokunan çelişkileri süzer (önceden var olan tutarsızlık profile yüklenmez).
     */
    static List<Cakisma> tumu(Map<String, Double> degerler, Map<String, Siddet> siddetler) {
        List<Cakisma> sonuc = new ArrayList<>();
        for (Kural k : KURALLAR) {
            if (k.sart() != null && siddetler.get(k.sart()) == Siddet.KAPALI) {
                continue;
            }
            Double kucuk = degerler.get(k.kucuk());
            Double buyuk = degerler.get(k.buyuk());
            if (kucuk == null || buyuk == null || k.sifirKapatir() && kucuk == 0) {
                continue;
            }
            double sol = kucuk * k.kCarpan();
            double sag = buyuk * k.bCarpan();
            if (k.kesin() ? sol < sag - EPS : sol <= sag + EPS) {
                continue;
            }
            sonuc.add(new Cakisma(k.kucuk(), k.buyuk(), k.sart(), String.format(java.util.Locale.ROOT, k.kucukMesaj(), say(buyuk), say(sag / k.kCarpan()))));
        }
        return sonuc;
    }

    /** Kapalı {@code kod} kuralı UYARI/ENGEL'e açılırken, bağlı ayarlar çelişiyorsa mesaj (kural yeniden devrede), değilse {@code null}. */
    static String acilis(String kod, ToDoubleFunction<String> deger) {
        for (Kural k : KURALLAR) {
            if (!kod.equals(k.sart())) {
                continue;
            }
            double kucuk = deger.applyAsDouble(k.kucuk());
            double buyuk = deger.applyAsDouble(k.buyuk());
            double sol = kucuk * k.kCarpan();
            double sag = buyuk * k.bCarpan();
            if (k.sifirKapatir() && kucuk == 0 || (k.kesin() ? sol < sag - EPS : sol <= sag + EPS)) {
                continue;
            }
            return String.format(java.util.Locale.ROOT, k.kesin() ? k.kucukMesaj() : k.buyukMesaj(), say(k.kesin() ? 0 : kucuk), say(sag / k.kCarpan())) + k.acilisIpucu();
        }
        return null;
    }

    private static String say(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }
}
