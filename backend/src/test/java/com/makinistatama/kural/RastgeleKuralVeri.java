package com.makinistatama.kural;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Performans işi (MK-57) kanıt düzeni için tohumlu, DB'siz rastgele kural bağlamı üreticisi. Geçmiş yıl boyunca dağılır (K-15/K-17/K-18), gece nöbetleri, Aralık'ta ileri tarihli
 * atamalar, gün sınırında biten görevler, eşit başlangıç, yolculuk görevleri, ikinci hat, özel kural havuzu ve parametre çeşitlemeleri içerir. Aynı tohum aynı bağlamları üretir.
 */
final class RastgeleKuralVeri {

    static final long ANKARA = 1;
    static final long ESKISEHIR = 2;
    static final long HAT_A = 10;
    static final long HAT_B = 11;
    static final long ARAC = 20;
    static final Map<Long, String> ADLAR = Map.of(ANKARA, "Ankara", ESKISEHIR, "Eskişehir", HAT_A, "Ankara–Eskişehir", HAT_B, "Ankara–Sivas", ARAC, "YHT");
    static final Instant SIMDI = TestVerisi.an("2026-10-10T10:00");

    /** Bir senaryo: tek makinist + onun geçmişi + aday görevler; her aday bir {@link KuralBaglami}. */
    record Senaryo(int no, List<KuralBaglami> baglamlar) {
    }

    private RastgeleKuralVeri() {
    }

    static OzelKural kural(long id, Olcu olcu, Pencere pencere, Integer kayan, Karsilastirma kars, String esik, Siddet siddet) {
        return new OzelKural(id, "K" + id, olcu, pencere, kayan, kars, new BigDecimal(esik), Kapsam.TUMU, siddet, LocalDate.of(2000, 1, 1), null, "g", "k", 0);
    }

    static List<OzelKural> havuz() {
        Karsilastirma f = Karsilastirma.EN_FAZLA;
        return List.of(kural(1, Olcu.TOPLAM_GOREV_SURESI, Pencere.KAYAN, 5, f, "20", Siddet.ENGEL), kural(2, Olcu.GOREV_SAYISI, Pencere.HAFTA, null, f, "6", Siddet.ENGEL),
                kural(3, Olcu.TOPLAM_GOREV_SURESI, Pencere.AY, null, f, "60", Siddet.UYARI), kural(4, Olcu.ARDISIK_CALISMA_GUNU, Pencere.SERI, null, f, "3", Siddet.ENGEL),
                kural(5, Olcu.IZIN_GUNU, Pencere.HAFTA, null, Karsilastirma.EN_AZ, "2", Siddet.ENGEL),
                kural(6, Olcu.TOPLAM_GOREV_SURESI, Pencere.YIL, null, f, "400", Siddet.UYARI),
                kural(7, Olcu.NOBETLER_ARASI_DINLENME, Pencere.NOBET, null, Karsilastirma.EN_AZ, "13", Siddet.ENGEL));
    }

    private static Instant an(LocalDate gun, LocalTime saat) {
        return gun.atTime(saat).atZone(ZamanHesabi.ISTANBUL).toInstant();
    }

    private static Parametreler parametre(Random r, boolean agir, boolean k14Engel) {
        Map<String, String> d = new HashMap<>(VarsayilanParametreler.DEGERLER);
        Map<String, Siddet> s = new HashMap<>(VarsayilanParametreler.SIDDETLER);
        d.put("nobet.bekleme_sayilir", r.nextInt(3) == 0 ? "1" : "0");
        d.put("gece.hafta_min_gorev", String.valueOf(1 + r.nextInt(2)));
        d.put("gece.ard_hafta_max", String.valueOf(new int[] { 0, 1, 1, 3 }[r.nextInt(4)]));
        d.put("haftalik.normal_saat", agir ? "20" : new String[] { "45", "45", "30", "24" }[r.nextInt(4)]);
        d.put("gece.gorev_orani", new String[] { "0.5", "0.5", "0.3" }[r.nextInt(3)]);
        if (r.nextInt(5) == 0) {
            d.put("gece.baslangic", "22:00");
            d.put("gece.bitis", "05:00");
        }
        d.put("gorev.hazirlik_dk", r.nextInt(4) == 0 ? "30" : "0");
        d.put("gorev.alikoyma_dk", r.nextInt(4) == 0 ? "15" : "0");
        if (r.nextInt(6) == 0) {
            s.put("K-15", Siddet.KAPALI);
        }
        if (r.nextInt(6) == 0) {
            s.put("K-18", Siddet.UYARI);
        }
        if (r.nextInt(5) == 0) {
            s.put("K-21", Siddet.ENGEL);
        }
        if (r.nextInt(8) == 0) {
            s.put("K-17", Siddet.KAPALI);
        }
        if (k14Engel) { // rastgele çekim eklemez: aynı tohum aynı senaryoları üretir (OK-24: varsayılan K-14 KAPALI; ENGEL varyantı MK-5 için)
            s.put("K-14", Siddet.ENGEL);
        }
        List<OzelKural> ozel = new ArrayList<>();
        for (int i = r.nextInt(4); i > 0; i--) {
            OzelKural k = havuz().get(r.nextInt(havuz().size()));
            if (ozel.stream().noneMatch(x -> x.id() == k.id())) {
                ozel.add(k);
            }
        }
        return Parametreler.olustur(d, s, ozel);
    }

    private static Gorev gorev(Random r, Parametreler p, long id, LocalDate gun, boolean gece, boolean sinirda) {
        long kalkisGar = r.nextInt(3) == 0 ? ESKISEHIR : ANKARA;
        long varisGar = r.nextInt(3) == 0 ? ESKISEHIR : ANKARA;
        long hat = r.nextInt(5) == 0 ? HAT_B : HAT_A;
        LocalTime saat = gece ? LocalTime.of(20 + r.nextInt(4), 30 * r.nextInt(2)) : LocalTime.of(5 + r.nextInt(14), 30 * r.nextInt(2));
        Instant kalkis = an(gun, saat);
        long sureDk = gece ? 360 + 30 * r.nextInt(7) : 60 + 30 * r.nextInt(14);
        Instant varis = kalkis.plusSeconds(60 * sureDk);
        if (sinirda) { // gün sınırı: 23:59:59.999999999 ya da tam 00:00'da varış
            varis = an(gun.plusDays(r.nextInt(2)), LocalTime.MIDNIGHT).plusSeconds(r.nextBoolean() ? 0 : -1).plusNanos(r.nextBoolean() ? 999_999_999 : 0);
            if (!varis.isAfter(kalkis)) {
                varis = kalkis.plusSeconds(3600);
            }
        }
        if (r.nextInt(12) == 0) {
            return Gorev.yolculukOlustur(id, kalkisGar, varisGar, hat, ARAC, kalkis, kalkis, varis, r.nextBoolean());
        }
        Instant bas = kalkis.minusSeconds(60 * Long.parseLong(p.degerler().get("gorev.hazirlik_dk")));
        Instant bit = varis.plusSeconds(60 * Long.parseLong(p.degerler().get("gorev.alikoyma_dk")));
        return new Gorev(id, kalkisGar, varisGar, hat, ARAC, kalkis, varis, bas, bit, 50 + 50 * r.nextInt(3), r.nextBoolean() ? "YUK" : "YOLCU");
    }

    private static MakinistBilgisi makinist(Random r, long id) {
        LocalDate bitis = r.nextInt(10) == 0 ? LocalDate.of(2026, 10, 15 + r.nextInt(10)) : LocalDate.of(2030, 1, 1);
        Map<Long, MakinistBilgisi.HatBrove> hat = new HashMap<>();
        if (r.nextInt(8) != 0) {
            hat.put(HAT_A, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1).plusDays(r.nextInt(600))));
        }
        if (r.nextInt(3) != 0) {
            hat.put(HAT_B, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), r.nextBoolean() ? null : LocalDate.of(2025, 1, 1).plusDays(r.nextInt(600))));
        }
        List<MakinistBilgisi.Izin> izin = r.nextInt(5) == 0
                ? List.of(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 14 + r.nextInt(6)), LocalDate.of(2026, 10, 20 + r.nextInt(3)), MakinistBilgisi.IzinDurumu.ONAY))
                : List.of();
        return new MakinistBilgisi(id, r.nextBoolean() ? ANKARA : ESKISEHIR, MakinistBilgisi.Durum.AKTIF, bitis, bitis, bitis, Map.of(ARAC, LocalDate.of(2024, 1, 1)), hat,
                LocalDate.of(2025, 1, 1).plusDays(r.nextInt(600)), izin);
    }

    /** {@code adet} senaryo; her birinde {@code adayAdet} aday bağlam. {@code simdiDolu}: bağlamlarda K-22 için {@link #SIMDI} (aksi halde null). */
    static List<Senaryo> uret(long tohum, int adet, int adayAdet) {
        return uret(tohum, adet, adayAdet, false);
    }

    /** {@code k14Engel}: K-14 (haftalık üst sınır) ENGEL'e alınır; rastgele çekimler aynıdır. */
    static List<Senaryo> uret(long tohum, int adet, int adayAdet, boolean k14Engel) {
        Random r = new Random(tohum);
        List<Senaryo> sonuc = new ArrayList<>();
        for (int s = 0; s < adet; s++) {
            boolean agir = r.nextInt(6) == 0; // ağır yıl: K-15 (yıllık fazla mesai) tetiklensin
            boolean hafif = !agir && r.nextInt(3) == 0; // hafif senaryolar: ihlalsiz adaylar da çıksın
            Parametreler p = parametre(r, agir, k14Engel);
            MakinistBilgisi m = makinist(r, s + 1);
            LocalDate odak = LocalDate.of(2026, 10, 12).plusDays(r.nextInt(40) - 20);
            List<Gorev> gecmis = new ArrayList<>();
            long id = 1000L * (s + 1);
            int yogun = hafif ? r.nextInt(6) : 4 + r.nextInt(40);
            for (int i = 0; i < yogun; i++) { // odak çevresi
                gecmis.add(gorev(r, p, id++, odak.plusDays(r.nextInt(24) - 14), r.nextInt(3) == 0, r.nextInt(15) == 0));
            }
            for (int i = hafif ? 0 : agir ? 350 + r.nextInt(250) : r.nextInt(120); i > 0; i--) { // yıl boyu seyrek geçmiş (K-15 yıl toplamı, K-18 zinciri)
                gecmis.add(gorev(r, p, id++, LocalDate.of(2026, 1, 5).plusDays(r.nextInt(250)), r.nextInt(4) == 0, r.nextInt(25) == 0));
            }
            for (int i = hafif ? 0 : r.nextInt(30); i > 0; i--) { // Aralık: ileri tarihli atamalar (yıl penceresi ileri yükleme)
                gecmis.add(gorev(r, p, id++, LocalDate.of(2026, 12, 1).plusDays(r.nextInt(31)), r.nextInt(3) == 0, r.nextInt(25) == 0));
            }
            List<Gorev> adaylar = new ArrayList<>();
            for (int i = 0; i < adayAdet; i++) {
                LocalDate gun = odak.plusDays(r.nextInt(16) - 4);
                if (r.nextInt(10) == 0) {
                    gun = LocalDate.of(2026, 12, 1).plusDays(r.nextInt(31));
                }
                Gorev g = gorev(r, p, id++, gun, r.nextInt(3) == 0, r.nextInt(12) == 0);
                if (r.nextInt(8) == 0 && !gecmis.isEmpty()) { // eşit başlangıç (bağ kırıcı: seferId)
                    Gorev e = gecmis.get(r.nextInt(gecmis.size()));
                    g = new Gorev(g.seferId(), e.kalkisGarId(), e.varisGarId(), e.hatId(), e.aracTipiId(), e.kalkis(), e.varis(), e.baslangic(), e.bitis(), e.km(), e.trenTuru(),
                            e.yolculuk(), e.sureSayilir());
                }
                adaylar.add(g);
            }
            List<KuralBaglami> baglamlar = new ArrayList<>();
            for (Gorev aday : adaylar) {
                List<Gorev> diger = new ArrayList<>(gecmis);
                if (r.nextInt(3) == 0) { // ileri tarihli komşular (kronolojik gezinmede olmayanlar): S1 koşulu yanlış
                    diger.removeIf(g -> g.baslangic().isAfter(aday.baslangic()) && r.nextInt(3) != 0);
                } else if (r.nextBoolean()) { // yalnız adaydan önce başlayanlar (otomatik atama kronolojik): S1 koşulu doğru
                    diger.removeIf(g -> !g.baslangic().isBefore(aday.baslangic()));
                }
                if (r.nextBoolean()) {
                    Collections.shuffle(diger, r);
                }
                Map<Long, LocalDate> pencereOncesi = r.nextBoolean() ? Map.of(HAT_A, LocalDate.of(2025, 6, 1).plusDays(r.nextInt(300))) : Map.of();
                LocalDate genelOnce = r.nextBoolean() ? LocalDate.of(2025, 6, 1).plusDays(r.nextInt(300)) : null;
                baglamlar.add(new KuralBaglami(aday, diger, m, pencereOncesi, genelOnce, ADLAR, p, r.nextInt(4) == 0 ? SIMDI : null));
            }
            sonuc.add(new Senaryo(s, baglamlar));
        }
        return sonuc;
    }
}
