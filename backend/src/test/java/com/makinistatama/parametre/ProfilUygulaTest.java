package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.kural.Siddet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** T1.7 (FR-2.4/2.5, AK-2.4..2.6, 2.8, 2.10): seçmeli, tüm-ya-hiç, nihai durum denetimi, denetim kayıtları, canlı etki. */
class ProfilUygulaTest extends ProfilTestTabani {

    @Autowired
    ParametreServisi parametreler;

    private int sayi(String sql, Object... a) {
        return jdbc.queryForObject(sql, Integer.class, a);
    }

    private String deger(String anahtar) {
        return jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = ?", String.class, anahtar);
    }

    @Test
    void besDegisirinIkisiSecilince_yalnizIkisiYazilir_denetimUcPlusBir() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        paket.put("ad", "Deneme profili");
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");
        bolum(paket, "parametreler").put("gece.max_saat", "6");
        bolum(paket, "parametreler").put("yedek.oran", "0.2");
        bolum(paket, "siddetler").put("K-20", "KAPALI");
        bolum(paket, "ayarlar").put("gordum.kalem_gun", 10);

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat", "SIDDET:K-20", "AYAR:gordum.kalem_gun"), "Kurum kararı"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.yazilan").value(3)).andExpect(jsonPath("$.atlanan").value(0));

        assertThat(deger("dinlenme.ev_saat")).isEqualTo("8");
        assertThat(deger("gece.max_saat")).as("seçilmeyen satır mevcut değerinde").isEqualTo("7.5");
        assertThat(deger("yedek.oran")).isEqualTo("0.10");
        assertThat(parametreler.yukle().siddet("K-20")).isEqualTo(Siddet.KAPALI);
        assertThat(sayi("SELECT deger FROM uygulama_ayar WHERE anahtar = 'gordum.kalem_gun'")).isEqualTo(10);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI'")).isEqualTo(3);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PROFIL_UYGULA'")).isEqualTo(1);
        var ev = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND ayrinti LIKE '%dinlenme.ev_saat%'");
        assertThat(ev).containsEntry("eski", "11").containsEntry("yeni", "8").containsEntry("neden_notu", "Profil: Deneme profili — Kurum kararı");
        var ozet = jdbc.queryForMap("SELECT * FROM degisiklik_kaydi WHERE tur = 'PROFIL_UYGULA'");
        assertThat((String) ozet.get("ayrinti")).contains("Deneme profili").contains("\"secilen\":3").contains("\"yazilan\":3");
        assertThat(ozet).containsEntry("kullanici_adi", "Yöne Tici");
    }

    @Test
    void ikinciKezUygulama_sifirYazma_kayitYok_yeniDegerCanli() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");
        String govde = uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat"), "n");
        y.post("/api/profil/uygula", govde).andExpect(status().isOk());
        assertThat(parametreler.yukle().dakika("dinlenme.ev_saat")).as("AK-2.10: ilk istekte yeni değer").isEqualTo(480);
        int kayit = sayi("SELECT count(*) FROM degisiklik_kaydi");

        y.post("/api/profil/uygula", govde).andExpect(status().isOk()).andExpect(jsonPath("$.yazilan").value(0)).andExpect(jsonPath("$.atlanan").value(1));

        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).as("AYNI: yeni kayıt yok").isEqualTo(kayit);
    }

    @Test
    void secilenGecersiz_422_hicbiriYazilmaz_satirlarDoner() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");
        bolum(paket, "parametreler").put("gece.gorev_orani", "1.5");

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat", "PARAMETRE:gece.gorev_orani"), "n")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.kod").value("PROFIL_GECERSIZ")).andExpect(jsonPath("$.satirlar[0].anahtar").value("gece.gorev_orani"))
                .andExpect(jsonPath("$.satirlar[0].neden").isNotEmpty());

        assertThat(deger("dinlenme.ev_saat")).isEqualTo("11");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).isZero();
        // seçilmeyen geçersiz satır uygulamayı engellemez
        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat"), "n")).andExpect(status().isOk());
        assertThat(deger("dinlenme.ev_saat")).isEqualTo("8");
    }

    @Test
    void caprazTutarlilik_nihaiDurumda_birlikteKabul_ayriAyriRed() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("nobet.ayirma_saat", "12"); // dış merkez 11 iken tek başına reddedilirdi
        bolum(paket, "parametreler").put("dinlenme.dis_merkez_saat", "12");

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:nobet.ayirma_saat"), "n")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.kod").value("PROFIL_GECERSIZ")).andExpect(jsonPath("$.detail").value(containsString("dış merkez dinlenme süresini")));
        assertThat(deger("nobet.ayirma_saat")).isEqualTo("4");

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:nobet.ayirma_saat", "PARAMETRE:dinlenme.dis_merkez_saat"), "n")).andExpect(status().isOk())
                .andExpect(jsonPath("$.yazilan").value(2));
        assertThat(deger("nobet.ayirma_saat")).isEqualTo("12");
    }

    @Test
    void aktarma20dk_ayirma0_2saat_422_hicbiriYazilmaz() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("nobet.ayirma_saat", "0.2");
        bolum(paket, "parametreler").put("gece.max_saat", "6");

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:nobet.ayirma_saat", "PARAMETRE:gece.max_saat"), "n")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.kod").value("PROFIL_GECERSIZ")).andExpect(jsonPath("$.detail").value(containsString("altında olmalı")));

        assertThat(deger("nobet.ayirma_saat")).isEqualTo("4");
        assertThat(deger("gece.max_saat")).isEqualTo("7.5");
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).isZero();
    }

    @Test
    void nedenBosVeyaYok_400_bilinmeyenSatir_400() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat"), "  ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alanlar.neden").exists());
        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat"), null)).andExpect(status().isBadRequest());
        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:yok.satir"), "n")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.alanlar.secilen").exists());

        assertThat(deger("dinlenme.ev_saat")).isEqualTo("11");
    }

    @Test
    void denetimRaporundaGorunur() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        paket.put("ad", "Rapor profili");
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");
        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat"), "n")).andExpect(status().isOk());

        String rapor = y.get("/api/degisiklik?bas=" + java.time.LocalDate.now().minusDays(1) + "&bit=" + java.time.LocalDate.now().plusDays(1)).andReturn().getResponse().getContentAsString();

        assertThat(rapor).contains("Kural profili uygulandı").contains("Ayar değişti: Bağlı garda dinlenme süresi");
        JsonNode parametreBolumu = null;
        for (JsonNode b : JSON.readTree(rapor).get("bolumler")) {
            if (b.get("kod").asString().equals("PARAMETRE")) {
                parametreBolumu = b;
            }
        }
        assertThat(parametreBolumu.get("satirlar").toString()).contains("Kural profili uygulandı");
    }

    @Test
    void ozelKural_aynAdVarsaGuncellenir_yoksaEklenir_kayitlar() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Günlük sınır", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        ObjectNode paket = disaAktar(y);
        ArrayNode kurallar = (ArrayNode) paket.get("ozelKurallar");
        ((ObjectNode) kurallar.get(0)).put("esik", 9);
        ObjectNode yeni = ((ObjectNode) kurallar.get(0)).deepCopy();
        yeni.put("ad", "Yeni kural");
        kurallar.add(yeni);

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("OZEL_KURAL:gunluk sinir", "OZEL_KURAL:yeni kural"), "n")).andExpect(status().isOk())
                .andExpect(jsonPath("$.yazilan").value(2));

        assertThat(sayi("SELECT count(*) FROM ozel_kural")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT esik::int FROM ozel_kural WHERE id = 1", Integer.class)).isEqualTo(9);
        assertThat(sayi("SELECT versiyon FROM ozel_kural WHERE id = 1")).as("güncelleme sürümü artırır").isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'KURAL_GUNCELLE'")).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'KURAL_EKLE'")).isEqualTo(2); // ilk kurulum + profildeki yeni kural
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PROFIL_UYGULA'")).isEqualTo(1);
    }

    @Test
    void ozelKuralGecersizSecilirse_tumuHicYazilmaz_profildeOlmayanKuralaDokunulmaz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Eski kural", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        ObjectNode paket = disaAktar(y);
        ArrayNode kurallar = (ArrayNode) paket.get("ozelKurallar");
        ObjectNode bozuk = ((ObjectNode) kurallar.get(0)).deepCopy();
        bozuk.put("ad", "Garsiz");
        ((ArrayNode) bozuk.get("kapsam").get("kalkisGarlari")).removeAll().add("Atlantis");
        kurallar.removeAll().add(bozuk); // "Eski kural" profilde yok: dokunulmaz
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");
        int once = sayi("SELECT count(*) FROM degisiklik_kaydi");

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat", "OZEL_KURAL:garsiz"), "n")).andExpect(status().isUnprocessableEntity());

        assertThat(deger("dinlenme.ev_saat")).isEqualTo("11");
        assertThat(sayi("SELECT count(*) FROM ozel_kural")).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi")).isEqualTo(once);

        y.post("/api/profil/uygula", uygulaGovdesi(paket, List.of("PARAMETRE:dinlenme.ev_saat"), "n")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT siddet FROM ozel_kural WHERE ad = 'Eski kural'", String.class)).as("kapatılmaz, silinmez").isEqualTo("ENGEL");
    }
}
