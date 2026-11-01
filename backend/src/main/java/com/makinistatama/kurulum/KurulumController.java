package com.makinistatama.kurulum;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.SadeceYonetici;

/** İlk kurulum sihirbazı (MK-171): yalnız YONETICI (kurum bilgisi, makinist listesi ve profil kararları yönetici işidir). */
@RestController
@RequestMapping("/api/kurulum")
@SadeceYonetici
public class KurulumController {

    private final KurulumDurumServisi servis;

    KurulumController(KurulumDurumServisi servis) {
        this.servis = servis;
    }

    @GetMapping("/durum")
    public KurulumDurumDto durum() {
        return servis.durum();
    }

    @PutMapping("/sihirbaz-gizle")
    public KurulumDurumDto sihirbazGizle(@RequestBody KurulumDurumDto.GizleIstegi istek) {
        return servis.sihirbazGizle(istek.gizli());
    }
}
