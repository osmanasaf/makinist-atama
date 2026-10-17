package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.GunListesiServisi.GordumBaglami;
import com.makinistatama.atama.GunListesiDto.GorduDurumu;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;

/** FR-M10 sınırları (DB'siz): uyarı eşiği "AZ kala" (eşit değil), kalkışı geçmiş/iptal bilgi yok, hesapsız ayrı. */
class GordumBaglamiTest {

    static final Instant SIMDI = Instant.parse("2026-10-12T05:00:00Z");

    static SeferDto sefer(Instant kalkis, SeferDurumu durum) {
        OffsetDateTime k = kalkis.atOffset(ZoneOffset.ofHours(3));
        return new SeferDto(1L, "T1", k.toLocalDate(), 1L, "Ankara", 2L, "Eskişehir", k, k.plusHours(2), 0, 0, k, k.plusHours(2), 1L, "YHT", 1L, "Hat", null, durum, 0L, null, null, null, null);
    }

    static Atama atama(long makinistId) {
        return new Atama(1L, makinistId, AtamaRol.ASIL, "t", SIMDI, null, 0);
    }

    static GordumBaglami baglam(Set<Long> hesaplilar, Map<Long, GorevGordum> onaylar) {
        return new GordumBaglami(SIMDI, 12, hesaplilar, onaylar);
    }

    @Test
    void uyariEsigi_tamOnIkiSaatVurgulu_degil_birDakikaAzVurgulu() {
        var b = baglam(Set.of(7L), new HashMap<>());
        var tam = b.bilgi(atama(7), sefer(SIMDI.plus(Duration.ofHours(12)), SeferDurumu.PLANLI));
        var az = b.bilgi(atama(7), sefer(SIMDI.plus(Duration.ofHours(12)).minusSeconds(60), SeferDurumu.PLANLI));
        assertThat(tam.durum()).isEqualTo(GorduDurumu.GORMEDI);
        assertThat(tam.uyari()).isFalse();
        assertThat(az.uyari()).isTrue();
    }

    @Test
    void hesapsiz_gecmisVeIptal_ayriSiniflanir() {
        var b = baglam(Set.of(7L), new HashMap<>());
        assertThat(b.bilgi(atama(8), sefer(SIMDI.plus(Duration.ofHours(30)), SeferDurumu.PLANLI)).durum()).isEqualTo(GorduDurumu.HESAP_YOK);
        assertThat(b.bilgi(atama(7), sefer(SIMDI.minusSeconds(1), SeferDurumu.PLANLI))).as("kalkışı geçmiş").isNull();
        assertThat(b.bilgi(atama(7), sefer(SIMDI, SeferDurumu.PLANLI))).as("kalkış şimdi = geçmiş").isNull();
        // OK-9 (QA BUG-4): iptal seferde de bilgi VAR (önceki beklenti "iptal = null" ürün kararıyla tersine döndü): iptal bildirimi de tebliğ edilmeli; kalkışı geçmiş iptal yine null
        assertThat(b.bilgi(atama(7), sefer(SIMDI.plus(Duration.ofHours(30)), SeferDurumu.IPTAL)).durum()).as("iptal, hesaplı").isEqualTo(GorduDurumu.GORMEDI);
        assertThat(b.bilgi(atama(8), sefer(SIMDI.plus(Duration.ofHours(30)), SeferDurumu.IPTAL)).durum()).as("iptal, hesapsız").isEqualTo(GorduDurumu.HESAP_YOK);
        assertThat(b.bilgi(atama(7), sefer(SIMDI.minusSeconds(1), SeferDurumu.IPTAL))).as("kalkışı geçmiş iptal").isNull();
    }
}
