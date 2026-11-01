package com.makinistatama.rapor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.DolulukOzeti;
import com.makinistatama.atama.GunListesiServisi;
import com.makinistatama.atama.KirmiziSayisiDto;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.rapor.YonetimOzetiDto.Adet;

/**
 * Yönetim özeti derleyicisi (F4, MK-180; OK-44d). YENİ FORMÜL YOK: her sayı mevcut servisin AYNI çıktısından alınır (doluluk {@code GunListesiServisi.doluluk}, kırmızı {@code kirmiziSayisi}, uyarılı/neden/kayıt
 * {@code DegisiklikRaporServisi.rapor}, adalet {@code AdaletServisi.rapor}, fazla mesai {@code PuantajServisi.rapor}, yedek {@code YedekServisi.rapor}, belge {@code BelgeDurumuServisi.bugunku}); aritmetik yalnız
 * saf {@link OzetHesabi}'ndadır. Dönem takvim ayıdır; {@code ay} bugünden ±36 ay içinde olmalı. Salt okuma: denetim kaydı üretmez.
 */
@Service
@Transactional(readOnly = true)
public class YonetimOzetiServisi {

    static final int EN_COK_AY = 36;
    private static final int EN_COK_GEREKCE = 10;

    private final GunListesiServisi gunListesi;
    private final AdaletServisi adalet;
    private final PuantajServisi puantaj;
    private final YedekServisi yedek;
    private final BelgeDurumuServisi belge;
    private final DegisiklikRaporServisi degisiklik;
    private final MakinistServisi makinistler;
    private final Clock saat;

    YonetimOzetiServisi(GunListesiServisi gunListesi, AdaletServisi adalet, PuantajServisi puantaj, YedekServisi yedek, BelgeDurumuServisi belge, DegisiklikRaporServisi degisiklik,
            MakinistServisi makinistler, Clock saat) {
        this.gunListesi = gunListesi;
        this.adalet = adalet;
        this.puantaj = puantaj;
        this.yedek = yedek;
        this.belge = belge;
        this.degisiklik = degisiklik;
        this.makinistler = makinistler;
        this.saat = saat;
    }

    /** {@code ay} bugünden en çok 36 ay önce/sonra (makinist ucu değil, yönetici ucu; yine de keyfi uzak dönem taraması yaptırmaz). */
    void ayDogrula(YearMonth ay) {
        YearMonth buAy = YearMonth.now(saat);
        if (ay == null || ay.isBefore(buAy.minusMonths(EN_COK_AY)) || ay.isAfter(buAy.plusMonths(EN_COK_AY))) {
            throw UygulamaHatasi.dogrulama("ay", "Ay, bugünden en çok " + EN_COK_AY + " ay önce ya da sonra olabilir.");
        }
    }

    /** Denetim raporu bu dönemin tamamı için (tüm süzgeçler boş): özet ve denetim paketi aynı raporu paylaşır. */
    DegisiklikRaporuDto denetimRaporu(YearMonth ay) {
        return degisiklik.rapor(ay.atDay(1), ay.atEndOfMonth(), null, null, null, null);
    }

    public YonetimOzetiDto ozet(YearMonth ay) {
        ayDogrula(ay);
        return ozet(ay, denetimRaporu(ay), adalet.rapor(ay, PersonelTuru.MAKINIST));
    }

    /** Denetim paketi için: aynı denetim raporu ve adalet raporu üzerinden (Özet sayfası ile diğer sayfalar aynı veriden). */
    YonetimOzetiDto ozet(YearMonth ay, DegisiklikRaporuDto rapor, AdaletRaporuDto adaletRaporu) {
        LocalDate bas = ay.atDay(1);
        LocalDate bit = ay.atEndOfMonth();
        DolulukOzeti d = gunListesi.doluluk(bas, bit);
        KirmiziSayisiDto k = gunListesi.kirmiziSayisi(bas, bit);
        PuantajRaporuDto p = puantaj.rapor(ay, null);
        YedekRaporuDto y = yedek.rapor(ay);
        BelgeDurumuDto b = belge.bugunku();

        List<DegisiklikRaporuDto.Satir> satirlar = rapor.bolumler().stream().flatMap(x -> x.satirlar().stream()).toList();
        List<DegisiklikRaporuDto.Satir> uyarililar = satirlar.stream().filter(DegisiklikRaporuDto.Satir::uyarili).toList();
        Map<String, Long> gerekce = uyarililar.stream().collect(Collectors.groupingBy(s -> s.uyariGerekcesi() == null ? "" : s.uyariGerekcesi(), Collectors.counting()));
        Map<String, Long> neden = satirlar.stream().filter(s -> s.neden() != null).collect(Collectors.groupingBy(s -> s.neden().name(), Collectors.counting()));

        List<Double> saatler = adaletRaporu.satirlar().stream().map(AdaletSatiriDto::gorevSaat).toList();
        OzetHesabi.Dagilim dagilim = OzetHesabi.dagilim(saatler);
        var adaletDagilimi = dagilim == null ? new YonetimOzetiDto.AdaletDagilimi(0, null, null, null, null)
                : new YonetimOzetiDto.AdaletDagilimi(adaletRaporu.makinistSayisi(), adaletRaporu.ortalamaSaat(), OzetHesabi.ikiOndalik(dagilim.standartSapma()), dagilim.min(), dagilim.max());

        List<Double> fazlaMesai = p.satirlar().stream().map(PuantajRaporuDto.Satir::fazlaMesaiSaat).toList();
        Map<BelgeDurumuDto.Durum, Long> belgeDagilimi = b.makinistler().stream().filter(m -> m.durum() == MakinistDurum.AKTIF)
                .collect(Collectors.groupingBy(BelgeDurumuDto.MakinistBelgeleri::enKotu, Collectors.counting()));

        return new YonetimOzetiDto(ay.toString(), OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(), makinistler.demoVerisiVarMi(),
                new YonetimOzetiDto.Doluluk(d.toplam(), d.dolu(), d.bos(), OzetHesabi.yuzde(d.dolu(), d.toplam())),
                new YonetimOzetiDto.Kirmizi(k.kirmiziAtama(), k.kirmiziSefer(), k.kirmiziIntikal()),
                new YonetimOzetiDto.Uyarili(uyarililar.size(), adetler(gerekce, EN_COK_GEREKCE)), adaletDagilimi,
                new YonetimOzetiDto.FazlaMesai(OzetHesabi.toplam(fazlaMesai), (int) fazlaMesai.stream().filter(x -> x > 0).count()),
                new YonetimOzetiDto.Belge("Bugünkü durum", b.bugun(), sayi(belgeDagilimi, BelgeDurumuDto.Durum.GECERLI), sayi(belgeDagilimi, BelgeDurumuDto.Durum.YAKLASIYOR),
                        sayi(belgeDagilimi, BelgeDurumuDto.Durum.DOLDU), sayi(belgeDagilimi, BelgeDurumuDto.Durum.GIRILMEMIS)),
                new YonetimOzetiDto.Degisiklik(rapor.kayitSayisi(), adetler(neden, Integer.MAX_VALUE)),
                new YonetimOzetiDto.Yedek(y.eksikGunSayisi(), y.toplamAcik()));
    }

    private static int sayi(Map<BelgeDurumuDto.Durum, Long> m, BelgeDurumuDto.Durum durum) {
        return m.getOrDefault(durum, 0L).intValue();
    }

    /** Çoktan aza, eşitte ada göre; en çok {@code sinir} satır. */
    private static List<Adet> adetler(Map<String, Long> sayim, int sinir) {
        return sayim.entrySet().stream().map(e -> new Adet(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(Adet::adet).reversed().thenComparing(Adet::ad)).limit(sinir).toList();
    }
}
