package com.makinistatama.rapor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaSatiri;
import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.makinist.IzinDurumu;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;

/**
 * Günlük yedek makinist ihtiyacı (FR-11). İhtiyaç = {@code max(yedek.min, ceil(yedek.oran × PLANLI sefer))}; mevcut = AKTIF ∧ o gün onaylı izinde değil ∧ o gün hiç
 * görevi (ASIL/YARDIMCI, planlanan günü o gün olan sefer) olmayan makinist (tek havuz). Açık = ihtiyaç − mevcut (negatifse 0). Oran kaynaksız yer tutucudur
 * (analiz), ekranda öyle etiketlenir.
 */
@Service
@Transactional(readOnly = true)
public class YedekServisi {

    private final SeferServisi seferler;
    private final AtamaServisi atamalar;
    private final MakinistServisi makinistler;
    private final ParametreServisi parametreler;
    private final Clock saat;

    YedekServisi(SeferServisi seferler, AtamaServisi atamalar, MakinistServisi makinistler, ParametreServisi parametreler, Clock saat) {
        this.seferler = seferler;
        this.atamalar = atamalar;
        this.makinistler = makinistler;
        this.parametreler = parametreler;
        this.saat = saat;
    }

    public YedekRaporuDto rapor(YearMonth ay) {
        Parametreler p = parametreler.yukle();
        var oran = p.oran("yedek.oran");
        int min = (int) p.tamsayi("yedek.min");
        LocalDate bas = ay.atDay(1);
        LocalDate bit = ay.atEndOfMonth();
        List<MakinistDto> aktif = makinistler.aktifDetaylar(PersonelTuru.MAKINISTLER);
        Map<LocalDate, Long> planli = seferler.aralikListesi(bas, bit).stream().filter(s -> s.durum() == SeferDurumu.PLANLI)
                .collect(Collectors.groupingBy(SeferDto::tarih, Collectors.counting()));
        Map<LocalDate, Set<Long>> gorevli = atamalar.satirlar(aktif.stream().map(MakinistDto::id).toList(), bas, bit).stream()
                .collect(Collectors.groupingBy(AtamaSatiri::tarih, Collectors.mapping(AtamaSatiri::makinistId, Collectors.toSet())));

        List<YedekGunDto> gunler = new ArrayList<>();
        for (LocalDate gun : ZamanHesabi.gunler(bas, bit)) {
            int sefer = planli.getOrDefault(gun, 0L).intValue();
            int ihtiyac = Math.max(min, oran.multiply(java.math.BigDecimal.valueOf(sefer)).setScale(0, java.math.RoundingMode.CEILING).intValue());
            Set<Long> mesgul = new HashSet<>(gorevli.getOrDefault(gun, Set.of()));
            int izinli = 0;
            for (MakinistDto m : aktif) {
                boolean izinde = m.izinler().stream().anyMatch(i -> i.durum() == IzinDurumu.ONAY && !gun.isBefore(i.baslangic()) && !gun.isAfter(i.bitis()));
                if (izinde) {
                    izinli++;
                    mesgul.add(m.id());
                }
            }
            int mevcut = aktif.size() - mesgul.size();
            gunler.add(new YedekGunDto(gun, sefer, ihtiyac, mevcut, Math.max(0, ihtiyac - mevcut), izinli, gorevli.getOrDefault(gun, Set.of()).size()));
        }
        return new YedekRaporuDto(ay.toString(), OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(), aktif.size(),
                oran.doubleValue(), min, (int) gunler.stream().filter(g -> g.acik() > 0).count(), gunler.stream().mapToInt(YedekGunDto::acik).sum(), gunler);
    }
}
