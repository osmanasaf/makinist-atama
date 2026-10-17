package com.makinistatama.guvenlik;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Geçerli kullanıcıya tek erişim noktası (geçmiş kaydı kullanır). Oturum yoksa boş döner (sistem işlemi). */
public final class OturumKullanicisi {

    public record Kisi(long id, String adSoyad) {
    }

    private OturumKullanicisi() {
    }

    public static Optional<Kisi> simdiki() {
        Authentication kimlik = SecurityContextHolder.getContext().getAuthentication();
        if (kimlik != null && kimlik.getPrincipal() instanceof KullaniciKimligi k) {
            return Optional.of(new Kisi(k.id(), k.adSoyad()));
        }
        return Optional.empty();
    }
}
