package com.makinistatama.makinist;

import java.io.IOException;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.makinistatama.guvenlik.SadeceYonetici;
import com.makinistatama.ortak.UygulamaHatasi;

/** Makinist toplu içe aktarma (MK-170): kişi listesi toplu yazıldığından yalnız YONETICI (atamacı tek tek eklemeye devam eder). Dosya sunucuda saklanmaz. */
@RestController
@RequestMapping("/api/makinist/ice-aktar")
@SadeceYonetici
public class MakinistIceAktarController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MakinistIceAktarServisi servis;

    MakinistIceAktarController(MakinistIceAktarServisi servis) {
        this.servis = servis;
    }

    /** Önizleme ({@code onizle=true}, yazmaz) ve onay aynı uç. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MakinistYuklemeSonucuDto yukle(@RequestParam("dosya") MultipartFile dosya, @RequestParam(defaultValue = "true") boolean onizle) {
        try {
            return servis.yukle(dosya.getBytes(), onizle);
        } catch (IOException e) {
            throw UygulamaHatasi.gecersizDosya("Dosya okunamadı.");
        }
    }

    @GetMapping("/sablon")
    public ResponseEntity<byte[]> sablon() {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("makinist-sablonu.xlsx").build().toString()).body(servis.sablon());
    }
}
