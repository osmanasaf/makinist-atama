package com.makinistatama.sefer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
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

import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.sefer.ParcaHesabi.Hata;
import com.makinistatama.sefer.ParcaHesabi.Parca;
import com.makinistatama.sefer.ParcaHesabi.Parcalar;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Seferi ekrandan bölme çekirdeği (MK-121). ATAMA BİLMEZ, makinist/sefer kilidi ALMAZ, denetim kaydı YAZMAZ: çağıran (atama orkestratörü) işlemine katılır.
 * Mevcut satır parça 1 olur (kimlik/sürüm/bağlar korunur), parça 2..N yeni satırlardır. Hesap {@link ParcaHesabi}'ndedir (Excel'le ortak, kopya yok); burada yalnız
 * ekrana özgü kurallar (gar tanımı ve farklılığı, km zorunluluğu) ve gecikme dağıtımı (MK-125) vardır.
 */
@Service
public class SeferParcaServisi {

    private final SeferRepository depo;
    private final TanimServisi tanimlar;
    private final SeferServisi seferler;

    SeferParcaServisi(SeferRepository depo, TanimServisi tanimlar, SeferServisi seferler) {
        this.depo = depo;
        this.tanimlar = tanimlar;
        this.seferler = seferler;
    }

    /**
     * Önkoşul sırası: 404, sürüm 409 {@code VERSIYON_CAKISMASI}, 409 {@code SEFER_IPTAL}, 409 {@code SEFER_ZATEN_PARCALI}, 422 {@code SEFER_BASLAMIS} (güncel kalkış <=
     * {@code simdi}), sonra 400 doğrulamalar. Hata halinde hiçbir şey yazılmaz. Parça süresi ekranda sınırlanmaz (azami null); son parça varışı seferin varışına eşit
     * çıkmalıdır (pencere kuralı).
     */
    @Transactional
    public BolSonucu bol(long id, SeferBolIstegi istek, Instant simdi) {
        Sefer s = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Sefer bulunamadı."));
        if (s.versiyon() != istek.versiyon()) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        if (s.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "İptal edilmiş sefer bölünemez.");
        }
        if (s.parcaNo() != null) {
            throw UygulamaHatasi.cakisma("SEFER_ZATEN_PARCALI", "Sefer zaten parçalı; yeniden bölmek için önce birleştirin.");
        }
        if (!s.guncelKalkis().isAfter(simdi)) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "SEFER_BASLAMIS", "Kalkışı geçmiş (başlamış) sefer bölünemez.", null);
        }
        List<ParcaHesabi.Nokta> noktalar = noktalar(s, istek);
        Parcalar hesap = switch (ParcaHesabi.parcala(s.tarih(), yerel(s.planlananKalkis()), yerel(s.planlananVaris()), s.kalkisGarId(), s.varisGarId(), s.aracTipiId(), s.hatId(),
                s.km(), noktalar, null, s.planlananVaris())) {
            case Hata h -> throw UygulamaHatasi.dogrulama("noktalar", (h.noktaSirasi() > 0 ? h.noktaSirasi() + ". ara istasyon: " : "") + h.govde());
            case Parcalar p -> p;
        };
        List<Parca> parcalar = hesap.liste();

        // gecikme dağıtımı (MK-125/V6): kalkış gecikmesi her parçada k; varış gecikmesi son parçada v, diğerlerinde k
        int k = s.kalkisGecikmeDk();
        int v = s.varisGecikmeDk();
        for (Parca p : parcalar) {
            int pv = p.parcaNo() == parcalar.size() ? v : k;
            if (Duration.between(p.kalkis(), p.varis()).getSeconds() + 60L * (pv - k) <= 0) { // SeferServisi.guncelSureGecerli ile aynı formül
                throw UygulamaHatasi.dogrulama("noktalar", p.parcaNo() + ". parça için mevcut gecikme (" + k + "/" + pv + " dk) varışı kalkıştan öne düşürür. Önce gecikmeyi düzeltin.");
            }
        }

        SeferDto eski = seferler.getir(id);
        Parca ilk = parcalar.getFirst();
        s.guncelle(s.trenNo(), ilk.tarih(), ilk.kalkisGarId(), ilk.varisGarId(), ilk.kalkis(), ilk.varis(), ilk.aracTipiId(), ilk.hatId(), ilk.km(), s.trenTuru());
        s.parcaYaz(ilk.parcaNo(), ilk.parcaSayisi(), ilk.trenGunu());
        s.gecikmeYaz(k, parcalar.size() == 1 ? v : k);
        List<Sefer> yeniler = new ArrayList<>();
        for (Parca p : parcalar.subList(1, parcalar.size())) {
            Sefer y = Sefer.parca(s.trenNo(), p.tarih(), p.kalkisGarId(), p.varisGarId(), p.kalkis(), p.varis(), p.aracTipiId(), p.hatId(), p.km(), s.trenTuru(), p.parcaNo(),
                    p.parcaSayisi(), p.trenGunu());
            y.gecikmeYaz(k, p.parcaNo() == parcalar.size() ? v : k);
            yeniler.add(y);
        }
        try {
            depo.saveAllAndFlush(yeniler);
        } catch (DataIntegrityViolationException e) { // gece aşan parça ertesi günün kendi (tarih, tren no, parça no)'suyla çakıştı ya da eşzamanlı ikinci bölme
            throw UygulamaHatasi.cakisma("SEFER_TEKRARI", "Bölünen parçalardan biri, aynı gün aynı tren no ve parça numarasıyla başka bir seferle çakışıyor "
                    + "(gece aşan trenin ertesi günkü kaydı olabilir). Hiçbir değişiklik yapılmadı.");
        }

        List<Long> idler = new ArrayList<>();
        idler.add(s.id());
        yeniler.forEach(y -> idler.add(y.id()));
        Map<Long, SeferDto> dto = seferler.kimliklerle(idler);
        List<SeferDto> sonuc = idler.stream().map(dto::get).toList();
        return new BolSonucu(sonuc.getFirst(), sonuc, ozet(eski), sonuc.stream().map(d -> d.parcaNo() + "/" + d.parcaSayisi() + " " + ozet(d)).collect(Collectors.joining(" | ")),
                idler.subList(1, idler.size()));
    }

    // ------------------------------------------------------------------ birleştirme (MK-126; atama bilmez: kilit sırası ve atama koşulları orkestratörde)

    /**
     * Sefer düzeyi birleştirme koşulları (kilitsiz okuma). Sıra: 404, sürüm 409 {@code VERSIYON_CAKISMASI} ({@code id} satırının sürümü; zincirin herhangi parçası olabilir),
     * 409 {@code SEFER_PARCALI_DEGIL}, 409 {@code ZINCIR_EKSIK}, 409 {@code PARCA_IPTAL}, 422 {@code SEFER_BASLAMIS}, 409 {@code BIRLESTIRME_SURE_ASIYOR}, 409 {@code BIRLESTIRME_HAT_FARKLI}.
     */
    @Transactional
    public Zincir birlestirKontrol(long id, long versiyon, Instant simdi) {
        Sefer s = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Sefer bulunamadı."));
        if (s.versiyon() != versiyon) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        if (s.parcaNo() == null) {
            throw UygulamaHatasi.cakisma("SEFER_PARCALI_DEGIL", "Sefer parçalı değil; birleştirilecek bir şey yok.");
        }
        List<Sefer> p = depo.findByTrenNoAndTrenGunuOrderByParcaNo(s.trenNo(), s.trenGunu());
        zincirKosullari(p, s.parcaSayisi(), simdi);
        return new Zincir(s.trenNo(), s.trenGunu(), p.stream().map(Sefer::id).toList());
    }

    /**
     * Parça 2..N satırlarını ARTAN kimlikle {@code PESSIMISTIC_WRITE} kilitler ve zinciri yeniden okur (MK-123: {@code ata(M2 -> parça 2)} ile silme kilitlenmesini önler). Çağıran önce makinist
     * kilitlerini almış olmalıdır (kilit sırası makinist -> parça satırı). Zincir kilit beklerken değiştiyse (satır silindi/eklendi) 409 {@code VERSIYON_CAKISMASI}.
     */
    @Transactional
    public Zincir birlestirKilitle(Zincir z) {
        List<Sefer> kilitli = depo.zinciriKilitle(z.idler());
        List<Long> guncel = depo.findByTrenNoAndTrenGunuOrderByParcaNo(z.trenNo(), z.trenGunu()).stream().map(Sefer::id).toList();
        if (kilitli.size() != z.idler().size() || !guncel.equals(z.idler())) {
            throw UygulamaHatasi.versiyonCakismasi();
        }
        return new Zincir(z.trenNo(), z.trenGunu(), guncel);
    }

    /**
     * Zinciri parça 1 satırına geri yazar (id, sürüm denetimi, bağlar korunur), parça 2..N satırlarını siler. Atamalar/gördüm temizliği ÇAĞIRANDA yapılmış olmalıdır (FK
     * {@code atama.sefer_id}; sıra atama -> gördüm -> sefer). Koşullar kilit sonrası yeniden denetlenir. Denetim kaydı YAZMAZ.
     */
    @Transactional
    public BirlestirSonucu birlestir(Zincir z, Instant simdi) {
        List<Sefer> p = depo.findByTrenNoAndTrenGunuOrderByParcaNo(z.trenNo(), z.trenGunu());
        if (p.isEmpty() || p.getFirst().parcaSayisi() == null) { // kilit sonrası zincir başkasınca birleştirildi
            throw UygulamaHatasi.versiyonCakismasi();
        }
        zincirKosullari(p, p.getFirst().parcaSayisi(), simdi);
        Map<Long, SeferDto> dto = seferler.kimliklerle(z.idler());
        List<SeferDto> eski = z.idler().stream().map(dto::get).toList();

        Sefer ilk = p.getFirst();
        Sefer son = p.getLast();
        BigDecimal km = p.stream().anyMatch(x -> x.km() == null) ? null : p.stream().map(Sefer::km).reduce(BigDecimal.ZERO, BigDecimal::add);
        ilk.birlestirYaz(son.varisGarId(), son.planlananVaris(), km, ilk.kalkisGecikmeDk(), son.varisGecikmeDk());
        depo.deleteAll(p.subList(1, p.size()));
        depo.flush();

        SeferDto yeni = seferler.getir(ilk.id());
        return new BirlestirSonucu(yeni, z.silinecekIdler(), eski.stream().map(d -> d.parcaNo() + "/" + d.parcaSayisi() + " " + ozet(d)).collect(Collectors.joining(" | ")), ozet(yeni), eski);
    }

    private static void zincirKosullari(List<Sefer> p, int parcaSayisi, Instant simdi) {
        boolean tam = p.size() == parcaSayisi;
        for (int i = 0; tam && i < p.size(); i++) {
            tam = p.get(i).parcaNo() != null && p.get(i).parcaNo() == i + 1;
        }
        if (!tam) {
            throw UygulamaHatasi.cakisma("ZINCIR_EKSIK", "Treni oluşturan parçalardan bazıları yok (beklenen " + parcaSayisi + " parça, bulunan " + p.size() + "). Birleştirilemez.");
        }
        for (Sefer x : p) {
            if (x.durum() == SeferDurumu.IPTAL) {
                throw UygulamaHatasi.cakisma("PARCA_IPTAL", x.parcaNo() + ". parça iptal edilmiş; önce iptali düzeltin, sonra birleştirin.");
            }
        }
        Sefer ilk = p.getFirst();
        Sefer son = p.getLast();
        if (!ilk.guncelKalkis().isAfter(simdi)) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "SEFER_BASLAMIS", "Kalkışı geçmiş (başlamış) sefer birleştirilemez.", null);
        }
        Duration sure = Duration.between(ilk.planlananKalkis(), son.planlananVaris());
        if (sure.compareTo(Duration.ofHours(24)) > 0) {
            throw UygulamaHatasi.cakisma("BIRLESTIRME_SURE_ASIYOR", "Birleşik sefer " + sure.toHours() + " s " + sure.toMinutesPart() + " dk sürer; en çok 24 saat olabilir. Bu tren birleştirilemez.");
        }
        for (Sefer x : p.subList(1, p.size())) {
            if (!Objects.equals(x.hatId(), ilk.hatId())) {
                throw UygulamaHatasi.cakisma("BIRLESTIRME_HAT_FARKLI", x.parcaNo() + ". parçanın hattı 1. parçadan farklı; birleşik satır tek hat taşır. Önce parçaların hattını eşitleyin.");
            }
            if (!Objects.equals(x.aracTipiId(), ilk.aracTipiId())) {
                throw UygulamaHatasi.cakisma("BIRLESTIRME_HAT_FARKLI", x.parcaNo() + ". parçanın araç tipi 1. parçadan farklı; birleşik satır tek araç tipi taşır. Önce parçaların araç tipini eşitleyin.");
            }
            if (x.trenTuru() != ilk.trenTuru()) {
                throw UygulamaHatasi.cakisma("BIRLESTIRME_HAT_FARKLI", x.parcaNo() + ". parçanın tren türü 1. parçadan farklı; birleşik satır tek tür taşır. Önce parçaların türünü eşitleyin.");
            }
        }
        if (!son.guncelVaris().isAfter(ilk.guncelKalkis())) { // sefer_guncel_sure_ck: birleşik güncel varış güncel kalkıştan sonra olmalı (parçalara ayrı gecikme girilmişse bozulabilir)
            throw UygulamaHatasi.cakisma("BIRLESTIRME_GECIKME_UYUMSUZ", "Birleşik seferin güncel varışı güncel kalkışından önce kalıyor (1. parçanın kalkış, son parçanın varış gecikmesi). Önce gecikmeleri düzeltin.");
        }
    }

    /** Ekrana özgü kurallar + {@link ParcaHesabi}'nin girdisi. */
    private List<ParcaHesabi.Nokta> noktalar(Sefer s, SeferBolIstegi istek) {
        List<SeferBolIstegi.Nokta> girilen = istek.noktalar();
        if (girilen == null || girilen.isEmpty()) {
            throw UygulamaHatasi.dogrulama("noktalar", "En az bir ara istasyon girilmeli.");
        }
        if (girilen.size() > ParcaHesabi.EN_FAZLA_NOKTA) {
            throw UygulamaHatasi.dogrulama("noktalar", "Ara istasyon en çok " + ParcaHesabi.EN_FAZLA_NOKTA + " olabilir (parça en çok " + (ParcaHesabi.EN_FAZLA_NOKTA + 1) + ").");
        }
        Set<Long> gorulen = new HashSet<>();
        List<ParcaHesabi.Nokta> sonuc = new ArrayList<>();
        for (int i = 0; i < girilen.size(); i++) {
            SeferBolIstegi.Nokta n = girilen.get(i);
            String onek = (i + 1) + ". ara istasyon: ";
            if (n == null || n.garId() == null || n.varisSaat() == null || n.kalkisSaat() == null) {
                throw UygulamaHatasi.dogrulama("noktalar", onek + "gar, varış saati ve kalkış saati gerekli.");
            }
            tanimDogrula(TanimTipi.GAR, n.garId(), onek);
            if (n.hatId() != null) {
                tanimDogrula(TanimTipi.HAT, n.hatId(), onek);
            }
            if (n.aracTipiId() != null) {
                tanimDogrula(TanimTipi.ARAC_TIPI, n.aracTipiId(), onek);
            }
            if (n.garId().equals(s.kalkisGarId()) || n.garId().equals(s.varisGarId())) {
                throw UygulamaHatasi.dogrulama("noktalar", onek + "gar seferin kalkış ve varış garından farklı olmalı.");
            }
            if (!gorulen.add(n.garId())) {
                throw UygulamaHatasi.dogrulama("noktalar", onek + "gar başka bir ara istasyonda da var; garlar yinelenemez.");
            }
            if (n.kalkisSaat().isBefore(n.varisSaat())) {
                throw UygulamaHatasi.dogrulama("noktalar", onek + "Kalkış saati varış saatinden önce olamaz.");
            }
            if (s.km() != null && n.km() == null) {
                throw UygulamaHatasi.dogrulama("noktalar", onek + "KM zorunlu (seferin KM'si dolu; boş bırakmak parça KM'sini siler).");
            }
            if (s.km() == null && n.km() != null) {
                throw UygulamaHatasi.dogrulama("noktalar", onek + "seferin KM'si boş olduğundan KM girilemez.");
            }
            sonuc.add(new ParcaHesabi.Nokta(i + 1, n.garId(), n.varisSaat(), n.kalkisSaat(), n.km(), n.hatId(), n.aracTipiId()));
        }
        return sonuc;
    }

    private void tanimDogrula(TanimTipi tip, long id, String onek) {
        try {
            tanimlar.bul(tip, id, "noktalar");
        } catch (UygulamaHatasi e) {
            throw UygulamaHatasi.dogrulama("noktalar", onek + e.getMessage());
        }
    }

    private static LocalTime yerel(Instant an) {
        return an.atZone(SeferServisi.ISTANBUL).toLocalTime();
    }

    private static String ozet(SeferDto d) {
        return (d.parcaNo() == null ? d.trenNo() + " " : "") + d.kalkisGar() + " " + d.planlananKalkis().toLocalTime() + " > " + d.varisGar() + " " + d.planlananVaris().toLocalTime();
    }
}
