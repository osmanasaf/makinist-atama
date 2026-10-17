package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.degisiklik.Neden;
import com.makinistatama.guvenlik.Personel;

import jakarta.validation.Valid;

/** Serbest intikal (MK-106): giriş yapmış personel (ATAMACI ve YONETICI) yazar ve okur; MAKINIST yalnız {@code /api/benim/...} ile kendi kaydını okur ("gördüm"). */
@RestController
@RequestMapping("/api/intikal")
@Personel
public class IntikalController {

    private final IntikalServisi servis;

    IntikalController(IntikalServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<IntikalDto> liste(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit,
            @RequestParam(required = false) Long makinistId) {
        return servis.liste(bas, bit, makinistId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntikalSonucuDto ekle(@Valid @RequestBody IntikalIstegi istek) {
        return servis.ekle(istek);
    }

    @PutMapping("/{id}")
    public IntikalSonucuDto guncelle(@PathVariable long id, @Valid @RequestBody IntikalIstegi istek) {
        return servis.guncelle(id, istek);
    }

    /** İptal (silme yok): {@code neden} zorunlu; yanıt iptal sonrası yeni kırmızı olan atamaları da taşır. */
    @DeleteMapping("/{id}")
    public IntikalSonucuDto iptal(@PathVariable long id, @RequestParam(required = false) Long versiyon, @RequestParam(required = false) Neden neden,
            @RequestParam(required = false) String nedenNotu) {
        return servis.iptal(id, versiyon, neden, nedenNotu);
    }
}
