package com.makinistatama.atama;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/**
 * "Yolculukla ata" intikal önerisi (MK-110, FR-07): gar süre tablosu tanımlıysa TEK serbest intikal; saatler sunucuda (varış = hedef başlangıcı - {@code yolculuk.aktarma_dk},
 * kalkış = varış - süre). Tablo yok / konum zaten hedef garı / geçmiş saat / kural engeli = öneri yok. Seferli öneriler etkilenmez.
 */
class YolculukIntikalOneriTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long m;
    long hedef;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001"); // bağlı gar Ankara
        hedef = v.sefer("H", GUN, "09:00", "10:30", v.eskisehir, v.ankara); // Eskişehir -> Ankara
        t = atamaci();
    }

    private void sure(int dakika) throws Exception {
        yonetici().put("/api/tanimlar/intikal-sure", "{\"garAId\":" + v.ankara + ",\"garBId\":" + v.eskisehir + ",\"dakika\":" + dakika + "}").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions oner() throws Exception {
        return t.get("/api/atama/sefer/" + hedef + "/yolculuk-oner?makinistId=" + m).andExpect(status().isOk());
    }

    @Test
    void tabloYoksaOneriYok() throws Exception {
        oner().andExpect(jsonPath("$.intikal").value(nullValue())).andExpect(jsonPath("$.oneriler", hasSize(0)));
    }

    @Test
    void tabloVarsa_saatlerSunucudaHesaplanir() throws Exception {
        sure(90);

        oner().andExpect(jsonPath("$.intikal.kalkisGar").value("Ankara")).andExpect(jsonPath("$.intikal.varisGar").value("Eskişehir"))
                .andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:15:00+03:00")).andExpect(jsonPath("$.intikal.varis").value("2026-10-14T08:45:00+03:00"))
                .andExpect(jsonPath("$.intikal.sureDk").value(90)).andExpect(jsonPath("$.intikal.aktarmaDk").value(15)).andExpect(jsonPath("$.intikal.uyarilar", hasSize(0)));
    }

    @Test
    void aktarmaAyariVarisiKaydirir() throws Exception {
        sure(90);
        yonetici().put("/api/ayar/yolculuk.aktarma_dk", "{\"deger\":30,\"neden\":\"deneme\"}").andExpect(status().isOk());

        oner().andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:00:00+03:00")).andExpect(jsonPath("$.intikal.varis").value("2026-10-14T08:30:00+03:00"))
                .andExpect(jsonPath("$.intikal.aktarmaDk").value(30));
    }

    @Test
    void oncekiGorev0710BitinceVerilir_0720BitinceKuralEngeliOneriYok() throws Exception {
        sure(90);
        long once = v.seferEv("P", GUN, "05:00", "07:10"); // Ankara'da biter; intikal 07:15'te kalkar
        v.atama(once, m, "ASIL");
        oner().andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:15:00+03:00"));

        jdbc.update("UPDATE sefer SET planlanan_varis = planlanan_varis + interval '10 minutes' WHERE id = ?", once); // 07:20: intikalla örtüşür (K-10)
        oner().andExpect(jsonPath("$.intikal").value(nullValue()));
    }

    @Test
    void konumZatenHedefGarindaysaOneriYok() throws Exception {
        sure(90);
        long once = v.sefer("P", GUN, "05:00", "07:10"); // Ankara -> Eskişehir: makinist Eskişehir'de
        v.atama(once, m, "ASIL");

        oner().andExpect(jsonPath("$.intikal").value(nullValue()));
    }

    @Test
    void kalkisGecmisteyseOneriYok() throws Exception {
        sure(90);
        long gecmis = v.sefer("G", LocalDate.of(2026, 10, 10), "11:00", "12:30", v.eskisehir, v.ankara); // simdi 10:00; intikal 09:15'te kalkmış olurdu

        t.get("/api/atama/sefer/" + gecmis + "/yolculuk-oner?makinistId=" + m).andExpect(status().isOk()).andExpect(jsonPath("$.intikal").value(nullValue()));
    }

    @Test
    void kondukturdeIntikalOnerisiYok() throws Exception {
        sure(90);
        long k = v.konduktor("K001");

        t.get("/api/atama/sefer/" + hedef + "/yolculuk-oner?makinistId=" + k).andExpect(status().isOk()).andExpect(jsonPath("$.intikal").value(nullValue()));
    }

    @Test
    void izinGunuOneriYok_K09() throws Exception {
        sure(90);
        jdbc.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (?, ?, ?, 'YILLIK', 'ONAY', 'test')", m, GUN, GUN);

        oner().andExpect(jsonPath("$.intikal").value(nullValue()));
    }

    @Test
    void seferliOnerilerEtkilenmez_ikisiBirlikte() throws Exception {
        sure(90);
        v.sefer("E1", GUN, "06:00", "07:30"); // Ankara -> Eskişehir: seferli yolculuk (konum Ankara)

        oner().andExpect(jsonPath("$.oneriler", hasSize(1))).andExpect(jsonPath("$.oneriler[0].trenNo").value("E1")).andExpect(jsonPath("$.intikal.kalkis").value("2026-10-14T07:15:00+03:00"));
    }
}
