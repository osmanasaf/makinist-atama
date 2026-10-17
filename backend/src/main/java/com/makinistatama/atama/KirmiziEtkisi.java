package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.tanim.TanimServisi;

/**
 * Bir değişikliğin (atama kaldırma, sefer bölme/birleştirme) yan etkisi olan "yeni kırmızı"lar (MK-124; {@code AtamaServisi.kaldir}'dan taşındı, davranış aynı).
 * İKİ ADIMLI: değişiklikten ÖNCE {@link #once}, değişiklik yazıldıktan (ve flush edildikten) SONRA {@link #sonra}. Fark: önce kırmızı OLMAYIP sonra kırmızı olan atamalar
 * (kural gereği, hata değil; K-18 gece sınıfı, K-20 konum, K-23 aktarma vb.). Aynı işlem içinde çağrılmalıdır (yönetilen varlıklar güncel okunur).
 * <p>
 * Küme: verilen makinistlerin, çapa seferin gününden başlayan kural penceresi ({@link BaglamYukleyici#pencereGun}; özel yıl kuralı varsa yıl sonuna kadar) içindeki
 * tüm görevleri (çapa seferin kendisi dahil) + K-07/K-08'in ay ölçekli erişimindeki ilk sonraki sürüş. Çapada YOLCU olan makinist için uzak sürüş aranmaz (sürüş değil).
 */
@Component
@Transactional(readOnly = true)
class KirmiziEtkisi {

    private final AtamaRepository depo;
    private final SeferServisi seferler;
    private final BaglamYukleyici yukleyici;
    private final KirmiziAtamaHesabi kirmiziHesabi;
    private final IntikalRepository intikalDepo;
    private final TanimServisi tanimlar;

    KirmiziEtkisi(AtamaRepository depo, SeferServisi seferler, BaglamYukleyici yukleyici, KirmiziAtamaHesabi kirmiziHesabi, IntikalRepository intikalDepo, TanimServisi tanimlar) {
        this.depo = depo;
        this.seferler = seferler;
        this.yukleyici = yukleyici;
        this.kirmiziHesabi = kirmiziHesabi;
        this.intikalDepo = intikalDepo;
        this.tanimlar = tanimlar;
    }

    /** {@link #once} çıktısı; yalnız {@link #sonra}'ya geçirilir. {@code intikaller}/{@code intikalDurum}: aynı makinistlerin pencere içindeki serbest intikalleri (review W2). */
    record Once(List<Atama> yakin, List<Atama> uzak, Set<Long> seferIdler, Map<Long, KirmiziAtamaHesabi.Sonuc> durum, Predicate<Atama> haric, List<Intikal> intikaller,
            Map<Long, KirmiziAtamaHesabi.Sonuc> intikalDurum) {
    }

    /**
     * Değişiklikten ÖNCE: kırmızı durumunu yakalar.
     *
     * @param makinistIdler etkisi aranan makinistler (bölmede: seferin tüm atamalı makinistleri, roller + YOLCU)
     * @param capa          değişen sefer; pencere onun gününden hesaplanır (gün değişmez)
     * @param haric         YALNIZ "sonra" kümesinden çıkarılacak atamalar (kaldırmada silinen atama; önce kümesinde o da bulunur). Hariç tutma yoksa {@code a -> false}
     */
    Once once(Collection<Long> makinistIdler, SeferDto capa, Predicate<Atama> haric) {
        var par = yukleyici.parametreler();
        int pencere = BaglamYukleyici.pencereGun(par); // etki ufku (MK-98)
        LocalDate pencereSonu = ZamanHesabi.pencereSonu(capa.tarih(), pencere, par.ozelYillik());
        LocalDate pencereBasi = ZamanHesabi.pencereBasi(capa.tarih(), pencere, par.ozelYillik());
        Set<Long> yolcular = depo.findBySeferIdIn(List.of(capa.id())).stream().filter(a -> a.rol() == AtamaRol.YOLCU).map(Atama::makinistId).collect(Collectors.toSet());
        List<Atama> yakin = new ArrayList<>();
        List<Atama> uzak = new ArrayList<>();
        List<Intikal> intikaller = new ArrayList<>();
        for (long mid : makinistIdler) {
            yakin.addAll(depo.makinistinGorevleri(mid, pencereBasi, pencereSonu));
            intikaller.addAll(intikalDepo.aralik(IntikalServisi.gunBasi(pencereBasi), IntikalServisi.gunBasi(pencereSonu.plusDays(1)), mid));
            if (!yolcular.contains(mid)) {
                uzak.addAll(uzakSurusler(mid, capa, par, pencereSonu));
            }
        }
        Map<Long, SeferDto> komsu = seferler.kimliklerle(Stream.concat(yakin.stream(), uzak.stream()).map(Atama::seferId).toList());
        return new Once(yakin, uzak, Set.copyOf(komsu.keySet()), kirmiziDurum(komsu, yakin, uzak), haric, intikaller, kirmiziHesabi.intikalDurumlari(intikaller));
    }

    /**
     * Serbest intikal değişikliğinden ÖNCE (MK-106): makinistin [gunBas, gunBit] gününü çevreleyen kural penceresindeki atamalarının kırmızı durumu. Uzak sürüş (K-07/K-08) aranmaz:
     * intikal sürüş değildir. {@link #sonra} aynen kullanılır (hariç tutma yok; intikalin kendisi sefer kümesinde değil, yazılan değişiklik satır kaynağından okunur).
     */
    Once onceIntikal(long makinistId, LocalDate gunBas, LocalDate gunBit) {
        var par = yukleyici.parametreler();
        int pencere = BaglamYukleyici.pencereGun(par);
        List<Atama> yakin = depo.makinistinGorevleri(makinistId, ZamanHesabi.pencereBasi(gunBas, pencere, par.ozelYillik()), ZamanHesabi.pencereSonu(gunBit, pencere, par.ozelYillik()));
        Map<Long, SeferDto> komsu = seferler.kimliklerle(yakin.stream().map(Atama::seferId).toList());
        return new Once(yakin, List.of(), Set.copyOf(komsu.keySet()), kirmiziDurum(komsu, yakin, List.of()), a -> false, List.of(), Map.of());
    }

    /**
     * Değişiklikten SONRA: seferler yeniden okunur (bölme/birleştirme sefer saatlerini değiştirir; {@code flush} sonrası yönetilen güncel varlıklar), {@code haric}'e uyan
     * atamalar çıkarılır, kırmızı durumu yeniden hesaplanır. Dönüş: önce kırmızı olmayıp sonra kırmızı olanlar (atamaId, seferId, trenNo, sonraki nedenler) ve aynı makinistlerin
     * yeni kırmızı serbest intikalleri (review W2: kaldırılan atama konumu geri getirince intikal kırmızı olabilir).
     */
    KaldirSonucuDto sonra(Once o) {
        Map<Long, SeferDto> komsu = seferler.kimliklerle(List.copyOf(o.seferIdler()));
        List<Atama> yakin = o.yakin().stream().filter(x -> !o.haric().test(x)).toList();
        List<Atama> uzak = o.uzak().stream().filter(x -> !o.haric().test(x)).toList();
        Map<Long, KirmiziAtamaHesabi.Sonuc> sonra = kirmiziDurum(komsu, yakin, uzak);
        Map<Long, KirmiziAtamaHesabi.Sonuc> once = o.durum();
        return new KaldirSonucuDto(Stream.concat(yakin.stream(), uzak.stream())
                .filter(x -> sonra.get(x.id()) != null && sonra.get(x.id()).kirmizi() && !(once.get(x.id()) != null && once.get(x.id()).kirmizi()))
                .map(x -> new KaldirSonucuDto.Etkilenen(x.id(), x.seferId(), komsu.get(x.seferId()).trenNo(), sonra.get(x.id()).nedenler())).toList(), yeniKirmiziIntikal(o));
    }

    private List<KaldirSonucuDto.EtkilenenIntikal> yeniKirmiziIntikal(Once o) {
        if (o.intikaller().isEmpty()) {
            return List.of();
        }
        Map<Long, KirmiziAtamaHesabi.Sonuc> sonra = kirmiziHesabi.intikalDurumlari(o.intikaller());
        Map<Long, String> garlar = tanimlar.tumAdlar();
        return o.intikaller().stream().filter(i -> sonra.get(i.id()) != null && sonra.get(i.id()).kirmizi() && !(o.intikalDurum().get(i.id()) != null && o.intikalDurum().get(i.id()).kirmizi()))
                .map(i -> new KaldirSonucuDto.EtkilenenIntikal(i.id(), i.ozet(garlar), sonra.get(i.id()).nedenler())).toList();
    }

    /**
     * Review W3: K-07 (hat ara kuralı) ve K-08 (bröve düşme) AY ölçeklidir ({@code hat.ara_max_ay}, {@code brove.surus_ara_max_ay}); kural penceresi (~15 gün) kaldırılan sürüşün
     * sonraki sürüşü "ara kuralından kurtarmasını" görmez. Sonraki sürüşlerin son sürüşü zincirle tazelenir: kaldırılandan sonraki İLK sürüş günü (K-07: aynı hat; K-08: her hat) ve
     * gece yarısını aşan görevler için izleyen iki gün hesaba katılır; daha sonrakilerin son sürüşü o ilk sürüşten gelir, kaldırılandan bağımsızdır. Kural kapalıysa ya da erişim
     * (kaldırılan gün + ay sınırı) pencere içindeyse eklenecek bir şey yoktur. Dönüş: pencere sonrasındaki, bu yüzden yakın kümeyle çakışmayan atamalar.
     */
    private List<Atama> uzakSurusler(long makinistId, SeferDto sefer, Parametreler par, LocalDate pencereSonu) {
        Map<Long, Atama> bulunan = new LinkedHashMap<>();
        for (var kural : List.of(Map.entry("K-07", "hat.ara_max_ay"), Map.entry("K-08", "brove.surus_ara_max_ay"))) {
            if (par.siddet(kural.getKey()) == Siddet.KAPALI) {
                continue;
            }
            LocalDate enGec = sefer.tarih().plusMonths(par.tamsayi(kural.getValue())).plusDays(3); // +3: varış günü ve ay sonu kayması payı
            LocalDate gun = depo.sonrakiSurusGunu(makinistId, pencereSonu, enGec, kural.getKey().equals("K-07") ? sefer.hatId() : null);
            if (gun != null) {
                depo.makinistinGorevleri(makinistId, gun, gun.plusDays(2)).forEach(x -> bulunan.putIfAbsent(x.id(), x));
            }
        }
        return List.copyOf(bulunan.values());
    }

    /** Yakın (kural penceresi) ve uzak (ay ölçekli ilk sürüş) kümeler AYRI hesaplanır: tek çağrı [min, max] aralığını yüklerdi (bir makinistin aylar boyu atamaları). */
    private Map<Long, KirmiziAtamaHesabi.Sonuc> kirmiziDurum(Map<Long, SeferDto> seferler, List<Atama> yakin, List<Atama> uzak) {
        Map<Long, KirmiziAtamaHesabi.Sonuc> hepsi = new HashMap<>(kirmiziDurum(seferler, yakin));
        if (!uzak.isEmpty()) {
            hepsi.putAll(kirmiziDurum(seferler, uzak));
        }
        return hepsi;
    }

    private Map<Long, KirmiziAtamaHesabi.Sonuc> kirmiziDurum(Map<Long, SeferDto> seferler, List<Atama> atamalar) {
        return kirmiziHesabi.hesapla(List.copyOf(seferler.values()), atamalar.stream().collect(Collectors.groupingBy(Atama::seferId)));
    }
}
