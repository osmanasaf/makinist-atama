package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** F3b MK-86 (FR-04/05/08): KONDUKTOR rolü, rol-tür kapısı, tür bazlı aday havuzu, kondüktör kuralları. */
class KonduktorAtamaTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    Tarayici t;
    long yolcuTreni;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        yolcuTreni = v.sefer("E101", GUN, "08:00", "09:30"); // Ankara -> Eskişehir
        jdbc.update("UPDATE sefer SET tren_turu = 'YOLCU' WHERE id = ?", yolcuTreni);
        t = atamaci();
    }

    private static String govde(long makinistId, String rol) {
        return "{\"makinistId\":" + makinistId + ",\"rol\":\"" + rol + "\"}";
    }

    @Test
    void asilMakinistVeKonduktorBirlikte_ikiSatir_ikisiDeKuralKontrollu() throws Exception {
        long m1 = v.makinist("D001");
        long c1 = v.konduktor("K001");

        t.post("/api/atama/sefer/" + yolcuTreni, govde(m1, "ASIL")).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("KONDUKTOR"))
                .andExpect(jsonPath("$.sicil").value("K001"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, yolcuTreni)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT yeni FROM degisiklik_kaydi WHERE tur = 'ATAMA' ORDER BY id", String.class)).containsExactly("ASIL:D001", "KONDUKTOR:K001");
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE yeni = 'KONDUKTOR:K001'", String.class)).contains("\"kontrolEdilenKodlar\"");
    }

    @Test
    void ikinciKonduktor_ayniKisi409_baskaKisiDegistir_nedenZorunlu() throws Exception {
        long c1 = v.konduktor("K001");
        long c2 = v.konduktor("K002");
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isCreated());

        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("ZATEN_ATANMIS"));
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c2, "KONDUKTOR")).andExpect(status().isBadRequest()); // seferde en çok bir kondüktör: değiştirme nedensiz olmaz
        t.post("/api/atama/sefer/" + yolcuTreni, "{\"makinistId\":" + c2 + ",\"rol\":\"KONDUKTOR\",\"neden\":\"IZIN\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.sicil").value("K002"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ? AND rol = 'KONDUKTOR'", Integer.class, yolcuTreni)).isEqualTo(1);
    }

    @Test
    void rolTurKapisi_422_PERSONEL_TURU_UYMUYOR_butunYonler() throws Exception {
        long m1 = v.makinist("D001");
        long c1 = v.konduktor("K001");

        t.post("/api/atama/sefer/" + yolcuTreni, govde(m1, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("PERSONEL_TURU_UYMUYOR"));
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "ASIL")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("PERSONEL_TURU_UYMUYOR"));
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "YARDIMCI")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("PERSONEL_TURU_UYMUYOR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
    }

    @Test
    void yolcuOlarakIkiTurDeGider_belgeSorulmaz() throws Exception {
        long c1 = v.konduktor("K001");
        jdbc.update("UPDATE makinist SET ehliyet_bitis = '2020-01-01' WHERE id = ?", c1); // belge geçersiz: YOLCU görevinde sorulmaz
        long m1 = v.makinist("D001", false, "AKTIF"); // hat yetkisi yok

        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "YOLCU")).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + yolcuTreni, govde(m1, "YOLCU")).andExpect(status().isCreated());
    }

    @Test
    void adaylar_turaGore_asilKonduktorsuzAyni_konduktorYalnizKonduktorler() throws Exception {
        v.makinist("D001");
        v.makinist("D002");
        v.konduktor("K001");
        v.konduktor("K002");

        String asil = t.get("/api/atama/sefer/" + yolcuTreni + "/adaylar?rol=ASIL").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String yardimci = t.get("/api/atama/sefer/" + yolcuTreni + "/adaylar?rol=YARDIMCI").andReturn().getResponse().getContentAsString();
        String kond = t.get("/api/atama/sefer/" + yolcuTreni + "/adaylar?rol=KONDUKTOR").andReturn().getResponse().getContentAsString();
        String yolcu = t.get("/api/atama/sefer/" + yolcuTreni + "/adaylar?rol=YOLCU").andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(asil, "$[*].sicil")).containsExactly("D001", "D002");
        assertThat((List<String>) JsonPath.read(yardimci, "$[*].sicil")).containsExactly("D001", "D002");
        assertThat((List<String>) JsonPath.read(kond, "$[*].sicil")).containsExactly("K001", "K002");
        assertThat((List<String>) JsonPath.read(yolcu, "$[*].sicil")).containsExactlyInAnyOrder("D001", "D002", "K001", "K002");
        assertThat((List<Boolean>) JsonPath.read(kond, "$[*].uygun")).containsOnly(true); // bröve sorulmaz: kondüktör yolcu trenine ihlalsiz aday
    }

    @Test
    void yukTrenineKonduktorElleAtanir_uyarisiz() throws Exception {
        jdbc.update("UPDATE sefer SET tren_turu = 'YUK' WHERE id = ?", yolcuTreni);
        long c1 = v.konduktor("K001");
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isCreated()).andExpect(jsonPath("$.uyarilar.length()").value(0));
    }

    @Test
    void konduktorBelgesiBitmis_422_kisiselEmniyetBelgesi_ve_aday_uygunDegil() throws Exception {
        long c1 = v.konduktor("K001");
        jdbc.update("UPDATE makinist SET ehliyet_bitis = '2026-10-13' WHERE id = ?", c1); // varış 14 Eki

        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.kod").value("KURAL_IHLALI"))
                .andExpect(jsonPath("$.ihlaller[0].kod").value("K-02")).andExpect(jsonPath("$.ihlaller[0].metin").value(containsString("Kişisel emniyet belgesi")));
        t.get("/api/atama/sefer/" + yolcuTreni + "/adaylar?rol=KONDUKTOR").andExpect(jsonPath("$[0].uygun").value(false));
    }

    @Test
    void konduktorCakisanSeferde_K10_ENGEL_zamanKurallariAyni() throws Exception {
        long c1 = v.konduktor("K001");
        long ikinci = v.sefer("E102", GUN, "08:30", "10:00");
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + ikinci, govde(c1, "KONDUKTOR")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.ihlaller[0].kod").value("K-10"));
    }

    @Test
    void kaldir_konduktorRolu_genelYoldan() throws Exception {
        long c1 = v.konduktor("K001");
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isCreated());
        t.delete("/api/atama/sefer/" + yolcuTreni + "?rol=KONDUKTOR&neden=IZIN").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA_KALDIR'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rolTurSqlDegismezi_sifir() throws Exception {
        long m1 = v.makinist("D001");
        long c1 = v.konduktor("K001");
        t.post("/api/atama/sefer/" + yolcuTreni, govde(m1, "ASIL")).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + yolcuTreni, govde(c1, "KONDUKTOR")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM atama a JOIN makinist m ON m.id = a.makinist_id
                WHERE a.rol <> 'YOLCU' AND (a.rol = 'KONDUKTOR') <> (m.tur = 'KONDUKTOR')""", Integer.class)).isZero();
    }
}
