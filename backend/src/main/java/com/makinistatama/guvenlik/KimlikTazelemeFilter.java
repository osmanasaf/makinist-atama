package com.makinistatama.guvenlik;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.makinistatama.kullanici.Kullanici;
import com.makinistatama.kullanici.KullaniciServisi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Oturum açık kalsa bile her istekte kullanıcıyı veritabanından tazeler: pasifleştirilen kullanıcının oturumu
 * hemen düşer, parolası sıfırlanan kullanıcının açık oturumları düşer, rol/ad değişikliği bir sonraki istekte geçerli olur (oturumun iptali kolay olmalı).
 * İstek başına tek birincil anahtar sorgusu; 2-5 kullanıcı için ihmal edilebilir.
 */
final class KimlikTazelemeFilter extends OncePerRequestFilter {

    private final KullaniciServisi kullanicilar;
    private final Clock saat;

    KimlikTazelemeFilter(KullaniciServisi kullanicilar, Clock saat) {
        this.kullanicilar = kullanicilar;
        this.saat = saat;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest istek, HttpServletResponse yanit, FilterChain zincir)
            throws ServletException, IOException {
        SecurityContext baglam = SecurityContextHolder.getContext();
        Authentication kimlik = baglam.getAuthentication();
        if (kimlik != null && kimlik.getPrincipal() instanceof KullaniciKimligi eski) {
            Optional<Kullanici> guncel = kullanicilar.idIle(eski.id()).filter(Kullanici::aktif);
            KullaniciKimligi yeni = guncel.map(k -> new KullaniciKimligi(k, saat.instant())).orElse(null);
            // parola sıfırlandıysa (iz değişti) açık oturumlar da düşer: ele geçirilmiş parolayla açılmış oturum 8 saat yaşamaz
            if (yeni != null && yeni.parolaIzi().equals(eski.parolaIzi())) {
                yeni.eraseCredentials(); // karma oturum nesnesinde tutulmaz
                baglam.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(yeni, null, yeni.getAuthorities()));
            } else {
                SecurityContextHolder.clearContext();
                HttpSession oturum = istek.getSession(false);
                if (oturum != null) {
                    oturum.invalidate();
                }
            }
        }
        zincir.doFilter(istek, yanit);
    }
}
