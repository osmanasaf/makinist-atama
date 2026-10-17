package com.makinistatama.parametre;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/**
 * Kural kurucu uçları (MK-22). Okuma: giriş yapmış herkes. Yazma: yalnız YONETICI (kapatma = PUT ile şiddet KAPALI; silme yok).
 * Adresleme {@code id} iledir ({@code Ö-NNN} kodu yalnız görüntüdür).
 */
@RestController
@RequestMapping("/api/ozel-kural")
@Personel
public class OzelKuralController {

    private final OzelKuralServisi servis;

    OzelKuralController(OzelKuralServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<OzelKuralDto> liste() {
        return servis.liste();
    }

    @GetMapping("/katalog")
    public List<OlcuDto> katalog() {
        return servis.katalog();
    }

    @PostMapping
    @SadeceYonetici
    @ResponseStatus(HttpStatus.CREATED)
    public OzelKuralDto ekle(@Valid @RequestBody OzelKuralIstegi istek) {
        return servis.ekle(istek);
    }

    @PutMapping("/{id}")
    @SadeceYonetici
    public OzelKuralDto guncelle(@PathVariable long id, @Valid @RequestBody OzelKuralIstegi istek) {
        return servis.guncelle(id, istek);
    }
}
