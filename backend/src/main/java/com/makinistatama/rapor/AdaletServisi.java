package com.makinistatama.rapor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaSatiri;
import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.cikti.TabloExcel;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.makinist.IzinDurumu;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistKarti;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ParametreServisi;

/**
 * Adalet raporu (FR-10): ay seç -> AKTIF makinist başına sefer sayısı, görev saati, km, gece saati, izin günü, ortalamadan fark. İptal seferler sayılmaz
 * (atama sorgusu dışlar). Görev saati ve gece saati puantajla AYNI hesaptır ({@link PuantajHesabi}): çalışma aralığı (kalkış gecikmesi, hazırlık/alıkoyma dahil) ay
 * sınırında gece yarısından bölünür; sefer sayısı ve km seferin planlanan gününün ayına yazılır (MK-3). Saatler tam dakikadan hesaplanır, yalnız gösterimde
 * saate çevrilir.
 */
@Service
@Transactional(readOnly = true)
public class AdaletServisi {

    private final AtamaServisi atamalar;
    private final MakinistServisi makinistler;
    private final ParametreServisi parametreler;
    private final Clock saat;

    AdaletServisi(AtamaServisi atamalar, MakinistServisi makinistler, ParametreServisi parametreler, Clock saat) {
        this.atamalar = atamalar;
        this.makinistler = makinistler;
        this.parametreler = parametreler;
        this.saat = saat;
    }

    private record Ham(MakinistDto m, int sefer, int yolculuk, long dakika, BigDecimal km, int kmYok, long geceDk, int izin) {
    }

    /** Makinist grubu (vars.): kondüktör ayrı grup olduğundan ortalamayı bozmaz (F3b, MK-89). */
    public AdaletRaporuDto rapor(YearMonth ay) {
        return rapor(ay, PersonelTuru.MAKINIST);
    }

    /** Tek türün raporu: ortalama yalnız o türün AKTIF personeli üzerindendir (OK-19: tür bazında ayrı grup). */
    public AdaletRaporuDto rapor(YearMonth ay, PersonelTuru tur) {
        Parametreler p = parametreler.yukle();
        LocalDate bas = ay.atDay(1);
        LocalDate bit = ay.atEndOfMonth();
        // Süre, gece ve km puantajla AYNI hesaptan gelir (OK-2, OK-3, UX-4): çalışma aralığı gece yarısından aya bölünür, gece penceresi aynıdır.
        // Bir önceki günün vardiyası ay başına taşabilir; sefer sayısı ve km planlanan günün ayındadır.
        PuantajHesabi.Oranlar o = PuantajServisi.oranlar(p);
        List<MakinistDto> aktif = makinistler.aktifDetaylar(Set.of(tur));
        List<AtamaSatiri> tum = atamalar.satirlar(aktif.stream().map(MakinistDto::id).toList(), bas.minusDays(1), bit);
        // F2b (MK-40): yolculuk süre/gün hesabına yalnız yolculuk.calisma_sayilir=1 iken girer; "yolculuk sayısı" her durumda sayılır. Km yolculukta 0 (PuantajServisi.gorev)
        boolean yolculukSayilir = tum.stream().noneMatch(AtamaSatiri::yolculuk) || atamalar.yolculukSayilir();
        Map<Long, List<AtamaSatiri>> satirlar = tum.stream().collect(Collectors.groupingBy(AtamaSatiri::makinistId));

        List<Ham> ham = new ArrayList<>();
        for (MakinistDto m : aktif) {
            List<AtamaSatiri> s = satirlar.getOrDefault(m.id(), List.of());
            PuantajHesabi.Sonuc r = PuantajHesabi.hesapla(ay, s.stream().filter(a -> yolculukSayilir || !a.yolculuk()).map(a -> PuantajServisi.gorev(a, p)).toList(), o);
            int sefer = (int) s.stream().filter(a -> !a.yolculuk() && YearMonth.from(a.tarih()).equals(ay)).count();
            int yolculuk = (int) s.stream().filter(a -> a.yolculuk() && YearMonth.from(a.tarih()).equals(ay)).count();
            ham.add(new Ham(m, sefer, yolculuk, r.fiiliDakika(), r.km(), r.kmBilinmeyenSefer(), r.geceDakika(), izinGunu(m, bas, bit)));
        }
        double ortDk = ham.stream().mapToLong(Ham::dakika).average().orElse(0);
        double ortKm = ham.stream().mapToDouble(h -> h.km().doubleValue()).average().orElse(0);
        List<AdaletSatiriDto> sonuc = ham.stream().map(h -> new AdaletSatiriDto(h.m().id(), h.m().sicil(), h.m().adSoyad(), h.sefer(), saat2(h.dakika()),
                h.km().setScale(1, RoundingMode.HALF_UP).doubleValue(), h.kmYok(), saat2(h.geceDk()), h.izin(),
                ortDk == 0 ? null : Math.round((h.dakika() - ortDk) / ortDk * 1000) / 10.0, h.dakika() == 0, h.yolculuk())).toList();
        return new AdaletRaporuDto(ay.toString(), OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(), sonuc.size(),
                saat2(Math.round(ortDk)), Math.round(ortKm * 10) / 10.0, ham.stream().mapToInt(Ham::kmYok).sum(), p.degerler().get("adalet.olcut"),
                o.geceBaslangic() + "–" + o.geceBitis(), sonuc, tur);
    }

    /**
     * Makinistin kendi özeti (MK-30): {@link #rapor(YearMonth)} sonucundan KENDİ satırı ve ortalama alınır (ikinci formül yok; adalet raporuyla aynı sayı).
     * {@code ay} [bu ay - 12, bu ay + 1] dışındaysa 400: makinist ucu keyfi geçmiş taraması yaptırmasın. Başkasının satırı dönmez.
     */
    public MakinistOzetDto makinistOzeti(YearMonth ay, long makinistId) {
        YearMonth buAy = YearMonth.now(saat);
        if (ay.isBefore(buAy.minusMonths(12)) || ay.isAfter(buAy.plusMonths(1))) {
            throw UygulamaHatasi.dogrulama("ay", "Ay, son 12 ay ile gelecek ay arasında olmalı.");
        }
        AdaletRaporuDto r = rapor(ay, makinistler.kart(makinistId).map(MakinistKarti::tur).orElse(PersonelTuru.MAKINIST));
        return r.satirlar().stream().filter(s -> s.makinistId().equals(makinistId)).findFirst()
                .map(s -> new MakinistOzetDto(r.ay(), true, true, s.seferSayisi(), s.gorevSaat(), s.km(), s.kmBilinmeyenSefer(), s.geceSaat(), s.izinGunu(), r.ortalamaSaat(),
                        s.farkYuzde(), s.yolculukSayisi()))
                .orElseGet(() -> new MakinistOzetDto(r.ay(), false, true, 0, 0, 0, 0, 0, 0, r.ortalamaSaat(), null, 0));
    }

    private static double saat2(long dakika) {
        return Math.round(dakika / 60.0 * 100) / 100.0;
    }

    /** Onaylı izinlerin ay ile kesişen gün sayısı (iki uç dahil; çakışan izinler tekrar sayılmaz). */
    static int izinGunu(MakinistDto m, LocalDate bas, LocalDate bit) {
        return (int) m.izinler().stream().filter(i -> i.durum() == IzinDurumu.ONAY)
                .flatMap(i -> ZamanHesabi.gunler(i.baslangic().isBefore(bas) ? bas : i.baslangic(), i.bitis().isAfter(bit) ? bit : i.bitis()).stream()).distinct()
                .count();
    }

    public byte[] excel(AdaletRaporuDto r) {
        List<List<Object>> satirlar = r.satirlar().stream().map(s -> Arrays.<Object>asList(s.sicil(), s.adSoyad(), s.seferSayisi(), s.gorevSaat(), s.km(),
                s.kmBilinmeyenSefer(), s.geceSaat(), s.izinGunu(), s.farkYuzde(), s.sifirSaat() ? "0 saat" : "", s.yolculukSayisi())).toList();
        List<List<Object>> bilgi = List.of(List.of("Ay", r.ay()), List.of("Üretim zamanı", r.uretimZamani().toLocalDateTime().toString().replace('T', ' ')),
                List.of("Üreten", r.ureten()), List.of("Personel türü", r.tur().name()), List.of(r.tur() == PersonelTuru.MAKINIST ? "AKTIF makinist sayısı" : "AKTIF kondüktör sayısı", r.makinistSayisi()), List.of("Ortalama görev saati", r.ortalamaSaat()),
                List.of("Ortalama km", r.ortalamaKm()), List.of("Km bilinmeyen sefer", r.kmBilinmeyenSefer()), List.of("Gece saati penceresi", r.geceSaatiPenceresi()),
                List.of("Dipnot", "Sefer sayısı yalnız sürüş seferleridir; yolcu olarak gidilen seferler Yolculuk sütununda ayrı sayılır (süresi yolculuk.calisma_sayilir ayarına göre görev saatine girer, km'si hiçbir zaman girmez). Km bilinmeyen seferler km toplamına girmez. Gecikme (kalkış dahil) görev saatine dahildir, iptal seferler sayılmaz. Ay sınırını aşan vardiya gece yarısından bölünür; sefer sayısı ve km seferin planlanan gününün ayına yazılır. Gece saati puantajla aynı hesaptır."));
        return TabloExcel.yaz(List.of(new TabloExcel.Sayfa("Adalet", List.of("Sicil", "Ad soyad", "Sefer", "Görev saati", "Km", "Km bilinmeyen sefer", "Gece saati",
                "İzin günü", "Ortalamadan fark %", "Not", "Yolculuk"), satirlar), new TabloExcel.Sayfa("Rapor bilgisi", List.of("Alan", "Değer"), bilgi)));
    }
}
