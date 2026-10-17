package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.Bos;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.atama.OtomatikAtama.Sonuc;
import com.makinistatama.atama.YolculukOnerisi.YolculukAdayi;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.VarsayilanParametreler;
import com.makinistatama.kural.ZamanHesabi;

/**
 * Performans işi (MK-57, E2): otomatik atama planının "önce/sonra aynı" kanıtı (DB'siz). 30 tohum x {yolculuk aşaması kapalı, açık}: {@code Sonuc}'un (atananlar, boş kalanlar: neden +
 * örnekler, yolculuk arama sayısı) kararlı özeti altın dosyayla ({@code golden/otomatik-onceki-sonraki.json}) karşılaştırılır; aynı koşuda tembel yol eski tam yolla eşit olmalı.
 */
class OncesiSonrasiEsdegerTest {

    private static final Instant SIMDI = OtomatikAtamaTest.an("2026-10-10T10:00");
    private static final String[] SAATLER = { "05:00", "08:00", "11:00", "14:00", "17:00", "21:00", "22:30" };
    private static final long ANKARA = OtomatikAtamaTest.ANKARA;
    private static final long ESKISEHIR = OtomatikAtamaTest.ESKISEHIR;

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

    private static Instant an(LocalDate gun, String saat) {
        return gun.atTime(LocalTime.parse(saat)).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    private static Gorev rastgeleGorev(Random r, long id, LocalDate gun, long kalkisGar) {
        Instant kalkis = an(gun, SAATLER[r.nextInt(SAATLER.length)]);
        Instant varis = kalkis.plusSeconds(60L * (60 + 30 * r.nextInt(14)));
        long varisGar = r.nextBoolean() ? ANKARA : ESKISEHIR;
        return new Gorev(id, kalkisGar, varisGar, OtomatikAtamaTest.HAT, OtomatikAtamaTest.ARAC, kalkis, varis, kalkis, varis, 50 + 50 * r.nextInt(3));
    }

    private static long rastgeleGar(Random r) {
        return r.nextBoolean() ? ANKARA : ESKISEHIR;
    }

    private record Girdi(List<SeferGirdisi> seferler, List<MakinistGirdisi> makinistler, Parametreler p, List<YolculukAdayi> yolculuk) {
    }

    private static Girdi uret(long tohum) {
        Random r = new Random(tohum);
        List<OzelKural> secilen = new ArrayList<>();
        for (int i = r.nextInt(4); i > 0; i--) {
            OzelKural k = havuz().get(r.nextInt(havuz().size()));
            if (secilen.stream().noneMatch(x -> x.id() == k.id())) {
                secilen.add(k);
            }
        }
        Map<String, String> d = new HashMap<>(OtomatikAtamaTest.P.degerler());
        d.put("nobet.bekleme_sayilir", r.nextInt(4) == 0 ? "1" : "0");
        d.put("gece.ard_hafta_max", String.valueOf(new int[] { 1, 1, 0, 3 }[r.nextInt(4)]));
        d.put("gece.hafta_min_gorev", String.valueOf(1 + r.nextInt(2)));
        Parametreler p = Parametreler.olustur(d, OtomatikAtamaTest.P.siddetler(), secilen);
        List<MakinistGirdisi> makinistler = new ArrayList<>();
        int sayi = 4 + r.nextInt(8);
        for (int m = 0; m < sayi; m++) {
            List<Gorev> gecmis = new ArrayList<>();
            double saat = 0;
            for (int i = r.nextInt(10); i > 0; i--) {
                Gorev g = rastgeleGorev(r, 1000L * (m + 1) + i, LocalDate.of(2026, 10, 1).plusDays(r.nextInt(45)), rastgeleGar(r));
                gecmis.add(g);
                if (g.kalkis().atZone(ZamanHesabi.ISTANBUL).getMonthValue() == 10) {
                    saat += g.sureDakika() / 60.0;
                }
            }
            for (int i = r.nextInt(60); i > 0; i--) { // yıl boyu seyrek geçmiş (K-15 / K-18 zinciri; yıllık pencere)
                gecmis.add(rastgeleGorev(r, 1000L * (m + 1) + 100 + i, LocalDate.of(2026, 1, 5).plusDays(r.nextInt(270)), rastgeleGar(r)));
            }
            for (int i = r.nextInt(8); i > 0; i--) { // Aralık: ileri tarihli atamalar
                gecmis.add(rastgeleGorev(r, 1000L * (m + 1) + 200 + i, LocalDate.of(2026, 12, 1).plusDays(r.nextInt(31)), rastgeleGar(r)));
            }
            List<MakinistBilgisi.Izin> izin = r.nextInt(5) == 0
                    ? List.of(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 14 + r.nextInt(6)), LocalDate.of(2026, 10, 20 + r.nextInt(3)), MakinistBilgisi.IzinDurumu.ONAY))
                    : List.of();
            LocalDate bitis = LocalDate.of(2030, 1, 1);
            MakinistBilgisi bilgi = new MakinistBilgisi(m + 1, rastgeleGar(r), MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis, Map.of(OtomatikAtamaTest.ARAC, LocalDate.of(2024, 1, 1)),
                    r.nextInt(6) == 0 ? Map.of() : Map.of(OtomatikAtamaTest.HAT, OtomatikAtamaTest.hatBrove()), LocalDate.of(2026, 9, 1), izin);
            Map<YearMonth, double[]> aylik = new HashMap<>();
            aylik.put(OtomatikAtamaTest.EKIM, new double[] { Math.round(saat), 100.0 * r.nextInt(3) });
            makinistler.add(new MakinistGirdisi(m + 1, String.format("D%03d", r.nextInt(2) == 0 ? m + 1 : 100 - m), bilgi, gecmis, Map.of(), null, aylik));
        }
        List<SeferGirdisi> seferler = new ArrayList<>();
        for (int i = 0, n = 8 + r.nextInt(20); i < n; i++) {
            Gorev g = rastgeleGorev(r, 5000 + i, LocalDate.of(2026, 10, 12).plusDays(r.nextInt(7)), rastgeleGar(r));
            LocalDate gun = ZamanHesabi.gun(g.kalkis());
            seferler.add(new SeferGirdisi(g.seferId(), gun, YearMonth.from(gun), g));
        }
        List<YolculukAdayi> yolculuk = new ArrayList<>();
        for (int i = 0, n = 6 + r.nextInt(10); i < n; i++) {
            LocalDate gun = LocalDate.of(2026, 10, 12).plusDays(r.nextInt(7));
            Instant kalkis = an(gun, SAATLER[r.nextInt(SAATLER.length)]);
            long kalkisGar = rastgeleGar(r);
            yolculuk.add(new YolculukAdayi(9000 + i, gun, Gorev.yolculukOlustur(9000 + i, kalkisGar, kalkisGar == ANKARA ? ESKISEHIR : ANKARA, OtomatikAtamaTest.HAT,
                    OtomatikAtamaTest.ARAC, kalkis, kalkis, kalkis.plusSeconds(60L * (60 + 30 * r.nextInt(5))), r.nextBoolean()), false));
        }
        return new Girdi(seferler, makinistler, p, yolculuk);
    }

    private static Sonuc planla(Girdi g, boolean yolculuk, boolean tembel) {
        return OtomatikAtama.planla(g.seferler(), g.makinistler(), OtomatikAtamaTest.ADLAR, g.p(), SIMDI, BaglamYukleyici.pencereGun(g.p()), BaglamYukleyici.yillik(g.p()), tembel,
                yolculuk ? g.yolculuk() : List.of(), 15, 12);
    }

    private static String ozet(Sonuc s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Atandi a : s.atananlar()) {
            md.update(("A|" + a.seferId() + "|" + a.makinistId() + "|" + a.yolculukSeferId() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        for (Bos b : s.bosKalanlar()) {
            md.update(("B|" + b.seferId() + "|" + b.neden() + "|" + b.ornekler() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        md.update(("Y|" + s.yolculukAramaSayisi()).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest()).substring(0, 16);
    }

    @Test
    void otuzTohum_yolculukKapaliVeAcik_planOzetiAltinDosyayla_veTembelEskiTamYolaEsit() throws Exception {
        List<Map<String, Object>> satirlar = new ArrayList<>();
        int atanan = 0;
        int bos = 0;
        int yolculuklu = 0;
        int arama = 0;
        for (long tohum = 1; tohum <= 30; tohum++) {
            Girdi g = uret(tohum);
            for (boolean yolculuk : new boolean[] { false, true }) {
                Sonuc tembel = planla(g, yolculuk, true);
                assertThat(tembel).as("tohum " + tohum + " yolculuk " + yolculuk + ": tembel = eski tam yol").isEqualTo(planla(g, yolculuk, false));
                Map<String, Object> satir = new LinkedHashMap<>();
                satir.put("tohum", tohum);
                satir.put("yolculuk", yolculuk);
                satir.put("atanan", tembel.atananlar().size());
                satir.put("bos", tembel.bosKalanlar().size());
                satir.put("yolculukAramaSayisi", tembel.yolculukAramaSayisi());
                satir.put("ozet", ozet(tembel));
                satirlar.add(satir);
                atanan += tembel.atananlar().size();
                bos += tembel.bosKalanlar().size();
                yolculuklu += (int) tembel.atananlar().stream().filter(a -> a.yolculukSeferId() != null).count();
                arama += tembel.yolculukAramaSayisi();
            }
        }
        assertThat(atanan).as("duyarlılık: atamalar var").isGreaterThan(400);
        assertThat(bos).as("duyarlılık: boş kalanlar var").isGreaterThan(100);
        assertThat(arama).as("duyarlılık: yolculuk aşaması arıyor").isGreaterThan(0);
        assertThat(yolculuklu).as("duyarlılık: yolculuklu atama çıkıyor").isGreaterThan(0);
        AltinDosya.kontrol("otomatik-onceki-sonraki", satirlar);
    }
}
