package com.makinistatama.demo;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.makinistatama.TestVeritabani;

/** Varsayılan (dev olmayan) profilde demo uçları yoktur. */
class DemoProdTest extends TestVeritabani {

    @Test
    void demoUclariYok_404() throws Exception {
        yonetici().get("/api/demo/durum").andExpect(status().isNotFound());
        yonetici().post("/api/demo/yukle", "").andExpect(status().isNotFound());
        yonetici().post("/api/demo/sifirla", "").andExpect(status().isNotFound());
        yonetici().post("/api/demo/yolculuk-ornekleri", "").andExpect(status().isNotFound());
    }
}
