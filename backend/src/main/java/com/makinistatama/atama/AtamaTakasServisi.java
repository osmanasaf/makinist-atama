package com.makinistatama.atama;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/**
 * Takasın atama tarafı (AtamaServisi'nden ayrıldı, MK-47; F2a-3, MK-35): iki yönlü kural kontrolü (yazmaz) ve karşılıklı takasın tek işlemde uygulanması. Yalnız {@code TakasServisi} çağırır.
 * Denetim kaydı ve "başkasına verildi" olayı için {@link AtamaServisi#anlikGoruntu} ve {@link AtamaServisi#olayYaz} paket-içi yöntemleri kullanılır (OtomatikAtamaServisi kalıbı).
 */
@Service
@Transactional(readOnly = true)
public class AtamaTakasServisi {

    private final AtamaRepository depo;
    private final AtamaServisi atamalar;
    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final BaglamYukleyici yukleyici;
    private final DegisiklikServisi degisiklik;
    private final GorevGordumRepository gordumlar;
    private final Clock saat;

    AtamaTakasServisi(AtamaRepository depo, AtamaServisi atamalar, SeferServisi seferler, MakinistServisi makinistler, BaglamYukleyici yukleyici, DegisiklikServisi degisiklik,
            GorevGordumRepository gordumlar, Clock saat) {
        this.depo = depo;
        this.atamalar = atamalar;
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.yukleyici = yukleyici;
        this.degisiklik = degisiklik;
        this.gordumlar = gordumlar;
        this.saat = saat;
    }

    private record TakasTaraf(Atama atama, SeferDto sefer, MakinistDto makinist) {
    }

    private static UygulamaHatasi takasBayat() {
        return UygulamaHatasi.cakisma("TALEP_BAYAT", "Talep bayatladı: görevlerden biri değişti ya da kaldırıldı. Talebi yenileyin.");
    }

    /** Atamayı yeniden okur; talep anındaki makinist/sefer/özetle aynı değilse 409 {@code TALEP_BAYAT}. Çağıran makinist kilitlerini ÖNCE almış olmalıdır (ilk varlık okuması kilit sonrası). */
    private TakasTaraf takasTarafi(TakasTarafi t) {
        Atama a = depo.findById(t.atamaId()).orElseThrow(AtamaTakasServisi::takasBayat);
        SeferDto s = seferler.getir(a.seferId());
        if (!a.makinistId().equals(t.makinistId()) || !a.seferId().equals(t.seferId()) || !GorevOzeti.hesapla(a, s).equals(t.ozet())) {
            throw takasBayat();
        }
        return new TakasTaraf(a, s, makinistler.detaylar(List.of(a.makinistId())).getFirst());
    }

    /**
     * {@code m}, {@code aday} seferi alır ve {@code biraktigi} seferi bırakır. (1) Aday TÜM komşularıyla (bırakılan çıkarılmış) {@code degerlendirYazma}. (2) W2/MK-5: elde kalan
     * HER görev takas sonrası görev kümesinde canlı kırmızı hesabıyla yeniden değerlendirilir; takas öncesinde olmayan yeni ENGEL (ör. bırakılan köprü görevi kalkınca nöbetin ikiye
     * bölünüp dinlenmenin yetmemesi) ihlal olarak döner: önceden kırmızı olan görev takası engellemez. Kural motoru ve {@code BaglamYukleyici} hesabı değişmez; bırakılan sefer yüklemeden çıkarılır ({@code Yuk.seferiBirak}) ki bitişik parçaların hazırlık / alıkoyması gerçek durumda hesaplansın.
     */
    private List<Ihlal> takasDegerlendir(MakinistDto m, SeferDto aday, SeferDto biraktigi, AtamaRol adayRol) {
        Yuk y = yukleyici.yukleAralik(aday.tarih().isBefore(biraktigi.tarih()) ? aday.tarih() : biraktigi.tarih(),
                aday.tarih().isAfter(biraktigi.tarih()) ? aday.tarih() : biraktigi.tarih(), List.of(m));
        List<AtamaSatiri> once = List.copyOf(y.satirlar(m.id())); // takas ÖNCESİ gerçek durum (DB)
        y.seferiBirak(m.id(), biraktigi, atamaId -> depo.findById(atamaId).orElseThrow().kalkisGecikmeDk()); // bırakılan yok: komşu parçaların hazırlık / alıkoyması geri gelir (W2)
        y.sefereBagla(aday);
        List<Gorev> diger = y.digerGorevler(m.id());
        List<Ihlal> sonuc = new ArrayList<>(KuralMotoru.degerlendirYazma(y.baglam(m, y.adayGorev(m.id()), diger, y.simdi())));
        List<AtamaSatiri> kalan = y.satirlar(m.id()); // bırakılan çıkarılmış, aday zincir komşularını güncellemiş
        boolean[] bag = Yuk.komsuBayraklari(kalan, aday); // OK-17: aday satırı gerçek bağa göre
        List<AtamaSatiri> sonra = new ArrayList<>(kalan);
        sonra.add(new AtamaSatiri(-1L, aday.id(), adayRol, m.id(), aday.tarih(), aday.trenNo(), aday.kalkisGarId(), aday.varisGarId(), aday.hatId(), aday.aracTipiId(),
                aday.planlananKalkis().toInstant(), aday.planlananVaris().toInstant(), aday.kalkisGecikmeDk(), aday.varisGecikmeDk(), aday.km(), aday.kalkisGecikmeDk(), aday.trenTuru(), aday.parcaNo(), aday.trenGunu(), bag[0], bag[1]));
        for (AtamaSatiri s : sonra) {
            if (s.seferId().equals(aday.id())) {
                continue; // aday yukarıda iki yönlü değerlendirildi
            }
            Set<String> eskiEngel = canliEngelKodlari(y, m, s, once);
            for (Ihlal i : canliEngel(y, m, s, sonra)) {
                if (!eskiEngel.contains(i.kod()) && sonuc.stream().noneMatch(h -> h.siddet() == Siddet.ENGEL && h.kod().equals(i.kod()))) { // adayın kendi iki yönlü ENGEL'i aynı ihlali zaten taşır
                    sonuc.add(new Ihlal(i.kod(), i.siddet(), i.olculen(), i.esik(), i.birim(), "Elinizde kalan " + s.trenNo() + " görevi kırmızı olur: " + i.metin()));
                }
            }
        }
        return sonuc;
    }

    /** {@code s} görevinin, {@code kume} içindeki sorumlu komşularıyla canlı kırmızı hesabındaki (KirmiziAtamaHesabi ile aynı) ENGEL'leri. */
    private static List<Ihlal> canliEngel(Yuk y, MakinistDto m, AtamaSatiri s, List<AtamaSatiri> kume) {
        Gorev g = y.gorev(s);
        return KuralMotoru.degerlendir(y.baglam(m, g, y.komsular(kume, s.tarih(), s.seferId(), g), null)).stream().filter(i -> i.siddet() == Siddet.ENGEL).toList();
    }

    private static Set<String> canliEngelKodlari(Yuk y, MakinistDto m, AtamaSatiri s, List<AtamaSatiri> kume) {
        return canliEngel(y, m, s, kume).stream().map(Ihlal::kod).collect(Collectors.toSet());
    }

    private static List<TakasIhlali> takasIhlalleri(TakasTaraf taraf, List<Ihlal> ihlaller, boolean yalnizEngel) {
        return ihlaller.stream().filter(i -> !yalnizEngel || i.siddet() == Siddet.ENGEL).map(i -> new TakasIhlali(taraf.makinist().sicil(), IhlalDto.of(i))).toList();
    }

    private void takasDogrula(TakasTaraf a, TakasTaraf b) {
        // MK-90: takas yalnız aynı personel türü içinde; her kişi aldığı yuvanın rolüne uygun olmalı (açılış ve uygulama anı aynı kapıdan geçer)
        if (a.makinist().tur() != b.makinist().tur() || !b.atama().rol().turler().contains(a.makinist().tur()) || !a.atama().rol().turler().contains(b.makinist().tur())) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "PERSONEL_TURU_UYMUYOR", "Takas yalnız aynı personel türü arasında yapılabilir (makinist-makinist, kondüktör-kondüktör).", null);
        }
        if (a.sefer().id().equals(b.sefer().id())) {
            throw UygulamaHatasi.dogrulama("hedefAtamaId", "Aynı seferdeki iki görev takas edilemez.");
        }
        if (depo.findBySeferIdAndMakinistId(b.sefer().id(), a.makinist().id()).isPresent() || depo.findBySeferIdAndMakinistId(a.sefer().id(), b.makinist().id()).isPresent()) {
            throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", "Taraflardan biri takas edilecek seferde zaten görevli.");
        }
    }

    /**
     * Karşılıklı takasın iki yönlü kural kontrolü (yazmaz): A için SA komşu listeden çıkarılıp SB aday, B için tersi. Dönüş: iki tarafın TÜM ihlalleri (ENGEL ve UYARI). Bayat → 409.
     * Çağıran, iki makinistin kilidini önceden almış olmalıdır (onay) ya da sonucu yalnız gösterir (ayrıntı ekranı).
     */
    public List<TakasIhlali> takasKontrol(TakasTarafi kaynak, TakasTarafi hedef) {
        TakasTaraf a = takasTarafi(kaynak);
        TakasTaraf b = takasTarafi(hedef);
        takasDogrula(a, b);
        List<TakasIhlali> sonuc = new ArrayList<>(takasIhlalleri(a, takasDegerlendir(a.makinist(), b.sefer(), a.sefer(), b.atama().rol()), false));
        sonuc.addAll(takasIhlalleri(b, takasDegerlendir(b.makinist(), a.sefer(), b.sefer(), a.atama().rol()), false));
        return sonuc;
    }

    /**
     * Talebi AÇAN personelin kendi tarafı için ENGEL metinleri (yazmaz; boş = engel yok). Karşı tarafın kural/belge durumu bilerek HESAPLANMAZ (W4): açılış yanıtı karşı tarafı hakkında bilgi sızdırmasın; karşı tarafın
     * ihlali yalnız atamacı ayrıntısında / onayında görünür.
     */
    public List<String> takasKendiTarafiEngelMetinleri(TakasTarafi kendi, TakasTarafi diger) {
        TakasTaraf a = takasTarafi(kendi);
        TakasTaraf b = takasTarafi(diger);
        takasDogrula(a, b);
        return takasDegerlendir(a.makinist(), b.sefer(), a.sefer(), b.atama().rol()).stream().filter(i -> i.siddet() == Siddet.ENGEL).map(Ihlal::metin).toList();
    }

    /**
     * Karşılıklı takas, tek işlemde (MK-35): iki makinist TEK çağrıda artan kimlikle kilitlenir (MK-34/4: çağıran talep satırından ÖNCE almıştır; tekrar zararsız), iki atama kilit altında yeniden
     * okunup talep anıyla karşılaştırılır (bayat → 409 {@code TALEP_BAYAT}), iki yönlü {@code degerlendirYazma}: herhangi bir ENGEL → 422 {@code KURAL_IHLALI}, hiçbir şey yazılmaz. UYARI varsa
     * gerekçe zorunlu. Yazım: ÖNCE iki satır sil + flush, SONRA iki satır ekle (UNIQUE ara durumda çakışmaz); rol yuvayla gider; iki {@code ATAMA_DEGISTIR} ortak {@code takas} kimliğiyle.
     * Yazan kayıt sayısı: yazma kontrolü ENGEL'siz olduğundan canlı kırmızı üretmez (MK-5; {@code TakasMk5Test}).
     */
    @Transactional
    public TakasUygulamaSonucu takasUygula(TakasTarafi kaynak, TakasTarafi hedef, long takasId, Neden neden, String nedenNotu, String gerekce) {
        makinistler.kilitle(List.of(kaynak.makinistId(), hedef.makinistId()));
        TakasTaraf a = takasTarafi(kaynak);
        TakasTaraf b = takasTarafi(hedef);
        takasDogrula(a, b);
        List<Ihlal> ihA = takasDegerlendir(a.makinist(), b.sefer(), a.sefer(), b.atama().rol());
        List<Ihlal> ihB = takasDegerlendir(b.makinist(), a.sefer(), b.sefer(), a.atama().rol());
        List<TakasIhlali> engeller = new ArrayList<>(takasIhlalleri(a, ihA, true));
        engeller.addAll(takasIhlalleri(b, ihB, true));
        if (!engeller.isEmpty()) {
            throw UygulamaHatasi.kuralIhlali("Kural ihlali: bu takas kurallara uymuyor.", engeller);
        }
        String g = gerekce == null || gerekce.isBlank() ? null : gerekce.strip();
        List<TakasIhlali> uyarilar = new ArrayList<>(takasIhlalleri(a, ihA, false));
        uyarilar.addAll(takasIhlalleri(b, ihB, false));
        if (!uyarilar.isEmpty() && g == null) {
            throw UygulamaHatasi.dogrulama("gerekce", "Kural uyarısı var; takasın onayı için gerekçe yazılmalı.");
        }
        Parametreler p = yukleyici.parametreler();
        depo.delete(a.atama());
        depo.delete(b.atama());
        depo.flush();
        gordumlar.atamalariSilinenleriTemizle(List.of(a.atama().id(), b.atama().id()));
        atamalar.olayYaz(a.atama(), a.sefer(), AtamaOlayi.Tur.DEGISTI_BASKASINA, neden);
        atamalar.olayYaz(b.atama(), b.sefer(), AtamaOlayi.Tur.DEGISTI_BASKASINA, null); // neden talebi açanındır (a): karşı makinistin kaleminde başkasının nedeni (ör. hastalık) görünmez
        Atama yeniA;
        Atama yeniB;
        try {
            yeniA = depo.save(new Atama(b.sefer().id(), a.makinist().id(), b.atama().rol(), DegisiklikServisi.simdikiKullaniciAdi(), saat.instant(), g, b.sefer().kalkisGecikmeDk()));
            yeniB = depo.save(new Atama(a.sefer().id(), b.makinist().id(), a.atama().rol(), DegisiklikServisi.simdikiKullaniciAdi(), saat.instant(), g, a.sefer().kalkisGecikmeDk()));
            depo.flush();
        } catch (DataIntegrityViolationException e) {
            throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", "Takas edilen seferlerden biri az önce başka biri tarafından değiştirildi. Listeyi yenileyin.");
        }
        takasKaydi(yeniA, a, b, ihA, p, g, takasId, neden, nedenNotu);
        takasKaydi(yeniB, b, a, ihB, p, g, takasId, neden, nedenNotu);
        return new TakasUygulamaSonucu(yeniA.id(), yeniB.id(), uyarilar);
    }

    private void takasKaydi(Atama yeni, TakasTaraf yeniMakinist, TakasTaraf oncekiMakinist, List<Ihlal> ihlaller, Parametreler p, String gerekce, long takasId, Neden neden, String nedenNotu) {
        // yeni satır yeniMakinist'in; o seferin önceki sahibi oncekiMakinist (rol yuvayla gider)
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.ATAMA_DEGISTIR, "ATAMA", yeni.id()).makinist(yeniMakinist.makinist().id()).sefer(yeni.seferId())
                .degisim(yeni.rol() + ":" + oncekiMakinist.makinist().sicil(), yeni.rol() + ":" + yeniMakinist.makinist().sicil()).neden(neden, nedenNotu)
                .ayrinti(atamalar.anlikGoruntu(p, yeniMakinist.makinist(), ihlaller, gerekce, false, takasId, false)));
    }

}
