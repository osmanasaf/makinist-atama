package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * F2b EK-1 altın dosya: bir sonucu kararlı JSON olarak {@code src/test/resources/golden/<ad>.json} ile karşılaştırır. {@code -Dgolden.yaz=true} ile YAZAR
 * (yalnız bilinçli yenileme: demo veri ya da kural bilinçli değiştiyse); aksi hâlde birebir eşitlik arar. Yalnız sayı/kimlik saklanır; sonradan eklenen alanlar
 * çağıranın seçtiği alanlara girmez.
 */
public final class AltinDosya {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final Path DIZIN = Path.of("src", "test", "resources", "golden");

    private AltinDosya() {
    }

    public static void kontrol(String ad, Object icerik) throws IOException {
        String yeni = JSON.writeValueAsString(icerik).replace("\r\n", "\n") + "\n";
        if (Boolean.getBoolean("golden.yaz")) {
            Files.createDirectories(DIZIN);
            Files.writeString(DIZIN.resolve(ad + ".json"), yeni, StandardCharsets.UTF_8);
            return;
        }
        try (InputStream in = AltinDosya.class.getResourceAsStream("/golden/" + ad + ".json")) {
            assertThat(in).as("altın dosya yok: golden/" + ad + ".json (bir kez -Dgolden.yaz=true ile yazın)").isNotNull();
            assertThat(yeni).as("altın dosya " + ad).isEqualTo(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }
}
