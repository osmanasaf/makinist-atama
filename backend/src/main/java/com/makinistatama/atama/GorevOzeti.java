package com.makinistatama.atama;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;

/**
 * Görevin kısa, okunur özeti (MK-32): "gördüm" kaydı, DEĞİŞTİ tespiti ve "kaldırıldı" kalemi bu TEK fonksiyondan beslenir. Yalnız makinistin bilmesi gerekenleri içerir:
 * rol, kalkış/varış gar ve güncel (gecikmeli) saatleri, durum. Tren no ve km özette YOKTUR (değişimi onayı sıfırlamaz [V8]).
 */
public final class GorevOzeti {

    private static final DateTimeFormatter BICIM = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private GorevOzeti() {
    }

    /** Atama satırı + seferin o anki durumundan özet (MK-32): yazma yolları, takas bayatlık karşılaştırması, gün listesi ve makinist ekranı bu TEK noktayı kullanır. */
    static String hesapla(Atama a, SeferDto s) {
        return hesapla(a.rol(), s.kalkisGar(), s.varisGar(), s.guncelKalkis(), s.guncelVaris(), s.durum());
    }

    public static String hesapla(AtamaRol rol, String kalkisGar, String varisGar, OffsetDateTime guncelKalkis, OffsetDateTime guncelVaris, SeferDurumu durum) {
        String rolAdi = switch (rol) { // YOLCU'yu sessizce "Yardımcı" yazmasın (MK-45)
            case ASIL -> "Asıl";
            case YARDIMCI -> "Yardımcı";
            case KONDUKTOR -> "Kondüktör";
            case YOLCU -> "Yolcu";
        };
        return rolAdi + " · " + kalkisGar + " " + an(guncelKalkis) + " → " + varisGar + " " + an(guncelVaris)
                + (durum == SeferDurumu.IPTAL ? " · İPTAL" : "");
    }

    private static String an(OffsetDateTime t) {
        return t.atZoneSameInstant(ZamanAyari.BOLGE).format(BICIM);
    }
}
