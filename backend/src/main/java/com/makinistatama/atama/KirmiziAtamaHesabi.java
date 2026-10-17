package com.makinistatama.atama;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;

/**
 * Kırmızı atama (FR-07, MK-5): SAKLANMAZ, her okumada canlı hesaplanır; parametre, izin, belge, gecikme değişince kendiliğinden doğru kalır.
 * Her atama yalnız KENDİSİNDEN ÖNCE başlayan görevlerle değerlendirilir (eşit başlangıçta sefer kimliği sırası): zinciri bozan sonraki atama
 * sorumludur, böylece gecikmeli ilk sefer "sonrakine dinlenme yetmiyor" diye kendisi kırmızı olmaz. En az bir ENGEL ya da iptal sefer = kırmızı;
 * yalnız UYARI düzeyindekiler {@code uyarilar}'da görünür. K-22 (bitmiş sefere atama) atama anı denetimidir, burada uygulanmaz.
 */
@Component
@Transactional(readOnly = true)
class KirmiziAtamaHesabi {

    static final String IPTAL_NEDENI = "İPTAL – makinist boşta";

    /** Bir atamanın canlı durumu. */
    record Sonuc(boolean kirmizi, List<String> nedenler, List<String> uyarilar) {
    }

    static final String OKSUZ_YOLCULUK = "Yolculuktan sonra bu garda görev yok";

    private final BaglamYukleyici yukleyici;
    private final MakinistServisi makinistler;
    private final AyarServisi ayarlar;

    KirmiziAtamaHesabi(BaglamYukleyici yukleyici, MakinistServisi makinistler, AyarServisi ayarlar) {
        this.yukleyici = yukleyici;
        this.makinistler = makinistler;
        this.ayarlar = ayarlar;
    }

    /** {@code seferler}: değerlendirilecek seferler (iptaller dahil); {@code atamalar}: sefer kimliği -> o seferin atamaları. Dönüş: atama kimliği -> sonuç. */
    Map<Long, Sonuc> hesapla(List<SeferDto> seferler, Map<Long, List<Atama>> atamalar) {
        return hesapla(seferler, atamalar, null);
    }

    /**
     * Aynı hesap, verilen parametrelerle (MK-21: "kuralı dene" mevcut ve taslak-kurallı iki koşu); {@code null} = güncel parametreler. Hesap değişmez, yazma yok.
     */
    Map<Long, Sonuc> hesapla(List<SeferDto> seferler, Map<Long, List<Atama>> atamalar, Parametreler parametreler) {
        Map<Long, Sonuc> sonuc = new HashMap<>();
        java.util.Set<Long> makinistIdler = new java.util.HashSet<>();
        LocalDate bas = null;
        LocalDate bit = null;
        for (SeferDto s : seferler) {
            List<Atama> as = atamalar.getOrDefault(s.id(), List.of());
            if (s.durum() == SeferDurumu.IPTAL) {
                as.forEach(a -> sonuc.put(a.id(), new Sonuc(true, List.of(IPTAL_NEDENI), List.of())));
            } else if (!as.isEmpty()) {
                as.forEach(a -> makinistIdler.add(a.makinistId()));
                bas = bas == null || s.tarih().isBefore(bas) ? s.tarih() : bas;
                bit = bit == null || s.tarih().isAfter(bit) ? s.tarih() : bit;
            }
        }
        if (makinistIdler.isEmpty()) {
            return sonuc;
        }
        Map<Long, MakinistDto> kisiler = makinistler.detaylar(makinistIdler).stream().collect(Collectors.toMap(MakinistDto::id, m -> m));
        Yuk y = yukleyici.yukleAralik(bas, bit, List.copyOf(kisiler.values()), parametreler);
        for (SeferDto s : seferler) {
            if (s.durum() == SeferDurumu.IPTAL) {
                continue;
            }
            for (Atama a : atamalar.getOrDefault(s.id(), List.of())) {
                boolean yolculuk = a.rol() == AtamaRol.YOLCU;
                // OK-6: görev, atamanın yapıldığı andaki gecikmeyle başlar (OK-17: aynı trenin önceki parçasından devam ediyorsa güncel kalkış; yüklenen satır TEK kaynak);
                // YOLCU satırı yolculuk görevidir (hazırlık/alıkoyma/belge kuralı yok; MK-40)
                // yüklenen satır TEK kaynak (OK-6/OK-17 başlangıcı, F2c tur 3 hazırlık/alıkoyma); satır yoksa (pencere dışı) sefer + atamadaki gecikme
                AtamaSatiri satir = y.satirlar(a.makinistId()).stream().filter(r -> a.id().equals(r.atamaId())).findFirst().orElse(null);
                Gorev aday = satir != null ? y.gorev(satir) : y.gorev(s, a.kalkisGecikmeDk(), yolculuk);
                MakinistDto m = kisiler.get(a.makinistId());
                List<Gorev> oncekiler = y.komsular(m.id(), s.tarih(), s.id(), aday);
                List<Ihlal> ihlaller = KuralMotoru.degerlendir(y.baglam(m, aday, oncekiler, null));
                List<String> engel = ihlaller.stream().filter(i -> i.siddet() == Siddet.ENGEL).map(Ihlal::metin).toList();
                List<String> uyari = ihlaller.stream().filter(i -> i.siddet() == Siddet.UYARI).map(Ihlal::metin).toList();
                // MK-46: kırmızı yapmaz, yalnız uyarı; eve (bağlı gara) dönüş yolculuğunda sonraki görev aranmaz
                if (yolculuk && !java.util.Objects.equals(m.bagliGarId(), s.varisGarId()) && oksuzYolculuk(y, m.id(), s.varisGarId(), s.guncelVaris().toInstant())) {
                    uyari = new ArrayList<>(uyari);
                    uyari.add(OKSUZ_YOLCULUK);
                }
                sonuc.put(a.id(), new Sonuc(!engel.isEmpty(), engel, uyari));
            }
        }
        return sonuc;
    }

    /**
     * Serbest intikallerin canlı durumu (MK-107; intikal atama olmadığından {@link #hesapla} onları görmez): aday = intikalin yolculuk görevi, komşular yalnız ondan ÖNCE başlayanlar
     * ve onunla aynı nöbettekiler (aynı {@code NobetHesabi.sorumluKomsular} kuralı: önceki seferin gecikmesiyle örtüşen intikalde sorumlu SONRAKİ olan, yani intikaldir). IPTAL
     * intikal haritada yoktur. Dönüş: intikal kimliği -> sonuç. Kırmızı atama sayacına GİRMEZ (OK-28: ayrı sayım {@code kirmiziIntikalSayisi}).
     */
    Map<Long, Sonuc> intikalDurumlari(List<Intikal> intikaller) {
        List<Intikal> planli = intikaller.stream().filter(i -> i.durum() == IntikalDurumu.PLANLI).toList();
        Map<Long, Sonuc> sonuc = new HashMap<>();
        if (planli.isEmpty()) {
            return sonuc;
        }
        Map<Long, MakinistDto> kisiler = makinistler.detaylar(planli.stream().map(Intikal::makinistId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MakinistDto::id, m -> m));
        LocalDate bas = planli.stream().map(i -> ZamanHesabi.gun(i.kalkis())).min(LocalDate::compareTo).orElseThrow();
        LocalDate bit = planli.stream().map(i -> ZamanHesabi.gun(i.kalkis())).max(LocalDate::compareTo).orElseThrow();
        Yuk y = yukleyici.yukleAralik(bas, bit, List.copyOf(kisiler.values()));
        for (Intikal i : planli) {
            MakinistDto m = kisiler.get(i.makinistId());
            AtamaSatiri satir = AtamaSatiri.intikaldan(i);
            Gorev aday = y.gorev(satir);
            List<Gorev> oncekiler = y.komsular(m.id(), satir.tarih(), i.seferKimligi(), aday);
            List<Ihlal> ihlaller = KuralMotoru.degerlendir(y.baglam(m, aday, oncekiler, null));
            List<String> engel = ihlaller.stream().filter(x -> x.siddet() == Siddet.ENGEL).map(Ihlal::metin).toList();
            List<String> uyari = ihlaller.stream().filter(x -> x.siddet() == Siddet.UYARI).map(Ihlal::metin).toList();
            if (!java.util.Objects.equals(m.bagliGarId(), i.varisGarId()) && oksuzYolculuk(y, m.id(), i.varisGarId(), i.varis())) { // MK-46: eve dönüşte aranmaz
                uyari = new ArrayList<>(uyari);
                uyari.add(OKSUZ_YOLCULUK);
            }
            sonuc.put(i.id(), new Sonuc(!engel.isEmpty(), engel, uyari));
        }
        return sonuc;
    }

    /**
     * MK-46: yolculuğun bittiği (varış) andan sonra, yolculuk varış garından kalkan, iptal olmayan, yolculuk olmayan hiçbir görevi {@code yolculuk.oksuz_saat} içinde
     * başlamıyor mu. Pencere süzgeci değil sayım: yalnız YOLCU satırı olan okumada ayar okunur (YOLCU yokken ek sorgu yok).
     */
    private boolean oksuzYolculuk(Yuk y, long makinistId, Long varisGarId, Instant varis) {
        Instant son = varis.plusSeconds(3600L * ayarlar.tamsayi("yolculuk.oksuz_saat"));
        return y.satirlar(makinistId).stream().noneMatch(r -> !r.yolculuk() && !r.kalkis().isBefore(varis) && !r.kalkis().isAfter(son)
                && r.kalkisGarId().equals(varisGarId));
    }
}
