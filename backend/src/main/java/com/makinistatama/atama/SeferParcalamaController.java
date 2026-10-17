package com.makinistatama.atama;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.sefer.SeferBolIstegi;

import jakarta.validation.Valid;

/** Seferi ekrandan bölme ve geri alma (birleştirme) (MK-121/126/128): ATAMACI ve YONETICI. {@code onizle=true} hiçbir şey bırakmaz, yanıt gerçek koşuyla aynıdır. */
@RestController
@RequestMapping("/api/atama/sefer")
@Personel
public class SeferParcalamaController {

    private final SeferParcalamaServisi servis;

    SeferParcalamaController(SeferParcalamaServisi servis) {
        this.servis = servis;
    }

    @PostMapping("/{id}/bol")
    public BolSonucuDto bol(@PathVariable long id, @Valid @RequestBody SeferBolIstegi istek) {
        return servis.bol(id, istek);
    }

    /** Zinciri parça 1 satırına geri döndürür (MK-126/128): yoldaki {id} zincirin herhangi parçası olabilir. Yanıt `{sefer, silinenSeferIdler, yeniKirmizi}`. */
    @PostMapping("/{id}/birlestir")
    public BirlestirSonucuDto birlestir(@PathVariable long id, @RequestBody SeferBirlestirIstegi istek) {
        return servis.birlestir(id, istek);
    }
}
