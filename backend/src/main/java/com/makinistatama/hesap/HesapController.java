package com.makinistatama.hesap;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.HerkesGiris;
import com.makinistatama.guvenlik.KullaniciKimligi;
import com.makinistatama.guvenlik.Personel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Makinist hesabı (personel: aç / sıfırla / kilit aç) ve herkesin kendi parolasını değiştirmesi. Sınıf yolu {@code /api}; beyanlar metot düzeyinde. */
@RestController
@RequestMapping("/api")
public class HesapController {

    public record ParolaDegistirIstegi(
            @NotBlank(message = "Eski parola boş olamaz.") @Size(max = 200, message = "Parola çok uzun.") String eskiParola,
            @NotBlank(message = "Yeni parola boş olamaz.") @Size(max = 200, message = "Parola çok uzun.") String yeniParola) {
    }

    private final HesapServisi servis;

    HesapController(HesapServisi servis) {
        this.servis = servis;
    }

    @GetMapping("/makinist/{id}/hesap")
    @Personel
    public HesapDto hesap(@PathVariable long id) {
        return servis.hesap(id);
    }

    @PostMapping("/makinist/{id}/hesap")
    @Personel
    @ResponseStatus(HttpStatus.CREATED)
    public GeciciParolaDto hesapAc(@PathVariable long id) {
        return servis.hesapAc(id);
    }

    @PostMapping("/makinist/{id}/hesap/parola-sifirla")
    @Personel
    public GeciciParolaDto parolaSifirla(@PathVariable long id) {
        return servis.parolaSifirla(id);
    }

    @PostMapping("/makinist/{id}/hesap/kilit-ac")
    @Personel
    public HesapDto kilidiAc(@PathVariable long id) {
        return servis.kilidiAc(id);
    }

    @PostMapping("/auth/parola")
    @HerkesGiris
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void parolaDegistir(@AuthenticationPrincipal KullaniciKimligi kimlik, @Valid @RequestBody ParolaDegistirIstegi istek) {
        servis.kendiParolasiniDegistir(kimlik, istek.eskiParola(), istek.yeniParola());
    }
}
