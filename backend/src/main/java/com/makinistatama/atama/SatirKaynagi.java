package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.ortak.ZamanAyari;

/**
 * Kural motorunun satır kaynağı (MK-105): {@code atama ⨝ sefer} satırları + serbest intikaller ({@link AtamaSatiri#intikaldan}). TEK birleşim yeri: {@code BaglamYukleyici},
 * {@code AtamaServisi.satirlar} (adalet/puantaj/yedek) ve izin etkisi bunu kullanır; {@code AtamaRepository.aralik}'ı doğrudan çağıran başka üretim kodu yoktur
 * (kaynak taraması testi). İntikal tablosu boşken çıktı {@code aralik} ile birebir aynıdır.
 */
@Component
@Transactional(readOnly = true)
class SatirKaynagi {

    private final AtamaRepository atamalar;
    private final IntikalRepository intikaller;

    SatirKaynagi(AtamaRepository atamalar, IntikalRepository intikaller) {
        this.atamalar = atamalar;
        this.intikaller = intikaller;
    }

    /** Makinistlerin [bas, bit] (planlanan/kalkış günü, iki uç dahil) iptal olmayan satırları; makinist listesi boşsa sorgu yok. */
    List<AtamaSatiri> aralik(Collection<Long> makinistler, LocalDate bas, LocalDate bit) {
        if (makinistler.isEmpty()) {
            return List.of();
        }
        List<AtamaSatiri> satirlar = atamalar.aralik(makinistler, bas, bit);
        List<Intikal> intikalSatirlari = intikaller.satirlar(makinistler, bas.atStartOfDay(ZamanAyari.BOLGE).toInstant(),
                bit.plusDays(1).atStartOfDay(ZamanAyari.BOLGE).toInstant());
        if (intikalSatirlari.isEmpty()) {
            return satirlar;
        }
        List<AtamaSatiri> hepsi = new ArrayList<>(satirlar);
        intikalSatirlari.forEach(i -> hepsi.add(AtamaSatiri.intikaldan(i)));
        return hepsi;
    }
}
