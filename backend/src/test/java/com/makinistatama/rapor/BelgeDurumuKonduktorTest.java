package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.rapor.BelgeDurumuDto.Belge;
import com.makinistatama.rapor.BelgeDurumuDto.Durum;
import com.makinistatama.rapor.BelgeDurumuDto.MakinistBelgeleri;
import com.makinistatama.rapor.BelgeDurumuDto.Tur;

/** F3b MK-84 (FR-07): belge panosunda kondüktör 3 satır (kişisel emniyet belgesi, sağlık, psikoteknik); bröve/sürüş satırı ve GIRILMEMIS yok; makinist aynen. "Bugün" 2026-10-10. */
class BelgeDurumuKonduktorTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.of(2026, 10, 10);

    @Autowired
    BelgeDurumuServisi servis;

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private MakinistBelgeleri satir(long id) {
        return servis.rapor(BUGUN).makinistler().stream().filter(m -> m.makinistId() == id).findFirst().orElseThrow();
    }

    @Test
    void konduktor_ucSatir_broveVeSurusSatiriYok_enKotuGirilmemisOlmaz() {
        long k = v.konduktor("K001");
        long m = v.makinist("D001");

        MakinistBelgeleri kondukturSatiri = satir(k);
        assertThat(kondukturSatiri.tur()).isEqualTo(PersonelTuru.KONDUKTOR);
        assertThat(kondukturSatiri.belgeler()).extracting(Belge::tur).containsExactlyInAnyOrder(Tur.EHLIYET, Tur.SAGLIK, Tur.PSIKOTEKNIK);
        assertThat(kondukturSatiri.belgeler()).filteredOn(b -> b.tur() == Tur.EHLIYET).extracting(Belge::ad).containsExactly("Kişisel emniyet belgesi");
        assertThat(kondukturSatiri.belgeler()).extracting(Belge::durum).doesNotContain(Durum.GIRILMEMIS);
        assertThat(kondukturSatiri.enKotu()).isEqualTo(Durum.GECERLI);

        MakinistBelgeleri makinistSatiri = satir(m);
        assertThat(makinistSatiri.tur()).isEqualTo(PersonelTuru.MAKINIST);
        assertThat(makinistSatiri.belgeler()).extracting(Belge::tur).contains(Tur.ARAC_TIPI, Tur.HAT_SINAV, Tur.HAT_SURUS, Tur.GENEL_SURUS);
        assertThat(makinistSatiri.belgeler()).filteredOn(b -> b.tur() == Tur.EHLIYET).extracting(Belge::ad).containsExactly("Ehliyet");
    }

    @Test
    void konduktorBelgesiDolunca_enKotuDoldu() {
        long k = v.konduktor("K001");
        jdbc.update("UPDATE makinist SET ehliyet_bitis = '2026-10-09' WHERE id = ?", k);
        MakinistBelgeleri s = satir(k);
        assertThat(s.enKotu()).isEqualTo(Durum.DOLDU);
        assertThat(s.belgeler().get(0).tur()).isEqualTo(Tur.EHLIYET);
    }
}
