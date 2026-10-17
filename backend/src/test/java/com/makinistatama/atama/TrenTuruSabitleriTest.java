package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Kapsam;
import com.makinistatama.sefer.TrenTuru;

/** MK-23: kural paketi sefer enum'unu göremez, tren türü orada String'dir; iki kümenin eşitliği bu testle korunur (atama test paketi ikisini de görür). */
class TrenTuruSabitleriTest {

    @Test
    void kapsamTrenTurleri_seferEnumDegerleriyleAyni() {
        assertThat(Kapsam.TREN_TURLERI).isEqualTo(Arrays.stream(TrenTuru.values()).map(Enum::name).collect(Collectors.toSet()));
    }
}
