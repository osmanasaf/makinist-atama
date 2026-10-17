package com.makinistatama.rapor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.atama.KadroIstegi;
import com.makinistatama.atama.KadroRaporuDto;
import com.makinistatama.atama.KadroTahminServisi;
import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/**
 * Kadro ihtiyacı tahmini (F3a, MK-79): yalnız YONETICI. POST çünkü gövde büyük olabilir (sanal sefer satırları); okuma niteliğinde, yan etkisiz (veritabanına
 * yazmaz, denetim kaydı üretmez). Excel: gövde istemcinin aldığı rapordur, sunucu yeniden hesaplamaz ({@link KadroExcel}).
 */
@RestController
@RequestMapping("/api/rapor/kadro")
@SadeceYonetici
public class KadroController {

    private final KadroTahminServisi servis;

    KadroController(KadroTahminServisi servis) {
        this.servis = servis;
    }

    @PostMapping
    public KadroRaporuDto hesapla(@Valid @RequestBody KadroIstegi istek) {
        return servis.hesapla(istek);
    }

    @PostMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestBody KadroRaporuDto rapor) {
        byte[] icerik = KadroExcel.yaz(rapor);
        return ResponseEntity.ok().contentType(AdaletController.XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("kadro-ihtiyaci-" + rapor.varsayim().bas() + ".xlsx").build().toString())
                .body(icerik);
    }
}
