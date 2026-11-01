package com.makinistatama.rapor;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.cikti.TabloExcel;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.parametre.ProfilServisi;

/**
 * Denetim paketi (F4, MK-181): denetçiye tek Excel, yedi sayfa. Yeni hesap yok: "Değişiklik kayıtları" denetim raporunun sayfa üretimidir ({@link DegisiklikRaporServisi#sayfalar}, satır sayısı = {@code kayitSayisi}),
 * "Adalet" adalet raporunun sayfasıdır, "Özet" {@link YonetimOzetiServisi}'nin aynı verisinden, "Kural değerleri" o anki parametre / şiddet / ayar / özel kural değerleridir (kaynak ve güvenilirlikle).
 * Tüm hücreler {@link TabloExcel}'den geçer: metin değer olarak yazılır, baştaki {@code = + - @} önüne {@code '} konur (formül enjeksiyonu). Veritabanında demo makinist varsa "DEMO – gerçek değil" etiketi.
 */
@Service
@Transactional(readOnly = true)
public class DenetimPaketiServisi {

    private final YonetimOzetiServisi ozetler;
    private final DegisiklikRaporServisi degisiklik;
    private final AdaletServisi adalet;
    private final ParametreServisi parametreler;
    private final AyarServisi ayarlar;
    private final ProfilServisi profil;

    DenetimPaketiServisi(YonetimOzetiServisi ozetler, DegisiklikRaporServisi degisiklik, AdaletServisi adalet, ParametreServisi parametreler, AyarServisi ayarlar, ProfilServisi profil) {
        this.ozetler = ozetler;
        this.degisiklik = degisiklik;
        this.adalet = adalet;
        this.parametreler = parametreler;
        this.ayarlar = ayarlar;
        this.profil = profil;
    }

    public byte[] excel(YearMonth ay) {
        ozetler.ayDogrula(ay);
        DegisiklikRaporuDto rapor = ozetler.denetimRaporu(ay);
        AdaletRaporuDto adaletRaporu = adalet.rapor(ay, PersonelTuru.MAKINIST);
        YonetimOzetiDto ozet = ozetler.ozet(ay, rapor, adaletRaporu);
        TabloExcel.Sayfa kayitlar = degisiklik.sayfalar(rapor).getFirst();
        List<DegisiklikRaporuDto.Satir> satirlar = rapor.bolumler().stream().flatMap(b -> b.satirlar().stream()).toList();
        return TabloExcel.yaz(List.of(
                ozetSayfasi(ozet),
                new TabloExcel.Sayfa("Değişiklik kayıtları", kayitlar.basliklar(), kayitlar.satirlar()),
                uyarililar(satirlar),
                ayarDegisiklikleri(rapor),
                kuralDegerleri(),
                adalet.excelSayfalari(adaletRaporu).getFirst(),
                bilgiSayfasi(ozet, rapor, ay)));
    }

    private static String zaman(OffsetDateTime z) {
        return z.toLocalDateTime().toString().replace('T', ' ');
    }

    private static TabloExcel.Sayfa ozetSayfasi(YonetimOzetiDto o) {
        List<List<Object>> s = new ArrayList<>();
        s.add(Arrays.asList("Dönem", o.ay()));
        s.add(Arrays.asList("Sefer sayısı (iptal hariç)", o.doluluk().toplamSefer()));
        s.add(Arrays.asList("Asıl makinisti olan sefer", o.doluluk().dolu()));
        s.add(Arrays.asList("Boş sefer (asıl makinisti yok)", o.doluluk().bos()));
        s.add(Arrays.asList("Doluluk (%)", o.doluluk().yuzde()));
        s.add(Arrays.asList("Kural dışı (kırmızı) atama", o.kirmizi().atama()));
        s.add(Arrays.asList("Kural dışı (kırmızı) sefer", o.kirmizi().sefer()));
        s.add(Arrays.asList("Kural dışı (kırmızı) serbest intikal", o.kirmizi().intikal()));
        s.add(Arrays.asList("Uyarılı atama", o.uyarili().atama()));
        o.uyarili().gerekceler().forEach(g -> s.add(Arrays.asList("Uyarı gerekçesi: " + (g.ad().isEmpty() ? "(gerekçe yok)" : g.ad()), g.adet())));
        s.add(Arrays.asList("Adalet: makinist sayısı", o.adalet().n()));
        s.add(Arrays.asList("Adalet: ortalama görev saati", o.adalet().ortalamaSaat()));
        s.add(Arrays.asList("Adalet: standart sapma (saat)", o.adalet().standartSapmaSaat()));
        s.add(Arrays.asList("Adalet: en az görev saati", o.adalet().minSaat()));
        s.add(Arrays.asList("Adalet: en çok görev saati", o.adalet().maxSaat()));
        s.add(Arrays.asList("Fazla mesai toplamı (saat)", o.fazlaMesai().toplamSaat()));
        s.add(Arrays.asList("Fazla mesai yapan makinist", o.fazlaMesai().makinistSayisi()));
        s.add(Arrays.asList("Belge uyumu (" + o.belge().etiket() + ", " + o.belge().bugun() + "): geçerli", o.belge().gecerli()));
        s.add(Arrays.asList("Belge uyumu: yaklaşan", o.belge().yaklasan()));
        s.add(Arrays.asList("Belge uyumu: süresi dolmuş", o.belge().dolmus()));
        s.add(Arrays.asList("Belge uyumu: girilmemiş", o.belge().girilmemis()));
        s.add(Arrays.asList("Değişiklik kaydı sayısı", o.degisiklik().kayitSayisi()));
        o.degisiklik().nedenler().forEach(n -> s.add(Arrays.asList("Değişiklik nedeni: " + n.ad(), n.adet())));
        s.add(Arrays.asList("Yedek açığı olan gün", o.yedek().acikGunSayisi()));
        return new TabloExcel.Sayfa("Özet", List.of("Alan", "Değer"), s);
    }

    private static TabloExcel.Sayfa uyarililar(List<DegisiklikRaporuDto.Satir> satirlar) {
        List<List<Object>> s = satirlar.stream().filter(DegisiklikRaporuDto.Satir::uyarili)
                .map(x -> Arrays.<Object>asList(zaman(x.zaman()), x.kim(), x.sicil(), x.trenNo(), x.seferTarihi() == null ? null : x.seferTarihi().toString(), x.uyariGerekcesi(),
                        String.join("; ", x.uyarilar())))
                .toList();
        return new TabloExcel.Sayfa("Uyarılı atamalar", List.of("Zaman", "Kim", "Sicil", "Tren no", "Sefer tarihi", "Uyarı gerekçesi (atamacı)", "Uyaran kural"), s);
    }

    /** "Parametre ve kural değişiklikleri" bölümü (PARAMETRE_DEGISTI, KURAL_EKLE/GUNCELLE, PROFIL_UYGULA): ayar kayıtları. */
    private static TabloExcel.Sayfa ayarDegisiklikleri(DegisiklikRaporuDto rapor) {
        List<List<Object>> s = rapor.bolumler().stream().filter(b -> "PARAMETRE".equals(b.kod())).flatMap(b -> b.satirlar().stream())
                .map(x -> Arrays.<Object>asList(zaman(x.zaman()), x.kim(), x.turAdi(), x.eski(), x.yeni(), x.neden() == null ? null : x.neden().name(), x.nedenNotu())).toList();
        return new TabloExcel.Sayfa("Ayar değişiklikleri", List.of("Zaman", "Kim", "Tür", "Eski", "Yeni", "Neden", "Not"), s);
    }

    /** O anki değerler: parametre (kaynak/güvenilirlikle), kural şiddeti (K-10 dahil kural listesi), uygulama ayarı, özel kural. */
    private TabloExcel.Sayfa kuralDegerleri() {
        List<List<Object>> s = new ArrayList<>();
        parametreler.parametreListesi().forEach(p -> s.add(Arrays.asList("Parametre", p.anahtar(), p.ad(), p.deger(), p.birim(), p.kaynak(), p.guvenilirlik())));
        parametreler.kuralListesi().forEach(k -> s.add(Arrays.asList("Kural şiddeti", k.kod(), k.ad(), k.siddet().name(), null, k.kaynak(), k.guvenilirlik())));
        ayarlar.liste().forEach(a -> s.add(Arrays.asList("Uygulama ayarı", a.anahtar(), a.ad(), String.valueOf(a.deger()), a.birim(), null, null)));
        profil.disaAktar().ozelKurallar().forEach(k -> s.add(Arrays.asList("Özel kural", null, k.ad(), k.olcu() + " " + k.karsilastirma() + " " + k.esik() + " (" + k.pencere() + ") — " + k.siddet(), null,
                k.kaynak(), null)));
        return new TabloExcel.Sayfa("Kural değerleri", List.of("Tür", "Anahtar", "Ad", "Değer", "Birim", "Kaynak", "Güvenilirlik"), s);
    }

    private TabloExcel.Sayfa bilgiSayfasi(YonetimOzetiDto o, DegisiklikRaporuDto rapor, YearMonth ay) {
        List<List<Object>> s = new ArrayList<>();
        if (o.demo()) {
            s.add(List.of("UYARI", "DEMO – gerçek değil"));
        }
        s.add(List.of("Dönem", o.ay()));
        s.add(List.of("Tarih aralığı (takvim ayı)", ay.atDay(1) + " – " + ay.atEndOfMonth()));
        s.add(List.of("Üretim zamanı", zaman(o.uretimZamani())));
        s.add(Arrays.asList("Üreten", o.ureten()));
        s.add(List.of("Kayıt sayısı (Değişiklik kayıtları sayfası)", rapor.kayitSayisi()));
        s.add(List.of("Standart sapma", "Popülasyon standart sapması (n'e bölünür): √( Σ (saatᵢ − ortalama)² / n ); Adalet raporundaki AKTIF makinist grubunun görev saatlerinden."));
        s.add(List.of("Belge uyumu", "Dönem değil, rapor alındığı günkü durumdur (belge panosuyla aynı sınıflama)."));
        s.add(List.of("Kaynak", "Sayılar mevcut raporlarla aynıdır: Adalet, Puantaj, Denetim (değişiklik) raporu, Yedek planı, Belge panosu, Atama Masası (doluluk, kırmızı)."));
        s.add(List.of("Not", "Karar destek aracıdır; puantaj bordro değildir. Denetim kayıtları ek-yalnızdır, silinmez."));
        return new TabloExcel.Sayfa("Rapor bilgisi", List.of("Alan", "Değer"), s);
    }
}
