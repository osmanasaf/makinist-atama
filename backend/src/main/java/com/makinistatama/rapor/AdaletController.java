package com.makinistatama.rapor;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.makinist.PersonelTuru;

/** Adalet raporu (FR-10): giriş yapmış herkes okur; salt okuma. {@code ay=YYYY-MM}; {@code tur} (F3b, vars. MAKINIST): tür bazında ayrı grup. */
@RestController
@RequestMapping("/api/rapor/adalet")
@Personel
public class AdaletController {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AdaletServisi servis;

    AdaletController(AdaletServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public AdaletRaporuDto rapor(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay, @RequestParam(required = false) PersonelTuru tur) {
        return servis.rapor(ay, tur == null ? PersonelTuru.MAKINIST : tur);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay, @RequestParam(required = false) PersonelTuru tur) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("adalet-raporu-" + ay + ".xlsx").build().toString())
                .body(servis.excel(servis.rapor(ay, tur == null ? PersonelTuru.MAKINIST : tur)));
    }
}
