package com.makinistatama.makinist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/**
 * T13.3: izin talebi / onayı (FR-11). TALEP atamayı engellemez ve atamayı kırmızı yapmaz; ONAY sonrası K-09 devreye girer (mevcut atama kırmızı, yeni atama 422);
 * RED etkisiz. Karar bir kez verilir (409). Onay yanıtı çakışan atama taşımaz (MK-15): etki ayrı GET ile okunur.
 */
class IzinTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    AtamaVeri v;
    long makinist;
    long sefer;
    Tarayici t;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        makinist = v.makinist("D001");
        sefer = v.seferEv("T1", GUN, "08:00", "10:00");
        v.atama(sefer, makinist, "ASIL");
        t = atamaci();
    }

    private String talepGovdesi() {
        return "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-10-13\",\"bitis\":\"2026-10-15\",\"tur\":\"YILLIK\"}";
    }

    private long talepAc() throws Exception {
        t.post("/api/izin", talepGovdesi()).andExpect(status().isCreated()).andExpect(jsonPath("$.durum").value("TALEP")).andExpect(jsonPath("$.sicil").value("D001"));
        return jdbc.queryForObject("SELECT max(id) FROM izin", Long.class);
    }

    @Test
    void talepDurumundaIzin_atamayiEtkilemez_onayKirmiziYapar_atamaYapilamaz() throws Exception {
        long izin = talepAc();

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].kirmizi").value(false));
        long yeniSefer = v.seferEv("T2", GUN, "12:00", "14:00");
        t.post("/api/atama/sefer/" + yeniSefer, AtamaVeri.ataGovdesi(makinist)).andExpect(status().isCreated());

        yonetici().post("/api/izin/" + izin + "/onay", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("ONAY")).andExpect(jsonPath("$.kararVeren").value("Yöne Tici"))
                .andExpect(jsonPath("$.atamalar").doesNotExist()); // MK-15: yanıt çakışan atama taşımaz

        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].kirmizi").value(true))
                .andExpect(jsonPath("$.satirlar[0].makinistler[0].kirmiziNedenler[0]").value(org.hamcrest.Matchers.containsString("zin")));
        long sonSefer = v.seferEv("T3", GUN, "16:00", "17:00");
        t.post("/api/atama/sefer/" + sonSefer, AtamaVeri.ataGovdesi(makinist)).andExpect(status().isUnprocessableEntity());
        // etki ayrı uçtan okunur
        t.get("/api/atama/makinist/" + makinist + "/izin-etkisi?bas=2026-10-13&bit=2026-10-15").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void red_etkisiz_ikinciKarar409_kararVerenKayitta() throws Exception {
        long izin = talepAc();

        t.post("/api/izin/" + izin + "/red", "").andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("RED"));
        t.get("/api/atama/gun?tarih=" + GUN).andExpect(jsonPath("$.satirlar[0].kirmizi").value(false));
        yonetici().post("/api/izin/" + izin + "/onay", "").andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("IZIN_KARARLI"));
        t.post("/api/izin/" + izin + "/red", "").andExpect(status().isConflict());

        var kayit = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'IZIN_RED'");
        assertThat(kayit).containsEntry("kullanici_adi", "Ata Maci").containsEntry("eski", "TALEP").containsEntry("yeni", "RED").containsEntry("makinist_id", makinist);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur IN ('IZIN_TALEP', 'IZIN_RED')", Integer.class)).isEqualTo(2);
    }

    @Test
    void liste_durumSuzgeci_dogrulama_404_yetki() throws Exception {
        long izin = talepAc();
        t.post("/api/izin", "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-11-02\",\"bitis\":\"2026-11-03\",\"tur\":\"RAPOR\"}").andExpect(status().isCreated());
        yonetici().post("/api/izin/" + izin + "/onay", "").andExpect(status().isOk());

        t.get("/api/izin").andExpect(jsonPath("$", hasSize(2)));
        t.get("/api/izin?durum=TALEP").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].tur").value("RAPOR")).andExpect(jsonPath("$[0].adSoyad").value("Demo Makinist D001"));
        t.get("/api/izin?durum=ONAY").andExpect(jsonPath("$", hasSize(1)));

        t.post("/api/izin", "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-11-05\",\"bitis\":\"2026-11-04\",\"tur\":\"YILLIK\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.bitis").exists());
        t.post("/api/izin", "{\"makinistId\":99999,\"baslangic\":\"2026-11-05\",\"bitis\":\"2026-11-06\",\"tur\":\"YILLIK\"}").andExpect(status().isNotFound());
        t.post("/api/izin", "{\"baslangic\":\"2026-11-05\",\"bitis\":\"2026-11-06\",\"tur\":\"YILLIK\"}").andExpect(status().isBadRequest());
        t.post("/api/izin/99999/onay", "").andExpect(status().isNotFound());
        new Tarayici(mvc).get("/api/izin").andExpect(status().isUnauthorized());
        yonetici().post("/api/izin/" + izin + "/red", "").andExpect(status().isConflict()); // YONETICI de karar uçlarını görür; karar zaten verilmiş
    }

    // ---- M4 review W5, OK-4, QA BUG-15

    @Test
    void w5_onayVeRedYarisi_birBasari_digeri409_birKararKaydi() throws Exception {
        Tarayici a = yonetici();
        Tarayici b = girisli("yonetici2@ornek.test", "Yöne Tici İki", com.makinistatama.kullanici.Rol.YONETICI);
        int cift200 = 0;
        for (int tur = 0; tur < 15; tur++) {
            jdbc.update("DELETE FROM izin");
            long izin = talepAc();
            CyclicBarrier bariyer = new CyclicBarrier(2);
            ExecutorService havuz = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> onay = havuz.submit(() -> {
                    bariyer.await();
                    return a.post("/api/izin/" + izin + "/onay", "").andReturn().getResponse().getStatus();
                });
                Future<Integer> red = havuz.submit(() -> {
                    bariyer.await();
                    return b.post("/api/izin/" + izin + "/red", "").andReturn().getResponse().getStatus();
                });
                List<Integer> durumlar = List.of(onay.get(), red.get());
                assertThat(durumlar).as("tur %d", tur).containsExactlyInAnyOrder(200, 409);
                if (durumlar.stream().filter(d -> d == 200).count() > 1) {
                    cift200++;
                }
            } finally {
                havuz.shutdownNow();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur IN ('IZIN_ONAY', 'IZIN_RED') AND varlik_id = ?", Integer.class, izin)).as("tur %d", tur).isEqualTo(1);
            String durum = jdbc.queryForObject("SELECT durum FROM izin WHERE id = ?", String.class, izin);
            assertThat(jdbc.queryForObject("SELECT tur FROM degisiklik_kaydi WHERE tur IN ('IZIN_ONAY', 'IZIN_RED') AND varlik_id = ?", String.class, izin)).isEqualTo("IZIN_" + (durum.equals("ONAY") ? "ONAY" : "RED"));
        }
        assertThat(cift200).isZero();
    }

    @Test
    void ok4_kimseKendiTalebiniOnaylayamaz_403_baskasiOnaylar_redSerbest() throws Exception {
        long izin = talepAc(); // ATAMACI açtı
        t.post("/api/izin/" + izin + "/onay", "").andExpect(status().isForbidden()).andExpect(jsonPath("$.kod").value("KENDI_TALEBI"));
        assertThat(jdbc.queryForObject("SELECT durum FROM izin WHERE id = ?", String.class, izin)).isEqualTo("TALEP");
        Tarayici y = yonetici();
        y.post("/api/izin/" + izin + "/onay", "").andExpect(status().isOk());

        // YONETICI de kendi talebini onaylayamaz; ATAMACI onaylar
        y.post("/api/izin", "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-11-02\",\"bitis\":\"2026-11-03\",\"tur\":\"RAPOR\"}").andExpect(status().isCreated());
        long ikinci = jdbc.queryForObject("SELECT max(id) FROM izin", Long.class);
        y.post("/api/izin/" + ikinci + "/onay", "").andExpect(status().isForbidden());
        t.post("/api/izin/" + ikinci + "/onay", "").andExpect(status().isOk());

        // başka bir ATAMACI da onaylayabilir; kendi talebini geri çekmek (RED) serbesttir
        Tarayici digeri = girisli("atamaci2@ornek.test", "Ata Maci İki", com.makinistatama.kullanici.Rol.ATAMACI);
        digeri.post("/api/izin", "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-12-02\",\"bitis\":\"2026-12-03\",\"tur\":\"YILLIK\"}").andExpect(status().isCreated());
        long ucuncu = jdbc.queryForObject("SELECT max(id) FROM izin", Long.class);
        digeri.post("/api/izin/" + ucuncu + "/red", "").andExpect(status().isOk());
    }

    @Test
    void bug15_cakisanIzinTalebi409_redOlanVeBitisikOlanCakismaz() throws Exception {
        long izin = talepAc(); // 13-15 Eki TALEP
        String ic = "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-10-15\",\"bitis\":\"2026-10-16\",\"tur\":\"YILLIK\"}";
        t.post("/api/izin", ic).andExpect(status().isConflict()).andExpect(jsonPath("$.kod").value("IZIN_CAKISMASI"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("15.10.2026 – 16.10.2026"))); // 15 Eki iki uç dahil: çakışır
        yonetici().post("/api/izin/" + izin + "/onay", "").andExpect(status().isOk());
        t.post("/api/izin", ic).andExpect(status().isConflict()); // ONAY ile de çakışır
        t.post("/api/makinist/" + makinist + "/izin", "{\"baslangic\":\"2026-10-14\",\"bitis\":\"2026-10-14\",\"tur\":\"YILLIK\"}").andExpect(status().isConflict()); // karttan doğrudan ekleme de
        t.post("/api/izin", "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-10-16\",\"bitis\":\"2026-10-17\",\"tur\":\"YILLIK\"}").andExpect(status().isCreated()); // bitişik gün çakışmaz
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin", Integer.class)).isEqualTo(2);

        // RED olan izin başkasının yolunu kapatmaz
        long red = jdbc.queryForObject("SELECT max(id) FROM izin", Long.class);
        yonetici().post("/api/izin/" + red + "/red", "").andExpect(status().isOk());
        t.post("/api/izin", "{\"makinistId\":" + makinist + ",\"baslangic\":\"2026-10-16\",\"bitis\":\"2026-10-17\",\"tur\":\"RAPOR\"}").andExpect(status().isCreated());
    }
}
