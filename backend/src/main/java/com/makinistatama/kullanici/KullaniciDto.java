package com.makinistatama.kullanici;

/** Yanıtlarda parola karması ve kilit alanları asla bulunmaz. {@code makinistId}: makinist hesabında bağlı kart; {@code parolaDegistirmeli}: geçici parola bayrağı. */
public record KullaniciDto(Long id, String eposta, String adSoyad, Rol rol, boolean aktif, Long makinistId, boolean parolaDegistirmeli) {

    static KullaniciDto of(Kullanici k) {
        return new KullaniciDto(k.id(), k.eposta(), k.adSoyad(), k.rol(), k.aktif(), k.makinistId(), k.parolaDegistirmeli());
    }
}
