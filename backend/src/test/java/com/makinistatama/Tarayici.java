package com.makinistatama;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;

/**
 * Tek bir tarayıcı gibi davranan test istemcisi: oturum ve {@code XSRF-TOKEN} çerezini tutar, her istekte
 * çerezi ve {@code X-XSRF-TOKEN} başlığını SPA'nın yaptığı gibi gönderir (ham değer; ADR-05).
 * MockMvc çerez saklamadığı için bu sınıf gereklidir.
 */
public final class Tarayici {

    public static final String PAROLA = "dogru-parola-123";

    private final MockMvc mvc;
    public String xsrf;
    public MockHttpSession oturum;

    public Tarayici(MockMvc mvc) throws Exception {
        this.mvc = mvc;
        get("/api/auth/ben"); // anonim 401: XSRF-TOKEN çerezini alır
    }

    /** Giriş yapılmış tarayıcı. */
    public static Tarayici girisYapmis(MockMvc mvc, String eposta, String parola) throws Exception {
        Tarayici t = new Tarayici(mvc);
        t.giris(eposta, parola);
        return t;
    }

    public ResultActions giris(String eposta, String parola) throws Exception {
        return post("/api/auth/giris", "{\"eposta\":\"%s\",\"parola\":\"%s\"}".formatted(eposta, parola));
    }

    public ResultActions get(String url) throws Exception {
        return istek(MockMvcRequestBuilders.get(url));
    }

    public ResultActions post(String url, String json) throws Exception {
        return istek(MockMvcRequestBuilders.post(url).contentType("application/json").content(json));
    }

    public ResultActions put(String url, String json) throws Exception {
        return istek(MockMvcRequestBuilders.put(url).contentType("application/json").content(json));
    }

    public ResultActions patch(String url, String json) throws Exception {
        return istek(MockMvcRequestBuilders.patch(url).contentType("application/json").content(json));
    }

    public ResultActions delete(String url) throws Exception {
        return istek(MockMvcRequestBuilders.delete(url));
    }

    public ResultActions istek(AbstractMockHttpServletRequestBuilder<?> istek) throws Exception {
        if (xsrf != null) {
            istek.cookie(new Cookie("XSRF-TOKEN", xsrf)).header("X-XSRF-TOKEN", xsrf);
        }
        if (oturum != null) {
            istek.session(oturum);
        }
        ResultActions sonuc = mvc.perform(istek);
        MvcResult r = sonuc.andReturn();
        Cookie c = r.getResponse().getCookie("XSRF-TOKEN");
        if (c != null) {
            xsrf = c.getValue();
        }
        HttpSession s = r.getRequest().getSession(false);
        if (s instanceof MockHttpSession m) {
            oturum = m;
        }
        if (oturum != null && oturum.isInvalid()) {
            oturum = null;
        }
        return sonuc;
    }
}
