package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Vardiya;

import tools.jackson.databind.json.JsonMapper;

/** F3b MK-85 / WARNING-2: Kapsam iki kurucu taşır (6 ve 7 bileşen); Jackson 3 kanonik kurucuyu seçer, alansız eski JSON boş küme okur. */
class OzelKuralJsonUyumTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void eskiJsonAlansiz_personelTurleriBos() {
        Kapsam eski = json.readValue("{\"hatlar\":[1],\"aracTipleri\":[],\"kalkisGarlari\":[],\"varisGarlari\":[],\"trenTurleri\":[],\"vardiya\":\"GECE\"}", Kapsam.class);
        assertThat(eski.hatlar()).containsExactly(1L);
        assertThat(eski.vardiya()).isEqualTo(Vardiya.GECE);
        assertThat(eski.personelTurleri()).isEmpty();
        assertThat(json.readValue("{}", Kapsam.class)).isEqualTo(Kapsam.TUMU);
    }

    @Test
    void personelTurleri_gidisDonus_sirali() {
        Kapsam k = new Kapsam(null, null, null, null, null, null, Set.of(MakinistBilgisi.Tur.KONDUKTOR, MakinistBilgisi.Tur.MAKINIST));
        String s = json.writeValueAsString(k);
        assertThat(s).contains("\"personelTurleri\":[\"MAKINIST\",\"KONDUKTOR\"]");
        assertThat(json.readValue(s, Kapsam.class)).isEqualTo(k);
        assertThat(json.readValue("{\"personelTurleri\":[\"KONDUKTOR\"]}", Kapsam.class).personelTurleri()).containsExactly(MakinistBilgisi.Tur.KONDUKTOR);
        assertThat(json.readValue("{\"personelTurleri\":null}", Kapsam.class).personelTurleri()).isEmpty();
    }

    @Test
    void gecersizTur_okumadaHata() {
        assertThatThrownBy(() -> json.readValue("{\"personelTurleri\":[\"MANEVRACI\"]}", Kapsam.class)).hasMessageContaining("MANEVRACI");
    }
}
