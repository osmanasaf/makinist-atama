package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;

/** F2b MK-39/T1.3: {@code yolculuk.calisma_sayilir} TEK okuma yeri {@code BaglamYukleyici}; yönetici değiştirir (F2a {@code PUT /api/ayar}), kayıt düşer, yeni yüklemede geçerlidir. */
class YolculukParametreTest extends TestVeritabani {

    @Autowired
    BaglamYukleyici yukleyici;

    @Test
    void tohum1_yoneticiDegistirince0_kayitDuser_atamaciDegistiremez() throws Exception {
        assertThat(yukleyici.yolculukSayilir()).isTrue();

        atamaci().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isForbidden());
        assertThat(yukleyici.yolculukSayilir()).isTrue();

        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"TİS yorumu\"}").andExpect(status().isOk());

        assertThat(yukleyici.yolculukSayilir()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'PARAMETRE_DEGISTI' AND ayrinti LIKE '%yolculuk.calisma_sayilir%'", Integer.class)).isEqualTo(1);
    }

    @Test
    void sinirDisiDeger400() throws Exception {
        yonetici().put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":2,\"neden\":\"deneme\"}").andExpect(status().isBadRequest());
        yonetici().put("/api/ayar/yolculuk.arama_saat", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isBadRequest());
        yonetici().put("/api/ayar/yolculuk.aktarma_dk", "{\"deger\":241,\"neden\":\"deneme\"}").andExpect(status().isBadRequest());
    }
}
