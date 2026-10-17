package com.makinistatama.hesap;

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

import com.makinistatama.guvenlik.SadeceYonetici;
import com.makinistatama.kullanici.KullaniciDto;
import com.makinistatama.kullanici.KullaniciIstekleri;

import jakarta.validation.Valid;

/** Sınıf düzeyinde {@code @SadeceYonetici}: yeni uç nokta varsayılan olarak yönetici-only olur. Mutasyonlar {@link HesapServisi} üzerinden denetim kaydı yazar (MK-27). */
@RestController
@RequestMapping("/api/kullanicilar")
@SadeceYonetici
public class KullaniciController {

    private final HesapServisi servis;

    KullaniciController(HesapServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<KullaniciDto> liste() {
        return servis.liste();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KullaniciDto olustur(@Valid @RequestBody KullaniciIstekleri.Olustur istek) {
        return servis.olustur(istek);
    }

    @PutMapping("/{id}")
    public KullaniciDto guncelle(@PathVariable long id, @Valid @RequestBody KullaniciIstekleri.Guncelle istek) {
        return servis.guncelle(id, istek);
    }

    @PostMapping("/{id}/parola")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void parolaSifirla(@PathVariable long id, @Valid @RequestBody KullaniciIstekleri.Parola istek) {
        servis.parolaSifirla(id, istek.yeniParola());
    }
}
