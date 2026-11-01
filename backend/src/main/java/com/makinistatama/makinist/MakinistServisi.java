package com.makinistatama.makinist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Makinist kartı (FR-01): CRUD, bröve kümesinin bütün olarak değiştirilmesi, izin ekleme. Sicil benzersizdir (409 {@code SICIL_TEKRARI});
 * eski {@code versiyon} ile güncelleme 409 {@code VERSIYON_CAKISMASI}. Her mutasyon aynı işlemde değişiklik kaydı yazar.
 */
@Service
@Transactional(readOnly = true)
public class MakinistServisi {

    private final MakinistRepository depo;
    private final BroveAracTipiRepository aracTipleri;
    private final BroveHatRepository hatlar;
    private final IzinRepository izinler;
    private final TanimServisi tanimlar;
    private final ParametreServisi parametreler;
    private final DegisiklikServisi degisiklik;

    MakinistServisi(MakinistRepository depo, BroveAracTipiRepository aracTipleri, BroveHatRepository hatlar, IzinRepository izinler,
            TanimServisi tanimlar, ParametreServisi parametreler, DegisiklikServisi degisiklik) {
        this.depo = depo;
        this.aracTipleri = aracTipleri;
        this.hatlar = hatlar;
        this.izinler = izinler;
        this.tanimlar = tanimlar;
        this.parametreler = parametreler;
        this.degisiklik = degisiklik;
    }

    /** Sicil / ad (Türkçe-duyarsız, içeren) ve durum süzgeciyle liste; TÜM türler (sicil/ad haritaları tür-bağımsızdır, MK-83). */
    public List<MakinistDto.Ozet> liste(String arama, MakinistDurum durum) {
        return liste(arama, durum, PersonelTuru.TUMU);
    }

    /** Kart ekranı için tür süzgeçli liste (MK-83). */
    public List<MakinistDto.Ozet> liste(String arama, MakinistDurum durum, Set<PersonelTuru> turler) {
        String aranan = arama == null ? "" : AramaMetni.katla(arama.strip());
        List<Makinist> secilen = depo.findAllByOrderBySicil().stream()
                .filter(m -> durum == null || m.durum() == durum)
                .filter(m -> turler.contains(m.tur()))
                .filter(m -> aranan.isEmpty() || AramaMetni.katla(m.sicil()).contains(aranan) || AramaMetni.katla(m.adSoyad()).contains(aranan))
                .toList();
        Map<Long, String> adlar = tanimlar.adlar(secilen.stream().map(Makinist::bagliGarId).collect(Collectors.toSet()));
        return secilen.stream().map(m -> new MakinistDto.Ozet(m.id(), m.sicil(), m.adSoyad(), m.bagliGarId(), adlar.get(m.bagliGarId()), m.durum(),
                m.tur(), m.ehliyetBitis(), m.saglikBitis(), m.psikoteknikBitis())).toList();
    }

    /**
     * Makinist satırlarını atama yazımı için kilitler (MK-7): sıralı kimlik (kilitlenme yok), en çok 5 sn beklenir. Çağıranın işlemi içinde olmalı.
     * Var olmayan kimlik 404.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void kilitle(java.util.Collection<Long> idler) {
        if (idler.isEmpty()) {
            return;
        }
        depo.kilitZamanAsimi();
        List<Makinist> kilitli = depo.findByIdInOrderById(new java.util.TreeSet<>(idler));
        if (kilitli.size() != new HashSet<>(idler).size()) {
            throw UygulamaHatasi.bulunamadi("Makinist bulunamadı.");
        }
    }

    /** Var olan tüm sicil kodları (toplu içe aktarma "zaten var" denetimi). */
    public Set<String> tumSiciller() {
        return new HashSet<>(depo.tumSiciller());
    }

    /** Veritabanında demo makinist ({@code Demo Makinist NN}, FR-14) var mı: raporlar "DEMO – gerçek değil" etiketi koyar. */
    public boolean demoVerisiVarMi() {
        return depo.existsByAdSoyadStartingWith("Demo Makinist ");
    }

    /** Verilen durumdaki makinist kimlikleri (varlık yüklenmez: kilitten ÖNCE çağrılsa bile sonradan okunan veri bayat kalmaz). */
    public List<Long> kimlikler(MakinistDurum durum, Set<PersonelTuru> turler) {
        return depo.kimlikler(durum, bos(turler));
    }

    /**
     * AKTIF personelin kart ayrıntısı (aday, otomatik atama, rapor ve boş neden hesapları; sicil sırasıyla). Tür ZORUNLUDUR (F3b, MK-83): sessiz "hepsi" varsayılanı yok;
     * bilinçli seçim için {@link PersonelTuru#MAKINISTLER}, {@link PersonelTuru#KONDUKTORLER}, {@link PersonelTuru#TUMU}. Boş küme programcı hatasıdır.
     */
    public List<MakinistDto> aktifDetaylar(Set<PersonelTuru> turler) {
        Set<PersonelTuru> istenen = bos(turler);
        return detaylar(null).stream().filter(m -> m.durum() == MakinistDurum.AKTIF && istenen.contains(m.tur())).toList();
    }

    /** Kuralların ve panoların toplu girdisi: verilen makinistlerin (null = hepsi) kart ayrıntısı, yan sorgular makinist sayısından bağımsız (N+1 yok). */
    public List<MakinistDto> detaylar(java.util.Collection<Long> idler) {
        List<Makinist> secilen = idler == null ? depo.findAllByOrderBySicil() : depo.findAllById(idler).stream().sorted(java.util.Comparator.comparing(Makinist::sicil)).toList();
        if (secilen.isEmpty()) {
            return List.of();
        }
        List<Long> idler2 = secilen.stream().map(Makinist::id).toList();
        int ay = (int) parametreler.yukle().tamsayi("belge.sinav_gecerlilik_ay");
        Map<Long, List<BroveAracTipi>> at = aracTipleri.findByMakinistIdIn(idler2).stream().collect(Collectors.groupingBy(BroveAracTipi::makinistId));
        Map<Long, List<BroveHat>> ht = hatlar.findByMakinistIdIn(idler2).stream().collect(Collectors.groupingBy(BroveHat::makinistId));
        Map<Long, List<Izin>> iz = izinler.findByMakinistIdInOrderByBaslangic(idler2).stream().collect(Collectors.groupingBy(Izin::makinistId));
        Map<Long, String> adlar = tanimlar.tumAdlar();
        return secilen.stream().map(m -> dto(m, ay, at.getOrDefault(m.id(), List.of()), ht.getOrDefault(m.id(), List.of()), iz.getOrDefault(m.id(), List.of()), adlar)).toList();
    }


    /** Hafif okuma (varlık yüklemeden): makinist yoksa boş. Hesap durum kapısı ({@code benim/BenimKapisi}) her istekte bunu çağırır. */
    public java.util.Optional<MakinistKarti> kart(long id) {
        return depo.findById(id).map(m -> new MakinistKarti(m.id(), m.sicil(), m.adSoyad(), m.durum(), m.tur()));
    }

    /** Kimlik -> hafif kart (sicil, ad, durum); yalnız verilenler, olmayanlar yok. */
    public Map<Long, MakinistKarti> kartlar(java.util.Collection<Long> idler) {
        if (idler.isEmpty()) {
            return Map.of();
        }
        return depo.findAllById(idler).stream().collect(Collectors.toMap(Makinist::id, m -> new MakinistKarti(m.id(), m.sicil(), m.adSoyad(), m.durum(), m.tur())));
    }

    /** Kimlik -> ad soyad (yalnız verilenler; olmayanlar yok). Eş makinist adı gibi salt ad gösterimi için. */
    public Map<Long, String> adlar(java.util.Collection<Long> idler) {
        if (idler.isEmpty()) {
            return Map.of();
        }
        return depo.findAllById(idler).stream().collect(Collectors.toMap(Makinist::id, Makinist::adSoyad));
    }

    public MakinistDto getir(long id) {
        Makinist m = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        return dto(m);
    }

    @Transactional
    public MakinistDto olustur(MakinistIstegi istek) {
        return dto(ekle(istek));
    }

    /** Tek doğrulama + yazma yolu ({@link #olustur} ve toplu içe aktarma): DTO kurmaz (satır başına parametre/tanım okuması toplu yüklemeyi yavaşlatırdı). */
    @Transactional
    Makinist ekle(MakinistIstegi istek) {
        String sicil = sicil(istek.sicil());
        if (depo.existsBySicil(sicil)) {
            throw sicilTekrari(sicil);
        }
        PersonelTuru tur = istek.tur() == null ? PersonelTuru.MAKINIST : istek.tur();
        gecerliMi(istek, tur);
        Makinist m = new Makinist(sicil, tur);
        m.guncelle(istek.adSoyad().strip(), istek.bagliGarId(), istek.durum(), istek.ehliyetBitis(), istek.saglikBitis(), istek.psikoteknikBitis(),
                istek.sonSurus());
        try {
            m = depo.saveAndFlush(m);
        } catch (DataIntegrityViolationException e) { // eşzamanlı aynı sicil
            throw sicilTekrari(sicil);
        }
        broveYaz(m.id(), istek);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.MAKINIST_EKLE, "MAKINIST", m.id()).makinist(m.id()).degisim(null, sicil));
        return m;
    }

    @Transactional
    public void guncelle(long id, MakinistIstegi istek) {
        if (istek.versiyon() == null) {
            throw UygulamaHatasi.dogrulama("versiyon", "Sürüm bilgisi gerekli.");
        }
        Makinist m = depo.findWithVersionById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        if (m.versiyon() != istek.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        String sicil = sicil(istek.sicil());
        if (!m.sicil().equals(sicil)) {
            throw UygulamaHatasi.dogrulama("sicil", "Sicil değiştirilemez.");
        }
        if (istek.tur() != null && istek.tur() != m.tur()) {
            throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "TUR_DEGISTIRILEMEZ",
                    "Kartın personel türü sonradan değiştirilemez; yanlış açıldıysa kartı pasife alıp yenisini açın.", null);
        }
        gecerliMi(istek, m.tur());

        Map<String, Object[]> fark = new LinkedHashMap<>();
        farkEkle(fark, "adSoyad", m.adSoyad(), istek.adSoyad().strip());
        farkEkle(fark, "bagliGarId", m.bagliGarId(), istek.bagliGarId());
        farkEkle(fark, "durum", m.durum(), istek.durum());
        farkEkle(fark, "ehliyetBitis", m.ehliyetBitis(), istek.ehliyetBitis());
        farkEkle(fark, "saglikBitis", m.saglikBitis(), istek.saglikBitis());
        farkEkle(fark, "psikoteknikBitis", m.psikoteknikBitis(), istek.psikoteknikBitis());
        farkEkle(fark, "sonSurus", m.sonSurus(), istek.sonSurus());
        String eskiBrove = broveOzeti(m.id());

        m.guncelle(istek.adSoyad().strip(), istek.bagliGarId(), istek.durum(), istek.ehliyetBitis(), istek.saglikBitis(), istek.psikoteknikBitis(),
                istek.sonSurus());
        aracTipleri.makinistinTumunuSil(m.id());
        hatlar.makinistinTumunuSil(m.id());
        broveYaz(m.id(), istek);
        farkEkle(fark, "brove", eskiBrove, broveOzeti(m.id()));
        try {
            depo.flush();
        } catch (DataIntegrityViolationException e) {
            throw UygulamaHatasi.versiyonCakismasi();
        }

        String eski = fark.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()[0]).collect(Collectors.joining("; "));
        String yeni = fark.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()[1]).collect(Collectors.joining("; "));
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.MAKINIST_GUNCELLE, "MAKINIST", m.id()).makinist(m.id()).degisim(eski, yeni));
    }

    @Transactional
    public MakinistDto.IzinDto izinEkle(long makinistId, IzinIstegi istek) {
        Makinist m = depo.findById(makinistId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        if (istek.bitis().isBefore(istek.baslangic())) {
            throw UygulamaHatasi.dogrulama("bitis", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        Izin.cakismaDenetle(izinler, m.id(), istek.baslangic(), istek.bitis());
        Izin izin = izinler.save(new Izin(m.id(), istek.baslangic(), istek.bitis(), istek.tur(), IzinDurumu.ONAY, DegisiklikServisi.simdikiKullaniciAdi(),
                DegisiklikServisi.simdikiKullaniciId().orElse(null)));
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.IZIN_EKLE, "IZIN", izin.id()).makinist(m.id())
                .degisim(null, istek.tur() + " " + istek.baslangic() + ".." + istek.bitis()));
        return MakinistDto.IzinDto.of(izin);
    }

    // ------------------------------------------------------------------ yardımcılar

    private MakinistDto dto(Makinist m) {
        int ay = (int) parametreler.yukle().tamsayi("belge.sinav_gecerlilik_ay");
        List<BroveAracTipi> at = aracTipleri.findByMakinistIdIn(List.of(m.id()));
        List<BroveHat> ht = hatlar.findByMakinistIdIn(List.of(m.id()));
        Set<Long> idler = new HashSet<>();
        idler.add(m.bagliGarId());
        at.forEach(a -> idler.add(a.aracTipiId()));
        ht.forEach(h -> idler.add(h.hatId()));
        return dto(m, ay, at, ht, izinler.findByMakinistIdInOrderByBaslangic(List.of(m.id())), tanimlar.adlar(idler));
    }

    private static MakinistDto dto(Makinist m, int ay, List<BroveAracTipi> at, List<BroveHat> ht, List<Izin> iz, Map<Long, String> adlar) {
        return new MakinistDto(m.id(), m.sicil(), m.adSoyad(), m.bagliGarId(), adlar.get(m.bagliGarId()), m.durum(), m.tur(), m.ehliyetBitis(),
                m.saglikBitis(), m.psikoteknikBitis(), m.sonSurus(), m.versiyon(),
                at.stream().map(a -> new MakinistDto.BroveAracTipiDto(a.aracTipiId(), adlar.get(a.aracTipiId()), a.sinavTarihi(),
                        a.sinavTarihi().plusMonths(ay))).sorted(java.util.Comparator.comparing(MakinistDto.BroveAracTipiDto::aracTipi)).toList(),
                ht.stream().map(h -> new MakinistDto.BroveHatDto(h.hatId(), adlar.get(h.hatId()), h.sinavTarihi(), h.sonSurus(),
                        h.sinavTarihi().plusMonths(ay))).sorted(java.util.Comparator.comparing(MakinistDto.BroveHatDto::hat)).toList(),
                iz.stream().map(MakinistDto.IzinDto::of).toList());
    }

    /** Tanım kimlikleri doğru tipte mi, bröve kümesinde tekrar var mı. */
    private void gecerliMi(MakinistIstegi istek, PersonelTuru tur) {
        if (tur == PersonelTuru.KONDUKTOR && !(liste(istek.broveAracTipleri()).isEmpty() && liste(istek.broveHatlar()).isEmpty())) {
            throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "KONDUKTOR_BROVESI_OLMAZ", "Kondüktör kartında araç tipi ve hat bröve bilgisi olmaz.", null);
        }
        tanimlar.bul(TanimTipi.GAR, istek.bagliGarId(), "bagliGarId");
        Set<Long> gorulen = new HashSet<>();
        for (var a : liste(istek.broveAracTipleri())) {
            tanimlar.bul(TanimTipi.ARAC_TIPI, a.aracTipiId(), "broveAracTipleri");
            if (!gorulen.add(a.aracTipiId())) {
                throw UygulamaHatasi.dogrulama("broveAracTipleri", "Aynı araç tipi iki kez girilemez.");
            }
        }
        gorulen.clear();
        for (var h : liste(istek.broveHatlar())) {
            tanimlar.bul(TanimTipi.HAT, h.hatId(), "broveHatlar");
            if (!gorulen.add(h.hatId())) {
                throw UygulamaHatasi.dogrulama("broveHatlar", "Aynı hat iki kez girilemez.");
            }
        }
    }

    private void broveYaz(long makinistId, MakinistIstegi istek) {
        liste(istek.broveAracTipleri()).forEach(a -> aracTipleri.save(new BroveAracTipi(makinistId, a.aracTipiId(), a.sinavTarihi())));
        liste(istek.broveHatlar()).forEach(h -> hatlar.save(new BroveHat(makinistId, h.hatId(), h.sinavTarihi(), h.sonSurus())));
        aracTipleri.flush();
    }

    private String broveOzeti(long makinistId) {
        Stream<String> a = aracTipleri.findByMakinistIdIn(List.of(makinistId)).stream().map(x -> "AT" + x.aracTipiId() + "@" + x.sinavTarihi());
        Stream<String> h = hatlar.findByMakinistIdIn(List.of(makinistId)).stream().map(x -> "H" + x.hatId() + "@" + x.sinavTarihi() + "/" + x.sonSurus());
        return Stream.concat(a, h).sorted().collect(Collectors.joining(","));
    }

    private static void farkEkle(Map<String, Object[]> fark, String alan, Object eski, Object yeni) {
        if (!Objects.equals(eski, yeni)) {
            fark.put(alan, new Object[] { eski, yeni });
        }
    }

    private static <T> List<T> liste(List<T> l) {
        return l == null ? new ArrayList<>() : l;
    }

    private static Set<PersonelTuru> bos(Set<PersonelTuru> turler) {
        if (turler == null || turler.isEmpty()) {
            throw new IllegalArgumentException("Personel türü kümesi boş olamaz.");
        }
        return turler;
    }

    private static String sicil(String ham) {
        return ham.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static UygulamaHatasi sicilTekrari(String sicil) {
        return UygulamaHatasi.cakisma("SICIL_TEKRARI", "'" + sicil + "' sicilli bir makinist zaten var.");
    }

}
