package com.makinistatama.rapor;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;

/** Günlük yedek ihtiyacı (FR-11): giriş yapmış herkes okur. {@code ay=YYYY-MM}. */
@RestController
@RequestMapping("/api/rapor/yedek")
@Personel
public class YedekController {

    private final YedekServisi servis;

    YedekController(YedekServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public YedekRaporuDto rapor(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay) {
        return servis.rapor(ay);
    }
}
