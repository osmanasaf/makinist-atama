package com.makinistatama.benim;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * İstekte çözülmüş makinist (MK-28): kimlik YALNIZCA oturumdan, {@link BenimKapisi} tarafından bir kez çözülüp istek özniteliğine yazılır. Servis metotları {@link Kisi}
 * ister; makinist kimliği istek yolu/sorgusu/gövdesinden üretilemez. Öznitelik yoksa (kapı atlanmış) 500: güvenli yönde hata.
 */
public final class BenimKimlik {

    static final String OZNITELIK = "benim.kisi";

    /** Oturumdaki hesabın bağlı olduğu makinist (kimlik + hafif kart). */
    public record Kisi(long makinistId, String sicil, String adSoyad) {
    }

    private BenimKimlik() {
    }

    public static Kisi kisi() {
        RequestAttributes istek = RequestContextHolder.getRequestAttributes();
        Object kisi = istek == null ? null : istek.getAttribute(OZNITELIK, RequestAttributes.SCOPE_REQUEST);
        if (kisi instanceof Kisi k) {
            return k;
        }
        throw new IllegalStateException("Makinist kimliği çözülmemiş: BenimKapisi atlanmış");
    }
}
