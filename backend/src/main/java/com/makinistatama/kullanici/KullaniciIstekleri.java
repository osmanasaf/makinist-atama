package com.makinistatama.kullanici;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** İstek gövdeleri (sözleşme: KullaniciOlusturIstegi, KullaniciGuncelleIstegi, ParolaIstegi). */
public final class KullaniciIstekleri {

    private KullaniciIstekleri() {
    }

    /** {@code eposta} baştaki/sondaki boşluklardan arındırılarak doğrulanır (servis de aynısını yapar). */
    public record Olustur(
            @NotBlank(message = "E-posta boş olamaz.") @Size(max = 254, message = "E-posta en fazla 254 karakter olabilir.")
            @Email(message = "E-posta biçimi geçersiz.") String eposta,
            @NotBlank(message = "Ad soyad boş olamaz.") @Size(max = 120, message = "Ad soyad en fazla 120 karakter olabilir.") String adSoyad,
            @NotNull(message = "Rol seçilmeli.") Rol rol,
            @NotBlank(message = "Parola boş olamaz.") @Size(min = 10, max = 72, message = "Parola 10 ile 72 karakter arasında olmalı.") String parola) {

        public Olustur {
            eposta = eposta == null ? null : eposta.strip();
        }
    }

    public record Guncelle(
            @NotBlank(message = "Ad soyad boş olamaz.") @Size(max = 120, message = "Ad soyad en fazla 120 karakter olabilir.") String adSoyad,
            @NotNull(message = "Rol seçilmeli.") Rol rol,
            @NotNull(message = "Aktiflik belirtilmeli.") Boolean aktif) {
    }

    public record Parola(
            @NotBlank(message = "Parola boş olamaz.") @Size(min = 10, max = 72, message = "Parola 10 ile 72 karakter arasında olmalı.") String yeniParola) {
    }
}
