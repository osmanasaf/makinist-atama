package com.makinistatama.parametre;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;

import tools.jackson.databind.JsonNode;

/**
 * Denetim kaydındaki kural JSON'unu (eski → yeni tam {@code OzelKural}) okunur metne çevirir: kimlikler tanım adına ("Hat: Ankara–Sivas"), kodlar Türkçe etikete
 * ("Engel", "Eşik 8 s → 9 s"). JSON tolerant okunur (ileride alan eklenirse eski kayıtlar da çevrilir); bilinmeyen değer olduğu gibi kalır.
 */
public final class OzelKuralMetni {

    private OzelKuralMetni() {
    }

    /** Tek kural, tek satır: {@code "Ö-003 Dizel hatlar — Ölçü Tek sefer süresi · Pencere sefer · Koşul en fazla · Eşik 8 s · Hat: Ankara–Sivas · Şiddet Engel"}. */
    public static String ozet(JsonNode k, Map<Long, String> adlar) {
        return baslik(k) + " — " + alanlar(k, adlar).entrySet().stream().filter(e -> !e.getKey().equals("ad") && !e.getKey().equals("gerekce")).map(Map.Entry::getValue)
                .collect(Collectors.joining(" · "));
    }

    /**
     * Güncelleme: yalnız değişen alanlar. {@code [0]} eski değerler, {@code [1]} yeni değerler ("Ö-003 Dizel hatlar — Eşik 8 s · Şiddet Engel" →
     * "Eşik 9 s · Şiddet Uyarı"); değişen alan yoksa ikisi de özettir.
     */
    public static String[] fark(JsonNode eski, JsonNode yeni, Map<Long, String> adlar) {
        Map<String, String> e = alanlar(eski, adlar);
        Map<String, String> y = alanlar(yeni, adlar);
        List<String> eskiParca = new ArrayList<>();
        List<String> yeniParca = new ArrayList<>();
        for (String anahtar : y.keySet()) {
            String a = e.getOrDefault(anahtar, "—");
            String b = y.get(anahtar);
            if (!a.equals(b)) {
                eskiParca.add(a);
                yeniParca.add(b);
            }
        }
        if (eskiParca.isEmpty()) {
            return new String[] { ozet(eski, adlar), ozet(yeni, adlar) };
        }
        return new String[] { baslik(eski) + " — " + String.join(" · ", eskiParca), String.join(" · ", yeniParca) };
    }

    private static String baslik(JsonNode k) {
        return kod(k.path("id").asLong(0)) + " " + k.path("ad").asString("");
    }

    private static String kod(long id) {
        return com.makinistatama.kural.OzelKural.kod(id);
    }

    /** Alan etiketi → okunur değer (sıralı). Değer metni etiketi de içerir ("Eşik 8 s"), böylece farkta tek başına okunur. */
    private static Map<String, String> alanlar(JsonNode k, Map<Long, String> adlar) {
        Map<String, String> m = new LinkedHashMap<>();
        Olcu olcu = enumDegeri(Olcu.class, k.path("olcu").asString(""));
        Pencere pencere = enumDegeri(Pencere.class, k.path("pencere").asString(""));
        Karsilastirma kars = enumDegeri(Karsilastirma.class, k.path("karsilastirma").asString(""));
        m.put("ad", "Ad " + k.path("ad").asString(""));
        m.put("olcu", "Ölçü " + (olcu == null ? k.path("olcu").asString("") : olcu.ad()));
        if (pencere != null) {
            JsonNode kayan = k.path("kayanGun");
            m.put("pencere", pencere == Pencere.KAYAN && kayan.isNumber() ? "Pencere kayan " + kayan.asInt() + " gün" : "Pencere " + pencere.ad());
        }
        String birim = olcu == null ? "" : " " + olcu.birim();
        m.put("kars", "Koşul " + (kars == null ? k.path("karsilastirma").asString("") : kars.ad()));
        m.put("esik", "Eşik " + sayi(k.path("esik").asString("")) + birim);
        JsonNode kapsam = k.path("kapsam");
        kume(m, "hat", "Hat", kapsam.path("hatlar"), adlar);
        kume(m, "arac", "Araç tipi", kapsam.path("aracTipleri"), adlar);
        kume(m, "kalkis", "Kalkış garı", kapsam.path("kalkisGarlari"), adlar);
        kume(m, "varis", "Varış garı", kapsam.path("varisGarlari"), adlar);
        if (kapsam.path("trenTurleri").size() > 0) {
            m.put("tren", "Tren: " + metinler(kapsam.path("trenTurleri"), t -> t.equals("YUK") ? "Yük" : t.equals("YOLCU") ? "Yolcu" : t));
        }
        if (kapsam.path("personelTurleri").size() > 0) {
            m.put("personel", "Personel: " + metinler(kapsam.path("personelTurleri"), t -> t.equals("KONDUKTOR") ? "Kondüktör" : t.equals("MAKINIST") ? "Makinist" : t));
        }
        String vardiya = kapsam.path("vardiya").asString("HEPSI");
        if (!vardiya.equals("HEPSI")) {
            m.put("vardiya", "Vardiya: " + (vardiya.equals("GECE") ? "gece" : vardiya.equals("GUNDUZ") ? "gündüz" : vardiya));
        }
        Siddet siddet = enumDegeri(Siddet.class, k.path("siddet").asString(""));
        m.put("siddet", "Şiddet " + (siddet == null ? k.path("siddet").asString("") : siddetAdi(siddet)));
        String bas = k.path("gecerlilikBas").asString("");
        String bit = k.path("gecerlilikBit").isNull() ? "" : k.path("gecerlilikBit").asString("");
        m.put("gecerlilik", "Geçerlilik " + (bas.isEmpty() ? "—" : bas) + (bit.isEmpty() ? "" : " – " + bit));
        String gerekce = k.path("gerekce").asString("");
        if (!gerekce.isEmpty()) {
            m.put("gerekce", "Gerekçe: " + gerekce);
        }
        return m;
    }

    /** Kümedeki tanım kimliklerini ad olarak yazar ("Hat: Ankara–Sivas, Eskişehir–Konya"). */
    private static void kume(Map<String, String> m, String anahtar, String etiket, JsonNode dizi, Map<Long, String> adlar) {
        if (dizi.size() > 0) {
            Set<Long> idler = new TreeSet<>();
            dizi.forEach(n -> idler.add(n.asLong()));
            m.put(anahtar, etiket + ": " + idler.stream().map(i -> adlar.getOrDefault(i, "#" + i)).collect(Collectors.joining(", ")));
        }
    }

    private static String metinler(JsonNode dizi, Function<String, String> f) {
        List<String> l = new ArrayList<>();
        dizi.forEach(n -> l.add(f.apply(n.asString(""))));
        return String.join(", ", l);
    }

    private static String sayi(String s) {
        try {
            return new java.math.BigDecimal(s).stripTrailingZeros().toPlainString().replace('.', ',');
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static String siddetAdi(Siddet s) {
        return switch (s) {
            case ENGEL -> "Engel";
            case UYARI -> "Uyarı";
            case KAPALI -> "Kapalı";
        };
    }

    private static <E extends Enum<E>> E enumDegeri(Class<E> tip, String ad) {
        try {
            return Enum.valueOf(tip, ad);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
