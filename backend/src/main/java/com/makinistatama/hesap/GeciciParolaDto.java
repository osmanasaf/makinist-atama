package com.makinistatama.hesap;

/** Hesap açma / parola sıfırlama yanıtı: geçici parola yalnız bu yanıtta, bir kez döner; hiçbir yerde saklanmaz, okuma ucunda yoktur. */
public record GeciciParolaDto(HesapDto hesap, String geciciParola) {
}
