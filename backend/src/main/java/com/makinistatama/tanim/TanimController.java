package com.makinistatama.tanim;

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
import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/** Okuma: giriş yapmış herkes (ATAMACI dahil); ekleme ve yeniden adlandırma yalnız YONETICI. */
@RestController
@RequestMapping("/api/tanimlar")
@Personel
public class TanimController {

    private final TanimServisi servis;

    TanimController(TanimServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<TanimDto> liste(@RequestParam TanimTipi tip) {
        return servis.liste(tip);
    }

    @PostMapping
    @SadeceYonetici
    @ResponseStatus(HttpStatus.CREATED)
    public TanimDto ekle(@Valid @RequestBody TanimIstegi istek) {
        return servis.ekle(istek.tip(), istek.ad());
    }

    @PutMapping("/{id}")
    @SadeceYonetici
    public TanimDto yenidenAdlandir(@PathVariable long id, @Valid @RequestBody TanimAdIstegi istek) {
        return servis.yenidenAdlandir(id, istek.ad());
    }
}
