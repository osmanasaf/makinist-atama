package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.makinist.PersonelTuru;

/** MK-82: kural paketi {@code makinist.PersonelTuru}'nu göremez (paket yönü); saf ikizi {@code MakinistBilgisi.Tur} ile adları eşit kalmalı ({@code TrenTuruSabitleriTest} emsali). */
class PersonelTuruSabitleriTest {

    @Test
    void iki_enum_ayniAdlariTasir() {
        List<String> a = Arrays.stream(PersonelTuru.values()).map(Enum::name).toList();
        List<String> b = Arrays.stream(MakinistBilgisi.Tur.values()).map(Enum::name).toList();
        assertThat(b).isEqualTo(a);
    }

    @Test
    void sabitKumeler() {
        assertThat(PersonelTuru.MAKINISTLER).containsExactly(PersonelTuru.MAKINIST);
        assertThat(PersonelTuru.KONDUKTORLER).containsExactly(PersonelTuru.KONDUKTOR);
        assertThat(PersonelTuru.TUMU).containsExactlyInAnyOrder(PersonelTuru.values());
    }
}
