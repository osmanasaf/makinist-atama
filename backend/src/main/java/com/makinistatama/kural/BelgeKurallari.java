package com.makinistatama.kural;

import static com.makinistatama.kural.ZamanHesabi.tarih;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Belge, yetki ve izin kurallari K-01..K-09 (K-05..K-08 yalniz MAKINIST; K-02 kondüktörde "Kişisel emniyet belgesi"). Belge/yetki kiyasi seferin GUNCEL VARIS gunune gore yapilir. */
public final class BelgeKurallari {

    private BelgeKurallari() {
    }

    public static List<Ihlal> kontrol(KuralBaglami b) {
        List<Ihlal> o = new ArrayList<>();
        Parametreler p = b.parametreler();
        MakinistBilgisi m = b.makinist();
        Gorev a = b.aday();
        LocalDate gun = ZamanHesabi.gun(a.varis());

        if (m.durum() != MakinistBilgisi.Durum.AKTIF) {
            Ihlal.ekle(o, p, "K-01", 0, 0, "", (m.tur() == MakinistBilgisi.Tur.MAKINIST ? "Makinist" : "Kondüktör") + " durumu " + m.durum() + " (AKTIF değil)");
        }
        if (a.yolculuk()) { // F2b (MK-40): yolcu olarak gitmek belge/yetki/sürüş-ara kuralı gerektirmez; yalnız durum (K-01) ve izin (K-09)
            izin(o, b);
            return o;
        }
        boolean makinist = m.tur() == MakinistBilgisi.Tur.MAKINIST;
        belge(o, p, "K-02", makinist ? "Ehliyet" : "Kişisel emniyet belgesi", m.ehliyetBitis(), gun);
        belge(o, p, "K-03", "Sağlık raporu", m.saglikBitis(), gun);
        belge(o, p, "K-04", "Psikoteknik raporu", m.psikoteknikBitis(), gun);
        if (makinist) { // F3b (MK-84): bröve (araç tipi/hat yetkisi, son sürüş ara kuralı) makinist kurallarıdır; kondüktörde bröve yoktur
            yetki(o, b, gun);
            sonSurus(o, b, gun);
        }
        izin(o, b);
        return o;
    }

    private static void belge(List<Ihlal> o, Parametreler p, String kod, String ad, LocalDate bitis, LocalDate gun) {
        if (bitis == null) {
            Ihlal.ekle(o, p, kod, 0, 0, "gün", ad + " bitiş tarihi girilmemiş");
        } else if (bitis.isBefore(gun)) {
            Ihlal.ekle(o, p, kod, ChronoUnit.DAYS.between(bitis, gun), 0, "gün",
                    ad + " geçerlilik bitişi " + tarih(bitis) + "; varış günü " + tarih(gun));
        }
    }

    /** K-05 (arac tipi) ve K-06 (hat): brovede var ve sinav + belge.sinav_gecerlilik_ay >= sefer gunu. */
    private static void yetki(List<Ihlal> o, KuralBaglami b, LocalDate gun) {
        Parametreler p = b.parametreler();
        MakinistBilgisi m = b.makinist();
        Gorev a = b.aday();
        long ay = p.tamsayi("belge.sinav_gecerlilik_ay");

        LocalDate arac = m.aracTipiSinavlari().get(a.aracTipiId());
        String aracAd = b.ad(a.aracTipiId());
        if (arac == null) {
            Ihlal.ekle(o, p, "K-05", 0, 0, "", "Araç tipi yetkisi yok: " + aracAd);
        } else if (arac.plusMonths(ay).isBefore(gun)) {
            Ihlal.ekle(o, p, "K-05", 0, 0, "", "Araç tipi sınavı geçerliliği " + tarih(arac.plusMonths(ay))
                    + " tarihinde bitti: " + aracAd + "; varış günü " + tarih(gun));
        }

        MakinistBilgisi.HatBrove hat = m.hatBroveleri().get(a.hatId());
        String hatAd = b.ad(a.hatId());
        if (hat == null) {
            Ihlal.ekle(o, p, "K-06", 0, 0, "", "Hat yetkisi yok: " + hatAd);
        } else if (hat.sinavTarihi().plusMonths(ay).isBefore(gun)) {
            Ihlal.ekle(o, p, "K-06", 0, 0, "", "Hat sınavı geçerliliği " + tarih(hat.sinavTarihi().plusMonths(ay))
                    + " tarihinde bitti: " + hatAd + "; varış günü " + tarih(gun));
        }
    }

    /** K-07 (hat ara kurali) ve K-08 (brove dusme). */
    private static void sonSurus(List<Ihlal> o, KuralBaglami b, LocalDate gun) {
        Parametreler p = b.parametreler();
        MakinistBilgisi m = b.makinist();
        Gorev a = b.aday();

        MakinistBilgisi.HatBrove hat = m.hatBroveleri().get(a.hatId());
        if (hat != null) {
            LocalDate son = hatSonSurus(Arrays.asList(hat.sonSurus(), b.pencereOncesiHatSonSurus().get(a.hatId()),
                    pencereSonSurus(b, gun, true)), hat.sinavTarihi());
            ara(o, p, "K-07", "Hat son sürüşü", b.ad(a.hatId()), p.tamsayi("hat.ara_max_ay"), son, gun);
        }

        List<LocalDate> surus = new ArrayList<>(Arrays.asList(m.kartSonSurus(), b.pencereOncesiGenelSonSurus(), pencereSonSurus(b, gun, false)));
        List<LocalDate> sinav = new ArrayList<>(m.aracTipiSinavlari().values());
        for (MakinistBilgisi.HatBrove h : m.hatBroveleri().values()) {
            surus.add(h.sonSurus());
            sinav.add(h.sinavTarihi());
        }
        LocalDate genel = genelSonSurus(surus, sinav);
        if (genel != null) {
            ara(o, p, "K-08", "Son sürüş", null, p.tamsayi("brove.surus_ara_max_ay"), genel, gun);
        }
    }

    private static void ara(List<Ihlal> o, Parametreler p, String kod, String ad, String ek, long maxAy,
                            LocalDate son, LocalDate gun) {
        if (gun.isAfter(son.plusMonths(maxAy))) {
            long ay = Math.max(ChronoUnit.MONTHS.between(son, gun), maxAy + 1);
            Ihlal.ekle(o, p, kod, ay, maxAy, "ay", ad + " " + tarih(son) + ", varış günü " + tarih(gun) + ": "
                    + ay + " ay > " + maxAy + " ay" + (ek == null ? "" : " (" + ek + ")"));
        }
    }

    /** Penceredeki, aday seferin gunuden ONCE biten (hat verilmisse yalniz o hattaki) gorevlerin en yeni varis gunu. */
    private static LocalDate pencereSonSurus(KuralBaglami b, LocalDate gun, boolean yalnizAdayHat) {
        // MK-55: gün değil ANI karşılaştırılır (gun(t) < G <=> t < gunBasi(G); gun(t) t'de tekdüze): en yeni an bulunur, tek gün dönüşümü yapılır (görev başına saat dilimi dönüşümü pahalıydı)
        Instant sinir = ZamanHesabi.gunBasi(gun);
        Instant en = null;
        for (Gorev g : b.digerGorevler()) {
            if (!g.yolculuk() && g.varis().isBefore(sinir) && (!yalnizAdayHat || g.hatId() == b.aday().hatId()) && (en == null || g.varis().isAfter(en))) { // yolculuk sürüş değildir, son sürüşü "sulamaz" (MK-40)
                en = g.varis();
            }
        }
        return en == null ? null : ZamanHesabi.gun(en);
    }

    /** K-07 ve belge panosunun ORTAK tanımı: hattaki en yeni sürüş; hiç sürüş kaydı yoksa hat sınav tarihi. null'lar yok sayılır. */
    public static LocalDate hatSonSurus(Collection<LocalDate> surusTarihleri, LocalDate sinavTarihi) {
        LocalDate son = enYeni(surusTarihleri.toArray(new LocalDate[0]));
        return son != null ? son : sinavTarihi;
    }

    /** K-08 ve belge panosunun ORTAK tanımı: en yeni sürüş; hiç sürüş kaydı yoksa en yeni sınav tarihi (araç tipi ve hat). */
    public static LocalDate genelSonSurus(Collection<LocalDate> surusTarihleri, Collection<LocalDate> sinavTarihleri) {
        LocalDate son = enYeni(surusTarihleri.toArray(new LocalDate[0]));
        return son != null ? son : enYeni(sinavTarihleri.toArray(new LocalDate[0]));
    }

    private static LocalDate enYeni(LocalDate... tarihler) {
        LocalDate en = null;
        for (LocalDate t : tarihler) {
            if (t != null && (en == null || t.isAfter(en))) {
                en = t;
            }
        }
        return en;
    }

    /** K-09: yalniz ONAY durumundaki izin; gorev araliginin dokundugu herhangi bir gun izin gunuyse. */
    private static void izin(List<Ihlal> o, KuralBaglami b) {
        Gorev a = b.aday();
        List<LocalDate> gunler = ZamanHesabi.gunler(a.baslangic(), a.bitis());
        for (MakinistBilgisi.Izin i : b.makinist().izinler()) {
            if (i.durum() != MakinistBilgisi.IzinDurumu.ONAY) {
                continue;
            }
            boolean kesisir = gunler.stream().anyMatch(g -> !g.isBefore(i.baslangic()) && !g.isAfter(i.bitis()));
            if (kesisir) {
                Ihlal.ekle(o, b.parametreler(), "K-09", 0, 0, "",
                        "Onaylı izin: " + tarih(i.baslangic()) + " – " + tarih(i.bitis()));
            }
        }
    }
}
