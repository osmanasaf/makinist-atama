package com.makinistatama.konaklama;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.kural.Gorev;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Dış garda konaklama (F2b, MK-43): kayıt (ekle / düzelt / sil, çakışma denetimli), öneri ve makinistin kendi okuması. Kural motorunu ETKİLEMEZ: hiçbir atama kuralı
 * konaklamaya bakmaz; tek bağ {@link AtamaServisi#gorevler} (okuma). Her yazım aynı işlemde denetim kaydı üretir.
 */
@Service
@Transactional(readOnly = true)
public class KonaklamaServisi {

    private static final int EN_COK_ARALIK_GUN = 62;
    private static final DateTimeFormatter AN = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final KonaklamaRepository depo;
    private final MakinistServisi makinistler;
    private final TanimServisi tanimlar;
    private final AtamaServisi atamalar;
    private final AyarServisi ayarlar;
    private final ParametreServisi parametreler;
    private final DegisiklikServisi degisiklik;

    KonaklamaServisi(KonaklamaRepository depo, MakinistServisi makinistler, TanimServisi tanimlar, AtamaServisi atamalar, AyarServisi ayarlar, ParametreServisi parametreler,
            DegisiklikServisi degisiklik) {
        this.depo = depo;
        this.makinistler = makinistler;
        this.tanimlar = tanimlar;
        this.atamalar = atamalar;
        this.ayarlar = ayarlar;
        this.parametreler = parametreler;
        this.degisiklik = degisiklik;
    }

    // ------------------------------------------------------------------ kayıt

    /** [bas, bit] (İstanbul günü, iki uç dahil) ile kesişen kayıtlar, girişe göre; {@code makinistId} boşsa hepsi. */
    public List<KonaklamaDto> liste(LocalDate bas, LocalDate bit, Long makinistId) {
        aralikDogrula(bas, bit);
        return dto(depo.kesisenler(gunBasi(bas), gunBasi(bit.plusDays(1)), makinistId));
    }

    @Transactional
    public KonaklamaDto ekle(KonaklamaIstegi istek) {
        MakinistDto m = dogrula(istek);
        makinistler.kilitle(List.of(m.id())); // aynı makinistin kesişen kaydını yarışta iki kez yazmasınlar
        cakismaDenetle(istek, null);
        Konaklama k = depo.saveAndFlush(new Konaklama(istek.makinistId(), istek.garId(), istek.yerTipi(), istek.giris().toInstant(), istek.cikis().toInstant(), notu(istek)));
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.KONAKLAMA_EKLE, "KONAKLAMA", k.id()).makinist(m.id()).degisim(null, ozet(k)));
        return dto(List.of(k)).getFirst();
    }

    @Transactional
    public KonaklamaDto guncelle(long id, KonaklamaIstegi istek) {
        Konaklama k = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Konaklama kaydı bulunamadı."));
        if (istek.versiyon() == null) {
            throw UygulamaHatasi.dogrulama("versiyon", "Kayıt sürümü gerekli.");
        }
        if (istek.versiyon() != k.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        MakinistDto m = dogrula(istek);
        makinistler.kilitle(List.of(m.id()));
        cakismaDenetle(istek, id);
        String eski = ozet(k);
        k.degistir(istek.makinistId(), istek.garId(), istek.yerTipi(), istek.giris().toInstant(), istek.cikis().toInstant(), notu(istek));
        depo.saveAndFlush(k);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.KONAKLAMA_GUNCELLE, "KONAKLAMA", k.id()).makinist(m.id()).degisim(eski, ozet(k)));
        return dto(List.of(k)).getFirst();
    }

    @Transactional
    public void sil(long id) {
        Konaklama k = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Konaklama kaydı bulunamadı."));
        depo.delete(k);
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.KONAKLAMA_SIL, "KONAKLAMA", k.id()).makinist(k.makinistId()).degisim(ozet(k), null));
    }

    /** Doğrulama: makinist var, gar bir GAR tanımı, zamanlar makul aralıkta ve çıkış girişten sonra; makinisti döner. */
    private MakinistDto dogrula(KonaklamaIstegi istek) {
        MakinistDto m = makinistler.getir(istek.makinistId()); // 404
        tanimlar.bul(TanimTipi.GAR, istek.garId(), "garId");
        if (istek.giris().getYear() < 2000 || istek.giris().getYear() > 2100) {
            throw UygulamaHatasi.dogrulama("giris", "Tarih 2000 ile 2100 arasında olmalı.");
        }
        if (istek.cikis().getYear() < 2000 || istek.cikis().getYear() > 2100) {
            throw UygulamaHatasi.dogrulama("cikis", "Tarih 2000 ile 2100 arasında olmalı.");
        }
        if (!istek.cikis().toInstant().isAfter(istek.giris().toInstant())) {
            throw UygulamaHatasi.dogrulama("cikis", "Çıkış zamanı girişten sonra olmalı.");
        }
        return m;
    }

    private void cakismaDenetle(KonaklamaIstegi istek, Long haricId) {
        if (depo.cakisanVarMi(istek.makinistId(), istek.giris().toInstant(), istek.cikis().toInstant(), haricId)) {
            throw UygulamaHatasi.cakisma("KONAKLAMA_CAKISMASI", "Bu makinistin bu aralıkla kesişen başka bir konaklama kaydı var.");
        }
    }

    private static String notu(KonaklamaIstegi istek) {
        return istek.notu() == null || istek.notu().isBlank() ? null : istek.notu().strip();
    }

    /** Denetim kaydı metni: "Sivas · OTEL · 14.10 11:30 → 15.10 06:00". */
    private String ozet(Konaklama k) {
        return tanimlar.adlar(List.of(k.garId())).get(k.garId()) + " · " + k.yerTipi() + " · " + k.giris().atZone(ZamanAyari.BOLGE).format(AN) + " → "
                + k.cikis().atZone(ZamanAyari.BOLGE).format(AN);
    }

    // ------------------------------------------------------------------ öneri

    /**
     * [bas, bit] (öneri boşluğunun BAŞLADIĞI gün) için konaklama önerileri: aktif makinistlerin (ya da tek makinistin) dış gardaki nöbetler arası boşlukları. Yazmaz.
     */
    public List<KonaklamaOneriDto> oner(LocalDate bas, LocalDate bit, Long makinistId) {
        return oner(bas, bit, makinistId, PersonelTuru.MAKINIST);
    }

    /** F3b (MK-89): {@code makinistId} boşsa yalnız {@code tur} türünün AKTIF personeli. */
    public List<KonaklamaOneriDto> oner(LocalDate bas, LocalDate bit, Long makinistId, PersonelTuru tur) {
        aralikDogrula(bas, bit);
        List<MakinistDto> kisiler = makinistId != null ? List.of(makinistler.getir(makinistId))
                : makinistler.aktifDetaylar(java.util.Set.of(tur));
        if (kisiler.isEmpty()) {
            return List.of();
        }
        List<Long> idler = kisiler.stream().map(MakinistDto::id).toList();
        Map<Long, List<Gorev>> gorevler = atamalar.gorevler(idler, bas, bit);
        var par = parametreler.yukle();
        long ayirmaDk = par.dakika("nobet.ayirma_saat");
        long minDk = 60L * ayarlar.tamsayi("konaklama.oneri_min_saat");
        long geceMinDk = ayarlar.tamsayi("konaklama.gece_min_dk");
        Instant altSinir = gunBasi(bas);
        Instant ustSinir = gunBasi(bit.plusDays(1));
        Map<Long, List<Konaklama>> kayitlar = depo.findByMakinistIdIn(idler).stream().collect(Collectors.groupingBy(Konaklama::makinistId));
        Map<Long, String> garAdlari = tanimlar.tumAdlar();
        List<KonaklamaOneriDto> sonuc = new ArrayList<>();
        for (MakinistDto m : kisiler) {
            for (OneriHesabi.Oneri o : OneriHesabi.hesapla(m.id(), m.bagliGarId(), gorevler.getOrDefault(m.id(), List.of()), ayirmaDk, minDk, par.saat("gece.baslangic"), par.saat("gece.bitis"), geceMinDk)) {
                if (o.bas().isBefore(altSinir) || !o.bas().isBefore(ustSinir)) {
                    continue;
                }
                boolean kayitli = kayitlar.getOrDefault(m.id(), List.of()).stream()
                        .anyMatch(k -> k.garId() == o.garId() && k.giris().isBefore(o.bit()) && k.cikis().isAfter(o.bas()));
                sonuc.add(new KonaklamaOneriDto(m.id(), m.sicil(), m.adSoyad(), o.garId(), garAdlari.get(o.garId()), o.bas().atZone(ZamanAyari.BOLGE).toOffsetDateTime(),
                        o.bit().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), o.sureDk(), o.gece(), kayitli));
            }
        }
        sonuc.sort(Comparator.comparing(KonaklamaOneriDto::bas).thenComparing(KonaklamaOneriDto::sicil));
        return sonuc;
    }

    // ------------------------------------------------------------------ makinistin kendi okuması (OK-10)

    /** Makinistin [bas, bit] (İstanbul günü) ile kesişen konaklamaları; yalnız KENDİ kayıtları (kimliği çağıran oturumdan verir), not alanı yok. */
    public List<MakinistKonaklamaDto> makinistinKonaklamalari(long makinistId, LocalDate bas, LocalDate bit) {
        Map<Long, String> garlar = tanimlar.tumAdlar();
        return depo.kesisenler(gunBasi(bas), gunBasi(bit.plusDays(1)), makinistId).stream()
                .map(k -> new MakinistKonaklamaDto(k.id(), garlar.get(k.garId()), k.yerTipi(), k.giris().atZone(ZamanAyari.BOLGE).toOffsetDateTime(),
                        k.cikis().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), KonaklamaHesabi.gece(k.giris(), k.cikis())))
                .toList();
    }

    // ------------------------------------------------------------------ yardımcılar

    private List<KonaklamaDto> dto(List<Konaklama> kayitlar) {
        if (kayitlar.isEmpty()) {
            return List.of();
        }
        Map<Long, MakinistDto.Ozet> kisiler = makinistler.liste(null, null).stream().collect(Collectors.toMap(MakinistDto.Ozet::id, o -> o));
        Map<Long, String> garlar = tanimlar.tumAdlar();
        return kayitlar.stream().map(k -> {
            MakinistDto.Ozet m = kisiler.get(k.makinistId());
            return new KonaklamaDto(k.id(), k.makinistId(), m == null ? null : m.sicil(), m == null ? null : m.adSoyad(), k.garId(), garlar.get(k.garId()), k.yerTipi(),
                    k.giris().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), k.cikis().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), KonaklamaHesabi.gece(k.giris(), k.cikis()),
                    k.notu(), k.versiyon());
        }).toList();
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
