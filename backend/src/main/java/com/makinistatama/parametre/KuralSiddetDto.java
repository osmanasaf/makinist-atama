package com.makinistatama.parametre;

import com.makinistatama.kural.Siddet;

public record KuralSiddetDto(String kod, String ad, Siddet siddet, String aciklama, String kaynak, String guvenilirlik, Siddet varsayilan, String grup, int sira) {

    static KuralSiddetDto of(KuralSiddet s) {
        return new KuralSiddetDto(s.kod(), AyarAdlari.ad(s.kod()), s.siddet(), s.aciklama(), s.kaynak(), s.guvenilirlik(), s.varsayilan(), s.grup(), s.sira());
    }
}
