package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Kural parametreleri (MK-10). {@link #yukle()} her çağrıda ({@code ≈31 satır}) güncel değerleri saf {@link Parametreler} kaydına çevirir;
 * önbellek yoktur (geçersizleştirme riski, kazanç yok). Yazma (YONETICI, FR-03): tür ve sınır doğrulanır, neden zorunludur, her değişiklik
 * eski -> yeni kayda düşer ve sonraki {@link #yukle()} çağrısında hemen görünür.
 */
@Service
@Transactional(readOnly = true)
public class ParametreServisi {

    private static final Pattern SAAT_GUNU = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]");
    /** Boş bırakılabilen ("boşsa hesaplanmaz") parametreler. */
    private static final Set<String> ISTEGE_BAGLI = Set.of("puantaj.saat_ucreti", "puantaj.km_birim_tutari");
    /** Tam sayı olması gereken birimler (ay, adet, hafta, gün, dk, 0/1). */
    private static final Set<String> TAMSAYI_BIRIMLERI = Set.of("ay", "adet", "hafta", "gün", "dk", "0/1");
    private static final Set<String> ADALET_OLCUTLERI = Set.of("SAAT", "KM");
    /** Üst sınırı tablodan gelmeyen sayılar (TL tutarları) için tavan ve büyüklük denetimi: üstel yazım (1E40000000) metne dökülmeden reddedilir. */
    private static final BigDecimal SINIRSIZ_TAVAN = BigDecimal.valueOf(1_000_000_000L);
    private static final int EN_FAZLA_TAM_BASAMAK = 12;
    private static final int EN_FAZLA_ONDALIK = 6;

    private final KuralParametreRepository parametreler;
    private final KuralSiddetRepository siddetler;
    private final DegisiklikServisi degisiklik;
    private final OzelKuralServisi ozelKurallar;

    ParametreServisi(KuralParametreRepository parametreler, KuralSiddetRepository siddetler, DegisiklikServisi degisiklik, OzelKuralServisi ozelKurallar) {
        this.parametreler = parametreler;
        this.siddetler = siddetler;
        this.degisiklik = degisiklik;
        this.ozelKurallar = ozelKurallar;
    }

    public Parametreler yukle() {
        Map<String, String> degerler = new LinkedHashMap<>();
        parametreler.findAllByOrderByAnahtar().forEach(p -> degerler.put(p.anahtar(), p.deger()));
        Map<String, Siddet> siddet = new LinkedHashMap<>();
        siddetler.findAllByOrderByKod().forEach(s -> siddet.put(s.kod(), s.siddet()));
        return Parametreler.olustur(degerler, siddet, ozelKurallar.aktifler()); // MK-17: aktif özel kurallar (en çok 50 satır, +1 SELECT)
    }

    public List<ParametreDto> parametreListesi() {
        return parametreler.findAllByOrderByAnahtar().stream().map(ParametreDto::of).toList();
    }

    public List<KuralSiddetDto> kuralListesi() {
        return siddetler.findAllByOrderByKod().stream().map(KuralSiddetDto::of).toList();
    }

    // ------------------------------------------------------------------ yazma (YONETICI)

    @Transactional
    public ParametreDto degerDegistir(String anahtar, ParametreGuncellemeIstegi istek) {
        KuralParametre p = parametreler.findById(anahtar).orElseThrow(() -> UygulamaHatasi.bulunamadi("Parametre bulunamadı."));
        String yeni = degerDogrula(p, istek.deger().strip());
        if (p.tip().equals("SAYI") && !yeni.isEmpty()) {
            String hata = CaprazDenetim.parametre(anahtar, Double.parseDouble(yeni), this::sayi, this::siddetiOku);
            if (hata != null) {
                throw UygulamaHatasi.dogrulama("deger", hata);
            }
        }
        String eski = p.deger();
        if (!ayniDeger(p, eski, yeni)) {
            // ponytail: son yazan kazanır (satır kilidi yok); yalnız YONETICI yazar, eski değer kayıtta
            p.degerYaz(yeni);
            parametreler.flush();
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.PARAMETRE_DEGISTI, "PARAMETRE", null).degisim(eski, yeni)
                    .neden(Neden.DIGER, istek.neden().strip()).ayrinti("{\"anahtar\":\"" + anahtar + "\"}"));
        }
        return ParametreDto.of(p);
    }

    @Transactional
    public KuralSiddetDto siddetDegistir(String kod, SiddetGuncellemeIstegi istek) {
        if (kod.equals("K-10")) {
            throw UygulamaHatasi.dogrulama("kod", "K-10 (çakışma) fiziksel bir kuraldır, değiştirilemez.");
        }
        KuralSiddet k = siddetler.findById(kod).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kural bulunamadı."));
        Siddet eski = k.siddet();
        if (eski == Siddet.KAPALI && istek.siddet() != Siddet.KAPALI) { // kapalıyken aranmayan çapraz denetimler (K-14, K-23) açılırken denetlenir
            String hata = CaprazDenetim.acilis(kod, this::sayi);
            if (hata != null) {
                throw UygulamaHatasi.dogrulama("siddet", hata);
            }
        }
        if (eski != istek.siddet()) {
            k.siddetYaz(istek.siddet());
            siddetler.flush();
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.PARAMETRE_DEGISTI, "KURAL_SIDDET", null).degisim(eski, istek.siddet())
                    .neden(Neden.DIGER, istek.neden().strip()).ayrinti("{\"anahtar\":\"" + kod + "\"}"));
        }
        return KuralSiddetDto.of(k);
    }

    // ------------------------------------------------------------------ toplu uygulama (profil, MK-164)

    /** Toplu yazmanın sonucu: gerçekten değişen ayar ve kural sayısı. */
    public record TopluSonuc(int parametre, int siddet) {
    }

    /**
     * Profil uygulaması: değerler ve şiddetler TEK işlemde yazılır (tüm-ya-hiç). Her değer {@link #degerDogrula} ile (tür, aralık, üstel yazım) doğrulanır; çapraz tutarlılık
     * tek tek değil NİHAİ durum üzerinden denetlenir ({@link CaprazDenetim#tumu}); K-10 yazılamaz. Değişmeyen anahtar için kayıt yazılmaz; değişen her anahtar için mevcut
     * {@code PARAMETRE_DEGISTI} kaydı düşer (eski -> yeni, {@code neden}). Herhangi bir kontrol başarısızsa hiçbir şey yazılmaz.
     */
    @Transactional
    public TopluSonuc topluUygula(Map<String, String> degerler, Map<String, Siddet> yeniSiddetler, String neden) {
        Map<String, KuralParametre> mevcut = parametreler.findAllByOrderByAnahtar().stream().collect(java.util.stream.Collectors.toMap(KuralParametre::anahtar, p -> p));
        Map<String, String> degisenDegerler = new LinkedHashMap<>();
        for (var e : degerler.entrySet()) {
            KuralParametre p = mevcut.get(e.getKey());
            if (p == null) {
                throw UygulamaHatasi.dogrulama("parametreler", "Bilinmeyen ayar: " + e.getKey());
            }
            String yeni = degerDogrula(p, e.getValue().strip());
            if (!ayniDeger(p, p.deger(), yeni)) {
                degisenDegerler.put(e.getKey(), yeni);
            }
        }
        Map<String, KuralSiddet> mevcutSiddet = siddetler.findAllByOrderByKod().stream().collect(java.util.stream.Collectors.toMap(KuralSiddet::kod, k -> k));
        Map<String, Siddet> degisenSiddetler = new LinkedHashMap<>();
        for (var e : yeniSiddetler.entrySet()) {
            if (e.getKey().equals("K-10")) {
                throw UygulamaHatasi.dogrulama("siddetler", "K-10 (çakışma) fiziksel bir kuraldır, değiştirilemez.");
            }
            KuralSiddet k = mevcutSiddet.get(e.getKey());
            if (k == null) {
                throw UygulamaHatasi.dogrulama("siddetler", "Bilinmeyen kural: " + e.getKey());
            }
            if (k.siddet() != e.getValue()) {
                degisenSiddetler.put(e.getKey(), e.getValue());
            }
        }
        List<CaprazDenetim.Cakisma> cakismalar = caprazlar(mevcut, mevcutSiddet, degisenDegerler, degisenSiddetler);
        if (!cakismalar.isEmpty()) {
            throw new UygulamaHatasi(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "PROFIL_GECERSIZ",
                    cakismalar.stream().map(CaprazDenetim.Cakisma::mesaj).collect(java.util.stream.Collectors.joining(" ")), null);
        }
        for (var e : degisenDegerler.entrySet()) {
            KuralParametre p = mevcut.get(e.getKey());
            String eski = p.deger();
            p.degerYaz(e.getValue());
            parametreler.flush();
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.PARAMETRE_DEGISTI, "PARAMETRE", null).degisim(eski, e.getValue())
                    .neden(Neden.DIGER, neden).ayrinti("{\"anahtar\":\"" + e.getKey() + "\"}"));
        }
        for (var e : degisenSiddetler.entrySet()) {
            KuralSiddet k = mevcutSiddet.get(e.getKey());
            Siddet eski = k.siddet();
            k.siddetYaz(e.getValue());
            siddetler.flush();
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.PARAMETRE_DEGISTI, "KURAL_SIDDET", null).degisim(eski, e.getValue())
                    .neden(Neden.DIGER, neden).ayrinti("{\"anahtar\":\"" + e.getKey() + "\"}"));
        }
        return new TopluSonuc(degisenDegerler.size(), degisenSiddetler.size());
    }

    /**
     * Önizleme için (yazmaz): verilen kanonik DEĞİŞEN değer ve şiddetler uygulanırsa ortaya çıkacak nihai durumdaki çelişki mesajları. Önceden var olan, değişenlere
     * dokunmayan tutarsızlık sayılmaz.
     */
    public List<String> caprazMesajlari(Map<String, String> degisenDegerler, Map<String, Siddet> degisenSiddetler) {
        Map<String, KuralParametre> mevcut = parametreler.findAllByOrderByAnahtar().stream().collect(java.util.stream.Collectors.toMap(KuralParametre::anahtar, p -> p));
        Map<String, KuralSiddet> mevcutSiddet = siddetler.findAllByOrderByKod().stream().collect(java.util.stream.Collectors.toMap(KuralSiddet::kod, k -> k));
        return caprazlar(mevcut, mevcutSiddet, degisenDegerler, degisenSiddetler).stream().map(CaprazDenetim.Cakisma::mesaj).toList();
    }

    private static List<CaprazDenetim.Cakisma> caprazlar(Map<String, KuralParametre> mevcut, Map<String, KuralSiddet> mevcutSiddet, Map<String, String> degisenDegerler,
            Map<String, Siddet> degisenSiddetler) {
        Map<String, Double> sayilar = new java.util.HashMap<>();
        mevcut.forEach((a, p) -> {
            String d = degisenDegerler.getOrDefault(a, p.deger());
            if (p.tip().equals("SAYI") && !d.isEmpty()) {
                sayilar.put(a, Double.parseDouble(d.replace(',', '.')));
            }
        });
        Map<String, Siddet> siddetler = new java.util.HashMap<>();
        mevcutSiddet.forEach((k, s) -> siddetler.put(k, degisenSiddetler.getOrDefault(k, s.siddet())));
        return CaprazDenetim.tumu(sayilar, siddetler).stream()
                .filter(c -> degisenDegerler.containsKey(c.kucuk()) || degisenDegerler.containsKey(c.buyuk()) || c.sart() != null && degisenSiddetler.containsKey(c.sart()))
                .toList();
    }

    /** Sayıda metin değil değer karşılaştırılır ("0.10" == "0.1"): kanonik olmayan tohum yüzünden sahte değişiklik kaydı yazılmaz. */
    static boolean ayniDeger(KuralParametre p, String eski, String yeni) {
        if (eski.equals(yeni)) {
            return true;
        }
        try {
            return p.tip().equals("SAYI") && !eski.isEmpty() && !yeni.isEmpty() && new BigDecimal(eski.replace(',', '.')).compareTo(new BigDecimal(yeni)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Türüne göre doğrular ve saklanacak kanonik metni döner ("0,50" -> "0.5", "270" -> "270"). */
    static String degerDogrula(KuralParametre p, String ham) {
        String alan = "deger";
        if (ham.isEmpty()) {
            if (ISTEGE_BAGLI.contains(p.anahtar())) {
                return "";
            }
            throw UygulamaHatasi.dogrulama(alan, "Değer boş olamaz.");
        }
        switch (p.tip()) {
            case "SAAT_GUNU" -> {
                if (!SAAT_GUNU.matcher(ham).matches()) {
                    throw UygulamaHatasi.dogrulama(alan, "Saat sa:dk biçiminde olmalı (örn. 20:00).");
                }
                return ham;
            }
            case "METIN" -> {
                String m = ham.toUpperCase(java.util.Locale.ROOT);
                if (p.anahtar().equals("adalet.olcut") && !ADALET_OLCUTLERI.contains(m)) {
                    throw UygulamaHatasi.dogrulama(alan, "Ölçüt SAAT ya da KM olmalı.");
                }
                return m;
            }
            default -> {
                BigDecimal v;
                try {
                    v = new BigDecimal(ham.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw UygulamaHatasi.dogrulama(alan, "Değer sayı olmalı.");
                }
                // büyüklük önce ve metne dökmeden: "1E40000000" ya da "1E-40000000" gibi üstel yazımlar sınırsız metin üretirdi
                BigDecimal sade = v.stripTrailingZeros();
                if (v.signum() != 0 && (sade.scale() > EN_FAZLA_ONDALIK || sade.precision() - sade.scale() > EN_FAZLA_TAM_BASAMAK)) {
                    throw UygulamaHatasi.dogrulama(alan, "Değer en çok " + EN_FAZLA_TAM_BASAMAK + " basamaklı ve " + EN_FAZLA_ONDALIK + " ondalıklı olabilir.");
                }
                if (TAMSAYI_BIRIMLERI.contains(p.birim()) && sade.scale() > 0) {
                    throw UygulamaHatasi.dogrulama(alan, "Değer tam sayı olmalı.");
                }
                BigDecimal ust = p.ustSinir() != null ? p.ustSinir() : SINIRSIZ_TAVAN;
                if (p.altSinir() != null && v.compareTo(p.altSinir()) < 0 || v.compareTo(ust) > 0) {
                    throw UygulamaHatasi.dogrulama(alan, "Değer " + sinir(p.altSinir()) + " ile " + sinir(ust) + " arasında olmalı.");
                }
                return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
            }
        }
    }

    private static String sinir(BigDecimal s) {
        return s == null ? "…" : s.stripTrailingZeros().toPlainString();
    }

    private Siddet siddetiOku(String kod) {
        return siddetler.findById(kod).map(KuralSiddet::siddet).orElse(Siddet.ENGEL);
    }

    private double sayi(String anahtar) {
        return Double.parseDouble(parametreler.findById(anahtar).orElseThrow().deger().replace(',', '.'));
    }
}
