package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/**
 * FR-M13: kalkışa {@code takas.son_saat} (12) saat kala sınırı. Entegrasyon testleri gerçek saate göre bir-iki dakika payla çalışır; tam eşitlik (12 s kala = uygun) ve karşı onay süresi
 * {@code TakasKuraliTest}'te sabit saatle kanıtlanır. Burada: açma / kabul / hastalık istisnası / kalkış geçince kapanış, ayar değişince sınır değişir.
 */
class TakasSureTest extends TakasTabani {

    @Test
    void acma_12SaatBirkacDakikaKalaKabul_11Saat50DakikaKalaTAKAS_SURESI_GECTI() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long uygunA = atama(seferSimdiden("TA1", 12, 5), a, "ASIL");
        long b1 = atama(sefer("TB1", gun(4), "08:00", "09:00"), b, "ASIL");
        Tarayici ta = giris("D001");
        ta.post("/api/benim/takas", acGovdesi(uygunA, b1, "IZIN", null)).andExpect(status().isCreated());   // en erken kalkış 12 s 05 dk sonra

        long dar = atama(seferSimdiden("TA2", 11, 50), a, "YARDIMCI");
        long b2 = atama(sefer("TB2", gun(5), "08:00", "09:00"), b, "YARDIMCI");
        ta.post("/api/benim/takas", acGovdesi(dar, b2, "IZIN", null)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
        // karşılıklı takasta iki görevin EN ERKEN kalkışı esas: B'nin görevi yakın olsa da aynı sınır
        long uzakA = atama(sefer("TA3", gun(6), "08:00", "09:00"), a, "ASIL");
        long yakinB = atama(seferSimdiden("TB3", 11, 50), b, "ASIL");
        ta.post("/api/benim/takas", acGovdesi(uzakA, yakinB, "IZIN", null)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isEqualTo(1);
    }

    @Test
    void birakma_hastalikNedeniAcilIstisna_digerNedenlerSinirdaReddedilir_kalkistanSonraHastalikTaOlmaz() throws Exception {
        long a = hesapli("D001");
        long yakin = atama(seferSimdiden("TA1", 5, 0), a, "ASIL");
        long yakin2 = atama(seferSimdiden("TA2", 6, 0), a, "YARDIMCI");
        long gecmis = atama(seferSimdiden("TA3", -1, 0), a, "ASIL");
        Tarayici ta = giris("D001");

        ta.post("/api/benim/takas", acGovdesi(yakin, null, "IZIN", null)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
        ta.post("/api/benim/takas", acGovdesi(yakin, null, "HASTALIK", null)).andExpect(status().isCreated()).andExpect(jsonPath("$.durum").value("ATAMACI_ONAYI_BEKLIYOR"));
        ta.post("/api/benim/takas", acGovdesi(yakin2, null, "DIGER", "ailevi")).andExpect(status().isUnprocessableEntity());
        int gecmisKod = ta.post("/api/benim/takas", acGovdesi(gecmis, null, "HASTALIK", null)).andReturn().getResponse().getStatus(); // kalkışı geçmiş: hastalık da açamaz
        assertThat(gecmisKod).as("dün kalkmışsa ufukta yok (404), bugün kalkmışsa kalkış geçti (422)").isIn(404, 422);
    }

    @Test
    void sinirAyariDegisirseSinirDegisir_takasSonSaat() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long ataA = atama(seferSimdiden("TA1", 20, 0), a, "ASIL");
        long ataB = atama(sefer("TB1", gun(4), "08:00", "09:00"), b, "ASIL");
        Tarayici ta = giris("D001");
        jdbc.update("UPDATE uygulama_ayar SET deger = 24 WHERE anahtar = 'takas.son_saat'");
        ta.post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
        jdbc.update("UPDATE uygulama_ayar SET deger = 12 WHERE anahtar = 'takas.son_saat'");
        ta.post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andExpect(status().isCreated());
    }

    @Test
    void kabul_karsiOnaySuresiGecmisse422_kalicikapanir_kalkisSiniriGecmisseDe422() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));

        jdbc.update("UPDATE takas_talebi SET olusturma = now() - interval '24 hours 1 minute' WHERE id = ?", talep); // karşı onay süresi (24 s) doldu
        tb.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("SURESI_DOLDU")).andExpect(jsonPath("$[0].yanitBekliyor").value(false));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));
        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("SURESI_DOLDU");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_SURESI_DOLDU'")).isEqualTo(1);

        long talep2 = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated())); // süresi dolan kapalı: yeniden açılır
        jdbc.update("UPDATE takas_talebi SET son_kalkis = now() + interval '11 hours 59 minutes' WHERE id = ?", talep2); // kalkış − 12 s geçmiş
        tb.post("/api/benim/takas/" + talep2 + "/kabul", "").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
    }

    @Test
    void karsiOnaySuresiDolanTalebiRedEtmekMumkun_acanGeriCekebilir() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        jdbc.update("UPDATE takas_talebi SET olusturma = now() - interval '25 hours' WHERE id = ?", talep);
        ta.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("IPTAL"));
    }
}
