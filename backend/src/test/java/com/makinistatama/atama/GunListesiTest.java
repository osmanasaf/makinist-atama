package com.makinistatama.atama;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** T8.4 ve T8.7: gün listesi (masa + yazdırma ortak ucu) ve izin etkisi. */
class GunListesiTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    /** UX-13: kimsenin temiz olmadığı boş seferde masa nedeni gösterir; aday varsa, atanmışsa ya da geçmişse göstermez. Testlerde şimdi = 10 Eki 10:00. */
    @Test
    void bosSefer_kimseUygunDegilse_nedenGelir_aksiHalde_bos() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        v.makinist("Y001", false, "AKTIF"); // hat yetkisi yok
        v.makinist("Y002", false, "AKTIF");
        v.sefer("YETKISIZ", GUN, "08:00", "12:00");
        v.sefer("GECMIS", LocalDate.of(2026, 10, 10), "06:00", "09:00");
        Tarayici t = atamaci();

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].bosNeden").value("0/2 uygun: hat yetkisi 2"));
        t.get("/api/atama/gun?tarih=2026-10-10").andExpect(jsonPath("$.satirlar[0].bosNeden").doesNotExist());

        v.makinist("D001"); // yetkili biri var: sefer yalnız atanmamış, "neden" yok
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].bosNeden").doesNotExist());
    }


    /**
     * OK-50 (MRU sırası): uygun makinist sicil sırasında sonda iken de neden yalnız kimsenin uygun olmadığı seferde gelir ve özet liste sırasına göre aynıdır. A: Z001 uygun (neden yok);
     * B: Z001 zaten bu seferde YARDIMCI, Y001 hat yetkisiz (neden: çakışma + hat yetkisi, eski taramayla aynı metin); C: Z001 yine uygun.
     */
    @Test
    void bosSefer_uygunKisiSonda_nedenYalnizKimseUygunDegilse() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        v.makinist("Y001", false, "AKTIF"); // hat yetkisi yok, sicil sırasında önce
        long z = v.makinist("Z001");
        v.seferEv("A", GUN, "08:00", "09:00");
        long b = v.seferEv("B", GUN, "11:00", "12:00");
        v.seferEv("C", GUN, "14:00", "15:00");
        v.atama(b, z, "YARDIMCI");

        atamaci().get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].bosNeden").doesNotExist())
                .andExpect(jsonPath("$.satirlar[1].bosNeden").value("0/2 uygun (yetkili 1, yetkisiz 1): çakışma 1, hat yetkisi 1")).andExpect(jsonPath("$.satirlar[2].bosNeden").doesNotExist());
    }
    @Test
    void yuzSefer_dortAtanmamis_birIptal_birGecikmeli_alanlarDogru() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        for (int i = 0; i < 100; i++) {
            int dk = 4 * 60 + i * 10;
            long s = v.sefer(String.format("T%03d", i), GUN, String.format("%02d:%02d", dk / 60, dk % 60), String.format("%02d:%02d", (dk + 30) / 60, (dk + 30) % 60));
            if (i >= 5) { // T000 iptal, T001-T004 atanmamış, gerisi atanmış (her sefere ayrı makinist; kural kontrolü bu testin konusu değil)
                v.atama(s, v.makinist(String.format("E%03d", i)), "ASIL");
            }
        }
        jdbc.update("UPDATE sefer SET durum = 'IPTAL' WHERE tren_no = 'T000'");
        jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 90, varis_gecikme_dk = 90 WHERE tren_no = 'T050'"); // planlanan 12:20 -> yeni 13:50

        Tarayici t = atamaci();
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(status().isOk()).andExpect(jsonPath("$.tarih").value("2026-10-12"))
                .andExpect(jsonPath("$.uretimZamani").value(startsWith("20")))
                .andExpect(jsonPath("$.satirlar", hasSize(100)))
                .andExpect(jsonPath("$.satirlar[?(@.makinistler.length()==0)]", hasSize(5)))
                .andExpect(jsonPath("$.satirlar[?(@.durum=='IPTAL')]", hasSize(1)))
                .andExpect(jsonPath("$.satirlar[?(@.kalkisGecikmeDk==90)]", hasSize(1)))
                .andExpect(jsonPath("$.satirlar[?(@.trenNo=='T050')].planlananKalkis", contains(startsWith("2026-10-12T12:20"))))
                .andExpect(jsonPath("$.satirlar[?(@.trenNo=='T050')].guncelKalkis", contains(startsWith("2026-10-12T13:50"))))
                .andExpect(jsonPath("$.satirlar[0].trenNo").value("T000")).andExpect(jsonPath("$.satirlar[99].trenNo").value("T099"))
                .andExpect(jsonPath("$.satirlar[5].makinistler[0].rol").value("ASIL")).andExpect(jsonPath("$.satirlar[5].makinistler[0].sicil").value("E005"));
        t.get("/api/atama/gun?tarih=2026-10-13").andExpect(jsonPath("$.satirlar", hasSize(0)));
        t.get("/api/atama/gun?tarih=2026-02-31").andExpect(status().isBadRequest());
        t.get("/api/atama/gun").andExpect(status().isBadRequest());
    }

    @Test
    void izinEtkisi_aralikIcindekiAtanmisSeferler() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long icinde = v.sefer("T1", LocalDate.of(2026, 10, 22), "08:00", "10:00");
        long disinda = v.sefer("T2", LocalDate.of(2026, 10, 25), "08:00", "10:00");
        long once = v.sefer("T3", LocalDate.of(2026, 10, 19), "08:00", "10:00");
        v.atama(icinde, m, "ASIL");
        v.atama(disinda, m, "ASIL");
        v.atama(once, m, "ASIL");

        Tarayici t = atamaci();
        t.get("/api/atama/makinist/" + m + "/izin-etkisi?bas=2026-10-20&bit=2026-10-24").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trenNo").value("T1")).andExpect(jsonPath("$[0].kalkisGar").value("Ankara")).andExpect(jsonPath("$[0].rol").value("ASIL"));
        t.get("/api/atama/makinist/" + m + "/izin-etkisi?bas=2026-10-22&bit=2026-10-22").andExpect(jsonPath("$", hasSize(1)));
        t.get("/api/atama/makinist/" + m + "/izin-etkisi?bas=2026-10-24&bit=2026-10-20").andExpect(status().isBadRequest());
        t.get("/api/atama/makinist/999/izin-etkisi?bas=2026-10-20&bit=2026-10-24").andExpect(status().isNotFound());
    }
}
