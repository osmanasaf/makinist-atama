package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/** FR-M11 / FR-M12 / FR-M15: talep açma, karşı kabul / ret, geri çekme; sahiplik ve süzgeçler; ön kontrol ayrıntı sızdırmaz. */
class TakasAcmaTest extends TakasTabani {

    @Test
    void karsilikliAcma_201_karsiTarafaKartVeRozet_ucuncuKisiGormez_birKayit() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        Tarayici tc = giris("D003");

        var yanit = ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", "dugun")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.durum").value("KARSI_ONAY_BEKLIYOR")).andExpect(jsonPath("$.tur").value("TAKAS")).andExpect(jsonPath("$.gelen").value(false))
                .andExpect(jsonPath("$.karsiSicil").value("D002")).andExpect(jsonPath("$.neden").value("IZIN")).andExpect(jsonPath("$.nedenNotu").value("dugun"))
                .andExpect(jsonPath("$.sonKabul").exists());
        long talep = id(yanit);

        tb.get("/api/benim/takas").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(talep))
                .andExpect(jsonPath("$[0].gelen").value(true)).andExpect(jsonPath("$[0].yanitBekliyor").value(true)).andExpect(jsonPath("$[0].karsiSicil").value("D001"))
                .andExpect(jsonPath("$[0].neden").doesNotExist()).andExpect(jsonPath("$[0].nedenNotu").doesNotExist());
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(1));
        tc.get("/api/benim/takas").andExpect(jsonPath("$.length()").value(0));
        tc.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));

        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_TALEP'")).isEqualTo(1);
        var k = jdbc.queryForMap("SELECT kullanici_adi, makinist_id, sefer_id, varlik_id FROM degisiklik_kaydi WHERE tur = 'TAKAS_TALEP'");
        assertThat(k).containsEntry("kullanici_adi", "Demo Makinist D001").containsEntry("makinist_id", s.a()).containsEntry("sefer_id", s.sa()).containsEntry("varlik_id", talep);
        assertThat(sayi("SELECT count(*) FROM atama")).as("talep atamaları değiştirmez").isEqualTo(2);
        assertThat(c).isPositive();
    }

    @Test
    void birakmaTalebi_dogrudanAtamaciOnayinaDuser_kartiTekilTarafta() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), null, "HASTALIK", null)).andExpect(status().isCreated()).andExpect(jsonPath("$.tur").value("BIRAK"))
                .andExpect(jsonPath("$.durum").value("ATAMACI_ONAYI_BEKLIYOR")).andExpect(jsonPath("$.karsiSicil").doesNotExist()).andExpect(jsonPath("$.sonKabul").doesNotExist());
        atamaci().get("/api/takas").andExpect(jsonPath("$.bekleyen").value(1));
    }

    @Test
    void sahiplikVeSuzgecler_baskasininAtamasi404_kendiHedefi400_ayniSefer400_zatenAtanmis409() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        ta.post("/api/benim/takas", acGovdesi(s.ataB(), s.ataA(), "IZIN", null)).andExpect(status().isNotFound());   // başkasının atamasını teklif
        ta.post("/api/benim/takas", acGovdesi(987654, s.ataB(), "IZIN", null)).andExpect(status().isNotFound());     // olmayan
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), 987654L, "IZIN", null)).andExpect(status().isNotFound());     // tahmin edilen / olmayan hedef
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataA(), "IZIN", null)).andExpect(status().isBadRequest()); // kendi göreviyle

        long yardimci = atama(s.sa(), s.b(), "YARDIMCI"); // B, A ile AYNI seferde (SA)
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), yardimci, "IZIN", null)).andExpect(status().isBadRequest()); // aynı sefer içi takas anlamsız
        jdbc.update("DELETE FROM atama WHERE id = ?", yardimci);

        atama(s.sb(), s.a(), "ASIL"); // A zaten SB'de de görevli
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isZero();
    }

    @Test
    void hedefHesapsizPasifYaDaAskida_404_tahminEdilenKimlikVarlikSizdirmaz() throws Exception {
        long a = hesapli("D001");
        long sa = sefer("TA", gun(3), "08:00", "09:00");
        long ataA = atama(sa, a, "ASIL");
        v.makinist("D002");
        long sb = sefer("TB", gun(4), "08:00", "09:00");
        long ataB = atama(sb, jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'D002'", Long.class), "ASIL");
        Tarayici ta = giris("D001");

        ta.post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andExpect(status().isNotFound()); // hesapsız
        long b = jdbc.queryForObject("SELECT id FROM makinist WHERE sicil = 'D002'", Long.class);
        kullanicilar.makinistHesabiAc(b, "D002", "Demo Makinist D002", Tarayici.PAROLA);
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", b);
        ta.post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andExpect(status().isNotFound()); // askıda makinist
        jdbc.update("UPDATE makinist SET durum = 'AKTIF' WHERE id = ?", b);
        ta.post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)).andExpect(status().isCreated());
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isEqualTo(1);
    }

    @Test
    void birAtamaTekAcikTalepte_ikinciAcis409_hedefBaskaTalepteyse404_kapaliOlanSonrasiYenidenAcilir() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        long sc = sefer("TC", gun(5), "08:00", "09:00");
        long ataC = atama(sc, c, "ASIL");
        Tarayici ta = giris("D001");
        Tarayici tc = giris("D003");

        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), ataC, "IZIN", null)).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_ACIK")); // kaynak zaten açık talepte
        tc.post("/api/benim/takas", acGovdesi(ataC, s.ataB(), "IZIN", null)).andExpect(status().isNotFound()); // hedef (B'nin SB'si) başka açık talepte: süzgeçte değil
        giris("D002").post("/api/benim/takas", acGovdesi(s.ataB(), ataC, "IZIN", null)).andExpect(status().isConflict()); // hedef atama kaynak olarak da bir talepte (çapraz durum)

        ta.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("IPTAL"));
        tc.post("/api/benim/takas", acGovdesi(ataC, s.ataB(), "IZIN", null)).andExpect(status().isCreated());
    }

    @Test
    void kendiTarafiEngelliyse422_sebepKendiTarafinMetni_karsiTarafBilgisiYok() throws Exception {
        Sahne s = sahne();
        // A'nın SB günü (4 gün sonra 08:00–09:00) çakışan görevi var: A, SB'yi alırsa çakışma ENGEL'i (kendi tarafı)
        atama(sefer("TL", gun(4), "08:30", "09:30"), s.a(), "ASIL");
        Tarayici ta = giris("D001");

        var yanit = ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.kod").value("TAKAS_KURALA_UYMUYOR")).andReturn().getResponse().getContentAsString();
        assertThat(yanit).contains("Bu takas kurallara uymuyor:").contains("Çakışma").doesNotContain("ihlaller").doesNotContain("D002").doesNotContain("Demo Makinist"); // sebep: açanın KENDİ tarafı (UX-1)
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isZero();
    }

    /** W4: karşı tarafın kural / belge durumu açılış yanıtından ÇIKARILAMAZ: karşı ENGEL'li de sağlam da 201; ihlal yalnız atamacıda (onay 422). */
    @Test
    void karsiTarafiEngelliOlsaDaAcilis201_ihlalYalnizAtamacidaGorunur() throws Exception {
        Sahne s = sahne();
        // B'nin SB günü, SA'yla (3 gün sonra 08:00–09:00) çakışan görevi var: B, SA'yı alırsa çakışma ENGEL'i (karşı taraf)
        atama(sefer("TL", gun(3), "08:30", "09:30"), s.b(), "ASIL");
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[0].sicil").value("D002"));
    }

    /** W4: açma kotası (uygulama_ayar takas.gunluk_acma): geri çekilenler de sayılır; başka makinist etkilenmez. */
    @Test
    void acmaKotasi_geriCekilenlerSayilir_dolunca429_baskasiEtkilenmez() throws Exception {
        Sahne s = sahne();
        jdbc.update("UPDATE uygulama_ayar SET deger = 3 WHERE anahtar = 'takas.gunluk_acma'");
        Tarayici ta = giris("D001");
        for (int i = 0; i < 3; i++) {
            long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
            ta.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isOk());
        }
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.kod").value("TAKAS_KOTASI"));
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isEqualTo(3);
        Tarayici tb = giris("D002");
        long d2 = id(tb.post("/api/benim/takas", acGovdesi(s.ataB(), s.ataA(), "IZIN", null)).andExpect(status().isCreated()));
        tb.post("/api/benim/takas/" + d2 + "/iptal", "").andExpect(status().isOk());
        jdbc.update("UPDATE takas_talebi SET olusturma = olusturma - interval '25 hours' WHERE durum = 'IPTAL' AND kaynak_makinist_id = ?", s.a());
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()); // 24 saat geçti: kota yeniden açıldı
    }

    @Test
    void nedenZorunlu_gecerliNedenler_digerdeNotZorunlu_notUzunluguSiniri() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        ta.post("/api/benim/takas", "{\"atamaId\":" + s.ataA() + ",\"hedefAtamaId\":" + s.ataB() + "}").andExpect(status().isBadRequest());
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IPTAL", null)).andExpect(status().isBadRequest());
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "DIGER", null)).andExpect(status().isBadRequest());
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", "x".repeat(121))).andExpect(status().isBadRequest());
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "DIGER", "aile işi")).andExpect(status().isCreated());
    }

    @Test
    void karsiKabulRedIptal_yalnizIlgiliTaraf_digerleri404_kapaliTalepte409() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        Tarayici tc = giris("D003");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));

        ta.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isNotFound());  // açan kendi talebini kabul edemez
        tc.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isNotFound());
        tc.post("/api/benim/takas/" + talep + "/red", "").andExpect(status().isNotFound());
        tc.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isNotFound());
        tb.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isNotFound());  // karşı taraf geri çekemez
        tb.post("/api/benim/takas/987654/kabul", "").andExpect(status().isNotFound());

        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ATAMACI_ONAYI_BEKLIYOR"));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_KABUL'")).isEqualTo(1);
        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_KAPANDI"));
        tb.post("/api/benim/takas/" + talep + "/red", "").andExpect(status().isConflict());
        ta.post("/api/benim/takas/" + talep + "/iptal", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("IPTAL"));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_IPTAL'")).isEqualTo(1);
        assertThat(c).isPositive();
    }

    @Test
    void karsiRed_acanaGorunur_atamalarKorunur() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));

        tb.post("/api/benim/takas/" + talep + "/red", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("REDDEDILDI"));
        ta.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("REDDEDILDI"));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_RED'")).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM atama WHERE id = ? AND makinist_id = ?", s.ataA(), s.a())).isEqualTo(1);
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()); // kapalı talep yeni açılışı engellemez
    }

    @Test
    void askidaYaDaPasifHesap_yazamaz() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        jdbc.update("UPDATE makinist SET durum = 'ASKIDA' WHERE id = ?", s.a());
        ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("HESAP_ASKIDA"));
        ta.get("/api/benim/takas").andExpect(status().isOk());
    }

    @Test
    void ufukDisiGorev_404() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long uzak = atama(sefer("TU", gun(9), "08:00", "09:00"), a, "ASIL"); // ufuk 7 gün: bugün..bugün+6
        long sb = atama(sefer("TB", gun(4), "08:00", "09:00"), b, "ASIL");
        giris("D001").post("/api/benim/takas", acGovdesi(uzak, sb, "IZIN", null)).andExpect(status().isNotFound());
    }

    /** QA UX-3 / UX-4: görev yanıtı açık talepteki kendi atamalarımı taşır; reddedilen talepte kimin reddettiği (yalnız rol) görünür. */
    @Test
    void acikAtamalarVeReddeden_makinistYanitlarinda() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.takasAcikAtamalar[0]").value((int) s.ataA())).andExpect(jsonPath("$.takasAcikAtamalar.length()").value(1));
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.takasAcikAtamalar[0]").value((int) s.ataB()));

        tb.post("/api/benim/takas/" + talep + "/red", "").andExpect(status().isOk()).andExpect(jsonPath("$.reddeden").value("KARSI_MAKINIST"));
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.takasAcikAtamalar.length()").value(0));
        ta.get("/api/benim/takas").andExpect(jsonPath("$[0].reddeden").value("KARSI_MAKINIST"));

        long talep2 = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        atamaci().post("/api/takas/" + talep2 + "/red", "").andExpect(status().isOk());
        ta.get("/api/benim/takas").andExpect(jsonPath("$[0].reddeden").value("ATAMACI"));
        tb.get("/api/benim/takas").andExpect(jsonPath("$[0].reddeden").value("ATAMACI"));
    }
}
