package com.makinistatama.rapor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaSatiri;
import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.atama.GunListesiServisi;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.IzinDurumu;
import com.makinistatama.makinist.IzinTuru;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.tanim.ResmiTatilServisi;

/**
 * Aylık çizelge ızgarası (F5, MK-177). TEK hesap kaynağı: satır toplamları {@link AdaletServisi#rapor} satırlarından (aynı {@code PuantajHesabi}; ikinci formül yok), izin günü
 * {@code MakinistDto.izinler} (yalnız ONAY), hücre görevleri {@code AtamaServisi.satirlar} (iptal sefer yok; gece yarısını geçen vardiya BAŞLADIĞI günün hücresinde = planlanan gün, MK-3),
 * kırmızı bayrağı {@code GunListesiServisi.kirmiziAtamaKimlikleri} ve {@code kirmiziIntikalKimlikleri} ({@code kontrol=true}; serbest intikal dahil, üst çubuk sayısıyla aynı kümeler), resmi tatil {@link ResmiTatilServisi} (yalnız gösterim). Salt okuma; hiçbir şey yazmaz.
 */
@Service
@Transactional(readOnly = true)
public class CizelgeServisi {

    private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AdaletServisi adalet;
    private final AtamaServisi atamalar;
    private final GunListesiServisi gunListesi;
    private final MakinistServisi makinistler;
    private final ResmiTatilServisi tatiller;
    private final ParametreServisi parametreler;
    private final Clock saat;

    CizelgeServisi(AdaletServisi adalet, AtamaServisi atamalar, GunListesiServisi gunListesi, MakinistServisi makinistler, ResmiTatilServisi tatiller, ParametreServisi parametreler,
            Clock saat) {
        this.adalet = adalet;
        this.atamalar = atamalar;
        this.gunListesi = gunListesi;
        this.makinistler = makinistler;
        this.tatiller = tatiller;
        this.parametreler = parametreler;
        this.saat = saat;
    }

    /** Ay aralığı: son 36 ay .. +12 ay (yazdırma ve ızgara geçmişi; keyfi tarama yok). */
    private void ayDogrula(YearMonth ay) {
        YearMonth buAy = YearMonth.now(saat);
        if (ay.isBefore(buAy.minusMonths(36)) || ay.isAfter(buAy.plusMonths(12))) {
            throw UygulamaHatasi.dogrulama("ay", "Ay, son 36 ay ile gelecek 12 ay arasında olmalı.");
        }
    }

    /** @param garId boşsa tüm garlar; doluysa yalnız o garda bağlı personel (toplamlar yine o satırların adalet toplamıdır) */
    public CizelgeDto cizelge(YearMonth ay, PersonelTuru tur, Long garId, boolean kontrol) {
        ayDogrula(ay);
        Parametreler p = parametreler.yukle();
        LocalDate bas = ay.atDay(1);
        LocalDate bit = ay.atEndOfMonth();
        Map<Long, AdaletSatiriDto> toplamlar = adalet.rapor(ay, tur).satirlar().stream().collect(Collectors.toMap(AdaletSatiriDto::makinistId, Function.identity()));
        List<MakinistDto> kisiler = makinistler.aktifDetaylar(Set.of(tur)).stream().filter(m -> garId == null || garId.equals(m.bagliGarId())).toList();
        Map<Long, List<AtamaSatiri>> satirlar = atamalar.satirlar(kisiler.stream().map(MakinistDto::id).toList(), bas, bit).stream().collect(Collectors.groupingBy(AtamaSatiri::makinistId));
        Set<Long> kirmizilar = kontrol ? gunListesi.kirmiziAtamaKimlikleri(bas, bit) : Set.of();
        Set<Long> kirmiziIntikaller = kontrol ? gunListesi.kirmiziIntikalKimlikleri(bas, bit) : Set.of();
        LocalTime geceBas = p.saat("gece.baslangic");
        LocalTime geceBit = p.saat("gece.bitis");

        List<CizelgeDto.Satir> sonuc = new ArrayList<>(kisiler.size());
        for (MakinistDto m : kisiler) {
            AdaletSatiriDto t = toplamlar.get(m.id());
            Map<Integer, IzinTuru> izinler = izinGunleri(m, bas, bit);
            Map<Integer, List<CizelgeDto.Gorev>> gorevler = new TreeMap<>();
            satirlar.getOrDefault(m.id(), List.of()).stream().filter(a -> !a.tarih().isBefore(bas) && !a.tarih().isAfter(bit))
                    .sorted(Comparator.comparing(AtamaSatiri::kalkis).thenComparing(a -> a.seferId() == null ? 0L : a.seferId()))
                    .forEach(a -> gorevler.computeIfAbsent(a.tarih().getDayOfMonth(), g -> new ArrayList<>()).add(gorev(a, geceBas, geceBit, kirmizilar, kirmiziIntikaller)));
            List<CizelgeDto.Hucre> hucreler = hucreler(izinler, gorevler);
            sonuc.add(new CizelgeDto.Satir(m.id(), m.sicil(), m.adSoyad(), m.bagliGar(), t.gorevSaat(), t.geceSaat(), t.km(), t.seferSayisi(), t.yolculukSayisi(), t.izinGunu(), hucreler));
        }
        List<CizelgeDto.Tatil> tatilListesi = tatiller.aralik(bas, bit).stream().map(x -> new CizelgeDto.Tatil(x.tarih().getDayOfMonth(), x.ad())).toList();
        return new CizelgeDto(ay.toString(), ay.lengthOfMonth(), tur, garId, kontrol, OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(), sonuc, tatilListesi);
    }

    private static List<CizelgeDto.Hucre> hucreler(Map<Integer, IzinTuru> izinler, Map<Integer, List<CizelgeDto.Gorev>> gorevler) {
        Map<Integer, CizelgeDto.Hucre> gunler = new TreeMap<>();
        izinler.forEach((g, tur) -> gunler.put(g, new CizelgeDto.Hucre(g, tur, null)));
        gorevler.forEach((g, liste) -> gunler.put(g, new CizelgeDto.Hucre(g, izinler.get(g), liste)));
        return List.copyOf(gunler.values());
    }

    /** Ay içindeki onaylı izin günleri (ayın günü -> tür; çakışan izinde ilki). {@code AdaletServisi.izinGunu} ile aynı küme. */
    private static Map<Integer, IzinTuru> izinGunleri(MakinistDto m, LocalDate bas, LocalDate bit) {
        Map<Integer, IzinTuru> gunler = new TreeMap<>();
        m.izinler().stream().filter(i -> i.durum() == IzinDurumu.ONAY).forEach(i -> ZamanHesabi.gunler(i.baslangic().isBefore(bas) ? bas : i.baslangic(), i.bitis().isAfter(bit) ? bit : i.bitis())
                .forEach(g -> gunler.putIfAbsent(g.getDayOfMonth(), i.tur())));
        return gunler;
    }

    private static CizelgeDto.Gorev gorev(AtamaSatiri a, LocalTime geceBas, LocalTime geceBit, Set<Long> kirmizilar, Set<Long> kirmiziIntikaller) {
        return new CizelgeDto.Gorev(a.intikal() ? null : a.seferId(), a.trenNo(), saatMetni(a.kalkis()), saatMetni(a.varis()), a.rol(), gece(a, geceBas, geceBit) ? Boolean.TRUE : null,
                a.yolculuk() ? Boolean.TRUE : null, (a.intikal() ? kirmiziIntikaller.contains(-a.seferId()) : kirmizilar.contains(a.atamaId())) ? Boolean.TRUE : null); // serbest intikalde seferId = -intikal kimliği (MK-105)
    }

    private static String saatMetni(java.time.Instant an) {
        return an.atZone(ZamanHesabi.ISTANBUL).format(SAAT);
    }

    /** Atama masasının gece göstergesiyle aynı kural: kalkış ya da varış gece penceresinde ya da sefer ertesi güne sarkıyor. */
    private static boolean gece(AtamaSatiri a, LocalTime bas, LocalTime bit) {
        LocalTime k = a.kalkis().atZone(ZamanHesabi.ISTANBUL).toLocalTime();
        LocalTime v = a.varis().atZone(ZamanHesabi.ISTANBUL).toLocalTime();
        return penceredeMi(k, bas, bit) || penceredeMi(v, bas, bit) || !ZamanHesabi.gun(a.kalkis()).equals(ZamanHesabi.gun(a.varis()));
    }

    private static boolean penceredeMi(LocalTime t, LocalTime bas, LocalTime bit) {
        return bas.isAfter(bit) ? !t.isBefore(bas) || t.isBefore(bit) : !t.isBefore(bas) && t.isBefore(bit);
    }
}
