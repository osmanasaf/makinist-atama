package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.makinistatama.TestVeritabani;

/** Test profilinde hızlı BCrypt (maliyet 4) kullanılır; üretim yapılandırması maliyet 10 olarak kalır. */
class SifreleyiciMaliyetiTest extends TestVeritabani {

    @Autowired
    PasswordEncoder testSifreleyicisi;

    @Test
    void uretimSifreleyicisiMaliyet10_testteHizliMaliyet4() {
        assertThat(SecurityConfig.sifreleyici().encode("x")).startsWith("$2a$10$");
        assertThat(testSifreleyicisi.encode("x")).startsWith("$2a$04$");
    }
}
