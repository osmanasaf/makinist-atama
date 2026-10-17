package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.makinistatama.sefer.SeferDurumu;

/** MK-32: görev özeti tek fonksiyon; DEĞİŞTİ tespiti yalnız gar, güncel saatler, rol ve durumla değişir (tren no / km özette yok [V8]). */
class GorevOzetiTest {

    static final OffsetDateTime KALKIS = OffsetDateTime.parse("2026-10-12T08:10:00+03:00");
    static final OffsetDateTime VARIS = OffsetDateTime.parse("2026-10-12T10:00:00+03:00");

    static String ozet(AtamaRol rol, String kalkisGar, String varisGar, OffsetDateTime kalkis, OffsetDateTime varis, SeferDurumu durum) {
        return GorevOzeti.hesapla(rol, kalkisGar, varisGar, kalkis, varis, durum);
    }

    @Test
    void okunurMetin_istanbulSaatiyle() {
        assertThat(ozet(AtamaRol.ASIL, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI)).isEqualTo("Asıl · Ankara 12.10 08:10 → Eskişehir 12.10 10:00");
        // aynı an, başka ofset: yine İstanbul saati
        assertThat(ozet(AtamaRol.YARDIMCI, "Ankara", "Eskişehir", KALKIS.withOffsetSameInstant(java.time.ZoneOffset.UTC), VARIS, SeferDurumu.IPTAL))
                .isEqualTo("Yardımcı · Ankara 12.10 08:10 → Eskişehir 12.10 10:00 · İPTAL");
    }

    @Test
    void gecikmeDegisir_ozetDegisir() {
        String once = ozet(AtamaRol.ASIL, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI);
        assertThat(ozet(AtamaRol.ASIL, "Ankara", "Eskişehir", KALKIS.plusMinutes(25), VARIS.plusMinutes(25), SeferDurumu.PLANLI)).isNotEqualTo(once);
        assertThat(ozet(AtamaRol.ASIL, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.IPTAL)).isNotEqualTo(once);
        assertThat(ozet(AtamaRol.YARDIMCI, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI)).isNotEqualTo(once);
        assertThat(ozet(AtamaRol.ASIL, "Ankara", "Sivas", KALKIS, VARIS, SeferDurumu.PLANLI)).isNotEqualTo(once);
    }

    @Test
    void ayniGirdi_ayniOzet_trenNoVeKmParametreDegil() {
        // işlev imzasında tren no / km yok: o alanların değişimi özeti değiştiremez (GordumTest, uçtan uca)
        assertThat(ozet(AtamaRol.ASIL, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI))
                .isEqualTo(ozet(AtamaRol.ASIL, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI));
    }
}
