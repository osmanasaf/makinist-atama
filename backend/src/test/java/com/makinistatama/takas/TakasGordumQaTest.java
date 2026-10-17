package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.GordumYardimci;
import com.makinistatama.Tarayici;

/**
 * QA (F2a-3 son tur): takas / bırakma onayı ile "gördüm" tebliği birlikte. Görevi gördüğünü onaylamış makinistin eski onayı, takas sonucu elindeki YENİ göreve geçmez;
 * atamacı masasında yeni sahipler "görmedi" görünür; eski atama kimliğiyle "gördüm" 404 verir; yeniden basınca atamacıda "gördü"ye döner.
 */
class TakasGordumQaTest extends TakasTabani {

    private String gunYolu(int sonra, String tren) {
        return "$.satirlar[?(@.trenNo=='" + tren + "')].makinistler[0].gordu";
    }

    @Test
    void takasOnayiSonrasi_eskiGordumYeniGoreveGecmez_atamacidaGormedi_yenidenGordumlaGorduyeDoner() throws Exception {
        Sahne s = sahne();
        Tarayici a = giris("D001");
        Tarayici b = giris("D002");
        GordumYardimci.gordum(a, s.ataA()).andExpect(status().isOk());
        GordumYardimci.gordum(b, s.ataB()).andExpect(status().isOk());
        long talep = id(a.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        b.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());

        Tarayici atamaci = atamaci();
        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());

        // A artık SB'de, B artık SA'da: ikisi de YENİ, atamacıda ikisi de "görmedi"
        a.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].seferId").value(s.sb())).andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
        b.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].seferId").value(s.sa())).andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
        atamaci.get("/api/atama/gun?tarih=" + gun(3)).andExpect(jsonPath(gunYolu(3, "TA")).value("GORMEDI"));
        atamaci.get("/api/atama/gun?tarih=" + gun(4)).andExpect(jsonPath(gunYolu(4, "TB")).value("GORMEDI"));
        assertThat(sayi("SELECT count(*) FROM gorev_gordum WHERE atama_id IN (?, ?)", s.ataA(), s.ataB())).as("silinen atamaların onayı öksüz kalmaz").isZero();

        // eski ekrandaki (artık silinmiş) atama kimliğiyle gördüm: 404
        GordumYardimci.gordum(a, s.ataA(), "-").andExpect(status().isNotFound());

        // A yeni görevini görüp basar: atamacıda yalnız o taraf "gördü"
        a.get("/api/benim/gorevler").andReturn();
        long yeniAtaA = jdbc.queryForObject("SELECT id FROM atama WHERE sefer_id = ?", Long.class, s.sb());
        GordumYardimci.gordum(a, yeniAtaA).andExpect(status().isOk());
        atamaci.get("/api/atama/gun?tarih=" + gun(4)).andExpect(jsonPath(gunYolu(4, "TB")).value("GORDU"));
        atamaci.get("/api/atama/gun?tarih=" + gun(3)).andExpect(jsonPath(gunYolu(3, "TA")).value("GORMEDI"));
    }

    @Test
    void birakmaOnayiSonrasi_devralanMakinistGormedi_birakanaKalemGelir_atamacidaGormedi() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        Tarayici a = giris("D001");
        Tarayici cc = giris("D003");
        GordumYardimci.gordum(a, s.ataA()).andExpect(status().isOk());
        long talep = id(a.post("/api/benim/takas", acGovdesi(s.ataA(), null, "HASTALIK", null)).andExpect(status().isCreated()));

        Tarayici atamaci = atamaci();
        atamaci.post("/api/takas/" + talep + "/onay", "{\"yeniMakinistId\":" + c + "}").andExpect(status().isOk());

        cc.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].seferId").value(s.sa())).andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
        atamaci.get("/api/atama/gun?tarih=" + gun(3)).andExpect(jsonPath(gunYolu(3, "TA")).value("GORMEDI"));
        a.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(0)).andExpect(jsonPath("$.kaldirilanlar.length()").value(1));
    }
}
