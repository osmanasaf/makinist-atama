package com.makinistatama.benim;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.makinistatama.guvenlik.KullaniciKimligi;
import com.makinistatama.makinist.MakinistKarti;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /api/benim/**} tek geçidi (MK-28, {@code YetkiOnKontrol}'dan SONRA): oturumdaki hesabın makinist kartını çözer, durum kapısını uygular ve çözülen {@link BenimKimlik.Kisi}'yi
 * istek özniteliğine yazar. PASIF makinist: her uç 403 {@code HESAP_KAPALI}; ASKIDA: yalnız okuma (GET/HEAD) serbest, yazma 403 {@code HESAP_ASKIDA}. Kapı interceptor
 * olduğu için her yeni {@code benim} ucu otomatik kapsanır (controller satırında unutulamaz).
 */
@Component
class BenimKapisi implements HandlerInterceptor, WebMvcConfigurer {

    private final MakinistServisi makinistler;

    BenimKapisi(MakinistServisi makinistler) {
        this.makinistler = makinistler;
    }

    @Override
    public void addInterceptors(InterceptorRegistry kayit) {
        kayit.addInterceptor(this).addPathPatterns("/api/benim/**").order(10);
    }

    @Override
    public boolean preHandle(HttpServletRequest istek, HttpServletResponse yanit, Object isleyici) {
        Authentication k = SecurityContextHolder.getContext().getAuthentication();
        if (k == null || !(k.getPrincipal() instanceof KullaniciKimligi kimlik) || kimlik.makinistId() == null) {
            throw UygulamaHatasi.yasak("HESAP_KAPALI", "Bu hesap bir makinist kartına bağlı değil.");
        }
        MakinistKarti kart = makinistler.kart(kimlik.makinistId()).orElseThrow(() -> UygulamaHatasi.yasak("HESAP_KAPALI", "Makinist kartı bulunamadı. Atamacınıza başvurun."));
        if (kart.durum() == MakinistDurum.PASIF) {
            throw UygulamaHatasi.yasak("HESAP_KAPALI", "Hesabınız kapalı. Atamacınıza başvurun.");
        }
        String yontem = istek.getMethod();
        boolean okuma = "GET".equals(yontem) || "HEAD".equals(yontem) || "OPTIONS".equals(yontem);
        if (kart.durum() == MakinistDurum.ASKIDA && !okuma) {
            throw new UygulamaHatasi(HttpStatus.FORBIDDEN, "HESAP_ASKIDA", "Hesabınız askıda: görevlerinizi görebilirsiniz ama işlem yapamazsınız.", null);
        }
        istek.setAttribute(BenimKimlik.OZNITELIK, new BenimKimlik.Kisi(kart.id(), kart.sicil(), kart.adSoyad()));
        return true;
    }
}
