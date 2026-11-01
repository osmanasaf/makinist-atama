package com.makinistatama.tanim;

import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Resmi tatil listesi (MK-178): yönetici elle girer; YALNIZ GÖSTERİM (aylık çizelge başlığı). Kural motoru, puantaj, adalet ve kırmızı hesabı bu listeyi OKUMAZ. Her yazım denetim kaydı
 * üretir ({@code TATIL_EKLE}, {@code TATIL_SIL}; "2026-10-29 Cumhuriyet Bayramı").
 */
@Service
@Transactional(readOnly = true)
public class ResmiTatilServisi {

    private static final int ILK_YIL = 2000;
    private static final int SON_YIL = 2100;

    private final ResmiTatilRepository depo;
    private final DegisiklikServisi degisiklik;

    ResmiTatilServisi(ResmiTatilRepository depo, DegisiklikServisi degisiklik) {
        this.depo = depo;
        this.degisiklik = degisiklik;
    }

    /** {@code yil} boşsa hepsi; doluysa o yılın tatilleri (tarih sıralı). */
    public List<ResmiTatilDto> liste(Integer yil) {
        List<ResmiTatil> satirlar = yil == null ? depo.findAllByOrderByTarihAsc()
                : depo.findByTarihBetweenOrderByTarihAsc(LocalDate.of(yilDogrula(yil, "yil"), 1, 1), LocalDate.of(yil, 12, 31));
        return satirlar.stream().map(ResmiTatilServisi::dto).toList();
    }

    /** Aralıkta (iki uç dahil) tatiller; çizelge başlığı için. */
    public List<ResmiTatilDto> aralik(LocalDate bas, LocalDate bit) {
        return depo.findByTarihBetweenOrderByTarihAsc(bas, bit).stream().map(ResmiTatilServisi::dto).toList();
    }

    @Transactional
    public ResmiTatilDto ekle(ResmiTatilIstegi istek) {
        yilDogrula(istek.tarih().getYear(), "tarih");
        String ad = istek.ad().strip();
        if (depo.existsById(istek.tarih())) {
            throw UygulamaHatasi.cakisma("TATIL_VAR", "Bu tarih için resmi tatil zaten tanımlı.");
        }
        try {
            depo.saveAndFlush(new ResmiTatil(istek.tarih(), ad));
        } catch (DataIntegrityViolationException e) { // eşzamanlı aynı tarih
            throw UygulamaHatasi.cakisma("TATIL_VAR", "Bu tarih için resmi tatil zaten tanımlı.");
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TATIL_EKLE, "RESMI_TATIL", null).degisim(null, metin(istek.tarih(), ad)));
        return new ResmiTatilDto(istek.tarih(), ad);
    }

    @Transactional
    public void sil(LocalDate tarih) {
        ResmiTatil t = depo.findById(tarih).orElseThrow(() -> UygulamaHatasi.bulunamadi("Bu tarih için tanımlı resmi tatil yok."));
        depo.delete(t);
        depo.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.TATIL_SIL, "RESMI_TATIL", null).degisim(metin(tarih, t.ad()), null));
    }

    private static int yilDogrula(int yil, String alan) {
        if (yil < ILK_YIL || yil > SON_YIL) {
            throw UygulamaHatasi.dogrulama(alan, "Yıl " + ILK_YIL + " ile " + SON_YIL + " arasında olmalı.");
        }
        return yil;
    }

    private static ResmiTatilDto dto(ResmiTatil t) {
        return new ResmiTatilDto(t.tarih(), t.ad());
    }

    private static String metin(LocalDate tarih, String ad) {
        return tarih + " " + ad;
    }
}
