package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.makinistatama.TestVeritabani;

/** V17 (MK-34): veritabanı kısıtları servis hatasına karşı arka güvencedir (kısmi UNIQUE, CHECK'ler). */
class TakasSemaTest extends TestVeritabani {

    private static final String EKLE = """
            INSERT INTO takas_talebi (tur, durum, kaynak_atama_id, kaynak_makinist_id, kaynak_sefer_id, kaynak_ozet,
                hedef_atama_id, hedef_makinist_id, hedef_sefer_id, hedef_ozet, neden, olusturan_kullanici_id, olusturma, son_kalkis)
            VALUES (?, ?, ?, ?, ?, 'ozet', ?, ?, ?, ?, 'IZIN', 1, now(), now() + interval '2 day')""";

    private int takas(String durum, long kaynakAtama, long kaynakM, long kaynakSefer, long hedefAtama, long hedefM, long hedefSefer) {
        return jdbc.update(EKLE, "TAKAS", durum, kaynakAtama, kaynakM, kaynakSefer, hedefAtama, hedefM, hedefSefer, "ozet");
    }

    private int birak(String durum, long atama, long m, long sefer) {
        return jdbc.update(EKLE, "BIRAK", durum, atama, m, sefer, null, null, null, null);
    }

    @Test
    void kismiUnique_acikTaleplerdeAtamaTekrarlanamaz_kapaliOlanlardaTekrarlanir() {
        assertThat(takas("KARSI_ONAY_BEKLIYOR", 10, 1, 100, 20, 2, 200)).isEqualTo(1);
        assertThatThrownBy(() -> takas("ATAMACI_ONAYI_BEKLIYOR", 10, 1, 100, 30, 3, 300)).as("aynı kaynak atama ikinci açık talepte").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> takas("KARSI_ONAY_BEKLIYOR", 40, 4, 400, 20, 2, 200)).as("aynı hedef atama ikinci açık talepte").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> birak("ATAMACI_ONAYI_BEKLIYOR", 10, 1, 100)).as("bırakma da aynı kaynak atamayı tutamaz").isInstanceOf(DataIntegrityViolationException.class);

        assertThat(takas("REDDEDILDI", 10, 1, 100, 20, 2, 200)).as("kapalı talep UNIQUE'e girmez").isEqualTo(1);
        assertThat(takas("BAYAT", 10, 1, 100, 20, 2, 200)).isEqualTo(1);
        assertThat(birak("ATAMACI_ONAYI_BEKLIYOR", 50, 5, 500)).as("hedefi NULL olan bırakmalar birbirini engellemez").isEqualTo(1);
        assertThat(birak("ATAMACI_ONAYI_BEKLIYOR", 60, 6, 600)).isEqualTo(1);
    }

    @Test
    void checkKisitlari() {
        assertThatThrownBy(() -> takas("ONAY_SAKLI", 10, 1, 100, 20, 2, 200)).as("durum listesi dışı").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> takas("KARSI_ONAY_BEKLIYOR", 10, 1, 100, 20, 2, 100)).as("aynı sefer takası").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> takas("KARSI_ONAY_BEKLIYOR", 10, 1, 100, 20, 1, 200)).as("aynı makinist").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> birak("KARSI_ONAY_BEKLIYOR", 10, 1, 100)).as("bırakmada karşı onay yok").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(EKLE, "TAKAS", "KARSI_ONAY_BEKLIYOR", 10, 1, 100, null, null, null, null)).as("takasta hedef zorunlu")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(EKLE.replace("'IZIN'", "'IPTAL'"), "BIRAK", "ATAMACI_ONAYI_BEKLIYOR", 10, 1, 100, null, null, null, null)).as("neden listesi dışı")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tohumAyarlari() {
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'takas.son_saat'", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT deger FROM uygulama_ayar WHERE anahtar = 'takas.karsi_onay_saat'", Integer.class)).isEqualTo(24);
    }
}
