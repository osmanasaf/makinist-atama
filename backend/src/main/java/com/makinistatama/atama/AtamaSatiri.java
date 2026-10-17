package com.makinistatama.atama;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.makinistatama.kural.ZamanHesabi;
import com.makinistatama.sefer.TrenTuru;

/** {@code atama ⨝ sefer} projeksiyonu (MK-6): kural bağlamı, "bu ay" toplamı ve izin etkisi tek sorgudan beslenir. İptal seferler sorguda yoktur. */
public record AtamaSatiri(Long atamaId, Long seferId, AtamaRol rol, Long makinistId, LocalDate tarih, String trenNo, Long kalkisGarId, Long varisGarId,
        Long hatId, Long aracTipiId, Instant planlananKalkis, Instant planlananVaris, int kalkisGecikmeDk, int varisGecikmeDk, BigDecimal km, int atamaGecikmeDk,
        TrenTuru trenTuru, Integer parcaNo, LocalDate trenGunu, boolean devamEden, boolean devamEdecek) {

    /** İntikal satırının {@code trenNo} değeri (gösterimde "tren no" yerine). */
    public static final String INTIKAL_ETIKETI = "İntikal";

    /**
     * Serbest intikal (MK-105): TEK üretim yeri. {@code atamaId = null} (intikal bayrağı), {@code seferId = -id} (negatif, gerçek sefer kimliğiyle çakışmaz), rol YOLCU: kural motoru
     * onu F2b yolculuğu gibi görür (hazırlık/alıkoyma yok, km 0, belge/yetki sorulmaz). Hat/araç tipi 0 (yolculukta okunmaz).
     */
    static AtamaSatiri intikaldan(Intikal i) {
        return new AtamaSatiri(null, i.seferKimligi(), AtamaRol.YOLCU, i.makinistId(), ZamanHesabi.gun(i.kalkis()), INTIKAL_ETIKETI, i.kalkisGarId(), i.varisGarId(), 0L, 0L,
                i.kalkis(), i.varis(), 0, 0, null, 0, null, null, null, false, false);
    }

    /** Satır serbest bir intikal mi ({@code atama} satırı değil; {@code atamaId} yok). */
    public boolean intikal() {
        return atamaId == null;
    }

    /** OK-17: aynı satır, başlangıç gecikmesi verilen değerle (aday önceki parçaysa bağ henüz kurulmamışken de güncel kalkış; {@code BaglamYukleyici.Yuk.sefereBagla}). */
    public AtamaSatiri atamaGecikmeyle(int gecikmeDk) {
        return new AtamaSatiri(atamaId, seferId, rol, makinistId, tarih, trenNo, kalkisGarId, varisGarId, hatId, aracTipiId, planlananKalkis, planlananVaris, kalkisGecikmeDk,
                varisGecikmeDk, km, gecikmeDk, trenTuru, parcaNo, trenGunu, devamEden, devamEdecek);
    }

    /** Aynı satır, zincir bayraklarıyla: {@code devamEden} = önceki parça aynı makinistte (hazırlık yok), {@code devamEdecek} = sonraki parça aynı makinistte (alıkoyma yok). */
    public AtamaSatiri zincirle(boolean devamEdenYeni, boolean devamEdecekYeni) {
        return new AtamaSatiri(atamaId, seferId, rol, makinistId, tarih, trenNo, kalkisGarId, varisGarId, hatId, aracTipiId, planlananKalkis, planlananVaris, kalkisGecikmeDk,
                varisGecikmeDk, km, atamaGecikmeDk, trenTuru, parcaNo, trenGunu, devamEdenYeni, devamEdecekYeni);
    }

    /** OK-6: görevin başlangıç saati; atama gecikme bilinmeden yapıldıysa planlanan, bilindikten sonra yapıldıysa güncel kalkış (güncel kalkıştan geç olamaz: {@code kural.Gorev.calismaAraligi} min alır). OK-17: aynı trenin önceki parçasından devam eden makinistte {@code atamaGecikmeDk} sorguda zaten güncel kalkış gecikmesidir ({@code AtamaRepository.aralik}). */
    public Instant baslangicKalkisi() {
        return planlananKalkis.plusSeconds(60L * atamaGecikmeDk);
    }

    /** F2b (MK-40): YOLCU satırı sürüş değildir; çalışma aralığı hazırlık/alıkoyma içermez, km 0 sayılır. Tüm tüketiciler bu TEK bayrağa bakar. */
    public boolean yolculuk() {
        return rol == AtamaRol.YOLCU;
    }

    public Instant kalkis() {
        return planlananKalkis.plusSeconds(60L * kalkisGecikmeDk);
    }

    public Instant varis() {
        return planlananVaris.plusSeconds(60L * varisGecikmeDk);
    }
}
