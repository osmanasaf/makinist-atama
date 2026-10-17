package com.makinistatama.rapor;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.makinist.PersonelTuru;

/** Puantaj ön hesabı (FR-12): giriş yapmış herkes okur; salt okuma. {@code ay=YYYY-MM}, isteğe bağlı {@code makinist} (kimlik). */
@RestController
@RequestMapping("/api/rapor/puantaj")
@Personel
public class PuantajController {

    private final PuantajServisi servis;

    PuantajController(PuantajServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public PuantajRaporuDto rapor(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay, @RequestParam(required = false) Long makinist,
            @RequestParam(required = false) PersonelTuru tur) {
        return servis.rapor(ay, makinist, tur == null ? PersonelTuru.MAKINIST : tur);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay, @RequestParam(required = false) Long makinist,
            @RequestParam(required = false) PersonelTuru tur) {
        return ResponseEntity.ok().contentType(AdaletController.XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("puantaj-" + ay + ".xlsx").build().toString())
                .body(servis.excel(servis.rapor(ay, makinist, tur == null ? PersonelTuru.MAKINIST : tur)));
    }
}
