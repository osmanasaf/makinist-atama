package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * T10.2 (MK-5): kırmızı atama SAKLANMAZ, her okumada canlı hesaplanır; her atama yalnız kendisinden önce başlayan görevlerle değerlendirilir
 * (sorumlu = zinciri bozan sonraki atama). Plan örneği: 12 Eki 10:00–18:00 seferi 150 dk gecikir (varış 20:30) -> 13 Eki 06:00 ataması kırmızı
 * "Dinlenme 9 s 30 dk < 11 s", 12 Eki ataması kırmızı değil.
 */
class KirmiziAtamaTest extends TestVeritabani {

    static final LocalDate D12 = LocalDate.of(2026, 10, 12);
    static final LocalDate D13 = LocalDate.of(2026, 10, 13);

    AtamaVeri v;
    long m;
    long a; // 12 Eki 10:00-18:00, Ankara -> Ankara (evde biter)
    long b; // 13 Eki 06:00-10:00

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        a = v.sefer("A", D12, "10:00", "18:00", v.ankara, v.ankara);
        b = v.sefer("B", D13, "06:00", "10:00", v.ankara, v.ankara);
        v.atama(a, m, "ASIL");
        v.atama(b, m, "ASIL");
    }

    private void gecikme(long sefer, int dk) throws Exception {
        atamaci().post("/api/sefer/" + sefer + "/gecikme", "{\"kalkisGecikmeDk\":" + dk + ",\"neden\":\"GECIKME\"}").andExpect(status().isOk());
    }

    @Test
    void gecikmedenOnce_kimseKirmiziDegil() throws Exception {
        Tarayici t = atamaci();
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].kirmizi").value(false)).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D13).andExpect(jsonPath("$.kirmiziSefer").value(0)).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void ilkSeferGecikir_sonrakiAtamaKirmizi_ilkSeferKirmiziDegil() throws Exception {
        gecikme(a, 150); // varış 20:30 -> B'ye 9 s 30 dk dinlenme

        Tarayici t = atamaci();
        t.get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[0].kirmizi").value(false))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false)).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler", empty()));
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value(containsString("Dinlenme 9 s 30 dk < 11 s")));
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D13).andExpect(jsonPath("$.kirmiziSefer").value(1)).andExpect(jsonPath("$.kirmiziAtama").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isEqualTo(2); // hiçbir şey saklanmadı / silinmedi
    }

    @Test
    void iptalSefer_atamasiKirmizi_makinistBosta() throws Exception {
        atamaci().post("/api/sefer/" + a + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());

        atamaci().get("/api/atama/gun?tarih=" + D12).andExpect(jsonPath("$.satirlar[0].durum").value("IPTAL")).andExpect(jsonPath("$.satirlar[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value("İPTAL – makinist boşta"));
        atamaci().get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D12).andExpect(jsonPath("$.kirmiziSefer").value(1));
    }

    @Test
    void canliHesap_izinBelgeVeParametreDegisinceKirmiziDegisir() throws Exception {
        Tarayici t = atamaci();
        // onaylı izin B gününe eklenince kırmızı olur
        t.post("/api/makinist/" + m + "/izin", "{\"baslangic\":\"2026-10-13\",\"bitis\":\"2026-10-13\",\"tur\":\"YILLIK\"}").andExpect(status().isCreated());
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value(containsString("Onaylı izin")));
        jdbc.update("DELETE FROM izin");
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));

        // belge tarihi geçmişe çekilince kırmızı, düzeltilince geri döner
        jdbc.update("UPDATE makinist SET ehliyet_bitis = '2026-10-12' WHERE id = ?", m);
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value(containsString("Ehliyet")));
        jdbc.update("UPDATE makinist SET ehliyet_bitis = '2030-01-01' WHERE id = ?", m);

        // dinlenme eşiği yükselince ve inince
        gecikme(a, 150);
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(true));
        yonetici().put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"8\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        t.get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false));
    }

    @Test
    void uyariDuzeyi_kirmiziDegil_ama_uyarilarda_gorunur() throws Exception {
        jdbc.update("UPDATE sefer SET varis_gar_id = ? WHERE id = ?", v.eskisehir, a); // A Eskişehir'de biter, B Ankara'dan kalkar: konum uyuşmazlığı (K-20 UYARI)

        atamaci().get("/api/atama/gun?tarih=" + D13).andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmizi").value(false))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].uyarilar", hasSize(1)))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].uyarilar[0]").value(containsString("Eskişehir")));
    }

    @Test
    void gecmisTarihliAtama_k22_kirmizi_ya_da_uyari_sayilmaz() throws Exception {
        long gecmis = v.sefer("G", LocalDate.of(2026, 10, 9), "08:00", "10:00");
        v.atama(gecmis, m, "ASIL");

        atamaci().get("/api/atama/gun?tarih=2026-10-09").andExpect(jsonPath("$.satirlar[0].kirmizi").value(false))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].uyarilar", empty()));
    }

    @Test
    void kirmiziSayisi_dogrulama() throws Exception {
        atamaci().get("/api/atama/kirmizi-sayisi?bas=2026-10-13&bit=2026-10-12").andExpect(status().isBadRequest());
        atamaci().get("/api/atama/kirmizi-sayisi?bas=2026-01-01&bit=2026-12-31").andExpect(status().isBadRequest());
        atamaci().get("/api/atama/kirmizi-sayisi?bas=2026-10-13").andExpect(status().isBadRequest());
    }

    /** B-2: pano derin bağlantısı için aralıktaki EN ERKEN kırmızı sefer günü; kırmızı yoksa null (anahtar yanıtta var). */
    @Test
    void kirmiziSayisi_ilkKirmiziGun_enErkenKirmiziGun_yoksaNull() throws Exception {
        long m2 = v.makinist("D002");
        long a2 = v.sefer("A2", D13, "10:00", "18:00", v.ankara, v.ankara);
        long c2 = v.sefer("C2", LocalDate.of(2026, 10, 14), "06:00", "10:00", v.ankara, v.ankara);
        v.atama(a2, m2, "ASIL");
        v.atama(c2, m2, "ASIL");
        Tarayici t = atamaci();
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=" + D13).andExpect(jsonPath("$.kirmiziSefer").value(0)).andExpect(jsonPath("$.ilkKirmiziGun").value(org.hamcrest.Matchers.nullValue()));

        gecikme(a, 150); // D13 (m) kırmızı
        gecikme(a2, 150); // D14 (m2) kırmızı
        t.get("/api/atama/kirmizi-sayisi?bas=" + D12 + "&bit=2026-10-14").andExpect(jsonPath("$.kirmiziSefer").value(2)).andExpect(jsonPath("$.ilkKirmiziGun").value("2026-10-13"));
        t.get("/api/atama/kirmizi-sayisi?bas=2026-10-14&bit=2026-10-14").andExpect(jsonPath("$.ilkKirmiziGun").value("2026-10-14"));
    }
}
