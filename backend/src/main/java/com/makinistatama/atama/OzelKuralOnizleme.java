package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.parametre.OzelKuralIstegi;
import com.makinistatama.parametre.OzelKuralServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/**
 * "Kuralı dene" (FR-07, MK-21): kaydetmeden, bugünden itibaren 31 gün için aynı {@link KirmiziAtamaHesabi}'nı iki kez koşar: mevcut parametrelerle ve
 * "mevcut + taslak kural" ile. Fark = yeni kırmızı (ENGEL) ve yeni UYARI atamalar. Ayrı hızlı yol yok (MK-5 ile ayrışma riski). Hiçbir şey yazmaz,
 * denetim kaydı üretmez ({@code readOnly}).
 */
@Service
@Transactional(readOnly = true)
public class OzelKuralOnizleme {

    static final int GUN = 31;
    static final int EN_COK_ORNEK = 10;

    private final OzelKuralServisi kurallar;
    private final ParametreServisi parametreler;
    private final SeferServisi seferler;
    private final AtamaRepository depo;
    private final KirmiziAtamaHesabi hesap;
    private final MakinistServisi makinistler;
    private final AtamaSaati saat;

    OzelKuralOnizleme(OzelKuralServisi kurallar, ParametreServisi parametreler, SeferServisi seferler, AtamaRepository depo, KirmiziAtamaHesabi hesap,
            MakinistServisi makinistler, AtamaSaati saat) {
        this.kurallar = kurallar;
        this.parametreler = parametreler;
        this.seferler = seferler;
        this.depo = depo;
        this.hesap = hesap;
        this.makinistler = makinistler;
        this.saat = saat;
    }

    /** @param duzenlenenId mevcut kuralın düzenlenmesiyse kimliği (taslak onun yerine geçer), yeni kuralda {@code null} (kod {@code Ö-000}). */
    public OzelKuralOnizlemeDto dene(OzelKuralIstegi istek, Long duzenlenenId) {
        OzelKural taslak = kurallar.taslak(istek, duzenlenenId); // 400/404
        Parametreler once = parametreler.yukle();
        Parametreler sonra = once.ozelDegistir(taslak);

        LocalDate bas = saat.simdi().atZone(ZamanHesabi.ISTANBUL).toLocalDate();
        LocalDate bit = bas.plusDays(GUN - 1L);
        List<SeferDto> liste = seferler.aralikListesi(bas, bit);
        Map<Long, List<Atama>> atamaMap = liste.isEmpty() ? Map.of()
                : depo.findBySeferIdIn(liste.stream().map(SeferDto::id).toList()).stream().collect(Collectors.groupingBy(Atama::seferId));
        Map<Long, KirmiziAtamaHesabi.Sonuc> eski = hesap.hesapla(liste, atamaMap, once);
        Map<Long, KirmiziAtamaHesabi.Sonuc> yeni = hesap.hesapla(liste, atamaMap, sonra);

        Map<Long, String> sicil = makinistler.liste(null, null).stream().collect(Collectors.toMap(MakinistDto.Ozet::id, MakinistDto.Ozet::sicil));
        int toplam = 0;
        Set<Long> kirmiziKisi = new HashSet<>();
        Set<Long> uyariKisi = new HashSet<>();
        int kirmizi = 0;
        int uyari = 0;
        List<Satir> kirmiziSatir = new ArrayList<>();
        List<Satir> uyariSatir = new ArrayList<>();
        for (SeferDto s : liste) {
            for (Atama a : atamaMap.getOrDefault(s.id(), List.of())) {
                toplam++;
                KirmiziAtamaHesabi.Sonuc e = eski.get(a.id());
                KirmiziAtamaHesabi.Sonuc y = yeni.get(a.id());
                if (e == null || y == null) {
                    continue;
                }
                if (y.kirmizi() && !e.kirmizi()) {
                    kirmizi++;
                    kirmiziKisi.add(a.makinistId());
                    kirmiziSatir.add(new Satir(sicil.get(a.makinistId()), s, "KIRMIZI", fark(y.nedenler(), e.nedenler())));
                } else if (!y.kirmizi() && y.uyarilar().size() > e.uyarilar().size()) {
                    uyari++;
                    uyariKisi.add(a.makinistId());
                    uyariSatir.add(new Satir(sicil.get(a.makinistId()), s, "UYARI", fark(y.uyarilar(), e.uyarilar())));
                }
            }
        }
        Comparator<Satir> sira = Comparator.comparing((Satir x) -> x.sefer().tarih()).thenComparing(Satir::sicil);
        List<OzelKuralOnizlemeDto.Ornek> ornekler = new ArrayList<>();
        for (List<Satir> grup : List.of(kirmiziSatir, uyariSatir)) {
            Set<String> gorulen = new HashSet<>(); // aynı makinist, aynı gün, aynı metin: tek satır (BUG-6; sayılar tekrarsız değil, atama sayısıdır)
            grup.stream().sorted(sira).filter(x -> gorulen.add(x.sicil() + "|" + x.sefer().tarih() + "|" + x.metin()))
                    .forEach(x -> ornekler.add(new OzelKuralOnizlemeDto.Ornek(x.sicil(), x.sefer().tarih(), x.sefer().trenNo(), x.durum(), x.metin())));
        }
        return new OzelKuralOnizlemeDto(bas, bit, toplam, kirmizi, kirmiziKisi.size(), uyari, uyariKisi.size(),
                ornekler.size() > EN_COK_ORNEK ? List.copyOf(ornekler.subList(0, EN_COK_ORNEK)) : ornekler);
    }

    private record Satir(String sicil, SeferDto sefer, String durum, String metin) {
    }

    /** Yeni listede olup eskide olmayan ilk metin (taslağın eklediği ihlal); bulunamazsa yenisinin ilki. */
    private static String fark(List<String> yeni, List<String> eski) {
        return yeni.stream().filter(m -> !eski.contains(m)).findFirst().orElse(yeni.isEmpty() ? "" : yeni.getFirst());
    }
}
