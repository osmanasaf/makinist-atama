package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.KuralBaglami;
import com.makinistatama.kural.YakinlikAyarlari;
import com.makinistatama.kural.YakinlikKontrolu;
import com.makinistatama.kural.YorgunlukHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.AyarDto;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;

/**
 * Sınıra yakınlık ve yorgunluk (F3, MK-174): kural motorundan BAĞIMSIZ, salt okuma. Aday ucu {@code AtamaServisi.adaylar} ile aynı havuzu ve tek {@link Yuk} yüklemesini
 * kullanır, kural motorunu ÇAĞIRMAZ; gün ucu günün atamalarını {@code GunListesiServisi}'yle aynı gün yüklemesinden (aralık = o gün) alır. Marj değerleri her istekte
 * {@code uygulama_ayar}'dan okunur (önbellek yok: ayar değişince bir sonraki istekte etkili). {@code marj.aktif=0} iken hesap yapılmaz.
 */
@Service
@Transactional(readOnly = true)
public class YakinlikServisi {

    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final BaglamYukleyici yukleyici;
    private final AtamaRepository depo;
    private final AyarServisi ayarlar;

    YakinlikServisi(SeferServisi seferler, MakinistServisi makinistler, BaglamYukleyici yukleyici, AtamaRepository depo, AyarServisi ayarlar) {
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.yukleyici = yukleyici;
        this.depo = depo;
        this.ayarlar = ayarlar;
    }

    /** Okunmuş ayarlar: kural girdisi + yorgunluk eşikleri. */
    private record Ayar(YakinlikAyarlari yakinlik, YakinlikDto.Esikler esikler) {
    }

    private Ayar ayarOku() {
        Map<String, Integer> d = new HashMap<>();
        for (AyarDto a : ayarlar.liste()) {
            d.put(a.anahtar(), a.deger());
        }
        return new Ayar(new YakinlikAyarlari(d.get("marj.aktif") == 1, d.get("marj.gunluk_dk"), d.get("marj.gece_dk"), d.get("marj.dinlenme_dk"), d.get("marj.hafta_tatili_dk"),
                d.get("marj.yillik_saat"), d.get("marj.aktarma_dk"), d.get("marj.gece_hafta")),
                new YakinlikDto.Esikler(d.get("yorgunluk.gun"), d.get("yorgunluk.hafta_saat"), d.get("yorgunluk.gece_adet"), d.get("yorgunluk.dinlenme_saat")));
    }

    private static YakinlikDto.Satir satir(Long makinistId, Long atamaId, KuralBaglami b, Ayar a, Yuk y) {
        List<Gorev> tum = new ArrayList<>(b.digerGorevler());
        tum.add(b.aday());
        int pencere = a.esikler().pencereGun();
        return new YakinlikDto.Satir(makinistId, atamaId, YakinlikKontrolu.kontrol(b, a.yakinlik()).stream().map(YakinlikDto.Isaret::of).toList(),
                YakinlikDto.Yorgunluk.of(YorgunlukHesabi.hesapla(tum, b.aday().bitis(), y.parametreler(), pencere), pencere));
    }

    /** Bir seferin adayları için (aday havuzu {@code adaylar} ile aynı); sıra makinist kimliğidir, ön yüz kendi sırasına eşler. */
    public YakinlikDto aday(long seferId, AtamaRol rol) {
        SeferDto sefer = seferler.getir(seferId);
        if (sefer.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "İptal edilmiş sefere atama yapılamaz.");
        }
        Ayar a = ayarOku();
        if (!a.yakinlik().aktif()) {
            return new YakinlikDto(false, a.esikler(), List.of());
        }
        List<MakinistDto> aktif = makinistler.aktifDetaylar(rol.turler());
        Yuk y = yukleyici.yukle(sefer, aktif, rol);
        return new YakinlikDto(true, a.esikler(), aktif.stream().map(m -> satir(m.id(), null, y.baglam(m), a, y)).sorted(Comparator.comparing(YakinlikDto.Satir::makinistId)).toList());
    }

    /** Günün atamaları için (YOLCU hariç, iptal seferler hariç); her atama, makinistin DİĞER tüm görevleriyle (iki yönlü) değerlendirilir. */
    public YakinlikDto gun(LocalDate tarih) {
        Ayar a = ayarOku();
        if (!a.yakinlik().aktif()) {
            return new YakinlikDto(false, a.esikler(), List.of());
        }
        Map<Long, SeferDto> liste = seferler.gunListesi(tarih).stream().filter(s -> s.durum() != SeferDurumu.IPTAL).collect(Collectors.toMap(SeferDto::id, s -> s));
        if (liste.isEmpty()) {
            return new YakinlikDto(true, a.esikler(), List.of());
        }
        List<Atama> atamalar = depo.findBySeferIdIn(liste.keySet()).stream().filter(x -> x.rol() != AtamaRol.YOLCU).toList();
        Map<Long, MakinistDto> kisiler = makinistler.detaylar(atamalar.stream().map(Atama::makinistId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(MakinistDto::id, m -> m));
        Yuk y = yukleyici.yukleAralik(tarih, tarih, List.copyOf(kisiler.values()));
        List<YakinlikDto.Satir> satirlar = new ArrayList<>();
        for (Atama at : atamalar) {
            SeferDto s = liste.get(at.seferId());
            MakinistDto m = kisiler.get(at.makinistId());
            AtamaSatiri sr = y.satirlar(m.id()).stream().filter(r -> at.id().equals(r.atamaId())).findFirst().orElse(null);
            Gorev aday = sr != null ? y.gorev(sr) : y.gorev(s, at.kalkisGecikmeDk(), false);
            satirlar.add(satir(m.id(), at.id(), y.baglam(m, aday, y.komsular(m.id(), s.tarih(), s.id(), null), null), a, y));
        }
        satirlar.sort(Comparator.comparing(YakinlikDto.Satir::atamaId));
        return new YakinlikDto(true, a.esikler(), satirlar);
    }
}
