package com.makinistatama;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Kalıcı koruma: kaynakta çift kodlanmış (UTF-8 -> Windows-1252 -> UTF-8) Türkçe bulunmaz. Bozuk metin kullanıcıya olduğu gibi gider ve testler yalnız
 * {@code kod} alanına baktığından fark edilmez (serbest intikal BUG-1). Bozuk dizilerin kendisi bu dosyada da yazılmaz: kaçış dizileriyle üretilir.
 */
class KaynakKodlamaTest {

    /** Öncü bayt karakteri (A-tilde, A-circumflex, A-umlaut, A-ring) + devam baytı (U+0080..U+00BF ya da Windows-1252'deki karşılığı); "a-circumflex" + devam; ya da U+FFFD. Geçerli Türkçe eşleşmez. */
    private static final String DEVAM = "[\\u0080-\\u00BF\\u0152\\u0153\\u0160\\u0161\\u0178\\u017D\\u017E\\u0192\\u02C6\\u02DC\\u2013\\u2014\\u2018-\\u201E\\u2020-\\u2022\\u2026\\u2030\\u2039\\u203A\\u20AC\\u2122]";
    static final Pattern BOZUK = Pattern.compile("[\\u00C2\\u00C3\\u00C4\\u00C5]" + DEVAM + "|\\u00E2" + DEVAM + "|\\uFFFD");

    private static final Pattern UZANTI = Pattern.compile(".*\\.(java|ts|vue|js|css|html|json|sql|properties|yml|yaml)$");

    @Test
    void kaynaklardaMojibakeYok() throws IOException {
        List<String> bulunan = new ArrayList<>();
        for (Path kok : List.of(Path.of("src"), Path.of("..", "frontend", "src"))) {
            assertThat(kok).as("kaynak dizini bulunamadı: çalışma dizini backend/ olmalı").isDirectory();
            try (Stream<Path> dosyalar = Files.walk(kok)) {
                for (Path p : dosyalar.filter(f -> Files.isRegularFile(f) && UZANTI.matcher(f.toString().replace('\\', '/')).matches()).toList()) {
                    List<String> satirlar = Files.readAllLines(p);
                    for (int i = 0; i < satirlar.size(); i++) {
                        if (BOZUK.matcher(satirlar.get(i)).find()) {
                            bulunan.add(p.toString().replace('\\', '/') + ":" + (i + 1));
                        }
                    }
                }
            }
        }
        assertThat(bulunan).as("çift kodlanmış Türkçe (mojibake) satırları").isEmpty();
    }

    private static String m(int... kodNoktalari) {
        return new String(kodNoktalari, 0, kodNoktalari.length);
    }

    @Test
    void desen_bozukMetniYakalar_gecerliMetniYakalamaz() {
        // örnekler: noktalı büyük i, ö, ok işareti, ş (cp1252 yolu), ı, bir C1 denetim karakteri, U+FFFD
        assertThat(BOZUK.matcher(m(0xC4, 0xB0) + "ntikal").find()).isTrue();
        assertThat(BOZUK.matcher(m(0xC3, 0xB6) + "neri").find()).isTrue();
        assertThat(BOZUK.matcher("A " + m(0xE2, 0x2020, 0x2019) + " B").find()).isTrue();
        assertThat(BOZUK.matcher(m(0xC5, 0x0178)).find()).isTrue();
        assertThat(BOZUK.matcher("kald" + m(0xC4, 0xB1)).find()).isTrue();
        assertThat(BOZUK.matcher("kal" + m(0xC4, 0x9F)).find()).isTrue();
        assertThat(BOZUK.matcher("bozuk " + m(0xFFFD)).find()).isTrue();
        assertThat(BOZUK.matcher("İntikal öneri çığ şüğ → hâlâ kâr ÇĞİÖŞÜ âlem ― Ankara – Sivas").find()).isFalse();
    }
}
