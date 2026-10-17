package com.makinistatama.guvenlik;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


/** Test yardımcısı: rol tablosundaki ({@code GuvenlikTaramaTest.ROLLER}) "M" uçları. Yeni {@code /api/benim} ucu tabloya eklenince bu taramalar onu otomatik kapsar. */
public final class BenimUclari {

    private BenimUclari() {
    }

    /** "YÖNTEM /yol" anahtarları. */
    public static List<String> tumu() {
        Map<String, String> tablo = new TreeMap<>(GuvenlikTaramaTest.ROLLER);
        tablo.entrySet().removeIf(e -> !"M".equals(e.getValue()));
        return List.copyOf(tablo.keySet());
    }

    public static AbstractMockHttpServletRequestBuilder<?> istek(String anahtar) {
        String yontem = anahtar.substring(0, anahtar.indexOf(' '));
        String yol = anahtar.substring(anahtar.indexOf(' ') + 1).replaceAll("\\{[^}]+}", "1");
        return switch (yontem) {
            case "GET" -> MockMvcRequestBuilders.get(yol).queryParam("atamaId", "1"); // zorunlu sorgu parametresi isteyen okuma uçları (takas/hedefler) için; diğerleri yok sayar
            case "POST" -> MockMvcRequestBuilders.post(yol).contentType("application/json").content("{}");
            case "PUT" -> MockMvcRequestBuilders.put(yol).contentType("application/json").content("{}");
            case "DELETE" -> MockMvcRequestBuilders.delete(yol);
            default -> throw new IllegalStateException(anahtar);
        };
    }

    public static boolean okuma(String anahtar) {
        return anahtar.startsWith("GET ");
    }
}
