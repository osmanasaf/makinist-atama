package com.makinistatama.atama;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.SadeceYonetici;
import com.makinistatama.parametre.OzelKuralIstegi;

import jakarta.validation.Valid;

/**
 * "Kuralı dene" ucu (MK-21). {@code parametre} paketi {@code atama}yı göremediğinden burada yaşar. Yazmaz; yalnız YONETICI.
 * Gövde kaydetme isteğiyle aynıdır (neden gerekmez); mevcut kuralı düzenlerken {@code id} verilir (taslak onun yerine geçer).
 */
@RestController
@RequestMapping("/api/ozel-kural")
public class OzelKuralOnizlemeController {

    private final OzelKuralOnizleme onizleme;

    OzelKuralOnizlemeController(OzelKuralOnizleme onizleme) {
        this.onizleme = onizleme;
    }

    @PostMapping("/dene")
    @SadeceYonetici
    public OzelKuralOnizlemeDto dene(@Valid @RequestBody OzelKuralIstegi istek, @RequestParam(required = false) Long id) {
        return onizleme.dene(istek, id);
    }
}
