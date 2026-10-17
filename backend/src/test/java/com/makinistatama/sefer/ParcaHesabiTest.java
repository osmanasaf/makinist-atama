package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.sefer.ParcaHesabi.Hata;
import com.makinistatama.sefer.ParcaHesabi.Nokta;
import com.makinistatama.sefer.ParcaHesabi.Parca;
import com.makinistatama.sefer.ParcaHesabi.Parcalar;
import com.makinistatama.sefer.ParcaHesabi.Sonuc;

/** Saf parça hesabı (MK-120): DB'siz. Ankara=1 Sivas=2 Erzincan=3 Kars=4; araç tipi 20, hat 10. */
class ParcaHesabiTest {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    static Nokta nokta(int sira, long gar, String varis, String kalkis, String km) {
        return new Nokta(sira, gar, LocalTime.parse(varis), LocalTime.parse(kalkis), km == null ? null : new BigDecimal(km), null, null);
    }

    static Instant an(LocalDate gun, String saat) {
        return gun.atTime(LocalTime.parse(saat)).atZone(ZamanAyari.BOLGE).toInstant();
    }

    /** DE-1503 Ankara 08:00 -> Kars 20:10. */
    static Sonuc hesapla(List<Nokta> n, String km, Integer azami, Instant sinir) {
        return ParcaHesabi.parcala(GUN, LocalTime.of(8, 0), LocalTime.of(20, 10), 1, 4, 20, 10, km == null ? null : new BigDecimal(km), n, azami, sinir);
    }

    static List<Parca> parcalar(Sonuc s) {
        assertThat(s).isInstanceOf(Parcalar.class);
        return ((Parcalar) s).liste();
    }

    static Hata hata(Sonuc s) {
        assertThat(s).isInstanceOf(Hata.class);
        return (Hata) s;
    }

    @Test
    void ikiParca_kmFarkiVeSaatler() {
        List<Parca> p = parcalar(hesapla(List.of(nokta(1, 2, "13:30", "13:40", "603")), "1103", 24, null));

        assertThat(p).hasSize(2);
        assertThat(p.get(0).kalkisGarId()).isEqualTo(1);
        assertThat(p.get(0).varisGarId()).isEqualTo(2);
        assertThat(p.get(0).kalkis()).isEqualTo(an(GUN, "08:00"));
        assertThat(p.get(0).varis()).isEqualTo(an(GUN, "13:30"));
        assertThat(p.get(0).km()).isEqualByComparingTo("603");
        assertThat(p.get(1).kalkisGarId()).isEqualTo(2);
        assertThat(p.get(1).varisGarId()).isEqualTo(4);
        assertThat(p.get(1).kalkis()).isEqualTo(an(GUN, "13:40"));
        assertThat(p.get(1).varis()).isEqualTo(an(GUN, "20:10"));
        assertThat(p.get(1).km()).isEqualByComparingTo("500");
        assertThat(p).extracting(Parca::parcaNo).containsExactly(1, 2);
        assertThat(p).extracting(Parca::parcaSayisi).containsExactly(2, 2);
        assertThat(p).extracting(Parca::trenGunu).containsOnly(GUN);
        assertThat(p).extracting(Parca::tarih).containsOnly(GUN);
    }

    @Test
    void ucParca_kumulatifKmFarki() {
        List<Parca> p = parcalar(hesapla(List.of(nokta(1, 2, "11:00", "11:10", "300"), nokta(2, 3, "15:00", "15:10", "800")), "1103", 24, null));

        assertThat(p).hasSize(3);
        assertThat(p.get(0).km()).isEqualByComparingTo("300");
        assertThat(p.get(1).km()).isEqualByComparingTo("500");
        assertThat(p.get(2).km()).isEqualByComparingTo("303");
        assertThat(p).extracting(Parca::kalkisGarId).containsExactly(1L, 2L, 3L);
        assertThat(p).extracting(Parca::varisGarId).containsExactly(2L, 3L, 4L);
        assertThat(p.get(2).kalkis()).isEqualTo(an(GUN, "15:10"));
    }

    @Test
    void kmYoksa_farkNull() {
        assertThat(parcalar(hesapla(List.of(nokta(1, 2, "13:30", "13:40", null)), null, 24, null))).extracting(Parca::km).containsOnlyNulls();
        // sefer km null, nokta km dolu: ilk parça nokta km'si, son parça farkı null
        List<Parca> p = parcalar(hesapla(List.of(nokta(1, 2, "13:30", "13:40", "603")), null, 24, null));
        assertThat(p.get(0).km()).isEqualByComparingTo("603");
        assertThat(p.get(1).km()).isNull();
    }

    @Test
    void geceAsan_parca2ErtesiGundeBiterAmaTrenGunuAyni() {
        // 20:00 -> 07:00; nokta 23:30 / 23:50: parça 2 ertesi günde biter
        List<Parca> p = parcalar(ParcaHesabi.parcala(GUN, LocalTime.of(20, 0), LocalTime.of(7, 0), 1, 4, 20, 10, null, List.of(nokta(1, 2, "23:30", "23:50", null)), 24, null));

        assertThat(p.get(0).tarih()).isEqualTo(GUN);
        assertThat(p.get(0).varis()).isEqualTo(an(GUN, "23:30"));
        assertThat(p.get(1).tarih()).isEqualTo(GUN);
        assertThat(p.get(1).kalkis()).isEqualTo(an(GUN, "23:50"));
        assertThat(p.get(1).varis()).isEqualTo(an(GUN.plusDays(1), "07:00"));
        assertThat(p).extracting(Parca::trenGunu).containsOnly(GUN);
    }

    @Test
    void geceAsan_parcaVarisiGeceyarisindanSonra_sonrakiParcaErtesiGunBaslar() {
        // 20:00 -> 09:00; nokta 01:00 varış (ertesi gün), 01:20 kalkış: parça 2 ertesi günün satırı
        List<Parca> p = parcalar(ParcaHesabi.parcala(GUN, LocalTime.of(20, 0), LocalTime.of(9, 0), 1, 4, 20, 10, null, List.of(nokta(1, 2, "01:00", "01:20", null)), 24, null));

        assertThat(p.get(0).tarih()).isEqualTo(GUN);
        assertThat(p.get(0).varis()).isEqualTo(an(GUN.plusDays(1), "01:00"));
        assertThat(p.get(1).tarih()).isEqualTo(GUN.plusDays(1));
        assertThat(p.get(1).kalkis()).isEqualTo(an(GUN.plusDays(1), "01:20"));
        assertThat(p.get(1).varis()).isEqualTo(an(GUN.plusDays(1), "09:00"));
        assertThat(p).extracting(Parca::trenGunu).containsOnly(GUN);
    }

    @Test
    void hatVeAracMirasi_noktadaDoluysaOBaslangicta_degilseSatirinki() {
        Nokta a = new Nokta(1, 2, LocalTime.of(11, 0), LocalTime.of(11, 10), null, 11L, 21L);
        Nokta b = new Nokta(2, 3, LocalTime.of(15, 0), LocalTime.of(15, 10), null, null, null);
        List<Parca> p = parcalar(hesapla(List.of(a, b), null, 24, null));

        assertThat(p).extracting(Parca::hatId).containsExactly(10L, 11L, 10L); // parça 3 nokta 2'de boş: satırınki
        assertThat(p).extracting(Parca::aracTipiId).containsExactly(20L, 21L, 20L);
    }

    @Test
    void besIkiNokta_enFazlaBes() {
        List<Nokta> n = List.of(nokta(1, 2, "09:00", "09:10", "100"), nokta(2, 2, "10:00", "10:10", "200"), nokta(3, 2, "11:00", "11:10", "300"), nokta(4, 2, "12:00", "12:10", "400"),
                nokta(5, 2, "13:00", "13:10", "500"), nokta(6, 2, "14:00", "14:10", "600"));

        Hata h = ParcaHesabi.trenSorunu(n);
        assertThat(h).isNotNull();
        assertThat(h.govde()).contains("en çok 5 değişim noktası");
        assertThat(h.engelli()).isFalse();
        assertThat(hata(hesapla(n, "1103", 24, null)).govde()).contains("en çok 5 değişim noktası"); // parcala önce trenSorunu'na bakar
        assertThat(ParcaHesabi.trenSorunu(n.subList(0, 5))).isNull();
    }

    @Test
    void sira_ardisikDegil() {
        Hata h = ParcaHesabi.trenSorunu(List.of(nokta(2, 2, "13:30", "13:40", "603")));
        assertThat(h.govde()).contains("sıra 1'den başlayıp ardışık olmalı (1..1)");
        assertThat(ParcaHesabi.trenSorunu(List.of(nokta(1, 2, "13:30", "13:40", "603"), nokta(3, 3, "16:00", "16:10", "800")))).isNotNull();
        assertThat(ParcaHesabi.trenSorunu(List.of(nokta(1, 2, "13:30", "13:40", "603"), nokta(2, 3, "16:00", "16:10", "800")))).isNull();
    }

    @Test
    void kmAzaliyor_noktalarArasi() {
        Hata h = ParcaHesabi.trenSorunu(List.of(nokta(1, 2, "13:30", "13:40", "900"), nokta(2, 3, "16:00", "16:10", "800")));
        assertThat(h.govde()).isEqualTo("KM sıra ilerledikçe azalamaz");
        assertThat(h.noktaSirasi()).isEqualTo(2);
        // km'si olmayan nokta karşılaştırmayı atlar
        assertThat(ParcaHesabi.trenSorunu(List.of(nokta(1, 2, "13:30", "13:40", "900"), nokta(2, 3, "16:00", "16:10", null), nokta(3, 3, "17:00", "17:10", "950")))).isNull();
    }

    @Test
    void negatifKm_sonParcaVeAraParca() {
        Hata son = hata(hesapla(List.of(nokta(1, 2, "13:30", "13:40", "1200")), "1103", 24, null)); // sefer km 1103 < nokta 1200
        assertThat(son.govde()).isEqualTo("KM tutarsız, 2. parçanın km'si negatif çıkıyor (Sefer KM'si noktanın KM'sinden küçük olamaz)");
        assertThat(son.noktaSirasi()).isEqualTo(1);
        assertThat(son.engelli()).isFalse();

        Hata son3 = hata(hesapla(List.of(nokta(1, 2, "11:00", "11:10", "300"), nokta(2, 3, "15:00", "15:10", "800")), "700", 24, null)); // son parça 700 - 800 < 0
        assertThat(son3.govde()).contains("3. parçanın km'si negatif");
        assertThat(son3.noktaSirasi()).isEqualTo(2);
    }

    @Test
    void azamiParcaSuresiAsimi_engelliHata() {
        // varış 07:30 yazım hatası: parça 2 = 13:40 -> ertesi gün 07:30 = 17 s 50 dk
        Hata h = hata(ParcaHesabi.parcala(GUN, LocalTime.of(8, 0), LocalTime.of(7, 30), 1, 4, 20, 10, null, List.of(nokta(1, 2, "13:30", "13:40", null)), 12, null));

        assertThat(h.engelli()).isTrue();
        assertThat(h.govde()).isEqualTo("2. parça 17 s 50 dk sürüyor (en çok 12 s; saat yazım hatası olabilir. Doğruysa 'sefer.parca_azami_saat' ayarını artırın)");
        assertThat(h.noktaSirasi()).isEqualTo(1);
    }

    @Test
    void azamiParcaSuresi_tamSinirdaKabul_azamiNullDenetimsiz() {
        // parça 1 = 5 s 30 dk, parça 2 = 6 s 30 dk
        assertThat(hesapla(List.of(nokta(1, 2, "13:30", "13:40", null)), null, 6, null)).isInstanceOf(Hata.class);
        assertThat(hesapla(List.of(nokta(1, 2, "13:30", "13:40", null)), null, 7, null)).isInstanceOf(Parcalar.class);

        Sonuc uzun = ParcaHesabi.parcala(GUN, LocalTime.of(8, 0), LocalTime.of(7, 30), 1, 4, 20, 10, null, List.of(nokta(1, 2, "13:30", "13:40", null)), null, null);
        assertThat(uzun).isInstanceOf(Parcalar.class);
    }

    @Test
    void bitisSiniri_esleserKabul_uyusmazsaPencereHatasi() {
        List<Nokta> n = List.of(nokta(1, 2, "13:30", "13:40", "603"));

        assertThat(hesapla(n, "1103", null, an(GUN, "20:10"))).isInstanceOf(Parcalar.class);

        Hata h = hata(hesapla(n, "1103", null, an(GUN, "20:11")));
        assertThat(h.govde()).contains("seferin zaman aralığında olmalı").contains("son parça 20:10").contains("sefer 20:11");
        assertThat(h.engelli()).isFalse();
        assertThat(h.noktaSirasi()).isEqualTo(1);

        // saat aynı, gün farklı (ara istasyon saatleri sırayla ilerlerken son parça günlere kayıyor): mesaj "11:49 / 11:49" demez, tarihleri yazar
        Hata kayma = hata(ParcaHesabi.parcala(GUN, LocalTime.of(6, 26), LocalTime.of(11, 49), 1, 4, 20, 10, null, List.of(nokta(1, 2, "03:00", "23:00", null)), null, an(GUN, "11:49")));
        assertThat(kayma.govde()).contains("son parça 14.10 11:49'de varıyor").contains("sefer 12.10 11:49'de varmalı").contains("farklı bir güne kayıyor").contains("(sefer 06:26–11:49)");

        // gece aşan: sınır ertesi günün varışı
        Sonuc gece = ParcaHesabi.parcala(GUN, LocalTime.of(20, 0), LocalTime.of(7, 0), 1, 4, 20, 10, null, List.of(nokta(1, 2, "23:30", "23:50", null)), null, an(GUN.plusDays(1), "07:00"));
        assertThat(gece).isInstanceOf(Parcalar.class);
    }
}
