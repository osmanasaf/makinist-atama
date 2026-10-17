package com.makinistatama.makinist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;

import jakarta.validation.Valid;

/** Giriş yapmış herkes (ATAMACI ve YONETICI) makinist kartını okur ve yazar. */
@RestController
@RequestMapping("/api/makinist")
@Personel
public class MakinistController {

    private final MakinistServisi servis;

    MakinistController(MakinistServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<MakinistDto.Ozet> liste(@RequestParam(required = false) String arama, @RequestParam(required = false) MakinistDurum durum,
            @RequestParam(required = false) PersonelTuru tur) {
        return servis.liste(arama, durum, tur == null ? PersonelTuru.TUMU : java.util.Set.of(tur));
    }

    @GetMapping("/{id}")
    public MakinistDto getir(@PathVariable long id) {
        return servis.getir(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MakinistDto olustur(@Valid @RequestBody MakinistIstegi istek) {
        return servis.olustur(istek);
    }

    @PutMapping("/{id}")
    public MakinistDto guncelle(@PathVariable long id, @Valid @RequestBody MakinistIstegi istek) {
        servis.guncelle(id, istek);
        return servis.getir(id); // sürüm artışı (OPTIMISTIC_FORCE_INCREMENT) işlem sonunda yazılır: güncel sürüm için yeniden okunur
    }

    @PostMapping("/{id}/izin")
    @ResponseStatus(HttpStatus.CREATED)
    public MakinistDto.IzinDto izinEkle(@PathVariable long id, @Valid @RequestBody IzinIstegi istek) {
        return servis.izinEkle(id, istek);
    }
}
