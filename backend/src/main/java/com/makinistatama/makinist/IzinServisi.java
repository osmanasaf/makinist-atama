package com.makinistatama.makinist;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * İzin talebi ve onayı (FR-11, T13.3). Talep TALEP olarak açılır; ONAY/RED kararı bir kez verilir (karar veren kayda yazılır; eşzamanlı ikinci karar iyimser kilitle 409,
 * W5). ATAMACI ve YONETICI karar verebilir, kimse kendi talebini onaylayamaz (403, OK-4); makinistin TALEP/ONAY izniyle çakışan yeni izin 409 (BUG-15). Çakışan atama / yedek açığı
 * bu yanıtlara GÖMÜLMEZ ({@code makinist → atama} döngüsü olmasın, MK-15): arayüz onaydan sonra {@code GET /api/atama/makinist/{id}/izin-etkisi} ve
 * {@code GET /api/rapor/yedek} uçlarını çağırır. Kural motoru yalnız ONAY'ı sayar (K-09). Kartta doğrudan "izin ekle" (ONAY) ayrıca durur.
 */
@Service
@Transactional(readOnly = true)
public class IzinServisi {

    private final IzinRepository izinler;
    private final MakinistRepository makinistler;
    private final DegisiklikServisi degisiklik;

    IzinServisi(IzinRepository izinler, MakinistRepository makinistler, DegisiklikServisi degisiklik) {
        this.izinler = izinler;
        this.makinistler = makinistler;
        this.degisiklik = degisiklik;
    }

    /** Tüm izinler (yeni başlangıç önce); {@code durum} boşsa hepsi. */
    public List<IzinKaydiDto> liste(IzinDurumu durum) {
        List<Izin> secilen = izinler.findAll(Sort.by(Sort.Direction.DESC, "baslangic", "id")).stream().filter(i -> durum == null || i.durum() == durum).toList();
        return dto(secilen);
    }

    /** Personel ucu ({@code POST /api/izin}): makinist gövdeden gelir; yalnız ATAMACI/YONETICI çağırır. Makinist kendi talebini {@code benim} ucundan açar. */
    @Transactional
    public IzinKaydiDto talepEt(IzinTalepIstegi istek) {
        return talepEt(istek.makinistId(), istek.baslangic(), istek.bitis(), istek.tur());
    }

    /** Talep çekirdeği (ortak): hem personel ucu hem makinistin kendi ucu buraya düşer; açan, oturumdaki hesaptır. */
    @Transactional
    public IzinKaydiDto talepEt(long makinistId, LocalDate baslangic, LocalDate bitis, IzinTuru tur) {
        Makinist m = makinistler.findById(makinistId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        if (bitis.isBefore(baslangic)) {
            throw UygulamaHatasi.dogrulama("bitis", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        Izin.cakismaDenetle(izinler, m.id(), baslangic, bitis);
        Izin izin = izinler.save(new Izin(m.id(), baslangic, bitis, tur, IzinDurumu.TALEP, DegisiklikServisi.simdikiKullaniciAdi(),
                DegisiklikServisi.simdikiKullaniciId().orElse(null)));
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.IZIN_TALEP, "IZIN", izin.id()).makinist(m.id()).degisim(null, tur + " " + baslangic + ".." + bitis));
        return dto(List.of(izin)).getFirst();
    }

    /** Makinistin onay bekleyen (TALEP) izin sayısı: makinist ucundaki açık talep sınırı için. */
    public long acikTalepSayisi(long makinistId) {
        return izinler.countByMakinistIdAndDurum(makinistId, IzinDurumu.TALEP);
    }

    /** [bas, bit] ile kesişen ilk TALEP/ONAY izni (yoksa boş): makinist ucu çakışmayı kendi izniyle açıklar. */
    public java.util.Optional<MakinistIzinDto> cakisanIzin(long makinistId, LocalDate bas, LocalDate bit) {
        return izinler.cakisanlar(makinistId, bas, bit).stream().findFirst().map(MakinistIzinDto::of);
    }

    /** Makinistin kendi izinleri (TALEP/ONAY/RED), yeni başlangıç önce; açan/karar veren personel adı verilmez. */
    public List<MakinistIzinDto> makinistinIzinleri(long makinistId) {
        return izinler.findByMakinistIdInOrderByBaslangic(List.of(makinistId)).stream().sorted(java.util.Comparator.comparing(Izin::baslangic).reversed())
                .map(MakinistIzinDto::of).toList();
    }

    @Transactional
    public IzinKaydiDto onayla(long id) {
        return karar(id, IzinDurumu.ONAY, DegisiklikTuru.IZIN_ONAY);
    }

    @Transactional
    public IzinKaydiDto reddet(long id) {
        return karar(id, IzinDurumu.RED, DegisiklikTuru.IZIN_RED);
    }

    private IzinKaydiDto karar(long id, IzinDurumu yeni, DegisiklikTuru tur) {
        Izin izin = izinler.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("İzin bulunamadı."));
        if (izin.durum() != IzinDurumu.TALEP) {
            throw UygulamaHatasi.cakisma("IZIN_KARARLI", "Bu izin için karar zaten verilmiş (" + izin.durum() + ").");
        }
        if (yeni == IzinDurumu.ONAY && izin.olusturanKullaniciId() != null && DegisiklikServisi.simdikiKullaniciId().filter(izin.olusturanKullaniciId()::equals).isPresent()) {
            throw UygulamaHatasi.yasak("KENDI_TALEBI", "Kendi açtığınız izin talebini onaylayamazsınız; başka bir yetkili onaylamalı.");
        }
        izin.kararVer(yeni, DegisiklikServisi.simdikiKullaniciAdi());
        izinler.flush();
        degisiklik.kaydet(Kayit.of(tur, "IZIN", izin.id()).makinist(izin.makinistId()).degisim(IzinDurumu.TALEP, yeni));
        return dto(List.of(izin)).getFirst();
    }

    private List<IzinKaydiDto> dto(List<Izin> liste) {
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, Makinist> kisiler = makinistler.findAllById(liste.stream().map(Izin::makinistId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Makinist::id, m -> m));
        return liste.stream().map(i -> new IzinKaydiDto(i.id(), i.makinistId(), kisiler.get(i.makinistId()).sicil(), kisiler.get(i.makinistId()).adSoyad(),
                i.baslangic(), i.bitis(), i.tur(), i.durum(), i.olusturan(), i.kararVeren())).toList();
    }
}
