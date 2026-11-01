package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.kurum.KurumGorselleri;

/**
 * F1 / MK-168 kapı kanıtı: kurum adı + logo okuma ANONİM (giriş sayfası marka gösterir) ve yalnız ad + logo bilgisi döner; yazma uçları anonim 401, atamacı/makinist 403, yönetici 200;
 * /api/kurum altındaki başka her yol anonim 401. Logo yanıtı sabit içerik türü + nosniff + CSP ile verilir.
 */
class KurumYetkiTest extends TestVeritabani {

    private static final String AD_GOVDESI = "{\"ad\":\"Örnek İşletme (demo)\"}";

    private int durum(Tarayici t, org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> istek) throws Exception {
        return t.istek(istek).andReturn().getResponse().getStatus();
    }

    @Test
    void anonim_okur_yalnizAdVeLogoBilgisiDoner() throws Exception {
        yonetici().put("/api/kurum", AD_GOVDESI);
        yonetici().istek(MockMvcRequestBuilders.put("/api/kurum/logo").contentType("application/octet-stream").content(KurumGorselleri.png()));

        Tarayici anonim = new Tarayici(mvc);
        var acik = anonim.get("/api/kurum/acik").andReturn().getResponse();
        assertThat(acik.getStatus()).isEqualTo(200);
        String govde = acik.getContentAsString();
        assertThat(govde).contains("\"ad\":\"Örnek İşletme (demo)\"").contains("\"logoVar\":true").contains("\"logoSurum\":");
        assertThat(govde).as("yalnız üç alan: kullanıcı/yapılandırma/sayaç sızmaz").doesNotContain("@ornek.test").doesNotContain("sihirbaz").doesNotContain("eposta").doesNotContain("rol");
        assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(govde).size()).isEqualTo(3);

        var logo = anonim.get("/api/kurum/logo").andReturn().getResponse();
        assertThat(logo.getStatus()).isEqualTo(200);
        assertThat(logo.getContentType()).isEqualTo("image/png");
        assertThat(logo.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(logo.getHeader("Content-Security-Policy")).contains("default-src 'none'").contains("sandbox");
        assertThat(logo.getHeader("ETag")).isNotBlank();
        assertThat(logo.getContentAsByteArray()).isEqualTo(KurumGorselleri.png());
        var yeniden = anonim.istek(MockMvcRequestBuilders.get("/api/kurum/logo").header("If-None-Match", logo.getHeader("ETag"))).andReturn().getResponse();
        assertThat(yeniden.getStatus()).as("ETag eşleşince 304").isEqualTo(304);
    }

    @Test
    void anonim_bosKurulum_adNull_logo404() throws Exception {
        Tarayici anonim = new Tarayici(mvc);
        assertThat(anonim.get("/api/kurum/acik").andReturn().getResponse().getContentAsString()).contains("\"ad\":null").contains("\"logoVar\":false");
        assertThat(anonim.get("/api/kurum/logo").andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void yazma_anonim401_atamaci403_makinist403_yonetici200() throws Exception {
        Tarayici anonim = new Tarayici(mvc);
        Tarayici atamaci = atamaci();
        Tarayici makinist = makinistGirisli(new AtamaVeri(jdbc).makinist("D001"), "D001");
        byte[] png = KurumGorselleri.png();
        var yazmalar = java.util.List.<java.util.function.Supplier<org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?>>>of(
                () -> MockMvcRequestBuilders.put("/api/kurum").contentType("application/json").content(AD_GOVDESI),
                () -> MockMvcRequestBuilders.put("/api/kurum/logo").contentType("application/octet-stream").content(png),
                () -> MockMvcRequestBuilders.delete("/api/kurum/logo"));
        for (var yazma : yazmalar) {
            assertThat(durum(anonim, yazma.get())).as("anonim").isEqualTo(401);
            assertThat(durum(atamaci, yazma.get())).as("atamaci").isEqualTo(403);
            assertThat(durum(makinist, yazma.get())).as("makinist").isEqualTo(403);
        }
        assertThat(anonim.get("/api/kurum/acik").andReturn().getResponse().getContentAsString()).as("reddedilen yazma hiçbir şey değiştirmedi").contains("\"ad\":null");
        Tarayici y = yonetici();
        for (var yazma : yazmalar) {
            assertThat(durum(y, yazma.get())).as("yönetici").isEqualTo(200);
        }
    }

    @Test
    void kurumAltindaBaskaYollar_anonim401_veMakinistOkumaDa() throws Exception {
        Tarayici anonim = new Tarayici(mvc);
        for (String yol : new String[] { "/api/kurum/baska", "/api/kurum", "/api/kurum/acik/x", "/api/kurum/logo/x" }) {
            assertThat(anonim.get(yol).andReturn().getResponse().getStatus()).as("anonim GET " + yol).isEqualTo(401);
        }
        assertThat(anonim.post("/api/kurum/acik", "{}").andReturn().getResponse().getStatus()).as("POST anonim").isEqualTo(401);
    }

    @Test
    void logoYukleme_gecersizGovde400_veAsiriBoyut400() throws Exception {
        Tarayici y = yonetici();
        assertThat(durum(y, MockMvcRequestBuilders.put("/api/kurum/logo").contentType("application/octet-stream").content("<svg/>".getBytes()))).isEqualTo(400);
        assertThat(durum(y, MockMvcRequestBuilders.put("/api/kurum/logo").contentType("image/png").content(new byte[300 * 1024]))).isEqualTo(400);
        assertThat(durum(y, MockMvcRequestBuilders.put("/api/kurum").contentType("application/json").content("{\"ad\":\"" + "x".repeat(121) + "\"}"))).isEqualTo(400);
        assertThat(durum(y, MockMvcRequestBuilders.put("/api/kurum").contentType("application/json").content("{\"ad\":\"\"}"))).isEqualTo(400);
        assertThat(new Tarayici(mvc).get("/api/kurum/acik").andReturn().getResponse().getContentAsString()).contains("\"logoVar\":false");
    }
}
