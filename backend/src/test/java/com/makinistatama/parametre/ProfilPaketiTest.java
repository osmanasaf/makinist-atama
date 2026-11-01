package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ProfilPaketi.KapsamAdlari;
import com.makinistatama.parametre.ProfilPaketi.OzelKuralGirdisi;

/** T1.3 (MK-162, saf, DB'siz): profil paketinin JSON gidiş-dönüşü, sürüm/biçim/boyut sınırları, bozuk içerik ve üstel sayı metni. */
class ProfilPaketiTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String kod(Runnable r) {
        try {
            r.run();
        } catch (UygulamaHatasi h) {
            return h.kod();
        }
        return "HATA YOK";
    }

    private static final String BASLIK = "{\"format\":\"makinist-atama-profil\",\"profilSurumu\":1,\"ad\":\"X\"";

    @Test
    void gidisDonus_hicbirAlanKaybolmaz() {
        var kural = new OzelKuralGirdisi("Günlük sınır", "TOPLAM_GOREV_SURESI", "GUN", null, "EN_FAZLA", new BigDecimal("8"),
                new KapsamAdlari(List.of("Ankara–Sivas"), List.of(), List.of("Ankara"), List.of(), List.of("YOLCU"), "GECE", List.of("MAKINIST")), "ENGEL",
                LocalDate.of(2026, 1, 1), null, "gerekçe", "kaynak");
        var paket = new ProfilPaketi(ProfilPaketi.FORMAT, 1, "Ad", "Açıklama", ProfilPaketi.ETIKET, "2026-10-10T07:00:00Z", "Yöne Tici", Map.of("dinlenme.ev_saat", "AB 12"),
                Map.of("dinlenme.ev_saat", "12", "yedek.oran", "0.10"), Map.of("K-14", "ENGEL"), Map.of("gordum.kalem_gun", 7), List.of(kural));

        ProfilPaketi geri = ProfilJson.oku(ProfilJson.yaz(paket));

        assertThat(geri).isEqualTo(paket);
    }

    @Test
    void bilinmeyenUstDuzeyAlanYokSayilir_eksikBolumlerBosGelir() {
        ProfilPaketi p = ProfilJson.oku(b(BASLIK + ",\"gelecekteBirAlan\":{\"x\":1},\"parametreler\":{\"a\":\"1\"}}"));

        assertThat(p.parametreler()).containsExactly(Map.entry("a", "1"));
        assertThat(p.siddetler()).isEmpty();
        assertThat(p.ayarlar()).isEmpty();
        assertThat(p.ozelKurallar()).isEmpty();
        assertThat(p.dayanak()).isEmpty();
    }

    @Test
    void buyukSurum_PROFIL_SURUMU_400_kucukSurumKabul() {
        assertThatThrownBy(() -> ProfilJson.oku(b("{\"format\":\"makinist-atama-profil\",\"profilSurumu\":2}"))).isInstanceOfSatisfying(UygulamaHatasi.class,
                h -> assertThat(h.kod()).isEqualTo("PROFIL_SURUMU"));
        assertThat(ProfilJson.oku(b("{\"format\":\"makinist-atama-profil\",\"profilSurumu\":0}")).profilSurumu()).isZero();
    }

    @Test
    void bozukBosYanlisBicim_GECERSIZ_DOSYA() {
        for (String ham : new String[] { "", "{bozuk", "[1,2]", "{\"profilSurumu\":1}", "{\"format\":\"baska\",\"profilSurumu\":1}", "{\"format\":\"makinist-atama-profil\"}",
                BASLIK + "} fazlalık", "null" }) {
            assertThat(kod(() -> ProfilJson.oku(b(ham)))).as(ham).isEqualTo("GECERSIZ_DOSYA");
        }
    }

    @Test
    void govdeBirMegabaytiAsmaz_okumaSinirlidir() throws Exception {
        byte[] tamam = new byte[ProfilJson.EN_COK_BAYT];
        assertThat(ProfilJson.govde(new ByteArrayInputStream(tamam))).hasSize(ProfilJson.EN_COK_BAYT);
        assertThatThrownBy(() -> ProfilJson.govde(new ByteArrayInputStream(new byte[ProfilJson.EN_COK_BAYT + 1]))).isInstanceOfSatisfying(UygulamaHatasi.class,
                h -> assertThat(h.kod()).isEqualTo("GECERSIZ_DOSYA"));
    }

    @Test
    void girdiSayisiSiniri_400() {
        StringBuilder sb = new StringBuilder(BASLIK).append(",\"parametreler\":{");
        for (int i = 0; i < ProfilJson.EN_COK_GIRDI + 1; i++) {
            sb.append(i == 0 ? "" : ",").append("\"p").append(i).append("\":\"1\"");
        }
        sb.append("}}");
        assertThat(kod(() -> ProfilJson.oku(b(sb.toString())))).isEqualTo("GECERSIZ_DOSYA");

        String kural = "{\"ad\":\"k\"}";
        String yuzBir = String.join(",", java.util.Collections.nCopies(ProfilJson.EN_COK_OZEL_KURAL + 1, kural));
        assertThat(kod(() -> ProfilJson.oku(b(BASLIK + ",\"ozelKurallar\":[" + yuzBir + "]}")))).isEqualTo("GECERSIZ_DOSYA");
    }

    @Test
    void ustelSayi_okuma_asilmaz_degerMetniOlarakKalir() {
        long bas = System.nanoTime();
        ProfilPaketi p = ProfilJson.oku(b(BASLIK + ",\"parametreler\":{\"a\":1E40000000,\"b\":\"1E40000000\",\"c\":\"-1E-40000000\"},\"ayarlar\":{\"x\":1E40000000,\"y\":1.5}}"));

        assertThat(p.parametreler()).containsKeys("a", "b", "c");
        assertThat(p.ayarlar()).containsKeys("x", "y");
        assertThat(System.nanoTime() - bas).as("üstel yazım sınırsız metin/işlem üretmez").isLessThan(2_000_000_000L);
    }

    @Test
    void uygulaGovdesi_paketZorunlu_secilenBosOlabilir() {
        var i = ProfilJson.okuUygula(b("{\"paket\":" + BASLIK + "},\"neden\":\"n\"}"));
        assertThat(i.secilen()).isEmpty();
        assertThat(i.neden()).isEqualTo("n");
        assertThat(kod(() -> ProfilJson.okuUygula(b("{\"neden\":\"n\"}")))).isEqualTo("GECERSIZ_DOSYA");
    }
}
