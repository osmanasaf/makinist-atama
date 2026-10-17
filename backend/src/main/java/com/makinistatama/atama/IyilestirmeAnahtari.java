package com.makinistatama.atama;

import java.util.Map;

/**
 * İyileştirmenin en iyi turunun girdisi (F2d, MK-68): önizleme yanıtında döner, {@code uygula} isteğinde geri gelir; sunucu kilit altında yalnız bu anahtarla TEK tur koşar.
 * {@code oncelik}: sefer kimliği -> ağırlık (seyrek; yalnız önceki turlarda boş kalanlar), {@code kaydirDk}: ağırlık başına öne alma dakikası. {@code imza}: sunucunun anahtara
 * (aralıkla birlikte) bastığı HMAC (F2d tur 2, W4): istemci anahtarı uyduramaz ya da değiştiremez, bu yüzden kilit süresi önizlemenin ürettiği anahtarın süresiyle sınırlıdır.
 */
public record IyilestirmeAnahtari(int kaydirDk, Map<Long, Integer> oncelik, String imza) {

    public IyilestirmeAnahtari {
        oncelik = oncelik == null ? Map.of() : oncelik;
    }

    /** İmzasız (yalnız imzalanmadan önce; testler). */
    public IyilestirmeAnahtari(int kaydirDk, Map<Long, Integer> oncelik) {
        this(kaydirDk, oncelik, null);
    }
}
