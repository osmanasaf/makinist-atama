package com.makinistatama;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Testlerde BCrypt maliyeti 4 (üretim {@code SecurityConfig.sifreleyici()} varsayılan 10 kalır): her test kullanıcıyı yeniden oluşturup giriş yaptığı için
 * maliyet 10 testlerin süresinin büyük kısmını yiyordu. Hash biçimi ({@code $2a$}) aynıdır. Üretim maliyetini
 * {@code guvenlik.SifreleyiciMaliyetiTest} kilitler.
 */
@TestConfiguration
public class HizliSifreleyici {

    @Bean
    @Primary
    PasswordEncoder hizliSifreleyici() {
        return new BCryptPasswordEncoder(4);
    }
}
