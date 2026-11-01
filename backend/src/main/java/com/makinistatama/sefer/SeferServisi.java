package com.makinistatama.sefer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Sefer CRUD (FR-02). Makinist bilmez (atama {@code atama} paketindedir, MK-1). {@code tarih} planlanan kalkıştan hesaplanır, gecikmeyle kaymaz
 * (MK-3). (tarih, tren no) benzersizdir (409 {@code SEFER_TEKRARI}). Varış kalkıştan küçük/eşitse ertesi gün; süre 24 saati aşarsa 400.
 */
@Service
@Transactional(readOnly = true)
public class SeferServisi {

    static final ZoneId ISTANBUL = ZamanAyari.BOLGE;

    private final SeferRepository depo;
    private final TanimServisi tanimlar;
    private final DegisiklikServisi degisiklik;

    SeferServisi(SeferRepository depo, TanimServisi tanimlar, DegisiklikServisi degisiklik) {
        this.depo = depo;
        this.tanimlar = tanimlar;
        this.degisiklik = degisiklik;
    }

    /** Toplam sefer sayısı (kurulum durumu: "ilk sefer yüklendi mi"). */
    public long sayi() {
        return depo.count();
    }

    /** Günün seferleri (planlanan gün), planlanan kalkış sırasıyla. */
    public List<SeferDto> gunListesi(LocalDate gun) {
        return dto(depo.findByTarihOrderByPlanlananKalkisAscTrenNoAsc(gun));
    }

    /** [bas, bit] (planlanan gün, iki uç dahil) seferleri, planlanan kalkış sırasıyla. */
    public List<SeferDto> aralikListesi(LocalDate bas, LocalDate bit) {
        return dto(depo.findByTarihBetweenOrderByPlanlananKalkisAscTrenNoAsc(bas, bit));
    }

    /** Verilen kimliklerin seferleri (kimlik -> sefer); olmayanlar yok. Rapor ekranları için toplu okuma. */
    public Map<Long, SeferDto> kimliklerle(java.util.Collection<Long> idler) {
        if (idler.isEmpty()) {
            return Map.of();
        }
        Map<Long, SeferDto> sonuc = new java.util.HashMap<>();
        dto(depo.findAllById(idler)).forEach(s -> sonuc.put(s.id(), s));
        return sonuc;
    }

    /** Tren numarası (büyük/küçük harf duyarsız) eşleşen seferlerin kimlikleri. */
    public List<Long> trenNoIle(String trenNo) {
        return depo.findByTrenNoIgnoreCase(trenNo.strip()).stream().map(Sefer::id).toList();
    }

    /**
     * İptal (FR-07): sefer IPTAL olur, planlanan saatler korunur; neden zorunlu. Atamalara dokunulmaz (kırmızı canlı hesaplanır, MK-5).
     * Parçalı trende {@code devam} (boş = true): girilen parça ve SONRAKİ iptal olmayan parçalar iptal edilir, önceki (yapılmış) parçalara dokunulmaz (MK-63);
     * her iptal edilen parça için ayrı {@code SEFER_IPTAL} kaydı (gördüm/bayat hesabı sefer bazlıdır).
     */
    @Transactional
    public SeferDto iptalEt(long id, SeferIptalIstegi istek) {
        DegisiklikServisi.nedenDogrula(istek.neden(), istek.nedenNotu());
        Sefer s = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Sefer bulunamadı."));
        if (s.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "Sefer zaten iptal edilmiş.");
        }
        iptalYaz(s, istek, false);
        if (devam(s, istek.devam())) {
            sonrakiParcalar(s).stream().filter(p -> p.durum() != SeferDurumu.IPTAL).forEach(p -> iptalYaz(p, istek, true));
        }
        return getir(s.id());
    }

    private void iptalYaz(Sefer s, SeferIptalIstegi istek, boolean yayilan) {
        s.iptalEt();
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_IPTAL, "SEFER", s.id()).sefer(s.id()).degisim(SeferDurumu.PLANLI, SeferDurumu.IPTAL)
                .neden(istek.neden(), istek.nedenNotu())
                .ayrinti("{\"trenNo\":\"" + s.trenNo() + "\",\"tarih\":\"" + s.tarih() + "\"" + (yayilan ? ",\"yayilan\":true" : "") + "}"));
    }

    /**
     * Gecikme (FR-07): planlanan saat saklı kalır, güncel saat = planlanan + gecikme; varış gecikmesi boşsa kalkışınkine eşit. Parçalı trende {@code devam}
     * (boş = true): V = girilen parçanın varış gecikmesi; sonraki iptal olmayan her parçanın kalkış ve varış gecikmesi {@code max(mevcut, V)} olur, sonra
     * V = o parçanın varışı (kademeli; MK-62). Yalnız YÜKSELTİR: azaltma yayılmaz, elle girilmiş daha büyük değer düşmez. Hepsi doğrulanmadan yazılmaz.
     */
    @Transactional
    public SeferDto gecikmeGir(long id, SeferGecikmeIstegi istek) {
        DegisiklikServisi.nedenDogrula(istek.neden(), istek.nedenNotu());
        Sefer s = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Sefer bulunamadı."));
        if (s.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "İptal edilmiş sefere gecikme girilemez.");
        }
        int kalkis = istek.kalkisGecikmeDk();
        int varis = istek.varisGecikmeDk() == null ? kalkis : istek.varisGecikmeDk();
        long sureSn = Duration.between(s.planlananKalkis(), s.planlananVaris()).getSeconds();
        if (!guncelSureGecerli(s.planlananKalkis(), s.planlananVaris(), kalkis, varis)) { // BUG-11: güncel varış güncel kalkıştan sonra olmalı
            long enaz = Math.floorDiv(60L * kalkis - sureSn, 60) + 1;
            throw UygulamaHatasi.dogrulama("varisGecikmeDk", "Varış, kalkıştan sonra olmalı: kalkış " + kalkis + " dk gecikmişken varış gecikmesi en az " + enaz + " dk olmalı.");
        }
        List<Sefer> yayilacak = new java.util.ArrayList<>();
        List<int[]> yeniDegerler = new java.util.ArrayList<>();
        if (devam(s, istek.devam())) {
            int v = varis;
            for (Sefer p : sonrakiParcalar(s)) {
                if (p.durum() == SeferDurumu.IPTAL) {
                    continue; // iptal parça atlanır ama zincir sürer
                }
                int pk = Math.max(p.kalkisGecikmeDk(), v);
                int pv = Math.max(p.varisGecikmeDk(), v);
                if (!guncelSureGecerli(p.planlananKalkis(), p.planlananVaris(), pk, pv)) {
                    throw UygulamaHatasi.dogrulama("varisGecikmeDk", p.parcaNo() + ". parça (" + p.planlananKalkis().atZone(ISTANBUL).toLocalTime() + "-"
                            + p.planlananVaris().atZone(ISTANBUL).toLocalTime() + ") için " + pk + "/" + pv + " dk gecikme geçersiz: varış kalkıştan sonra olmalı. Hiçbir değişiklik yapılmadı; 'sonraki parçalara yansıt' seçeneğini kapatıp parçaları ayrı girin.");
                }
                if (pk != p.kalkisGecikmeDk() || pv != p.varisGecikmeDk()) {
                    yayilacak.add(p);
                    yeniDegerler.add(new int[] { pk, pv });
                }
                v = pv;
            }
        }
        gecikmeYaz(s, kalkis, varis, istek, false);
        for (int i = 0; i < yayilacak.size(); i++) {
            gecikmeYaz(yayilacak.get(i), yeniDegerler.get(i)[0], yeniDegerler.get(i)[1], istek, true);
        }
        return getir(s.id());
    }

    /** Tek sefere gecikme yazar ve değişiklik kaydı düşer (çekirdek; yayılan parçalar da aynı yoldan geçer). */
    private void gecikmeYaz(Sefer s, int kalkis, int varis, SeferGecikmeIstegi istek, boolean yayilan) {
        String eski = guncelSaatler(s);
        s.gecikmeYaz(kalkis, varis);
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_GECIKME, "SEFER", s.id()).sefer(s.id()).degisim(eski, guncelSaatler(s))
                .neden(istek.neden(), istek.nedenNotu())
                .ayrinti("{\"trenNo\":\"" + s.trenNo() + "\",\"tarih\":\"" + s.tarih() + "\",\"kalkisGecikmeDk\":" + kalkis + ",\"varisGecikmeDk\":" + varis
                        + (yayilan ? ",\"yayilan\":true" : "") + "}"));
    }

    /** Kısayol yalnız parçalı seferde; boş {@code devam} = true (V6, eski istemciler uyumlu). */
    private static boolean devam(Sefer s, Boolean devam) {
        return s.parcaNo() != null && !Boolean.FALSE.equals(devam);
    }

    /** Aynı tren gününün (MK-60) girilen parçadan SONRAKİ parçaları, parça sırasıyla; parçasızda boş. */
    private List<Sefer> sonrakiParcalar(Sefer s) {
        if (s.parcaNo() == null) {
            return List.of();
        }
        return depo.findByTrenNoAndTrenGunuOrderByParcaNo(s.trenNo(), s.trenGunu()).stream().filter(p -> p.parcaNo() > s.parcaNo()).toList();
    }

    /** Güncel varış (planlanan + varış gecikmesi) güncel kalkıştan (planlanan + kalkış gecikmesi) sonra mı. */
    private static boolean guncelSureGecerli(Instant planlananKalkis, Instant planlananVaris, int kalkisGecikmeDk, int varisGecikmeDk) {
        return Duration.between(planlananKalkis, planlananVaris).getSeconds() + 60L * (varisGecikmeDk - kalkisGecikmeDk) > 0;
    }

    private static String guncelSaatler(Sefer s) {
        return s.guncelKalkis().atZone(ISTANBUL).toLocalTime() + "-" + s.guncelVaris().atZone(ISTANBUL).toLocalTime();
    }

    public SeferDto getir(long id) {
        return dto(List.of(depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Sefer bulunamadı.")))).getFirst();
    }

    @Transactional
    public SeferDto olustur(SeferIstegi istek) {
        gecerliMi(istek);
        Instant[] saatler = saatler(istek);
        String trenNo = istek.trenNo().strip();
        LocalDate tarih = saatler[0].atZone(ISTANBUL).toLocalDate();
        if (depo.trenVarMi(tarih, trenNo)) {
            throw tekrar(tarih, trenNo);
        }
        Sefer s = new Sefer(trenNo, tarih);
        s.guncelle(trenNo, tarih, istek.kalkisGarId(), istek.varisGarId(), saatler[0], saatler[1], istek.aracTipiId(), istek.hatId(), istek.km(), istek.trenTuru());
        try {
            s = depo.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) { // eşzamanlı aynı anahtar
            throw tekrar(tarih, trenNo);
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_EKLE, "SEFER", s.id()).sefer(s.id()).degisim(null, tarih + " " + trenNo));
        return getir(s.id());
    }

    @Transactional
    public SeferDto guncelle(long id, SeferIstegi istek) {
        if (istek.versiyon() == null) {
            throw UygulamaHatasi.dogrulama("versiyon", "Sürüm bilgisi gerekli.");
        }
        Sefer s = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Sefer bulunamadı."));
        if (s.versiyon() != istek.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        gecerliMi(istek);
        Instant[] saatler = saatler(istek);
        String trenNo = istek.trenNo().strip();
        if (s.parcaNo() != null && !trenNo.equals(s.trenNo())) { // zincir tren no'ya bağlı (MK-60)
            throw UygulamaHatasi.dogrulama("trenNo", "Parçalı seferin tren no'su değiştirilemez.");
        }
        LocalDate tarih = saatler[0].atZone(ISTANBUL).toLocalDate();
        if (s.parcaNo() == null && (!tarih.equals(s.tarih()) || !trenNo.equals(s.trenNo())) && depo.trenVarMi(tarih, trenNo)) { // parçalıda tekillik indekse bırakılır
            throw tekrar(tarih, trenNo);
        }
        String eski = ozet(s);
        if (!guncelSureGecerli(saatler[0], saatler[1], s.kalkisGecikmeDk(), s.varisGecikmeDk())) { // mevcut gecikme yeni planlanan saatlerle varışı kalkıştan öne düşürür
            throw UygulamaHatasi.dogrulama("varisSaat", "Bu saatlerle mevcut gecikme (" + s.kalkisGecikmeDk() + "/" + s.varisGecikmeDk() + " dk) varışı kalkıştan öne düşürür. Önce gecikmeyi düzeltin.");
        }
        s.guncelle(trenNo, tarih, istek.kalkisGarId(), istek.varisGarId(), saatler[0], saatler[1], istek.aracTipiId(), istek.hatId(), istek.km(), istek.trenTuru() != null ? istek.trenTuru() : s.trenTuru()); // gönderilmeyen tür silinmez (Excel'den gelen veri düzenlemede kaybolmasın)
        try {
            depo.flush();
        } catch (DataIntegrityViolationException e) {
            throw tekrar(tarih, trenNo);
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_GUNCELLE, "SEFER", s.id()).sefer(s.id()).degisim(eski, ozet(s)));
        return getir(s.id());
    }

    // ------------------------------------------------------------------ yardımcılar

    /** Tanım kimlikleri doğru tipte mi (gar/gar/araç tipi/hat). Yükleme servisi de aynı denetimi kullanır. */
    void gecerliMi(SeferIstegi istek) {
        tanimlar.bul(TanimTipi.GAR, istek.kalkisGarId(), "kalkisGarId");
        tanimlar.bul(TanimTipi.GAR, istek.varisGarId(), "varisGarId");
        tanimlar.bul(TanimTipi.ARAC_TIPI, istek.aracTipiId(), "aracTipiId");
        tanimlar.bul(TanimTipi.HAT, istek.hatId(), "hatId");
    }

    /** [planlanan kalkış, planlanan varış]; süre > 24 saat ya da varış tarihi kalkıştan önce ise 400. */
    static Instant[] saatler(SeferIstegi istek) {
        Instant kalkis = istek.tarih().atTime(istek.kalkisSaat()).atZone(ISTANBUL).toInstant();
        LocalDate varisGunu = istek.varisTarihi() != null ? istek.varisTarihi()
                : istek.varisSaat().isAfter(istek.kalkisSaat()) ? istek.tarih() : istek.tarih().plusDays(1);
        Instant varis = varisGunu.atTime(istek.varisSaat()).atZone(ISTANBUL).toInstant();
        if (!varis.isAfter(kalkis)) {
            throw UygulamaHatasi.dogrulama("varisSaat", "Varış, kalkıştan sonra olmalı.");
        }
        if (Duration.between(kalkis, varis).compareTo(Duration.ofHours(24)) > 0) {
            throw UygulamaHatasi.dogrulama("varisSaat", "Sefer süresi 24 saati aşamaz.");
        }
        return new Instant[] { kalkis, varis };
    }

    private List<SeferDto> dto(List<Sefer> seferler) {
        Set<Long> idler = new HashSet<>();
        seferler.forEach(s -> {
            idler.add(s.kalkisGarId());
            idler.add(s.varisGarId());
            idler.add(s.aracTipiId());
            idler.add(s.hatId());
        });
        Map<Long, String> ad = idler.isEmpty() ? Map.of() : tanimlar.adlar(idler);
        return seferler.stream().map(s -> new SeferDto(s.id(), s.trenNo(), s.tarih(), s.kalkisGarId(), ad.get(s.kalkisGarId()), s.varisGarId(),
                ad.get(s.varisGarId()), s.planlananKalkis().atZone(ISTANBUL).toOffsetDateTime(), s.planlananVaris().atZone(ISTANBUL).toOffsetDateTime(),
                s.kalkisGecikmeDk(), s.varisGecikmeDk(), s.guncelKalkis().atZone(ISTANBUL).toOffsetDateTime(),
                s.guncelVaris().atZone(ISTANBUL).toOffsetDateTime(), s.aracTipiId(), ad.get(s.aracTipiId()), s.hatId(), ad.get(s.hatId()), s.km(),
                s.durum(), s.versiyon(), s.trenTuru(), s.parcaNo(), s.parcaSayisi(), s.trenGunu())).toList();
    }

    private static String ozet(Sefer s) {
        return s.tarih() + " " + s.trenNo() + " " + s.planlananKalkis().atZone(ISTANBUL).toLocalTime() + "-"
                + s.planlananVaris().atZone(ISTANBUL).toLocalTime() + " G" + s.kalkisGarId() + ">G" + s.varisGarId() + " AT" + s.aracTipiId()
                + " H" + s.hatId() + " km=" + s.km() + (s.trenTuru() == null ? "" : " " + s.trenTuru());
    }

    private static UygulamaHatasi tekrar(LocalDate tarih, String trenNo) {
        return UygulamaHatasi.cakisma("SEFER_TEKRARI", tarih.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " tarihli " + trenNo + " numaralı sefer zaten var.");
    }
}
