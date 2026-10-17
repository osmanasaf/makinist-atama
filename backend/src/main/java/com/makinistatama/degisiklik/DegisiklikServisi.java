package com.makinistatama.degisiklik;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.guvenlik.OturumKullanicisi;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Değişiklik kaydı (MK-9, FR-08): her mutasyon bu servisi açıkça çağırır. Kayıt çağıranın işleminde yazılır ({@code MANDATORY}): mutasyon geri
 * alınırsa kayıt da gider, işlem dışı çağrı hata verir. Kim: oturumdaki kullanıcı; oturum yoksa (demo/test başlatıcı) {@code "sistem"}.
 */
@Service
public class DegisiklikServisi {

    static final String SISTEM = "sistem";

    /**
     * Yazılacak kayıt. {@code of(tur, varlikTipi, varlikId)} ile başlanır, gerisi zincirlenir; örn.
     * {@code Kayit.of(ATAMA, "ATAMA", id).makinist(m).sefer(s).degisim(null, "ASIL").neden(Neden.DIGER, "not")}.
     */
    public record Kayit(DegisiklikTuru tur, String varlikTipi, Long varlikId, Long makinistId, Long seferId, String eski, String yeni,
            Neden neden, String nedenNotu, String ayrinti) {

        public static Kayit of(DegisiklikTuru tur, String varlikTipi, Long varlikId) {
            return new Kayit(tur, varlikTipi, varlikId, null, null, null, null, null, null, null);
        }

        public Kayit makinist(Long id) {
            return new Kayit(tur, varlikTipi, varlikId, id, seferId, eski, yeni, neden, nedenNotu, ayrinti);
        }

        public Kayit sefer(Long id) {
            return new Kayit(tur, varlikTipi, varlikId, makinistId, id, eski, yeni, neden, nedenNotu, ayrinti);
        }

        public Kayit degisim(Object eskiDeger, Object yeniDeger) {
            return new Kayit(tur, varlikTipi, varlikId, makinistId, seferId, degerMetni(eskiDeger), degerMetni(yeniDeger), neden, nedenNotu, ayrinti);
        }

        public Kayit neden(Neden n, String not) {
            return new Kayit(tur, varlikTipi, varlikId, makinistId, seferId, eski, yeni, n, not, ayrinti);
        }

        public Kayit ayrinti(String json) {
            return new Kayit(tur, varlikTipi, varlikId, makinistId, seferId, eski, yeni, neden, nedenNotu, json);
        }
    }

    private final DegisiklikRepository depo;
    private final Clock saat;

    DegisiklikServisi(DegisiklikRepository depo, Clock saat) {
        this.depo = depo;
        this.saat = saat;
    }

    /** Karşılaştırılabilir metin: {@code null} korunur, ondalıklar sadeleşir (20 = 20.0000), enum adıyla, gerisi {@code toString}. */
    public static String degerMetni(Object deger) {
        if (deger == null) {
            return null;
        }
        if (deger instanceof BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        if (deger instanceof Enum<?> e) {
            return e.name();
        }
        return deger.toString();
    }

    /** Oturumdaki kullanıcının adı; oturum yoksa "sistem" (satırlarda "kim" alanı için; diğer paketler guvenlik paketini görmez). */
    /** Oturumdaki kullanıcının kimliği (oturum yoksa boş): "kendi talebini onaylayamaz" gibi kimlik karşılaştırmaları için. */
    public static java.util.Optional<Long> simdikiKullaniciId() {
        return OturumKullanicisi.simdiki().map(OturumKullanicisi.Kisi::id);
    }

    public static String simdikiKullaniciAdi() {
        return OturumKullanicisi.simdiki().map(OturumKullanicisi.Kisi::adSoyad).orElse(SISTEM);
    }

    /** {@code neden == DIGER} iken not boş olamaz (FR-07); servisler değişiklik uçlarında bunu çağırır. */
    public static void nedenDogrula(Neden neden, String not) {
        if (neden == null) {
            throw UygulamaHatasi.dogrulama("neden", "Neden seçilmeli.");
        }
        if (neden == Neden.DIGER && (not == null || not.isBlank())) {
            throw UygulamaHatasi.dogrulama("nedenNotu", "Diğer nedeni için açıklama yazılmalı.");
        }
    }

    /** Okunan kayıt (denetim raporu): tablo satırının salt okunur görünümü. */
    public record Satir(Long id, Instant zaman, String kim, DegisiklikTuru tur, String varlikTipi, Long varlikId, Long makinistId, Long seferId,
            String eski, String yeni, Neden neden, String nedenNotu, String ayrinti) {
    }

    /** Bir okumada en çok bu kadar kayıt döner; fazlası sessizce kesilmez, aralığı daraltmak istenir (denetim raporu eksik olmamalı). */
    public static final int EN_COK_SATIR = 20_000;

    /** [bas, bit) arasındaki kayıtlar, zaman sırasıyla; {@link #EN_COK_SATIR}'ı aşarsa 400. */
    @Transactional(readOnly = true)
    public List<Satir> ara(Instant bas, Instant bit) {
        if (depo.countByZamanGreaterThanEqualAndZamanLessThan(bas, bit) > EN_COK_SATIR) {
            throw UygulamaHatasi.dogrulama("bit", "Bu aralıkta çok fazla kayıt var (en çok " + EN_COK_SATIR + "). Tarih aralığını daraltın.");
        }
        return depo.findByZamanGreaterThanEqualAndZamanLessThanOrderByZamanAscIdAsc(bas, bit).stream()
                .map(k -> new Satir(k.id(), k.zaman(), k.kullaniciAdi(), k.tur(), k.varlikTipi(), k.varlikId(), k.makinistId(), k.seferId(), k.eski(), k.yeni(),
                        k.neden(), k.nedenNotu(), k.ayrinti()))
                .toList();
    }

    /** Verilen seferlerin atama kayıtları (ATAMA / ATAMA_DEGISTIR / ATAMA_KALDIR), kimlik sırasıyla; tarih aralığından bağımsız (rapor: bir iptal/gecikmeden kim etkilendi). */
    @Transactional(readOnly = true)
    public List<Satir> atamaGecmisi(java.util.Collection<Long> seferIdler) {
        if (seferIdler.isEmpty()) {
            return List.of();
        }
        return depo.findBySeferIdInAndTurInOrderByIdAsc(seferIdler, List.of(DegisiklikTuru.ATAMA, DegisiklikTuru.ATAMA_DEGISTIR, DegisiklikTuru.ATAMA_KALDIR)).stream()
                .map(k -> new Satir(k.id(), k.zaman(), k.kullaniciAdi(), k.tur(), k.varlikTipi(), k.varlikId(), k.makinistId(), k.seferId(), k.eski(), k.yeni(),
                        k.neden(), k.nedenNotu(), k.ayrinti()))
                .toList();
    }

    /** Kaydı yazar (yalnız INSERT). Çağıran işlem yoksa {@code IllegalTransactionStateException}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void kaydet(Kayit kayit) {
        var kisi = OturumKullanicisi.simdiki();
        depo.save(new DegisiklikKaydi(saat.instant().truncatedTo(ChronoUnit.MILLIS), kisi.map(OturumKullanicisi.Kisi::id).orElse(null),
                kisi.map(OturumKullanicisi.Kisi::adSoyad).orElse(SISTEM), kayit));
    }
}
