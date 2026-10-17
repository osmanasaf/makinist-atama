package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.demo.SabitTarihliDemo;

/** Review INFO: yolculuk bayrağı AÇIKKEN de ikinci (ve üçüncü) "uygula" yeni satır üretmez (tek geçişli greedy'nin geride bıraktığı seferler aynı işlemde yeniden denenir). */
class OtomatikAtamaYolculukIdempotentTest extends TestVeritabani {

    @Autowired
    ApplicationContext ctx;

    @Test
    void bayrakAcik_ikinciUygula_yeniSatirUretmez_onizlemeUygulamaylaAyni() throws Exception {
        SabitTarihliDemo.yukle(ctx);
        Tarayici t = atamaci();
        String onizle = "{\"bas\":\"2026-10-12\",\"bit\":\"2026-11-11\",\"uygula\":false,\"yolculuk\":true}";
        String uygula = "{\"bas\":\"2026-10-12\",\"bit\":\"2026-11-11\",\"uygula\":true,\"yolculuk\":true}";
        String onizlenen = t.post("/api/atama/otomatik", onizle).andReturn().getResponse().getContentAsString();
        t.post("/api/atama/otomatik", uygula);
        int once = jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class);
        assertThat(onizlenen).contains("\"atananSayisi\":" + (once - jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'YOLCU'", Integer.class)));

        t.post("/api/atama/otomatik", uygula);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama", Integer.class)).as("ikinci uygulamada yeni satır").isEqualTo(once);
    }
}
