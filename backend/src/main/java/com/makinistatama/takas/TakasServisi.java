package com.makinistatama.takas;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaIstegi;
import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.atama.AtamaTakasServisi;
import com.makinistatama.atama.GorevGorunumleri;
import com.makinistatama.atama.GorevGorunumu;
import com.makinistatama.atama.TakasIhlali;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistKarti;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarServisi;

/**
 * Takas / görev bırakma talebi (FR-M11..M15, MK-34). Durum makinesi düz yöntemlerdir (State/Strategy sınıfı yok). Bayatlık ve süre aşımı TÜRETİLİR ({@link #etkinDurum}): okumalar (GET) hiçbir şey yazmaz;
 * kalıcı kapanış yalnız yazma yollarında ({@link #sistemKapat}) olur.
 * <p>
 * <b>Kilit sırası (W3):</b> takas yazan HER yol önce talepteki makinistleri (kaynak, hedef, bırakmada seçilen aday) {@code kilitle} ile TEK çağrıda artan kimlikle kilitler, SONRA talep satırını
 * okur/yazar, sonra atama satırlarına dokunur. Kilit öncesinde yalnız projeksiyon okunur (varlık önbelleğine girmez), talep varlığı kilitten sonra taze yüklenir.
 * <p>
 * Makinist yanıtlarında karşı tarafın kural ayrıntısı YOKTUR [V12]: ayrıntı yalnız {@link #detay} (atamacı).
 */
@Service
@Transactional(readOnly = true)
public class TakasServisi {

    static final String SON_SAAT = "takas.son_saat";
    static final String KARSI_ONAY_SAAT = "takas.karsi_onay_saat";
    static final String GORUNUR_GUN = "makinist.gorunur_gun";
    static final String GUNLUK_ACMA = "takas.gunluk_acma";
    /** Kapanmış talepler listede bu kadar gün görünür (makinist ret / bayat sonucunu burada görür; ayrı "görüldü" izi tutulmaz). */
    static final String KAPANMIS_GUN = "takas.kapanmis_gun";

    private static final Set<Neden> NEDENLER = EnumSet.of(Neden.HASTALIK, Neden.IZIN, Neden.DIGER);

    private final TakasRepository depo;
    private final GorevGorunumleri gorunumler;
    private final AtamaServisi atamalar;
    private final AtamaTakasServisi takaslar;
    private final MakinistServisi makinistler;
    private final AyarServisi ayarlar;
    private final DegisiklikServisi degisiklik;
    private final Clock saat;

    TakasServisi(TakasRepository depo, GorevGorunumleri gorunumler, AtamaServisi atamalar, AtamaTakasServisi takaslar, MakinistServisi makinistler, AyarServisi ayarlar, DegisiklikServisi degisiklik,
            Clock saat) {
        this.depo = depo;
        this.gorunumler = gorunumler;
        this.atamalar = atamalar;
        this.takaslar = takaslar;
        this.makinistler = makinistler;
        this.ayarlar = ayarlar;
        this.degisiklik = degisiklik;
        this.saat = saat;
    }

    // ------------------------------------------------------------------ saf süre / durum kuralları (birim testli)

    /**
     * Karşı makinistin son yanıt anı: {@code min(açılış + karsi_onay_saat, en erken kalkış − son_saat)}. Eşit an hâlâ uygundur (kabul {@code !simdi.isAfter(sonKabul)}).
     */
    static Instant sonKabul(Instant olusturma, Instant enErkenKalkis, int karsiOnaySaat, int sonSaat) {
        Instant a = olusturma.plus(karsiOnaySaat, ChronoUnit.HOURS);
        Instant b = enErkenKalkis.minus(sonSaat, ChronoUnit.HOURS);
        return a.isBefore(b) ? a : b;
    }

    /**
     * Talep açılabilir mi: kalkışa {@code ayirmaSaat} saatten az kalmamış (eşit uygun) ve kalkış geçmemiş. Hastalık nedenli bırakma {@code ayirmaSaat = 0} ile gelir (S2: acil bırakma 12 saat
     * sınırına girmez ama kalkıştan önce olmalıdır).
     */
    static boolean acilabilir(Instant simdi, Instant enErkenKalkis, int ayirmaSaat) {
        return !simdi.isAfter(enErkenKalkis.minus(ayirmaSaat, ChronoUnit.HOURS)) && simdi.isBefore(enErkenKalkis);
    }

    private static int ayirmaSaat(TakasTuru tur, Neden neden, int sonSaat) {
        return tur == TakasTuru.BIRAK && neden == Neden.HASTALIK ? 0 : sonSaat;
    }

    /** Okuma anındaki bağlam: şimdi, eşikler ve açık taleplerin atamalarının güncel görünümü. */
    private record Baglam(Instant simdi, int sonSaat, int karsiOnaySaat, Map<Long, GorevGorunumu> gorunum, Set<String> gorevli) {
    }

    /**
     * Etkin durum (MK-34/3): kapanmış talep kendisidir. Açık talep: kaynak ya da hedef atama silinmiş / makinisti ya da sefer özeti (gecikme, iptal, saat) değişmişse BAYAT; karşı onayda
     * {@link #sonKabul} geçmişse ya da kalkış geçmişse SURESI_DOLDU. Hiçbir şey yazmaz.
     */
    static TakasDurumu etkinDurum(TakasTalebi t, Instant simdi, int sonSaat, int karsiOnaySaat, Map<Long, GorevGorunumu> gorunum) {
        if (!t.durum().acik()) {
            return t.durum();
        }
        GorevGorunumu k = gorunum.get(t.kaynakAtamaId());
        if (k == null || !k.ayniMi(t.kaynakTarafi())) {
            return TakasDurumu.BAYAT;
        }
        if (t.tur() == TakasTuru.TAKAS) {
            GorevGorunumu h = gorunum.get(t.hedefAtamaId());
            if (h == null || !h.ayniMi(t.hedefTarafi())) {
                return TakasDurumu.BAYAT;
            }
        }
        if (!simdi.isBefore(t.sonKalkis())) {
            return TakasDurumu.SURESI_DOLDU;
        }
        if (t.durum() == TakasDurumu.KARSI_ONAY_BEKLIYOR && simdi.isAfter(sonKabul(t.olusturma(), t.sonKalkis(), karsiOnaySaat, sonSaat))) {
            return TakasDurumu.SURESI_DOLDU;
        }
        return t.durum();
    }

    private Baglam baglam(List<TakasTalebi> talepler) {
        Set<Long> atamaIdler = new HashSet<>();
        Set<Long> seferIdler = new HashSet<>();
        for (TakasTalebi t : talepler) {
            if (t.durum().acik()) {
                if (t.tur() == TakasTuru.TAKAS) {
                    seferIdler.add(t.kaynakSeferId());
                    seferIdler.add(t.hedefSeferId());
                }
                atamaIdler.add(t.kaynakAtamaId());
                if (t.hedefAtamaId() != null) {
                    atamaIdler.add(t.hedefAtamaId());
                }
            }
        }
        Map<Long, GorevGorunumu> gorunum = gorunumler.kimliklerle(atamaIdler).stream().collect(Collectors.toMap(GorevGorunumu::atamaId, g -> g));
        Set<String> gorevli = gorunumler.seferAtamalari(seferIdler).stream().map(k -> gorevliAnahtari(k.seferId(), k.makinistId())).collect(Collectors.toSet());
        return new Baglam(saat.instant(), ayarlar.tamsayi(SON_SAAT), ayarlar.tamsayi(KARSI_ONAY_SAAT), gorunum, gorevli);
    }

    private static String gorevliAnahtari(long sefer, long makinist) {
        return sefer + ":" + makinist;
    }

    /**
     * {@link #etkinDurum} + çapraz çakışma (W3, zincirleme talep): açık karşılıklı takasta taraflardan biri, değişecek seferde artık zaten görevliyse (başka bir takas / atama yüzünden) talep
     * yürütülemez; açılışta geçerliydi, sonradan bozuldu: BAYAT.
     */
    private TakasDurumu etkin(TakasTalebi t, Baglam b) {
        TakasDurumu d = etkinDurum(t, b.simdi(), b.sonSaat(), b.karsiOnaySaat(), b.gorunum());
        if (d.acik() && t.tur() == TakasTuru.TAKAS && (b.gorevli().contains(gorevliAnahtari(t.hedefSeferId(), t.kaynakMakinistId()))
                || b.gorevli().contains(gorevliAnahtari(t.kaynakSeferId(), t.hedefMakinistId())))) {
            return TakasDurumu.BAYAT;
        }
        return d;
    }

    private static OffsetDateTime an(Instant t) {
        return t.atZone(ZamanAyari.BOLGE).toOffsetDateTime();
    }

    // ------------------------------------------------------------------ makinist: okuma

    /** Makinistin taraf olduğu açık talepler ile son {@code takas.kapanmis_gun} günde kapanmışlar (en yeni önce). Karşı tarafın yalnız sicili ve görev özeti görünür. */
    public List<MakinistTakasDto> makinistinTalepleri(long makinistId) {
        List<TakasTalebi> liste = depo.makinistinkiler(makinistId, TakasDurumu.ACIKLAR, saat.instant().minus(ayarlar.tamsayi(KAPANMIS_GUN), ChronoUnit.DAYS));
        return makinistDtolari(liste, makinistId);
    }

    private List<MakinistTakasDto> makinistDtolari(List<TakasTalebi> liste, long makinistId) {
        Baglam b = baglam(liste);
        Map<Long, MakinistKarti> kartlar = makinistler.kartlar(liste.stream().flatMap(t -> java.util.stream.Stream.of(t.kaynakMakinistId(), t.hedefMakinistId())).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return liste.stream().map(t -> {
            boolean gelen = t.hedefMakinistId() != null && t.hedefMakinistId() == makinistId;
            TakasDurumu d = etkin(t, b);
            Long karsi = gelen ? t.kaynakMakinistId() : t.hedefMakinistId();
            MakinistKarti k = karsi == null ? null : kartlar.get(karsi);
            Instant sonKabul = t.tur() == TakasTuru.TAKAS && d == TakasDurumu.KARSI_ONAY_BEKLIYOR ? sonKabul(t.olusturma(), t.sonKalkis(), b.karsiOnaySaat(), b.sonSaat()) : null;
            return new MakinistTakasDto(t.id(), t.tur(), d, gelen, gelen && d == TakasDurumu.KARSI_ONAY_BEKLIYOR, gelen ? t.hedefOzet() : t.kaynakOzet(), k == null ? null : k.sicil(),
                    gelen ? t.kaynakOzet() : t.hedefOzet(), gelen ? null : t.neden(), gelen ? null : t.nedenNotu(), an(t.olusturma()), sonKabul == null ? null : an(sonKabul), d == TakasDurumu.REDDEDILDI ? t.atamaciReddetti() ? "ATAMACI" : "KARSI_MAKINIST" : null);
        }).toList();
    }

    private MakinistTakasDto makinistDtosu(TakasTalebi t, long makinistId) {
        return makinistDtolari(List.of(t), makinistId).getFirst();
    }

    /** Makinistin kendi atamalarından AÇIK (etkin) talepte olanlar: görev kartında "takas bekleniyor" işareti, yeni talep düğmeleri gizlenir (QA UX-3). */
    public List<Long> acikAtamalari(long makinistId) {
        List<TakasTalebi> liste = depo.makinistinkiler(makinistId, TakasDurumu.ACIKLAR, saat.instant().plusSeconds(1)); // bas gelecekte: yalnız açıklar
        if (liste.isEmpty()) {
            return List.of();
        }
        Baglam b = baglam(liste);
        return liste.stream().filter(t -> etkin(t, b).acik()).map(t -> t.kaynakMakinistId() == makinistId ? t.kaynakAtamaId() : t.hedefAtamaId()).toList();
    }

    /** Rozet: makinistin kabul ya da reddetmesi gereken (etkin durumu hâlâ KARSI_ONAY_BEKLIYOR) gelen talepler. Bayat / süresi dolmuş sayılmaz. */
    public int bekleyenSayisi(long makinistId) {
        List<TakasTalebi> liste = depo.findByHedefMakinistIdAndDurum(makinistId, TakasDurumu.KARSI_ONAY_BEKLIYOR);
        if (liste.isEmpty()) {
            return 0;
        }
        Baglam b = baglam(liste);
        return (int) liste.stream().filter(t -> etkin(t, b) == TakasDurumu.KARSI_ONAY_BEKLIYOR).count();
    }

    // ------------------------------------------------------------------ makinist: hedef seçimi (MK-34/7)

    private record HedefBaglami(Instant simdi, List<GorevGorunumu> gorunumler, Set<Long> aktifler, Map<Long, MakinistKarti> kartlar, Set<Long> blokeAtamalar, int sonSaat) {
    }

    private HedefBaglami hedefBaglami() {
        LocalDate bugun = LocalDate.now(saat);
        List<GorevGorunumu> gorunum = gorunumler.aralik(bugun, bugun.plusDays(ayarlar.tamsayi(GORUNUR_GUN) - 1L));
        List<TakasTalebi> acik = depo.findByDurumIn(TakasDurumu.ACIKLAR);
        Baglam b = baglam(acik);
        Set<Long> bloke = new HashSet<>();
        for (TakasTalebi t : acik) {
            if (etkin(t, b).acik()) {
                bloke.add(t.kaynakAtamaId());
                if (t.hedefAtamaId() != null) {
                    bloke.add(t.hedefAtamaId());
                }
            }
        }
        return new HedefBaglami(b.simdi(), gorunum, new HashSet<>(makinistler.kimlikler(MakinistDurum.AKTIF, PersonelTuru.TUMU)),
                makinistler.kartlar(gorunum.stream().map(GorevGorunumu::makinistId).collect(Collectors.toSet())), bloke, b.sonSaat());
    }

    private static boolean zatenAtanmis(GorevGorunumu kendi, GorevGorunumu h, HedefBaglami c) {
        return c.gorunumler().stream().anyMatch(g -> (g.seferId() == h.seferId() && g.makinistId() == kendi.makinistId())
                || (g.seferId() == kendi.seferId() && g.makinistId() == h.makinistId()));
    }

    /**
     * {@code hedefUygunMu} (MK-34/7): ufuk içi (listede olmak), kendi dışı, aynı sefer değil, hesaplı ve AKTIF personel, AYNI TÜRDE (F3b, MK-90), açık (etkin) talepte olmayan, ikisi de zaten diğerinin seferinde görevli değil.
     * Kural motoru ÇALIŞMAZ ve ENGEL'e göre süzülmez: başkasının belge / dinlenme durumu toplu sorgulanamaz.
     */
    private static boolean hedefUygunMu(GorevGorunumu kendi, GorevGorunumu h, HedefBaglami c) {
        return h.makinistId() != kendi.makinistId() && h.seferId() != kendi.seferId() && h.hesapli() && c.aktifler().contains(h.makinistId())
                && !c.blokeAtamalar().contains(h.atamaId()) && !zatenAtanmis(kendi, h, c)
                && c.kartlar().get(h.makinistId()).tur() == c.kartlar().get(kendi.makinistId()).tur(); // MK-90: takas yalnız aynı personel türü içinde (tür karttan; askı durumundan bağımsız)
    }

    private GorevGorunumu kendiGorev(HedefBaglami c, long makinistId, long atamaId) {
        return c.gorunumler().stream().filter(g -> g.atamaId() == atamaId && g.makinistId() == makinistId).findFirst()
                .orElseThrow(() -> UygulamaHatasi.bulunamadi("Görev bulunamadı."));
    }

    /**
     * Seçilebilir karşı görevler (ufuk içi): sicil + herkese açık pano bilgisi. Açılış ucuyla AYNI {@link #hedefUygunMu}; ek olarak, açılışta kesin 422 verecek (süre sınırı geçmiş) görevler
     * kullanıcıyı yanıltmasın diye listeden çıkarılır (sefer saati zaten panoda herkese açıktır, yeni bilgi sızmaz).
     */
    public List<TakasHedefDto> hedefler(long makinistId, long atamaId) {
        HedefBaglami c = hedefBaglami();
        GorevGorunumu kendi = kendiGorev(c, makinistId, atamaId);
        return c.gorunumler().stream().filter(h -> hedefUygunMu(kendi, h, c))
                .filter(h -> acilabilir(c.simdi(), enErken(kendi, h), ayirmaSaat(TakasTuru.TAKAS, Neden.DIGER, c.sonSaat())))
                .sorted(Comparator.comparing(GorevGorunumu::guncelKalkis).thenComparing(GorevGorunumu::trenNo))
                .map(h -> new TakasHedefDto(h.atamaId(), c.kartlar().get(h.makinistId()).sicil(), h.ozet(), h.trenNo(), h.tarih(), h.kalkisGar(), h.varisGar(), h.guncelKalkis())).toList();
    }

    private static Instant enErken(GorevGorunumu kendi, GorevGorunumu h) {
        Instant k = kendi.guncelKalkis().toInstant();
        return h == null ? k : k.isBefore(h.guncelKalkis().toInstant()) ? k : h.guncelKalkis().toInstant();
    }

    // ------------------------------------------------------------------ makinist: yazma

    /**
     * Talep açar. Kendi atamasını teklif eder (başkasınınki 404); karşılıklıda hedef {@link #hedefUygunMu} süzgecinde değilse 404 (tahmin edilen kimlik varlık sızdırmaz). Açanın KENDİ tarafı için kural ön kontrolü
     * ENGEL verirse 422 {@code TAKAS_KURALA_UYMUYOR}: mesaj yalnız açanın KENDİ ihlal metinlerini taşır, karşı tarafın durumu söylenmez [V12]. Aynı atamadaki bayat / süresi dolmuş açık talep önce kapatılır.
     */
    @Transactional
    public MakinistTakasDto ac(long makinistId, TakasAcIstegi istek) {
        Neden neden = istek.neden();
        if (!NEDENLER.contains(neden)) {
            throw UygulamaHatasi.dogrulama("neden", "Neden Hastalık, İzin ya da Diğer olmalı.");
        }
        DegisiklikServisi.nedenDogrula(neden, istek.nedenNotu());
        String not = istek.nedenNotu() == null || istek.nedenNotu().isBlank() ? null : istek.nedenNotu().strip();
        Long atamaId = istek.atamaId();
        Long hedefAtamaId = istek.hedefAtamaId();
        TakasTuru tur = hedefAtamaId == null ? TakasTuru.BIRAK : TakasTuru.TAKAS;
        if (atamaId.equals(hedefAtamaId)) {
            throw UygulamaHatasi.dogrulama("hedefAtamaId", "Kendi göreviniz ile takas yapılamaz.");
        }

        // kilit ÖNCESİ: yalnız "bu atamalar kimin" (projeksiyon); sahiplik kilit SONRASI yeniden doğrulanır
        Map<Long, Long> sahipler = gorunumler.sahipleri(hedefAtamaId == null ? List.of(atamaId) : List.of(atamaId, hedefAtamaId));
        if (!Objects.equals(sahipler.get(atamaId), makinistId)) {
            throw UygulamaHatasi.bulunamadi("Görev bulunamadı.");
        }
        if (hedefAtamaId != null && sahipler.get(hedefAtamaId) == null) {
            throw UygulamaHatasi.bulunamadi("Görev bulunamadı.");
        }
        makinistler.kilitle(hedefAtamaId == null ? List.of(makinistId) : List.of(makinistId, sahipler.get(hedefAtamaId)));

        int kota = ayarlar.tamsayi(GUNLUK_ACMA);
        if (depo.countByKaynakMakinistIdAndOlusturmaAfter(makinistId, saat.instant().minus(1, ChronoUnit.DAYS)) >= kota) { // W4: aç / geri çek döngüsü sınırı (kilit altında: eşzamanlı açışlar da sayılır)
            throw new UygulamaHatasi(HttpStatus.TOO_MANY_REQUESTS, "TAKAS_KOTASI", "Son 24 saatte en fazla " + kota + " talep açabilirsiniz. Gerekirse atamacınıza telefonla bildirin.", null);
        }
        HedefBaglami c = hedefBaglami();
        GorevGorunumu kendi = kendiGorev(c, makinistId, atamaId);
        if (c.blokeAtamalar().contains(atamaId)) {
            throw UygulamaHatasi.cakisma("TALEP_ACIK", "Bu görev için zaten açık bir talep var.");
        }
        GorevGorunumu h = null;
        if (hedefAtamaId != null) {
            h = c.gorunumler().stream().filter(g -> g.atamaId() == hedefAtamaId).findFirst().orElseThrow(() -> UygulamaHatasi.bulunamadi("Görev bulunamadı."));
            if (h.makinistId() != sahipler.get(hedefAtamaId)) {
                throw UygulamaHatasi.bulunamadi("Görev bulunamadı.");
            }
            if (h.seferId() == kendi.seferId()) {
                throw UygulamaHatasi.dogrulama("hedefAtamaId", "Aynı seferdeki iki görev takas edilemez.");
            }
            if (zatenAtanmis(kendi, h, c)) {
                throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", "Taraflardan biri takas edilecek seferde zaten görevli.");
            }
            if (!hedefUygunMu(kendi, h, c)) {
                throw UygulamaHatasi.bulunamadi("Görev bulunamadı.");
            }
        }
        Instant enErken = enErken(kendi, h);
        int sonSaat = c.sonSaat();
        if (!acilabilir(c.simdi(), enErken, ayirmaSaat(tur, neden, sonSaat))) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "TAKAS_SURESI_GECTI", tur == TakasTuru.BIRAK && neden == Neden.HASTALIK
                    ? "Görevin kalkışı geçti; talep açılamaz. Atamacınıza telefonla bildirin."
                    : "Kalkışa " + sonSaat + " saatten az kaldı; talep açılamaz. Atamacınıza telefonla bildirin.", null);
        }
        List<String> engeller = h == null ? List.of() : takaslar.takasKendiTarafiEngelMetinleri(kendi.tarafi(), h.tarafi());
        if (!engeller.isEmpty()) { // sebep yalnız AÇANIN kendi tarafı (karşı tarafın durumu hesaplanmaz, sızmaz)
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "TAKAS_KURALA_UYMUYOR", "Bu takas kurallara uymuyor: " + String.join("; ", engeller), null);
        }
        // aynı atamalardaki bayat / süresi dolmuş açık talepler kapatılır (yoksa kısmi UNIQUE yeni talebi reddeder)
        List<Long> atamaIdler = hedefAtamaId == null ? List.of(atamaId) : List.of(atamaId, hedefAtamaId);
        List<TakasTalebi> eskiler = depo.acikOlanlar(TakasDurumu.ACIKLAR, atamaIdler);
        if (!eskiler.isEmpty()) {
            Baglam b = baglam(eskiler);
            for (TakasTalebi e : eskiler) {
                TakasDurumu d = etkin(e, b);
                if (d.acik()) {
                    throw UygulamaHatasi.cakisma("TALEP_ACIK", "Bu görev için zaten açık bir talep var.");
                }
                sistemKapat(e, d);
            }
        }
        TakasTalebi t;
        try {
            t = depo.saveAndFlush(new TakasTalebi(tur, kendi.tarafi(), h == null ? null : h.tarafi(), neden, not, DegisiklikServisi.simdikiKullaniciId().orElseThrow(), c.simdi(), enErken));
        } catch (DataIntegrityViolationException e) { // eşzamanlı ikinci açış (kısmi UNIQUE)
            throw UygulamaHatasi.cakisma("TALEP_ACIK", "Bu görev için zaten açık bir talep var.");
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TAKAS_TALEP, "TAKAS", t.id()).makinist(makinistId).sefer(kendi.seferId())
                .degisim(null, tur + ": " + kendi.ozet() + (h == null ? "" : " <-> " + h.ozet())).neden(neden, not));
        return makinistDtosu(t, makinistId);
    }

    /** Karşı makinist (yalnız hedef) kabul eder: sürede ve bayat değilse atamacı onayına geçer. Başkası için 404. */
    @Transactional(noRollbackFor = TakasKapanisHatasi.class)
    public MakinistTakasDto kabul(long makinistId, long talepId) {
        TakasKisileri k = depo.kisiler(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (!Objects.equals(k.hedef(), makinistId)) {
            throw UygulamaHatasi.bulunamadi("Talep bulunamadı.");
        }
        makinistler.kilitle(List.of(k.kaynak(), k.hedef()));
        TakasTalebi t = depo.findById(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (t.durum() != TakasDurumu.KARSI_ONAY_BEKLIYOR) {
            throw kapandi(t);
        }
        sureVeBayatDenetle(t);
        t.karsiKabul(saat.instant());
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TAKAS_KABUL, "TAKAS", t.id()).makinist(makinistId).sefer(t.hedefSeferId()).degisim(TakasDurumu.KARSI_ONAY_BEKLIYOR, TakasDurumu.ATAMACI_ONAYI_BEKLIYOR));
        return makinistDtosu(t, makinistId);
    }

    /** Karşı makinist (yalnız hedef) reddeder. Süresi dolmuş / bayat talep de reddedilebilir (kimseye zarar vermez). */
    @Transactional
    public MakinistTakasDto red(long makinistId, long talepId) {
        TakasKisileri k = depo.kisiler(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (!Objects.equals(k.hedef(), makinistId)) {
            throw UygulamaHatasi.bulunamadi("Talep bulunamadı.");
        }
        makinistler.kilitle(List.of(k.kaynak(), k.hedef()));
        TakasTalebi t = depo.findById(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (t.durum() != TakasDurumu.KARSI_ONAY_BEKLIYOR) {
            throw kapandi(t);
        }
        t.kapat(TakasDurumu.REDDEDILDI, saat.instant());
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TAKAS_RED, "TAKAS", t.id()).makinist(makinistId).sefer(t.hedefSeferId()).degisim(TakasDurumu.KARSI_ONAY_BEKLIYOR, TakasDurumu.REDDEDILDI));
        return makinistDtosu(t, makinistId);
    }

    /** Açan makinist (yalnız kaynak) açık talebini geri çeker. Başkası için 404. */
    @Transactional
    public MakinistTakasDto iptal(long makinistId, long talepId) {
        TakasKisileri k = depo.kisiler(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (!Objects.equals(k.kaynak(), makinistId)) {
            throw UygulamaHatasi.bulunamadi("Talep bulunamadı.");
        }
        makinistler.kilitle(k.hedef() == null ? List.of(k.kaynak()) : List.of(k.kaynak(), k.hedef()));
        TakasTalebi t = depo.findById(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (!t.durum().acik()) {
            throw kapandi(t);
        }
        TakasDurumu eski = t.durum();
        t.kapat(TakasDurumu.IPTAL, saat.instant());
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TAKAS_IPTAL, "TAKAS", t.id()).makinist(makinistId).sefer(t.kaynakSeferId()).degisim(eski, TakasDurumu.IPTAL));
        return makinistDtosu(t, makinistId);
    }

    private static UygulamaHatasi kapandi(TakasTalebi t) {
        return UygulamaHatasi.cakisma("TALEP_KAPANDI", "Bu talep artık açık değil (" + t.durum() + ").");
    }

    /** Bayat / süresi dolmuş AÇIK talebi çağıranın işleminde kalıcı kapatır + denetim kaydı (MK-34/3); çağıran kilitleri tutuyor, talep taze yüklenmiş olmalıdır. */
    private void sistemKapat(TakasTalebi t, TakasDurumu yeni) {
        TakasDurumu eski = t.durum();
        t.sistemKapat(yeni);
        depo.flush();
        degisiklik.kaydet(Kayit.of(yeni == TakasDurumu.BAYAT ? DegisiklikTuru.TAKAS_BAYAT : DegisiklikTuru.TAKAS_SURESI_DOLDU, "TAKAS", t.id()).makinist(t.kaynakMakinistId())
                .sefer(t.kaynakSeferId()).degisim(eski, yeni));
    }

    /**
     * Kilit altında etkin durumu denetler: BAYAT → 409 {@code TALEP_BAYAT}, SURESI_DOLDU → 422 {@code TAKAS_SURESI_GECTI}. Fırlatmadan ÖNCE talep kalıcı kapatılır
     * ({@link #sistemKapat}, aynı işlemde) ve işlemi geri almayan {@link TakasKapanisHatasi} fırlatılır (W1: ayrı bağlantı açılmaz).
     */
    private void sureVeBayatDenetle(TakasTalebi t) {
        TakasDurumu d = etkin(t, baglam(List.of(t)));
        if (d == TakasDurumu.BAYAT) {
            sistemKapat(t, d);
            throw new TakasKapanisHatasi(HttpStatus.CONFLICT, "TALEP_BAYAT", "Talep bayatladı: görevlerden biri değişti ya da kaldırıldı.");
        }
        if (d == TakasDurumu.SURESI_DOLDU) {
            sistemKapat(t, d);
            throw new TakasKapanisHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "TAKAS_SURESI_GECTI", "Talebin süresi doldu.");
        }
    }

    // ------------------------------------------------------------------ atamacı

    private TakasSatiriDto satir(TakasTalebi t, Baglam b, Map<Long, MakinistKarti> kartlar) {
        MakinistKarti k = kartlar.get(t.kaynakMakinistId());
        MakinistKarti h = t.hedefMakinistId() == null ? null : kartlar.get(t.hedefMakinistId());
        TakasDurumu d = etkin(t, b);
        Instant sonKabul = t.tur() == TakasTuru.TAKAS ? sonKabul(t.olusturma(), t.sonKalkis(), b.karsiOnaySaat(), b.sonSaat()) : null;
        return new TakasSatiriDto(t.id(), t.tur(), d, t.durum(), t.kaynakSeferId(), t.kaynakMakinistId(), k.sicil(), k.adSoyad(), t.kaynakOzet(), t.hedefMakinistId(),
                h == null ? null : h.sicil(), h == null ? null : h.adSoyad(), t.hedefOzet(), t.neden(), t.nedenNotu(), an(t.olusturma()), sonKabul == null ? null : an(sonKabul), an(t.sonKalkis()));
    }

    private List<TakasSatiriDto> satirlar(List<TakasTalebi> liste) {
        Baglam b = baglam(liste);
        Map<Long, MakinistKarti> kartlar = makinistler.kartlar(liste.stream().flatMap(t -> java.util.stream.Stream.of(t.kaynakMakinistId(), t.hedefMakinistId())).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return liste.stream().map(t -> satir(t, b, kartlar)).toList();
    }

    /**
     * Atamacı listesi: açık talepler ve son {@code takas.kapanmis_gun} günde kapananlar; {@code durum} verilirse ETKİN duruma göre süzülür. {@code bekleyen} süzgeçten bağımsız, atamacı onayı
     * bekleyen talep sayısıdır (rozet).
     */
    public TakasListeDto liste(TakasDurumu durum) {
        List<TakasSatiriDto> hepsi = satirlar(depo.tumu(TakasDurumu.ACIKLAR, saat.instant().minus(ayarlar.tamsayi(KAPANMIS_GUN), ChronoUnit.DAYS)));
        int bekleyen = (int) hepsi.stream().filter(s -> s.durum() == TakasDurumu.ATAMACI_ONAYI_BEKLIYOR).count();
        return new TakasListeDto(bekleyen, hepsi.stream().filter(s -> durum == null || s.durum() == durum).toList());
    }

    /** Ayrıntı: kural ihlallerinin AYRINTISI yalnız burada. Bayat / kapanmış talepte ihlaller boş. */
    public TakasDetayDto detay(long talepId) {
        TakasTalebi t = depo.findById(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        TakasSatiriDto s = satirlar(List.of(t)).getFirst();
        boolean canli = s.durum().acik();
        List<TakasIhlali> ihlaller = canli && t.tur() == TakasTuru.TAKAS ? takaslar.takasKontrol(t.kaynakTarafi(), t.hedefTarafi()) : List.of();
        // bırakılan atamanın GERÇEK rolüyle öner (kondüktör yuvasına makinist önerilmez; onay aynı rolle ata çağırır)
        return new TakasDetayDto(s, ihlaller, canli && t.tur() == TakasTuru.BIRAK ? atamalar.oner(t.kaynakSeferId(), gorunumler.kimliklerle(List.of(t.kaynakAtamaId())).getFirst().rol()) : null);
    }

    /**
     * Atamacı onayı. Karşılıklı: {@code AtamaTakasServisi.takasUygula} (iki yönlü kural; ENGEL → 422 ve hiçbir şey değişmez). Bırakma: {@code yeniMakinistId} ile mevcut {@code ata} (değiştir) ya da boşsa
     * {@code kaldir}. Yalnız ATAMACI_ONAYI_BEKLIYOR talep onaylanır; bayat → 409 {@code TALEP_BAYAT}; kalkıştan sonra 422 {@code TAKAS_SURESI_GECTI}. Eşzamanlı ikinci onay 409 {@code TALEP_KAPANDI}.
     */
    @Transactional(noRollbackFor = TakasKapanisHatasi.class)
    public TakasSatiriDto onay(long talepId, TakasOnayIstegi istek) {
        TakasKisileri k = depo.kisiler(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        Set<Long> kilit = new HashSet<>();
        kilit.add(k.kaynak());
        if (k.hedef() != null) {
            kilit.add(k.hedef());
        }
        if (k.hedef() == null && istek.yeniMakinistId() != null) {
            kilit.add(istek.yeniMakinistId());
        }
        makinistler.kilitle(kilit);
        TakasTalebi t = depo.findById(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (t.durum() == TakasDurumu.KARSI_ONAY_BEKLIYOR) {
            throw UygulamaHatasi.cakisma("KARSI_ONAY_YOK", "Karşı makinist henüz kabul etmedi.");
        }
        if (t.durum() != TakasDurumu.ATAMACI_ONAYI_BEKLIYOR) {
            throw kapandi(t);
        }
        sureVeBayatDenetle(t);
        String yeniSicil = null;
        if (t.tur() == TakasTuru.TAKAS) {
            takaslar.takasUygula(t.kaynakTarafi(), t.hedefTarafi(), t.id(), t.neden(), t.nedenNotu(), istek.gerekce());
        } else {
            GorevGorunumu g = gorunumler.kimliklerle(List.of(t.kaynakAtamaId())).getFirst();
            if (istek.yeniMakinistId() != null) {
                MakinistKarti c = makinistler.kartlar(List.of(istek.yeniMakinistId())).get(istek.yeniMakinistId());
                if (c == null || c.durum() != MakinistDurum.AKTIF) {
                    throw UygulamaHatasi.dogrulama("yeniMakinistId", "Devralacak makinist aktif olmalı.");
                }
                yeniSicil = c.sicil();
                atamalar.ata(g.seferId(), new AtamaIstegi(c.id(), g.rol(), istek.gerekce(), t.neden(), t.nedenNotu()));
            } else {
                atamalar.kaldir(g.seferId(), g.rol(), t.neden(), t.nedenNotu());
            }
        }
        t.atamaciKarari(TakasDurumu.ONAYLANDI, DegisiklikServisi.simdikiKullaniciAdi(), saat.instant());
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TAKAS_ONAY, "TAKAS", t.id()).makinist(t.kaynakMakinistId()).sefer(t.kaynakSeferId())
                .degisim(TakasDurumu.ATAMACI_ONAYI_BEKLIYOR, yeniSicil == null ? TakasDurumu.ONAYLANDI.name() : "ONAYLANDI -> " + yeniSicil));
        return satirlar(List.of(t)).getFirst();
    }

    /** Atamacı reddeder (açık herhangi bir aşamada); kaynak atamasını korur. */
    @Transactional
    public TakasSatiriDto atamaciRed(long talepId) {
        TakasKisileri k = depo.kisiler(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        makinistler.kilitle(k.hedef() == null ? List.of(k.kaynak()) : List.of(k.kaynak(), k.hedef()));
        TakasTalebi t = depo.findById(talepId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Talep bulunamadı."));
        if (!t.durum().acik()) {
            throw kapandi(t);
        }
        TakasDurumu eski = t.durum();
        t.atamaciKarari(TakasDurumu.REDDEDILDI, DegisiklikServisi.simdikiKullaniciAdi(), saat.instant());
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TAKAS_RED, "TAKAS", t.id()).makinist(t.kaynakMakinistId()).sefer(t.kaynakSeferId()).degisim(eski, TakasDurumu.REDDEDILDI));
        return satirlar(List.of(t)).getFirst();
    }
}
