package com.makinistatama.guvenlik;

import java.time.Instant;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.makinistatama.kullanici.Kullanici;

/** Servis düzeyi testlerde "bu kullanıcı oturum açmış gibi" çalışmak için (HTTP olmadan). Testten sonra {@link #kapat()} çağrılmalı. */
public final class TestOturumu {

    private TestOturumu() {
    }

    public static void ac(Kullanici kullanici) {
        KullaniciKimligi kimlik = new KullaniciKimligi(kullanici, Instant.now());
        SecurityContext baglam = SecurityContextHolder.createEmptyContext();
        baglam.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(kimlik, null, kimlik.getAuthorities()));
        SecurityContextHolder.setContext(baglam);
    }

    public static void kapat() {
        SecurityContextHolder.clearContext();
    }
}
