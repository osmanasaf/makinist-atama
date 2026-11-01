package com.makinistatama.tanim;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/** Resmi tatil listesi (MK-178): okuma personel (makinist 403); ekleme ve silme yalnız YONETICI. Yalnız gösterim. */
@RestController
@RequestMapping("/api/resmi-tatil")
@Personel
public class ResmiTatilController {

    private final ResmiTatilServisi servis;

    ResmiTatilController(ResmiTatilServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<ResmiTatilDto> liste(@RequestParam(required = false) Integer yil) {
        return servis.liste(yil);
    }

    @PostMapping
    @SadeceYonetici
    @ResponseStatus(HttpStatus.CREATED)
    public ResmiTatilDto ekle(@Valid @RequestBody ResmiTatilIstegi istek) {
        return servis.ekle(istek);
    }

    @DeleteMapping("/{tarih}")
    @SadeceYonetici
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sil(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih) {
        servis.sil(tarih);
    }
}
