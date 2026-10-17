package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** F3a WARNING: kadro eğrisi iş parçacığı sayısı. Ayar 0 = bugünkü formül {@code min(6, çekirdek / 2)} (en az 1); ayar > 0 = sabit sayı. */
class KadroParalelTest {

    @Test
    void ayarSifir_formul_minAltiCekirdekYarisi() {
        assertThat(KadroTahminServisi.paralel(0, 1)).isEqualTo(1);
        assertThat(KadroTahminServisi.paralel(0, 4)).isEqualTo(2);
        assertThat(KadroTahminServisi.paralel(0, 8)).isEqualTo(4);
        assertThat(KadroTahminServisi.paralel(0, 16)).isEqualTo(6);
        assertThat(KadroTahminServisi.paralel(0, 64)).isEqualTo(6);
    }

    @Test
    void ayarPozitif_sabitSayi_cekirdekten_bagimsiz() {
        assertThat(KadroTahminServisi.paralel(4, 4)).isEqualTo(4);
        assertThat(KadroTahminServisi.paralel(12, 8)).isEqualTo(12);
        assertThat(KadroTahminServisi.paralel(1, 64)).isEqualTo(1);
    }

}
