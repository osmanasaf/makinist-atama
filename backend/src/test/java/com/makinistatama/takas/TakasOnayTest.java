package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/** FR-M12 / FR-M14 / FR-M15: atamacı onayı (karşılıklı ve bırakma), bayatlık, kalkış sonrası, ret, yetki. */
class TakasOnayTest extends TakasTabani {

    private long acVeKabulEt(Sahne s) throws Exception {
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        return talep;
    }

    private String sahip(long sefer, String rol) {
        return jdbc.queryForObject("SELECT m.sicil FROM atama a JOIN makinist m ON m.id = a.makinist_id WHERE a.sefer_id = ? AND a.rol = ?", String.class, sefer, rol);
    }

    @Test
    void karsilikliOnay_ikiAtamaYerDegistirir_rolYuvayla_ikiDegistirKaydi_birOnayKaydi_ekranlarGuncellenir() throws Exception {
        Sahne s = sahne();
        long talep = acVeKabulEt(s);
        Tarayici atamaci = atamaci();

        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));

        assertThat(sahip(s.sa(), "ASIL")).as("B, SA'da A'nın yuvasında (ASIL)").isEqualTo("D002");
        assertThat(sahip(s.sb(), "YARDIMCI")).as("A, SB'de B'nin yuvasında (YARDIMCI)").isEqualTo("D001");
        assertThat(sayi("SELECT count(*) FROM atama")).isEqualTo(2);
        assertThat(sayi("SELECT count(*) FROM atama WHERE id IN (?, ?)", s.ataA(), s.ataB())).as("eski satırlar silindi, yenileri eklendi").isZero();
        List<Map<String, Object>> degistir = jdbc.queryForList("SELECT ayrinti, neden, kullanici_adi FROM degisiklik_kaydi WHERE tur = 'ATAMA_DEGISTIR' ORDER BY id");
        assertThat(degistir).hasSize(2);
        assertThat(degistir).allSatisfy(d -> {
            assertThat((String) d.get("ayrinti")).contains("\"takas\":" + talep);
            assertThat(d.get("neden")).isEqualTo("IZIN");
            assertThat(d.get("kullanici_adi")).isEqualTo("Ata Maci");
        });
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_ONAY'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT kullanici_adi FROM degisiklik_kaydi WHERE tur = 'TAKAS_ONAY'", String.class)).isEqualTo("Ata Maci");
        assertThat(sayi("SELECT count(*) FROM atama_olayi")).as("iki eski makinist için 'başkasına verildi' kalemi").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT neden_etiketi FROM atama_olayi WHERE makinist_id = ?", String.class, s.a())).as("açanın kendi nedeni").isEqualTo("IZIN");
        assertThat(jdbc.queryForObject("SELECT neden_etiketi FROM atama_olayi WHERE makinist_id = ?", String.class, s.b())).as("karşı makinistin kaleminde başkasının nedeni yok").isNull();
        assertThat(jdbc.queryForObject("SELECT karar_veren FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("Ata Maci");

        giris("D001").get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(1)).andExpect(jsonPath("$.gorevler[0].seferId").value(s.sb()))
                .andExpect(jsonPath("$.gorevler[0].gordu").value("YENI"));
        giris("D002").get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler[0].seferId").value(s.sa()));
        giris("D001").get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("ONAYLANDI"));
        atamaci.get("/api/atama/kirmizi-sayisi?bas=" + gun(0) + "&bit=" + gun(8)).andExpect(jsonPath("$.kirmiziAtama").value(0));
    }

    @Test
    void karsiKabulOlmadanOnay409_makinistOnayUcunda403() throws Exception {
        Sahne s = sahne();
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("KARSI_ONAY_YOK"));
        giris("D001").post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isForbidden());
        giris("D002").post("/api/takas/" + talep + "/red", "").andExpect(status().isForbidden());
        giris("D002").get("/api/takas").andExpect(status().isForbidden());
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
    }

    @Test
    void birakma_yerineAta_atamaDegisir_nedenTalepNedeni_makinistinEkranindaKalem() throws Exception {
        Sahne s = sahne();
        long c = v.makinist("D003");
        Tarayici ta = giris("D001");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), null, "HASTALIK", "ates")).andExpect(status().isCreated()));
        Tarayici atamaci = atamaci();

        atamaci.get("/api/takas/" + talep).andExpect(status().isOk()).andExpect(jsonPath("$.talep.nedenNotu").value("ates")).andExpect(jsonPath("$.oneri.oneriler.length()").isNotEmpty())
                .andExpect(jsonPath("$.ihlaller.length()").value(0));
        atamaci.post("/api/takas/" + talep + "/onay", "{\"yeniMakinistId\":" + c + "}").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));

        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D003");
        assertThat(jdbc.queryForObject("SELECT neden FROM degisiklik_kaydi WHERE tur = 'ATAMA_DEGISTIR'", String.class)).isEqualTo("HASTALIK");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_ONAY'")).isEqualTo(1);
        ta.get("/api/benim/gorevler").andExpect(jsonPath("$.gorevler.length()").value(0)).andExpect(jsonPath("$.kaldirilanlar.length()").value(1))
                .andExpect(jsonPath("$.kaldirilanlar[0].nedenEtiketi").value("HASTALIK"));
    }

    @Test
    void birakma_adayYoksaYalnizKaldir_atamaSilinir_atamaKaldirKaydi() throws Exception {
        Sahne s = sahne();
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), null, "IZIN", null)).andExpect(status().isCreated()));
        atamaci().post("/api/takas/" + talep + "/onay", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAYLANDI"));
        assertThat(sayi("SELECT count(*) FROM atama WHERE sefer_id = ?", s.sa())).isZero();
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA_KALDIR'")).isEqualTo(1);
    }

    @Test
    void birakma_devralanKuralaUymuyorsa422_talepAcikKalir_atamaDegismez() throws Exception {
        Sahne s = sahne();
        long c = v.makinist("D003");
        atama(sefer("TC", gun(3), "08:30", "09:30"), c, "ASIL"); // C'nin SA ile çakışan görevi
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), null, "IZIN", null)).andExpect(status().isCreated()));
        Tarayici atamaci = atamaci();

        atamaci.post("/api/takas/" + talep + "/onay", "{\"yeniMakinistId\":" + c + "}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"));
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("ATAMACI_ONAYI_BEKLIYOR");
        atamaci.post("/api/takas/" + talep + "/onay", "{\"yeniMakinistId\":987654}").andExpect(status().isNotFound());
    }

    @Test
    void gecikmeGirilirse_kabulBayat409_talepKalicikapanir_birBayatKaydi() throws Exception {
        Sahne s = sahne();
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated()));
        atamaci().post("/api/sefer/" + s.sb() + "/gecikme", "{\"kalkisGecikmeDk\":25,\"varisGecikmeDk\":25,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        Tarayici tb = giris("D002");
        tb.get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("BAYAT")); // okuma TÜRETİR, yazmaz
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("KARSI_ONAY_BEKLIYOR");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_BAYAT'")).as("GET kayıt yazmaz").isZero();
        tb.get("/api/benim/gorevler").andExpect(jsonPath("$.bekleyenTakas").value(0));

        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).as("ayrı işlemde kalıcı kapandı").isEqualTo("BAYAT");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_BAYAT'")).isEqualTo(1);
    }

    @Test
    void kabulSonrasiSaatDegisirse_onayBayat409_atamalarDegismez_yenidenAcilabilir() throws Exception {
        Sahne s = sahne();
        long talep = acVeKabulEt(s);
        Tarayici atamaci = atamaci();
        atamaci.post("/api/sefer/" + s.sb() + "/gecikme", "{\"kalkisGecikmeDk\":10,\"varisGecikmeDk\":10,\"neden\":\"GECIKME\"}").andExpect(status().isOk());

        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
        assertThat(sahip(s.sb(), "YARDIMCI")).isEqualTo("D002");
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(0));

        // aynı atamalar için yeni talep: bayat olan açık talep önce kapatılır (kısmi UNIQUE 409 vermez)
        giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("BAYAT");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_BAYAT'")).isEqualTo(1);
    }

    @Test
    void sefAtamasiBaskasinaVerilirse_bayat_iptalSeferdeBayat() throws Exception {
        Sahne s = sahne();
        long c = v.makinist("D003");
        long talep = acVeKabulEt(s);
        Tarayici atamaci = atamaci();
        atamaci.post("/api/atama/sefer/" + s.sa(), "{\"makinistId\":" + c + ",\"neden\":\"HASTALIK\"}").andExpect(status().isOk()); // SA'yı C'ye verdi

        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D003");
        atamaci.get("/api/takas/" + talep).andExpect(jsonPath("$.talep.durum").value("BAYAT")).andExpect(jsonPath("$.ihlaller.length()").value(0));

        long talep2 = id(giris("D002").post("/api/benim/takas", acGovdesi(s.ataB(), null, "IZIN", null)).andExpect(status().isCreated()));
        atamaci.post("/api/sefer/" + s.sb() + "/iptal", "{\"neden\":\"IPTAL\"}").andExpect(status().isOk());
        atamaci.post("/api/takas/" + talep2 + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_BAYAT"));
    }

    @Test
    void kalkistenSonraOnayYok_422_suresiDoldu_kalicikapanir() throws Exception {
        Sahne s = sahne();
        long talep = acVeKabulEt(s);
        jdbc.update("UPDATE takas_talebi SET son_kalkis = now() - interval '1 minute' WHERE id = ?", talep);

        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("TAKAS_SURESI_GECTI"));
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("SURESI_DOLDU");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_SURESI_DOLDU'")).isEqualTo(1);
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
    }

    @Test
    void atamaciReddeder_kaynakAtamasiniKorur_ikinciKararVeOnay409() throws Exception {
        Sahne s = sahne();
        long talep = acVeKabulEt(s);
        Tarayici atamaci = atamaci();
        atamaci.post("/api/takas/" + talep + "/red", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("REDDEDILDI"));
        assertThat(sahip(s.sa(), "ASIL")).isEqualTo("D001");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_RED'")).isEqualTo(1);
        atamaci.post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("TALEP_KAPANDI"));
        atamaci.post("/api/takas/" + talep + "/red", "").andExpect(status().isConflict());
        atamaci.post("/api/takas/987654/red", "").andExpect(status().isNotFound());
        giris("D001").get("/api/benim/takas").andExpect(jsonPath("$[0].durum").value("REDDEDILDI"));
    }

    @Test
    void liste_durumSuzgeci_rozetSayisi() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        long ataC = atama(sefer("TC", gun(5), "08:00", "09:00"), c, "ASIL");
        acVeKabulEt(s);                                                                                  // ATAMACI_ONAYI_BEKLIYOR
        giris("D003").post("/api/benim/takas", acGovdesi(ataC, null, "IZIN", null)).andExpect(status().isCreated()); // BIRAK, ATAMACI_ONAYI_BEKLIYOR
        Tarayici atamaci = atamaci();
        atamaci.get("/api/takas").andExpect(jsonPath("$.bekleyen").value(2)).andExpect(jsonPath("$.talepler.length()").value(2));
        atamaci.get("/api/takas?durum=KARSI_ONAY_BEKLIYOR").andExpect(jsonPath("$.talepler.length()").value(0)).andExpect(jsonPath("$.bekleyen").value(2));
        atamaci.get("/api/takas?durum=ATAMACI_ONAYI_BEKLIYOR").andExpect(jsonPath("$.talepler.length()").value(2)).andExpect(jsonPath("$.talepler[0].kaynakSicil").value("D003"));
    }
}
