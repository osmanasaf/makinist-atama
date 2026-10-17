package com.makinistatama.konaklama;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * F2b MK-43/FR-09: konaklama önerisi: dış garda iki nöbet arası boşluk ≥ {@code konaklama.oneri_min_saat} (8 s) ise; bağlı garda, boşluk kısaysa ya da sonraki nöbet yoksa
 * yok; yolculuk nöbetin parçasıdır; kayıt varsa {@code kayitli}. Otomatik yazılmaz.
 */
class KonaklamaOneriTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long sivas;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        sivas = TestTanimlari.gar(jdbc, "Sivas");
        t = atamaci();
    }

    private void oneriSayisi(int adet) throws Exception {
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=" + m).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(adet)));
    }

    /** 14 Eki 06:00–11:30 Ankara -> Sivas ve {@code sonraki} saatinde Sivas -> Ankara (ertesi gün). */
    private void sivasKonaklamasi(String sonrakiKalkis) {
        v.atama(v.sefer("A", GUN, "06:00", "11:30", v.ankara, sivas), m, "ASIL");
        v.atama(v.sefer("B", GUN.plusDays(1), sonrakiKalkis, "12:00", sivas, v.ankara), m, "ASIL");
    }

    @Test
    void sivas_14Eki_1130_ile_15Eki_0600_arasi_18s30_1Gece() throws Exception {
        sivasKonaklamasi("06:00");

        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=" + m).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].gar").value("Sivas")).andExpect(jsonPath("$[0].sureDk").value(18 * 60 + 30)).andExpect(jsonPath("$[0].gece").value(1))
                .andExpect(jsonPath("$[0].kayitli").value(false)).andExpect(jsonPath("$[0].sicil").value("D001"));
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14").andExpect(jsonPath("$", hasSize(1))); // makinist verilmezse tüm aktifler
        oneriSayisi(1);
        t.get("/api/konaklama/oneri?bas=2026-10-20&bit=2026-10-21&makinistId=" + m).andExpect(jsonPath("$", hasSize(0))); // başka günler
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=" + m).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM konaklama", Integer.class)).isZero(); // otomatik yazılmaz
    }

    @Test
    void kayitVarsa_kayitliTrue_baskaGardakiKayitSayilmaz() throws Exception {
        sivasKonaklamasi("06:00");
        t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + v.eskisehir + ",\"yerTipi\":\"OTEL\",\"giris\":\"2026-10-14T12:00:00+03:00\",\"cikis\":\"2026-10-15T05:00:00+03:00\"}")
                .andExpect(status().isCreated());
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=" + m).andExpect(jsonPath("$[0].kayitli").value(false)); // yanlış gar

        t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + sivas + ",\"yerTipi\":\"YATAKHANE\",\"giris\":\"2026-10-15T00:00:00+03:00\",\"cikis\":\"2026-10-15T05:00:00+03:00\"}")
                .andExpect(status().isConflict()); // eskişehir kaydıyla kesişir (aynı makinist)
        jdbc.update("DELETE FROM konaklama");
        t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + sivas + ",\"yerTipi\":\"YATAKHANE\",\"giris\":\"2026-10-14T13:00:00+03:00\",\"cikis\":\"2026-10-15T05:00:00+03:00\"}")
                .andExpect(status().isCreated());
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=" + m).andExpect(jsonPath("$[0].kayitli").value(true));
    }

    @Test
    void bosluk8SaatinAltindaysa_oneriYok_tamSinirdaVar() throws Exception {
        v.atama(v.sefer("A2", GUN, "12:00", "19:00", v.ankara, sivas), m, "ASIL");
        v.atama(v.sefer("B2", GUN.plusDays(1), "02:30", "05:00", sivas, v.ankara), m, "ASIL"); // 7 s 30 dk (gece penceresinde)
        oneriSayisi(0);

        jdbc.update("UPDATE sefer SET planlanan_kalkis = planlanan_kalkis + interval '30 minutes', planlanan_varis = planlanan_varis + interval '30 minutes' WHERE tren_no = 'B2'"); // 8 s tam
        oneriSayisi(1);
    }

    /** UX-2: dış garda gündüz beklemesi (gece penceresine hiç/yeterince girmeyen boşluk) konaklama önerisi üretmez. */
    @Test
    void gunduzBeklemesi_gecePenceresineGirmiyorsa_oneriYok_gecePenceresineGiriyorsaVar() throws Exception {
        v.atama(v.sefer("G1", GUN, "06:00", "08:00", v.ankara, sivas), m, "ASIL");
        v.atama(v.sefer("G2", GUN, "17:30", "19:30", sivas, v.ankara), m, "ASIL"); // 08:00-17:30 = 9 s 30 dk, tümü gündüz
        oneriSayisi(0);

        jdbc.update("UPDATE sefer SET planlanan_kalkis = planlanan_kalkis + interval '28 hours', planlanan_varis = planlanan_varis + interval '28 hours', tarih = tarih + 1 WHERE tren_no = 'G2'"); // ertesi gün 21:30: gece 20:00-06:00 + 21:30 -> kesişim 10 s
        oneriSayisi(1);
    }

    /** MK-95: gece kesişim eşiği `konaklama.gece_min_dk` ayardır (varsayılan 60): 05:30-14:00 boşluğu gece penceresiyle 30 dk kesişir. */
    @Test
    void geceKesisimEsigi_ayardan_okunur() throws Exception {
        v.atama(v.sefer("K1", GUN, "01:00", "05:30", v.ankara, sivas), m, "ASIL");
        v.atama(v.sefer("K2", GUN, "14:00", "16:00", sivas, v.ankara), m, "ASIL");
        oneriSayisi(0);

        yonetici().put("/api/ayar/konaklama.gece_min_dk", "{\"deger\":30,\"neden\":\"deneme\"}").andExpect(status().isOk());
        oneriSayisi(1);
    }

    @Test
    void bagliGardaBitenNobetVeSonrakiNobetYoksa_oneriYok() throws Exception {
        v.atama(v.seferEv("EV1", GUN, "06:00", "08:00"), m, "ASIL"); // Ankara'da biter
        v.atama(v.seferEv("EV2", GUN.plusDays(1), "08:00", "10:00"), m, "ASIL");
        oneriSayisi(0);

        jdbc.update("DELETE FROM atama");
        v.atama(v.sefer("A", GUN, "06:00", "11:30", v.ankara, sivas), m, "ASIL"); // sonraki nöbet yok
        oneriSayisi(0);
    }

    @Test
    void yolculukNobetinParcasidir_varisGariniBelirler() throws Exception {
        // ASIL Ankara->Ankara 06:00-08:00, YOLCU Ankara->Sivas 08:30-14:00 (aynı nöbet), ertesi gün 08:00'de Sivas'tan kalkan sefer: nöbet Sivas'ta bitti, 18 s boşluk
        v.atama(v.seferEv("EV", GUN, "06:00", "08:00"), m, "ASIL");
        v.atama(v.sefer("YOLCU1", GUN, "08:30", "14:00", v.ankara, sivas), m, "YOLCU");
        v.atama(v.sefer("DONUS", GUN.plusDays(1), "08:00", "13:30", sivas, v.ankara), m, "ASIL");

        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=" + m).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].gar").value("Sivas"))
                .andExpect(jsonPath("$[0].sureDk").value(18 * 60));
    }

    @Test
    void aralikUcu400_62GunuAsmaz_makinist404() throws Exception {
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-13").andExpect(status().isBadRequest());
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-12-30").andExpect(status().isBadRequest());
        t.get("/api/konaklama/oneri?bas=2026-10-14&bit=2026-10-14&makinistId=999").andExpect(status().isNotFound());
    }
}
