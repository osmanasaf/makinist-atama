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

import com.makinistatama.guvenlik.SadeceYonetici;

/**
 * Yönetim özeti ve denetim paketi (F4, MK-180/MK-181): YALNIZ yönetici (atamacı ve makinist 403, anonim 401; OK-35: makinist özet/ortalama görmez). Salt okuma. {@code ay=YYYY-MM} (takvim ayı),
 * bugünden en çok 36 ay önce/sonra (aksi 400).
 */
@RestController
@RequestMapping("/api/rapor")
@SadeceYonetici
public class OzetController {

    private final YonetimOzetiServisi ozet;
    private final DenetimPaketiServisi paket;

    OzetController(YonetimOzetiServisi ozet, DenetimPaketiServisi paket) {
        this.ozet = ozet;
        this.paket = paket;
    }

    @GetMapping("/ozet")
    public YonetimOzetiDto ozet(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay) {
        return ozet.ozet(ay);
    }

    @GetMapping("/denetim-paketi/excel")
    public ResponseEntity<byte[]> denetimPaketi(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay) {
        return ResponseEntity.ok().contentType(AdaletController.XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("denetim-paketi-" + ay + ".xlsx").build().toString())
                .body(paket.excel(ay));
    }
}
