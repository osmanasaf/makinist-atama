package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.makinistatama.sefer.SeferDurumu;

/** F2b MK-45: görev özetinde YOLCU "Yolcu" yazar (sessizce "Yardımcı" değil); rol değişimi özeti değiştirir (DEĞİŞTİ tespiti). */
class GorevOzetiYolculukTest {

    static final OffsetDateTime KALKIS = OffsetDateTime.parse("2026-10-12T08:00:00+03:00");
    static final OffsetDateTime VARIS = OffsetDateTime.parse("2026-10-12T09:30:00+03:00");

    @Test
    void yolcuOzeti_yolcuYazar_trenNoVeKmYok() {
        String ozet = GorevOzeti.hesapla(AtamaRol.YOLCU, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI);

        assertThat(ozet).isEqualTo("Yolcu · Ankara 12.10 08:00 → Eskişehir 12.10 09:30");
        assertThat(GorevOzeti.hesapla(AtamaRol.YARDIMCI, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.PLANLI)).isNotEqualTo(ozet);
        assertThat(GorevOzeti.hesapla(AtamaRol.YOLCU, "Ankara", "Eskişehir", KALKIS, VARIS, SeferDurumu.IPTAL)).endsWith(" · İPTAL");
    }
}
