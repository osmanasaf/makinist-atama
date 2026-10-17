package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/** Takas paketinin atama okuması (MK-34): kimlik özeti projeksiyonla, sefer toplu; yazma yok. {@code atama} repository'si paket dışına çıkmaz. */
@Service
@Transactional(readOnly = true)
public class GorevGorunumleri {

    private final AtamaRepository depo;
    private final SeferServisi seferler;
    private final KullaniciServisi kullanicilar;

    GorevGorunumleri(AtamaRepository depo, SeferServisi seferler, KullaniciServisi kullanicilar) {
        this.depo = depo;
        this.seferler = seferler;
        this.kullanicilar = kullanicilar;
    }

    /** Atama kimliği -> makinist kimliği (var olanlar). Makinist kilidinden ÖNCE kullanılabilir: varlık önbelleğine girmez. */
    public Map<Long, Long> sahipleri(Collection<Long> atamaIdler) {
        return atamaIdler.isEmpty() ? Map.of() : depo.kisalar(atamaIdler).stream().collect(Collectors.toMap(AtamaKisa::atamaId, AtamaKisa::makinistId));
    }

    /** Verilen seferlerdeki atamalar (kimlik özeti): "kim hangi seferde görevli" sorusu (takas çapraz çakışması). */
    public List<AtamaKisa> seferAtamalari(Collection<Long> seferIdler) {
        return seferIdler.isEmpty() ? List.of() : depo.seferKisalar(seferIdler);
    }

    /** Verilen atamaların görünümü; silinmiş atama listede yoktur. */
    public List<GorevGorunumu> kimliklerle(Collection<Long> atamaIdler) {
        return atamaIdler.isEmpty() ? List.of() : birlestir(depo.kisalar(atamaIdler));
    }

    /** [bas, bit] (planlanan gün) iptal olmayan seferlerin bütün atamaları. */
    public List<GorevGorunumu> aralik(LocalDate bas, LocalDate bit) {
        return birlestir(depo.aralikKisalar(bas, bit));
    }

    private List<GorevGorunumu> birlestir(List<AtamaKisa> kisalar) {
        if (kisalar.isEmpty()) {
            return List.of();
        }
        Map<Long, SeferDto> sefer = seferler.kimliklerle(kisalar.stream().map(AtamaKisa::seferId).collect(Collectors.toSet()));
        java.util.Set<Long> hesaplilar = kullanicilar.makinistHesaplilari(kisalar.stream().map(AtamaKisa::makinistId).collect(Collectors.toSet()));
        return kisalar.stream().filter(k -> sefer.containsKey(k.seferId())).map(k -> {
            SeferDto s = sefer.get(k.seferId());
            return new GorevGorunumu(k.atamaId(), s.id(), k.makinistId(), k.rol(), GorevOzeti.hesapla(k.rol(), s.kalkisGar(), s.varisGar(), s.guncelKalkis(), s.guncelVaris(), s.durum()),
                    s.trenNo(), s.tarih(), s.kalkisGar(), s.varisGar(), s.guncelKalkis(), s.durum(), hesaplilar.contains(k.makinistId()));
        }).toList();
    }
}
