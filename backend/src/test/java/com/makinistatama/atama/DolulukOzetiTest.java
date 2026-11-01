package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;

/**
 * F4 / AK-4.2 / MK-180 / T5.1: doluluk sayacı "BOŞ" tanımını gün listesiyle TEK yerde paylaşır (iptal olmayan sefer, ASIL'ı olmayan = boş). 10 sefer: 1 iptal, 6 ASIL'lı, 3 boş → toplam 9, dolu 6,
 * boş 3. Gün listesiyle aynı sayı.
 */
class DolulukOzetiTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Autowired
    GunListesiServisi servis;

    @Test
    void onSefer_birIptal_altiDolu_ucBos_ve_gunListesiyleAyniSayi() {
        AtamaVeri v = new AtamaVeri(jdbc);
        long[] m = new long[8];
        for (int i = 0; i < m.length; i++) {
            m[i] = v.makinist("D00" + i);
        }
        for (int i = 0; i < 6; i++) { // 6 ASIL'lı sefer (3 farklı güne dağıtık)
            v.atama(v.seferEv("T" + i, GUN.plusDays(i % 3), String.format("%02d:00", 6 + i), String.format("%02d:00", 7 + i)), m[i], "ASIL");
        }
        for (int i = 6; i < 9; i++) { // 3 boş
            v.seferEv("B" + i, GUN.plusDays(i % 3), String.format("%02d:00", 12 + i), String.format("%02d:30", 12 + i));
        }
        long iptal = v.seferEv("I1", GUN, "20:00", "21:00"); // iptal: atamalı olsa da sayılmaz
        v.atama(iptal, m[6], "ASIL");
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE id = ?", iptal);

        DolulukOzeti d = servis.doluluk(GUN, GUN.plusDays(2));

        assertThat(d.toplam()).isEqualTo(9);
        assertThat(d.dolu()).isEqualTo(6);
        assertThat(d.bos()).isEqualTo(3);
        // gün listesiyle aynı tanım: iptal değil ∧ ASIL yok
        int bos = 0;
        int dolu = 0;
        for (int g = 0; g <= 2; g++) {
            for (var s : servis.gunListesi(GUN.plusDays(g)).satirlar()) {
                if (s.durum() == com.makinistatama.sefer.SeferDurumu.IPTAL) {
                    continue;
                }
                if (s.makinistler().stream().anyMatch(x -> x.rol() == AtamaRol.ASIL)) {
                    dolu++;
                } else {
                    bos++;
                }
            }
        }
        assertThat(d.dolu()).isEqualTo(dolu);
        assertThat(d.bos()).isEqualTo(bos);
    }

    @Test
    void yalnizYardimciOlanSefer_bos_sayilir_veriYoksaSifir() {
        AtamaVeri v = new AtamaVeri(jdbc);
        assertThat(servis.doluluk(GUN, GUN)).isEqualTo(new DolulukOzeti(0, 0, 0));
        v.atama(v.seferEv("T1", GUN, "08:00", "10:00"), v.makinist("D001"), "YARDIMCI");
        DolulukOzeti d = servis.doluluk(GUN, GUN);
        assertThat(d.toplam()).isEqualTo(1);
        assertThat(d.dolu()).isZero();
        assertThat(d.bos()).isEqualTo(1);
    }
}
