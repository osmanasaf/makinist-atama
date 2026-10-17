package com.makinistatama.rapor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.rapor.BelgeDurumuDto.Belge;
import com.makinistatama.rapor.BelgeDurumuDto.Durum;
import com.makinistatama.rapor.BelgeDurumuDto.Tur;

/** T9.2: belge panosu. "Bugün" sabit 2026-10-10. */
class BelgeDurumuTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.of(2026, 10, 10);

    @Autowired
    BelgeDurumuServisi servis;

    @Autowired
    com.makinistatama.atama.AtamaServisi atamaServisi;

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private Belge ehliyet(long makinistId, String bitis) {
        jdbc.update("UPDATE makinist SET ehliyet_bitis = ?::date WHERE id = ?", bitis, makinistId);
        return servis.rapor(BUGUN).makinistler().stream().filter(m -> m.makinistId() == makinistId).findFirst().orElseThrow().belgeler().stream()
                .filter(b -> b.tur() == Tur.EHLIYET).findFirst().orElseThrow();
    }

    @Test
    void sinirGunleri_bitis25Eki15GunYaklasiyor_9EkiDoldu_9Kas30Yaklasiyor_10Kas31Gecerli() {
        long m = v.makinist("D001");

        assertThat(ehliyet(m, "2026-10-25")).satisfies(b -> {
            assertThat(b.kalanGun()).isEqualTo(15);
            assertThat(b.durum()).isEqualTo(Durum.YAKLASIYOR);
        });
        assertThat(ehliyet(m, "2026-10-09")).satisfies(b -> {
            assertThat(b.kalanGun()).isEqualTo(-1);
            assertThat(b.durum()).isEqualTo(Durum.DOLDU);
        });
        assertThat(ehliyet(m, "2026-11-09")).satisfies(b -> {
            assertThat(b.kalanGun()).isEqualTo(30);
            assertThat(b.durum()).isEqualTo(Durum.YAKLASIYOR);
        });
        assertThat(ehliyet(m, "2026-11-10")).satisfies(b -> {
            assertThat(b.kalanGun()).isEqualTo(31);
            assertThat(b.durum()).isEqualTo(Durum.GECERLI);
        });
        assertThat(ehliyet(m, "2026-10-10")).satisfies(b -> { // bitiş günü dahil geçerli
            assertThat(b.kalanGun()).isZero();
            assertThat(b.durum()).isEqualTo(Durum.YAKLASIYOR);
        });
    }

    @Test
    void uyariGunuParametreden_enKotuVeSiralama() {
        long iyi = v.makinist("D001");
        long kotu = v.makinist("D002");
        jdbc.update("UPDATE makinist SET saglik_bitis = '2026-10-01' WHERE id = ?", kotu);
        jdbc.update("UPDATE kural_parametre SET deger = '10' WHERE anahtar = 'belge.uyari_gun'");
        jdbc.update("UPDATE makinist SET psikoteknik_bitis = '2026-10-25' WHERE id = ?", iyi); // 15 gün > 10: yaklaşan sayılmaz

        BelgeDurumuDto r = servis.rapor(BUGUN);

        assertThat(r.uyariGun()).isEqualTo(10);
        assertThat(r.makinistler()).extracting(BelgeDurumuDto.MakinistBelgeleri::sicil).containsExactly("D002", "D001");
        assertThat(r.makinistler().get(0).enKotu()).isEqualTo(Durum.DOLDU);
        assertThat(r.makinistler().get(0).belgeler().get(0).tur()).isEqualTo(Tur.SAGLIK);
        assertThat(r.makinistler().get(1).enKotu()).isEqualTo(Durum.GECERLI);
    }

    @Test
    void sonSurus_kartDegeriylaAtamadanTuretilenin_buyugu_gecerli() {
        long m = v.makinist("D001");
        jdbc.update("UPDATE brove_hat SET son_surus = '2025-01-01' WHERE makinist_id = ?", m); // kart: 18 ay -> 2026-07-01 (dolmuş)
        jdbc.update("UPDATE makinist SET son_surus = '2025-01-01' WHERE id = ?", m);

        assertThat(belge(m, Tur.HAT_SURUS).durum()).isEqualTo(Durum.DOLDU);
        assertThat(belge(m, Tur.GENEL_SURUS).durum()).isEqualTo(Durum.DOLDU);

        long gecmis = v.sefer("T1", LocalDate.of(2026, 9, 20), "08:00", "10:00"); // gerçekleşmiş sefer, kart değerinden yeni
        v.atama(gecmis, m, "ASIL");
        long gelecek = v.sefer("T2", LocalDate.of(2026, 12, 20), "08:00", "10:00"); // henüz olmadı: türetmeye girmez
        v.atama(gelecek, m, "ASIL");

        assertThat(belge(m, Tur.HAT_SURUS)).satisfies(b -> {
            assertThat(b.bitis()).isEqualTo(LocalDate.of(2028, 3, 20)); // 2026-09-20 + 18 ay
            assertThat(b.durum()).isEqualTo(Durum.GECERLI);
        });
        assertThat(belge(m, Tur.GENEL_SURUS).bitis()).isEqualTo(LocalDate.of(2028, 3, 20));
    }

    @Test
    void kartDegeriTuretilenDenBuyukse_kartGecerli() {
        long m = v.makinist("D001"); // kart: hat ve genel son sürüş 2026-09-01
        long eski = v.sefer("T1", LocalDate.of(2026, 3, 1), "08:00", "10:00");
        v.atama(eski, m, "ASIL");

        assertThat(belge(m, Tur.HAT_SURUS).bitis()).isEqualTo(LocalDate.of(2028, 3, 1)); // 2026-09-01 + 18 ay
    }

    private Belge belge(long makinistId, Tur tur) {
        return servis.rapor(BUGUN).makinistler().stream().filter(m -> m.makinistId() == makinistId).findFirst().orElseThrow().belgeler().stream()
                .filter(b -> b.tur() == tur).findFirst().orElseThrow();
    }


    /** M2 review W2: hiç sürüş kaydı yoksa pano da K-08 gibi en son sınav tarihini başlangıç sayar (GIRILMEMIS göstermez). */
    @Test
    void hicSurusKaydiYok_genelSurus_k08ileAyniYedegiKullanir() {
        long m = v.makinist("D001");
        jdbc.update("UPDATE makinist SET son_surus = NULL WHERE id = ?", m);
        jdbc.update("UPDATE brove_hat SET son_surus = NULL, sinav_tarihi = '2026-06-01' WHERE makinist_id = ?", m);
        jdbc.update("UPDATE brove_arac_tipi SET sinav_tarihi = '2026-05-01' WHERE makinist_id = ?", m);

        assertThat(belge(m, Tur.GENEL_SURUS)).satisfies(b -> {
            assertThat(b.bitis()).isEqualTo(LocalDate.of(2027, 12, 1)); // 2026-06-01 + 18 ay
            assertThat(b.durum()).isEqualTo(Durum.GECERLI);
        });
        assertThat(belge(m, Tur.HAT_SURUS).bitis()).isEqualTo(LocalDate.of(2027, 12, 1));
        assertThat(servis.rapor(BUGUN).makinistler().getFirst().enKotu()).isEqualTo(Durum.GECERLI);
    }

    /** Pano ile kural motoru birbirini yalanlamaz: panonun gösterdiği son gün atamada da son gündür, ertesi gün K-08 ENGEL. */
    @Test
    void panoBitisi_k08Siniriyla_ayniGun() {
        long m = v.makinist("D001");
        jdbc.update("UPDATE makinist SET son_surus = NULL WHERE id = ?", m);
        jdbc.update("UPDATE brove_hat SET son_surus = NULL, sinav_tarihi = '2026-06-01' WHERE makinist_id = ?", m);
        long son = v.sefer("S1", LocalDate.of(2027, 12, 1), "08:00", "10:00");
        long sonrasi = v.sefer("S2", LocalDate.of(2027, 12, 2), "08:00", "10:00");

        assertThat(belge(m, Tur.GENEL_SURUS).bitis()).isEqualTo(LocalDate.of(2027, 12, 1));
        assertThat(atamaServisi.adaylar(son, com.makinistatama.atama.AtamaRol.ASIL)).filteredOn(a -> a.makinistId() == m).singleElement()
                .satisfies(a -> assertThat(a.ihlaller()).extracting(com.makinistatama.atama.IhlalDto::kod).doesNotContain("K-07", "K-08"));
        assertThat(atamaServisi.adaylar(sonrasi, com.makinistatama.atama.AtamaRol.ASIL)).filteredOn(a -> a.makinistId() == m).singleElement()
                .satisfies(a -> assertThat(a.ihlaller()).extracting(com.makinistatama.atama.IhlalDto::kod).contains("K-07", "K-08"));
    }

    @Test
    void uc_atamaciGorur_anonim401() throws Exception {
        v.makinist("D001");

        atamaci().get("/api/rapor/belge-durumu").andExpect(status().isOk()).andExpect(jsonPath("$.makinistler", hasSize(1)))
                .andExpect(jsonPath("$.makinistler[0].belgeler[0].durum").exists()).andExpect(jsonPath("$.uyariGun").value(30));
        new com.makinistatama.Tarayici(mvc).get("/api/rapor/belge-durumu").andExpect(status().isUnauthorized());
    }
}
