package com.makinistatama.atama;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Serbest intikal yazma ve okuma (MK-106..108). Yazma anı denetimi ikinci bir kural kopyası DEĞİL: aday {@link AtamaSatiri#intikaldan} yolculuk görevidir ve mevcut
 * {@link KuralMotoru#degerlendirYazma} ile (iki yönlü, tüm komşularla) denetlenir; ENGEL → 422 {@code KURAL_IHLALI}, işlem geri alınır, satır kalmaz. Akış (kimlik {@code -id}'ye bağlı
 * olduğundan): makinist kilidi → alan doğrulaması (400) → {@code saveAndFlush} → değerlendirme → denetim kaydı. Silme yok: {@code IPTAL} durumu (neden zorunlu). Kırmızı/öksüz durumu canlı
 * hesaplanır ve SAKLANMAZ (MK-107). {@link AtamaServisi}'ne EKLENMEZ (boyut eşiği MK-35); o yalnız anlık görüntü yazıcısını paylaşır.
 */
@Service
@Transactional(readOnly = true)
public class IntikalServisi {

    private static final int EN_COK_ARALIK_GUN = 62;
    private static final Duration EN_UZUN = Duration.ofHours(24);

    private final IntikalRepository depo;
    private final MakinistServisi makinistler;
    private final TanimServisi tanimlar;
    private final BaglamYukleyici yukleyici;
    private final KirmiziEtkisi kirmiziEtkisi;
    private final KirmiziAtamaHesabi kirmiziHesabi;
    private final DegisiklikServisi degisiklik;
    private final AtamaServisi atamalar;
    private final Clock saat;

    IntikalServisi(IntikalRepository depo, MakinistServisi makinistler, TanimServisi tanimlar, BaglamYukleyici yukleyici, KirmiziEtkisi kirmiziEtkisi,
            KirmiziAtamaHesabi kirmiziHesabi, DegisiklikServisi degisiklik, AtamaServisi atamalar, Clock saat) {
        this.depo = depo;
        this.makinistler = makinistler;
        this.tanimlar = tanimlar;
        this.yukleyici = yukleyici;
        this.kirmiziEtkisi = kirmiziEtkisi;
        this.kirmiziHesabi = kirmiziHesabi;
        this.degisiklik = degisiklik;
        this.atamalar = atamalar;
        this.saat = saat;
    }

    // ------------------------------------------------------------------ okuma (atamacı)

    /** [bas, bit] (kalkış günü, İstanbul, iki uç dahil) intikaller, kalkışa göre; IPTAL dahil. {@code makinistId} boşsa hepsi. Canlı kırmızı/uyarı hesaplı. */
    public List<IntikalDto> liste(LocalDate bas, LocalDate bit, Long makinistId) {
        aralikDogrula(bas, bit);
        return dto(depo.aralik(gunBasi(bas), gunBasi(bit.plusDays(1)), makinistId));
    }

    /** [bas, bit] arasındaki PLANLI intikallerden canlı olarak kırmızı olanların sayısı (OK-28: atama kırmızı sayacına katılmaz, ayrı gösterilir). */
    public int kirmiziSayisi(LocalDate bas, LocalDate bit) {
        aralikDogrula(bas, bit);
        List<Intikal> liste = depo.aralik(gunBasi(bas), gunBasi(bit.plusDays(1)), null);
        return (int) kirmiziHesabi.intikalDurumlari(liste).values().stream().filter(KirmiziAtamaHesabi.Sonuc::kirmizi).count();
    }

    /** Atamacı "görülmeyen" sayacı için (FR-05, OK-30): intikalin makinisti, kalkışı ve güncel özeti gördüğü an (görmediyse null). */
    record GordumSatiri(long makinistId, Instant kalkis, Instant gorduZamani) {
    }

    /** [bas, bit] intikallerinin gördü durumu (IPTAL dahil: iptal bildirimi de tebliğ edilmeli, sefer iptaliyle aynı OK-9). Kural değerlendirmesi yok, yalnız iki okuma. */
    public List<GordumSatiri> gordumSatirlari(LocalDate bas, LocalDate bit) {
        aralikDogrula(bas, bit);
        List<Intikal> liste = depo.aralik(gunBasi(bas), gunBasi(bit.plusDays(1)), null);
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, String> garlar = tanimlar.tumAdlar();
        return liste.stream().map(i -> new GordumSatiri(i.makinistId(), i.kalkis(), gordumDurumu(i, i.ozet(garlar)) == GordumDurumu.GORULDU ? i.gorduZaman() : null)).toList();
    }

    // ------------------------------------------------------------------ yazma

    @Transactional
    public IntikalSonucuDto ekle(IntikalIstegi istek) {
        MakinistDto m = makinistler.getir(istek.makinistId()); // 404
        makinistler.kilitle(List.of(m.id())); // MK-7: aynı makinistte eşzamanlı intikal / atama yazımları serileşir
        dogrula(istek);
        Intikal i = depo.saveAndFlush(new Intikal(m.id(), istek.kalkisGarId(), istek.varisGarId(), istek.kalkis().toInstant(), istek.varis().toInstant(), istek.aracTuru(), notu(istek)));
        Yazma yazma = yazmaDenetimi(i, m, istek.gerekce());
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.INTIKAL_EKLE, "INTIKAL", i.id()).makinist(m.id()).degisim(null, ozet(i)).ayrinti(yazma.anlikGoruntu()));
        return new IntikalSonucuDto(dto(List.of(i)).getFirst(), List.of());
    }

    @Transactional
    public IntikalSonucuDto guncelle(long id, IntikalIstegi istek) {
        Intikal i = bul(id);
        if (istek.versiyon() == null) {
            throw UygulamaHatasi.dogrulama("versiyon", "Kayıt sürümü gerekli.");
        }
        if (!i.makinistId().equals(istek.makinistId())) {
            throw UygulamaHatasi.dogrulama("makinistId", "Makinist değiştirilemez; intikali iptal edip yeniden ekleyin.");
        }
        MakinistDto m = makinistler.getir(i.makinistId());
        makinistler.kilitle(List.of(m.id()));
        if (istek.versiyon() != i.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        if (i.durum() == IntikalDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("INTIKAL_IPTAL", "İptal edilmiş intikal düzeltilemez.");
        }
        dogrula(istek);
        LocalDate eskiGun = ZamanHesabi.gun(i.kalkis());
        LocalDate yeniGun = ZamanHesabi.gun(istek.kalkis().toInstant());
        var once = kirmiziEtkisi.onceIntikal(m.id(), eskiGun.isBefore(yeniGun) ? eskiGun : yeniGun, eskiGun.isAfter(yeniGun) ? eskiGun : yeniGun);
        String eski = ozet(i);
        i.degistir(m.id(), istek.kalkisGarId(), istek.varisGarId(), istek.kalkis().toInstant(), istek.varis().toInstant(), istek.aracTuru(), notu(istek));
        depo.saveAndFlush(i);
        Yazma yazma = yazmaDenetimi(i, m, istek.gerekce());
        var yeniKirmizi = kirmiziEtkisi.sonra(once).yeniKirmizi();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.INTIKAL_GUNCELLE, "INTIKAL", i.id()).makinist(m.id()).degisim(eski, ozet(i)).ayrinti(yazma.anlikGoruntu()));
        return new IntikalSonucuDto(dto(List.of(i)).getFirst(), yeniKirmizi);
    }

    /** İptal (silme yok): neden zorunlu; ikinci iptal 409. Kural motoru artık görmez; komşu atamaların yeni kırmızıları sonuçta döner (konum geri gelir: K-20 vb.). */
    @Transactional
    public IntikalSonucuDto iptal(long id, Long versiyon, Neden neden, String nedenNotu) {
        Intikal i = bul(id);
        if (versiyon == null) {
            throw UygulamaHatasi.dogrulama("versiyon", "Kayıt sürümü gerekli.");
        }
        DegisiklikServisi.nedenDogrula(neden, nedenNotu);
        MakinistDto m = makinistler.getir(i.makinistId());
        makinistler.kilitle(List.of(m.id()));
        if (versiyon != i.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        if (i.durum() == IntikalDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("INTIKAL_ZATEN_IPTAL", "Bu intikal zaten iptal edilmiş.");
        }
        LocalDate gun = ZamanHesabi.gun(i.kalkis());
        var once = kirmiziEtkisi.onceIntikal(m.id(), gun, gun);
        String eski = ozet(i);
        i.iptalEt();
        depo.saveAndFlush(i);
        var yeniKirmizi = kirmiziEtkisi.sonra(once).yeniKirmizi();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.INTIKAL_IPTAL, "INTIKAL", i.id()).makinist(m.id()).degisim(eski, ozet(i)).neden(neden, nedenNotu));
        return new IntikalSonucuDto(dto(List.of(i)).getFirst(), yeniKirmizi);
    }

    // ------------------------------------------------------------------ makinist (MK-108)

    /** Makinistin KENDİ intikalleri: kalkışı [bas, bit] (İstanbul günü) içinde, IPTAL dahil, kalkış sırasıyla; atamacının notu YOK. */
    public List<MakinistIntikalDto> makinistinIntikalleri(long makinistId, LocalDate bas, LocalDate bit) {
        List<Intikal> liste = depo.aralik(gunBasi(bas), gunBasi(bit.plusDays(1)), makinistId);
        Map<Long, String> garlar = tanimlar.tumAdlar();
        return liste.stream().map(i -> makinistDto(i, garlar)).toList();
    }

    /**
     * "Gördüm": makinist intikalini güncel hâliyle gördüğünü onaylar. Kendi kaydı değilse ya da [bas, bit] ufku dışındaysa 404 (varlık sızdırılmaz); ekrandaki özet güncelle
     * uyuşmazsa 409 {@code OZET_DEGISTI} (yanıtta güncel kayıt {@code gorev} (istemci ApiHata.gorev, görevdekiyle aynı sözleşme)); zaten GORULDU ise idempotent (denetim kaydı eklenmez).
     */
    @Transactional
    public MakinistIntikalDto gordum(long makinistId, long id, LocalDate bas, LocalDate bit, String ekrandakiOzet) {
        makinistler.kilitle(List.of(makinistId));
        Intikal i = depo.findById(id).filter(x -> x.makinistId() == makinistId).filter(x -> {
            LocalDate gun = ZamanHesabi.gun(x.kalkis());
            return !gun.isBefore(bas) && !gun.isAfter(bit);
        }).orElseThrow(() -> UygulamaHatasi.bulunamadi("İntikal bulunamadı."));
        Map<Long, String> garlar = tanimlar.tumAdlar();
        MakinistIntikalDto g = makinistDto(i, garlar);
        if (!g.ozet().equals(ekrandakiOzet)) {
            throw new UygulamaHatasi(HttpStatus.CONFLICT, "OZET_DEGISTI", "İntikal siz bakarken değişti. Yeni hâline bakıp yeniden onaylayın.", Map.of("gorev", g));
        }
        if (g.gordu() == GordumDurumu.GORULDU) {
            return g;
        }
        i.goruldu(saat.instant(), g.ozet());
        depo.saveAndFlush(i);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.GORDUM, "INTIKAL", i.id()).makinist(makinistId).degisim(g.gordu() == GordumDurumu.DEGISTI ? g.eskiOzet() : null, g.ozet()));
        return makinistDto(i, garlar);
    }

    // ------------------------------------------------------------------ yardımcılar

    private record Yazma(String anlikGoruntu) {
    }

    /**
     * Yazma anı denetimi (MK-106): satır yazılmış (flush edilmiş), kendi {@code -id}'si komşulardan hariç; ENGEL → 422 (çağıranın işlemi geri alınır), UYARI varsa gerekçe zorunlu.
     */
    private Yazma yazmaDenetimi(Intikal i, MakinistDto m, String gerekceMetni) {
        LocalDate gun = ZamanHesabi.gun(i.kalkis());
        Yuk y = yukleyici.yukleAralik(gun, gun, List.of(m));
        Gorev aday = y.gorev(AtamaSatiri.intikaldan(i));
        List<Gorev> diger = y.komsular(m.id(), gun, i.seferKimligi(), null);
        List<Ihlal> ihlaller = KuralMotoru.degerlendirYazma(y.baglam(m, aday, diger, y.simdi()));
        if (KuralMotoru.engelVarMi(ihlaller)) {
            throw UygulamaHatasi.kuralIhlali("Kural ihlali: " + m.sicil() + " için bu intikal yazılamaz.", ihlaller.stream().filter(x -> x.siddet() == Siddet.ENGEL).map(IhlalDto::of).toList());
        }
        String gerekce = gerekceMetni == null || gerekceMetni.isBlank() ? null : gerekceMetni.strip();
        if (!ihlaller.isEmpty() && gerekce == null) {
            throw UygulamaHatasi.dogrulama("gerekce", "Kural uyarısı var; intikal için gerekçe yazılmalı.");
        }
        return new Yazma(atamalar.anlikGoruntu(y.parametreler, m, ihlaller, gerekce, false, null, true));
    }

    private Intikal bul(long id) {
        return depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("İntikal bulunamadı."));
    }

    /** Alan doğrulaması (400): gar çifti farklı ve gerçekten GAR, zamanlar makul, varış kalkıştan sonra ve en çok 24 saat sonra. */
    private void dogrula(IntikalIstegi istek) {
        if (istek.kalkisGarId().equals(istek.varisGarId())) {
            throw UygulamaHatasi.dogrulama("varisGarId", "Kalkış ve varış garı aynı olamaz.");
        }
        tanimlar.bul(TanimTipi.GAR, istek.kalkisGarId(), "kalkisGarId");
        tanimlar.bul(TanimTipi.GAR, istek.varisGarId(), "varisGarId");
        yilDogrula(istek.kalkis(), "kalkis");
        yilDogrula(istek.varis(), "varis");
        if (!istek.varis().toInstant().isAfter(istek.kalkis().toInstant())) {
            throw UygulamaHatasi.dogrulama("varis", "Varış zamanı kalkıştan sonra olmalı.");
        }
        if (Duration.between(istek.kalkis().toInstant(), istek.varis().toInstant()).compareTo(EN_UZUN) > 0) {
            throw UygulamaHatasi.dogrulama("varis", "İntikal en çok 24 saat sürebilir.");
        }
    }

    private static void yilDogrula(OffsetDateTime an, String alan) {
        if (an.getYear() < 2000 || an.getYear() > 2100) {
            throw UygulamaHatasi.dogrulama(alan, "Tarih 2000 ile 2100 arasında olmalı.");
        }
    }

    private static String notu(IntikalIstegi istek) {
        return istek.notu() == null || istek.notu().isBlank() ? null : istek.notu().strip();
    }

    /** Denetim kaydı metni: "İntikal · Eskişehir 15.10 07:00 → Ankara 15.10 08:30 · Servis" (iptalde " · İPTAL"). */
    private String ozet(Intikal i) {
        return i.ozet(tanimlar.tumAdlar());
    }

    private MakinistIntikalDto makinistDto(Intikal i, Map<Long, String> garlar) {
        String ozet = i.ozet(garlar);
        GordumDurumu gordu = gordumDurumu(i, ozet);
        return new MakinistIntikalDto(i.id(), garlar.get(i.kalkisGarId()), garlar.get(i.varisGarId()), atZone(i.kalkis()), atZone(i.varis()), i.aracTuru(), i.durum(), gordu,
                gordu == GordumDurumu.GORULDU ? atZone(i.gorduZaman()) : null, ozet, gordu == GordumDurumu.DEGISTI ? i.gorduOzet() : null);
    }

    private static GordumDurumu gordumDurumu(Intikal i, String guncelOzet) {
        if (i.gorduOzet() == null) {
            return GordumDurumu.YENI;
        }
        return i.gorduOzet().equals(guncelOzet) ? GordumDurumu.GORULDU : GordumDurumu.DEGISTI;
    }

    private List<IntikalDto> dto(List<Intikal> liste) {
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, KirmiziAtamaHesabi.Sonuc> durum = kirmiziHesabi.intikalDurumlari(liste);
        Map<Long, MakinistDto.Ozet> kisiler = makinistler.liste(null, null).stream().collect(Collectors.toMap(MakinistDto.Ozet::id, o -> o));
        Map<Long, String> garlar = tanimlar.tumAdlar();
        return liste.stream().map(i -> {
            MakinistDto.Ozet k = kisiler.get(i.makinistId());
            KirmiziAtamaHesabi.Sonuc d = durum.get(i.id());
            GordumDurumu gordu = gordumDurumu(i, i.ozet(garlar));
            return new IntikalDto(i.id(), i.makinistId(), k == null ? null : k.sicil(), k == null ? null : k.adSoyad(), i.kalkisGarId(), garlar.get(i.kalkisGarId()), i.varisGarId(),
                    garlar.get(i.varisGarId()), atZone(i.kalkis()), atZone(i.varis()), i.aracTuru(), i.notu(), i.durum(), i.versiyon(), gordu,
                    gordu == GordumDurumu.GORULDU ? atZone(i.gorduZaman()) : null, d != null && d.kirmizi(), d == null ? List.of() : d.nedenler(),
                    d == null ? List.of() : d.uyarilar());
        }).sorted(Comparator.comparing(IntikalDto::kalkis).thenComparing(IntikalDto::id)).toList();
    }

    private static OffsetDateTime atZone(Instant an) {
        return an.atZone(ZamanAyari.BOLGE).toOffsetDateTime();
    }

    static Instant gunBasi(LocalDate gun) {
        return gun.atStartOfDay(ZamanAyari.BOLGE).toInstant();
    }

    private static void aralikDogrula(LocalDate bas, LocalDate bit) {
        if (bit.isBefore(bas)) {
            throw UygulamaHatasi.dogrulama("bit", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        if (ChronoUnit.DAYS.between(bas, bit) > EN_COK_ARALIK_GUN) {
            throw UygulamaHatasi.dogrulama("bit", "Aralık en çok " + EN_COK_ARALIK_GUN + " gün olabilir.");
        }
    }
}
