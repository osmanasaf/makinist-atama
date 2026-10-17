package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;

/** F2b MK-40/MK-45: son sürüş sorguları ve takas hedef sorgusu YOLCU satırını saymaz; kural/rapor sorgusu ({@code aralik}) görür. */
class AtamaRepositoryYolculukTest extends TestVeritabani {

    @Autowired
    AtamaRepository depo;

    @Test
    void yolcuSatiri_sonSurusuDegistirmez_amaAraliktaGorunur() {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long eski = v.sefer("E0", LocalDate.of(2026, 9, 1), "08:00", "10:00");
        long yolculukSeferi = v.sefer("E1", LocalDate.of(2026, 10, 1), "08:00", "09:30");
        v.atama(eski, m, "ASIL");
        v.atama(yolculukSeferi, m, "YOLCU");

        // pencere öncesi: yalnız eylül sürüşü (hat bazında son varış)
        List<OncekiSurus> once = depo.pencereOncesiSonSurus(List.of(m), LocalDate.of(2026, 10, 12));
        assertThat(once).hasSize(1);
        assertThat(once.getFirst().varis()).isEqualTo(Instant.parse("2026-09-01T07:00:00Z"));
        // gerçekleşen: 10 Eki'ye kadar yalnız eylül sürüşü
        List<OncekiSurus> gerceklesen = depo.gerceklesenSonSurus(Instant.parse("2026-10-10T07:00:00Z"));
        assertThat(gerceklesen).hasSize(1);
        assertThat(gerceklesen.getFirst().varis()).isEqualTo(Instant.parse("2026-09-01T07:00:00Z"));
        // kural/rapor sorgusu yolculuğu görür
        List<AtamaSatiri> satirlar = depo.aralik(List.of(m), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        assertThat(satirlar).hasSize(1);
        assertThat(satirlar.getFirst().yolculuk()).isTrue();
        // takas hedef sorgusu YOLCU'yu listelemez
        assertThat(depo.aralikKisalar(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))).isEmpty();
        assertThat(depo.aralikKisalar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1))).hasSize(1);
    }
}
