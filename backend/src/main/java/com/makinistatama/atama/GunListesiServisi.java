package com.makinistatama.atama;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.sefer.TrenTuru;
import com.makinistatama.tanim.TanimServisi;

/**
 * Atama masasının okuma yüzü (AtamaServisi'nden ayrıldı, MK-47): gün listesi (canlı kırmızı atama), kırmızı sayısı, atamacı "gördüm" sayaçları ve izin etkisi. Yalnız salt okuma;
 * yazma, aday ve rapor okumaları {@link AtamaServisi}'nde kalır.
 */
@Service
@Transactional(readOnly = true)
public class GunListesiServisi {

    private static final ZoneId ISTANBUL = ZamanAyari.BOLGE;
    private static final int EN_COK_ARALIK_GUN = 62;
    private static final String UYARI_SAAT = "gordum.uyari_saat";

    private final AtamaRepository depo;
    private final SatirKaynagi satirKaynagi;
    private final IntikalServisi intikaller;
    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final TanimServisi tanimlar;
    private final BaglamYukleyici yukleyici;
    private final KirmiziAtamaHesabi kirmiziHesabi;
    private final GorevGordumRepository gordumlar;
    private final KullaniciServisi kullanicilar;
    private final AyarServisi ayarlar;
    private final Clock saat;

    GunListesiServisi(AtamaRepository depo, SatirKaynagi satirKaynagi, IntikalServisi intikaller, SeferServisi seferler, MakinistServisi makinistler, TanimServisi tanimlar, BaglamYukleyici yukleyici,
            KirmiziAtamaHesabi kirmiziHesabi, GorevGordumRepository gordumlar, KullaniciServisi kullanicilar, AyarServisi ayarlar, Clock saat) {
        this.depo = depo;
        this.satirKaynagi = satirKaynagi;
        this.intikaller = intikaller;
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.tanimlar = tanimlar;
        this.yukleyici = yukleyici;
        this.kirmiziHesabi = kirmiziHesabi;
        this.gordumlar = gordumlar;
        this.kullanicilar = kullanicilar;
        this.ayarlar = ayarlar;
        this.saat = saat;
    }

    /** Günün seferleri; her atamanın canlı kırmızı/uyarı durumu o okuma anında hesaplanır (MK-5). */
    public GunListesiDto gunListesi(LocalDate tarih) {
        List<SeferDto> liste = seferler.gunListesi(tarih);
        Map<Long, List<Atama>> atamaMap = atamalar(liste);
        Map<Long, KirmiziAtamaHesabi.Sonuc> durum = kirmiziHesabi.hesapla(liste, atamaMap);
        Map<Long, MakinistDto.Ozet> kisiler = makinistler.liste(null, null).stream().collect(Collectors.toMap(MakinistDto.Ozet::id, o -> o));
        Map<Long, String> bosNedenleri = bosNedenleri(liste, atamaMap);
        boolean kondukturGerekli = ayarlar.tamsayi("konduktor.yolcu_trenine_gerekli") == 1;
        GordumBaglami gordum = gordumBaglami(atamaMap);
        List<GunListesiDto.GunSatiriDto> satirlar = liste.stream().map(s -> {
            List<GunListesiDto.GorevliDto> gorevliler = atamaMap.getOrDefault(s.id(), List.of()).stream().sorted(Comparator.comparing(Atama::rol)).map(a -> {
                KirmiziAtamaHesabi.Sonuc d = durum.get(a.id());
                MakinistDto.Ozet k = kisiler.get(a.makinistId());
                GordumBilgisi g = gordum.bilgi(a, s);
                return new GunListesiDto.GorevliDto(a.id(), a.rol(), a.makinistId(), k.sicil(), k.adSoyad(), d != null && d.kirmizi(),
                        d == null ? List.of() : d.nedenler(), d == null ? List.of() : d.uyarilar(), g == null ? null : g.durum(),
                        g == null || g.zaman() == null ? null : g.zaman().atZone(ISTANBUL).toOffsetDateTime(), g != null && g.uyari());
            }).toList();
            return new GunListesiDto.GunSatiriDto(s.id(), s.trenNo(), s.kalkisGar(), s.varisGar(), s.planlananKalkis(), s.planlananVaris(), s.guncelKalkis(),
                    s.guncelVaris(), s.kalkisGecikmeDk(), s.varisGecikmeDk(), s.aracTipi(), s.hat(), s.km(), s.durum(), gorevliler,
                    gorevliler.stream().anyMatch(GunListesiDto.GorevliDto::kirmizi), bosNedenleri.get(s.id()), s.parcaNo(), s.parcaSayisi(),
                    kondukturGerekli && s.durum() != SeferDurumu.IPTAL && s.trenTuru() == TrenTuru.YOLCU && gorevliler.stream().noneMatch(g -> g.rol() == AtamaRol.KONDUKTOR));
        }).toList();
        return new GunListesiDto(tarih, OffsetDateTime.now(saat), satirlar, (int) satirlar.stream().filter(GunListesiDto.GunSatiriDto::kondukturEksik).count());
    }

    /**
     * UX-13: ASIL'ı olmayan, iptal ve geçmiş olmayan her sefer için, kurallara tam uyan (ihlalsiz) makinist yoksa "0/N uygun: ..." özeti (otomatik atamayla
     * aynı ölçüt ve özet). Bir günün tek yüklemesiyle hesaplanır; seferi boş olup adayı olanlar listede yoktur.
     */
    private Map<Long, String> bosNedenleri(List<SeferDto> liste, Map<Long, List<Atama>> atamaMap) {
        List<SeferDto> bos = liste.stream().filter(s -> s.durum() != SeferDurumu.IPTAL && atamaMap.getOrDefault(s.id(), List.of()).stream().noneMatch(a -> a.rol() == AtamaRol.ASIL)).toList();
        if (bos.isEmpty()) {
            return Map.of();
        }
        List<MakinistDto> aktif = makinistler.aktifDetaylar(PersonelTuru.MAKINISTLER);
        LocalDate gun = bos.getFirst().tarih();
        Yuk y = yukleyici.yukleAralik(gun, gun, aktif);
        Map<Long, String> sonuc = new java.util.HashMap<>();
        for (SeferDto s : bos) {
            if (!s.guncelKalkis().toInstant().isAfter(y.simdi())) {
                continue;
            }
            List<List<Ihlal>> elenenler = new ArrayList<>();
            boolean uygunVar = false;
            for (MakinistDto m : aktif) {
                boolean zatenGorevli = y.satirlar(m.id()).stream().anyMatch(sr -> sr.seferId().equals(s.id()));
                List<Ihlal> ihlaller = zatenGorevli ? List.of(new Ihlal("K-10", Siddet.ENGEL, 0, 0, "", "Makinist bu seferde zaten görevli"))
                        : KuralMotoru.degerlendirYazma(y.baglamZincirli(m, s));
                if (ihlaller.isEmpty()) {
                    uygunVar = true;
                    break;
                }
                elenenler.add(ihlaller);
            }
            if (!uygunVar) {
                sonuc.put(s.id(), NedenOzeti.ozet(aktif.size(), NedenOzeti.birincil(elenenler), y.parametreler()));
            }
        }
        return sonuc;
    }

    /** Aralıkta (iki uç dahil) kırmızı sefer / atama sayısı: üst çubuk rozeti (varsayılan: seçili gün + sonraki 7 gün, istemci seçer). */
    public KirmiziSayisiDto kirmiziSayisi(LocalDate bas, LocalDate bit) {
        aralikDogrula(bas, bit);
        List<SeferDto> liste = seferler.aralikListesi(bas, bit);
        Map<Long, List<Atama>> atamaMap = atamalar(liste);
        Map<Long, KirmiziAtamaHesabi.Sonuc> durum = kirmiziHesabi.hesapla(liste, atamaMap);
        int kirmiziAtama = (int) durum.values().stream().filter(KirmiziAtamaHesabi.Sonuc::kirmizi).count();
        List<SeferDto> kirmiziSeferler = liste.stream().filter(s -> atamaMap.getOrDefault(s.id(), List.of()).stream().anyMatch(a -> durum.get(a.id()).kirmizi())).toList();
        LocalDate ilkKirmiziGun = kirmiziSeferler.stream().map(SeferDto::tarih).min(LocalDate::compareTo).orElse(null);
        return new KirmiziSayisiDto(bas, bit, kirmiziSeferler.size(), kirmiziAtama, intikaller.kirmiziSayisi(bas, bit), ilkKirmiziGun);
    }

    /**
     * F5 (MK-177): {@link #kirmiziSayisi}'nın AYNI hesabı (aynı liste, aynı {@code KirmiziAtamaHesabi}), sayı yerine kırmızı atama kimlikleri; aylık çizelge hücre bayrağı. Aralık sınırı
     * ({@code EN_COK_ARALIK_GUN}) aynıdır. Salt okuma.
     */
    public java.util.Set<Long> kirmiziAtamaKimlikleri(LocalDate bas, LocalDate bit) {
        aralikDogrula(bas, bit);
        List<SeferDto> liste = seferler.aralikListesi(bas, bit);
        return kirmiziHesabi.hesapla(liste, atamalar(liste)).entrySet().stream().filter(e -> e.getValue().kirmizi()).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * F4 (MK-180): aralıkta (iki uç dahil) iptal OLMAYAN sefer sayısı ve kaçının ASIL'ı olduğu / olmadığı. "BOŞ" tanımı gün listesindeki ({@code bosNedenleri}, atamacı "Atanmamış" sayacı) ile aynıdır:
     * iptal değil ∧ ASIL yok (yalnız yardımcı/kondüktör/yolcu olan sefer de boştur). Aralık sınırı {@code kirmiziSayisi} ile aynı. Salt okuma, iki sorgu.
     */
    public DolulukOzeti doluluk(LocalDate bas, LocalDate bit) {
        aralikDogrula(bas, bit);
        List<SeferDto> liste = seferler.aralikListesi(bas, bit).stream().filter(s -> s.durum() != SeferDurumu.IPTAL).toList();
        Map<Long, List<Atama>> atamaMap = atamalar(liste);
        Map<Boolean, Long> bolunmus = liste.stream().collect(Collectors.partitioningBy(s -> atamaMap.getOrDefault(s.id(), List.of()).stream().anyMatch(a -> a.rol() == AtamaRol.ASIL), Collectors.counting()));
        return new DolulukOzeti(liste.size(), bolunmus.get(true).intValue(), bolunmus.get(false).intValue());
    }

    private static void aralikDogrula(LocalDate bas, LocalDate bit) {
        if (bit.isBefore(bas)) {
            throw UygulamaHatasi.dogrulama("bit", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        if (ChronoUnit.DAYS.between(bas, bit) > EN_COK_ARALIK_GUN) {
            throw UygulamaHatasi.dogrulama("bit", "Aralık en çok " + EN_COK_ARALIK_GUN + " gün olabilir.");
        }
    }

    /**
     * Atamacı "gördüm" sayaçları (FR-M10): [bas, bit] planlanan gün; yalnız kalkışı geçmemiş atamalar ve serbest intikaller (OK-30). Hesapsız makinistler ayrı sayılır
     * (görülmeyene dahil değil). Sorgu sayısı atama / intikal sayısından bağımsız.
     */
    public GordumOzetiDto gordumOzeti(LocalDate bas, LocalDate bit) {
        aralikDogrula(bas, bit);
        List<SeferDto> liste = seferler.aralikListesi(bas, bit);
        Map<Long, List<Atama>> atamaMap = atamalar(liste);
        List<IntikalServisi.GordumSatiri> intikalSatirlari = intikaller.gordumSatirlari(bas, bit);
        GordumBaglami gordum = gordumBaglami(atamaMap, intikalSatirlari.stream().map(IntikalServisi.GordumSatiri::makinistId).toList());
        List<GordumBilgisi> bilgiler = new ArrayList<>();
        for (SeferDto s : liste) {
            for (Atama a : atamaMap.getOrDefault(s.id(), List.of())) {
                bilgiler.add(gordum.bilgi(a, s));
            }
        }
        intikalSatirlari.forEach(i -> bilgiler.add(gordum.bilgi(i.makinistId(), i.kalkis(), i::gorduZamani))); // OK-30: makinistin görmesi gereken intikal de sayılır
        int toplam = 0;
        int gorulen = 0;
        int gorulmeyen = 0;
        int hesapsiz = 0;
        int uyari = 0;
        for (GordumBilgisi g : bilgiler) {
            if (g == null) {
                continue;
            }
            toplam++;
            switch (g.durum()) {
                case GORDU -> gorulen++;
                case HESAP_YOK -> hesapsiz++;
                case GORMEDI -> {
                    gorulmeyen++;
                    if (g.uyari()) {
                        uyari++;
                    }
                }
            }
        }
        return new GordumOzetiDto(bas, bit, toplam, gorulen, gorulmeyen, hesapsiz, uyari);
    }

    /** Bir atama için atamacı görünürlüğü: GORDU / GORMEDI / HESAP_YOK; {@code uyari}: GORMEDI ve kalkışa uyarı eşiğinden AZ kala. */
    record GordumBilgisi(GunListesiDto.GorduDurumu durum, Instant zaman, boolean uyari) {
    }

    /** Gün listesi ve sayaç için tek seferlik yükleme: hesaplı makinist kümesi + onay satırları (iki sorgu), şimdi ve uyarı eşiği. */
    record GordumBaglami(Instant simdi, long uyariSaat, java.util.Set<Long> hesaplilar, Map<Long, GorevGordum> onaylar) {

        /**
         * null: kalkışı geçmiş sefer (bilgi yok). İptal sefer DAHİLDİR (OK-9): iptal bildirimi de tebliğ edilmeli (makinist boşuna gara gelmesin); makinist ekranında iptal "DEĞİŞTİ" olup yeniden
         * onay istediği için atamacı masasında da "görmedi" → "gördü" görünür.
         */
        GordumBilgisi bilgi(Atama a, SeferDto s) {
            return bilgi(a.makinistId(), s.guncelKalkis().toInstant(), () -> {
                GorevGordum onay = onaylar.get(a.id());
                return GorevGordum.durumu(onay, a, GorevOzeti.hesapla(a, s)) == GordumDurumu.GORULDU ? onay.zaman() : null;
            });
        }

        /** Atama ve intikal için ortak karar; {@code gorduZamani}: güncel özeti gördüğü an (görmediyse null), yalnız hesaplı ve kalkışı geçmemişse çağrılır. */
        GordumBilgisi bilgi(long makinistId, Instant kalkis, java.util.function.Supplier<Instant> gorduZamani) {
            if (!kalkis.isAfter(simdi)) {
                return null;
            }
            if (!hesaplilar.contains(makinistId)) {
                return new GordumBilgisi(GunListesiDto.GorduDurumu.HESAP_YOK, null, false);
            }
            Instant gordu = gorduZamani.get();
            if (gordu != null) {
                return new GordumBilgisi(GunListesiDto.GorduDurumu.GORDU, gordu, false);
            }
            return new GordumBilgisi(GunListesiDto.GorduDurumu.GORMEDI, null, Duration.between(simdi, kalkis).compareTo(Duration.ofHours(uyariSaat)) < 0);
        }
    }

    private GordumBaglami gordumBaglami(Map<Long, List<Atama>> atamaMap) {
        return gordumBaglami(atamaMap, List.of());
    }

    /** {@code ekMakinistler}: ataması olmayıp yalnız intikali olanlar (hesaplı kümeye girmeleri için). */
    private GordumBaglami gordumBaglami(Map<Long, List<Atama>> atamaMap, java.util.Collection<Long> ekMakinistler) {
        List<Atama> tum = atamaMap.values().stream().flatMap(List::stream).toList();
        Map<Long, GorevGordum> onaylar = tum.isEmpty() ? Map.of()
                : gordumlar.findByAtamaIdIn(tum.stream().map(Atama::id).toList()).stream().collect(Collectors.toMap(GorevGordum::atamaId, g -> g));
        return new GordumBaglami(saat.instant(), ayarlar.tamsayi(UYARI_SAAT), kullanicilar.makinistHesaplilari(Stream.concat(tum.stream().map(Atama::makinistId), ekMakinistler.stream()).collect(Collectors.toSet())),
                onaylar);
    }

    private Map<Long, List<Atama>> atamalar(List<SeferDto> liste) {
        return liste.isEmpty() ? Map.of()
                : depo.findBySeferIdIn(liste.stream().map(SeferDto::id).toList()).stream().collect(Collectors.groupingBy(Atama::seferId));
    }

    /** Aralıktaki (iki uç dahil, İstanbul günü) atanmış seferler: izin kaydından sonra atamacıya uyarı listesi (FR-01). */
    public List<IzinEtkisiDto> izinEtkisi(long makinistId, LocalDate bas, LocalDate bit) {
        makinistler.getir(makinistId); // 404
        if (bit.isBefore(bas)) {
            throw UygulamaHatasi.dogrulama("bit", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        Map<Long, String> adlar = tanimlar.tumAdlar();
        return satirKaynagi.aralik(List.of(makinistId), bas.minusDays(1), bit.plusDays(1)).stream()
                .filter(s -> !ZamanHesabi.gun(s.kalkis()).isAfter(bit) && !ZamanHesabi.gun(s.varis()).isBefore(bas))
                .sorted(Comparator.comparing(AtamaSatiri::kalkis))
                .map(s -> new IzinEtkisiDto(s.atamaId(), s.seferId(), s.tarih(), s.trenNo(), s.rol(), adlar.get(s.kalkisGarId()), adlar.get(s.varisGarId()),
                        s.kalkis().atZone(ISTANBUL).toOffsetDateTime(), s.varis().atZone(ISTANBUL).toOffsetDateTime()))
                .toList();
    }

}
