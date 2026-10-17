package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.AtamaRol;
import com.makinistatama.atama.GorevGorunumu;
import com.makinistatama.atama.TakasTarafi;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.sefer.SeferDurumu;

/** Süre ve etkin durum kuralları saf fonksiyonlardır (DB'siz, sabit saat): eşitlik sınırları burada kanıtlanır. */
class TakasKuraliTest {

    private static final Instant KALKIS = Instant.parse("2026-10-13T05:10:00Z"); // 08:10 İstanbul
    private static final Instant ACILIS = Instant.parse("2026-10-12T17:09:00Z"); // 20:09 İstanbul: 12 s 01 dk kala

    @Test
    void acilabilir_12SaatKalaKadar_esitUygun_birDakikaGecAcilmaz() {
        assertThat(TakasServisi.acilabilir(ACILIS, KALKIS, 12)).as("12 s 01 dk kala").isTrue();
        assertThat(TakasServisi.acilabilir(ACILIS.plusSeconds(60), KALKIS, 12)).as("tam 12 s kala (eşit = uygun)").isTrue();
        assertThat(TakasServisi.acilabilir(ACILIS.plusSeconds(120), KALKIS, 12)).as("20:11 → sınır geçildi").isFalse();
    }

    @Test
    void acilabilir_hastalikBirakmasi12SaatSiniriGirmez_amaKalkistenOnceOlmali() {
        assertThat(TakasServisi.acilabilir(KALKIS.minusSeconds(60), KALKIS, 0)).isTrue();
        assertThat(TakasServisi.acilabilir(KALKIS, KALKIS, 0)).as("kalkış anı geçmiş sayılır").isFalse();
        assertThat(TakasServisi.acilabilir(KALKIS.plusSeconds(1), KALKIS, 0)).isFalse();
    }

    @Test
    void sonKabul_karsiOnaySuresiVeKalkisSinirindenErkeni() {
        Instant olusturma = Instant.parse("2026-10-12T08:00:00Z");
        Instant kalkisUzak = olusturma.plusSeconds(100 * 3600);
        assertThat(TakasServisi.sonKabul(olusturma, kalkisUzak, 24, 12)).as("24 saat daha erken").isEqualTo(olusturma.plusSeconds(24 * 3600));
        Instant kalkisYakin = olusturma.plusSeconds(30 * 3600);
        assertThat(TakasServisi.sonKabul(olusturma, kalkisYakin, 24, 12)).as("kalkış − 12 saat daha erken").isEqualTo(olusturma.plusSeconds(18 * 3600));
    }

    private static GorevGorunumu gorunum(long atama, long makinist, long sefer, String ozet) {
        return new GorevGorunumu(atama, sefer, makinist, AtamaRol.ASIL, ozet, "T" + sefer, LocalDate.of(2026, 10, 13), "Ankara", "Eskişehir",
                OffsetDateTime.of(2026, 10, 13, 8, 10, 0, 0, ZoneOffset.ofHours(3)), SeferDurumu.PLANLI, true);
    }

    private static TakasTalebi talep(TakasTuru tur, Instant olusturma, Instant sonKalkis) {
        return new TakasTalebi(tur, new TakasTarafi(1, 11, 101, "ozetA"), tur == TakasTuru.TAKAS ? new TakasTarafi(2, 12, 102, "ozetB") : null, Neden.IZIN, null, 1L, olusturma, sonKalkis);
    }

    @Test
    void etkinDurum_degismeyenAcikTalepAcikKalir_ozetYaDaMakinistDegisinceBayat_atamaSilinirseBayat() {
        TakasTalebi t = talep(TakasTuru.TAKAS, ACILIS, KALKIS);
        Instant simdi = ACILIS.plusSeconds(30); // sonKabul = 20:10 (kalkış − 12 s)
        Map<Long, GorevGorunumu> ayni = Map.of(1L, gorunum(1, 11, 101, "ozetA"), 2L, gorunum(2, 12, 102, "ozetB"));
        assertThat(TakasServisi.etkinDurum(t, simdi, 12, 24, ayni)).isEqualTo(TakasDurumu.KARSI_ONAY_BEKLIYOR);

        assertThat(TakasServisi.etkinDurum(t, simdi, 12, 24, Map.of(1L, gorunum(1, 11, 101, "ozetA"), 2L, gorunum(2, 12, 102, "gecikmeli ozet")))).as("sefer özeti değişti")
                .isEqualTo(TakasDurumu.BAYAT);
        assertThat(TakasServisi.etkinDurum(t, simdi, 12, 24, Map.of(1L, gorunum(1, 99, 101, "ozetA"), 2L, gorunum(2, 12, 102, "ozetB")))).as("atamanın makinisti değişti")
                .isEqualTo(TakasDurumu.BAYAT);
        assertThat(TakasServisi.etkinDurum(t, simdi, 12, 24, Map.of(2L, gorunum(2, 12, 102, "ozetB")))).as("kaynak atama silindi").isEqualTo(TakasDurumu.BAYAT);
    }

    @Test
    void etkinDurum_karsiOnaySuresi_esitUygunSonrasiSuresiDoldu_kalkisGecinceDolar() {
        Instant olusturma = Instant.parse("2026-10-12T08:00:00Z");
        Instant kalkis = olusturma.plusSeconds(100 * 3600);
        TakasTalebi t = talep(TakasTuru.TAKAS, olusturma, kalkis);
        Map<Long, GorevGorunumu> ayni = Map.of(1L, gorunum(1, 11, 101, "ozetA"), 2L, gorunum(2, 12, 102, "ozetB"));
        Instant son = olusturma.plusSeconds(24 * 3600);
        assertThat(TakasServisi.etkinDurum(t, son, 12, 24, ayni)).as("tam 24. saat").isEqualTo(TakasDurumu.KARSI_ONAY_BEKLIYOR);
        assertThat(TakasServisi.etkinDurum(t, son.plusSeconds(1), 12, 24, ayni)).isEqualTo(TakasDurumu.SURESI_DOLDU);

        TakasTalebi birak = talep(TakasTuru.BIRAK, olusturma, kalkis); // karşı onay yok: yalnız kalkış
        Map<Long, GorevGorunumu> yalniz = Map.of(1L, gorunum(1, 11, 101, "ozetA"));
        assertThat(TakasServisi.etkinDurum(birak, kalkis.minusSeconds(1), 12, 24, yalniz)).isEqualTo(TakasDurumu.ATAMACI_ONAYI_BEKLIYOR);
        assertThat(TakasServisi.etkinDurum(birak, kalkis, 12, 24, yalniz)).as("kalkıştan sonra atamacı onayı yok").isEqualTo(TakasDurumu.SURESI_DOLDU);
    }
}
