package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.makinistatama.Tarayici;
import com.makinistatama.atama.AtamaVeri;

/** T1.9 (AK-2.9, NFR-02, OK-35): 5 profil ucu YALNIZ yönetici (default-deny): atamacı 403, makinist 403, anonim 401; 1 MB üstü ve bozuk gövde 400. */
class ProfilYetkiTest extends ProfilTestTabani {

    private static final String PAKET = "{\"format\":\"makinist-atama-profil\",\"profilSurumu\":1,\"ad\":\"x\"}";

    private interface Cagri {
        ResultActions yap(Tarayici t) throws Exception;
    }

    private List<Cagri> uclar() {
        return List.of(
                t -> t.get("/api/profil/disa-aktar"),
                t -> t.get("/api/profil/hazir"),
                t -> t.get("/api/profil/hazir/varsayilan"),
                t -> t.post("/api/profil/onizle", PAKET),
                t -> t.post("/api/profil/uygula", "{\"paket\":" + PAKET + ",\"secilen\":[],\"neden\":\"n\"}"));
    }

    @Test
    void besUc_anonim401_atamaci403_makinist403_yonetici200() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici anonim = new Tarayici(mvc);
        Tarayici atamaci = atamaci();
        Tarayici makinist = makinistGirisli(id, "D001");
        Tarayici yonetici = yonetici();

        List<String> sorun = new ArrayList<>();
        int i = 0;
        for (Cagri c : uclar()) {
            i++;
            int a = c.yap(anonim).andReturn().getResponse().getStatus();
            int at = c.yap(atamaci).andReturn().getResponse().getStatus();
            int m = c.yap(makinist).andReturn().getResponse().getStatus();
            int y = c.yap(yonetici).andReturn().getResponse().getStatus();
            if (a != 401 || at != 403 || m != 403 || y != 200) {
                sorun.add("uç " + i + ": anonim " + a + ", atamacı " + at + ", makinist " + m + ", yönetici " + y);
            }
        }

        assertThat(sorun).isEmpty();
    }

    @Test
    void ikiMegabaytGovde_400_bozukGovde_400_formatYok_400() throws Exception {
        Tarayici y = yonetici();
        String iki = "{\"format\":\"makinist-atama-profil\",\"profilSurumu\":1,\"aciklama\":\"" + "a".repeat(2 * 1024 * 1024) + "\"}";

        y.post("/api/profil/onizle", iki).andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        y.post("/api/profil/uygula", "{\"paket\":" + iki + ",\"neden\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        y.post("/api/profil/onizle", "<html>bu bir json degil</html>").andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        y.post("/api/profil/onizle", "{\"ad\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        y.post("/api/profil/uygula", "{\"neden\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.kod").value("GECERSIZ_DOSYA"));
        y.get("/api/profil/hazir/yok-boyle").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi", Integer.class)).isZero();
    }
}
