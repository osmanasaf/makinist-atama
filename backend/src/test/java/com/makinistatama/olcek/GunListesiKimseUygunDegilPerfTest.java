package com.makinistatama.olcek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * QA tur 2 (YENİ-2): OK-50 düzeltmesi yalnız "uygun kişi VAR ama sicil sırasında geç" yolunu hızlandırdı. Hiç kimse uygun değilken (ör. yeni araç tipinde kimsenin bröve'si yok)
 * 100 seferin "boş sefer nedeni" hâlâ seferler × makinist kural değerlendirmesi: gerçek uygulamada 3.075 makinistle 8,7 sn ölçüldü (QA tur 2). Çalıştırma:
 * {@code mvnw test -Pyavas -Dtest=GunListesiKimseUygunDegilPerfTest}. BEKLENEN: KIRMIZI (süre sınırı aşılır) — kök neden {@code GunListesiServisi.bosNedenleri}.
 */
@Tag("yavas")
class GunListesiKimseUygunDegilPerfTest extends TestVeritabani {

    @Test
    void gunListesi_kimseUygunDegilken_100BosSeferNedeni_tekSaniyeAlti() throws Exception {
        LocalDate gun = LocalDate.now(ZamanAyari.BOLGE).plusDays(5);
        var v = new AtamaVeri(jdbc);
        for (int i = 0; i < 3000; i++) {
            v.makinist(String.format("A%04d", i), false, "AKTIF"); // hat yetkisi yok: ENGEL (kimse uygun değil)
        }
        for (int i = 0; i < 100; i++) {
            int dk = (i * 13) % (22 * 60);
            v.sefer("S" + i, gun, LocalTime.of(dk / 60, dk % 60).toString(), LocalTime.of(dk / 60, dk % 60).plusMinutes(30).toString());
        }
        Tarayici t = atamaci();
        t.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()); // ısınma

        long bas = System.nanoTime();
        t.get("/api/atama/gun?tarih=" + gun).andExpect(status().isOk()).andExpect(jsonPath("$.satirlar.length()").value(100)).andExpect(jsonPath("$.satirlar[?(@.bosNeden != null)]", org.hamcrest.Matchers.hasSize(100)));
        assertThat((System.nanoTime() - bas) / 1_000_000).as("3.000 makinist, kimse uygun değil, 100 boş sefer: gün listesi ms").isLessThan(1000);
    }
}
