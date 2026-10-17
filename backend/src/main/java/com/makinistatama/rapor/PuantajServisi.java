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
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.rapor.PuantajHesabi.Oranlar;

/**
 * Puantaj ön hesabı servisi (FR-12, T14.2): AKTIF makinist başına (ya da tek makinist) aylık çalışma, fazla mesai, gece ve km; hesap
 * {@link PuantajHesabi}'ndadır. Oranlar parametreden gelir, boşsa TL alanları {@code null} kalır. Ücret bordrosu değildir.
 */
@Service
@Transactional(readOnly = true)
public class PuantajServisi {

    static final String ETIKET = "Puantaj ön hesabı — ücret bordrosu değildir";

    private final AtamaServisi atamalar;
    private final MakinistServisi makinistler;
    private final ParametreServisi parametreler;
    private final Clock saat;

    PuantajServisi(AtamaServisi atamalar, MakinistServisi makinistler, ParametreServisi parametreler, Clock saat) {
        this.atamalar = atamalar;
        this.makinistler = makinistler;
        this.parametreler = parametreler;
        this.saat = saat;
    }

    /** Makinist grubu (vars.; F3b, MK-89). */
    public PuantajRaporuDto rapor(YearMonth ay, Long makinistId) {
        return rapor(ay, makinistId, PersonelTuru.MAKINIST);
    }

    /** @param makinistId boşsa {@code tur} türünün tüm AKTIF personeli; doluysa yalnız o kişi (404 yoksa; raporun türü kişinin türüdür) */
    public PuantajRaporuDto rapor(YearMonth ay, Long makinistId, PersonelTuru tur) {
        Parametreler p = parametreler.yukle();
        Oranlar o = oranlar(p);
        List<MakinistDto> kisiler = makinistId != null ? List.of(makinistler.getir(makinistId))
                : makinistler.aktifDetaylar(Set.of(tur));
        PersonelTuru raporTuru = makinistId != null ? kisiler.getFirst().tur() : tur;
        // ayın ilk Pazar'ının haftası önceki aydan başlayabilir; önceki ayın son gecesi de aya taşar
        LocalDate bas = ay.atDay(1).minusDays(8);
        Map<Long, List<AtamaSatiri>> satirlar = atamalar.calismaSatirlari(kisiler.stream().map(MakinistDto::id).toList(), bas, ay.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(AtamaSatiri::makinistId));

        List<PuantajRaporuDto.Satir> sonuc = new ArrayList<>();
        for (MakinistDto m : kisiler) {
            List<PuantajHesabi.Gorev> gorevler = satirlar.getOrDefault(m.id(), List.of()).stream().map(a -> gorev(a, p)).toList();
            PuantajHesabi.Sonuc s = PuantajHesabi.hesapla(ay, gorevler, o);
            sonuc.add(new PuantajRaporuDto.Satir(m.id(), m.sicil(), m.adSoyad(), s.calisilanGun(), saat2(s.fiiliDakika()), saat2(s.ucretlendirilenDakika()),
                    saat2(s.fazlaMesaiDakika()), saat2(s.geceDakika()), s.km().setScale(1, RoundingMode.HALF_UP).doubleValue(), s.kmBilinmeyenSefer(), s.fazlaMesaiTl(),
                    s.geceTl(), s.kmTl()));
        }
        var gosterim = new PuantajRaporuDto.Oranlar(o.saatUcreti(), o.fazlaMesaiCarpani(), o.geceOrani(), o.kmBirimTutari(), o.asgariGunDakika() / 60.0,
                o.normalHaftaDakika() / 60.0, o.geceBaslangic() + "–" + o.geceBitis());
        return new PuantajRaporuDto(ay.toString(), OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(), ETIKET,
                makinistler.demoVerisiVarMi(), o.saatUcreti() != null, o.kmBirimTutari() != null, gosterim, sonuc, raporTuru);
    }

    /** Atama satırının çalışma aralığı tek kaynaktan ({@code kural.Gorev.calismaAraligi}); puantaj ve adalet raporu aynı dönüşümü kullanır (OK-1, OK-2). */
    static PuantajHesabi.Gorev gorev(AtamaSatiri a, Parametreler p) {
        ZamanHesabi.Aralik c = com.makinistatama.kural.Gorev.calismaAraligi(a.yolculuk(), a.baslangicKalkisi(), a.kalkis(), a.varis(), p, !a.devamEden(), !a.devamEdecek());
        // yolculukta km sabit 0 (null DEĞİL: "km bilinmeyen sefer" sayılmasın; sefer km'si yolcuya yazılmaz; MK-40)
        return new PuantajHesabi.Gorev(a.tarih(), c.bas(), c.bit(), a.yolculuk() ? BigDecimal.ZERO : a.km());
    }

    static Oranlar oranlar(Parametreler p) {
        return new Oranlar(p.dakika("haftalik.normal_saat"), p.saat("gece.baslangic"), p.saat("puantaj.gece_bitis"), p.oran("puantaj.gece_tazminat_orani"),
                p.dakika("puantaj.asgari_gunluk_saat"), bosOlabilir(p, "puantaj.saat_ucreti"), p.oran("puantaj.fazla_mesai_carpani"), bosOlabilir(p, "puantaj.km_birim_tutari"));
    }

    private static BigDecimal bosOlabilir(Parametreler p, String anahtar) {
        String v = p.degerler().get(anahtar);
        return v == null || v.isBlank() ? null : p.oran(anahtar);
    }

    private static double saat2(long dakika) {
        return Math.round(dakika / 60.0 * 100) / 100.0;
    }

    /** Aynı rapor .xlsx: "Puantaj" (oran girilmemişse TL sütunları boş) + "Rapor bilgisi" (etiket, DEMO uyarısı, oranlar). */
    public byte[] excel(PuantajRaporuDto r) {
        List<List<Object>> satirlar = r.satirlar().stream().map(s -> Arrays.<Object>asList(s.sicil(), s.adSoyad(), s.calisilanGun(), s.fiiliSaat(),
                s.ucretlendirilenSaat(), s.fazlaMesaiSaat(), s.geceSaat(), s.km(), s.kmBilinmeyenSefer(), s.fazlaMesaiTl(), s.geceTl(), s.kmTl())).toList();
        var o = r.oranlar();
        List<List<Object>> bilgi = new ArrayList<>(List.of(List.of("Ay", r.ay()), List.of("Personel türü", r.tur().name()), List.of("Not", r.etiket()),
                List.of("Üretim zamanı", r.uretimZamani().toLocalDateTime().toString().replace('T', ' ')), List.of("Üreten", r.ureten()),
                Arrays.<Object>asList("Saat ücreti (TL)", o.saatUcreti() == null ? "oran girilmedi" : o.saatUcreti()),
                List.of("Fazla mesai çarpanı", o.fazlaMesaiCarpani()), List.of("Gece tazminatı oranı", o.geceTazminatOrani()),
                Arrays.<Object>asList("Km birim tutarı (TL)", o.kmBirimTutari() == null ? "oran girilmedi" : o.kmBirimTutari()),
                List.of("Asgari günlük süre (saat)", o.asgariGunlukSaat()), List.of("Normal haftalık süre (saat)", o.normalHaftalikSaat()),
                List.of("Gece saati penceresi", o.gecePenceresi())));
        if (r.demo()) {
            bilgi.add(0, List.of("UYARI", "DEMO – gerçek değil"));
        }
        return TabloExcel.yaz(List.of(new TabloExcel.Sayfa("Puantaj", List.of("Sicil", "Ad soyad", "Çalışılan gün", "Fiili saat", "Ücretlendirilen saat", "Fazla mesai saati",
                "Gece saati", "Km", "Km bilinmeyen sefer", "Fazla mesai TL", "Gece TL", "Km TL"), satirlar),
                new TabloExcel.Sayfa("Rapor bilgisi", List.of("Alan", "Değer"), bilgi)));
    }
}
