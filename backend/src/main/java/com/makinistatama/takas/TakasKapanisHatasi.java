package com.makinistatama.takas;

import org.springframework.http.HttpStatus;

import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Bayat / süresi dolmuş talebin kapanışı yazıldıktan sonra fırlatılan 409 / 422. İşlemi GERİ ALMAZ ({@code noRollbackFor}: {@code TakasServisi.kabul/onay}, {@code BenimServisi.takasKabul}):
 * kapanış ve denetim kaydı aynı işlemde kalır, ayrı bağlantı gerekmez (W1: {@code REQUIRES_NEW} havuzu kilitliyordu). Yalnız kapanıştan başka hiçbir şey yazılmamışken fırlatılmalıdır.
 */
public final class TakasKapanisHatasi extends UygulamaHatasi {

    TakasKapanisHatasi(HttpStatus durum, String kod, String mesaj) {
        super(durum, kod, mesaj, null);
    }
}
