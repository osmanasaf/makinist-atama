package com.makinistatama.atama;

import java.time.LocalDate;

import com.makinistatama.makinist.PersonelTuru;

import jakarta.validation.constraints.NotNull;

/**
 * Otomatik atama: [bas, bit] (planlanan gün, iki uç dahil). {@code uygula=false} yazmadan önizler, {@code true} aynı sonucu tek işlemde yazar. {@code yolculuk} (F2b, MK-42):
 * garda uygun aday bulunamayan seferde yolcu olarak gidecek aday da aranır (iki satır: önce YOLCU, sonra ASIL); varsayılan KAPALI, JSON'da yoksa kapalı.
 * {@code iyilestir} (F2d, MK-68): daha çok sefer dolduran plan aranır; varsayılan KAPALI. Önizleme aramayı yapar ve {@code anahtar} döndürür; {@code uygula} aynı anahtarla
 * (zorunlu) kilit altında tek tur koşar. {@code yolculuk} ile birlikte kullanılamaz (MK-70).
 * {@code personelTuru} (F3b, MK-88): hangi personel türüne atanacağı; yoksa MAKINIST (bugünkü davranış). KONDUKTOR: yalnız kondüktörler, yalnız yolcu trenleri, rol KONDUKTOR;
 * yolculuk ve iyileştirme ile birlikte kullanılamaz (400).

 */
public record OtomatikAtamaIstegi(
        @NotNull(message = "Başlangıç tarihi gerekli.") LocalDate bas,
        @NotNull(message = "Bitiş tarihi gerekli.") LocalDate bit,
        boolean uygula,
        Boolean yolculuk,
        Boolean iyilestir,
        IyilestirmeAnahtari anahtar,
        PersonelTuru personelTuru) {

    /** Jackson 3 eksik ilkel alanı hata sayar: bayrak sarmalayıcı tutulur, yoksa/boşsa KAPALI (EK-1: istekte alan yoksa bugünkü davranış). */
    public OtomatikAtamaIstegi {
        yolculuk = yolculuk != null && yolculuk;
        iyilestir = iyilestir != null && iyilestir;
        personelTuru = personelTuru == null ? PersonelTuru.MAKINIST : personelTuru;
    }

    public OtomatikAtamaIstegi(LocalDate bas, LocalDate bit, boolean uygula, Boolean yolculuk) {
        this(bas, bit, uygula, yolculuk, false, null, null);
    }

    public OtomatikAtamaIstegi(LocalDate bas, LocalDate bit, boolean uygula) {
        this(bas, bit, uygula, false, false, null, null);
    }
}
