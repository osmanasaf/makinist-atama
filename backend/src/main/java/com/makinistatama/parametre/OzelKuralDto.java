package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.Vardiya;

/**
 * Özel kural yanıtı. {@code kod}: {@code Ö-NNN} (yalnız görüntü ve ihlal anahtarı; adresleme {@code id} ile). {@code ozet}: tek cümle
 * ("Toplam görev süresi (gün) en fazla 8 s · Hat: Ankara–Sivas"). Kapsam kayıt olarak aynen döner (API gövdesiyle aynı biçim).
 */
public record OzelKuralDto(long id, String kod, String ad, Olcu olcu, String olcuAd, String birim, Pencere pencere, Integer kayanGun, Karsilastirma karsilastirma, BigDecimal esik,
        Kapsam kapsam, Siddet siddet, LocalDate gecerlilikBas, LocalDate gecerlilikBit, String gerekce, String kaynak, long versiyon, String ozet,
        String olusturan, Instant olusturma, Instant guncelleme) {

    static OzelKuralDto of(OzelKuralKaydi k, OzelKural kural, Map<Long, String> adlar) {
        return new OzelKuralDto(k.id(), kural.kod(), k.ad(), k.olcu(), k.olcu().ad(), k.olcu().birim(), k.pencere(), k.kayanGun(), k.karsilastirma(), kural.esik(),
                kural.kapsam(), k.siddet(), k.gecerlilikBas(), k.gecerlilikBit(), k.gerekce(), k.kaynak(), k.versiyon(), ozet(kural, adlar), k.olusturan(), k.olusturma(),
                k.guncelleme());
    }

    static String ozet(OzelKural k, Map<Long, String> adlar) {
        StringBuilder sb = new StringBuilder(k.olcu().ad());
        if (k.pencere() != Pencere.SEFER) {
            sb.append(" (").append(k.pencereMetni()).append(")");
        }
        sb.append(' ').append(k.karsilastirma().ad()).append(' ').append(k.esik().stripTrailingZeros().toPlainString().replace('.', ',')).append(' ').append(k.olcu().birim());
        Kapsam c = k.kapsam();
        List<String> parca = new ArrayList<>();
        ekle(parca, "Hat", c.hatlar(), adlar);
        ekle(parca, "Araç tipi", c.aracTipleri(), adlar);
        ekle(parca, "Kalkış garı", c.kalkisGarlari(), adlar);
        ekle(parca, "Varış garı", c.varisGarlari(), adlar);
        if (!c.trenTurleri().isEmpty()) {
            parca.add("Tren: " + c.trenTurleri().stream().map(t -> t.equals("YUK") ? "Yük" : "Yolcu").collect(Collectors.joining(", ")));
        }
        if (c.vardiya() != Vardiya.HEPSI) {
            parca.add("Vardiya: " + (c.vardiya() == Vardiya.GECE ? "gece" : "gündüz"));
        }
        if (!c.personelTurleri().isEmpty()) {
            parca.add("Personel: " + c.personelTurleri().stream().map(t -> t == com.makinistatama.kural.MakinistBilgisi.Tur.KONDUKTOR ? "Kondüktör" : "Makinist").collect(Collectors.joining(", ")));
        }
        for (String p : parca) {
            sb.append(" · ").append(p);
        }
        return sb.toString();
    }

    private static void ekle(List<String> parca, String etiket, java.util.Set<Long> idler, Map<Long, String> adlar) {
        if (!idler.isEmpty()) {
            parca.add(etiket + ": " + idler.stream().map(i -> adlar.getOrDefault(i, "#" + i)).collect(Collectors.joining(", ")));
        }
    }
}
