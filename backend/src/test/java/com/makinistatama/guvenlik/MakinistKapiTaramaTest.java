package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * NFR-M01 / FR-M03 kapı kanıtı: MAKINIST oturumu {@link GuvenlikTaramaTest#ROLLER} tablosundaki HER personel/yönetici ucunda 403 alır (dış çit +
 * varsayılan ret); yalnız {@code /api/auth/*} (kimlik, çıkış, parola) açıktır. Var olmayan {@code /api/xyz} MAKINIST'te 403, ATAMACI'da 404 (dış kat kanıtı).
 * Gövdelerde kişisel veri yoktur.
 */
class MakinistKapiTaramaTest extends TestVeritabani {

    private static List<String> personelUclari() {
        Map<String, String> tablo = new TreeMap<>(GuvenlikTaramaTest.ROLLER);
        tablo.entrySet().removeIf(e -> "A".equals(e.getValue()) || "M".equals(e.getValue()) || e.getKey().contains(" /api/auth/"));
        return List.copyOf(tablo.keySet());
    }

    @Test
    void makinistTumPersonelVeYoneticiUclarindaIki403() throws Exception {
        yonetici(); // kayıtlı e-posta var: yanıtta geçmemeli
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");

        List<String> sorunlar = new ArrayList<>();
        int sayac = 0;
        for (String anahtar : personelUclari()) {
            String yontem = anahtar.substring(0, anahtar.indexOf(' '));
            String yol = anahtar.substring(anahtar.indexOf(' ') + 1).replaceAll("\\{[^}]+}", "1");
            var istek = switch (yontem) {
                case "GET" -> MockMvcRequestBuilders.get(yol);
                case "POST" -> MockMvcRequestBuilders.post(yol).contentType("application/json").content("{}");
                case "PUT" -> MockMvcRequestBuilders.put(yol).contentType("application/json").content("{}");
                case "DELETE" -> MockMvcRequestBuilders.delete(yol);
                default -> throw new IllegalStateException(anahtar);
            };
            var yanit = m.istek(istek).andReturn().getResponse();
            String govde = yanit.getContentAsString();
            if (yanit.getStatus() != 403 || govde.contains("@ornek.test") || govde.contains("com.makinistatama") || govde.contains("Exception")) {
                sorunlar.add(anahtar + " -> " + yanit.getStatus());
            }
            sayac++;
        }
        assertThat(sorunlar).isEmpty();
        assertThat(sayac).as("tablodaki her personel/yönetici ucu denendi (auth ve anonim uçlar hariç)").isEqualTo(personelUclari().size()).isGreaterThanOrEqualTo(54);
    }

    @Test
    void makinist_kimlikCikisAcik_olmayanUc403_atamaci404() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");
        assertThat(m.get("/api/xyz").andReturn().getResponse().getStatus()).as("dış çit: var olmayan /api ucu").isEqualTo(403);
        assertThat(atamaci().get("/api/xyz").andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(m.get("/api/auth/ben").andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(m.post("/api/auth/cikis", "").andReturn().getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void makinistKimligiYanitiParolaVeKarmaTasimaz() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici m = makinistGirisli(id, "D001");
        String govde = m.get("/api/auth/ben").andReturn().getResponse().getContentAsString();
        assertThat(govde).contains("\"rol\":\"MAKINIST\"").contains("\"parolaDegistirmeli\":false").contains("\"makinistId\":" + id)
                .doesNotContain("karma").doesNotContain("$2a$").doesNotContain("parola\":\"");
    }
}
