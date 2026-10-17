package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * Serbest intikal, makinist tarafı (MK-108, FR-05): yalnız KENDİ intikalleri ({@code GET /api/benim/gorevler} → {@code intikaller}; atamacının notu yok), IPTAL dahil; "gördüm"
 * kendi kaydı ve ufuk içinde, özet bayatsa 409, GORULDU iken idempotent; atamacı içerik değiştirince DEGISTI; "gördüm" atamacının sürüm kilidini bayatlatmaz.
 */
class BenimIntikalTest extends TestVeritabani {

    static final LocalDate BUGUN = LocalDate.now(ZamanAyari.BOLGE);
    static final LocalDate YARIN = BUGUN.plusDays(1);
    static final DateTimeFormatter GM = DateTimeFormatter.ofPattern("dd.MM");

    AtamaVeri v;
    long a;
    long b;
    Tarayici ta;
    Tarayici tb;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        a = v.makinist("D001");
        b = v.makinist("D002");
        ta = makinistGirisli(a, "D001");
        tb = makinistGirisli(b, "D002");
        v.intikal(a, v.ankara, v.eskisehir, YARIN, "07:00", "09:00"); // id 1
        jdbc.update("UPDATE intikal SET notu = 'atamacı iç notu' WHERE id = 1");
        v.intikal(b, v.ankara, v.eskisehir, YARIN, "07:00", "09:00"); // id 2: başkası
        v.intikal(a, v.ankara, v.eskisehir, BUGUN.plusDays(20), "07:00", "09:00"); // id 3: ufuk (7 gün) dışı
    }

    private String ozet() {
        return "İntikal · Ankara " + GM.format(YARIN) + " 07:00 → Eskişehir " + GM.format(YARIN) + " 09:00 · Servis";
    }

    private static String gordum(String ozet) {
        return "{\"ozet\":\"" + ozet + "\"}";
    }

    @Test
    void kendiIntikali_notYok_baskasininkiVeUfukDisiGorunmez() throws Exception {
        String govde = ta.get("/api/benim/gorevler").andExpect(status().isOk()).andExpect(jsonPath("$.intikaller", hasSize(1))).andExpect(jsonPath("$.intikaller[0].id").value(1))
                .andExpect(jsonPath("$.intikaller[0].kalkisGar").value("Ankara")).andExpect(jsonPath("$.intikaller[0].varisGar").value("Eskişehir"))
                .andExpect(jsonPath("$.intikaller[0].aracTuru").value("SERVIS")).andExpect(jsonPath("$.intikaller[0].durum").value("PLANLI"))
                .andExpect(jsonPath("$.intikaller[0].gordu").value("YENI")).andExpect(jsonPath("$.intikaller[0].ozet").value(ozet())).andReturn().getResponse().getContentAsString();
        assertThat(govde).doesNotContain("iç notu").doesNotContain("notu").doesNotContain("makinistId");
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.intikaller", hasSize(1))).andExpect(jsonPath("$.intikaller[0].id").value(2));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler", hasSize(0)));
    }

    @Test
    void gordum_200_idempotent_tekDenetimKaydi() throws Exception {
        ta.post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU")).andExpect(jsonPath("$.gorduZamani").exists());
        ta.post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM' AND varlik_tipi = 'INTIKAL'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'GORDUM' AND varlik_tipi = 'INTIKAL'", String.class)).isEqualTo(ozet());
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.intikaller[0].gordu").value("GORULDU"));
    }

    @Test
    void gordum_bayatOzet409_baskasininVeUfukDisiVeYetkisiz() throws Exception {
        ta.post("/api/benim/intikal/1/gordum", gordum("eski özet")).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("OZET_DEGISTI"))
                .andExpect(jsonPath("$.gorev.ozet").value(ozet()));
        ta.post("/api/benim/intikal/2/gordum", gordum(ozet())).andExpect(status().isNotFound()); // başkasının
        ta.post("/api/benim/intikal/3/gordum", gordum(ozet())).andExpect(status().isNotFound()); // ufuk dışı
        ta.post("/api/benim/intikal/99/gordum", gordum(ozet())).andExpect(status().isNotFound());
        ta.post("/api/benim/intikal/1/gordum", "{}").andExpect(status().isBadRequest());
        atamaci().post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isForbidden());
        new Tarayici(mvc).post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT gordu_ozet FROM intikal WHERE id = 1", String.class)).isNull();
    }

    @Test
    void atamaciDegistirirseDegisti_yenidenGordum_gordumSurumuBayatlatmaz() throws Exception {
        ta.post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isOk());
        Tarayici atamaci = atamaci();
        String yeni = "{\"makinistId\":" + a + ",\"kalkisGarId\":" + v.ankara + ",\"varisGarId\":" + v.eskisehir + ",\"kalkis\":\"" + YARIN + "T08:00:00+03:00\",\"varis\":\"" + YARIN
                + "T10:00:00+03:00\",\"aracTuru\":\"SERVIS\",\"versiyon\":0}";

        atamaci.put("/api/intikal/1", yeni).andExpect(status().isOk()); // makinistin "gördüm"ü sürümü artırmadı: sürüm 0 hâlâ geçerli
        String yeniOzet = "İntikal · Ankara " + GM.format(YARIN) + " 08:00 → Eskişehir " + GM.format(YARIN) + " 10:00 · Servis";
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.intikaller[0].gordu").value("DEGISTI")).andExpect(jsonPath("$.intikaller[0].eskiOzet").value(ozet()))
                .andExpect(jsonPath("$.intikaller[0].ozet").value(yeniOzet));
        atamaci.get("/api/intikal?bas=" + YARIN + "&bit=" + YARIN + "&makinistId=" + a).andExpect(jsonPath("$[0].gordu").value("DEGISTI"));

        ta.post("/api/benim/intikal/1/gordum", gordum(yeniOzet)).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'GORDUM'", Integer.class)).isEqualTo(2);
        atamaci.get("/api/intikal?bas=" + YARIN + "&bit=" + YARIN + "&makinistId=" + a).andExpect(jsonPath("$[0].gordu").value("GORULDU"));
    }

    @Test
    void iptalEdilenIntikalMakinistteIptalEtiketiyle_yenidenGordumIster() throws Exception {
        ta.post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isOk());

        atamaci().delete("/api/intikal/1?neden=IZIN&versiyon=0").andExpect(status().isOk());

        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.intikaller", hasSize(1))).andExpect(jsonPath("$.intikaller[0].durum").value("IPTAL"))
                .andExpect(jsonPath("$.intikaller[0].gordu").value("DEGISTI")).andExpect(jsonPath("$.intikaller[0].ozet").value(ozet() + " · İPTAL"));
        ta.post("/api/benim/intikal/1/gordum", gordum(ozet() + " · İPTAL")).andExpect(status().isOk()).andExpect(jsonPath("$.gordu").value("GORULDU"));
    }

    @Test
    void askidaMakinistOkur_gordumYazmaOlduguIcin403() throws Exception {
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", a);

        ta.get("/api/benim/gorevler").andExpect(status().isOk()).andExpect(jsonPath("$.intikaller", hasSize(1)));
        ta.post("/api/benim/intikal/1/gordum", gordum(ozet())).andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("HESAP_ASKIDA"));
    }
}
