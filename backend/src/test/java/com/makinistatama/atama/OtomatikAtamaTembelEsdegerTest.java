package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;

/**
 * W3 eşdeğerlik: tembel doğrulama (tercih sırasındaki İLK ihlalsiz makinistte dur) ile eski tam değerlendirme (hepsini değerlendir, en iyisini seç) BİREBİR aynı
 * sonucu verir: atananlar, boş kalanlar, nedenler, örnekler. Rastgele (DB'siz) + demo veri (DB'li).
 */
@ActiveProfiles("dev")
class OtomatikAtamaTembelEsdegerTest extends TestVeritabani {

    private static final Instant SIMDI = OtomatikAtamaTest.an("2026-10-10T10:00");
    private static final String[] SAATLER = { "05:00", "08:00", "11:00", "14:00", "17:00", "21:00" };

    @Autowired
    OtomatikAtamaServisi servis;

    private static OzelKural kural(long id, Olcu olcu, Pencere pencere, Integer kayan, Karsilastirma kars, String esik, Siddet siddet) {
        return new OzelKural(id, "K" + id, olcu, pencere, kayan, kars, new BigDecimal(esik), Kapsam.TUMU, siddet, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    private static List<OzelKural> havuz() {
        Karsilastirma f = Karsilastirma.EN_FAZLA;
        return List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 5, f, "20", Siddet.ENGEL), kural(2, Olcu.GOREV_SAYISI, Pencere.HAFTA, null, f, "6", Siddet.ENGEL),
                kural(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, null, f, "60", Siddet.UYARI), kural(4, Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, null, f, "3", Siddet.ENGEL),
                kural(5, Olcu.IZIN_GUNU, Pencere.HAFTA, null, Karsilastirma.EN_AZ, "2", Siddet.ENGEL), kural(6, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, null, f, "400", Siddet.UYARI),
                kural(7, Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, null, Karsilastirma.EN_AZ, "13", Siddet.ENGEL));
    }

    private static Gorev rastgeleGorev(Random r, long id, LocalDate gun, long kalkisGar) {
        var kalkis = gun.atTime(java.time.LocalTime.parse(SAATLER[r.nextInt(SAATLER.length)])).atZone(com.makinistatama.kural.ZamanHesabi.ISTANBUL).toInstant();
        var varis = kalkis.plusSeconds(60L * (60 + 30 * r.nextInt(10)));
        long varisGar = r.nextBoolean() ? OtomatikAtamaTest.ANKARA : OtomatikAtamaTest.ESKISEHIR;
        return new Gorev(id, kalkisGar, varisGar, OtomatikAtamaTest.HAT, OtomatikAtamaTest.ARAC, kalkis, varis, kalkis, varis, 50 + 50 * r.nextInt(3));
    }

    @Test
    void rastgele_tembelYolEskiTamYolunAyniSonucunuUretir() {
        Random r = new Random(30);
        int atanan = 0;
        int bos = 0;
        for (int s = 0; s < 120; s++) {
            List<OzelKural> secilen = new ArrayList<>();
            for (int i = r.nextInt(4); i > 0; i--) {
                OzelKural k = havuz().get(r.nextInt(havuz().size()));
                if (secilen.stream().noneMatch(x -> x.id() == k.id())) {
                    secilen.add(k);
                }
            }
            Parametreler p = Parametreler.olustur(OtomatikAtamaTest.P.degerler(), OtomatikAtamaTest.P.siddetler(), secilen);
            List<MakinistGirdisi> makinistler = new ArrayList<>();
            int sayi = 4 + r.nextInt(8);
            for (int m = 0; m < sayi; m++) {
                List<Gorev> gecmis = new ArrayList<>();
                double saat = 0;
                for (int i = r.nextInt(8); i > 0; i--) {
                    Gorev g = rastgeleGorev(r, 1000L * (m + 1) + i, LocalDate.of(2026, 10, 1).plusDays(r.nextInt(45)), r.nextBoolean() ? OtomatikAtamaTest.ANKARA : OtomatikAtamaTest.ESKISEHIR);
                    gecmis.add(g);
                    if (g.kalkis().atZone(com.makinistatama.kural.ZamanHesabi.ISTANBUL).getMonthValue() == 10) {
                        saat += g.sureDakika() / 60.0;
                    }
                }
                List<MakinistBilgisi.Izin> izin = r.nextInt(5) == 0
                        ? List.of(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 14 + r.nextInt(6)), LocalDate.of(2026, 10, 20 + r.nextInt(3)), MakinistBilgisi.IzinDurumu.ONAY))
                        : List.of();
                LocalDate bitis = LocalDate.of(2030, 1, 1);
                MakinistBilgisi bilgi = new MakinistBilgisi(m + 1, r.nextBoolean() ? OtomatikAtamaTest.ANKARA : OtomatikAtamaTest.ESKISEHIR, MakinistBilgisi.Durum.AKTIF, bitis, bitis,
                        bitis, Map.of(OtomatikAtamaTest.ARAC, LocalDate.of(2024, 1, 1)),
                        r.nextInt(6) == 0 ? Map.of() : Map.of(OtomatikAtamaTest.HAT, OtomatikAtamaTest.hatBrove()), LocalDate.of(2026, 9, 1), izin);
                Map<YearMonth, double[]> aylik = new HashMap<>();
                aylik.put(OtomatikAtamaTest.EKIM, new double[] { Math.round(saat), 100.0 * r.nextInt(3) }); // sık eşitlik: bağ kırıcılar (km, sicil) da sınanır
                makinistler.add(new MakinistGirdisi(m + 1, String.format("D%03d", r.nextInt(2) == 0 ? m + 1 : 100 - m), bilgi, gecmis, Map.of(), null, aylik));
            }
            List<SeferGirdisi> seferler = new ArrayList<>();
            for (int i = 0, n = 8 + r.nextInt(20); i < n; i++) {
                Gorev g = rastgeleGorev(r, 5000 + i, LocalDate.of(2026, 10, 12).plusDays(r.nextInt(7)), r.nextBoolean() ? OtomatikAtamaTest.ANKARA : OtomatikAtamaTest.ESKISEHIR);
                LocalDate gun = com.makinistatama.kural.ZamanHesabi.gun(g.kalkis());
                seferler.add(new SeferGirdisi(g.seferId(), gun, YearMonth.from(gun), g));
            }
            int pencere = BaglamYukleyici.pencereGun(p);
            boolean yillik = BaglamYukleyici.yillik(p);
            Sonuc tam = OtomatikAtama.planla(seferler, makinistler, OtomatikAtamaTest.ADLAR, p, SIMDI, pencere, yillik, false);
            Sonuc tembel = OtomatikAtama.planla(seferler, makinistler, OtomatikAtamaTest.ADLAR, p, SIMDI, pencere, yillik, true);
            assertThat(tembel).as("senaryo " + s + " kurallar " + secilen.stream().map(OzelKural::ad).toList()).isEqualTo(tam);
            atanan += tam.atananlar().size();
            bos += tam.bosKalanlar().size();
        }
        assertThat(atanan).isGreaterThan(300); // duyarlılık: karşılaştırma boş sonuçlarla geçmiyor
        assertThat(bos).isGreaterThan(50);
    }

    @Test
    void demoVerisi_tembelYolEskiTamYolunAyniSonucunuUretir() throws Exception {
        Tarayici y = yonetici();
        y.post("/api/demo/yukle", "").andExpect(MockMvcResultMatchers.status().isCreated());
        LocalDate bas = LocalDate.now().plusDays(1);
        OtomatikAtamaIstegi istek = new OtomatikAtamaIstegi(bas, bas.plusDays(6), false);
        karsilastir(istek);
        long dizel = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'ARAC_TIPI' AND ad ILIKE 'Dizel%'", Long.class);
        for (String govde : List.of(
                "{\"ad\":\"Gece 3\",\"olcu\":\"ARDISIK_GECE_GUNU\",\"pencere\":\"SERI\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":3,\"kapsam\":{},\"siddet\":\"ENGEL\",\"gerekce\":\"t\"}",
                "{\"ad\":\"Izin 8\",\"olcu\":\"IZIN_GUNU\",\"pencere\":\"AY\",\"karsilastirma\":\"EN_AZ\",\"esik\":8,\"kapsam\":{},\"siddet\":\"ENGEL\",\"gerekce\":\"t\"}",
                "{\"ad\":\"Dizel 8\",\"olcu\":\"TEK_SEFER_SURESI\",\"pencere\":\"SEFER\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":8,\"kapsam\":{\"aracTipleri\":[" + dizel + "]},\"siddet\":\"ENGEL\",\"gerekce\":\"t\"}")) {
            y.post("/api/ozel-kural", govde).andExpect(MockMvcResultMatchers.status().isCreated());
        }
        karsilastir(new OtomatikAtamaIstegi(bas, bas.plusDays(13), false));
    }

    private void karsilastir(OtomatikAtamaIstegi istek) {
        servis.tembel = false;
        OtomatikAtamaDto tam;
        try {
            tam = servis.onizle(istek);
        } finally {
            servis.tembel = true;
        }
        OtomatikAtamaDto tembel = servis.onizle(istek);
        assertThat(tam.atananSayisi()).isGreaterThan(100);
        assertThat(tam.bosSayisi()).isPositive();
        assertThat(tembel).isEqualTo(tam);
    }
}
