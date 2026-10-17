package com.makinistatama.rapor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * T13.4: günlük yedek ihtiyacı (FR-11). Plan örneği: 40 sefer, oran 0,10 -> ihtiyaç 4; AKTIF 50, izinli 4, görevli 40 -> mevcut 6 (yeterli); 3 izin daha -> mevcut 3 < 4
 * "1 eksik"; 5 sefer -> ceil(0,5) = 1 ve {@code yedek.min} 1.
 */
class YedekTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);
    static final LocalDate GUN2 = LocalDate.of(2026, 10, 15);

    AtamaVeri v;
    List<Long> makinistler = new ArrayList<>();
    Tarayici t;

    private void izin(long makinist, LocalDate gun) {
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, ?, ?, 'YILLIK', 'ONAY', 'test')", makinist, gun, gun);
    }

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        for (int i = 1; i <= 50; i++) {
            makinistler.add(v.makinist(String.format("D%03d", i)));
        }
        for (int i = 0; i < 40; i++) { // 40 sefer, her biri ayrı makinist (görevli 40)
            long sefer = v.sefer(String.format("T%02d", i), GUN, "06:00", "08:00");
            v.atama(sefer, makinistler.get(i), "ASIL");
        }
        for (int i = 40; i < 44; i++) { // izinli 4
            izin(makinistler.get(i), GUN);
        }
        for (int i = 0; i < 5; i++) { // ertesi gün 5 sefer
            v.sefer("U" + i, GUN2, "06:00", "08:00");
        }
        t = atamaci();
    }

    @Test
    void kirkSefer_ihtiyac4_mevcut6_yeterli_sonra3IzinDahaIleBirEksik_besSeferMin1() throws Exception {
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.gunler", hasSize(31))).andExpect(jsonPath("$.aktifMakinist").value(50))
                .andExpect(jsonPath("$.oran").value(0.1)).andExpect(jsonPath("$.min").value(1))
                .andExpect(jsonPath("$.gunler[13].tarih").value("2026-10-14")).andExpect(jsonPath("$.gunler[13].planliSefer").value(40))
                .andExpect(jsonPath("$.gunler[13].ihtiyac").value(4)).andExpect(jsonPath("$.gunler[13].izinli").value(4)).andExpect(jsonPath("$.gunler[13].gorevli").value(40))
                .andExpect(jsonPath("$.gunler[13].mevcut").value(6)).andExpect(jsonPath("$.gunler[13].acik").value(0))
                .andExpect(jsonPath("$.gunler[14].planliSefer").value(5)).andExpect(jsonPath("$.gunler[14].ihtiyac").value(1)); // ceil(0,5) = 1

        for (int i = 44; i < 47; i++) { // 3 izin daha: mevcut 3 < 4
            izin(makinistler.get(i), GUN);
        }
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[13].mevcut").value(3)).andExpect(jsonPath("$.gunler[13].acik").value(1))
                .andExpect(jsonPath("$.eksikGunSayisi").value(1)).andExpect(jsonPath("$.toplamAcik").value(1));
    }

    @Test
    void seferYokkenYedekMin_iptalSeferSayilmaz_izinliVeGorevliAyniKisiBirKezDusulur() throws Exception {
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[0].planliSefer").value(0)).andExpect(jsonPath("$.gunler[0].ihtiyac").value(1)) // yedek.min
                .andExpect(jsonPath("$.gunler[0].mevcut").value(50));

        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE tren_no IN ('T00', 'T01')");
        izin(makinistler.get(2), GUN); // zaten görevli (kırmızı durum): mevcut'tan bir kez düşer
        t.get("/api/rapor/yedek?ay=2026-10").andExpect(jsonPath("$.gunler[13].planliSefer").value(38)).andExpect(jsonPath("$.gunler[13].ihtiyac").value(4))
                .andExpect(jsonPath("$.gunler[13].mevcut").value(8)); // iptal seferlerin atamaları sorguda yok: D001, D002 artık görevsiz (6 + 2); D003 izinli ama zaten görevli: bir kez düşer
    }

    @Test
    void ayBozuk400_anonim401() throws Exception {
        t.get("/api/rapor/yedek?ay=bozuk").andExpect(status().isBadRequest());
        new Tarayici(mvc).get("/api/rapor/yedek?ay=2026-10").andExpect(status().isUnauthorized());
    }
}
