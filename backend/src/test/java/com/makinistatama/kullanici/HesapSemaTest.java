package com.makinistatama.kullanici;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.TestVeritabani;

/** V14 (MK-26): veritabanı kısıtları servis hatasına karşı arka güvencedir. */
class HesapSemaTest extends TestVeritabani {

    private static final String EKLE = "INSERT INTO kullanici (eposta, ad_soyad, parola_karma, rol, makinist_id, parola_degistirmeli) VALUES (?, 'Ad', 'karma', ?, ?, ?)";

    @Test
    void kisitlar() {
        assertThatThrownBy(() -> jdbc.update(EKLE, "d001", "MAKINIST", null, false)).as("MAKINIST makinist_id'siz").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(EKLE, "ata@ornek.test", "ATAMACI", 5L, false)).as("ATAMACI makinist_id'li").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(EKLE, "ata@ornek.test", "ATAMACI", null, true)).as("personelde parola bayrağı").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(EKLE, "d@001", "MAKINIST", 7L, false)).as("@'li sicil giriş adı").isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.update(EKLE, "d001", "MAKINIST", 5L, true)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(EKLE, "d002", "MAKINIST", 5L, true)).as("ikinci hesap aynı makinist").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(EKLE, "ata@ornek.test", "ATAMACI", null, false)).isEqualTo(1);
    }
}
