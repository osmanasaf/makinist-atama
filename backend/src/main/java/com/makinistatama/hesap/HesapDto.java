package com.makinistatama.hesap;

/** Makinist kartındaki hesap durumu. Parola/karma/geçici parola bu okuma modelinde ASLA yoktur. {@code var=false}: hesap açılmamış (öteki alanlar boş/false). */
public record HesapDto(boolean var, String girisAdi, boolean aktif, boolean parolaDegistirmeli, boolean kilitli) {

    static HesapDto yok() {
        return new HesapDto(false, null, false, false, false);
    }
}
