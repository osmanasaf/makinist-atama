package com.makinistatama.degisiklik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.makinistatama.TestVeritabani;

/** NFR-03 / MK-9: kayıt ek-yalnızdır (DB tetikleyicisi); işlem dışı yazım reddedilir; oturum yoksa kim = sistem. */
class DegisiklikDegismezTest extends TestVeritabani {

    @Autowired
    DegisiklikServisi servis;

    @Autowired
    TransactionTemplate islem;

    private void satirEkle() {
        islem.executeWithoutResult(s -> servis.kaydet(DegisiklikServisi.Kayit.of(DegisiklikTuru.TANIM_EKLE, "TANIM", 1L).degisim(null, "GAR:Ankara")));
    }

    @Test
    void insertTamam_oturumsuzKimSistem() {
        satirEkle();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT kullanici_adi FROM degisiklik_kaydi", String.class)).isEqualTo("sistem");
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi", String.class)).isEqualTo("GAR:Ankara");
    }

    @Test
    void update_restrictViolation() {
        satirEkle();

        assertThatThrownBy(() -> jdbc.update("UPDATE degisiklik_kaydi SET yeni = 'x'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("değiştirilemez");
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi", String.class)).isEqualTo("GAR:Ankara");
    }

    @Test
    void delete_restrictViolation() {
        satirEkle();

        assertThatThrownBy(() -> jdbc.update("DELETE FROM degisiklik_kaydi")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isEqualTo(1);
    }

    @Test
    void islemDisindaKaydet_hata() {
        assertThatThrownBy(() -> servis.kaydet(DegisiklikServisi.Kayit.of(DegisiklikTuru.TANIM_EKLE, "TANIM", 1L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void islemGeriAlinirsaKayitDaGider() {
        assertThatThrownBy(() -> islem.executeWithoutResult(s -> {
            servis.kaydet(DegisiklikServisi.Kayit.of(DegisiklikTuru.TANIM_EKLE, "TANIM", 1L));
            throw new IllegalStateException("geri al");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }
}
