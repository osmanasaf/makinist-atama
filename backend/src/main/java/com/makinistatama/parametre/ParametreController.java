package com.makinistatama.parametre;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.validation.Valid;

/** Okuma: giriş yapmış herkes (atama ekranı eşikleri ve kaynakları görür). Yazma: yalnız YONETICI, neden zorunlu. */
@RestController
@RequestMapping("/api")
public class ParametreController {

    private final ParametreServisi servis;

    ParametreController(ParametreServisi servis) {
        this.servis = servis;
    }

    @GetMapping("/parametre")
    @Personel
    public List<ParametreDto> parametreler() {
        return servis.parametreListesi();
    }

    @GetMapping("/kural")
    @Personel
    public List<KuralSiddetDto> kurallar() {
        return servis.kuralListesi();
    }

    @PutMapping("/parametre/{anahtar}")
    @SadeceYonetici
    public ParametreDto degerDegistir(@PathVariable String anahtar, @Valid @RequestBody ParametreGuncellemeIstegi istek) {
        return servis.degerDegistir(anahtar, istek);
    }

    @PutMapping("/kural/{kod}/siddet")
    @SadeceYonetici
    public KuralSiddetDto siddetDegistir(@PathVariable String kod, @Valid @RequestBody SiddetGuncellemeIstegi istek) {
        return servis.siddetDegistir(kod, istek);
    }
}
