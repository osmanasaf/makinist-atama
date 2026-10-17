package com.makinistatama.parametre;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Uygulama ayarları (MK-29): makinist görünümü eşikleri gibi kural dışı tamsayılar. Okuma {@link #tamsayi(String)}; yazma YONETICI, neden zorunlu,
 * alt/üst sınır doğrulanır ve eski -> yeni {@code PARAMETRE_DEGISTI} kaydına düşer. ponytail: yalnız tamsayı; ikinci tür gerekirse sütun eklenir.
 */
@Service
@Transactional(readOnly = true)
public class AyarServisi {

    private final UygulamaAyarRepository ayarlar;
    private final DegisiklikServisi degisiklik;

    AyarServisi(UygulamaAyarRepository ayarlar, DegisiklikServisi degisiklik) {
        this.ayarlar = ayarlar;
        this.degisiklik = degisiklik;
    }

    /** Ayarın güncel değeri; tohumlanmamış anahtar kod hatasıdır (500 değil açık mesaj). */
    public int tamsayi(String anahtar) {
        return ayarlar.findById(anahtar).map(UygulamaAyar::deger)
                .orElseThrow(() -> new IllegalStateException("Uygulama ayarı tohumlanmamış: " + anahtar));
    }

    /** Ayarlar ekranı için tüm uygulama ayarları (grup, sıra). */
    public List<AyarDto> liste() {
        return ayarlar.findAllByOrderByGrupAscSiraAsc().stream().map(AyarDto::of).toList();
    }

    @Transactional
    public AyarDto degerDegistir(String anahtar, AyarGuncellemeIstegi istek) {
        UygulamaAyar a = ayarlar.findById(anahtar).orElseThrow(() -> UygulamaHatasi.bulunamadi("Ayar bulunamadı."));
        int yeni = istek.deger();
        if (yeni < a.alt() || yeni > a.ust()) {
            throw UygulamaHatasi.dogrulama("deger", "Değer " + a.alt() + " ile " + a.ust() + " arasında olmalı.");
        }
        int eski = a.deger();
        if (eski != yeni) {
            a.degerYaz(yeni);
            ayarlar.flush();
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.PARAMETRE_DEGISTI, "AYAR", null).degisim(eski, yeni)
                    .neden(Neden.DIGER, istek.neden().strip()).ayrinti("{\"anahtar\":\"" + anahtar + "\"}"));
        }
        return AyarDto.of(a);
    }
}
