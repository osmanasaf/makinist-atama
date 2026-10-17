package com.makinistatama.takas;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;

import jakarta.validation.Valid;

/**
 * Atamacı uçları (ATAMACI + YONETICI): bekleyen talepler, kural ihlali ayrıntısı (YALNIZ burada), onay ve ret. Makinist uçları {@code /api/benim/takas*} altındadır (kimlik oturumdan).
 */
@RestController
@RequestMapping("/api/takas")
@Personel
public class TakasController {

    private final TakasServisi servis;

    TakasController(TakasServisi servis) {
        this.servis = servis;
    }

    /** Açık ve son 7 günde kapanan talepler; {@code durum} etkin duruma göre süzer. {@code bekleyen}: atamacı onayı bekleyen sayısı (rozet). */
    @GetMapping
    public TakasListeDto liste(@RequestParam(required = false) TakasDurumu durum) {
        return servis.liste(durum);
    }

    @GetMapping("/{id}")
    public TakasDetayDto detay(@PathVariable long id) {
        return servis.detay(id);
    }

    /** Karşılıklı takasta iki yönlü kural kontrolüyle iki atamayı değiştirir; bırakmada {@code yeniMakinistId} ile yerine atar ya da boşsa kaldırır. */
    @PostMapping("/{id}/onay")
    public TakasSatiriDto onay(@PathVariable long id, @Valid @RequestBody(required = false) TakasOnayIstegi istek) {
        return servis.onay(id, istek == null ? new TakasOnayIstegi(null, null) : istek);
    }

    @PostMapping("/{id}/red")
    public TakasSatiriDto red(@PathVariable long id) {
        return servis.atamaciRed(id);
    }
}
