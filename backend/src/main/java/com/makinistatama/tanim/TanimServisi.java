package com.makinistatama.tanim;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.MetinDenetimi;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Gar / hat / araç tipi tanımları (MK-11). Ad tip içinde Türkçe-duyarsız benzersizdir ({@code Eskişehir = ESKISEHIR}); tekrar 409
 * {@code TANIM_TEKRARI}. Ekleme ve yeniden adlandırma değişiklik kaydı yazar; silme yoktur.
 */
@Service
@Transactional(readOnly = true)
public class TanimServisi {

    private final TanimRepository depo;
    private final DegisiklikServisi degisiklik;

    TanimServisi(TanimRepository depo, DegisiklikServisi degisiklik) {
        this.depo = depo;
        this.degisiklik = degisiklik;
    }

    public List<TanimDto> liste(TanimTipi tip) {
        return depo.findByTipOrderByIdAsc(tip).stream().map(TanimDto::of).toList();
    }

    /** İstenen tipte tanımı getirir; yoksa ya da başka tipteyse 400 ({@code alan} istek alanının adıdır). */
    public TanimDto bul(TanimTipi tip, long id, String alan) {
        return depo.findById(id).filter(t -> t.tip() == tip).map(TanimDto::of)
                .orElseThrow(() -> UygulamaHatasi.dogrulama(alan, "Seçilen kayıt bulunamadı."));
    }

    /** Verilen kimliklerin adları (kimlik -> ad); sefer/makinist DTO'larında gösterim için. Bilinmeyen kimlik haritada olmaz. */
    public Map<Long, String> adlar(Collection<Long> idler) {
        return depo.findByIdIn(idler).stream().collect(Collectors.toMap(Tanim::id, Tanim::ad));
    }

    /** Tüm tanımların adları (kimlik -> ad); toplu yüklemelerde (kural bağlamı mesajları, paneller). */
    public Map<Long, String> tumAdlar() {
        return depo.findAll().stream().collect(Collectors.toMap(Tanim::id, Tanim::ad));
    }

    /** Tipe göre ad -> kimlik haritası (Türkçe-katlanmış anahtar); Excel eşleştirmesi için. */
    public Map<String, Long> katlanmisAdlar(TanimTipi tip) {
        return depo.findByTipOrderByIdAsc(tip).stream().collect(Collectors.toMap(t -> AramaMetni.katla(t.ad().strip()), Tanim::id, (a, b) -> a));
    }

    @Transactional
    public TanimDto ekle(TanimTipi tip, String ad) {
        String temiz = temizle(ad);
        tekrarKontrol(tip, temiz, null);
        Tanim t;
        try {
            t = depo.saveAndFlush(new Tanim(tip, temiz));
        } catch (DataIntegrityViolationException e) { // eşzamanlı aynı ad
            throw tekrar(temiz);
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TANIM_EKLE, "TANIM", t.id()).degisim(null, tip + ":" + temiz));
        return TanimDto.of(t);
    }

    @Transactional
    public TanimDto yenidenAdlandir(long id, String yeniAd) {
        Tanim t = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Tanım bulunamadı."));
        String temiz = temizle(yeniAd);
        String eski = t.ad();
        if (eski.equals(temiz)) {
            return TanimDto.of(t);
        }
        tekrarKontrol(t.tip(), temiz, t.id());
        t.adDegistir(temiz);
        try {
            depo.flush();
        } catch (DataIntegrityViolationException e) {
            throw tekrar(temiz);
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TANIM_GUNCELLE, "TANIM", t.id()).degisim(eski, temiz));
        return TanimDto.of(t);
    }

    private void tekrarKontrol(TanimTipi tip, String temiz, Long haricId) {
        String katli = AramaMetni.katla(temiz);
        boolean var = depo.findByTipOrderByIdAsc(tip).stream()
                .anyMatch(t -> !t.id().equals(haricId) && AramaMetni.katla(t.ad()).equals(katli));
        if (var) {
            throw tekrar(temiz);
        }
    }

    private static String temizle(String ad) {
        String temiz = ad.strip().replaceAll("\\s+", " ");
        if (MetinDenetimi.gorunumBozanKarakterVar(temiz)) {
            throw UygulamaHatasi.dogrulama("ad", "Ad kontrol ya da yön denetim karakteri içeremez.");
        }
        return temiz;
    }

    private static UygulamaHatasi tekrar(String ad) {
        return UygulamaHatasi.cakisma("TANIM_TEKRARI", "'" + ad + "' adlı bir tanım zaten var.");
    }
}
