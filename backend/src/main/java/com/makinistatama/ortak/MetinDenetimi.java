package com.makinistatama.ortak;

/** Kullanıcı metni denetimleri (kurum adı, tatil adı): ekranda/dışa aktarımda görünümü bozan karakterler. */
public final class MetinDenetimi {

    private MetinDenetimi() {
    }

    /**
     * Kontrol karakteri (NUL, sekme, satır sonu...) ya da biçim karakteri (Unicode Cf: yön denetimi {@code U+202A..202E, U+2066..2069}, sıfır genişlik, BOM) var mı. Bunlar metni
     * görünmez yapar ya da başka metnin yönünü tersine çevirir; Türkçe harfler ve noktalama etkilenmez.
     */
    public static boolean gorunumBozanKarakterVar(String metin) {
        return metin.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT);
    }
}
