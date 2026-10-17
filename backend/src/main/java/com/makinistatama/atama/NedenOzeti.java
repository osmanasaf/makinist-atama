package com.makinistatama.atama;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.Parametreler;

/**
 * "Neden kimse uygun değil" özeti (saf): her makinistin ilk engelleyici kural kodu kısa bir etikete çevrilir ve sayılır.
 * Örn. {@code "0/3 uygun: hat yetkisi 1, izin 1, dinlenme 1"}. Otomatik atamanın boş kalan seferleri ve "yerine ata" aynı özeti kullanır.
 */
final class NedenOzeti {

    private static final Map<String, String> ETIKET = Map.ofEntries(
            Map.entry("K-01", "durum"), Map.entry("K-02", "belge"), Map.entry("K-03", "belge"), Map.entry("K-04", "belge"),
            Map.entry("K-05", "araç tipi yetkisi"), Map.entry("K-06", "hat yetkisi"), Map.entry("K-07", "sürüş arası"), Map.entry("K-08", "sürüş arası"),
            Map.entry("K-09", "izin"), Map.entry("K-10", "çakışma"), Map.entry("K-11", "dinlenme"), Map.entry("K-12", "dinlenme"),
            Map.entry("K-13", "günlük süre"), Map.entry("K-14", "haftalık süre"), Map.entry("K-15", "yıllık süre"), Map.entry("K-16", "gece çalışması"),
            Map.entry("K-17", "haftalık süre"), Map.entry("K-18", "gece çalışması"), Map.entry("K-19", "hafta tatili"), Map.entry("K-20", "konum"),
            Map.entry("K-21", "sefer süresi"), Map.entry("K-22", "geçmiş sefer"), Map.entry("K-23", "aktarma süresi"));

    private NedenOzeti() {
    }

    /** Yerleşik kural: kısa etiket; özel kural (Ö-NNN): kuralın adı (MK-17). */
    static String etiket(String kod, Parametreler p) {
        if (OzelKural.ozelMi(kod)) {
            try {
                return p.ozelKural(kod).ad();
            } catch (IllegalArgumentException e) { // kapatılmış ya da silinmiş kural: kodun kendisi
                return kod;
            }
        }
        return ETIKET.getOrDefault(kod, kod);
    }

    /**
     * Her adayı, ENGELLEYEN kodları arasından adaylar genelinde EN ÇOK adayı engelleyen koda atfeder (eşitte ENGEL öncelikli, sonra liste sırası): tek bir kural
     * herkesi engelliyorsa özette gerçek neden o görünür, kural koduna göre ilk sıradaki başka bir engel değil (BUG-4). Sayılar yine adayların toplamıdır.
     */
    static List<String> birincil(List<List<Ihlal>> adaylar) {
        Map<String, Integer> sikilik = new java.util.HashMap<>();
        for (List<Ihlal> l : adaylar) {
            l.stream().map(Ihlal::kod).distinct().forEach(k -> sikilik.merge(k, 1, Integer::sum));
        }
        List<String> sonuc = new ArrayList<>(adaylar.size());
        for (List<Ihlal> l : adaylar) {
            Ihlal en = null;
            for (Ihlal i : l) {
                if (en == null || sikilik.get(i.kod()) > sikilik.get(en.kod()) || (sikilik.get(i.kod()).equals(sikilik.get(en.kod())) && i.siddet() == Siddet.ENGEL && en.siddet() != Siddet.ENGEL)) {
                    en = i;
                }
            }
            sonuc.add(en.kod());
        }
        return sonuc;
    }

    /** Makinistin kendisinden kaynaklanan "yetkisiz" nedenler (durum, belge, bröve, sürüş arası): kapasite eksiğini (süre, çakışma, dinlenme, izin ...) gizlemesin diye sona alınır. */
    static boolean yetkisizlik(String kod) {
        return kod.length() == 4 && kod.startsWith("K-0") && kod.charAt(3) >= '1' && kod.charAt(3) <= '8';
    }

    /**
     * {@code ilkKodlar}: engellenen her makinistin ilk kural kodu; {@code toplam}: değerlendirilen makinist sayısı. Asıl neden önde: önce kapasite nedenleri
     * (çok olandan aza), sonra yetkisizlik nedenleri; ikisi birlikteyse başlıkta dağılım: {@code "0/38 uygun (yetkili 12, yetkisiz 26): haftalık süre 7, ..., hat yetkisi 20"}.
     */
    static String ozet(int toplam, Collection<String> ilkKodlar, Parametreler p) {
        Map<String, Integer> kapasite = new TreeMap<>();
        Map<String, Integer> yetkisiz = new TreeMap<>();
        int yetkisizSayi = 0;
        for (String kod : ilkKodlar) {
            if (yetkisizlik(kod)) {
                yetkisiz.merge(etiket(kod, p), 1, Integer::sum);
                yetkisizSayi++;
            } else {
                kapasite.merge(etiket(kod, p), 1, Integer::sum);
            }
        }
        int yetkiliSayi = ilkKodlar.size() - yetkisizSayi;
        StringBuilder sb = new StringBuilder("0/").append(toplam).append(" uygun");
        if (yetkiliSayi > 0 && yetkisizSayi > 0) {
            sb.append(" (yetkili ").append(yetkiliSayi).append(", yetkisiz ").append(yetkisizSayi).append(")");
        }
        List<String> parcalar = new ArrayList<>();
        for (Map<String, Integer> grup : List.of(kapasite, yetkisiz)) {
            List<Map.Entry<String, Integer>> sirali = new ArrayList<>(grup.entrySet());
            sirali.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
            sirali.forEach(e -> parcalar.add(e.getKey() + " " + e.getValue()));
        }
        if (!parcalar.isEmpty()) {
            sb.append(": ").append(String.join(", ", parcalar));
        }
        return sb.toString();
    }
}
