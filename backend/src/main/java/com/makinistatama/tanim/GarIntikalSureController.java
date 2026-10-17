package com.makinistatama.tanim;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/** Gar çifti intikal süreleri (MK-109): okuma giriş yapmış personel; yazma yalnız YONETICI. Literal yol, TanimController'ın {@code /{id}} yoluyla çakışmaz. */
@RestController
@RequestMapping("/api/tanimlar/intikal-sure")
@Personel
public class GarIntikalSureController {

    private final GarIntikalSureServisi servis;

    GarIntikalSureController(GarIntikalSureServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<GarIntikalSuresiDto> liste() {
        return servis.liste();
    }

    /** Ekler ya da günceller (çift kayıtlıysa {@code versiyon} zorunlu). */
    @PutMapping
    @SadeceYonetici
    public GarIntikalSuresiDto yaz(@Valid @RequestBody GarIntikalSureIstegi istek) {
        return servis.yaz(istek);
    }

    @DeleteMapping
    @SadeceYonetici
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sil(@RequestParam long garAId, @RequestParam long garBId) {
        servis.sil(garAId, garBId);
    }
}
