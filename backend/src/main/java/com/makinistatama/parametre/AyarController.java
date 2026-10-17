package com.makinistatama.parametre;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/** Uygulama ayarı yazma (yalnız YONETICI, neden zorunlu). Okuma da yalnız YONETICI (Ayarlar ekranı). */
@RestController
@RequestMapping("/api/ayar")
public class AyarController {

    private final AyarServisi servis;

    AyarController(AyarServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    @SadeceYonetici
    public List<AyarDto> liste() {
        return servis.liste();
    }

    @PutMapping("/{anahtar}")
    @SadeceYonetici
    public AyarDto degerDegistir(@PathVariable String anahtar, @Valid @RequestBody AyarGuncellemeIstegi istek) {
        return servis.degerDegistir(anahtar, istek);
    }
}
