package com.makinistatama.atama;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * İyileştirme anahtarı imzası (F2d tur 2, W4): HMAC-SHA256, süreç başına rastgele sır (yeniden başlatma anahtarları geçersiz kılar: önizleme yenilenir). İmza anahtarı VE aralığı
 * bağlar; böylece istemci rastgele/şişirilmiş öncelik haritasıyla kilit altında pahalı ya da kötü plan yazdıramaz.
 */
@Component
class IyilestirmeImzasi {

    private final byte[] sir = new byte[32];

    IyilestirmeImzasi() {
        new SecureRandom().nextBytes(sir);
    }

    IyilestirmeAnahtari imzala(LocalDate bas, LocalDate bit, int kaydirDk, Map<Long, Integer> oncelik) {
        return new IyilestirmeAnahtari(kaydirDk, oncelik, hesapla(bas, bit, kaydirDk, oncelik));
    }

    boolean dogru(LocalDate bas, LocalDate bit, IyilestirmeAnahtari a) {
        if (a.imza() == null) {
            return false;
        }
        return MessageDigest.isEqual(hesapla(bas, bit, a.kaydirDk(), a.oncelik()).getBytes(StandardCharsets.UTF_8), a.imza().getBytes(StandardCharsets.UTF_8));
    }

    private String hesapla(LocalDate bas, LocalDate bit, int kaydirDk, Map<Long, Integer> oncelik) {
        StringBuilder s = new StringBuilder().append(bas).append('|').append(bit).append('|').append(kaydirDk);
        new TreeMap<>(oncelik).forEach((k, v) -> s.append('|').append(k).append(':').append(v));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sir, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(s.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
