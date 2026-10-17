package com.makinistatama.rapor;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.guvenlik.Personel;

/** Şeffaflık / denetim raporu (FR-08): giriş yapmış herkes okur (ATAMACI ve YONETICI); kayıtlar ek-yalnızdır, bu uçlar yalnız okur. */
@RestController
@RequestMapping("/api/degisiklik")
@Personel
public class DegisiklikController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DegisiklikRaporServisi servis;

    DegisiklikController(DegisiklikRaporServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public DegisiklikRaporuDto rapor(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit, @RequestParam(required = false) Long makinistId,
            @RequestParam(required = false) String trenNo, @RequestParam(required = false) String kullanici, @RequestParam(required = false) DegisiklikTuru tur) {
        return servis.rapor(bas, bit, makinistId, trenNo, kullanici, tur);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit, @RequestParam(required = false) Long makinistId,
            @RequestParam(required = false) String trenNo, @RequestParam(required = false) String kullanici, @RequestParam(required = false) DegisiklikTuru tur) {
        byte[] xlsx = servis.excel(servis.rapor(bas, bit, makinistId, trenNo, kullanici, tur));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("degisiklik-raporu-" + bas + "_" + bit + ".xlsx").build().toString())
                .body(xlsx);
    }
}
