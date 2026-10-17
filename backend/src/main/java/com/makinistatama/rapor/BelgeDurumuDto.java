package com.makinistatama.rapor;

import java.time.LocalDate;
import java.util.List;

import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.PersonelTuru;

/** Belge panosu (FR-09). {@code bugun} hesabın dayandığı gündür; {@code uyariGun}: bitişe bu kadar gün kalınca YAKLASIYOR. */
public record BelgeDurumuDto(LocalDate bugun, int uyariGun, List<MakinistBelgeleri> makinistler) {

    public enum Durum {
        DOLDU, GIRILMEMIS, YAKLASIYOR, GECERLI
    }

    public enum Tur {
        EHLIYET, SAGLIK, PSIKOTEKNIK, ARAC_TIPI, HAT_SINAV, HAT_SURUS, GENEL_SURUS
    }

    /** {@code kalanGun}: bitişe kalan gün; negatifse bitişten geçen gün (bitiş günü dahil geçerlidir: kalan 0 = bugün son gün). */
    public record Belge(Tur tur, String ad, LocalDate bitis, Long kalanGun, Durum durum) {
    }

    public record MakinistBelgeleri(Long makinistId, String sicil, String adSoyad, MakinistDurum durum, PersonelTuru tur, Durum enKotu, List<Belge> belgeler) {
    }
}
