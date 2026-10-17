package com.makinistatama.hesap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;

/** FR-M17 / MK-36: hesap olayları denetim kaydı üretir; kayıtlarda parola, karma ya da geçici parola ASLA yoktur. */
class HesapDenetimTest extends TestVeritabani {

    @Test
    void parolaVeKarmaKayittaYok() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici y = yonetici();
        String gecici1 = JsonPath.read(y.post("/api/makinist/" + id + "/hesap", "").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.geciciParola");
        String gecici2 = JsonPath.read(y.post("/api/makinist/" + id + "/hesap/parola-sifirla", "").andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.geciciParola");
        y.post("/api/makinist/" + id + "/hesap/kilit-ac", "").andExpect(status().isOk());
        Tarayici m = new Tarayici(mvc);
        m.giris("d001", gecici2).andExpect(status().isOk());
        m.post("/api/auth/parola", "{\"eskiParola\":\"" + gecici2 + "\",\"yeniParola\":\"benim-yeni-parolam-9\"}").andExpect(status().isNoContent());
        y.post("/api/kullanicilar", "{\"eposta\":\"yeni.ata@ornek.test\",\"adSoyad\":\"Yeni Ata\",\"rol\":\"ATAMACI\",\"parola\":\"kayitta-gorunmesin-77\"}").andExpect(status().isCreated());

        List<Map<String, Object>> kayitlar = jdbc.queryForList("SELECT tur, eski, yeni, ayrinti, neden_notu FROM degisiklik_kaydi WHERE varlik_tipi = 'KULLANICI' ORDER BY id");
        assertThat(kayitlar).extracting(k -> k.get("tur")).containsExactly("HESAP_ACILDI", "HESAP_PAROLA_SIFIRLANDI", "HESAP_KILIT_ACILDI", "PAROLA_DEGISTI", "HESAP_ACILDI");
        String tumu = kayitlar.toString();
        for (String yasak : List.of(gecici1, gecici2, "benim-yeni-parolam-9", "kayitta-gorunmesin-77", "$2a$", "$2b$", "karma")) {
            assertThat(tumu).as("kayıtlarda '%s' geçmemeli", yasak).doesNotContain(yasak);
        }
        // denetim raporu uç noktası da aynısını göstermez
        String rapor = y.get("/api/degisiklik?bas=2026-10-01&bit=2026-10-31").andReturn().getResponse().getContentAsString();
        assertThat(rapor).doesNotContain(gecici1).doesNotContain(gecici2).doesNotContain("benim-yeni-parolam-9");
    }
}
