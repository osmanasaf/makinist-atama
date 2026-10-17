package com.makinistatama.tanim;

public record TanimDto(Long id, TanimTipi tip, String ad) {

    static TanimDto of(Tanim t) {
        return new TanimDto(t.id(), t.tip(), t.ad());
    }
}
