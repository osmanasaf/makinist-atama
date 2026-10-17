package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;

/** T3.2: özel kural otomatik atamada (saf) ve neden özetinde: Ö- kodu kural adını etiket yapar, kapasite grubundadır. */
class OzelKuralOtomatikAtamaTest {

    private static Parametreler p(OzelKural... kurallar) {
        Parametreler t = VarsayilanParametreler.varsayilan();
        return Parametreler.olustur(t.degerler(), t.siddetler(), List.of(kurallar));
    }

    private static OzelKural dizel(Siddet siddet) {
        return new OzelKural(1, "Dizel hatlar 8 s", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, Karsilastirma.EN_FAZLA, new BigDecimal("8"),
                new Kapsam(java.util.Set.of(OtomatikAtamaTest.HAT), null, null, null, null, null), siddet, LocalDate.of(2026, 1, 1), null, "g", "k", 0);
    }

    private static Sonuc planla(Parametreler p, String bas, String bit) {
        List<MakinistGirdisi> m = List.of(OtomatikAtamaTest.makinist(1, "A", 0, 0), OtomatikAtamaTest.makinist(2, "B", 0, 0), OtomatikAtamaTest.makinist(3, "C", 0, 0));
        return OtomatikAtama.planla(List.of(OtomatikAtamaTest.sefer(101, bas, bit)), m, OtomatikAtamaTest.ADLAR, p, OtomatikAtamaTest.an("2026-10-10T10:00"), 15, true);
    }

    @Test
    void kural_seferiBosBirakir_nedenKuralAdiniEtiketYapar() {
        Sonuc s = planla(p(dizel(Siddet.ENGEL)), "2026-10-12T08:00", "2026-10-12T16:01"); // 8 s 1 dk
        assertThat(s.atananlar()).isEmpty();
        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> {
            assertThat(b.neden()).isEqualTo("0/3 uygun: Dizel hatlar 8 s 3");
            assertThat(b.ornekler()).singleElement().isEqualTo("Dizel hatlar 8 s: Tek sefer süresi: 8 s 1 dk > 8 s (Kural: Dizel hatlar 8 s)");
        });
    }

    @Test
    void sinirdaUygun_8s00_atanir_kapaliVeUyariKuralYokkenDeAtanmazAmaUyariElenir() {
        assertThat(planla(p(dizel(Siddet.ENGEL)), "2026-10-12T08:00", "2026-10-12T16:00").atananlar()).containsExactly(new Atandi(101, 1));
        assertThat(planla(p(dizel(Siddet.KAPALI)), "2026-10-12T08:00", "2026-10-12T16:01").atananlar()).containsExactly(new Atandi(101, 1));
        // UYARI: otomatik atama yalnız kurallara tam uyanı seçer, UYARI'lı elenir
        assertThat(planla(p(dizel(Siddet.UYARI)), "2026-10-12T08:00", "2026-10-12T16:01").atananlar()).isEmpty();
    }

    @Test
    void nedenOzeti_ozelKodKapasiteGrubunda_yetkisizlikSonda_kuralKapatilmissaKodunKendisi() {
        Parametreler p = p(dizel(Siddet.ENGEL));
        assertThat(NedenOzeti.ozet(3, List.of("K-06", "Ö-001", "Ö-001"), p)).isEqualTo("0/3 uygun (yetkili 2, yetkisiz 1): Dizel hatlar 8 s 2, hat yetkisi 1");
        assertThat(NedenOzeti.yetkisizlik("Ö-001")).isFalse();
        assertThat(NedenOzeti.etiket("Ö-001", p)).isEqualTo("Dizel hatlar 8 s");
        assertThat(NedenOzeti.etiket("Ö-009", p)).isEqualTo("Ö-009"); // kapatılmış/bilinmeyen: kodun kendisi
        assertThat(NedenOzeti.etiket("K-13", p)).isEqualTo("günlük süre");
    }

    @Test
    void ozelKural_gunlukToplam_ikinciSeferiAyniMakinisteVermez() {
        OzelKural gunluk = new OzelKural(2, "Günlük 6 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, Karsilastirma.EN_FAZLA, new BigDecimal("6"), Kapsam.TUMU, Siddet.ENGEL,
                LocalDate.of(2026, 1, 1), null, "g", "k", 0);
        List<MakinistGirdisi> m = List.of(OtomatikAtamaTest.makinist(1, "A", 0, 0), OtomatikAtamaTest.makinist(2, "B", 0, 0));
        Sonuc s = OtomatikAtama.planla(List.of(OtomatikAtamaTest.sefer(101, "2026-10-12T06:00", "2026-10-12T10:00"), OtomatikAtamaTest.sefer(102, "2026-10-12T11:00", "2026-10-12T15:00")),
                m, Map.of(), p(gunluk), OtomatikAtamaTest.an("2026-10-10T10:00"), 15, true);
        assertThat(s.atananlar()).containsExactly(new Atandi(101, 1), new Atandi(102, 2)); // A'ya ikinci sefer 8 s > 6 s olurdu
    }

    @Test
    void yilKurali_ileridekiMevcutGorevleriGorur_16GunOtesindeBileKacinir() {
        // A'nın 10 ve 11 Kasım'da 10'ar saatlik görevi var (yıl toplamı 20 s); 20 Ekim'e 1 saatlik sefer A'ya verilirse yıl 21 s > 20 s olur
        OzelKural yil = new OzelKural(3, "Yıl 20 s", Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, Karsilastirma.EN_FAZLA, new BigDecimal("20"), Kapsam.TUMU, Siddet.ENGEL,
                LocalDate.of(2026, 1, 1), null, "g", "k", 0);
        var a = OtomatikAtamaTest.makinist(1, "A", 0, 0, List.of(OtomatikAtamaTest.gorev(901, "2026-11-10T08:00", "2026-11-10T18:00"),
                OtomatikAtamaTest.gorev(902, "2026-11-11T08:00", "2026-11-11T18:00")), true, List.of());
        var b = OtomatikAtamaTest.makinist(2, "B", 0, 0);
        Sonuc s = OtomatikAtama.planla(List.of(OtomatikAtamaTest.sefer(101, "2026-10-20T08:00", "2026-10-20T09:00")), List.of(a, b), OtomatikAtamaTest.ADLAR, p(yil),
                OtomatikAtamaTest.an("2026-10-10T10:00"), 15, true);
        assertThat(s.atananlar()).containsExactly(new Atandi(101, 2));
    }

    @Test
    void bos_sefer_ozeti_tekKuralHerkesiEngelliyorsaGercekNedeniGosterir() { // Tur 2 BUG-4
        OzelKural dizel = dizel(Siddet.ENGEL);
        // A'nın 2 saat önce biten görevi var (K-11 dinlenme de A'yı eler); üçünü de dizel kuralı eler (9 s sefer)
        var a = OtomatikAtamaTest.makinist(1, "A", 0, 0, List.of(OtomatikAtamaTest.gorev(901, "2026-10-12T04:00", "2026-10-12T06:00")), true, List.of());
        Sonuc s = OtomatikAtama.planla(List.of(OtomatikAtamaTest.sefer(101, "2026-10-12T08:00", "2026-10-12T17:00")),
                List.of(a, OtomatikAtamaTest.makinist(2, "B", 0, 0), OtomatikAtamaTest.makinist(3, "C", 0, 0)), OtomatikAtamaTest.ADLAR, p(dizel),
                OtomatikAtamaTest.an("2026-10-10T10:00"), 15, true);
        assertThat(s.bosKalanlar()).singleElement().satisfies(b -> assertThat(b.neden()).isEqualTo("0/3 uygun: Dizel hatlar 8 s 3"));
    }
}
