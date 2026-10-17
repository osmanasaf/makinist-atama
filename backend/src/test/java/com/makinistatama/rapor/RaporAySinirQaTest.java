package com.makinistatama.rapor;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * QA (Faz 1 son kapı): puantaj ve adalet raporunun AY SINIRI davranışı, gerçek PostgreSQL + servis zinciriyle.
 * (1) Ay başında hafta önceki aya taşıyorsa (28 Eyl Pzt – 4 Eki Paz) fazla mesai tüm haftadan hesaplanıp Pazar'ın ayı olan Ekim'e yazılır; Eylül'e yazılmaz.
 * (2) Ay sonunu aşan gece seferi puantajda ve adalet raporunda aynı şekilde iki aya bölünür (OK-3); sefer sayısı ve km planlanan günün ayına yazılır.
 * Görevler SQL ile yazılır (K-13/K-14 atama kuralları bu testin konusu değil).
 */
class RaporAySinirQaTest extends TestVeritabani {

    AtamaVeri v;
    long makinist;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        makinist = v.makinist("D001");
        LocalDate pazartesi = LocalDate.of(2026, 9, 28);
        for (int i = 0; i < 5; i++) { // 28 Eyl .. 2 Eki, 5 x 10 saat = 50 saat
            v.atama(v.sefer("S" + i, pazartesi.plusDays(i), "06:00", "16:00"), makinist, "ASIL");
        }
        v.atama(v.seferEv("GECE", LocalDate.of(2026, 10, 31), "22:00", "03:00"), makinist, "ASIL"); // 31 Eki 22:00 -> 1 Kas 03:00 (5 s)
        t = atamaci();
    }

    @Test
    void puantaj_ayBasiHaftasi_fazlaMesaiPazarinAyinaYazilir_ekimde5Saat_eylulde0() throws Exception {
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + makinist).andExpect(status().isOk())
                .andExpect(jsonPath("$.satirlar[0].fazlaMesaiSaat").value(5.0)) // 28 Eyl–4 Eki haftası: 50 − 45
                .andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(22.0)); // Ekim payı: 1–2 Eki 20 s + 31 Eki gece 22:00–24:00 2 s
        t.get("/api/rapor/puantaj?ay=2026-09&makinist=" + makinist).andExpect(jsonPath("$.satirlar[0].fazlaMesaiSaat").value(0.0))
                .andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(30.0)); // 28–30 Eyl: 3 x 10 s
    }

    @Test
    void ayiAsanGeceSeferi_puantajdaVeAdaletteAyniSekildeGeceYarisindanBolunur() throws Exception {
        // OK-3: saat hangi ayda geçtiyse o aya; vardiya tek gündür (planlanan gün Ekim), sefer sayısı ve km planlanan günün ayına
        t.get("/api/rapor/puantaj?ay=2026-11&makinist=" + makinist).andExpect(jsonPath("$.satirlar[0].fiiliSaat").value(3.0)) // 1 Kas 00:00–03:00
                .andExpect(jsonPath("$.satirlar[0].geceSaat").value(3.0)).andExpect(jsonPath("$.satirlar[0].calisilanGun").value(0)).andExpect(jsonPath("$.satirlar[0].km").value(0.0));
        t.get("/api/rapor/puantaj?ay=2026-10&makinist=" + makinist).andExpect(jsonPath("$.satirlar[0].km").value(750.0)); // km planlanan günün ayına: 1–2 Eki + 31 Eki = 3 x 250 (Eylül seferleri Eylül'e)
        t.get("/api/rapor/adalet?ay=2026-11").andExpect(jsonPath("$.satirlar[0].gorevSaat").value(3.0)).andExpect(jsonPath("$.satirlar[0].geceSaat").value(3.0))
                .andExpect(jsonPath("$.satirlar[0].seferSayisi").value(0)).andExpect(jsonPath("$.satirlar[0].sifirSaat").value(false));
        t.get("/api/rapor/adalet?ay=2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.satirlar[0].seferSayisi").value(3)) // 1–2 Eki + 31 Eki
                .andExpect(jsonPath("$.satirlar[0].gorevSaat").value(22.0)) // 10 + 10 + 2 (31 Eki 22:00–24:00); puantaj Ekim fiili ile aynı
                .andExpect(jsonPath("$.satirlar[0].geceSaat").value(4.0)); // 31 Eki 22:00–24:00 (2 s) + 1–2 Eki 06:00–07:00 (2 s)
    }
}
