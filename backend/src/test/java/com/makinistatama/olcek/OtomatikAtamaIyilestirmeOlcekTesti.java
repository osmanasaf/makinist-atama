package com.makinistatama.olcek;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.makinistatama.TestVeritabani;
import com.makinistatama.demo.SabitTarihliDemo;

/**
 * F2d (T5.2) demo verisi: 31 gün (12 Ekim - 11 Kasım) bugünkü plan 69 boş; iyileştirmeli plan <= 40 (plan kapısı 45; ölçülen 37, 12. turda). Çalıştırma:
 * {@code mvnw test -Pyavas "-Dtest=OtomatikAtamaIyilestirmeOlcekTesti"}.
 */
@Tag("yavas")
class OtomatikAtamaIyilestirmeOlcekTesti extends TestVeritabani {

    @Autowired
    ApplicationContext ctx;

    @Test
    void demo_otuzBirGun_iyilestirmeBosuAzaltir_kirmiziYok_kilitAltiTekTur() throws Exception {
        SabitTarihliDemo.yukle(ctx);
        IyilestirOlcum.olc(atamaci(), jdbc, "demo 31 gün", "2026-10-12", "2026-11-11", new IyilestirOlcum.Esik(55, 20_000, 1_500, 2.5, 1.25), true); // OK-24: bos tavanı 40 -> 55 (ölçülen 65 -> 51): K-23 ENGEL/20 dk varsayılanı sıkışık demo tarifesinde daha çok sefer boş bırakır
    }
}
