package com.makinistatama.guvenlik;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.kullanici.KullaniciDto;
import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.ortak.UygulamaHatasi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record GirisIstegi(
            @NotBlank(message = "E-posta boş olamaz.") @Size(max = 254, message = "E-posta çok uzun.") String eposta,
            @NotBlank(message = "Parola boş olamaz.") @Size(max = 200, message = "Parola çok uzun.") String parola) {
    }

    private final AuthenticationManager kimlikYoneticisi;
    private final SecurityContextRepository oturumBaglami;
    private final KullaniciServisi kullanicilar;

    AuthController(AuthenticationManager kimlikYoneticisi, SecurityContextRepository oturumBaglami, KullaniciServisi kullanicilar) {
        this.kimlikYoneticisi = kimlikYoneticisi;
        this.oturumBaglami = oturumBaglami;
        this.kullanicilar = kullanicilar;
    }

    @PostMapping("/giris")
    @Acik
    public KullaniciDto giris(@Valid @RequestBody GirisIstegi istek, HttpServletRequest http, HttpServletResponse yanit) {
        Authentication kimlik;
        try {
            kimlik = kimlikYoneticisi.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(istek.eposta().trim(), istek.parola()));
        } catch (LockedException e) {
            throw new UygulamaHatasi(HttpStatus.TOO_MANY_REQUESTS, "COK_FAZLA_DENEME",
                    "Çok fazla hatalı deneme yapıldı. Hesap 15 dakika kilitlendi.", null);
        } catch (BadCredentialsException e) {
            kullanicilar.hataliGiris(istek.eposta()); // var olmayan e-posta sayılmaz
            throw girisHatali();
        } catch (AuthenticationException e) { // pasif hesap vb.: ayrım sızdırılmaz
            throw girisHatali();
        }
        KullaniciKimligi k = (KullaniciKimligi) kimlik.getPrincipal();

        SecurityContext baglam = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        baglam.setAuthentication(kimlik);
        if (http.getSession(false) != null) {
            http.changeSessionId(); // oturum sabitleme (session fixation) koruması
        }
        oturumBaglami.saveContext(baglam, http, yanit);
        kullanicilar.girisBasarili(k.id());
        return new KullaniciDto(k.id(), k.getUsername(), k.adSoyad(), k.rol(), true, k.makinistId(), k.parolaDegistirmeli());
    }

    @PostMapping("/cikis")
    @HerkesGiris
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cikis(HttpServletRequest http) {
        SecurityContextHolder.clearContext();
        HttpSession oturum = http.getSession(false);
        if (oturum != null) {
            oturum.invalidate();
        }
    }

    @GetMapping("/ben")
    @HerkesGiris
    public KullaniciDto ben(@AuthenticationPrincipal KullaniciKimligi k) {
        return new KullaniciDto(k.id(), k.getUsername(), k.adSoyad(), k.rol(), true, k.makinistId(), k.parolaDegistirmeli());
    }

    private static UygulamaHatasi girisHatali() {
        return new UygulamaHatasi(HttpStatus.UNAUTHORIZED, "GIRIS_HATALI", "E-posta veya parola hatalı.", null);
    }
}
