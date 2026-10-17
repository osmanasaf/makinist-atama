package com.makinistatama.ortak;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "Bugün" ve "şimdi" tek yerden gelir (testte sabit saatle değiştirilebilir). */
@Configuration(proxyBeanMethods = false)
public class ZamanAyari {

    public static final ZoneId BOLGE = ZoneId.of("Europe/Istanbul");

    @Bean
    Clock saat() {
        return Clock.system(BOLGE);
    }
}
