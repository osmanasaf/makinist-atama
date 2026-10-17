package com.makinistatama.guvenlik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.makinistatama.kullanici.KullaniciIstekleri;
import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.kullanici.Rol;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Kullanıcı tablosu boşsa ve {@code MAKINIST_ADMIN_EPOSTA / MAKINIST_ADMIN_SIFRE} tanımlıysa ilk yöneticiyi açar.
 * Repoda parola yoktur; tanımsızsa yalnız uyarı yazılır. Parola loglanmaz.
 */
@Component
class BaslangicYoneticisi implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BaslangicYoneticisi.class);

    private final KullaniciServisi kullanicilar;
    private final String eposta;
    private final String sifre;

    BaslangicYoneticisi(KullaniciServisi kullanicilar,
            @Value("${makinist.baslangic-yonetici.eposta:}") String eposta,
            @Value("${makinist.baslangic-yonetici.sifre:}") String sifre) {
        this.kullanicilar = kullanicilar;
        this.eposta = eposta;
        this.sifre = sifre;
    }

    @Override
    public void run(ApplicationArguments args) {
        tohumla();
    }

    void tohumla() {
        if (kullanicilar.sayi() > 0) {
            return;
        }
        if (eposta.isBlank() || sifre.isBlank()) {
            log.warn("Kullanıcı tablosu boş ve MAKINIST_ADMIN_EPOSTA / MAKINIST_ADMIN_SIFRE tanımlı değil: giriş yapabilecek kullanıcı yok.");
            return;
        }
        try {
            kullanicilar.olustur(new KullaniciIstekleri.Olustur(eposta, "Yönetici", Rol.YONETICI, sifre));
            log.info("İlk yönetici oluşturuldu: {}", eposta);
        } catch (UygulamaHatasi e) {
            log.error("İlk yönetici oluşturulamadı: {}", e.getMessage());
        }
    }
}
