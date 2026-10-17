package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/** T8.1: bağlam yükleme: gecikmeli güncel saat, hazırlık/alıkoyma, iptal dışarıda, aday kendi görevleri arasında yok, pencere genişliği. */
class BaglamYukleyiciTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Autowired
    BaglamYukleyici yukleyici;

    @Autowired
    SeferServisi seferler;

    @Autowired
    MakinistServisi makinistler;

    AtamaVeri v;
    long m;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
    }

    private KuralBaglami baglam(long adaySefer) {
        SeferDto sefer = seferler.getir(adaySefer);
        MakinistDto dto = makinistler.detaylar(List.of(m)).getFirst();
        return yukleyici.yukle(sefer, List.of(dto)).baglam(dto);
    }

    @Test
    void gecikmeliSeferinGuncelSaati_veHazirlikAlikoymaDahilAralik() {
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.hazirlik_dk'");
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar = 'gorev.alikoyma_dk'");
        long onceki = v.sefer("T0", GUN, "08:00", "10:00");
        v.atama(onceki, m, "ASIL");
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 90, varis_gecikme_dk = 90 WHERE id = ?", onceki);
        long aday = v.sefer("T1", GUN, "20:00", "22:00");

        KuralBaglami b = baglam(aday);

        assertThat(b.digerGorevler()).hasSize(1);
        Gorev g = b.digerGorevler().getFirst();
        assertThat(g.kalkis()).isEqualTo(Instant.parse("2026-10-12T06:30:00Z"));  // 08:00 + 90 dk gecikme = 09:30 İstanbul
        assertThat(g.varis()).isEqualTo(Instant.parse("2026-10-12T08:30:00Z"));   // 10:00 + 90 dk = 11:30 İstanbul
        assertThat(g.baslangic()).isEqualTo(Instant.parse("2026-10-12T04:30:00Z")); // planlanan kalkış 08:00 (kalkış gecikmesi çalışma süresine girer, OK-1) - hazırlık 30 dk
        assertThat(g.bitis()).isEqualTo(Instant.parse("2026-10-12T08:45:00Z"));     // alıkoyma 15 dk
        assertThat(b.aday().baslangic()).isEqualTo(Instant.parse("2026-10-12T16:30:00Z")); // 20:00 İstanbul - 30 dk
        assertThat(b.aday().seferId()).isEqualTo(aday);
    }

    @Test
    void iptalSeferGorevSayilmaz_adayKendiGorevleriArasindaDegil() {
        long iptal = v.sefer("T0", GUN, "08:00", "10:00");
        v.atama(iptal, m, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptal);
        long aday = v.sefer("T1", GUN, "12:00", "14:00");
        v.atama(aday, m, "YARDIMCI"); // aday seferdeki başka rol: bağlamda olmamalı

        KuralBaglami b = baglam(aday);

        assertThat(b.digerGorevler()).isEmpty();
    }

    @Test
    void sonSurusIcinPencereOncesiHatBazindaEnGecVaris() {
        long eski = v.sefer("T0", GUN.minusDays(400), "08:00", "10:00");
        v.atama(eski, m, "ASIL");
        long aday = v.sefer("T1", GUN, "12:00", "14:00");

        KuralBaglami b = baglam(aday);

        assertThat(b.pencereOncesiHatSonSurus()).containsEntry(v.hat, GUN.minusDays(400)).hasSize(1);
        assertThat(b.pencereOncesiGenelSonSurus()).isEqualTo(GUN.minusDays(400));
    }

    @Test
    void pencere_max15_7xArdHaftaMaxArti1_gun() {
        assertThat(BaglamYukleyici.pencereGun(parametre("gece.ard_hafta_max", "0"))).isEqualTo(15);
        assertThat(BaglamYukleyici.pencereGun(parametre("gece.ard_hafta_max", "1"))).isEqualTo(15);
        assertThat(BaglamYukleyici.pencereGun(parametre("gece.ard_hafta_max", "3"))).isEqualTo(28);
        assertThat(BaglamYukleyici.pencereGun(parametre("gece.ard_hafta_max", "10"))).isEqualTo(77);
    }

    @Test
    void yuklenenPencere_ardHaftaMax3_iken28GunGeriyeBakar_K15kapaliyken() {
        jdbc.update("UPDATE kural_siddet SET siddet = 'KAPALI' WHERE kod = 'K-15'"); // yıl başı yüklemesi pencereyi genişletmesin
        long yirmiYediGunOnce = v.sefer("T0", GUN.minusDays(27), "08:00", "10:00");
        long otuzGunOnce = v.sefer("T00", GUN.minusDays(30), "08:00", "10:00");
        v.atama(yirmiYediGunOnce, m, "ASIL");
        v.atama(otuzGunOnce, m, "ASIL");
        long aday = v.sefer("T1", GUN, "12:00", "14:00");

        assertThat(baglam(aday).digerGorevler()).isEmpty(); // varsayılan 15 gün: 27 ve 30 gün önceki dışarıda

        jdbc.update("UPDATE kural_parametre SET deger = '3' WHERE anahtar = 'gece.ard_hafta_max'");
        assertThat(baglam(aday).digerGorevler()).extracting(Gorev::seferId).containsExactly(yirmiYediGunOnce); // 28 gün: 27 içeride, 30 dışarıda
    }

    @Test
    void ozelKuralVarken_pencereAyIcin31_yilIcinYillik_kuralYokkenAyni() {
        var bos = parametre("gece.ard_hafta_max", "1");
        assertThat(BaglamYukleyici.pencereGun(bos)).isEqualTo(15);
        assertThat(BaglamYukleyici.yillik(bos)).isTrue(); // K-15 varsayılan ENGEL
        var ay = ozelli(bos, com.makinistatama.kural.Pencere.AY, com.makinistatama.kural.Siddet.ENGEL);
        assertThat(BaglamYukleyici.pencereGun(ay)).isEqualTo(31); // ay sonu gece taşması: 30 değil 31 (Tur 3, W2)
        assertThat(BaglamYukleyici.pencereGun(ozelli(bos, com.makinistatama.kural.Pencere.AY, com.makinistatama.kural.Siddet.KAPALI))).isEqualTo(15);
        assertThat(BaglamYukleyici.pencereGun(ozelli(bos, com.makinistatama.kural.Pencere.HAFTA, com.makinistatama.kural.Siddet.ENGEL))).isEqualTo(15);
        var k15Kapali = com.makinistatama.kural.Parametreler.olustur(bos.degerler(), java.util.Map.of("K-15", com.makinistatama.kural.Siddet.KAPALI));
        assertThat(BaglamYukleyici.yillik(k15Kapali)).isFalse();
        assertThat(BaglamYukleyici.yillik(ozelli(k15Kapali, com.makinistatama.kural.Pencere.YIL, com.makinistatama.kural.Siddet.ENGEL))).isTrue(); // yıl başından yükleme
        assertThat(BaglamYukleyici.yillik(ozelli(k15Kapali, com.makinistatama.kural.Pencere.AY, com.makinistatama.kural.Siddet.ENGEL))).isFalse();
    }

    @Test
    void yuklenenPencere_aktifAyKuraliVarken31GunGeriyeBakar() {
        jdbc.update("UPDATE kural_siddet SET siddet = 'KAPALI' WHERE kod = 'K-15'");
        long yirmiSekizGunOnce = v.sefer("T0", GUN.minusDays(28), "08:00", "10:00");
        long otuzUcGunOnce = v.sefer("T00", GUN.minusDays(33), "08:00", "10:00");
        v.atama(yirmiSekizGunOnce, m, "ASIL");
        v.atama(otuzUcGunOnce, m, "ASIL");
        long aday = v.sefer("T1", GUN, "12:00", "14:00");
        assertThat(baglam(aday).digerGorevler()).isEmpty(); // özel kural yok: 15 gün

        jdbc.update("""
                INSERT INTO ozel_kural (ad, olcu, pencere, karsilastirma, esik, kapsam, siddet, gecerlilik_bas, gerekce, olusturan)
                VALUES ('Aylık 100', 'TOPLAM_GOREV_SURESI', 'AY', 'EN_FAZLA', 100, '{}', 'ENGEL', '2026-01-01', 'deneme', 'test')""");
        assertThat(baglam(aday).digerGorevler()).extracting(Gorev::seferId).containsExactly(yirmiSekizGunOnce); // 31 gün + 1 (önceki güne uzanan çalışma aralığı, kural kurucu Tur 3): 28 içeride, 33 dışarıda
    }

    private static com.makinistatama.kural.Parametreler ozelli(com.makinistatama.kural.Parametreler p, com.makinistatama.kural.Pencere pencere,
            com.makinistatama.kural.Siddet siddet) {
        var kural = new com.makinistatama.kural.OzelKural(1, "k", com.makinistatama.kural.Olcu.TOPLAM_GOREV_SURESI, pencere, com.makinistatama.kural.Karsilastirma.EN_FAZLA,
                java.math.BigDecimal.TEN, null, siddet, LocalDate.of(2026, 1, 1), null, "g", "k", 0);
        return com.makinistatama.kural.Parametreler.olustur(p.degerler(), p.siddetler(), List.of(kural));
    }

    private static com.makinistatama.kural.Parametreler parametre(String anahtar, String deger) {
        var d = new java.util.HashMap<>(VarsayilanParametreler.DEGERLER);
        d.put(anahtar, deger);
        return com.makinistatama.kural.Parametreler.olustur(d, VarsayilanParametreler.SIDDETLER);
    }
}
