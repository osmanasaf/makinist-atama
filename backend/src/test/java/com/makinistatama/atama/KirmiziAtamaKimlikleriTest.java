package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * F5 / MK-177 / T4.2: {@code kirmiziAtamaKimlikleri(bas, bit)}, {@code kirmiziSayisi} ile AYNI hesabın küme dönen kardeşidir (çizelge hücresi kırmızı bayrağı). Sayı eşitliği, iptal sefer
 * (atama kırmızı, "makinist boşta") ve 62 gün sınırı.
 */
class KirmiziAtamaKimlikleriTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Autowired
    GunListesiServisi servis;

    @Test
    void kirmiziSayisiIleAyniKume_cakisanIkiAtama_veIptalSeferAtamasi() {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long birinci = v.atama(v.seferEv("T1", GUN, "08:00", "12:00"), m1, "ASIL");
        long cakisan = v.atama(v.seferEv("T2", GUN, "10:00", "13:00"), m1, "ASIL"); // çakışma: iki atama da kırmızı
        long iptalSefer = v.seferEv("T3", GUN.plusDays(1), "08:00", "12:00");
        long iptalAtama = v.atama(iptalSefer, m2, "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptalSefer);
        v.atama(v.seferEv("T4", GUN.plusDays(2), "08:00", "12:00"), m2, "ASIL"); // temiz

        Set<Long> kimlikler = servis.kirmiziAtamaKimlikleri(GUN, GUN.plusDays(5));

        assertThat(kimlikler).containsExactlyInAnyOrder(birinci, cakisan, iptalAtama);
        assertThat(kimlikler).hasSize(servis.kirmiziSayisi(GUN, GUN.plusDays(5)).kirmiziAtama());
    }

    @Test
    void kirmiziYoksa_bosKume_veSayiSifir() {
        AtamaVeri v = new AtamaVeri(jdbc);
        v.atama(v.seferEv("T1", GUN, "08:00", "12:00"), v.makinist("D001"), "ASIL");
        assertThat(servis.kirmiziAtamaKimlikleri(GUN, GUN)).isEmpty();
        assertThat(servis.kirmiziSayisi(GUN, GUN).kirmiziAtama()).isZero();
    }

    @Test
    void aralik62GunSiniri_63GunVeTersAralik400() {
        assertThat(servis.kirmiziAtamaKimlikleri(GUN, GUN.plusDays(62))).isEmpty(); // iki uç dahil sınır: kirmiziSayisi ile aynı kural
        assertThatThrownBy(() -> servis.kirmiziAtamaKimlikleri(GUN, GUN.plusDays(63))).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> servis.kirmiziAtamaKimlikleri(GUN, GUN.minusDays(1))).isInstanceOf(UygulamaHatasi.class);
    }
}
