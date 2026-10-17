package com.makinistatama.rapor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;

/** Salt okuma raporları; giriş yapmış herkes görür. */
@RestController
@RequestMapping("/api/rapor")
@Personel
public class RaporController {

    private final BelgeDurumuServisi belgeler;

    RaporController(BelgeDurumuServisi belgeler) {
        this.belgeler = belgeler;
    }

    @GetMapping("/belge-durumu")
    public BelgeDurumuDto belgeDurumu() {
        return belgeler.bugunku();
    }
}
