package com.makinistatama.ortak;

import java.text.Normalizer;

/**
 * Arama için Türkçe ASCII katlama: {@code İ I ı i → i}, {@code Ç→c Ğ→g Ö→o Ş→s Ü→u}, gerisi küçük harf.
 * Yerel ayardan bağımsızdır ({@link Character#toLowerCase(char)} yerel kullanmaz; "I" ve "İ" ayrıca eşlenir), bu yüzden
 * tr-TR makinede de {@code "ISIK"} ile {@code "Işık"} aynı katlanmış değeri verir. Kırpma yapmaz.
 */
public final class AramaMetni {

    private AramaMetni() {
    }

    public static String katla(String metin) {
        if (metin == null || metin.isEmpty()) {
            return "";
        }
        // "İ" bazı kaynaklarda I + birleşik nokta olarak gelir: önce birleşik forma getir
        String s = Normalizer.normalize(metin, Normalizer.Form.NFC);
        StringBuilder sonuc = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sonuc.append(switch (c) {
                case 'İ', 'I', 'ı', 'i' -> 'i';
                case 'Ç', 'ç' -> 'c';
                case 'Ğ', 'ğ' -> 'g';
                case 'Ö', 'ö' -> 'o';
                case 'Ş', 'ş' -> 's';
                case 'Ü', 'ü' -> 'u';
                default -> Character.toLowerCase(c);
            });
        }
        return sonuc.toString();
    }
}
