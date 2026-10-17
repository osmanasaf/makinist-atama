package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * QA BUG-5: sınırı aşan dosya (5 MB dosya / 6 MB istek) GERÇEK Tomcat'e gönderilince bağlantı kopmamalı, anlamlı 413 gelmeli.
 * MockMvc Tomcat'in çok parçalı ayrıştırmasını ve erken yanıt sonrası gövdeyi yutmasını (max-swallow-size) çalıştırmaz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class YuklemeBoyutGercekSunucuTest extends TestVeritabani {

    @LocalServerPort
    int port;

    @Test
    void sinirinUstundekiDosya_anlamliHata_baglantiKopmaz() throws Exception {
        yonetici(); // kullanıcıyı oluşturur
        CookieManager cerezler = new CookieManager();
        HttpClient http = HttpClient.newBuilder().cookieHandler(cerezler).build();
        String kok = "http://localhost:" + port;

        http.send(HttpRequest.newBuilder(URI.create(kok + "/api/auth/ben")).build(), HttpResponse.BodyHandlers.discarding()); // XSRF çerezi
        String xsrf = cerezler.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
        HttpResponse<String> giris = http.send(HttpRequest.newBuilder(URI.create(kok + "/api/auth/giris"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", xsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"eposta\":\"yonetici@ornek.test\",\"parola\":\"" + Tarayici.PAROLA + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(giris.statusCode()).isEqualTo(200);
        String xsrfGiris = cerezler.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();

        for (int mb : new int[] { 6, 16 }) { // 6 MB: istek sınırının hemen üstü; 16 MB: büyük dosya
            String sinir = "----" + UUID.randomUUID();
            byte[] baslik = ("--" + sinir + "\r\nContent-Disposition: form-data; name=\"dosya\"; filename=\"dev.xlsx\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] icerik = new byte[mb * 1024 * 1024];
            Arrays.fill(icerik, (byte) 1);
            byte[] son = ("\r\n--" + sinir + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpResponse<String> yanit = http.send(HttpRequest.newBuilder(URI.create(kok + "/api/sefer/yukle?onizle=true"))
                    .header("Content-Type", "multipart/form-data; boundary=" + sinir).header("X-XSRF-TOKEN", xsrfGiris)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(baslik, icerik, son))).build(), HttpResponse.BodyHandlers.ofString());

            assertThat(yanit.statusCode()).as("%d MB", mb).isEqualTo(413);
            assertThat(yanit.body()).contains("DOSYA_COK_BUYUK").contains("En çok 5 MB");
        }
    }
}
