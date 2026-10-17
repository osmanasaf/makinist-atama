package com.makinistatama.sefer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferExcelOkuyucu.SatirHatasi;
import com.makinistatama.sefer.SeferExcelOkuyucu.Tanimlar;
import com.makinistatama.sefer.SeferExcelOkuyucu.YuklemeSatiri;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Excel yükleme (MK-12): stateless iki geçiş. Önizleme ve onay aynı uçtur ({@code onizle} bayrağı), onay dosyayı yeniden doğrular; sunucuda
 * geçici dosya yoktur. Var olan (tarih, tren no) satırları DEĞİŞMEZ ("zaten var" sayılır: iptal/gecikme ezilmez). Tek {@code SEFER_YUKLE} kaydı.
 */
@Service
public class SeferYuklemeServisi {

    private final SeferRepository depo;
    private final TanimServisi tanimlar;
    private final DegisiklikServisi degisiklik;
    private final AyarServisi ayarlar;

    SeferYuklemeServisi(SeferRepository depo, TanimServisi tanimlar, DegisiklikServisi degisiklik, AyarServisi ayarlar) {
        this.depo = depo;
        this.tanimlar = tanimlar;
        this.degisiklik = degisiklik;
        this.ayarlar = ayarlar;
    }

    @Transactional
    public YuklemeSonucuDto yukle(byte[] dosya, boolean onizle) {
        Tanimlar t = new Tanimlar(tanimlar.katlanmisAdlar(TanimTipi.GAR), tanimlar.katlanmisAdlar(TanimTipi.HAT),
                tanimlar.katlanmisAdlar(TanimTipi.ARAC_TIPI));
        SeferExcelOkuyucu.Sonuc okunan = SeferExcelOkuyucu.oku(dosya, t, ayarlar.tamsayi("sefer.parca_azami_saat"));
        if (okunan.engelli() && !onizle) { // arayüz önizlemede onayı kilitler; doğrudan çağrı da hiçbir şey yazamaz
            throw UygulamaHatasi.dogrulama("dosya", "Değişim Noktaları sayfasında Seferler sayfasında karşılığı olmayan tren no var; dosyayı düzeltip yeniden yükleyin.");
        }

        List<SatirHatasi> hatalar = new ArrayList<>(okunan.hatalar());
        Set<String> dosyadaGorulen = new HashSet<>();
        Set<Integer> reddedilenSatir = new HashSet<>();
        List<YuklemeSatiri> adaylar = new ArrayList<>();
        for (YuklemeSatiri s : okunan.gecerli()) {
            // Tekrar denetimi tren anahtarı üstünden, yalnız parça 1 / parçasız satırda; diğer parçalar satırlarının kararını izler (MK-60/61)
            if (s.parcaNo() != null && s.parcaNo() > 1) {
                if (!reddedilenSatir.contains(s.satirNo())) {
                    adaylar.add(s);
                }
            } else if (!dosyadaGorulen.add(anahtar(s))) {
                hatalar.add(new SatirHatasi(s.satirNo(), "Dosyada aynı tarih ve tren no tekrar ediyor", SeferExcelOkuyucu.SEFER_SAYFA, s.trenNo()));
                reddedilenSatir.add(s.satirNo());
            } else {
                adaylar.add(s);
            }
        }
        hatalar.sort(java.util.Comparator.comparing((SatirHatasi h) -> !SeferExcelOkuyucu.SEFER_SAYFA.equals(h.sayfa())).thenComparingInt(SatirHatasi::satirNo)); // önce Seferler, sonra Değişim Noktaları

        Set<LocalDate> gunler = adaylar.stream().map(SeferYuklemeServisi::trenGunu).collect(Collectors.toSet());
        Set<String> var = gunler.isEmpty() ? Set.of() : depo.trenGunuIn(gunler).stream()
                .map(s -> anahtar(s.trenGunu() != null ? s.trenGunu() : s.tarih(), s.trenNo())).collect(Collectors.toSet());
        List<YuklemeSatiri> yeniler = new ArrayList<>();
        Map<Integer, String> durum = new HashMap<>();
        for (YuklemeSatiri s : adaylar) {
            boolean zatenVar = var.contains(anahtar(s));
            durum.put(s.satirNo(), zatenVar ? "ZATEN_VAR" : "YENI");
            if (!zatenVar) {
                yeniler.add(s);
            }
        }

        if (!onizle && !yeniler.isEmpty()) {
            try {
                depo.saveAllAndFlush(yeniler.stream().map(SeferYuklemeServisi::varlik).toList());
            } catch (DataIntegrityViolationException e) { // eşzamanlı yükleme: önceden sorgulanan anahtarlar arasına başkası girdi
                throw UygulamaHatasi.cakisma("SEFER_TEKRARI", "Yükleme sırasında aynı seferler başka bir işlemle eklendi. Dosyayı yeniden önizleyip deneyin.");
            }
        }
        if (!onizle) {
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_YUKLE, "SEFER", null).degisim(null, yeniler.size() + " sefer")
                    .ayrinti("{\"eklenen\":" + yeniler.size() + ",\"zatenVar\":" + (adaylar.size() - yeniler.size()) + ",\"hata\":" + hatalar.size() + "}"));
        }

        Set<Long> idler = new HashSet<>();
        adaylar.forEach(s -> {
            idler.add(s.kalkisGarId());
            idler.add(s.varisGarId());
            idler.add(s.aracTipiId());
            idler.add(s.hatId());
        });
        Map<Long, String> ad = idler.isEmpty() ? Map.of() : tanimlar.adlar(idler);
        List<YuklemeSonucuDto.Satir> satirlar = adaylar.stream().map(s -> new YuklemeSonucuDto.Satir(s.satirNo(), s.trenNo(), s.tarih(),
                ad.get(s.kalkisGarId()), ad.get(s.varisGarId()), s.kalkis().atZone(SeferServisi.ISTANBUL).toOffsetDateTime(),
                s.varis().atZone(SeferServisi.ISTANBUL).toOffsetDateTime(), ad.get(s.aracTipiId()), ad.get(s.hatId()), durum.get(s.satirNo()), s.parcaNo(), s.parcaSayisi())).toList();
        return new YuklemeSonucuDto(onizle, adaylar.size() + hatalar.size(), yeniler.size(), adaylar.size() - yeniler.size(), hatalar.size(), satirlar,
                hatalar.stream().map(h -> new YuklemeSonucuDto.Hata(h.satirNo(), h.neden(), h.sayfa(), h.trenNo())).toList(), okunan.engelli());
    }

    public byte[] sablon() {
        return SeferExcelOkuyucu.sablon();
    }

    private static Sefer varlik(YuklemeSatiri s) {
        return Sefer.parca(s.trenNo(), s.tarih(), s.kalkisGarId(), s.varisGarId(), s.kalkis(), s.varis(), s.aracTipiId(), s.hatId(), s.km(), s.trenTuru(), s.parcaNo(),
                s.parcaSayisi(), s.trenGunu());
    }

    /** Tren anahtarı (MK-60): parçalıda zincirin ilk günü, parçasızda tarih; bu yüzden gece aşan parça 2, ertesi günün kendi trenini "zaten var" yapmaz. */
    private static LocalDate trenGunu(YuklemeSatiri s) {
        return s.trenGunu() != null ? s.trenGunu() : s.tarih();
    }

    private static String anahtar(YuklemeSatiri s) {
        return anahtar(trenGunu(s), s.trenNo());
    }

    private static String anahtar(LocalDate gun, String trenNo) {
        return gun + "|" + trenNo;
    }
}
