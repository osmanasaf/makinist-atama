package com.makinistatama.sefer;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.ortak.UygulamaHatasi;

import jakarta.validation.Valid;

/** Giriş yapmış herkes (ATAMACI ve YONETICI) sefer okur ve yazar. */
@RestController
@RequestMapping("/api/sefer")
@Personel
public class SeferController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SeferServisi servis;
    private final SeferYuklemeServisi yukleme;

    SeferController(SeferServisi servis, SeferYuklemeServisi yukleme) {
        this.servis = servis;
        this.yukleme = yukleme;
    }

    @GetMapping
    public List<SeferDto> gun(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gun) {
        return servis.gunListesi(gun);
    }

    @GetMapping("/{id}")
    public SeferDto getir(@PathVariable long id) {
        return servis.getir(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeferDto olustur(@Valid @RequestBody SeferIstegi istek) {
        return servis.olustur(istek);
    }

    @PostMapping("/{id}/iptal")
    public SeferDto iptal(@PathVariable long id, @Valid @RequestBody SeferIptalIstegi istek) {
        return servis.iptalEt(id, istek);
    }

    @PostMapping("/{id}/gecikme")
    public SeferDto gecikme(@PathVariable long id, @Valid @RequestBody SeferGecikmeIstegi istek) {
        return servis.gecikmeGir(id, istek);
    }

    @PutMapping("/{id}")
    public SeferDto guncelle(@PathVariable long id, @Valid @RequestBody SeferIstegi istek) {
        return servis.guncelle(id, istek);
    }

    /** Önizleme ({@code onizle=true}, yazmaz) ve onay aynı uç; dosya sunucuda saklanmaz. */
    @PostMapping(value = "/yukle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public YuklemeSonucuDto yukle(@RequestParam("dosya") MultipartFile dosya, @RequestParam(defaultValue = "true") boolean onizle) {
        try {
            return yukleme.yukle(dosya.getBytes(), onizle);
        } catch (IOException e) {
            throw UygulamaHatasi.gecersizDosya("Dosya okunamadı.");
        }
    }

    @GetMapping("/sablon")
    public ResponseEntity<byte[]> sablon() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("sefer-sablonu.xlsx").build().toString())
                .body(yukleme.sablon());
    }
}
