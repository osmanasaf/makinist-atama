package com.makinistatama.tanim;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Gar çifti intikal süre tablosu (MK-109): yönetici Tanımlar ekranından girer; "yolculukla ata" öneri satırını besler. Yönsüz (çift {@code min/max} normalize edilir). Tablo boşken
 * ya da çift tanımsızken {@link #sure} boş döner = öneri yok. Her yazım denetim kaydı üretir ({@code INTIKAL_SURESI_DEGISTI}, eski -> yeni dakika; silmede yeni boş).
 */
@Service
@Transactional(readOnly = true)
public class GarIntikalSureServisi {

    private final GarIntikalSureRepository depo;
    private final TanimServisi tanimlar;
    private final DegisiklikServisi degisiklik;

    GarIntikalSureServisi(GarIntikalSureRepository depo, TanimServisi tanimlar, DegisiklikServisi degisiklik) {
        this.depo = depo;
        this.tanimlar = tanimlar;
        this.degisiklik = degisiklik;
    }

    /** İki gar arası süre (dakika); aynı gar ya da tanımsız çift için boş. */
    public OptionalInt sure(long gar1, long gar2) {
        if (gar1 == gar2) {
            return OptionalInt.empty();
        }
        return depo.findById(new GarIntikalSuresi.Anahtar(Math.min(gar1, gar2), Math.max(gar1, gar2))).map(s -> OptionalInt.of(s.dakika())).orElse(OptionalInt.empty());
    }

    public List<GarIntikalSuresiDto> liste() {
        Map<Long, String> adlar = tanimlar.tumAdlar();
        return depo.findAllByOrderByGarAIdAscGarBIdAsc().stream().map(s -> dto(s, adlar)).toList();
    }

    /** Ekler ya da günceller. Çift kayıtlıysa {@code versiyon} zorunlu (yoksa 400, uyuşmazsa 409); aynı gar / gar olmayan tanım 400. */
    @Transactional
    public GarIntikalSuresiDto yaz(GarIntikalSureIstegi istek) {
        if (istek.garAId().equals(istek.garBId())) {
            throw UygulamaHatasi.dogrulama("garBId", "Süre iki farklı gar arasında tanımlanır.");
        }
        tanimlar.bul(TanimTipi.GAR, istek.garAId(), "garAId");
        tanimlar.bul(TanimTipi.GAR, istek.garBId(), "garBId");
        long a = Math.min(istek.garAId(), istek.garBId());
        long b = Math.max(istek.garAId(), istek.garBId());
        GarIntikalSuresi s = depo.findById(new GarIntikalSuresi.Anahtar(a, b)).orElse(null);
        Integer eski = null;
        if (s == null) {
            try {
                s = depo.saveAndFlush(new GarIntikalSuresi(a, b, istek.dakika()));
            } catch (DataIntegrityViolationException e) { // eşzamanlı aynı çift
                throw UygulamaHatasi.versiyonCakismasi();
            }
        } else {
            if (istek.versiyon() == null) {
                throw UygulamaHatasi.dogrulama("versiyon", "Kayıt sürümü gerekli.");
            }
            if (istek.versiyon() != s.versiyon()) {
                throw UygulamaHatasi.versiyonCakismasi();
            }
            eski = s.dakika();
            if (eski.intValue() == istek.dakika().intValue()) {
                return dto(s, tanimlar.tumAdlar());
            }
            s.dakikaDegistir(istek.dakika());
            depo.saveAndFlush(s);
        }
        Map<Long, String> adlar = tanimlar.tumAdlar();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.INTIKAL_SURESI_DEGISTI, "GAR_INTIKAL_SURESI", null).degisim(eski == null ? null : metin(adlar, a, b, eski), metin(adlar, a, b, s.dakika())));
        return dto(s, adlar);
    }

    @Transactional
    public void sil(long gar1, long gar2) {
        long a = Math.min(gar1, gar2);
        long b = Math.max(gar1, gar2);
        GarIntikalSuresi s = depo.findById(new GarIntikalSuresi.Anahtar(a, b)).orElseThrow(() -> UygulamaHatasi.bulunamadi("Bu gar çifti için tanımlı süre yok."));
        Map<Long, String> adlar = tanimlar.tumAdlar();
        depo.delete(s);
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.INTIKAL_SURESI_DEGISTI, "GAR_INTIKAL_SURESI", null).degisim(metin(adlar, a, b, s.dakika()), null));
    }

    private static GarIntikalSuresiDto dto(GarIntikalSuresi s, Map<Long, String> adlar) {
        return new GarIntikalSuresiDto(s.garAId(), adlar.get(s.garAId()), s.garBId(), adlar.get(s.garBId()), s.dakika(), s.versiyon());
    }

    /** Denetim metni: "Ankara – Eskişehir: 90 dk". */
    private static String metin(Map<Long, String> adlar, long a, long b, int dakika) {
        return adlar.get(a) + " – " + adlar.get(b) + ": " + dakika + " dk";
    }
}
