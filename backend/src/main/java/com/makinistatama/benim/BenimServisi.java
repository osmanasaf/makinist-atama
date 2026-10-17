package com.makinistatama.benim;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.atama.GordumServisi;
import com.makinistatama.atama.GorevOlayiDto;
import com.makinistatama.atama.IntikalServisi;
import com.makinistatama.atama.MakinistGorevDto;
import com.makinistatama.atama.MakinistIntikalDto;
import com.makinistatama.benim.BenimKimlik.Kisi;
import com.makinistatama.makinist.IzinDurumu;
import com.makinistatama.makinist.IzinServisi;
import com.makinistatama.makinist.IzinTuru;
import com.makinistatama.konaklama.KonaklamaServisi;
import com.makinistatama.konaklama.MakinistKonaklamaDto;
import com.makinistatama.makinist.MakinistIzinDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.rapor.AdaletServisi;
import com.makinistatama.rapor.BelgeDurumuServisi;
import com.makinistatama.rapor.MakinistBelgeDto;
import com.makinistatama.rapor.MakinistOzetDto;
import com.makinistatama.takas.MakinistTakasDto;
import com.makinistatama.takas.TakasAcIstegi;
import com.makinistatama.takas.TakasHedefDto;
import com.makinistatama.takas.TakasKapanisHatasi;
import com.makinistatama.takas.TakasServisi;

/**
 * Makinistin kendi görünümü (F2a). Her metot çözülmüş {@link Kisi}'yi alır: kimlik hiçbir zaman istekten gelmez (MK-28), başka makinistin verisine ulaşan sorgu
 * yoktur. Servisler arası çağrılar tek makinist süzgeciyle yapılır.
 */
@Service
@Transactional(readOnly = true)
public class BenimServisi {

    static final String GORUNUR_GUN = "makinist.gorunur_gun";
    static final String IZIN_EN_ILERI_GUN = "izin.en_ileri_gun";
    static final String IZIN_EN_UZUN_GUN = "izin.en_uzun_gun";
    static final String IZIN_EN_COK_ACIK = "izin.en_cok_acik_talep";

    /** {@code gorevler} yanıtı: {@code ufukGun} bugünden itibaren görünen gün sayısı (bugün dahil); {@code kaldirilanlar}: elinden alınan, henüz kapatılmamış görev kalemleri; {@code bekleyenTakas}: benim kabul / reddetmem gereken gelen takas talepleri (rozet); {@code takasAcikAtamalar}: açık talepteki kendi atama kimliklerim; {@code intikaller}: kendi serbest intikallerim (görev ufkunda, IPTAL dahil; MK-108). */
    public record GorevlerDto(LocalDate bugun, int ufukGun, List<MakinistGorevDto> gorevler, List<GorevOlayiDto> kaldirilanlar, int bekleyenTakas, List<Long> takasAcikAtamalar, List<MakinistIntikalDto> intikaller) {
    }

    private static final java.time.format.DateTimeFormatter GUN = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AtamaServisi atamalar;
    private final GordumServisi gordumlar;
    private final IntikalServisi intikaller;
    private final BelgeDurumuServisi belgeler;
    private final AdaletServisi adalet;
    private final IzinServisi izinler;
    private final AyarServisi ayarlar;
    private final TakasServisi takaslar;
    private final MakinistServisi makinistler;
    private final KonaklamaServisi konaklamalar;
    private final Clock saat;

    BenimServisi(AtamaServisi atamalar, GordumServisi gordumlar, IntikalServisi intikaller, BelgeDurumuServisi belgeler, AdaletServisi adalet, IzinServisi izinler, AyarServisi ayarlar, TakasServisi takaslar,
            MakinistServisi makinistler, KonaklamaServisi konaklamalar, Clock saat) {
        this.konaklamalar = konaklamalar;
        this.takaslar = takaslar;
        this.makinistler = makinistler;
        this.atamalar = atamalar;
        this.gordumlar = gordumlar;
        this.intikaller = intikaller;
        this.belgeler = belgeler;
        this.adalet = adalet;
        this.izinler = izinler;
        this.ayarlar = ayarlar;
        this.saat = saat;
    }

    public GorevlerDto gorevler(Kisi kisi) {
        return gorevler(kisi, LocalDate.now(saat));
    }

    /** Verilen günü "bugün" sayar (testte sabit gün). Ufuk: bugün .. bugün + (makinist.gorunur_gun - 1); iptal sefer dahil. */
    public GorevlerDto gorevler(Kisi kisi, LocalDate bugun) {
        int ufuk = ayarlar.tamsayi(GORUNUR_GUN);
        return new GorevlerDto(bugun, ufuk, atamalar.makinistGorevleri(kisi.makinistId(), bugun, bugun.plusDays(ufuk - 1L)), gordumlar.kaldirilanlar(kisi.makinistId()),
                takaslar.bekleyenSayisi(kisi.makinistId()), takaslar.acikAtamalari(kisi.makinistId()),
                intikaller.makinistinIntikalleri(kisi.makinistId(), bugun, bugun.plusDays(ufuk - 1L)));
    }

    // ---- takas / bırakma (F2a-3): iş kuralları TakasServisi'nde; burada yalnız oturumdaki makinist verilir

    public List<MakinistTakasDto> takaslar(Kisi kisi) {
        return takaslar.makinistinTalepleri(kisi.makinistId());
    }

    public List<TakasHedefDto> takasHedefler(Kisi kisi, long atamaId) {
        return takaslar.hedefler(kisi.makinistId(), atamaId);
    }

    @Transactional
    public MakinistTakasDto takasAc(Kisi kisi, TakasAcIstegi istek) {
        return takaslar.ac(kisi.makinistId(), istek);
    }

    @Transactional(noRollbackFor = TakasKapanisHatasi.class)
    public MakinistTakasDto takasKabul(Kisi kisi, long talepId) {
        return takaslar.kabul(kisi.makinistId(), talepId);
    }

    @Transactional
    public MakinistTakasDto takasRed(Kisi kisi, long talepId) {
        return takaslar.red(kisi.makinistId(), talepId);
    }

    @Transactional
    public MakinistTakasDto takasIptal(Kisi kisi, long talepId) {
        return takaslar.iptal(kisi.makinistId(), talepId);
    }

    /** "Gördüm" (FR-M08): kendi atamasını görünürlük ufku içinde onaylar; yabancı/ufuk dışı atama 404; ekrandaki özet güncelle uyuşmazsa 409. */
    @Transactional
    public MakinistGorevDto gordum(Kisi kisi, long atamaId, String ekrandakiOzet) {
        LocalDate bugun = LocalDate.now(saat);
        return gordumlar.gordum(kisi.makinistId(), atamaId, bugun, bugun.plusDays(ayarlar.tamsayi(GORUNUR_GUN) - 1L), ekrandakiOzet);
    }

    /** Serbest intikal "gördüm" (MK-108): kendi intikali, görev ufku içinde; yabancı/ufuk dışı 404; ekrandaki özet güncelle uyuşmazsa 409 {@code OZET_DEGISTI}. */
    @Transactional
    public MakinistIntikalDto intikalGordum(Kisi kisi, long intikalId, String ekrandakiOzet) {
        LocalDate bugun = LocalDate.now(saat);
        return intikaller.gordum(kisi.makinistId(), intikalId, bugun, bugun.plusDays(ayarlar.tamsayi(GORUNUR_GUN) - 1L), ekrandakiOzet);
    }

    /** Elinden alınan görev kalemini kapatır (FR-M09); yabancı kalem 404. */
    @Transactional
    public void olayiKapat(Kisi kisi, long olayId) {
        gordumlar.olayiKapat(kisi.makinistId(), olayId);
    }

    public MakinistBelgeDto belgeler(Kisi kisi) {
        return belgeler(kisi, LocalDate.now(saat));
    }

    public MakinistBelgeDto belgeler(Kisi kisi, LocalDate bugun) {
        return belgeler.makinistinBelgeleri(kisi.makinistId(), bugun);
    }

    /** {@code ay} boşsa bu ay. Adalet raporunun kendi satırı + ortalama (MK-30). */
    public MakinistOzetDto ozet(Kisi kisi, YearMonth ay) {
        return adalet.makinistOzeti(ay == null ? YearMonth.now(saat) : ay, kisi.makinistId());
    }

    /** OK-10 (F2b): makinistin kendi konaklaması, görev ufkuyla aynı pencerede (bugün .. bugün + makinist.gorunur_gun - 1); başkasının kaydı dönmez, not alanı yok. */
    public List<MakinistKonaklamaDto> konaklamalar(Kisi kisi) {
        LocalDate bugun = LocalDate.now(saat);
        return konaklamalar.makinistinKonaklamalari(kisi.makinistId(), bugun, bugun.plusDays(ayarlar.tamsayi(GORUNUR_GUN) - 1L));
    }

    public List<MakinistIzinDto> izinler(Kisi kisi) {
        return izinler.makinistinIzinleri(kisi.makinistId());
    }

    /** Kendi adına izin talebi: başlangıç bugünden önce olamaz [V5]; çakışma 409; açan oturumdaki makinist hesabıdır (talep TALEP olarak atamacı onayına düşer). */
    @Transactional
    public MakinistIzinDto izinTalepEt(Kisi kisi, LocalDate baslangic, LocalDate bitis, IzinTuru tur) {
        makinistler.kilitle(List.of(kisi.makinistId())); // MK-7: açık talep sayımı ile yazım serileşsin; paralel isteklerle sınır (ve çakışma denetimi) aşılmasın (05-review Tur 2 W2)
        LocalDate bugun = LocalDate.now(saat);
        if (baslangic.isBefore(bugun)) {
            throw UygulamaHatasi.dogrulama("baslangic", "Başlangıç tarihi geçmişte olamaz. Geçmiş izin için atamacınıza başvurun.");
        }
        int enIleri = ayarlar.tamsayi(IZIN_EN_ILERI_GUN);
        if (baslangic.isAfter(bugun.plusDays(enIleri))) {
            throw UygulamaHatasi.dogrulama("baslangic", "Başlangıç tarihi bugünden en çok " + enIleri + " gün sonrası olabilir.");
        }
        int enUzun = ayarlar.tamsayi(IZIN_EN_UZUN_GUN);
        if (!bitis.isBefore(baslangic) && java.time.temporal.ChronoUnit.DAYS.between(baslangic, bitis) + 1 > enUzun) {
            throw UygulamaHatasi.dogrulama("bitis", "Bir izin talebi en çok " + enUzun + " gün olabilir. Daha uzun izin için atamacınıza başvurun.");
        }
        izinler.cakisanIzin(kisi.makinistId(), baslangic, bitis).ifPresent(c -> {
            throw UygulamaHatasi.cakisma("IZIN_CAKISMASI", "Bu tarihler " + GUN.format(c.baslangic()) + " – " + GUN.format(c.bitis()) + " tarihli "
                    + (c.durum() == IzinDurumu.ONAY ? "onaylı" : "onay bekleyen") + " izninizle çakışıyor.");
        });
        int enCokAcik = ayarlar.tamsayi(IZIN_EN_COK_ACIK);
        if (izinler.acikTalepSayisi(kisi.makinistId()) >= enCokAcik) {
            throw UygulamaHatasi.cakisma("ACIK_TALEP_SINIRI", "Onay bekleyen en çok " + enCokAcik + " izin talebiniz olabilir. Önce bekleyenlerin sonuçlanmasını bekleyin.");
        }
        var kayit = izinler.talepEt(kisi.makinistId(), baslangic, bitis, tur);
        return new MakinistIzinDto(kayit.id(), kayit.baslangic(), kayit.bitis(), kayit.tur(), kayit.durum());
    }
}
