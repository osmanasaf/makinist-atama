package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/**
 * MK-35: {@code AtamaServisi.takasUygula} iki yönlü kural kontrolü. A'nın SB'den sonraki görevi dinlenme eşiğinin (11 saat, bağlı gar) sınırında; bırakılan görev komşu sayılmaz;
 * B'nin SB sonrası görevleri atlanmaz; UYARI gerekçe ister. Görevler talep açıldıktan SONRA eklenir (bayat sayılmaz: yalnız iki görevin özeti izlenir), böylece kural denetimi onayda yapılır.
 */
class TakasUygulaTest extends TakasTabani {

    private String sahip(long sefer, String rol) {
        return jdbc.queryForObject("SELECT m.sicil FROM atama a JOIN makinist m ON m.id = a.makinist_id WHERE a.sefer_id = ? AND a.rol = ?", String.class, sefer, rol);
    }

    private long kabulluTalep(Sahne s) throws Exception {
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        return talep;
    }

    @Test
    void dinlenmeEsigininBirDakikaAltinda422_hicbirSatirDegismez_esitteGecer() throws Exception {
        Sahne s = sahne();
        long talep = kabulluTalep(s);
        // SB 4. gün 08:00–09:00; A'nın SB'den sonraki görevi aynı gün: 19:59 (10 s 59 dk sonra: eşiğin 1 dk altı)
        long la = atama(sefer("TLA", gun(4), "19:59", "20:30"), s.a(), "ASIL");
        Tarayici atamaci = atamaci();

        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.ihlaller.length()").value(1)).andExpect(jsonPath("$.ihlaller[0].sicil").value("D001"))
                .andExpect(jsonPath("$.ihlaller[0].ihlal.kod").value("K-11")).andExpect(jsonPath("$.ihlaller[0].ihlal.siddet").value("ENGEL"));
        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"))
                .andExpect(jsonPath("$.ihlaller[0].sicil").value("D001")).andExpect(jsonPath("$.ihlaller[0].ihlal.kod").value("K-11"));
        assertThat(sahip(s.sa(), "ASIL")).as("hiçbir satır değişmedi").isEqualTo("D001");
        assertThat(sahip(s.sb(), "YARDIMCI")).isEqualTo("D002");
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).as("talep aynı durumda").isEqualTo("ATAMACI_ONAYI_BEKLIYOR");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur IN ('ATAMA_DEGISTIR', 'TAKAS_ONAY')")).isZero();

        jdbc.update("DELETE FROM atama WHERE id = ?", la);
        atama(sefer("TLB", gun(4), "20:00", "20:30"), s.a(), "ASIL"); // tam 11 saat: eşitte geçer
        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));
        assertThat(sahip(s.sb(), "YARDIMCI")).isEqualTo("D001");
    }

    @Test
    void birakilanGorevKomsuSayilmaz_yakinSeferlerdeYanlisCakismaEngeliUretmez() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long sa = sefer("TA", gun(3), "08:00", "10:00");
        long sb = sefer("TB", gun(3), "09:00", "11:00"); // SA ile çakışır; A SA'yı bırakıp SB'yi alır, B tersini yapar
        long ataA = atama(sa, a, "ASIL");
        long ataB = atama(sb, b, "YARDIMCI");
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andExpect(status().isCreated())); // ön kontrol de yanlış ENGEL vermedi
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());
        assertThat(sahip(sb, "YARDIMCI")).isEqualTo("D001");
        assertThat(sahip(sa, "ASIL")).isEqualTo("D002");
    }

    @Test
    void karsiTarafinSeferSonrasiGorevleriAtlanmaz_cakisma422_ihlalSahibiD002() throws Exception {
        Sahne s = sahne();
        long talep = kabulluTalep(s);
        // B (SB'de) SA gününde 08:30–09:30 görevi alır: B, SA'yı devralırsa çakışır (SB bırakılan, LB bırakılan DEĞİL)
        atama(sefer("TLB", gun(3), "08:30", "09:30"), s.b(), "ASIL");

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[0].sicil").value("D002"))
                .andExpect(jsonPath("$.ihlaller[0].ihlal.kod").value("K-10"));
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
    }

    @Test
    void uyariDuzeyindeGerekceZorunlu_gerekceliOnayGecer_atamayaYazilir() throws Exception {
        Sahne s = sahne();
        jdbc.update("UPDATE kural_siddet SET siddet = 'UYARI' WHERE kod = 'K-11'"); // TestVeritabani her testten önce tohuma döner
        long talep = kabulluTalep(s);
        atama(sefer("TLA", gun(4), "19:59", "20:30"), s.a(), "ASIL");
        Tarayici atamaci = atamaci();

        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("DOGRULAMA"));
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.ihlaller[0].ihlal.siddet").value("UYARI"));

        atamaci.post("/api/takas/" + talep + "/onay", "{\"gerekce\":\"vardiya amiri onayladi\"}").andExpect(status().isOk());
        assertThat(sahip(s.sb(), "YARDIMCI")).isEqualTo("D001");
        assertThat(jdbc.queryForObject("SELECT gerekce FROM atama WHERE sefer_id = ?", String.class, s.sb())).isEqualTo("vardiya amiri onayladi");
    }

    /** Köprü görevi (review W2): A aynı gün S0 08:00-09:30 ve S1 11:30-13:00 (takas edilecek); sonradan S3 15:00-16:30 gelirse S1 kalkınca S0-S3 arası 5,5 s (nöbet ayrılır, dinlenme < 11 s). Döner: S0, S1 atama kimlikleri. */
    private long[] kopru(long a) {
        long s0 = atama(sefer("KS0", gun(3), "08:00", "09:30"), a, "ASIL");
        long s1 = atama(sefer("KS1", gun(3), "11:30", "13:00"), a, "ASIL");
        return new long[] { s0, s1 };
    }

    @Test
    void kopruGoreviniTakasEtmek_elindeKalanUcuncuAtamayiKirmiziYapacaksaOnayda422() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long[] k = kopru(a);
        long ataB = atama(sefer("KB", gun(4), "08:00", "09:00"), b, "YARDIMCI");
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(k[1], ataB, "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        atama(sefer("KS3", gun(3), "15:00", "16:30"), a, "ASIL"); // talepten sonra eklenir: bayat sayılmaz, onayda yakalanmalı

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"))
                .andExpect(jsonPath("$.ihlaller[0].sicil").value("D001")).andExpect(jsonPath("$.ihlaller[0].ihlal.kod").value("K-11"));
        assertThat(jdbc.queryForObject("SELECT makinist_id FROM atama WHERE id = ?", Long.class, k[1])).as("hiçbir satır değişmedi").isEqualTo(a);
    }

    @Test
    void kopruGoreviniTakasEtmek_acilistaDaReddedilir() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long[] k = kopru(a);
        atama(sefer("KS3", gun(3), "15:00", "16:30"), a, "ASIL");
        long ataB = atama(sefer("KB", gun(4), "08:00", "09:00"), b, "YARDIMCI");

        giris("D001").post("/api/benim/takas", acGovdesi(k[1], ataB, "IZIN", null)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_KURALA_UYMUYOR"));
    }

    @Test
    void kopruOlmayanGorevinTakasiGecer() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        kopru(a);
        atama(sefer("KS3", gun(3), "15:00", "16:30"), a, "ASIL");
        long ataB = atama(sefer("KB", gun(4), "08:00", "09:00"), b, "YARDIMCI");
        // S3 (zincirin sonu) takas edilir: S0-S1 arası değişmez, kimse kırmızı olmaz
        long s3 = jdbc.queryForObject("SELECT id FROM atama WHERE makinist_id = ? ORDER BY id DESC LIMIT 1", Long.class, a);
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s3, ataB, "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());
    }

    /** Review W3: T1 = A.x(S1) <-> B.y(S2 ASIL), T2 = E.z(S2 YARDIMCI) <-> A.q(S4). T1 onaylanınca A S2'de görevli: T2 yürütülemez, BAYAT sayılır, ayrıntı açılır, rozet düşer, reddedilebilir. */
    @Test
    void zincirlemeTalep_ilkOnaylaninca_ikinciBayat_ayrintiAcilir_rozetDuser_kapatilabilir() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long e = hesapli("D003");
        long ataX = atama(sefer("ZS1", gun(3), "08:00", "09:00"), a, "ASIL");
        long s2 = sefer("ZS2", gun(4), "08:00", "09:00");
        long ataY = atama(s2, b, "ASIL");
        long ataZ = atama(s2, e, "YARDIMCI");
        long ataQ = atama(sefer("ZS4", gun(5), "08:00", "09:00"), a, "ASIL");
        long t1 = id(giris("D001").post("/api/benim/takas", acGovdesi(ataX, ataY, "IZIN", null)).andExpect(status().isCreated()));
        long t2 = id(giris("D003").post("/api/benim/takas", acGovdesi(ataZ, ataQ, "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + t1 + "/kabul", "").andExpect(status().isOk());
        giris("D001").post("/api/benim/takas/" + t2 + "/kabul", "").andExpect(status().isOk());
        Tarayici atamaci = atamaci();
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(2));

        atamaci.post("/api/takas/" + t1 + "/onay", "{}").andExpect(status().isOk());

        atamaci.get("/api/takas/" + t2).andExpect(status().isOk()).andExpect(jsonPath("$.talep.durum").value("BAYAT")).andExpect(jsonPath("$.talep.kayitliDurum").value("ATAMACI_ONAYI_BEKLIYOR"));
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(0));
        atamaci.post("/api/takas/" + t2 + "/red", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("REDDEDILDI"));
        assertThat(sahip(s2, "ASIL")).isEqualTo("D001");
    }

    @Test
    void zincirlemeTalep_onayVerilirse409TalepBayat_talepKalicikapanir() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long e = hesapli("D003");
        long ataX = atama(sefer("ZS1", gun(3), "08:00", "09:00"), a, "ASIL");
        long s2 = sefer("ZS2", gun(4), "08:00", "09:00");
        long ataY = atama(s2, b, "ASIL");
        long ataZ = atama(s2, e, "YARDIMCI");
        long ataQ = atama(sefer("ZS4", gun(5), "08:00", "09:00"), a, "ASIL");
        long t1 = id(giris("D001").post("/api/benim/takas", acGovdesi(ataX, ataY, "IZIN", null)).andExpect(status().isCreated()));
        long t2 = id(giris("D003").post("/api/benim/takas", acGovdesi(ataZ, ataQ, "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + t1 + "/kabul", "").andExpect(status().isOk());
        giris("D001").post("/api/benim/takas/" + t2 + "/kabul", "").andExpect(status().isOk());
        Tarayici atamaci = atamaci();
        atamaci.post("/api/takas/" + t1 + "/onay", "{}").andExpect(status().isOk());

        atamaci.post("/api/takas/" + t2 + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, t2)).isEqualTo("BAYAT");
    }
}
