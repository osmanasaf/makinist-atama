package com.makinistatama.guvenlik;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.makinistatama.kullanici.KullaniciServisi;

/**
 * sunucu oturumu + CSRF çift gönderim çerezi. {@code /api/**} kimlik doğrulamalı, {@code /api/auth/giris} ve
 * sağlık uç noktası açık, geri kalan (SPA statik dosyaları) açık. Yetki metot düzeyinde {@code @SadeceYonetici}.
 * 401/403 gövdeleri {@code HataYonetimi}'nden gelir (tek hata biçimi).
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    static PasswordEncoder sifreleyici() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityContextRepository oturumBaglami() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    UserDetailsService kullaniciDetaylari(KullaniciServisi kullanicilar, Clock saat) {
        return eposta -> kullanicilar.epostaIle(eposta)
                .map(k -> new KullaniciKimligi(k, saat.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı yok"));
    }

    @Bean
    AuthenticationManager kimlikYoneticisi(UserDetailsService kullaniciDetaylari, PasswordEncoder sifreleyici) {
        DaoAuthenticationProvider saglayici = new DaoAuthenticationProvider(kullaniciDetaylari);
        saglayici.setPasswordEncoder(sifreleyici);
        return new ProviderManager(saglayici);
    }

    @Bean
    SecurityFilterChain zincir(HttpSecurity http, SecurityContextRepository oturumBaglami, KullaniciServisi kullanicilar, Clock saat,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver hataCozucu,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cerezGuvenli) {
        CookieCsrfTokenRepository csrfDeposu = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfDeposu.setCookieCustomizer(c -> c.secure(cerezGuvenli)); // SameSite bu depoda uygulanmaz; belirteç başlıkla da doğrulandığı için sorun değil

        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/giris", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/benim/**").hasRole("MAKINIST")
                        .requestMatchers("/api/**").hasAnyRole("ATAMACI", "YONETICI")
                        .anyRequest().permitAll())
                .csrf(c -> c.csrfTokenRepository(csrfDeposu).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(c -> c.securityContextRepository(oturumBaglami))
                .requestCache(c -> c.requestCache(new NullRequestCache())) // anonim yoklamalar oturum açmasın
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((istek, yanit, hata) -> hataCozucu.resolveException(istek, yanit, null, hata))
                        .accessDeniedHandler((istek, yanit, hata) -> hataCozucu.resolveException(istek, yanit, null, hata)))
                .addFilterAfter(new KimlikTazelemeFilter(kullanicilar, saat), SecurityContextHolderFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }
}
