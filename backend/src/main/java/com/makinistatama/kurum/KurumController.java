package com.makinistatama.kurum;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Acik;
import com.makinistatama.guvenlik.SadeceYonetici;
import com.makinistatama.ortak.UygulamaHatasi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Kurum adı + logosu (MK-167/168). Anonim yüzey YALNIZ iki GET'tir ({@code /acik}, {@code /logo}; {@code SecurityConfig} aynı iki yolu açar, {@code GuvenlikTaramaTest} anonim uç kümesini
 * kilitler): giriş sayfası ve makinist kabuğu oturumsuz marka gösterir. Yazma yalnız YONETICI. Logo gövdesi ham bayttır (en çok 256 KB + 1 okunur; dosya adı kullanılmaz); yanıt
 * sabit içerik türü + {@code nosniff} + kutulayan CSP ile verilir.
 */
@RestController
@RequestMapping("/api/kurum")
public class KurumController {

    private final KurumServisi servis;

    KurumController(KurumServisi servis) {
        this.servis = servis;
    }

    @GetMapping("/acik")
    @Acik
    public KurumDto.Acik acik() {
        return servis.acik();
    }

    @GetMapping("/logo")
    @Acik
    public ResponseEntity<byte[]> logo(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String eslesen) {
        var surum = servis.logoSurumu().orElse(null); // baytsız: ETag eşleşirse logo satırı hiç okunmaz (anonim uç)
        if (surum == null) {
            return ResponseEntity.notFound().build();
        }
        String etag = "\"" + surum + "\"";
        if (etag.equals(eslesen)) {
            return yanit(HttpStatus.NOT_MODIFIED, etag).build();
        }
        var logo = servis.logo().orElse(null);
        if (logo == null) {
            return ResponseEntity.notFound().build();
        }
        return yanit(HttpStatus.OK, "\"" + logo.surum() + "\"").contentType(MediaType.parseMediaType("PNG".equals(logo.tur()) ? "image/png" : "image/jpeg")).body(logo.bayt());
    }

    private static ResponseEntity.BodyBuilder yanit(HttpStatus durum, String etag) {
        return ResponseEntity.status(durum).eTag(etag).cacheControl(CacheControl.noCache()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox");
    }

    @PutMapping
    @SadeceYonetici
    public KurumDto.Acik adYaz(@Valid @RequestBody KurumDto.AdIstegi istek) {
        return servis.adYaz(istek.ad());
    }

    @PutMapping("/logo")
    @SadeceYonetici
    public KurumDto.Acik logoYaz(HttpServletRequest istek) throws IOException {
        byte[] bayt = istek.getInputStream().readNBytes(KurumServisi.EN_FAZLA_BAYT + 1);
        if (bayt.length > KurumServisi.EN_FAZLA_BAYT) {
            throw UygulamaHatasi.gecersizDosya("Logo en fazla 256 KB olabilir.");
        }
        return servis.logoYaz(bayt);
    }

    @DeleteMapping("/logo")
    @SadeceYonetici
    public KurumDto.Acik logoSil() {
        return servis.logoSil();
    }
}
