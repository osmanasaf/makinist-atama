package com.makinistatama.rapor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.makinistatama.atama.KadroRaporuDto;
import com.makinistatama.cikti.TabloExcel;
import com.makinistatama.cikti.TabloExcel.Sayfa;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Kadro ihtiyacı Excel'i (F3a, MK-79; OK-22): gövde istemcinin aldığı {@link KadroRaporuDto}'dur, sunucu YENİDEN HESAPLAMAZ (hesap pahalıdır). Bu yüzden belge "sunucu hesabı"
 * iddiası taşımaz (Varsayımlar sayfasında yazılı); güven sınırı: toplam satır ve dize uzunluğu sınırlıdır (aşım 400), formül enjeksiyonu {@link TabloExcel} ile engellenir.
 */
final class KadroExcel {

    static final int EN_COK_SATIR = 5000;
    static final int EN_COK_DIZE = 500;

    private KadroExcel() {
    }

    static byte[] yaz(KadroRaporuDto r) {
        dogrula(r);
        List<Sayfa> sayfalar = new ArrayList<>();
        sayfalar.add(ozet(r));
        KadroRaporuDto.Simulasyon sim = r.simulasyon();
        if (sim != null) {
            sayfalar.add(new Sayfa("Doluluk eğrisi", List.of("Ek genel makinist", "Boş sefer", "Doluluk %", "Kişi başı kapanan sefer"),
                    sim.egri().stream().map(e -> List.<Object>of(e.ek(), e.bos(), yuvarla(e.dolulukYuzde()), e.marjinal() == null ? "" : yuvarla(e.marjinal()))).toList()));
            sayfalar.add(new Sayfa("Eklenen dağılımı", List.of("Bağlı gar", "Araç tipi", "Hat", "Kişi", "İşlediği sefer", "Haftalık saat"),
                    sim.dagilim().stream().map(d -> List.<Object>of(metin(d.gar()), metin(d.aracTipi()), metin(d.hat()), d.kisi(), d.seferSayisi(), yuvarla(d.haftalikSaat()))).toList()));
            sayfalar.add(new Sayfa("Kalan boş nedenleri", List.of("Neden", "Boş sefer"), sim.kalanNedenler().stream().map(n -> List.<Object>of(metin(n.neden()), n.adet())).toList()));
            sayfalar.add(new Sayfa("Kalan boşlar", List.of("Sefer", "Tren no", "Tarih", "Kalkış", "Varış", "Neden"),
                    sim.kalanBoslar().stream().map(b -> List.<Object>of(b.seferId() == null ? "" : b.seferId(), metin(b.trenNo()), String.valueOf(b.tarih()),
                            metin(b.kalkisGar()), metin(b.varisGar()), metin(b.neden()))).toList()));
        }
        sayfalar.add(new Sayfa("Talep", List.of("Hat", "Araç tipi", "Sefer sayısı", "Haftalık saat"),
                r.talep().stream().map(t -> List.<Object>of(metin(t.hat()), metin(t.aracTipi()), t.seferSayisi(), yuvarla(t.haftalikSaat()))).toList()));
        sayfalar.add(new Sayfa("Garlar", List.of("Gar", "Haftalık talep saati (kalkış)", "Mevcut makinist", "Eklenen (dağılım noktası)"),
                r.garlar().stream().map(g -> List.<Object>of(metin(g.gar()), yuvarla(g.haftalikTalepSaat()), g.mevcutMakinist(), g.eklenen())).toList()));
        sayfalar.add(new Sayfa("Bröve kümeleri", List.of("Araç tipleri", "Hatlar", "Mevcut kişi", "Kullanılan saat/hafta", "Kapasite saat/hafta", "Kullanım %", "Durum"),
                r.kumeler().stream().map(k -> List.<Object>of(String.join(", ", k.aracTipleri()), String.join(", ", k.hatlar()), k.mevcut(), yuvarla(k.kullanilanSaat()),
                        yuvarla(k.kapasiteSaat()), yuvarla(k.kullanimYuzde()), metin(k.durum()))).toList()));
        sayfalar.add(new Sayfa("Sınıf analizi", List.of("Hat", "Araç tipi", "Haftalık saat", "Gerekli kişi (tek bröveli)", "Mevcut yetkili", "Eksik saat", "Eksik kişi"),
                r.analitik().siniflar().stream().map(s -> List.<Object>of(metin(s.hat()), metin(s.aracTipi()), yuvarla(s.haftalikSaat()), s.gerekliKisi(), s.mevcutYetkili(),
                        yuvarla(s.eksikSaat()), s.eksikKisi())).toList()));
        if (r.senaryo() != null) {
            KadroRaporuDto.Senaryo s = r.senaryo();
            List<List<Object>> satirlar = new ArrayList<>();
            satirlar.add(List.of("Ek sefer sayısı", s.ekSeferSayisi()));
            satirlar.add(List.of("Ek haftalık saat", yuvarla(s.ekHaftalikSaat())));
            if (s.taban() != null && s.senaryo() != null) {
                satirlar.add(List.of("Taban: toplam boş sefer (mevcut kadro)", s.taban().bos()));
                satirlar.add(List.of("Senaryo: toplam boş sefer (aynı kadro)", s.senaryo().bos()));
                satirlar.add(List.of("Toplam boş farkı", s.bosFarki()));
                satirlar.add(List.of("Taban doluluk %", yuvarla(s.taban().dolulukYuzde())));
                satirlar.add(List.of("Senaryo doluluk %", yuvarla(s.senaryo().dolulukYuzde())));
                satirlar.add(List.of("Gürültü eşiği (boş)", s.gurultuEsigi()));
            }
            satirlar.add(List.of("Mevcut kadro yeter mi", s.mevcutYeter() ? "Evet" : "Hayır"));
            if (s.egriFarki() != null) {
                s.egriFarki().forEach(h -> satirlar.add(List.of("Eğri farkı: %" + h.yuzde() + " için ek makinist (taban → senaryo)",
                        (h.tabanEk() == null ? "ulaşılamadı" : String.valueOf(h.tabanEk())) + " → " + (h.senaryoEk() == null ? "ulaşılamadı" : String.valueOf(h.senaryoEk())))));
            }
            satirlar.add(List.of("Ek talep (kişi-eşdeğeri)", yuvarla(s.ekTalepKisiEsdegeri())));
            satirlar.add(List.of("Mevcut kadroda atıl kapasite (kişi-eşdeğeri)", yuvarla(s.atilKapasiteKisiEsdegeri())));
            satirlar.add(List.of("Analitik eksik saat: taban → senaryo", yuvarla(s.tabanEksikSaat()) + " → " + yuvarla(s.senaryoEksikSaat())));
            satirlar.add(List.of("Gerekçe", metin(s.gerekce())));
            if (s.uyari() != null) {
                satirlar.add(List.of("Uyarı", s.uyari()));
            }
            sayfalar.add(new Sayfa("Senaryo farkı", List.of("Kalem", "Değer"), satirlar));
        }
        sayfalar.add(varsayimlar(r));
        return TabloExcel.yaz(sayfalar);
    }

    private static String metin(String s) {
        return s == null ? "" : s;
    }

    private static String doluluk(KadroRaporuDto.Doluluk d) {
        return d.bos() + " boş / " + d.seferSayisi() + " sefer (doluluk %" + yuvarla(d.dolulukYuzde()) + ")";
    }

    private static Sayfa ozet(KadroRaporuDto r) {
        List<List<Object>> s = new ArrayList<>();
        KadroRaporuDto.Varsayim v = r.varsayim();
        s.add(List.of("Dönem", v.bas() + " – " + v.bit() + " (" + v.hafta() + " hafta)"));
        s.add(List.of("Not", "Sonuç seçilen " + v.hafta() + " haftalık pencereye bağlıdır; başka bir başlangıç tarihi ya da ufukta değişebilir."));
        s.add(List.of("Mevcut asıl makinist", r.analitik().mevcutAsil()));
        s.add(List.of("Haftalık talep (saat)", yuvarla(v.haftalikTalepSaat())));
        s.add(List.of("Kişi başı haftalık saat (devamsızlık dahil)", yuvarla(v.kisiBasinaSaat())));
        KadroRaporuDto.Simulasyon sim = r.simulasyon();
        if (sim != null) {
            s.add(List.of("BAŞLIK: mevcut kadroyla doluluk (" + metin(sim.mevcut().etiket()) + ")", doluluk(sim.mevcut())));
            for (KadroRaporuDto.Hedef h : sim.hedefler()) {
                s.add(List.of("%" + h.yuzde() + " doluluk için ek genel makinist", h.ulasildi() ? String.valueOf(h.ek())
                        : "ulaşılamadı (eğrinin en iyi noktası: " + h.bos() + " boş, doluluk %" + yuvarla(h.dolulukYuzde()) + ")"));
            }
            s.add(List.of("Eğri durma nedeni", metin(sim.egriDurma())));
            if (sim.konumsuz() != null) {
                s.add(List.of("Mevcut kadro, K-20 kapalı (yalnız kapasite)", doluluk(sim.konumsuz())));
            }
            if (sim.iyilestirmeli() != null) {
                s.add(List.of("Mevcut kadro, 'daha çok sefer doldur' (iyileştirmeli)", doluluk(sim.iyilestirmeli())));
            }
            if (sim.yolculuk() != null) {
                s.add(List.of("Mevcut kadro, yolculuk aşamalı", doluluk(sim.yolculuk())));
            }
            sim.atlananlar().forEach(a -> s.add(List.of("Hesaplanmadı", a)));
            s.add(List.of("Eklenen dağılımı dayanağı", metin(sim.dagilimEtiketi())));
            s.add(List.of("Ek hesaplar tamamlandı mı", sim.tamamlandi() ? "Evet" : "Hayır (süre sınırı: yalnız ek hesaplar kısmi; eğri etkilenmez)"));
        }
        s.add(List.of("Analitik alt sınır: gerekli kadro (her bröve olsa bile)", r.analitik().gerekliAltSinir()));
        s.add(List.of("Tek bröveli sanal makinistle gerekli kadro", r.analitik().tekBroveliGerekli()));
        s.add(List.of("Mevcut kadroya eksik (analitik alt sınır)", r.analitik().eksikAltSinir()));
        if (r.yedek() != null) {
            s.add(List.of("Yedek ihtiyacı (günlük en büyük; ayrı satır, devamsızlık içermez)", r.yedek().ihtiyac()));
        }
        return new Sayfa("Özet", List.of("Kalem", "Değer"), s);
    }

    private static Sayfa varsayimlar(KadroRaporuDto r) {
        KadroRaporuDto.Varsayim v = r.varsayim();
        List<List<Object>> s = new ArrayList<>();
        s.add(List.of("Devamsızlık yüzdesi", v.devamsizlikYuzde() + " (kaynaksız yer tutucu; İK verisiyle değişir). Yalnız simülasyonda (sentetik izin) ve aynı varsayımı paylaşan analitik hesapta sayılır."));
        s.add(List.of("Yedek oranı / asgari", v.yedekOran() + " / " + v.yedekMin() + " (kaynaksız yer tutucu); yedek operasyonel tampondur, devamsızlık içermez."));
        s.add(List.of("Ek hesap süre sınırı (sn)", v.sureSn() + " (yalnız iyileştirmeli/yolculuklu plan ve senaryo eğri farkı için; doluluk eğrisi süreden bağımsızdır)"));
        s.add(List.of("Mevcut atamalar", "Aralıktaki VE aralık sonrasındaki mevcut atamalar yok sayıldı (temiz sayfa); izinler ve aralık öncesi görevler korundu."));
        s.add(List.of("Sanal makinist", "GENEL: tarifedeki tüm araç tipi ve hat bröveleri, belgeleri geçerli, bağlı garı boş kalan seferlerin kalkış garı; devamsızlık sentetik izinle dağıtıldı. Kişi sayısı bu varsayımla ölçülür; bröve dağılımı plandan türetilir."));
        s.add(List.of("Eğri", "Ek makinist sayısına göre en az boş (≤ ek kişiyle bulunan en iyi); açgözlü ekleme, kurallar tekdüze olmadığı için 'en az' kanıtlanmaz."));
        s.add(List.of("Planlayıcı", "K-20 açık, iyileştirmesiz; 'daha çok sefer doldur' ayrı satırda."));
        s.add(List.of("Analitik alt sınır", "Yalnız saat ve yetki (gevşetilmiş akış); konum, dinlenme ve nöbet kuralları dahil değildir."));
        s.add(List.of("Belge", "Bu dosya ekrandaki rapordan üretildi; sunucu yeniden hesaplamadı."));
        return new Sayfa("Varsayımlar", List.of("Konu", "Açıklama"), s);
    }

    private static double yuvarla(double d) {
        return Math.round(d * 100) / 100.0;
    }

    /** Güven sınırı: istemci DTO'sunu geri gönderir; boyut ve alan sınırları aşılırsa 400, eksik ana bölüm 400. */
    static void dogrula(KadroRaporuDto r) {
        if (r == null || r.varsayim() == null || r.analitik() == null || r.talep() == null || r.garlar() == null || r.kumeler() == null) {
            throw UygulamaHatasi.dogrulama("rapor", "Rapor eksik: önce hesaplayın.");
        }
        if (r.varsayim().bas() == null || r.varsayim().bit() == null) {
            throw UygulamaHatasi.dogrulama("rapor", "Rapor eksik: dönem yok.");
        }
        if (r.analitik().siniflar() == null) {
            throw UygulamaHatasi.dogrulama("rapor", "Rapor eksik: sınıf analizi yok.");
        }
        if (r.talep().stream().anyMatch(Objects::isNull) || r.garlar().stream().anyMatch(Objects::isNull) || r.kumeler().stream().anyMatch(Objects::isNull)
                || r.analitik().siniflar().stream().anyMatch(Objects::isNull) || r.kumeler().stream().anyMatch(k -> k.aracTipleri() == null || k.hatlar() == null)) {
            throw UygulamaHatasi.dogrulama("rapor", "Rapor eksik: bozuk satır.");
        }
        int satir = r.talep().size() + r.garlar().size() + r.kumeler().size() + r.analitik().siniflar().size();
        KadroRaporuDto.Simulasyon sim = r.simulasyon();
        if (sim != null) {
            if (sim.mevcut() == null || sim.egri() == null || sim.hedefler() == null || sim.dagilim() == null || sim.kalanBoslar() == null
                    || sim.kalanNedenler() == null || sim.atlananlar() == null || sim.egri().stream().anyMatch(Objects::isNull) || sim.hedefler().stream().anyMatch(Objects::isNull)
                    || sim.dagilim().stream().anyMatch(Objects::isNull) || sim.kalanBoslar().stream().anyMatch(Objects::isNull) || sim.kalanNedenler().stream().anyMatch(Objects::isNull)) {
                throw UygulamaHatasi.dogrulama("rapor", "Rapor eksik: simülasyon bölümü bozuk.");
            }
            satir += sim.egri().size() + sim.hedefler().size() + sim.dagilim().size() + sim.kalanBoslar().size() + sim.kalanNedenler().size() + sim.atlananlar().size();
        }
        if (r.senaryo() != null) {
            if (r.senaryo().egriFarki() != null && r.senaryo().egriFarki().stream().anyMatch(Objects::isNull)) {
                throw UygulamaHatasi.dogrulama("rapor", "Rapor eksik: senaryo bölümü bozuk.");
            }
            satir += r.senaryo().egriFarki() == null ? 0 : r.senaryo().egriFarki().size();
        }
        if (satir > EN_COK_SATIR) {
            throw UygulamaHatasi.dogrulama("rapor", "Rapor çok büyük: en çok " + EN_COK_SATIR + " satır.");
        }
        if (dizeler(r).stream().anyMatch(d -> d.length() > EN_COK_DIZE)) {
            throw UygulamaHatasi.dogrulama("rapor", "Rapordaki bir metin " + EN_COK_DIZE + " karakteri aşıyor.");
        }
    }

    /** Raporun tüm metin alanları (uzunluk denetimi için). */
    private static List<String> dizeler(KadroRaporuDto r) {
        List<String> d = new ArrayList<>();
        r.talep().forEach(t -> {
            d.add(t.hat());
            d.add(t.aracTipi());
        });
        r.garlar().forEach(g -> d.add(g.gar()));
        r.kumeler().forEach(k -> {
            d.addAll(k.aracTipleri());
            d.addAll(k.hatlar());
            d.add(k.durum());
        });
        r.analitik().siniflar().forEach(s -> {
            d.add(s.hat());
            d.add(s.aracTipi());
        });
        d.add(r.kumeKaynagi());
        if (r.simulasyon() != null) {
            KadroRaporuDto.Simulasyon s = r.simulasyon();
            for (KadroRaporuDto.Doluluk k : Arrays.asList(s.mevcut(), s.konumsuz(), s.iyilestirmeli(), s.yolculuk())) {
                if (k != null) {
                    d.add(k.etiket());
                    d.add(k.ad());
                }
            }
            d.add(s.egriDurma());
            d.add(s.dagilimEtiketi());
            d.addAll(s.atlananlar());
            s.dagilim().forEach(e -> {
                d.add(e.gar());
                d.add(e.aracTipi());
                d.add(e.hat());
            });
            s.kalanNedenler().forEach(n -> d.add(n.neden()));
            s.kalanBoslar().forEach(b -> {
                d.add(b.trenNo());
                d.add(b.kalkisGar());
                d.add(b.varisGar());
                d.add(b.neden());
            });
        }
        if (r.senaryo() != null) {
            d.add(r.senaryo().gerekce());
            d.add(r.senaryo().uyari());
            for (KadroRaporuDto.Doluluk k : Arrays.asList(r.senaryo().taban(), r.senaryo().senaryo())) {
                if (k != null) {
                    d.add(k.etiket());
                    d.add(k.ad());
                }
            }
        }
        d.removeIf(Objects::isNull);
        return d;
    }
}
