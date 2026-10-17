package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.atama.AtamaVeri;

/** F3b MK-90 (FR-08): takas yalnız aynı personel türü içinde; hedef listesi, açılış ve uygulama anı. */
class KonduktorTakasTest extends TakasTabani {

    /** Hesaplı kondüktör (giriş sicil). */
    long hesapliKonduktor(String sicil) throws Exception {
        if (v == null) {
            v = new AtamaVeri(jdbc);
        }
        long k = v.konduktor(sicil);
        kullanicilar.makinistHesabiAc(k, sicil, "Demo Kondüktör " + sicil, Tarayici.PAROLA);
        jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE makinist_id = ?", k);
        return k;
    }

    private List<Integer> hedefAtamalari(String sicil, long atamaId) throws Exception {
        String govde = giris(sicil).get("/api/benim/takas/hedefler?atamaId=" + atamaId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(govde, "$[*].hedefAtamaId");
    }

    @Test
    void hedefListesi_yalnizAyniTur() throws Exception {
        long c1 = hesapliKonduktor("K001");
        long c2 = hesapliKonduktor("K002");
        long m1 = hesapli("D001");
        long m2 = hesapli("D002");
        long s1 = sefer("T1", gun(3), "08:00", "09:00");
        long s2 = sefer("T2", gun(4), "08:00", "09:00");
        long s3 = sefer("T3", gun(5), "08:00", "09:00");
        long s4 = sefer("T4", gun(6), "08:00", "09:00");
        long ataC1 = atama(s1, c1, "KONDUKTOR");
        long ataC2 = atama(s2, c2, "KONDUKTOR");
        long ataM1 = atama(s3, m1, "ASIL");
        long ataM2 = atama(s4, m2, "ASIL");

        assertThat(hedefAtamalari("K001", ataC1)).containsExactly((int) ataC2);
        assertThat(hedefAtamalari("D001", ataM1)).containsExactly((int) ataM2);
    }

    @Test
    void caprazTurTalep_404_ayniTurTakas_olur_ve_roller_yuvayla_gider() throws Exception {
        long c1 = hesapliKonduktor("K001");
        long c2 = hesapliKonduktor("K002");
        long m1 = hesapli("D001");
        long s1 = sefer("T1", gun(3), "08:00", "09:00");
        long s2 = sefer("T2", gun(4), "08:00", "09:00");
        long s3 = sefer("T3", gun(5), "08:00", "09:00");
        long ataC1 = atama(s1, c1, "KONDUKTOR");
        long ataC2 = atama(s2, c2, "KONDUKTOR");
        long ataM1 = atama(s3, m1, "ASIL");

        giris("K001").post("/api/benim/takas", acGovdesi(ataC1, ataM1, "IZIN", null)).andExpect(status().isNotFound()); // varlık sızdırmaz

        long talep = id(giris("K001").post("/api/benim/takas", acGovdesi(ataC1, ataC2, "IZIN", null)).andExpect(status().isCreated()));
        giris("K002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));

        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ? AND rol = 'KONDUKTOR'", s1, c2)).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ? AND rol = 'KONDUKTOR'", s2, c1)).isEqualTo(1);
    }

    @Test
    void asilYardimciTakasiDegismez() throws Exception {
        Sahne s = sahne(); // D001 ASIL, D002 YARDIMCI
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ? AND rol = 'ASIL'", s.sa(), s.b())).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ? AND rol = 'YARDIMCI'", s.sb(), s.a())).isEqualTo(1);
    }

    @Test
    void acilisIleUygulamaArasindaTurBozulursa_onay422_PERSONEL_TURU_UYMUYOR() throws Exception {
        Sahne s = sahne();
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        jdbc.update("UPDATE makinist SET tur = 'KONDUKTOR' WHERE id = ?", s.b()); // savunma: tür bozuldu (normalde sabit)

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("PERSONEL_TURU_UYMUYOR"));
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ?", s.sa(), s.a())).isEqualTo(1); // hiçbir şey yazılmadı
    }

    @Test
    void konduktorBirakma_oneriler_yalnizKonduktor_ve_varsayilanOnayOlur() throws Exception {
        long c1 = hesapliKonduktor("K001");
        long c2 = v.konduktor("K002"); // boşta aday kondüktör
        long m2 = v.makinist("D002"); // boşta makinist: önerilmemeli
        long s1 = sefer("T1", gun(3), "08:00", "09:00");
        long ataC1 = atama(s1, c1, "KONDUKTOR");

        long talep = id(giris("K001").post("/api/benim/takas", acGovdesi(ataC1, null, "IZIN", null)).andExpect(status().isCreated()));
        String detay = atamaci().get("/api/takas/" + talep).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(detay, "$.oneri.oneriler[*].makinistId")).containsExactly((int) c2).doesNotContain((int) m2);

        atamaci().post("/api/takas/" + talep + "/onay", "{\"yeniMakinistId\":" + c2 + "}").andExpect(status().isOk());
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ? AND makinist_id = ? AND rol = 'KONDUKTOR'", s1, c2)).isEqualTo(1);
    }

    @Test
    void askidaKonduktor_hedefleri_yalnizKonduktor() throws Exception {
        long c1 = hesapliKonduktor("K001");
        long c2 = hesapliKonduktor("K002");
        long m1 = hesapli("D001");
        long ataC1 = atama(sefer("T1", gun(3), "08:00", "09:00"), c1, "KONDUKTOR");
        long ataC2 = atama(sefer("T2", gun(4), "08:00", "09:00"), c2, "KONDUKTOR");
        atama(sefer("T3", gun(5), "08:00", "09:00"), m1, "ASIL");
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", c1);

        assertThat(hedefAtamalari("K001", ataC1)).containsExactly((int) ataC2);
    }
}
