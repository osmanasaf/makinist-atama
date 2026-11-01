package com.makinistatama.parametre;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.kural.Siddet;
import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ProfilPaketi.OzelKuralGirdisi;
import com.makinistatama.parametre.ProfilPaketi.UygulaIstegi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kural profili: dışa aktarma, hazır profiller, önizleme (fark tablosu) ve seçmeli uygulama (MK-162..166). Sunucuda durum yok: önizleme ve uygulama AYNI
 * {@link #degerlendir} doğrulayıcısını kullanır; uygulama paketi yeniden doğrular (istemciye güvenilmez), TEK işlemde ve tüm-ya-hiç yazar. Yazma yolları mevcut servislerin
 * toplu yöntemleridir ({@link ParametreServisi#topluUygula}, {@link AyarServisi#topluUygula}, {@link OzelKuralServisi#profilUygula}); ikinci bir doğrulayıcı yoktur.
 */
@Service
@Transactional(readOnly = true)
public class ProfilServisi {

    /** Önizleme satırı: {@code kimlik} = {@code TUR:anahtar} (uygulamada seçim anahtarı), {@code durum} = DEGISIR / AYNI / GECERSIZ / BILINMEYEN / PROFILDE_YOK. */
    public record Satir(String kimlik, String tur, String anahtar, String ad, String mevcut, String yeni, String durum, String neden, String dayanak, String kaynak,
            String guvenilirlik) {
    }

    public record Ozet(int degisir, int ayni, int gecersiz, int bilinmeyen, int profildeYok) {
    }

    public record Onizleme(ProfilPaketi paket, List<Satir> satirlar, List<String> uyarilar, Ozet ozet) {
    }

    public record UygulaSonucu(int yazilan, int atlanan) {
    }

    public record HazirOzet(String kod, String ad, String aciklama, String etiket) {
    }

    /** Değerlendirmenin ham sonucu: gösterim satırları + uygulanabilir (DEGISIR) kanonik değerler. */
    private record Degerlendirme(List<Satir> satirlar, List<String> uyarilar, Map<String, String> degerler, Map<String, Siddet> siddetler, Map<String, Integer> ayarlar,
            Map<String, OzelKuralGirdisi> kurallar) {
    }

    private final KuralParametreRepository parametreDepo;
    private final KuralSiddetRepository siddetDepo;
    private final UygulamaAyarRepository ayarDepo;
    private final ParametreServisi parametreler;
    private final AyarServisi ayarlar;
    private final OzelKuralServisi ozelKurallar;
    private final HazirProfiller hazirlar;
    private final DegisiklikServisi degisiklik;
    private final JsonMapper json;
    private final Clock saat;

    ProfilServisi(KuralParametreRepository parametreDepo, KuralSiddetRepository siddetDepo, UygulamaAyarRepository ayarDepo, ParametreServisi parametreler, AyarServisi ayarlar,
            OzelKuralServisi ozelKurallar, HazirProfiller hazirlar, DegisiklikServisi degisiklik, JsonMapper json, Clock saat) {
        this.parametreDepo = parametreDepo;
        this.siddetDepo = siddetDepo;
        this.ayarDepo = ayarDepo;
        this.parametreler = parametreler;
        this.ayarlar = ayarlar;
        this.ozelKurallar = ozelKurallar;
        this.hazirlar = hazirlar;
        this.degisiklik = degisiklik;
        this.json = json;
        this.saat = saat;
    }

    // ------------------------------------------------------------------ dışa aktarma ve hazır profiller

    /** Bu kurulumun tüm ayarları (parametreler, K-10 hariç kural şiddetleri, uygulama ayarları, özel kurallar); değerler API'deki ile aynıdır. */
    public ProfilPaketi disaAktar() {
        Map<String, String> p = new LinkedHashMap<>();
        parametreDepo.findAllByOrderByAnahtar().forEach(x -> p.put(x.anahtar(), x.deger()));
        Map<String, String> s = new LinkedHashMap<>();
        siddetDepo.findAllByOrderByKod().forEach(x -> s.put(x.kod(), x.siddet().name()));
        Map<String, Number> a = new LinkedHashMap<>();
        ayarlarSirali().forEach(x -> a.put(x.anahtar(), x.deger()));
        return new ProfilPaketi(ProfilPaketi.FORMAT, ProfilPaketi.SURUM, "Mevcut ayarlar", "Bu kurulumdan dışa aktarılan ayarlar, şiddetler ve özel kurallar.",
                "Bu kurulumdan dışa aktarıldı — kurum mevzuatıyla doğrulanmalı. Karar destek aracıdır.", saat.instant().truncatedTo(ChronoUnit.SECONDS).toString(),
                DegisiklikServisi.simdikiKullaniciAdi(), Map.of(), p, s, a, ozelKurallar.profilIcinDisaAktar());
    }

    public List<HazirOzet> hazirListe() {
        return hazirlar.hepsi().stream().map(h -> new HazirOzet(h.kod(), h.ad(), h.aciklama(), h.etiket())).toList();
    }

    /** Hazır profil: veritabanındaki {@code varsayilan} sütunları + profilin önerdiği farklar (özel kural içermez). */
    public ProfilPaketi hazir(String kod) {
        HazirProfiller.Profil h = hazirlar.bul(kod).orElseThrow(() -> UygulamaHatasi.bulunamadi("Hazır profil bulunamadı."));
        Map<String, String> p = new LinkedHashMap<>();
        parametreDepo.findAllByOrderByAnahtar().forEach(x -> p.put(x.anahtar(), x.varsayilan() == null ? "" : x.varsayilan()));
        p.putAll(h.oneri().parametreler());
        Map<String, String> s = new LinkedHashMap<>();
        siddetDepo.findAllByOrderByKod().forEach(x -> s.put(x.kod(), x.varsayilan().name()));
        s.putAll(h.oneri().siddetler());
        Map<String, Number> a = new LinkedHashMap<>();
        ayarlarSirali().forEach(x -> a.put(x.anahtar(), x.varsayilan()));
        a.putAll(h.oneri().ayarlar());
        return new ProfilPaketi(ProfilPaketi.FORMAT, ProfilPaketi.SURUM, h.ad(), h.aciklama(), h.etiket(), saat.instant().truncatedTo(ChronoUnit.SECONDS).toString(),
                "Hazır profil", h.dayanak(), p, s, a, List.of());
    }

    private List<UygulamaAyar> ayarlarSirali() {
        return ayarDepo.findAllByOrderByGrupAscSiraAsc().stream().sorted(Comparator.comparing(UygulamaAyar::anahtar)).toList();
    }

    // ------------------------------------------------------------------ önizleme

    /** Yazmaz, denetim kaydı üretmez (AK-2.3). */
    public Onizleme onizle(ProfilPaketi paket) {
        Degerlendirme d = degerlendir(paket);
        List<String> uyarilar = new ArrayList<>(d.uyarilar());
        parametreler.caprazMesajlari(d.degerler(), d.siddetler()).forEach(m -> uyarilar.add("Tutarlılık: " + m));
        int[] sayac = new int[5];
        for (Satir s : d.satirlar()) {
            sayac[switch (s.durum()) {
                case "DEGISIR" -> 0;
                case "AYNI" -> 1;
                case "GECERSIZ" -> 2;
                case "BILINMEYEN" -> 3;
                default -> 4;
            }]++;
        }
        return new Onizleme(paket, d.satirlar(), List.copyOf(uyarilar), new Ozet(sayac[0], sayac[1], sayac[2], sayac[3], sayac[4]));
    }

    // ------------------------------------------------------------------ uygulama

    /**
     * Seçilen satırları TEK işlemde yazar. Seçilenlerden biri GECERSIZ/BILINMEYEN ise 422 {@code PROFIL_GECERSIZ} ve hiçbir şey yazılmaz; çapraz tutarlılık seçilenlerin
     * NİHAİ durumu üzerinden denetlenir. Değişen her anahtar için {@code PARAMETRE_DEGISTI}, özel kural için {@code KURAL_EKLE/GUNCELLE}, ayrıca tek {@code PROFIL_UYGULA}
     * özet kaydı düşer (hiçbir şey değişmediyse özet kaydı da yazılmaz).
     */
    @Transactional
    public UygulaSonucu uygula(UygulaIstegi istek) {
        String neden = istek.neden() == null ? "" : istek.neden().strip();
        if (neden.isEmpty()) {
            throw UygulamaHatasi.dogrulama("neden", "Değişiklik nedeni yazılmalı.");
        }
        if (neden.length() > 500) {
            throw UygulamaHatasi.dogrulama("neden", "Neden en çok 500 karakter olabilir.");
        }
        ProfilPaketi paket = istek.paket();
        Degerlendirme d = degerlendir(paket);
        Map<String, Satir> kimlikle = new LinkedHashMap<>();
        d.satirlar().forEach(s -> kimlikle.put(s.kimlik(), s));
        Set<String> secilen = new LinkedHashSet<>(istek.secilen());
        List<Satir> gecersiz = new ArrayList<>();
        for (String kimlik : secilen) {
            Satir s = kimlikle.get(kimlik);
            if (s == null) {
                throw UygulamaHatasi.dogrulama("secilen", "Profilde böyle bir satır yok: " + kimlik);
            }
            if (s.durum().equals("GECERSIZ") || s.durum().equals("BILINMEYEN")) {
                gecersiz.add(s);
            }
        }
        if (!gecersiz.isEmpty()) {
            throw new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "PROFIL_GECERSIZ",
                    "Seçilen satırlardan " + gecersiz.size() + " tanesi uygulanamaz; hiçbir değişiklik yapılmadı.", Map.of("satirlar", gecersiz));
        }
        Map<String, String> degerler = new LinkedHashMap<>();
        Map<String, Siddet> siddetler = new LinkedHashMap<>();
        Map<String, Integer> ayarDegerleri = new LinkedHashMap<>();
        List<OzelKuralGirdisi> kurallar = new ArrayList<>();
        for (String kimlik : secilen) {
            Satir s = kimlikle.get(kimlik);
            if (!s.durum().equals("DEGISIR")) {
                continue; // AYNI / PROFILDE_YOK: yazılacak bir şey yok
            }
            switch (s.tur()) {
                case "PARAMETRE" -> degerler.put(s.anahtar(), d.degerler().get(s.anahtar()));
                case "SIDDET" -> siddetler.put(s.anahtar(), d.siddetler().get(s.anahtar()));
                case "AYAR" -> ayarDegerleri.put(s.anahtar(), d.ayarlar().get(s.anahtar()));
                default -> kurallar.add(d.kurallar().get(s.kimlik()));
            }
        }
        String ad = paket.ad() == null || paket.ad().isBlank() ? "(adsız)" : paket.ad().strip();
        String nedenMetni = "Profil: " + (ad.length() > 80 ? ad.substring(0, 80) : ad) + " — " + neden;
        var toplu = parametreler.topluUygula(degerler, siddetler, nedenMetni);
        int ayarSayisi = ayarlar.topluUygula(ayarDegerleri, nedenMetni);
        for (OzelKuralGirdisi g : kurallar) {
            ozelKurallar.profilUygula(g, nedenMetni);
        }
        int yazilan = toplu.parametre() + toplu.siddet() + ayarSayisi + kurallar.size();
        if (yazilan > 0) {
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.PROFIL_UYGULA, "PROFIL", null).neden(Neden.DIGER, neden)
                    .ayrinti(ozetJson(Map.of("profil", ad, "profilSurumu", paket.profilSurumu(), "secilen", secilen.size(), "yazilan", yazilan))));
        }
        return new UygulaSonucu(yazilan, secilen.size() - yazilan);
    }

    private String ozetJson(Map<String, Object> m) {
        try {
            return json.writeValueAsString(m);
        } catch (JacksonException e) {
            throw new IllegalStateException("Profil özeti yazılamadı", e);
        }
    }

    // ------------------------------------------------------------------ doğrulayıcı (tek)

    private Degerlendirme degerlendir(ProfilPaketi paket) {
        List<Satir> satirlar = new ArrayList<>();
        List<String> uyarilar = new ArrayList<>();
        Map<String, String> degerler = new LinkedHashMap<>();
        Map<String, Siddet> siddetler = new LinkedHashMap<>();
        Map<String, Integer> ayarDegerleri = new LinkedHashMap<>();
        Map<String, OzelKuralGirdisi> kurallar = new LinkedHashMap<>();
        if (paket.profilSurumu() != null && paket.profilSurumu() < ProfilPaketi.SURUM) {
            uyarilar.add("Profil eski bir biçim sürümüyle hazırlanmış (sürüm " + paket.profilSurumu() + "); pakette olmayan ayarlar mevcut değerinde bırakılır.");
        }

        // --- parametreler
        Set<String> bilinenParametre = new HashSet<>();
        for (KuralParametre p : parametreDepo.findAllByOrderByAnahtar()) {
            bilinenParametre.add(p.anahtar());
            String ad = AyarAdlari.ad(p.anahtar());
            String dayanak = paket.dayanak().get(p.anahtar());
            String kimlik = "PARAMETRE:" + p.anahtar();
            if (!paket.parametreler().containsKey(p.anahtar())) {
                satirlar.add(new Satir(kimlik, "PARAMETRE", p.anahtar(), ad, p.deger(), null, "PROFILDE_YOK", null, dayanak, p.kaynak(), p.guvenilirlik()));
                continue;
            }
            String ham = paket.parametreler().get(p.anahtar());
            try {
                if (ham == null) {
                    throw UygulamaHatasi.dogrulama("deger", "Değer boş olamaz.");
                }
                String yeni = ParametreServisi.degerDogrula(p, ham.strip());
                boolean ayni = ParametreServisi.ayniDeger(p, p.deger(), yeni);
                if (!ayni) {
                    degerler.put(p.anahtar(), yeni);
                }
                satirlar.add(new Satir(kimlik, "PARAMETRE", p.anahtar(), ad, p.deger(), yeni, ayni ? "AYNI" : "DEGISIR", null, dayanak, p.kaynak(), p.guvenilirlik()));
            } catch (UygulamaHatasi e) {
                satirlar.add(new Satir(kimlik, "PARAMETRE", p.anahtar(), ad, p.deger(), kisalt(ham), "GECERSIZ", e.getMessage(), dayanak, p.kaynak(), p.guvenilirlik()));
            }
        }
        for (var e : paket.parametreler().entrySet()) {
            if (!bilinenParametre.contains(e.getKey())) {
                satirlar.add(new Satir("PARAMETRE:" + kisalt(e.getKey()), "PARAMETRE", kisalt(e.getKey()), kisalt(e.getKey()), null, kisalt(e.getValue()), "BILINMEYEN",
                        "Bu kurulumda böyle bir ayar yok.", null, null, null));
            }
        }

        // --- kural şiddetleri
        Set<String> bilinenSiddet = new HashSet<>();
        for (KuralSiddet k : siddetDepo.findAllByOrderByKod()) {
            bilinenSiddet.add(k.kod());
            String ad = k.kod() + " " + AyarAdlari.ad(k.kod());
            String dayanak = paket.dayanak().get(k.kod());
            String kimlik = "SIDDET:" + k.kod();
            if (!paket.siddetler().containsKey(k.kod())) {
                satirlar.add(new Satir(kimlik, "SIDDET", k.kod(), ad, k.siddet().name(), null, "PROFILDE_YOK", null, dayanak, k.kaynak(), k.guvenilirlik()));
                continue;
            }
            String ham = paket.siddetler().get(k.kod());
            Siddet yeni = siddetOku(ham);
            if (yeni == null) {
                satirlar.add(new Satir(kimlik, "SIDDET", k.kod(), ad, k.siddet().name(), kisalt(ham), "GECERSIZ", "Şiddet ENGEL, UYARI ya da KAPALI olmalı.", dayanak, k.kaynak(),
                        k.guvenilirlik()));
                continue;
            }
            boolean ayni = yeni == k.siddet();
            if (!ayni) {
                siddetler.put(k.kod(), yeni);
            }
            satirlar.add(new Satir(kimlik, "SIDDET", k.kod(), ad, k.siddet().name(), yeni.name(), ayni ? "AYNI" : "DEGISIR", null, dayanak, k.kaynak(), k.guvenilirlik()));
        }
        for (var e : paket.siddetler().entrySet()) {
            if (!bilinenSiddet.contains(e.getKey())) {
                boolean k10 = e.getKey().equals("K-10");
                satirlar.add(new Satir("SIDDET:" + kisalt(e.getKey()), "SIDDET", kisalt(e.getKey()), kisalt(e.getKey()), null, kisalt(e.getValue()), k10 ? "GECERSIZ" : "BILINMEYEN",
                        k10 ? "K-10 (çakışma) fiziksel bir kuraldır, profile girmez." : "Bu kurulumda böyle bir kural yok.", null, null, null));
            }
        }

        // --- uygulama ayarları
        Set<String> bilinenAyar = new HashSet<>();
        for (UygulamaAyar a : ayarlarSirali()) {
            bilinenAyar.add(a.anahtar());
            String ad = AyarAdlari.ad(a.anahtar());
            String dayanak = paket.dayanak().get(a.anahtar());
            String kimlik = "AYAR:" + a.anahtar();
            if (!paket.ayarlar().containsKey(a.anahtar())) {
                satirlar.add(new Satir(kimlik, "AYAR", a.anahtar(), ad, String.valueOf(a.deger()), null, "PROFILDE_YOK", null, dayanak, null, null));
                continue;
            }
            Number ham = paket.ayarlar().get(a.anahtar());
            Integer yeni = tamsayi(ham);
            if (yeni == null) {
                satirlar.add(new Satir(kimlik, "AYAR", a.anahtar(), ad, String.valueOf(a.deger()), kisalt(String.valueOf(ham)), "GECERSIZ", "Değer tam sayı olmalı.", dayanak, null, null));
            } else if (yeni < a.alt() || yeni > a.ust()) {
                satirlar.add(new Satir(kimlik, "AYAR", a.anahtar(), ad, String.valueOf(a.deger()), String.valueOf(yeni), "GECERSIZ",
                        "Değer " + a.alt() + " ile " + a.ust() + " arasında olmalı.", dayanak, null, null));
            } else {
                boolean ayni = yeni == a.deger();
                if (!ayni) {
                    ayarDegerleri.put(a.anahtar(), yeni);
                }
                satirlar.add(new Satir(kimlik, "AYAR", a.anahtar(), ad, String.valueOf(a.deger()), String.valueOf(yeni), ayni ? "AYNI" : "DEGISIR", null, dayanak, null, null));
            }
        }
        for (var e : paket.ayarlar().entrySet()) {
            if (!bilinenAyar.contains(e.getKey())) {
                satirlar.add(new Satir("AYAR:" + kisalt(e.getKey()), "AYAR", kisalt(e.getKey()), kisalt(e.getKey()), null, kisalt(String.valueOf(e.getValue())), "BILINMEYEN",
                        "Bu kurulumda böyle bir ayar yok.", null, null, null));
            }
        }

        // --- özel kurallar (ad ile eşleşme; kapsam tanım adıyla; silme/kapatma yok)
        int acik = ozelKurallar.aktifler().size();
        Set<String> gorulen = new HashSet<>();
        int sira = 0;
        for (OzelKuralGirdisi g : paket.ozelKurallar()) {
            sira++;
            String adHam = g.ad() == null ? "" : g.ad().strip().replaceAll("\\s+", " ");
            String anahtar = adHam.isEmpty() ? "#" + sira : AramaMetni.katla(adHam);
            String kimlik = "OZEL_KURAL:" + anahtar;
            String gosterim = adHam.isEmpty() ? "Özel kural #" + sira : adHam;
            if (adHam.isEmpty() || !gorulen.add(anahtar)) {
                satirlar.add(new Satir(kimlik, "OZEL_KURAL", anahtar, gosterim, null, null, "GECERSIZ",
                        adHam.isEmpty() ? "Kural adı yazılmalı." : "Aynı ad profilde birden çok kez geçiyor.", null, g.kaynak(), null));
                continue;
            }
            var sonuc = ozelKurallar.profilDegerlendir(g);
            String durum = sonuc.durum();
            String neden = sonuc.neden();
            if (!durum.equals("GECERSIZ")) { // açık kural sınırı paketin kendi eklemeleriyle de aşılmasın
                if (!sonuc.mevcutAcik() && sonuc.yeniAcik()) {
                    if (acik >= OzelKuralServisi.EN_COK_AKTIF) {
                        durum = "GECERSIZ";
                        neden = "En çok " + OzelKuralServisi.EN_COK_AKTIF + " açık özel kural olabilir; önce birini kapatın.";
                    } else {
                        acik++;
                    }
                } else if (sonuc.mevcutAcik() && !sonuc.yeniAcik()) {
                    acik--;
                }
            }
            satirlar.add(new Satir(kimlik, "OZEL_KURAL", anahtar, gosterim, sonuc.mevcut(), sonuc.yeni(), durum, neden, null, g.kaynak(), null));
            if (durum.equals("DEGISIR")) {
                kurallar.put(kimlik, g);
            }
        }
        return new Degerlendirme(satirlar, uyarilar, degerler, siddetler, ayarDegerleri, kurallar);
    }

    private static Siddet siddetOku(String ham) {
        if (ham == null) {
            return null;
        }
        try {
            return Siddet.valueOf(ham.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Yalnız tam sayı (32 bit); kesirli, üstel ya da sınır dışı sayı {@code null}. */
    private static Integer tamsayi(Number n) {
        if (n instanceof Integer || n instanceof Short || n instanceof Byte) {
            return n.intValue();
        }
        if (n instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        return null;
    }

    /** Ekranda ve yanıtta çok uzun ham değer (1 MB'lık metin) taşınmaz. */
    private static String kisalt(String s) {
        return s == null ? null : s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
