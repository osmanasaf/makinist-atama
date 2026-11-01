package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

import jakarta.persistence.EntityManagerFactory;

/**
 * NFR-M06 / T6.5: gün listesine "gördü" bilgisi eklenince N+1 yok. Sorgu sayısı atama sayısından bağımsız olmalı (10 atama ile 300 atama aynı sayıda SQL), ve 300 atamalı gün
 * listesi hâlâ hızlı. Çalıştırma: {@code mvnw test -Pyavas "-Dtest=GunListesiPerfTest"}.
 */
@Tag("yavas")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class GunListesiPerfTest extends TestVeritabani {

    @Autowired
    EntityManagerFactory emf;

    private void doldur(int adet, String onEk, LocalDate gun) {
        var v = new AtamaVeri(jdbc);
        for (int i = 0; i < adet; i++) {
            long m = v.makinist(String.format("%s%03d", onEk, i));
            kullanicilar.makinistHesabiAc(m, String.format("%s%03d", onEk, i), "Demo Makinist " + onEk + i, Tarayici.PAROLA);
            int dk = (i * 4) % (22 * 60);
            v.atama(v.sefer(onEk + i, gun, LocalTime.of(dk / 60, dk % 60).toString(), LocalTime.of(dk / 60, dk % 60).plusMinutes(30).toString()), m, "ASIL");
        }
    }

    private long sorgu(Tarayici t, LocalDate gun) throws Exception {
        var istatistik = emf.unwrap(SessionFactory.class).getStatistics();
        istatistik.clear();
        t.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk());
        return istatistik.getPrepareStatementCount();
    }

    @Test
    void gunListesi_sorguSayisiAtamaSayisindanBagimsiz() throws Exception {
        LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).plusDays(5);
        Tarayici t = atamaci();
        doldur(10, "A", gun);
        sorgu(t, gun); // ısınma
        long az = sorgu(t, gun);
        assertThat(az).as("istatistik açık olmalı").isGreaterThan(3);

        doldur(290, "B", gun);
        long cok = sorgu(t, gun);

        assertThat(cok).as("10 atamada %s, 300 atamada %s sorgu", az, cok).isEqualTo(az);
        long bas = System.nanoTime();
        sorgu(t, gun);
        assertThat((System.nanoTime() - bas) / 1_000_000).as("300 atamalı gün listesi ms").isLessThan(3000);
    }

    /**
     * OK-50: kimsenin atanmadığı 100 seferli günde "boş sefer nedeni" (UX-13) hesabı, uygun kişiyi sicil sırasında GEÇ bulduğunda da hızlı kalmalı. Eski kod her sefer için listeyi baştan tarıyordu:
     * hat yetkisiz 3.000 makinist sicil sırasında öne düşünce 100 × 3.000 kural değerlendirmesi (QA: 8,1 sn). Çıktı değişmez: uygun kişi varsa neden yok.
     */
    @Test
    void gunListesi_uygunKisiSicilSirasindaGecBulunsa_bosSeferNedeni_tekSaniyeAlti() throws Exception {
        LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).plusDays(5);
        var v = new AtamaVeri(jdbc);
        for (int i = 0; i < 3000; i++) {
            v.makinist(String.format("A%04d", i), false, "AKTIF"); // hat yetkisi yok: ENGEL
        }
        for (int i = 0; i < 3; i++) {
            v.makinist("Z00" + i); // yetkili, sicil sırasında en sonda
        }
        for (int i = 0; i < 100; i++) {
            int dk = (i * 13) % (22 * 60);
            v.sefer("S" + i, gun, LocalTime.of(dk / 60, dk % 60).toString(), LocalTime.of(dk / 60, dk % 60).plusMinutes(30).toString());
        }
        Tarayici t = atamaci();
        t.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()); // ısınma

        long bas = System.nanoTime();
        t.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar.length()").value(100)).andExpect(jsonPath("$.satirlar[?(@.bosNeden != null)]").isEmpty());
        assertThat((System.nanoTime() - bas) / 1_000_000).as("3.003 makinistli, 100 atanmamış seferli gün listesi ms").isLessThan(1000);
    }

    /** F2b: her sefere bir de YOLCU satırı eklenince de sorgu sayısı atama sayısından bağımsızdır (yolculuk ayarı yükleme başına en çok bir kez okunur). */
    @Test
    void gunListesi_yolcuSatirlariylaDa_sorguSayisiAtamaSayisindanBagimsiz() throws Exception {
        LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).plusDays(5);
        Tarayici t = atamaci();
        yolcuIleDoldur(10, "A", gun);
        sorgu(t, gun); // ısınma
        long az = sorgu(t, gun);

        yolcuIleDoldur(290, "B", gun);
        long cok = sorgu(t, gun);

        assertThat(cok).as("20 satırda %s, 600 satırda %s sorgu", az, cok).isEqualTo(az);
    }

    /** Her sefere bir ASIL ve bir YOLCU makinist. */
    private void yolcuIleDoldur(int adet, String onEk, LocalDate gun) {
        var v = new AtamaVeri(jdbc);
        for (int i = 0; i < adet; i++) {
            long asil = v.makinist(String.format("%s%03d", onEk, i));
            long yolcu = v.makinist(String.format("%sY%03d", onEk, i));
            int dk = (i * 4) % (22 * 60);
            long sefer = v.sefer(onEk + i, gun, LocalTime.of(dk / 60, dk % 60).toString(), LocalTime.of(dk / 60, dk % 60).plusMinutes(30).toString());
            v.atama(sefer, asil, "ASIL");
            v.atama(sefer, yolcu, "YOLCU");
        }
    }
}
