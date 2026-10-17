package com.makinistatama.atama;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.sefer.TrenTuru;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kural kontrollü manuel atama (FR-04). ENGEL'li atama hiçbir yoldan yazılamaz: yazma anında makinist satırı kilitlenir (MK-7), kurallar
 * kilit ALTINDA yeniden değerlendirilir (aday TÜM komşu görevlerle iki yönlü), ENGEL varsa 422 {@code KURAL_IHLALI}. UYARI varsa gerekçe zorunlu.
 * Her yazım aynı işlemde kayıt üretir; atama kaydına atama anı kural anlık görüntüsü (JSON) eklenir. Ayrıca: "yerine ata" önerisi, boşta makinist için
 * uygun seferler (FR-07) ve rapor/makinist okumaları. Gün listesi/kırmızı sayısı/gördüm/izin etkisi {@link GunListesiServisi}'nde, takasın atama tarafı
 * {@link AtamaTakasServisi}'nde (MK-47).
 */
@Service
@Transactional(readOnly = true)
public class AtamaServisi {

    private static final ZoneId ISTANBUL = ZamanAyari.BOLGE;
    private static final int EN_COK_ONERI = 3;

    private final AtamaRepository depo;
    private final SatirKaynagi satirKaynagi;
    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final BaglamYukleyici yukleyici;
    private final KirmiziEtkisi kirmiziEtkisi;
    private final DegisiklikServisi degisiklik;
    private final GorevGordumRepository gordumlar;
    private final AtamaOlayiRepository olaylar;
    private final AyarServisi ayarlar;
    private final Clock saat;
    private final JsonMapper json;

    AtamaServisi(AtamaRepository depo, SatirKaynagi satirKaynagi, SeferServisi seferler, MakinistServisi makinistler, BaglamYukleyici yukleyici,
            KirmiziEtkisi kirmiziEtkisi, DegisiklikServisi degisiklik, GorevGordumRepository gordumlar, AtamaOlayiRepository olaylar,
            AyarServisi ayarlar, Clock saat, JsonMapper json) {
        this.depo = depo;
        this.satirKaynagi = satirKaynagi;
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.yukleyici = yukleyici;
        this.kirmiziEtkisi = kirmiziEtkisi;
        this.degisiklik = degisiklik;
        this.gordumlar = gordumlar;
        this.olaylar = olaylar;
        this.ayarlar = ayarlar;
        this.saat = saat;
        this.json = json;
    }

    /** {@code ata} sonucu: yeni atama mı (201) yoksa o roldeki makinist değiştirildi mi (200). */
    public record AtamaSonucu(AtamaDto atama, boolean yeni) {
    }

    // ------------------------------------------------------------------ adaylar

    /**
     * Rolün türündeki tüm AKTIF personel (ASIL/YARDIMCI: makinist, KONDUKTOR: kondüktör, YOLCU: ikisi), hepsinin ihlal listesiyle. Sıra: ENGEL'siz olanlar önce; kendi içinde bu ay saat, bu ay km, sicil.
     * ASIL ve YARDIMCI için kurallar aynıdır; YOLCU (F2b, MK-38) için aday bir yolculuk görevidir (belge/yetki sorulmaz, konum seferin kalkış garına göre).
     */
    public List<AdayDto> adaylar(long seferId, AtamaRol rol) {
        SeferDto sefer = atanabilirSefer(seferId, rol);
        List<MakinistDto> aktif = makinistler.aktifDetaylar(rol.turler()); // MK-86: havuz rolün türüyle sınırlı (YOLCU: tümü)
        Yuk y = yukleyici.yukle(sefer, aktif, rol);
        return aktif.stream().map(m -> {
            List<Ihlal> ihlaller = KuralMotoru.degerlendirYazma(y.baglam(m));
            double[] buAy = y.buAy(m.id());
            return new AdayDto(m.id(), m.sicil(), m.adSoyad(), m.bagliGar(), !KuralMotoru.engelVarMi(ihlaller),
                    ihlaller.stream().map(IhlalDto::of).toList(), yuvarla(buAy[0]), yuvarla(buAy[1]), y.buSeferdekiRol(m.id()));
        }).sorted(Comparator.comparing((AdayDto a) -> !a.uygun()).thenComparingDouble(AdayDto::buAySaat).thenComparingDouble(AdayDto::buAyKm)
                .thenComparing(AdayDto::sicil)).toList();
    }

    /** MK-86: rol-tür kapısı; kilit alındıktan sonra, kural değerlendirmesinden önce. */
    private static void turKapisi(AtamaRol rol, MakinistDto m) {
        if (!rol.turler().contains(m.tur())) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "PERSONEL_TURU_UYMUYOR",
                    m.sicil() + " bu role atanamaz: " + (rol == AtamaRol.KONDUKTOR ? "kondüktör rolüne yalnız kondüktör" : "makinist rolüne yalnız makinist") + " atanabilir.", null);
        }
    }

    /** İki ondalık: sunucu kayan nokta gürültüsünü (1.4666666666666666) arayüze sızdırmaz. */
    private static double yuvarla(double d) {
        return Math.round(d * 100) / 100.0;
    }

    // ------------------------------------------------------------------ yerine ata (FR-07)

    /**
     * Mevcut makinist hariç ilk 3 ENGEL'siz aday: UYARI'sız olanlar önce, sonra bu ay saat, km, sicil. Her öneri gerekçesiyle gelir. Atama yine {@code ata}
     * yolundan (neden zorunlu, kurallar yeniden denetlenir) yapılır; öneri dışı makinist de seçilebilir ama ENGEL ise 422.
     */
    public OneriDto oner(long seferId) {
        return oner(seferId, AtamaRol.ASIL);
    }

    /** Rolün türüne göre (MK-86): kondüktör yuvası için kondüktör, makinist yuvaları için makinist önerir. */
    public OneriDto oner(long seferId, AtamaRol rol) {
        List<AdayDto> hepsi = adaylar(seferId, rol);
        List<AdayDto> uygun = hepsi.stream().filter(a -> a.uygun() && a.buSeferdeRol() == null)
                .sorted(Comparator.comparing((AdayDto a) -> !a.ihlaller().isEmpty()).thenComparingDouble(AdayDto::buAySaat).thenComparingDouble(AdayDto::buAyKm)
                        .thenComparing(AdayDto::sicil))
                .limit(EN_COK_ONERI).toList();
        if (uygun.isEmpty()) {
            List<String> ilkKodlar = NedenOzeti.birincil(hepsi.stream().filter(a -> a.buSeferdeRol() == null && !a.ihlaller().isEmpty())
                    .map(a -> a.ihlaller().stream().filter(i -> i.siddet() == Siddet.ENGEL).map(i -> new Ihlal(i.kod(), i.siddet(), 0, 0, "", i.metin())).toList()).toList());
            int toplam = (int) hepsi.stream().filter(a -> a.buSeferdeRol() == null).count();
            return new OneriDto(List.of(), true, "Uygun aday yok. " + NedenOzeti.ozet(toplam, ilkKodlar, yukleyici.parametreler()));
        }
        List<OneriDto.Oneri> oneriler = uygun.stream().map(a -> new OneriDto.Oneri(a.makinistId(), a.sicil(), a.adSoyad(), a.bagliGar(), a.buAySaat(),
                a.buAyKm(), a.ihlaller(), oneriGerekcesi(a))).toList();
        return new OneriDto(oneriler, false, null);
    }

    private static String oneriGerekcesi(AdayDto a) {
        String temel = "Bu ay " + sayi(a.buAySaat()) + " saat, " + sayi(a.buAyKm()) + " km";
        return a.ihlaller().isEmpty() ? temel + "; kural ihlali yok"
                : temel + "; uyarı: " + a.ihlaller().stream().map(IhlalDto::metin).collect(Collectors.joining(" | "));
    }

    private static String sayi(double d) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR"))).format(d);
    }

    // ------------------------------------------------------------------ boşta makinist (FR-07, T10.4)

    /** Günün, ASIL'ı olmayan ve iptal edilmemiş seferlerinden makinistin ENGEL'siz atanabileceği olanlar (UYARI'lar listelenir). */
    public List<UygunSeferDto> uygunSeferler(long makinistId, LocalDate gun) {
        MakinistDto m = makinistler.detaylar(List.of(makinistId)).stream().findFirst().orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        if (m.durum() != MakinistDurum.AKTIF || m.tur() != PersonelTuru.MAKINIST) { // ASIL yuvaları makinist içindir (F3b, MK-86)
            return List.of();
        }
        List<SeferDto> gunun = seferler.gunListesi(gun).stream().filter(s -> s.durum() != SeferDurumu.IPTAL).toList();
        if (gunun.isEmpty()) {
            return List.of();
        }
        Set<Long> asilVar = depo.findBySeferIdIn(gunun.stream().map(SeferDto::id).toList()).stream().filter(a -> a.rol() == AtamaRol.ASIL)
                .map(Atama::seferId).collect(Collectors.toSet());
        Yuk y = yukleyici.yukleAralik(gun, gun, List.of(m));
        Set<Long> kendiSeferleri = y.satirlar(m.id()).stream().map(AtamaSatiri::seferId).collect(Collectors.toSet());
        List<UygunSeferDto> sonuc = new ArrayList<>();
        for (SeferDto s : gunun) {
            if (asilVar.contains(s.id()) || kendiSeferleri.contains(s.id())) {
                continue;
            }
            List<Ihlal> ihlaller = KuralMotoru.degerlendirYazma(y.baglamZincirli(m, s));
            if (!KuralMotoru.engelVarMi(ihlaller)) {
                sonuc.add(new UygunSeferDto(s.id(), s.trenNo(), s.kalkisGar(), s.varisGar(), s.guncelKalkis(), s.guncelVaris(), s.aracTipi(), s.hat(),
                        ihlaller.stream().map(IhlalDto::of).toList()));
            }
        }
        return sonuc;
    }

    // ------------------------------------------------------------------ ata / değiştir / kaldır

    @Transactional
    public AtamaSonucu ata(long seferId, AtamaIstegi istek) {
        AtamaRol rol = istek.rol() == null ? AtamaRol.ASIL : istek.rol();
        SeferDto sefer = atanabilirSefer(seferId, rol);
        long makinistId = istek.makinistId();

        // MK-7: makinist bazında serileştirme; "değiştir"de yalnız YENİ makinist kilitlenir (eskiden görev çıkarmak ihlal doğurmaz)
        makinistler.kilitle(List.of(makinistId));
        MakinistDto m = makinistler.detaylar(List.of(makinistId)).getFirst();
        turKapisi(rol, m);

        // YOLCU her zaman ekleme: bir seferde birden çok olabilir, "değiştir" yoktur (MK-38); findBySeferIdAndRol YOLCU ile çağrılmaz
        Atama mevcut = rol == AtamaRol.YOLCU ? null : depo.findBySeferIdAndRol(seferId, rol).orElse(null);
        if (mevcut != null && mevcut.makinistId() == makinistId) {
            throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", "Bu makinist bu seferde zaten bu rolde görevli.");
        }
        if (depo.findBySeferIdAndMakinistId(seferId, makinistId).isPresent()) {
            throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", "Bu makinist bu seferde zaten başka bir rolde görevli.");
        }
        if (mevcut != null) {
            DegisiklikServisi.nedenDogrula(istek.neden(), istek.nedenNotu());
        }

        Yuk y = yukleyici.yukle(sefer, List.of(m), rol);
        List<Ihlal> ihlaller = KuralMotoru.degerlendirYazma(y.baglam(m));
        if (KuralMotoru.engelVarMi(ihlaller)) {
            List<IhlalDto> engeller = ihlaller.stream().filter(i -> i.siddet() == Siddet.ENGEL).map(IhlalDto::of).toList();
            // başlık kısa: nedenler yanıtın ihlaller[] alanında (ekranda iki kez görünmesin)
            throw UygulamaHatasi.kuralIhlali("Kural ihlali: " + m.sicil() + " bu sefere atanamaz.", engeller);
        }
        String gerekce = istek.gerekce() == null || istek.gerekce().isBlank() ? null : istek.gerekce().strip();
        if (!ihlaller.isEmpty() && gerekce == null) {
            throw UygulamaHatasi.dogrulama("gerekce", "Kural uyarısı var; atama için gerekçe yazılmalı.");
        }
        List<IhlalDto> uyarilar = ihlaller.stream().map(IhlalDto::of).toList();

        String eskiSicil = null;
        if (mevcut != null) {
            eskiSicil = makinistler.getir(mevcut.makinistId()).sicil();
            depo.delete(mevcut);
            depo.flush();
            gordumlar.atamalariSilinenleriTemizle(List.of(mevcut.id()));
            olayYaz(mevcut, sefer, AtamaOlayi.Tur.DEGISTI_BASKASINA, istek.neden()); // FR-M09: eski makinistin ekranında "Başkasına verildi" kalemi (aynı işlem)
        }
        Atama yeni;
        try {
            yeni = depo.saveAndFlush(new Atama(seferId, makinistId, rol, DegisiklikServisi.simdikiKullaniciAdi(), saat.instant(), gerekce, sefer.kalkisGecikmeDk()));
        } catch (DataIntegrityViolationException e) { // aynı sefer + rol için eşzamanlı ikinci yazar (UNIQUE); YOLCU'da yalnız aynı makinist ikinci kez
            throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", rol == AtamaRol.YOLCU ? "Bu makinist bu seferde az önce görevlendirildi. Listeyi yenileyin."
                    : "Bu seferin bu rolü az önce başka biri tarafından dolduruldu. Listeyi yenileyin.");
        }

        Kayit kayit = Kayit.of(mevcut == null ? DegisiklikTuru.ATAMA : DegisiklikTuru.ATAMA_DEGISTIR, "ATAMA", yeni.id()).makinist(makinistId)
                .sefer(seferId).ayrinti(anlikGoruntu(y.parametreler, m, ihlaller, gerekce, false, null, rol == AtamaRol.YOLCU));
        if (mevcut == null) {
            kayit = kayit.degisim(null, rol + ":" + m.sicil());
        } else {
            kayit = kayit.degisim(rol + ":" + eskiSicil, rol + ":" + m.sicil()).neden(istek.neden(), istek.nedenNotu());
        }
        degisiklik.kaydet(kayit);
        return new AtamaSonucu(new AtamaDto(yeni.id(), seferId, rol, makinistId, m.sicil(), m.adSoyad(), yeni.atayan(),
                yeni.atamaZamani().atZone(ISTANBUL).toOffsetDateTime(), gerekce, uyarilar), mevcut == null);
    }

    /**
     * Otomatik atamanın tek atama yazımı (ASIL, kural ihlali yok): satır + ATAMA kaydı + anlık görüntü ({@code "otomatik":true}). Makinist satırı
     * ÇAĞIRAN tarafından kilitlenmiş olmalıdır. Satır Flush edilmez; çağıran toplu flush eder.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    Atama otomatikYaz(long seferId, int kalkisGecikmeDk, MakinistDto m, Parametreler p) {
        return otomatikYaz(seferId, kalkisGecikmeDk, m, AtamaRol.ASIL, p);
    }

    /** F2b (MK-42): YOLCU için de aynı yazım (otomatik atama önce YOLCU sonra ASIL yazar); anlık görüntüde {@code "yolculuk":true}. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    Atama otomatikYaz(long seferId, int kalkisGecikmeDk, MakinistDto m, AtamaRol rol, Parametreler p) {
        if (!rol.turler().contains(m.tur())) { // MK-86: uyumsuzluk çağıranın (otomatik atama) programcı hatasıdır, kullanıcı hatası değil
            throw new IllegalStateException("Rol-tür uyumsuzluğu: " + rol + " / " + m.tur() + " (" + m.sicil() + ")");
        }
        Atama yeni = depo.save(new Atama(seferId, m.id(), rol, DegisiklikServisi.simdikiKullaniciAdi(), saat.instant(), null, kalkisGecikmeDk));
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.ATAMA, "ATAMA", yeni.id()).makinist(m.id()).sefer(seferId).degisim(null, rol + ":" + m.sicil())
                .ayrinti(anlikGoruntu(p, m, List.of(), null, true, null, rol == AtamaRol.YOLCU)));
        return yeni;
    }

    void flush() {
        depo.flush();
    }

    /** ASIL/YARDIMCI kaldırma (rol başına tek atama); YOLCU için {@link #kaldir(long, AtamaRol, Long, Neden, String)}. */
    @Transactional
    public KaldirSonucuDto kaldir(long seferId, AtamaRol rol, Neden neden, String nedenNotu) {
        return kaldir(seferId, rol, null, neden, nedenNotu);
    }

    /**
     * {@code rol == YOLCU} ise {@code makinistId} ZORUNLUDUR (bir seferde birden çok yolcu olabilir; yoksa 400) ve satır makinistle bulunur (yoksa ya da satırın rolü
     * YOLCU değilse 404); diğer rollerde {@code makinistId} yok sayılır (MK-38).
     */
    @Transactional
    public KaldirSonucuDto kaldir(long seferId, AtamaRol rol, Long makinistId, Neden neden, String nedenNotu) {
        SeferDto sefer = seferler.getir(seferId); // 404
        AtamaRol r = rol == null ? AtamaRol.ASIL : rol;
        Atama a;
        if (r == AtamaRol.YOLCU) {
            if (makinistId == null) {
                throw UygulamaHatasi.dogrulama("makinistId", "Yolcu kaldırmak için makinist belirtilmeli.");
            }
            a = depo.findBySeferIdAndMakinistId(seferId, makinistId).filter(x -> x.rol() == AtamaRol.YOLCU)
                    .orElseThrow(() -> UygulamaHatasi.bulunamadi("Bu seferde bu makinist için yolcu kaydı yok."));
        } else {
            a = depo.findBySeferIdAndRol(seferId, r).orElseThrow(() -> UygulamaHatasi.bulunamadi("Bu seferde bu rolde atama yok."));
        }
        DegisiklikServisi.nedenDogrula(neden, nedenNotu);
        String sicil = makinistler.getir(a.makinistId()).sicil();
        var once = kirmiziEtkisi.once(List.of(a.makinistId()), sefer, x -> x.id().equals(a.id()));
        depo.delete(a);
        depo.flush();
        gordumlar.atamalariSilinenleriTemizle(List.of(a.id()));
        olayYaz(a, sefer, AtamaOlayi.Tur.KALDIRILDI, neden);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.ATAMA_KALDIR, "ATAMA", a.id()).makinist(a.makinistId()).sefer(seferId)
                .degisim(a.rol() + ":" + sicil, null).neden(neden, nedenNotu));
        return kirmiziEtkisi.sonra(once);
    }

    /** Elinden alınan görev kalemi (FR-M09): özet seferin o anki durumundan; neden NOTU yazılmaz, yalnız etiket [V9]. */
    void olayYaz(Atama alinan, SeferDto sefer, AtamaOlayi.Tur tur, Neden neden) {
        olaylar.save(new AtamaOlayi(alinan.makinistId(), sefer.id(), tur, GorevOzeti.hesapla(alinan, sefer), neden == null ? null : neden.name(), saat.instant()));
    }

    // ------------------------------------------------------------------ rapor ve makinist okumaları

    /**
     * Raporlar (adalet, yedek, puantaj) için: makinistlerin [bas, bit] (planlanan gün, iki uç dahil) iptal olmayan atama satırları, tüm roller; güncel (gecikmeli)
     * saatlerle. Salt okuma.
     */
    public List<AtamaSatiri> satirlar(java.util.Collection<Long> makinistIdler, LocalDate bas, LocalDate bit) {
        return makinistIdler.isEmpty() ? List.of() : satirKaynagi.aralik(makinistIdler, bas, bit);
    }

    /**
     * Makinistlerin [bas, bit] çevresindeki görevleri (kural penceresiyle yüklenir: öncesi ve sonrası dahil), başlangıca göre sıralı; YOLCU satırları yolculuk görevi olarak
     * dahildir (F2b, MK-43: konaklama önerisi nöbetleri bunlardan türetir). Salt okuma; tek yükleme.
     */
    public Map<Long, List<Gorev>> gorevler(java.util.Collection<Long> makinistIdler, LocalDate bas, LocalDate bit) {
        if (makinistIdler.isEmpty()) {
            return Map.of();
        }
        List<MakinistDto> kisiler = makinistler.detaylar(makinistIdler);
        Yuk y = yukleyici.yukleAralik(bas, bit, kisiler);
        Map<Long, List<Gorev>> sonuc = new java.util.HashMap<>();
        for (MakinistDto m : kisiler) {
            sonuc.put(m.id(), y.satirlar(m.id()).stream().map(y::gorev).sorted(Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId)).toList());
        }
        return sonuc;
    }

    /**
     * Yolculuk çalışma süresinden sayılıyor mu ({@code yolculuk.calisma_sayilir}, MK-39). Raporlar (adalet, puantaj) süre/gün hesabına YOLCU satırlarını yalnız
     * bu doğruysa katar; yedek ve izin etkisi yolculuktaki makinistin "görevli" olduğunu her durumda görür ({@link #satirlar}).
     */
    public boolean yolculukSayilir() {
        return yukleyici.yolculukSayilir();
    }

    /** {@link #satirlar} + yolculuk çalışma sayılmıyorsa YOLCU satırları elenmiş (adalet/puantaj süre ve gün hesabı; MK-40). */
    public List<AtamaSatiri> calismaSatirlari(java.util.Collection<Long> makinistIdler, LocalDate bas, LocalDate bit) {
        List<AtamaSatiri> tum = satirlar(makinistIdler, bas, bit);
        return tum.stream().anyMatch(AtamaSatiri::yolculuk) && !yolculukSayilir() ? tum.stream().filter(s -> !s.yolculuk()).toList() : tum;
    }

    /**
     * Makinistin kendi görevleri: [bas, bit] (planlanan gün, iki uç dahil), iptal sefer DAHİL, kalkış sırasıyla. Başka makinistin satırı yoktur (sorgu makinist kimliğiyle
     * süzülür); aynı seferdeki eş görevlinin yalnız adı eklenir [V4]. Tek sorgu + toplu okuma (N+1 yok).
     */
    public List<MakinistGorevDto> makinistGorevleri(long makinistId, LocalDate bas, LocalDate bit) {
        List<Atama> kendi = depo.makinistinGorevleri(makinistId, bas, bit);
        if (kendi.isEmpty()) {
            return List.of();
        }
        List<Long> seferIdler = kendi.stream().map(Atama::seferId).distinct().toList();
        Map<Long, SeferDto> sefer = seferler.kimliklerle(seferIdler);
        // eş makinist yalnız sürüş satırlarından (MK-45): yolcu, asıl makinistin "eşi" değildir; kondüktör de eş makinist değildir (F3b: makinist çıktısı aynı kalır, kondüktör asıl makinisti eş görür)
        Map<Long, Long> es = depo.findBySeferIdIn(seferIdler).stream().filter(a -> a.rol() != AtamaRol.YOLCU && a.rol() != AtamaRol.KONDUKTOR && !a.makinistId().equals(makinistId))
                .collect(Collectors.toMap(a -> a.seferId(), Atama::makinistId, (a, b) -> a));
        Map<Long, String> esAdlari = makinistler.adlar(es.values());
        Map<Long, GorevGordum> onaylar = gordumlar.findByAtamaIdIn(kendi.stream().map(Atama::id).toList()).stream().collect(Collectors.toMap(GorevGordum::atamaId, g -> g));
        Map<LocalDate, Integer> gunSayac = new java.util.HashMap<>();
        List<MakinistGorevDto> sonuc = new ArrayList<>();
        for (Atama a : kendi) {
            SeferDto s = sefer.get(a.seferId());
            int sira = gunSayac.merge(s.tarih(), 1, Integer::sum);
            Long esId = a.rol() == AtamaRol.YOLCU ? null : es.get(a.seferId());
            String ozet = GorevOzeti.hesapla(a, s);
            GorevGordum onay = onaylar.get(a.id());
            GordumDurumu gordu = GorevGordum.durumu(onay, a, ozet);
            sonuc.add(new MakinistGorevDto(a.id(), s.id(), s.tarih(), sira, s.trenNo(), a.rol(), s.durum(), s.kalkisGar(), s.varisGar(), s.planlananKalkis(), s.planlananVaris(),
                    s.guncelKalkis(), s.guncelVaris(), s.kalkisGecikmeDk(), s.varisGecikmeDk(), ZamanHesabi.gun(s.guncelVaris().toInstant()).isAfter(s.tarih()), s.aracTipi(), s.hat(),
                    s.km(), esId == null ? null : esAdlari.get(esId), gordu,
                    gordu == GordumDurumu.GORULDU ? onay.zaman().atZone(ISTANBUL).toOffsetDateTime() : null, ozet, gordu == GordumDurumu.DEGISTI ? onay.ozet() : null, s.parcaNo(), s.parcaSayisi()));
        }
        return sonuc;
    }

    /** Gerçekleşmiş atamalardan türetilen son sürüş günleri (belge panosu): makinist -> hat -> gün. */
    public Map<Long, Map<Long, LocalDate>> turetilmisSonSurus(java.time.Instant simdi) {
        Map<Long, Map<Long, LocalDate>> sonuc = new java.util.HashMap<>();
        for (OncekiSurus o : depo.gerceklesenSonSurus(simdi)) {
            sonuc.computeIfAbsent(o.makinistId(), k -> new java.util.HashMap<>()).put(o.hatId(), ZamanHesabi.gun(o.varis()));
        }
        return sonuc;
    }

    // ------------------------------------------------------------------ yardımcılar

    /** Atanabilir sefer: iptal değil; YOLCU için ayrıca yük treni ayarı. Aday listesi ve ata aynı denetimden geçer. */
    private SeferDto atanabilirSefer(long seferId, AtamaRol rol) {
        SeferDto sefer = seferler.getir(seferId);
        if (sefer.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "İptal edilmiş sefere atama yapılamaz.");
        }
        if (rol == AtamaRol.YOLCU && sefer.trenTuru() == TrenTuru.YUK && ayarlar.tamsayi("yolculuk.yuk_trenine_binilir") == 0) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "YOLCU_SEFER_UYGUN_DEGIL", "Yük trenine yolcu olarak binilemez (ayar: yolculuk.yuk_trenine_binilir).", null);
        }
        return sefer;
    }

    /**
     * Atama anı kural anlık görüntüsü: kontrol edilen kodlar, sonuç, ihlaller (ölçülen/eşik), gerekçe, belge bitişleri ve o andaki eşikler. {@code takasId} doluysa kayıt bir takasın
     * parçasıdır (iki yönün ortak kimliği); {@code yolculuk}: YOLCU satırı (F2b), anlık görüntüde {@code "yolculuk":true}.
     */
    String anlikGoruntu(Parametreler p, MakinistDto m, List<Ihlal> ihlaller, String gerekce, boolean otomatik, Long takasId, boolean yolculuk) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("kontrolEdilenKodlar", KuralMotoru.kontrolEdilenKodlar(p));
        g.put("sonuc", ihlaller.isEmpty() ? "UYGUN" : "UYARI");
        g.put("ihlaller", ihlaller.stream().map(IhlalDto::of).toList());
        g.put("gerekce", gerekce);
        if (otomatik) {
            g.put("otomatik", true);
        }
        if (takasId != null) {
            g.put("takas", takasId);
        }
        if (yolculuk) {
            g.put("yolculuk", true);
        }
        Map<String, Object> belge = new LinkedHashMap<>();
        belge.put("ehliyetBitis", String.valueOf(m.ehliyetBitis()));
        belge.put("saglikBitis", String.valueOf(m.saglikBitis()));
        belge.put("psikoteknikBitis", String.valueOf(m.psikoteknikBitis()));
        belge.put("aracTipleri", m.broveAracTipleri().stream().collect(Collectors.toMap(a -> String.valueOf(a.aracTipi()), a -> a.gecerlilikBitis().toString(),
                (a, b) -> a, LinkedHashMap::new)));
        belge.put("hatlar", m.broveHatlar().stream().collect(Collectors.toMap(h -> String.valueOf(h.hat()), h -> h.gecerlilikBitis().toString(),
                (a, b) -> a, LinkedHashMap::new)));
        g.put("belgeBitis", belge);
        Map<String, String> parametreler = new java.util.TreeMap<>(p.degerler());
        Map<String, Siddet> siddetler = new java.util.TreeMap<>(p.siddetler());
        g.put("parametreler", new LinkedHashMap<>(parametreler));
        g.put("siddetler", new LinkedHashMap<>(siddetler));
        try {
            return json.writeValueAsString(g);
        } catch (JacksonException e) {
            throw new IllegalStateException("Anlık görüntü yazılamadı", e);
        }
    }

}
