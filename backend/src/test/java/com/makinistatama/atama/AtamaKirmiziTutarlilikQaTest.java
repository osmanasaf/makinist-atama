package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * QA (Faz 1 son kapı) BUG-14, MK-5 değişmezi: sunucunun ENGEL'siz kabul ettiği (aday listesi + POST) atama, hemen ardından gün listesinde kırmızı olmamalı.
 * Eskiden yazma anı değerlendirmesi makinistin TÜM komşu görevlerini, canlı kırmızı hesabı yalnız kendisinden ÖNCE başlayanları kullanıyor, nöbet (gece/gündüz
 * sınıfı) sonraki görevle birleşince ikisi farklı hüküm veriyordu.
 * Senaryo: M'nin 11 Eki (Pazar) 22:00–03:00 gece görevi var (önceki hafta = gece haftası). 13 Eki 07:55–13:10 görevi atanmış. Şimdi 13 Eki 05:00–06:47
 * (gece görevi: süresinin %56'sı 20–06 penceresinde) seferi M'ye veriliyor; 68 dk ara (< nobet.ayirma_saat 4 s) ile iki sefer tek nöbet = gündüz nöbeti
 * olduğundan K-18 yok; canlı hesap da artık aynı nöbeti (sonraki görevi) görür.
 */
class AtamaKirmiziTutarlilikQaTest extends TestVeritabani {

    static final LocalDate D11 = LocalDate.of(2026, 10, 11);
    static final LocalDate D13 = LocalDate.of(2026, 10, 13);

    AtamaVeri v;
    long m;
    long erken; // 13 Eki 05:00-06:47 (atanacak)
    long gunduz; // 13 Eki 07:55-13:10 (önceden atanmış)

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        long gece = v.seferEv("G", D11, "22:00", "03:00");
        gunduz = v.seferEv("B", D13, "07:55", "13:10");
        erken = v.seferEv("A", D13, "05:00", "06:47");
        v.atama(gece, m, "ASIL");
        v.atama(gunduz, m, "ASIL");
    }

    @Test
    void kabulEdilenAtama_hemenKirmiziOlmaz_BUG14() throws Exception {
        Tarayici t = atamaci();
        t.get("/api/atama/sefer/" + erken + "/adaylar").andExpect(status().isOk()).andExpect(jsonPath("$[?(@.makinistId==" + m + ")].uygun").value(true));
        t.post("/api/atama/sefer/" + erken, AtamaVeri.ataGovdesi(m)).andExpect(status().isCreated()); // sunucu ENGEL görmedi, yazdı

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ?", Integer.class, erken, m)).isEqualTo(1);
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[?(@.seferId==" + erken + ")].makinistler[0].kirmizi").value(false))
                .andExpect(jsonPath("$.satirlar[?(@.seferId==" + gunduz + ")].makinistler[0].kirmizi").value(false));
        t.get("/api/atama/kirmizi-sayisi?bas=" + D11 + "&bit=" + D13).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }
}
