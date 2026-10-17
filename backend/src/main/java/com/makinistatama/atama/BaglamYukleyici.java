package com.makinistatama.atama;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.MakinistBilgisi;
import com.makinistatama.kural.NobetHesabi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.TrenTuru;
import com.makinistatama.tanim.TanimServisi;

/**
 * Kural bağlamını toplu yükler (MK-6): tek {@code atama ⨝ sefer} projeksiyonu + pencere öncesi son sürüş sorgusu; kurallar tamamen bellekte
 * çalışır (N+1 yok). {@code atama} satırı -> {@link Gorev} dönüşümü yalnız burada. Pencere sefer gününden geriye ve ileriye
 * {@code max(15, 7 × (gece.ard_hafta_max + 1))} gündür (K-18 ardışık haftalara iki yönde bakar); K-15 açıksa yıl başından başlar.
 * İki kullanım: tek aday sefer ({@link #yukle(SeferDto, List)}: aday panelleri, atama) ve gün aralığı ({@link #yukleAralik}: kırmızı atama
 * hesabı, uygun seferler, otomatik atama). F2b (MK-39/MK-40): YOLCU satırı ve YOLCU adayı yolculuk {@link Gorev}'i olarak dönüşür.
 */
@Component
@Transactional(readOnly = true)
class BaglamYukleyici {

    private final AtamaRepository atamalar;
    private final SatirKaynagi satirKaynagi;
    private final ParametreServisi parametreler;
    private final TanimServisi tanimlar;
    private final AtamaSaati saat;
    private final AyarServisi ayarlar;

    BaglamYukleyici(AtamaRepository atamalar, SatirKaynagi satirKaynagi, ParametreServisi parametreler, TanimServisi tanimlar, AtamaSaati saat, AyarServisi ayarlar) {
        this.atamalar = atamalar;
        this.satirKaynagi = satirKaynagi;
        this.parametreler = parametreler;
        this.tanimlar = tanimlar;
        this.saat = saat;
        this.ayarlar = ayarlar;
    }

    /** Yolculuk çalışma süresinden sayılır mı ({@code yolculuk.calisma_sayilir}, MK-39): TEK okuma yeri; {@code Yuk} yalnız YOLCU satırı/adayı görünce tembel okur. */
    boolean yolculukSayilir() {
        return ayarlar.tamsayi("yolculuk.calisma_sayilir") == 1;
    }

    /** Pencere uzunluğu (gün): {@code max(15, 7 × (gece.ard_hafta_max + 1), özel kuralların ihtiyacı)} (ay penceresi 30; MK-19). */
    static int pencereGun(Parametreler p) {
        return (int) Math.max(Math.max(15, 7 * (p.tamsayi("gece.ard_hafta_max") + 1)), p.ozelPencereGun());
    }

    /**
     * Yıl başından itibaren yükleme gerekir mi (MK-19): K-15 açık ya da yıl penceresi olan açık özel kural var. Bayrağın TEK tanımı:
     * {@link #yukleAralik} ve {@code OtomatikAtamaServisi} bunu kullanır.
     */
    static boolean yillik(Parametreler p) {
        return p.siddet("K-15") != Siddet.KAPALI || p.ozelYillik();
    }

    /** Güncel parametreler (özel kurallar dahil); yük hesaplamadan yalnız metin/etiket gerektiğinde. */
    Parametreler parametreler() {
        return parametreler.yukle();
    }

    /** {@code sefer} için, verilen makinistlerin değerlendirmesine yetecek bağlamı yükler. */
    Yuk yukle(SeferDto sefer, List<MakinistDto> makinistler) {
        return yukle(sefer, makinistler, AtamaRol.ASIL);
    }

    /** Aday sefer {@code rol} ile değerlendirilir: YOLCU ise aday bir yolculuk {@link Gorev}'idir (F2b, MK-40). */
    Yuk yukle(SeferDto sefer, List<MakinistDto> makinistler, AtamaRol rol) {
        return yukleAralik(sefer.tarih(), sefer.tarih(), makinistler).sefereBagla(sefer, rol);
    }

    /** [bas, bit] (planlanan gün, iki uç dahil) arasındaki seferlerin değerlendirmesine yetecek bağlamı yükler. */
    Yuk yukleAralik(LocalDate bas, LocalDate bit, List<MakinistDto> makinistler) {
        return yukleAralik(bas, bit, makinistler, null);
    }

    /** {@code verilen} boş değilse güncel parametreler yerine o kullanılır (MK-21: kaydedilmemiş taslak kuralla önizleme). */
    Yuk yukleAralik(LocalDate bas, LocalDate bit, List<MakinistDto> makinistler, Parametreler verilen) {
        Parametreler p = verilen != null ? verilen : parametreler.yukle();
        int pencere = pencereGun(p);
        boolean k15 = yillik(p);
        LocalDate gorevBas = Yuk.geriSinir(bas, pencere, k15);
        LocalDate gorevBit = ZamanHesabi.pencereSonu(bit, pencere, p.ozelYillik());
        LocalDate aralikBas = YearMonth.from(bas).atDay(1).isBefore(gorevBas) ? YearMonth.from(bas).atDay(1) : gorevBas;
        LocalDate aralikBit = YearMonth.from(bit).atEndOfMonth().isAfter(gorevBit) ? YearMonth.from(bit).atEndOfMonth() : gorevBit;

        List<Long> idler = makinistler.stream().map(MakinistDto::id).toList();
        List<AtamaSatiri> satirlar = satirKaynagi.aralik(idler, aralikBas, aralikBit);
        Map<Long, Map<Long, LocalDate>> hatSon = new HashMap<>();
        Map<Long, LocalDate> genelSon = new HashMap<>();
        if (!idler.isEmpty()) {
            for (OncekiSurus o : atamalar.pencereOncesiSonSurus(idler, aralikBas)) {
                LocalDate g = ZamanHesabi.gun(o.varis());
                hatSon.computeIfAbsent(o.makinistId(), k -> new HashMap<>()).put(o.hatId(), g);
                genelSon.merge(o.makinistId(), g, (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<Long, List<AtamaSatiri>> makinistSatirlari = satirlar.stream().collect(Collectors.groupingBy(AtamaSatiri::makinistId));
        return new Yuk(p, tanimlar.tumAdlar(), makinistSatirlari, hatSon, genelSon, pencere, k15, saat.simdi(), this::yolculukSayilir);
    }

    /** Bir yüklemenin sonucu: makinist başına {@link KuralBaglami} ve "bu ay" toplamı üretir. */
    static final class Yuk {

        final Parametreler parametreler;
        private final Map<Long, String> adlar;
        private final Map<Long, List<AtamaSatiri>> satirlar;
        private final Map<Long, Map<Long, LocalDate>> hatSon;
        private final Map<Long, LocalDate> genelSon;
        private final int pencere;
        private final boolean k15;
        private final Instant simdi;
        /** Anahtar yalnız atama kimliği değil: zincir bayrakları ve atama gecikmesi de görevi değiştirir (OK-17 satırı güncellenince eski görev dönmesin). {@code seferId} de girer (MK-105): takasın sentetik adayı {@code atamaId = -1} üretir, intikal {@code atamaId = null} + negatif {@code seferId}; iki farklı görev aynı girdiye düşmesin. */
        private record GorevAnahtari(Long atamaId, Long seferId, boolean devamEden, boolean devamEdecek, int atamaGecikmeDk) {
        }

        private final Map<GorevAnahtari, Gorev> gorevOnbellek = new HashMap<>();
        private final BooleanSupplier yolculukSayilirKaynagi;
        private Boolean yolculukSayilir;
        /** Yalnız tek-sefer kullanımında dolu: aday sefer ve adayın yolculuk (rol YOLCU) olup olmadığı. */
        private SeferDto sefer;
        private boolean adayYolculuk;

        Yuk(Parametreler parametreler, Map<Long, String> adlar, Map<Long, List<AtamaSatiri>> satirlar, Map<Long, Map<Long, LocalDate>> hatSon,
                Map<Long, LocalDate> genelSon, int pencere, boolean k15, Instant simdi, BooleanSupplier yolculukSayilirKaynagi) {
            this.parametreler = parametreler;
            this.adlar = adlar;
            this.satirlar = satirlar;
            this.hatSon = hatSon;
            this.genelSon = genelSon;
            this.pencere = pencere;
            this.k15 = k15;
            this.simdi = simdi;
            this.yolculukSayilirKaynagi = yolculukSayilirKaynagi;
        }

        Yuk sefereBagla(SeferDto aday) {
            return sefereBagla(aday, AtamaRol.ASIL);
        }

        /** Aday seferin rolü: YOLCU ise {@link #adayGorev()} yolculuk {@link Gorev}'idir (F2b, MK-40). */
        Yuk sefereBagla(SeferDto aday, AtamaRol rol) {
            this.sefer = aday;
            this.adayYolculuk = rol == AtamaRol.YOLCU;
            sonrakiParcayiGuncelle(aday);
            return this;
        }

        /**
         * OK-17, bağ henüz kurulmamışken: aday parçalıysa, aynı zincirin SONRAKİ parçası bir makinistte kayıtlıyken (o atama sorgusunda önceki parça aday olmadığından donuk
         * başlangıçlı), aday o makinistte kurulacağı için o satır güncel kalkışla görülür; yoksa parça 1'e gecikme sonrası atama sahte K-10 verir. Yalnız bağlı yükleme belleği değişir.
         */
        private void sonrakiParcayiGuncelle(SeferDto aday) {
            if (aday.parcaNo() == null || aday.trenGunu() == null) {
                return;
            }
            for (List<AtamaSatiri> liste : satirlar.values()) {
                liste.replaceAll(s -> {
                    if (!ayniZincir(s, aday)) {
                        return s;
                    }
                    if (s.parcaNo() == aday.parcaNo() + 1) { // aday bu makinistte kurulursa: bu satır devam eder (güncel kalkış, hazırlık yok)
                        return (s.atamaGecikmeDk() != s.kalkisGecikmeDk() ? s.atamaGecikmeyle(s.kalkisGecikmeDk()) : s).zincirle(true, s.devamEdecek());
                    }
                    return s.parcaNo() == aday.parcaNo() - 1 ? s.zincirle(s.devamEden(), true) : s; // bu satırdan sonra aday gelir: alıkoyma yok
                });
            }
        }

        /**
         * Takas (W2): makinist {@code birakilan} seferi bırakırsa onu yüklenen satırlardan çıkarır ve zincirin bitişik parçalarını OK-17'nin tersine çevirir: sonraki parça artık devam etmez
         * (hazırlık geri gelir, başlangıç atamanın kendi kayıtlı gecikmesine döner), önceki parça artık devam ettirilmez (alıkoyma geri gelir). Bağlı yükleme belleği değişir.
         */
        void seferiBirak(long makinistId, SeferDto birakilan, java.util.function.LongToIntFunction atamaGecikmesi) {
            List<AtamaSatiri> liste = satirlar.get(makinistId);
            if (liste == null) {
                return;
            }
            liste.removeIf(s -> s.seferId().equals(birakilan.id()));
            if (birakilan.parcaNo() == null || birakilan.trenGunu() == null) {
                return;
            }
            liste.replaceAll(s -> {
                if (!ayniZincir(s, birakilan)) {
                    return s;
                }
                if (s.parcaNo() == birakilan.parcaNo() + 1) {
                    return s.atamaGecikmeyle(atamaGecikmesi.applyAsInt(s.atamaId())).zincirle(false, s.devamEdecek());
                }
                return s.parcaNo() == birakilan.parcaNo() - 1 ? s.zincirle(s.devamEden(), false) : s;
            });
        }

        private static boolean ayniZincir(AtamaSatiri s, SeferDto aday) {
            return s.parcaNo() != null && aday.trenGunu().equals(s.trenGunu()) && s.trenNo().equals(aday.trenNo());
        }

        /** Aday görev, {@code makinistId} için: makinist zincirin komşu parçasını zaten (ya da aday olarak) taşıyorsa hazırlık / alıkoyma eklenmez. */
        Gorev adayGorev(long makinistId) {
            if (adayYolculuk || sefer.parcaNo() == null || sefer.trenGunu() == null) {
                return adayGorev();
            }
            boolean onceki = false;
            boolean sonraki = false;
            for (AtamaSatiri s : satirlar.getOrDefault(makinistId, List.of())) {
                if (ayniZincir(s, sefer)) {
                    onceki |= s.parcaNo() == sefer.parcaNo() - 1;
                    sonraki |= s.parcaNo() == sefer.parcaNo() + 1;
                }
            }
            if (!onceki && !sonraki) {
                return adayGorev();
            }
            Instant baslangicKalkisi = sefer.planlananKalkis().toInstant().plusSeconds(60L * sefer.kalkisGecikmeDk());
            return gorev(sefer.id(), sefer.kalkisGarId(), sefer.varisGarId(), sefer.hatId(), sefer.aracTipiId(), baslangicKalkisi, sefer.guncelKalkis().toInstant(),
                    sefer.guncelVaris().toInstant(), sefer.km(), sefer.trenTuru(), !onceki, !sonraki);
        }

        /** {@code yolculuk.calisma_sayilir} (MK-39): yalnız bir yolculuk görevi kurulurken, yükleme başına en çok bir kez okunur (YOLCU yokken ek sorgu yok). */
        private boolean yolculukSayilir() {
            if (yolculukSayilir == null) {
                yolculukSayilir = yolculukSayilirKaynagi.getAsBoolean();
            }
            return yolculukSayilir;
        }

        /** Gün için geri pencere sınırı: {@code gün - pencere}; K-15 açıksa en geç yıl başından (Pazar-yılı kuralı: −7 gün) başlar. */
        static LocalDate geriSinir(LocalDate gun, int pencere, boolean k15) {
            return ZamanHesabi.pencereBasi(gun, pencere, k15);
        }

        Parametreler parametreler() {
            return parametreler;
        }

        Instant simdi() {
            return simdi;
        }

        // ---- tek aday sefer

        /** Aday görev: seferin güncel saatleri + hazırlık/alıkoyma (yolculuk adayında hazırlık/alıkoyma yok). */
        Gorev adayGorev() {
            return gorev(sefer, sefer.kalkisGecikmeDk(), adayYolculuk);
        }

        /** Makinistin aday seferin dışındaki görevleri (kronolojik). */
        List<Gorev> digerGorevler(long makinistId) {
            return komsular(makinistId, sefer.tarih(), sefer.id(), null);
        }

        KuralBaglami baglam(MakinistDto m) {
            return baglam(m, adayGorev(m.id()), digerGorevler(m.id()), simdi);
        }

        /** Makinistin bu seferdeki rolü (varsa; başka rolde zaten görevli). */
        AtamaRol buSeferdekiRol(long makinistId) {
            return satirlar.getOrDefault(makinistId, List.of()).stream().filter(s -> s.seferId().equals(sefer.id())).map(AtamaSatiri::rol).findFirst()
                    .orElse(null);
        }

        /** "Bu ay" (planlanan kalkış ayı) toplam sefer saati ve km; aday sefer dahil değil (henüz atanmadı). */
        double[] buAy(long makinistId) {
            return buAy(makinistId, YearMonth.from(sefer.tarih()), sefer.id());
        }

        // ---- genel (aralık) kullanım

        /** Henüz atanmamış (aday) sefer: atama şimdi yapılacağı için gecikme dahil, başlangıç güncel kalkıştır (OK-6). */
        Gorev gorev(SeferDto s) {
            return gorev(s, s.kalkisGecikmeDk());
        }

        /**
         * Doldurulacak seferleri çekirdek girdisine çevirir ({@code gorev(s)}: atama şimdi yapılacak); aynı trenin bitişik parçası da listedeyse sefer girdisi onun kimliğini taşır (W1:
         * aynı çalışmada aynı makinistte ikisi birden yerleşirse aralarında hazırlık / alıkoyma yok). TEK yer: otomatik atama ve kadro simülasyonu.
         */
        List<OtomatikAtama.SeferGirdisi> seferGirdileri(List<SeferDto> seferler) {
            Map<String, Long> dizin = new HashMap<>();
            seferler.stream().filter(s -> s.parcaNo() != null && s.trenGunu() != null).forEach(s -> dizin.put(s.trenNo() + "|" + s.trenGunu() + "|" + s.parcaNo(), s.id()));
            return seferler.stream().map(s -> {
                boolean parcali = s.parcaNo() != null && s.trenGunu() != null;
                return new OtomatikAtama.SeferGirdisi(s.id(), s.tarih(), YearMonth.from(s.tarih()), gorev(s), parcali ? dizin.get(s.trenNo() + "|" + s.trenGunu() + "|" + (s.parcaNo() - 1)) : null,
                        parcali ? dizin.get(s.trenNo() + "|" + s.trenGunu() + "|" + (s.parcaNo() + 1)) : null);
            }).toList();
        }

        /** Sefer, atamanın yapıldığı andaki kalkış gecikmesiyle (OK-6; {@code Atama.kalkisGecikmeDk}). */
        Gorev gorev(SeferDto s, int atamaGecikmeDk) {
            return gorev(s, atamaGecikmeDk, false);
        }

        /** {@code yolculuk=true}: aynı sefer, yolculuk görevi olarak (hazırlık/alıkoyma yok, km 0; MK-39). */
        Gorev gorev(SeferDto s, int atamaGecikmeDk, boolean yolculuk) {
            Instant baslangicKalkisi = s.planlananKalkis().toInstant().plusSeconds(60L * atamaGecikmeDk);
            if (yolculuk) {
                return Gorev.yolculukOlustur(s.id(), s.kalkisGarId(), s.varisGarId(), s.hatId(), s.aracTipiId(), baslangicKalkisi, s.guncelKalkis().toInstant(),
                        s.guncelVaris().toInstant(), yolculukSayilir());
            }
            return gorev(s.id(), s.kalkisGarId(), s.varisGarId(), s.hatId(), s.aracTipiId(), baslangicKalkisi, s.guncelKalkis().toInstant(), s.guncelVaris().toInstant(),
                    s.km(), s.trenTuru());
        }

        /** Yolculuk adayı görev (T4/T5): henüz atanmamış, atama şimdi yapılacağı için gecikme dahil. */
        Gorev yolculukGorevi(SeferDto s) {
            return gorev(s, s.kalkisGecikmeDk(), true);
        }

        Gorev gorev(AtamaSatiri s) {
            return gorevOnbellek.computeIfAbsent(new GorevAnahtari(s.atamaId(), s.seferId(), s.devamEden(), s.devamEdecek(), s.atamaGecikmeDk()), k -> gorevHesapla(s));
        }

        private Gorev gorevHesapla(AtamaSatiri s) {
            if (s.yolculuk()) {
                return Gorev.yolculukOlustur(s.seferId(), s.kalkisGarId(), s.varisGarId(), s.hatId(), s.aracTipiId(), s.baslangicKalkisi(), s.kalkis(), s.varis(), yolculukSayilir());
            }
            return gorev(s.seferId(), s.kalkisGarId(), s.varisGarId(), s.hatId(), s.aracTipiId(), s.baslangicKalkisi(), s.kalkis(), s.varis(), s.km(), s.trenTuru(), !s.devamEden(),
                    !s.devamEdecek());
        }

        // ---- OK-17 zincir komşusu: aday henüz atanmamışken makinist zincirin bitişik parçasını taşıyorsa (TEK kaynak: uygun-seferler, gün listesi boş nedeni, otomatik atama)

        /**
         * Aday sefer {@code c} makinistte kurulursa: aday görev (taşınan komşu yönünde hazırlık / alıkoyma yok, güncel kalkış) ve komşu parçaların YENİ görevleri (komşunun
         * başlangıcı güncel kalkış, bayrakları güncel). Makinist zincirin bitişik parçasını taşımıyorsa ya da sefer parçalı değilse {@code null}.
         */
        OtomatikAtama.ZincirVaryanti varyant(SeferDto c, long makinistId) {
            if (c.parcaNo() == null || c.trenGunu() == null) {
                return null;
            }
            boolean onceki = false;
            boolean sonraki = false;
            List<Gorev> komsular = new ArrayList<>();
            for (AtamaSatiri s : satirlar.getOrDefault(makinistId, List.of())) {
                if (!ayniZincir(s, c) || s.seferId().equals(c.id())) {
                    continue;
                }
                if (s.parcaNo() == c.parcaNo() + 1) { // aday kurulursa komşu devam eder (güncel kalkış, hazırlık yok)
                    sonraki = true;
                    komsular.add(gorev((s.atamaGecikmeDk() != s.kalkisGecikmeDk() ? s.atamaGecikmeyle(s.kalkisGecikmeDk()) : s).zincirle(true, s.devamEdecek())));
                } else if (s.parcaNo() == c.parcaNo() - 1) { // komşudan sonra aday gelir: komşuda alıkoyma yok
                    onceki = true;
                    komsular.add(gorev(s.zincirle(s.devamEden(), true)));
                }
            }
            if (!onceki && !sonraki) {
                return null;
            }
            Instant baslangicKalkisi = c.planlananKalkis().toInstant().plusSeconds(60L * c.kalkisGecikmeDk());
            Gorev aday = gorev(c.id(), c.kalkisGarId(), c.varisGarId(), c.hatId(), c.aracTipiId(), baslangicKalkisi, c.guncelKalkis().toInstant(), c.guncelVaris().toInstant(), c.km(),
                    c.trenTuru(), !onceki, !sonraki);
            return new OtomatikAtama.ZincirVaryanti(aday, komsular);
        }

        /** {@code adaylar} içinde parçalı olanlar için (makinist -> sefer -> varyant); yalnız komşu parçayı taşıyan makinistler girer (toplu: otomatik atama). */
        Map<Long, Map<Long, OtomatikAtama.ZincirVaryanti>> zincirVaryantlari(List<SeferDto> adaylar) {
            Map<String, List<Long>> dizin = new HashMap<>(); // tren no + tren günü -> parça taşıyan makinistler
            satirlar.forEach((mid, liste) -> {
                for (AtamaSatiri s : liste) {
                    if (s.parcaNo() != null && s.trenGunu() != null) {
                        dizin.computeIfAbsent(s.trenNo() + "|" + s.trenGunu(), x -> new ArrayList<>()).add(mid);
                    }
                }
            });
            Map<Long, Map<Long, OtomatikAtama.ZincirVaryanti>> sonuc = new HashMap<>();
            for (SeferDto c : adaylar) {
                if (c.parcaNo() == null || c.trenGunu() == null) {
                    continue;
                }
                for (Long mid : new java.util.TreeSet<>(dizin.getOrDefault(c.trenNo() + "|" + c.trenGunu(), List.of()))) {
                    OtomatikAtama.ZincirVaryanti v = varyant(c, mid);
                    if (v != null) {
                        sonuc.computeIfAbsent(mid, x -> new HashMap<>()).put(c.id(), v);
                    }
                }
            }
            return sonuc;
        }

        /** Aralık kullanımı (uygun-seferler, gün listesi boş nedeni): {@code s} makinist {@code m}'e atanırsa kural bağlamı; komşu parçalar OK-17'ye göre güncel. */
        KuralBaglami baglamZincirli(MakinistDto m, SeferDto s) {
            List<Gorev> diger = komsular(m.id(), s.tarih(), s.id(), null);
            OtomatikAtama.ZincirVaryanti v = varyant(s, m.id());
            if (v == null) {
                return baglam(m, gorev(s), diger, simdi);
            }
            Map<Long, Gorev> yeni = v.komsular().stream().collect(Collectors.toMap(Gorev::seferId, g -> g));
            List<Gorev> degisik = new ArrayList<>(diger.stream().map(g -> yeni.getOrDefault(g.seferId(), g)).toList());
            degisik.sort(Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId));
            return baglam(m, v.aday(), degisik, simdi);
        }

        /**
         * Takas: aday {@code kume}ye eklenirse onun zincir komşusu olan satırların bayrakları güncellenir ({@code once}/{@code sonra} kümelerinde aday satırı da aynı
         * bayraklarla kurulsun diye aday için {devamEden, devamEdecek} döner).
         */
        static boolean[] komsuBayraklari(List<AtamaSatiri> kume, SeferDto aday) {
            boolean onceki = false;
            boolean sonraki = false;
            if (aday.parcaNo() != null && aday.trenGunu() != null) {
                for (AtamaSatiri s : kume) {
                    if (ayniZincir(s, aday) && !s.seferId().equals(aday.id())) {
                        onceki |= s.parcaNo() == aday.parcaNo() - 1;
                        sonraki |= s.parcaNo() == aday.parcaNo() + 1;
                    }
                }
            }
            return new boolean[] { onceki, sonraki };
        }

        private Gorev gorev(long seferId, long kalkisGar, long varisGar, long hat, long aracTipi, Instant baslangicKalkisi, Instant kalkis, Instant varis,
                BigDecimal km, TrenTuru trenTuru) {
            return gorev(seferId, kalkisGar, varisGar, hat, aracTipi, baslangicKalkisi, kalkis, varis, km, trenTuru, true, true);
        }

        private Gorev gorev(long seferId, long kalkisGar, long varisGar, long hat, long aracTipi, Instant baslangicKalkisi, Instant kalkis, Instant varis,
                BigDecimal km, TrenTuru trenTuru, boolean hazirlik, boolean alikoyma) {
            return Gorev.olustur(seferId, kalkisGar, varisGar, hat, aracTipi, baslangicKalkisi, kalkis, varis, km == null ? 0 : km.doubleValue(),
                    trenTuru == null ? null : trenTuru.name(), parametreler, hazirlik, alikoyma);
        }

        /**
         * Makinistin {@code gun} etrafındaki pencerede (geri sınır K-15'e göre yıl başına uzar) kayıtlı görevleri, kronolojik. {@code haricSefer} verilirse o
         * sefer çıkarılır; {@code oncesi} verilirse (MK-5: kırmızı atama kronolojik sorumluluk) yalnız ondan ÖNCE başlayanlar ve onunla AYNI NÖBETTE olan sonraki
         * görevler kalır: nöbet birleşince gece/gündüz sınıfı ve toplamlar değişir, yazma anı değerlendirmesi de nöbeti böyle görür (BUG-14). Nöbeti ayıran
         * (arası {@code nobet.ayirma_saat} ve üstü) sonraki görevler yine sorumlu sayılmaz.
         */
        List<Gorev> komsular(long makinistId, LocalDate gun, Long haricSefer, Gorev oncesi) {
            return komsular(satirlar.getOrDefault(makinistId, List.of()), gun, haricSefer, oncesi);
        }

        /** Aynı hesap, makinistin yüklenen satırları yerine verilen görev kümesiyle (takas sonrası varsayımsal küme; W2). */
        List<Gorev> komsular(List<AtamaSatiri> kume, LocalDate gun, Long haricSefer, Gorev oncesi) {
            LocalDate bas = geriSinir(gun, pencere, k15);
            LocalDate bit = ZamanHesabi.pencereSonu(gun, pencere, parametreler.ozelYillik());
            Comparator<Gorev> sira = Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId);
            List<Gorev> hepsi = kume.stream()
                    .filter(s -> haricSefer == null || !s.seferId().equals(haricSefer))
                    .filter(s -> !s.tarih().isBefore(bas) && !s.tarih().isAfter(bit))
                    .map(this::gorev)
                    .sorted(sira)
                    .toList();
            if (oncesi == null) {
                return hepsi;
            }
            return NobetHesabi.sorumluKomsular(hepsi, oncesi, parametreler.dakika("nobet.ayirma_saat"));
        }

        KuralBaglami baglam(MakinistDto m, Gorev aday, List<Gorev> diger, Instant simdi) {
            return new KuralBaglami(aday, diger, bilgi(m), hatSon.getOrDefault(m.id(), Map.of()), genelSon.get(m.id()), adlar, parametreler, simdi);
        }

        /** Yüklenen satırlardan, makinistin belirtilen ayındaki (planlanan kalkış ayı) toplam saat ve km; {@code haricSefer} sayılmaz. Yolculukta km 0 (MK-40). */
        double[] buAy(long makinistId, YearMonth ay, Long haricSefer) {
            double saat = 0;
            double km = 0;
            for (AtamaSatiri s : satirlar.getOrDefault(makinistId, List.of())) {
                if (s.seferId().equals(haricSefer) || !YearMonth.from(s.tarih()).equals(ay)) {
                    continue;
                }
                saat += gorev(s).sureDakika() / 60.0;
                km += s.yolculuk() || s.km() == null ? 0 : s.km().doubleValue();
            }
            return new double[] { saat, km };
        }

        /** Makinistin yüklenen tüm satırları (pencere süzgeci olmadan). */
        List<AtamaSatiri> satirlar(long makinistId) {
            return satirlar.getOrDefault(makinistId, List.of());
        }

        Map<Long, String> adlar() {
            return adlar;
        }

        Map<Long, LocalDate> hatSon(long makinistId) {
            return hatSon.getOrDefault(makinistId, Map.of());
        }

        LocalDate genelSon(long makinistId) {
            return genelSon.get(makinistId);
        }

        static MakinistBilgisi bilgi(MakinistDto m) {
            Map<Long, LocalDate> arac = m.broveAracTipleri().stream()
                    .collect(Collectors.toMap(MakinistDto.BroveAracTipiDto::aracTipiId, MakinistDto.BroveAracTipiDto::sinavTarihi));
            Map<Long, MakinistBilgisi.HatBrove> hat = m.broveHatlar().stream()
                    .collect(Collectors.toMap(MakinistDto.BroveHatDto::hatId, h -> new MakinistBilgisi.HatBrove(h.sinavTarihi(), h.sonSurus())));
            List<MakinistBilgisi.Izin> izin = m.izinler().stream()
                    .map(i -> new MakinistBilgisi.Izin(i.baslangic(), i.bitis(), MakinistBilgisi.IzinDurumu.valueOf(i.durum().name()))).toList();
            return new MakinistBilgisi(m.id(), m.bagliGarId(), MakinistBilgisi.Durum.valueOf(m.durum().name()), m.ehliyetBitis(), m.saglikBitis(),
                    m.psikoteknikBitis(), arac, hat, m.sonSurus(), izin, MakinistBilgisi.Tur.valueOf(m.tur().name()));
        }
    }
}
