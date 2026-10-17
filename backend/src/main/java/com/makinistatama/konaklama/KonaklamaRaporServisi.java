package com.makinistatama.konaklama;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.cikti.TabloExcel;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;

/**
 * Aylık konaklama gece raporu (F2b, MK-44): ay = GİRİŞ günü; gece = {@link KonaklamaHesabi#gece} (ceil(süre / 24 s), en az 1). Makinist x gar kırılımı ve makinist başına
 * "kaydı eksik öneri" (o ayın öneri sayısı − kayıtlı olanlar): öneri olup kaydı girilmemiş dış gar boşlukları.
 */
@Service
@Transactional(readOnly = true)
public class KonaklamaRaporServisi {

    /** Bir makinistin bir gardaki toplamı. */
    public record GarSatiri(Long garId, String gar, int konaklamaSayisi, int gece) {
    }

    public record MakinistSatiri(Long makinistId, String sicil, String adSoyad, int konaklamaSayisi, int gece, int eksikOneri, List<GarSatiri> garlar) {
    }

    public record KonaklamaRaporuDto(String ay, OffsetDateTime uretimZamani, String ureten, int toplamGece, int toplamEksikOneri, List<MakinistSatiri> makinistler) {
    }

    private final KonaklamaRepository depo;
    private final KonaklamaServisi konaklama;
    private final MakinistServisi makinistler;
    private final com.makinistatama.tanim.TanimServisi tanimlar;
    private final Clock saat;

    KonaklamaRaporServisi(KonaklamaRepository depo, KonaklamaServisi konaklama, MakinistServisi makinistler, com.makinistatama.tanim.TanimServisi tanimlar, Clock saat) {
        this.depo = depo;
        this.konaklama = konaklama;
        this.makinistler = makinistler;
        this.tanimlar = tanimlar;
        this.saat = saat;
    }

    public KonaklamaRaporuDto rapor(YearMonth ay) {
        Map<Long, String> garAdlari = tanimlar.tumAdlar();
        Map<Long, MakinistDto.Ozet> kisiler = makinistler.liste(null, null).stream().collect(Collectors.toMap(MakinistDto.Ozet::id, o -> o));
        List<Konaklama> kayitlar = depo.girisAraliginda(KonaklamaServisi.gunBasi(ay.atDay(1)), KonaklamaServisi.gunBasi(ay.plusMonths(1).atDay(1)));
        Map<Long, Integer> eksik = new LinkedHashMap<>();
        for (KonaklamaOneriDto o : konaklama.oner(ay.atDay(1), ay.atEndOfMonth(), null)) {
            if (!o.kayitli()) {
                eksik.merge(o.makinistId(), 1, Integer::sum);
            }
        }
        Map<Long, List<Konaklama>> makinistKayitlari = kayitlar.stream().collect(Collectors.groupingBy(Konaklama::makinistId));
        java.util.Set<Long> idler = new java.util.TreeSet<>(makinistKayitlari.keySet());
        idler.addAll(eksik.keySet());
        List<MakinistSatiri> satirlar = new ArrayList<>();
        for (Long id : idler) {
            MakinistDto.Ozet m = kisiler.get(id);
            Map<Long, List<Konaklama>> garaGore = makinistKayitlari.getOrDefault(id, List.of()).stream().collect(Collectors.groupingBy(Konaklama::garId));
            List<GarSatiri> garlar = garaGore.entrySet().stream()
                    .map(e -> new GarSatiri(e.getKey(), garAdlari.get(e.getKey()), e.getValue().size(), e.getValue().stream().mapToInt(k -> KonaklamaHesabi.gece(k.giris(), k.cikis())).sum()))
                    .sorted(Comparator.comparing(GarSatiri::gar, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            satirlar.add(new MakinistSatiri(id, m == null ? null : m.sicil(), m == null ? null : m.adSoyad(), garlar.stream().mapToInt(GarSatiri::konaklamaSayisi).sum(),
                    garlar.stream().mapToInt(GarSatiri::gece).sum(), eksik.getOrDefault(id, 0), garlar));
        }
        satirlar.sort(Comparator.comparing(MakinistSatiri::sicil, Comparator.nullsLast(Comparator.naturalOrder())));
        return new KonaklamaRaporuDto(ay.toString(), OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(),
                satirlar.stream().mapToInt(MakinistSatiri::gece).sum(), satirlar.stream().mapToInt(MakinistSatiri::eksikOneri).sum(), satirlar);
    }

    public byte[] excel(KonaklamaRaporuDto r) {
        List<List<Object>> garSatirlari = new ArrayList<>();
        List<List<Object>> ozet = new ArrayList<>();
        for (MakinistSatiri m : r.makinistler()) {
            ozet.add(java.util.Arrays.<Object>asList(m.sicil(), m.adSoyad(), m.konaklamaSayisi(), m.gece(), m.eksikOneri()));
            for (GarSatiri g : m.garlar()) {
                garSatirlari.add(java.util.Arrays.<Object>asList(m.sicil(), m.adSoyad(), g.gar(), g.konaklamaSayisi(), g.gece()));
            }
        }
        List<List<Object>> bilgi = List.of(List.of("Ay", r.ay()), List.of("Üretim zamanı", r.uretimZamani().toLocalDateTime().toString().replace('T', ' ')), List.of("Üreten", r.ureten()),
                List.of("Toplam gece", r.toplamGece()), List.of("Kaydı eksik öneri", r.toplamEksikOneri()),
                List.of("Dipnot", "Gece sayısı = süre / 24 saat yukarı yuvarlanır (en az 1); konaklama giriş gününün ayına yazılır. Kaydı eksik öneri: dış garda iki nöbet arası konaklama gerektiren boşluk olup kaydı girilmemiş."));
        return TabloExcel.yaz(List.of(
                new TabloExcel.Sayfa("Konaklama", List.of("Sicil", "Ad soyad", "Gar", "Konaklama sayısı", "Gece"), garSatirlari),
                new TabloExcel.Sayfa("Makinist özeti", List.of("Sicil", "Ad soyad", "Konaklama sayısı", "Gece", "Kaydı eksik öneri"), ozet),
                new TabloExcel.Sayfa("Rapor bilgisi", List.of("Alan", "Değer"), bilgi)));
    }
}
