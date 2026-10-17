package com.makinistatama.atama;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.makinistatama.sefer.SeferDurumu;

/** Bir atamanın takas için salt okunur görünümü (atama + sefer özeti). {@code hesapli}: makinistin aktif hesabı var (takas yalnız hesaplı makinistle yapılır). */
public record GorevGorunumu(long atamaId, long seferId, long makinistId, AtamaRol rol, String ozet, String trenNo, LocalDate tarih, String kalkisGar, String varisGar,
        OffsetDateTime guncelKalkis, SeferDurumu durum, boolean hesapli) {

    public TakasTarafi tarafi() {
        return new TakasTarafi(atamaId, makinistId, seferId, ozet);
    }

    /** Talep anındaki kimlik + özetle aynı mı (değiştiyse talep bayattır). */
    public boolean ayniMi(TakasTarafi t) {
        return atamaId == t.atamaId() && makinistId == t.makinistId() && seferId == t.seferId() && ozet.equals(t.ozet());
    }
}
