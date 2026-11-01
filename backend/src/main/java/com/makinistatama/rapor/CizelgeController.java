package com.makinistatama.rapor;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.makinist.PersonelTuru;

/**
 * Aylık çizelge ızgarası (F5, MK-177): yalnız personel (atamacı, yönetici); makinist 403 (başkalarının satırı/toplamı, OK-35). Salt okuma. {@code ay=YYYY-MM}; {@code tur} (vars. MAKINIST);
 * {@code garId} bağlı gar süzgeci; {@code kontrol=true} canlı kırmızı bayrağını da hesaplar (ağır; vars. kapalı).
 */
@RestController
@RequestMapping("/api/rapor/cizelge")
@Personel
public class CizelgeController {

    private final CizelgeServisi servis;

    CizelgeController(CizelgeServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public CizelgeDto cizelge(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay, @RequestParam(required = false) PersonelTuru tur, @RequestParam(required = false) Long garId,
            @RequestParam(defaultValue = "false") boolean kontrol) {
        return servis.cizelge(ay, tur == null ? PersonelTuru.MAKINIST : tur, garId, kontrol);
    }
}
