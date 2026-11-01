package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

/** F4 / AK-4.4 / MK-180: yönetim özetindeki TEK aritmetik yeri (saf, DB'siz). Popülasyon standart sapması (n'e bölünür); n=0 → null (sıfıra bölme yok). */
class OzetHesabiTest {

    @Test
    void onYirmiOtuz_ortalama20_standartSapmaKokIkiYuzBolUc() {
        OzetHesabi.Dagilim d = OzetHesabi.dagilim(List.of(10.0, 20.0, 30.0));
        assertThat(d.n()).isEqualTo(3);
        assertThat(d.ortalama()).isEqualTo(20.0);
        assertThat(d.standartSapma()).isCloseTo(8.165, within(0.001)); // √(200/3)
        assertThat(d.min()).isEqualTo(10.0);
        assertThat(d.max()).isEqualTo(30.0);
    }

    @Test
    void tekKisi_standartSapmaSifir() {
        OzetHesabi.Dagilim d = OzetHesabi.dagilim(List.of(42.5));
        assertThat(d.n()).isEqualTo(1);
        assertThat(d.standartSapma()).isZero();
        assertThat(d.min()).isEqualTo(42.5);
        assertThat(d.max()).isEqualTo(42.5);
    }

    @Test
    void bos_null() {
        assertThat(OzetHesabi.dagilim(List.of())).isNull();
    }

    @Test
    void yuzde_birOndalik_paydaSifirsaNull() {
        assertThat(OzetHesabi.yuzde(6, 9)).isEqualTo(66.7);
        assertThat(OzetHesabi.yuzde(0, 9)).isEqualTo(0.0);
        assertThat(OzetHesabi.yuzde(0, 0)).isNull();
    }

    @Test
    void toplam_ikiOndalik() {
        assertThat(OzetHesabi.toplam(List.of(1.005, 2.0, 0.333))).isEqualTo(3.34); // 3.338 → 3.34
        assertThat(OzetHesabi.toplam(List.of())).isZero();
    }
}
