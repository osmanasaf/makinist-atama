package com.makinistatama.kural;

import java.time.Duration;
import java.time.Instant;

import com.makinistatama.kural.ZamanHesabi.Aralik;

/**
 * Bir makinistin tek sefer gorevi. {@code kalkis/varis} sefer saatleridir (belge, yetki, son surus, K-21);
 * {@code baslangic/bitis} calisma araligidir (dinlenme, cakisma, sure kurallari; {@link #calismaAraligi}).
 * Aralik yari-aciktir: [baslangic, bitis).
 * <p>
 * F2b (MK-39): {@code yolculuk} gorev makinistin yolcu olarak gittigi seferdir (surus degil): hazirlik/alikoyma yoktur, km 0'dir; K-10, K-11/K-12, K-13..K-19, K-20
 * ve nobet zinciri onu AYNEN gorur, ama belge/yetki (K-02..K-08), K-21 ve hat/arac/tren turu kapsamli ozel kurallar uygulanmaz. {@code sureSayilir=false} ise yolculuk
 * calisma suresinden sayilmaz ({@link #sureDakika()} 0, nobetin calisma araliklarina girmez) ama konumu ve cakismayi yine belirler.
 * {@code devamEden} (K-23 muafiyeti, OK-17): makinist onceki goreve ayni trenin onceki parcasindan devam ediyor (hazirlik yok); aktarma suresi aranmaz.
 */
public record Gorev(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId,
                    Instant kalkis, Instant varis, Instant baslangic, Instant bitis, double km, String trenTuru,
                    boolean yolculuk, boolean sureSayilir, boolean devamEden) {

    /** Zincir bagi olmayan gorev (devamEden=false). */
    public Gorev(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId, Instant kalkis, Instant varis, Instant baslangic, Instant bitis,
                 double km, String trenTuru, boolean yolculuk, boolean sureSayilir) {
        this(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, kalkis, varis, baslangic, bitis, km, trenTuru, yolculuk, sureSayilir, false);
    }

    /** Tren türü bilinmeyen (null) görev: hiçbir tren türü kapsamına girmez [V7]. */
    public Gorev(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId, Instant kalkis, Instant varis, Instant baslangic, Instant bitis,
                 double km) {
        this(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, kalkis, varis, baslangic, bitis, km, null);
    }

    /** Sürüş görevi ({@code yolculuk=false, sureSayilir=true}). */
    public Gorev(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId, Instant kalkis, Instant varis, Instant baslangic, Instant bitis,
                 double km, String trenTuru) {
        this(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, kalkis, varis, baslangic, bitis, km, trenTuru, false, true);
    }

    public Gorev {
        if (bitis.isBefore(baslangic) || varis.isBefore(kalkis)) {
            throw new IllegalArgumentException("Gorev bitisi baslangicindan once: sefer " + seferId);
        }
    }

    /** Sefer saatlerinden; calisma araligi icin bkz. {@link #calismaAraligi}. {@code baslangicKalkisi}: bkz. calismaAraligi (OK-6). */
    public static Gorev olustur(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId,
                                Instant baslangicKalkisi, Instant kalkis, Instant varis, double km, Parametreler p) {
        return olustur(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, baslangicKalkisi, kalkis, varis, km, null, p);
    }

    /** {@code trenTuru}: "YUK" / "YOLCU" / null (belirsiz). */
    public static Gorev olustur(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId,
                                Instant baslangicKalkisi, Instant kalkis, Instant varis, double km, String trenTuru, Parametreler p) {
        return olustur(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, baslangicKalkisi, kalkis, varis, km, trenTuru, p, true, true);
    }

    /** Parça zincirinde makinist trende kalıyorsa hazırlık / alıkoyma eklenmez ({@link #calismaAraligi(Instant, Instant, Instant, Parametreler, boolean, boolean)}). */
    public static Gorev olustur(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId,
                                Instant baslangicKalkisi, Instant kalkis, Instant varis, double km, String trenTuru, Parametreler p, boolean hazirlik, boolean alikoyma) {
        Aralik a = calismaAraligi(baslangicKalkisi, kalkis, varis, p, hazirlik, alikoyma);
        return new Gorev(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, kalkis, varis, a.bas(), a.bit(), km, trenTuru, false, true, !hazirlik);
    }

    /** Gecikme yokmus gibi (planlanan kalkis = kalkis). */
    public static Gorev olustur(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId,
                                Instant kalkis, Instant varis, double km, Parametreler p) {
        return olustur(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, kalkis, kalkis, varis, km, p);
    }

    /**
     * Yolculuk gorevi (MK-39): calisma araligi {@link #calismaAraligi(boolean, Instant, Instant, Instant, Parametreler)} yolculuk dalidir (hazirlik/alikoyma yok),
     * km 0, tren turu bos. Parametre okumaz.
     */
    public static Gorev yolculukOlustur(long seferId, long kalkisGarId, long varisGarId, long hatId, long aracTipiId,
                                        Instant baslangicKalkisi, Instant kalkis, Instant varis, boolean sureSayilir) {
        Aralik a = calismaAraligi(true, baslangicKalkisi, kalkis, varis, null);
        return new Gorev(seferId, kalkisGarId, varisGarId, hatId, aracTipiId, kalkis, varis, a.bas(), a.bit(), 0, null, true, sureSayilir);
    }

    /**
     * Calisma suresinin TEK tanimi (OK-1, OK-2): kural motoru, adalet raporu ve puantaj ayni araligi kullanir.
     * Baslangic = min(baslangicKalkisi, guncel kalkis) - {@code gorev.hazirlik_dk}. {@code baslangicKalkisi} (OK-6): atama gecikme girilmeden
     * ONCE yapildiysa planlanan kalkis (makinist o saatte gorevdeydi, bekleme calismadir; TIS md. 32), gecikme bilindikten SONRA yapildiysa guncel kalkis
     * (planlanan + atamadaki gecikme; bkz. {@code atama.kalkis_gecikme_dk}), bitis = guncel varis + {@code gorev.alikoyma_dk}.
     * OK-17 (F2c): makinistin onceki gorevi AYNI trenin onceki parcasiysa (makinist zaten trende; bekleme yok) donuk baslangic uygulanmaz, cagiran guncel kalkisi verir -
     * bu karar kural motoru degil veri katmaninda ({@code AtamaRepository.aralik}) verilir; motor ve raporlar yalniz {@code baslangicKalkisi} gorur.
     */
    public static Aralik calismaAraligi(Instant baslangicKalkisi, Instant kalkis, Instant varis, Parametreler p) {
        return calismaAraligi(baslangicKalkisi, kalkis, varis, p, true, true);
    }

    /**
     * F2c tur 3: aynı makinistin aynı trendeki ardışık parçaları arasında makinist trende kalır; {@code hazirlik=false} (önceki parça aynı makinistte) kalkıştan önce işe çağırma,
     * {@code alikoyma=false} (sonraki parça aynı makinistte) varıştan sonra alıkoyma eklenmez. Tek kaynak: kural motoru, kırmızı hesabı, adalet ve puantaj bu fonksiyondan geçer.
     */
    public static Aralik calismaAraligi(Instant baslangicKalkisi, Instant kalkis, Instant varis, Parametreler p, boolean hazirlik, boolean alikoyma) {
        Instant bas = baslangicKalkisi.isBefore(kalkis) ? baslangicKalkisi : kalkis;
        return new Aralik(hazirlik ? bas.minusSeconds(60 * p.tamsayi("gorev.hazirlik_dk")) : bas, alikoyma ? varis.plusSeconds(60 * p.tamsayi("gorev.alikoyma_dk")) : varis);
    }

    /**
     * Ayni tanim, yolculuk dalli (F2b, MK-39; kural ve rapor TEK fonksiyondan gecer): yolculukta aralik [min(baslangicKalkisi, kalkis), varis] - hazirlik ve
     * alikoyma YOK ({@code p} okunmaz, null olabilir). Yolculuk degilse {@link #calismaAraligi(Instant, Instant, Instant, Parametreler)}.
     */
    public static Aralik calismaAraligi(boolean yolculuk, Instant baslangicKalkisi, Instant kalkis, Instant varis, Parametreler p) {
        return calismaAraligi(yolculuk, baslangicKalkisi, kalkis, varis, p, true, true);
    }

    /** Yolculuk dalı hazırlık/alıkoyma içermez; sürüşte {@link #calismaAraligi(Instant, Instant, Instant, Parametreler, boolean, boolean)}. */
    public static Aralik calismaAraligi(boolean yolculuk, Instant baslangicKalkisi, Instant kalkis, Instant varis, Parametreler p, boolean hazirlik, boolean alikoyma) {
        if (!yolculuk) {
            return calismaAraligi(baslangicKalkisi, kalkis, varis, p, hazirlik, alikoyma);
        }
        return new Aralik(baslangicKalkisi.isBefore(kalkis) ? baslangicKalkisi : kalkis, varis);
    }

    /** Calisma suresi (dk); sayilmayan yolculukta 0 (adalet "bu ay saati", otomatik atama aylik dakika ve {@code Yuk.buAy} ayni tanim). */
    public long sureDakika() {
        return sureSayilir ? Duration.between(baslangic, bitis).toMinutes() : 0;
    }
}
