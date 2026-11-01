package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.kural.Siddet;

import tools.jackson.databind.JsonNode;

/** T1.8 (FR-2.2/2.9, AK-2.7, OK-44b/OK-45): 3 hazır profil, etiketli; her dayanak projedeki kaynak metninde geçer; değerler aralık içi ve tutarlı; yasal/uyum iddiası yok. */
class HazirProfillerTest extends ProfilTestTabani {

    private static final Pattern YASAK = Pattern.compile("yasal|uyumlu|onaylı|mevzuata uygun", Pattern.CASE_INSENSITIVE);

    @Autowired
    ProfilServisi servis;

    @Autowired
    HazirProfiller hazirlar;

    @Test
    void uc_profil_mulgaMd21Yok_hepsiEtiketli() throws Exception {
        Tarayici y = yonetici();
        JsonNode liste = JSON.readTree(y.get("/api/profil/hazir").andReturn().getResponse().getContentAsString());

        assertThat(liste).hasSize(3);
        assertThat(liste.findValuesAsString("kod")).containsExactly("varsayilan", "ab-2005-47", "4857-siki-haftalik");
        for (JsonNode p : liste) {
            assertThat(p.get("etiket").asString()).isEqualTo("Örnek başlangıç — kurum mevzuatıyla doğrulanmalı. Karar destek aracıdır.");
            assertThat(p.get("ad").asString()).isNotBlank();
        }
        assertThat(liste.toString()).doesNotContainIgnoringCase("md. 21").doesNotContainIgnoringCase("mülga");
    }

    @Test
    void herDayanak_ilgiliSatirinKaynakMetnindeGecer_ayarlardaDayanakYok() {
        for (HazirProfiller.Profil h : hazirlar.hepsi()) {
            for (var d : h.dayanak().entrySet()) {
                String kaynak = jdbc.queryForList("SELECT kaynak FROM kural_parametre WHERE anahtar = ? UNION ALL SELECT kaynak FROM kural_siddet WHERE kod = ?", String.class, d.getKey(), d.getKey())
                        .stream().findFirst().orElseThrow(() -> new AssertionError(h.kod() + ": dayanaklı anahtar katalogda yok: " + d.getKey()));
                assertThat(kaynak).as("%s / %s", h.kod(), d.getKey()).contains(d.getValue());
            }
            // değişen her parametre/şiddet için dayanak yazılmış
            h.oneri().parametreler().keySet().forEach(k -> assertThat(h.dayanak()).as("%s / %s dayanaksız", h.kod(), k).containsKey(k));
            h.oneri().siddetler().keySet().forEach(k -> assertThat(h.dayanak()).as("%s / %s dayanaksız", h.kod(), k).containsKey(k));
            assertThat(h.oneri().ayarlar()).as("uygulama ayarlarının kaynak metni yok: öneri/dayanak taşıyamaz").isEmpty();
        }
    }

    @Test
    void degerlerAralikIcinde_ciraprazDenetimdenGecer_etiketVeAciklamaDolu() {
        for (HazirProfiller.Profil h : hazirlar.hepsi()) {
            ProfilPaketi paket = servis.hazir(h.kod());
            Map<String, Double> sayilar = new HashMap<>();
            for (var p : paket.parametreler().entrySet()) {
                var satir = jdbc.queryForMap("SELECT tip, alt_sinir, ust_sinir FROM kural_parametre WHERE anahtar = ?", p.getKey());
                if (satir.get("tip").equals("SAYI") && !p.getValue().isEmpty()) {
                    BigDecimal v = new BigDecimal(p.getValue());
                    assertThat(v).as("%s / %s aralık", h.kod(), p.getKey()).isBetween((BigDecimal) satir.get("alt_sinir"), (BigDecimal) satir.get("ust_sinir"));
                    sayilar.put(p.getKey(), v.doubleValue());
                }
            }
            Map<String, Siddet> siddetler = new HashMap<>();
            paket.siddetler().forEach((k, s) -> siddetler.put(k, Siddet.valueOf(s)));
            assertThat(CaprazDenetim.tumu(sayilar, siddetler)).as("%s çapraz denetim", h.kod()).isEmpty();
            assertThat(h.etiket()).isNotBlank();
            assertThat(h.aciklama()).isNotBlank();
        }
    }

    @Test
    void yasakliSozcukTaramasi_jsonVeServisMetinleri() throws Exception {
        for (HazirProfiller.Profil h : hazirlar.hepsi()) {
            String metin = String.join(" ", h.ad(), h.aciklama(), h.etiket(), String.join(" ", h.dayanak().values()));
            assertThat(YASAK.matcher(metin).find()).as("%s: yasaklı sözcük", h.kod()).isFalse();
        }
        for (String kaynak : List.of("ProfilPaketi.java", "ProfilServisi.java")) {
            String icerik = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/makinistatama/parametre", kaynak));
            // yalnız kullanıcıya görünen sabit etiketler taranır (kod yorumlarında "doğrulama" gibi sözcükler geçebilir)
            for (String satir : icerik.split("\n")) {
                if (satir.contains("ETIKET =") || satir.contains("kurum mevzuatıyla")) {
                    assertThat(YASAK.matcher(satir).find()).as(satir).isFalse();
                }
            }
        }
    }

    @Test
    void varsayilanProfil_veritabaniVarsayilanSutunlariyla_ayni_vePaketDogrudanOnizlenebilir() throws Exception {
        ProfilPaketi p = servis.hazir("varsayilan");

        jdbc.queryForList("SELECT anahtar, varsayilan FROM kural_parametre").forEach(r -> assertThat(p.parametreler().get(r.get("anahtar"))).isEqualTo(r.get("varsayilan")));
        jdbc.queryForList("SELECT kod, varsayilan FROM kural_siddet").forEach(r -> assertThat(p.siddetler().get(r.get("kod"))).isEqualTo(r.get("varsayilan")));
        jdbc.queryForList("SELECT anahtar, varsayilan FROM uygulama_ayar").forEach(r -> assertThat(p.ayarlar().get(r.get("anahtar")).intValue()).isEqualTo(r.get("varsayilan")));
        assertThat(p.ozelKurallar()).isEmpty();

        // taze kurulumda (tohum = varsayılan) varsayılan profil önizlemede hiçbir şeyi değiştirmez
        Tarayici y = yonetici();
        JsonNode o = JSON.readTree(y.get("/api/profil/hazir/varsayilan").andReturn().getResponse().getContentAsString());
        JsonNode onizleme = onizle(y, (tools.jackson.databind.node.ObjectNode) o);
        assertThat(onizleme.get("ozet").get("degisir").asInt()).isZero();
        assertThat(onizleme.get("ozet").get("gecersiz").asInt()).isZero();
    }

    @Test
    void ab_ve_4857_profilleri_beklenenFarklar() {
        ProfilPaketi ab = servis.hazir("ab-2005-47");
        assertThat(ab.parametreler()).containsEntry("dinlenme.ev_saat", "12").containsEntry("dinlenme.dis_merkez_saat", "8").containsEntry("hat.ara_max_ay", "12");
        assertThat(ab.dayanak()).containsEntry("dinlenme.ev_saat", "AB 12");

        ProfilPaketi sk = servis.hazir("4857-siki-haftalik");
        assertThat(sk.siddetler()).containsEntry("K-14", "ENGEL");
        assertThat(sk.parametreler()).containsEntry("haftalik.max_saat", "45").containsEntry("haftalik.normal_saat", "45");
    }
}
