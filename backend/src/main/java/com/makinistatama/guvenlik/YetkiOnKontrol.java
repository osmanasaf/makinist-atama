package com.makinistatama.guvenlik;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.makinistatama.kullanici.Rol;
import com.makinistatama.ortak.UygulamaHatasi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Varsayılan-ret yetki kapısı (MK-25, iç kat): her {@code /api/**} {@link HandlerMethod} ucu bir rol beyanı taşımalıdır
 * ({@link Acik}, {@link HerkesGiris}, {@link Personel}, {@link SadeceYonetici}); beyansız uç herkese, YONETICI dahil, 403 {@code UC_BEYANSIZ} verir.
 * Beyan metottaki işarettir, yoksa sınıftakidir. Denetim istek gövdesi çözülmeden ÖNCE yapılır (metot güvenliği argümandan sonra çalışıp
 * yetkisiz kullanıcıya 403 yerine 400 veriyordu); {@code @PreAuthorize} derin savunma olarak durur. Dış kat {@code SecurityConfig}'teki yol çitidir.
 */
@Component
class YetkiOnKontrol implements HandlerInterceptor, WebMvcConfigurer {

    /** İşaret → o uca girebilen roller. {@link Acik} anonimdir (kümesi kullanılmaz). */
    static final Map<Class<? extends Annotation>, Set<Rol>> BEYANLAR = Map.of(
            Acik.class, Set.of(),
            HerkesGiris.class, Set.of(Rol.values()),
            Personel.class, Set.of(Rol.ATAMACI, Rol.YONETICI),
            SadeceYonetici.class, Set.of(Rol.YONETICI),
            SadeceMakinist.class, Set.of(Rol.MAKINIST));

    @Override
    public void addInterceptors(InterceptorRegistry kayit) {
        kayit.addInterceptor(this).addPathPatterns("/api/**").order(0);
    }

    /** Ucun çözülmüş beyanı: metot üzerindeki işaret, yoksa sınıftaki. Testler de aynı çözümü kullanır. */
    static Optional<Class<? extends Annotation>> beyan(HandlerMethod m) {
        return isaretler(m.getMethod()).stream().findFirst().or(() -> isaretler(m.getBeanType()).stream().findFirst());
    }

    /** Bir yerde (metot ya da sınıf) bulunan beyan işaretleri; birden çok olması yanlıştır (testle denetlenir). */
    static List<Class<? extends Annotation>> isaretler(AnnotatedElement oge) {
        return BEYANLAR.keySet().stream().filter(a -> AnnotatedElementUtils.hasAnnotation(oge, a)).toList();
    }

    @Override
    public boolean preHandle(HttpServletRequest istek, HttpServletResponse yanit, Object isleyici) {
        if (!(isleyici instanceof HandlerMethod m)) {
            return true; // statik kaynak vb.: yol çiti (SecurityConfig) uygulanır
        }
        Class<? extends Annotation> beyan = beyan(m)
                .orElseThrow(() -> new UygulamaHatasi(HttpStatus.FORBIDDEN, "UC_BEYANSIZ", "Bu uç için yetki beyanı yok.", null));
        if (beyan == Acik.class) {
            return true;
        }
        Authentication k = SecurityContextHolder.getContext().getAuthentication();
        if (k == null || !(k.getPrincipal() instanceof KullaniciKimligi kimlik) || !BEYANLAR.get(beyan).contains(kimlik.rol())) {
            throw new AccessDeniedException("Rol bu uca yetkili değil");
        }
        if (kimlik.parolaDegistirmeli() && beyan != HerkesGiris.class) { // geçici parolayla yalnız kimlik/çıkış/parola uçları (MK-26)
            throw new UygulamaHatasi(HttpStatus.FORBIDDEN, "PAROLA_DEGISTIR", "Devam etmek için önce parolanızı değiştirmelisiniz.", null);
        }
        return true;
    }
}
