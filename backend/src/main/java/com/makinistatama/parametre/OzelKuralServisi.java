package com.makinistatama.parametre;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.tanim.TanimDto;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Yönetici tanımlı kurallar (kural kurucu, FR-01/FR-04): ekleme, düzenleme ve kapatma (şiddet KAPALI; silme yok). Doğrulama tek yerde ({@link #dogrula}):
 * katalog birleşimi ve eşik sınırı ({@link Olcu}), ad benzersizliği, kapsam kimliklerinin varlığı ve tipi, en çok 50 aktif kural. Her yazma aynı işlemde
 * {@code KURAL_EKLE}/{@code KURAL_GUNCELLE} kaydı yazar (eski -> yeni tam JSON; geçmiş tanım buradan çıkar, MK-20). Önbellek yok: her {@link #aktifler()}
 * çağrısı en çok 50 satır okur.
 */
@Service
@Transactional(readOnly = true)
public class OzelKuralServisi {

    static final int EN_COK_AKTIF = 50;
    static final int EN_COK_KUME = 100;

    private final OzelKuralRepository depo;
    private final TanimServisi tanimlar;
    private final DegisiklikServisi degisiklik;
    private final JsonMapper json;
    private final Clock saat;

    OzelKuralServisi(OzelKuralRepository depo, TanimServisi tanimlar, DegisiklikServisi degisiklik, JsonMapper json, Clock saat) {
        this.depo = depo;
        this.tanimlar = tanimlar;
        this.degisiklik = degisiklik;
        this.json = json;
        this.saat = saat;
    }

    // ------------------------------------------------------------------ okuma

    /** Motora giren kurallar: KAPALI olmayanlar (kimlik sırasıyla). */
    public List<OzelKural> aktifler() {
        return depo.findBySiddetNotOrderByIdAsc(Siddet.KAPALI).stream().map(this::kural).toList();
    }

    public List<OzelKuralDto> liste() {
        List<OzelKuralKaydi> hepsi = depo.findAllByOrderByIdAsc();
        Map<Long, String> adlar = hepsi.isEmpty() ? Map.of() : tanimlar.tumAdlar();
        return hepsi.stream().map(k -> OzelKuralDto.of(k, kural(k), adlar)).toList();
    }

    /** Ölçü kataloğu (MK-22): form seçenekleri ve doğrulama aynı {@link Olcu} enum'undan; DB yok. */
    public List<OlcuDto> katalog() {
        List<OlcuDto> l = new ArrayList<>();
        for (Olcu o : Olcu.values()) {
            List<OlcuDto.PencereSecenegi> pencereler = new ArrayList<>();
            for (Pencere p : Pencere.values()) {
                if (o.izinliPencereler().contains(p)) {
                    Olcu.EsikAraligi a = o.esikAraligi(p);
                    pencereler.add(new OlcuDto.PencereSecenegi(p, p.ad(), p.aciklama(), a.alt(), a.altDahil(), a.ust()));
                }
            }
            List<OlcuDto.KarsilastirmaSecenegi> k = new ArrayList<>();
            for (Karsilastirma c : Karsilastirma.values()) {
                if (o.izinliKarsilastirmalar().contains(c)) {
                    k.add(new OlcuDto.KarsilastirmaSecenegi(c, c.ad(), c.aciklama()));
                }
            }
            l.add(new OlcuDto(o, o.ad(), o.birim(), o.aciklama(), pencereler, k));
        }
        return l;
    }

    // ------------------------------------------------------------------ yazma (YONETICI)

    @Transactional
    public OzelKuralDto ekle(OzelKuralIstegi istek) {
        depo.kilitle(); // sayım + yazma sıralı (50 açık kural sınırı)
        OzelKural taslak = dogrula(istek, 0, 0, null);
        OzelKuralKaydi k = new OzelKuralKaydi(DegisiklikServisi.simdikiKullaniciAdi(), saat.instant());
        yaz(k, taslak);
        try {
            k = depo.saveAndFlush(k);
        } catch (DataIntegrityViolationException e) { // eşzamanlı aynı ad
            throw adTekrari(taslak.ad());
        }
        OzelKural yeni = kural(k);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.KURAL_EKLE, "OZEL_KURAL", k.id()).degisim(null, jsonMetni(yeni))
                .neden(Neden.DIGER, nedenMetni(istek)).ayrinti(ayrinti(yeni)));
        return OzelKuralDto.of(k, yeni, tanimlar.tumAdlar());
    }

    @Transactional
    public OzelKuralDto guncelle(long id, OzelKuralIstegi istek) {
        depo.kilitle();
        OzelKuralKaydi k = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kural bulunamadı."));
        if (istek.versiyon() == null) {
            throw UygulamaHatasi.dogrulama("versiyon", "Sürüm bilgisi gerekli.");
        }
        if (k.versiyon() != istek.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        if (istek.neden() == null || istek.neden().isBlank()) {
            throw UygulamaHatasi.dogrulama("neden", "Değişiklik nedeni yazılmalı.");
        }
        OzelKural eski = kural(k);
        OzelKural taslak = dogrula(istek, id, k.versiyon(), k);
        if (eski.equals(taslak)) { // değişiklik yok: sürüm artmaz, denetim kaydı yazılmaz
            return OzelKuralDto.of(k, eski, tanimlar.tumAdlar());
        }
        yaz(k, taslak);
        try {
            depo.flush();
        } catch (DataIntegrityViolationException e) {
            throw adTekrari(taslak.ad());
        }
        OzelKural yeni = kural(k);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.KURAL_GUNCELLE, "OZEL_KURAL", k.id()).degisim(jsonMetni(eski), jsonMetni(yeni))
                .neden(Neden.DIGER, istek.neden().strip()).ayrinti(ayrinti(yeni)));
        return OzelKuralDto.of(k, yeni, tanimlar.tumAdlar());
    }

    // ------------------------------------------------------------------ doğrulama (önizleme de kullanır)

    /**
     * "Kuralı dene" için: kaydedilmemiş taslağı doğrular ve saf kurala çevirir (yazmaz). {@code duzenlenenId}: mevcut kuralın düzenlenmesiyse kimliği
     * (aynı kodlu kuralın YERİNE geçer), yeni kuralda {@code null} (kod {@code Ö-000}).
     */
    public OzelKural taslak(OzelKuralIstegi istek, Long duzenlenenId) {
        OzelKuralKaydi mevcut = duzenlenenId == null ? null : depo.findById(duzenlenenId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kural bulunamadı."));
        return dogrula(istek, mevcut == null ? 0 : mevcut.id(), mevcut == null ? 0 : mevcut.versiyon(), mevcut);
    }

    /**
     * İsteği doğrular ve saf {@link OzelKural} üretir. {@code id}: düzenlemede mevcut kimlik, yeni kuralda 0 (önizlemede {@code Ö-000}).
     * {@code mevcut}: düzenlenen satır (ad ve aktif sayısı kendisini hariç tutar). Yazmaz.
     */
    private OzelKural dogrula(OzelKuralIstegi istek, long id, long versiyon, OzelKuralKaydi mevcut) {
        String ad = istek.ad().strip().replaceAll("\\s+", " ");
        if (ad.isEmpty()) {
            throw UygulamaHatasi.dogrulama("ad", "Kural adı yazılmalı.");
        }
        String h = istek.olcu().pencereHatasi(istek.pencere());
        if (h != null) {
            throw UygulamaHatasi.dogrulama("pencere", h);
        }
        h = istek.olcu().karsilastirmaHatasi(istek.karsilastirma());
        if (h != null) {
            throw UygulamaHatasi.dogrulama("karsilastirma", h);
        }
        h = Olcu.kayanGunHatasi(istek.pencere(), istek.kayanGun());
        if (h != null) {
            throw UygulamaHatasi.dogrulama("kayanGun", h);
        }
        h = istek.olcu().esikHatasi(istek.pencere(), istek.esik(), istek.kayanGun());
        if (h != null) {
            throw UygulamaHatasi.dogrulama("esik", h);
        }
        Kapsam kapsam = istek.kapsam() == null ? Kapsam.TUMU : istek.kapsam();
        kapsamDogrula(kapsam);
        LocalDate bas = istek.gecerlilikBas() != null ? istek.gecerlilikBas()
                : mevcut != null ? mevcut.gecerlilikBas() : LocalDate.now(saat.withZone(ZamanHesabi.ISTANBUL)); // düzenlemede boş: mevcut korunur
        if (istek.gecerlilikBit() != null && istek.gecerlilikBit().isBefore(bas)) {
            throw UygulamaHatasi.dogrulama("gecerlilikBit", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        String gerekce = istek.gerekce().strip();
        if (gerekce.isEmpty()) {
            throw UygulamaHatasi.dogrulama("gerekce", "Gerekçe yazılmalı.");
        }
        adKontrol(ad, id);
        if (istek.siddet() != Siddet.KAPALI) {
            long aktif = depo.countBySiddetNot(Siddet.KAPALI) - (mevcut != null && mevcut.siddet() != Siddet.KAPALI ? 1 : 0);
            if (aktif >= EN_COK_AKTIF) {
                throw UygulamaHatasi.dogrulama("siddet", "En çok " + EN_COK_AKTIF + " açık özel kural olabilir; önce birini kapatın.");
            }
        }
        String kaynak = istek.kaynak() == null || istek.kaynak().isBlank() ? null : istek.kaynak().strip();
        return new OzelKural(id, ad, istek.olcu(), istek.pencere(), istek.kayanGun(), istek.karsilastirma(), sade(istek.esik()), kapsam, istek.siddet(), bas, istek.gecerlilikBit(),
                gerekce, kaynak, versiyon);
    }

    private void kapsamDogrula(Kapsam k) {
        kumeDogrula("hatlar", k.hatlar(), TanimTipi.HAT, "Seçilen hat bulunamadı.");
        kumeDogrula("aracTipleri", k.aracTipleri(), TanimTipi.ARAC_TIPI, "Seçilen araç tipi bulunamadı.");
        kumeDogrula("kalkisGarlari", k.kalkisGarlari(), TanimTipi.GAR, "Seçilen kalkış garı bulunamadı.");
        kumeDogrula("varisGarlari", k.varisGarlari(), TanimTipi.GAR, "Seçilen varış garı bulunamadı.");
        if (!Kapsam.TREN_TURLERI.containsAll(k.trenTurleri())) {
            throw UygulamaHatasi.dogrulama("kapsam", "Tren türü Yük ya da Yolcu olmalı.");
        }
    }

    private void kumeDogrula(String alan, Set<Long> idler, TanimTipi tip, String mesaj) {
        if (idler.size() > EN_COK_KUME) {
            throw UygulamaHatasi.dogrulama("kapsam", "Bir kapsam alanında en çok " + EN_COK_KUME + " seçim olabilir.");
        }
        if (idler.isEmpty()) {
            return;
        }
        Set<Long> var = tanimlar.liste(tip).stream().map(TanimDto::id).collect(Collectors.toSet());
        if (!var.containsAll(idler)) {
            throw UygulamaHatasi.dogrulama("kapsam", mesaj);
        }
    }

    private void adKontrol(String ad, long haricId) {
        String katli = AramaMetni.katla(ad);
        boolean var = depo.findAllByOrderByIdAsc().stream().anyMatch(k -> k.id() != haricId && AramaMetni.katla(k.ad()).equals(katli));
        if (var) {
            throw adTekrari(ad);
        }
    }

    private static UygulamaHatasi adTekrari(String ad) {
        return UygulamaHatasi.cakisma("KURAL_AD_TEKRARI", "'" + ad + "' adlı bir kural zaten var.");
    }

    // ------------------------------------------------------------------ dönüşümler

    private void yaz(OzelKuralKaydi k, OzelKural t) {
        k.yaz(t.ad(), t.olcu(), t.pencere(), t.kayanGun(), t.karsilastirma(), t.esik(), kapsamMetni(t.kapsam()), t.siddet(), t.gecerlilikBas(), t.gecerlilikBit(), t.gerekce(),
                t.kaynak(), saat.instant());
    }

    private OzelKural kural(OzelKuralKaydi k) {
        Kapsam kapsam;
        try {
            kapsam = json.readValue(k.kapsam(), Kapsam.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Kural kapsamı okunamadı: " + k.id(), e);
        }
        return new OzelKural(k.id(), k.ad(), k.olcu(), k.pencere(), k.kayanGun(), k.karsilastirma(), sade(k.esik()), kapsam, k.siddet(), k.gecerlilikBas(), k.gecerlilikBit(), k.gerekce(),
                k.kaynak(), k.versiyon());
    }

    private String kapsamMetni(Kapsam k) {
        try {
            return json.writeValueAsString(k);
        } catch (JacksonException e) {
            throw new IllegalStateException("Kural kapsamı yazılamadı", e);
        }
    }

    private String jsonMetni(OzelKural k) {
        try {
            return json.writeValueAsString(k);
        } catch (JacksonException e) {
            throw new IllegalStateException("Kural yazılamadı", e);
        }
    }

    /** 8.00 -> 8, 1E+2 -> 100: JSON ve karşılaştırma için kanonik biçim. */
    private static java.math.BigDecimal sade(java.math.BigDecimal b) {
        return new java.math.BigDecimal(b.stripTrailingZeros().toPlainString());
    }

    private static String ayrinti(OzelKural k) {
        return "{\"kod\":\"" + k.kod() + "\"}";
    }

    private static String nedenMetni(OzelKuralIstegi istek) {
        return istek.neden() != null && !istek.neden().isBlank() ? istek.neden().strip() : istek.gerekce().strip();
    }
}
