package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.atama.AtamaVeri;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** T1.6 (FR-2.3, AK-2.2/2.3/2.8): önizleme yazmaz; gidiş-dönüş hepsi AYNI; geçersiz/bilinmeyen/eksik satırlar; özel kural ad eşleşmesi. */
class ProfilOnizleTest extends ProfilTestTabani {

    private int kayitSayisi() {
        return jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class);
    }

    @Test
    void gidisDonus_hepsiAyni_degisirSifir_yazmaYok() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Günlük sınır", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        int once = kayitSayisi();
        ObjectNode paket = disaAktar(y);

        JsonNode o = onizle(y, paket);

        assertThat(o.get("ozet").get("degisir").asInt()).isZero();
        assertThat(o.get("ozet").get("gecersiz").asInt()).isZero();
        assertThat(o.get("ozet").get("ayni").asInt()).isEqualTo(32 + 22 + 34 + 1);
        for (JsonNode s : o.get("satirlar")) {
            assertThat(s.get("durum").asString()).as(s.get("kimlik").asString()).isEqualTo("AYNI");
        }
        assertThat(o.get("uyarilar")).isEmpty();
        assertThat(kayitSayisi()).as("önizleme denetim kaydı üretmez").isEqualTo(once);
    }

    @Test
    void degisenSatir_mevcutYeniKaynakGuvenilirlik_yazmaYok() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("dinlenme.ev_saat", "8");
        bolum(paket, "siddetler").put("K-20", "KAPALI");
        bolum(paket, "ayarlar").put("gordum.kalem_gun", 10);

        JsonNode o = onizle(y, paket);

        JsonNode s = satir(o, "PARAMETRE:dinlenme.ev_saat");
        assertThat(s.get("durum").asString()).isEqualTo("DEGISIR");
        assertThat(s.get("mevcut").asString()).isEqualTo("11");
        assertThat(s.get("yeni").asString()).isEqualTo("8");
        assertThat(s.get("kaynak").asString()).isNotBlank();
        assertThat(s.get("guvenilirlik").asString()).isNotBlank();
        assertThat(satir(o, "SIDDET:K-20").get("yeni").asString()).isEqualTo("KAPALI");
        assertThat(satir(o, "AYAR:gordum.kalem_gun").get("durum").asString()).isEqualTo("DEGISIR");
        assertThat(o.get("ozet").get("degisir").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT deger FROM kural_parametre WHERE anahtar = 'dinlenme.ev_saat'", String.class)).isEqualTo("11");
        assertThat(kayitSayisi()).isZero();
    }

    @Test
    void gecersizBilinmeyenEksik_satirDurumlari() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        ObjectNode p = bolum(paket, "parametreler");
        p.put("dinlenme.ev_saat", "abc");
        p.put("gece.gorev_orani", "1.5"); // aralık dışı
        p.put("hat.ara_max_ay", "1E40000000"); // üstel yazım: metne dökülmeden reddedilir
        p.put("belge.sinav_gecerlilik_ay", "60.5"); // tam sayı olmalı
        p.put("yok.anahtar", "1");
        p.remove("yedek.min");
        ObjectNode s = bolum(paket, "siddetler");
        s.put("K-21", "BILMEM");
        s.put("K-10", "KAPALI");
        s.put("K-99", "ENGEL");
        bolum(paket, "ayarlar").put("makinist.gorunur_gun", 9999);
        bolum(paket, "ayarlar").put("gordum.kalem_gun", 1.5);
        bolum(paket, "ayarlar").put("yok.ayar", 1);

        JsonNode o = onizle(y, paket);

        assertThat(satir(o, "PARAMETRE:dinlenme.ev_saat").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "PARAMETRE:dinlenme.ev_saat").get("neden").asString()).isNotBlank();
        assertThat(satir(o, "PARAMETRE:gece.gorev_orani").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "PARAMETRE:hat.ara_max_ay").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "PARAMETRE:hat.ara_max_ay").get("neden").asString()).contains("basamak");
        assertThat(satir(o, "PARAMETRE:belge.sinav_gecerlilik_ay").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "PARAMETRE:yok.anahtar").get("durum").asString()).isEqualTo("BILINMEYEN");
        assertThat(satir(o, "PARAMETRE:yedek.min").get("durum").asString()).isEqualTo("PROFILDE_YOK");
        assertThat(satir(o, "PARAMETRE:yedek.min").get("mevcut").asString()).as("mevcut korunur").isNotBlank();
        assertThat(satir(o, "SIDDET:K-21").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "SIDDET:K-10").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "SIDDET:K-99").get("durum").asString()).isEqualTo("BILINMEYEN");
        assertThat(satir(o, "AYAR:makinist.gorunur_gun").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "AYAR:gordum.kalem_gun").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "AYAR:yok.ayar").get("durum").asString()).isEqualTo("BILINMEYEN");
        assertThat(o.get("ozet").get("profildeYok").asInt()).isEqualTo(1);
        assertThat(kayitSayisi()).isZero();
    }

    @Test
    void capraz_uyari_nihaiDurumdan() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        bolum(paket, "parametreler").put("nobet.ayirma_saat", "0.2"); // aktarma 20 dk iken

        JsonNode o = onizle(y, paket);

        assertThat(satir(o, "PARAMETRE:nobet.ayirma_saat").get("durum").asString()).isEqualTo("DEGISIR");
        assertThat(o.get("uyarilar").toString()).contains("Tutarlılık").contains("altında olmalı");
    }

    @Test
    void ozelKural_adEslesmesi_yeniGuncelleAyni_garAdiYoksaGecersiz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Günlük sınır", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        ObjectNode paket = disaAktar(y);
        var kurallar = (tools.jackson.databind.node.ArrayNode) paket.get("ozelKurallar");
        ((ObjectNode) kurallar.get(0)).put("esik", 9); // aynı ad, farklı eşik -> DEGISIR (güncelle)
        ObjectNode yeni = ((ObjectNode) kurallar.get(0)).deepCopy();
        yeni.put("ad", "Yeni kural");
        ((tools.jackson.databind.node.ArrayNode) yeni.get("kapsam").get("kalkisGarlari")).removeAll().add("Ankara");
        kurallar.add(yeni);
        ObjectNode bozuk = ((ObjectNode) kurallar.get(0)).deepCopy();
        bozuk.put("ad", "Olmayan garlı");
        ((tools.jackson.databind.node.ArrayNode) bozuk.get("kapsam").get("kalkisGarlari")).removeAll().add("Atlantis");
        kurallar.add(bozuk);

        JsonNode o = onizle(y, paket);

        assertThat(satir(o, "OZEL_KURAL:gunluk sinir").get("durum").asString()).isEqualTo("DEGISIR");
        assertThat(satir(o, "OZEL_KURAL:gunluk sinir").get("mevcut").asString()).contains("8 s");
        assertThat(satir(o, "OZEL_KURAL:gunluk sinir").get("yeni").asString()).contains("9 s");
        assertThat(satir(o, "OZEL_KURAL:yeni kural").get("durum").asString()).isEqualTo("DEGISIR");
        assertThat(satir(o, "OZEL_KURAL:yeni kural").get("mevcut").isNull()).isTrue();
        JsonNode b = satir(o, "OZEL_KURAL:olmayan garli");
        assertThat(b.get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(b.get("neden").asString()).contains("'Atlantis' garı bu kurulumda tanımlı değil");
        assertThat(o.get("ozet").get("gecersiz").asInt()).as("diğer satırlar etkilenmez").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ozel_kural", Integer.class)).isEqualTo(1);
    }

    @Test
    void ozelKural_51Acik_ilgiliSatirGecersiz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        for (int i = 0; i < 50; i++) {
            y.post("/api/ozel-kural", ozelKuralGovdesi("Kural " + i, "8", "UYARI", v.ankara, v.hat)).andExpect(status().isCreated());
        }
        ObjectNode paket = disaAktar(y);
        ObjectNode ek = ((ObjectNode) paket.get("ozelKurallar").get(0)).deepCopy();
        ek.put("ad", "Elli birinci");
        ((tools.jackson.databind.node.ArrayNode) paket.get("ozelKurallar")).add(ek);

        JsonNode o = onizle(y, paket);

        assertThat(satir(o, "OZEL_KURAL:elli birinci").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(satir(o, "OZEL_KURAL:elli birinci").get("neden").asString()).contains("En çok 50 açık özel kural");
        assertThat(satir(o, "OZEL_KURAL:kural 0").get("durum").asString()).isEqualTo("AYNI");
    }

    @Test
    void ozelKural_ustelEsik_hizlaGecersiz_sunucuSismez() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Günlük sınır", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        ObjectNode paket = disaAktar(y);
        String govde = JSON.writeValueAsString(paket).replace("\"esik\":8", "\"esik\":1E40000000"); // ham metin: JSON ağacı sonsuza çevirirdi
        assertThat(govde).contains("1E40000000");
        long bas = System.nanoTime();

        JsonNode o = JSON.readTree(y.post("/api/profil/onizle", govde).andReturn().getResponse().getContentAsString());

        assertThat(satir(o, "OZEL_KURAL:gunluk sinir").get("durum").asString()).isEqualTo("GECERSIZ");
        assertThat(System.nanoTime() - bas).as("üstel eşik sınırsız metin üretmez").isLessThan(3_000_000_000L);
    }

    @Test
    void ozelKural_ayniAdIkiKez_ikinciGecersiz() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        Tarayici y = yonetici();
        y.post("/api/ozel-kural", ozelKuralGovdesi("Günlük sınır", "8", "ENGEL", v.ankara, v.hat)).andExpect(status().isCreated());
        ObjectNode paket = disaAktar(y);
        var kurallar = (tools.jackson.databind.node.ArrayNode) paket.get("ozelKurallar");
        kurallar.add(kurallar.get(0).deepCopy());

        JsonNode o = onizle(y, paket);

        assertThat(o.get("ozet").get("gecersiz").asInt()).isEqualTo(1);
    }

    @Test
    void eskiSurum_uyari_buyukSurum_400_bozukDosya_400() throws Exception {
        Tarayici y = yonetici();
        ObjectNode paket = disaAktar(y);
        paket.put("profilSurumu", 0);
        assertThat(onizle(y, paket).get("uyarilar").toString()).contains("eski bir biçim sürümü");

        paket.put("profilSurumu", 2);
        y.post("/api/profil/onizle", JSON.writeValueAsString(paket)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("PROFIL_SURUMU"));
        y.post("/api/profil/onizle", "{bozuk").andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"))
                .andExpect(jsonPath("$.detail").value(containsString("JSON")));
        assertThat(List.of(kayitSayisi())).containsExactly(0);
    }
}
