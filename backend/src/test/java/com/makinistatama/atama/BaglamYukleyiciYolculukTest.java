package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.kural.Gorev;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/** F2b MK-39/MK-40/T1.3: YOLCU satırı ve YOLCU adayı yolculuk {@code Gorev}'ine döner; "bu ay" km değişmez, saat {@code yolculuk.calisma_sayilir}'a göre; ayar tembel okunur. */
class BaglamYukleyiciYolculukTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    @Autowired
    BaglamYukleyici yukleyici;

    @Autowired
    SeferServisi seferler;

    @Autowired
    MakinistServisi makinistler;

    AtamaVeri v;
    long m;
    long e1;
    long s2;

    @BeforeEach
    void kur() {
        jdbc.update("UPDATE kural_parametre SET deger = '30' WHERE anahtar = 'gorev.hazirlik_dk'");
        jdbc.update("UPDATE kural_parametre SET deger = '15' WHERE anahtar = 'gorev.alikoyma_dk'");
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
        e1 = v.sefer("E1", GUN, "08:00", "09:30"); // Ankara -> Eskişehir, km 250
        s2 = v.sefer("S2", GUN, "10:30", "13:00", v.eskisehir, v.ankara);
    }

    private Yuk yuk(long adaySefer, AtamaRol rol) {
        SeferDto sefer = seferler.getir(adaySefer);
        MakinistDto dto = makinistler.detaylar(List.of(m)).getFirst();
        return yukleyici.yukle(sefer, List.of(dto), rol);
    }

    @Test
    void yolcuSatiri_yolculukGorevinedonuser_buAyKmDegismez_saatSayilir() {
        v.atama(e1, m, "YOLCU");
        Yuk y = yuk(s2, AtamaRol.ASIL);

        List<Gorev> diger = y.digerGorevler(m);
        assertThat(diger).hasSize(1);
        Gorev g = diger.getFirst();
        assertThat(g.yolculuk()).isTrue();
        assertThat(g.sureSayilir()).isTrue();
        assertThat(g.km()).isZero();
        assertThat(g.baslangic()).isEqualTo(Instant.parse("2026-10-14T05:00:00Z")); // 08:00 İstanbul: hazırlık YOK
        assertThat(g.bitis()).isEqualTo(Instant.parse("2026-10-14T06:30:00Z")); // 09:30: alıkoyma YOK
        double[] buAy = y.buAy(m);
        assertThat(buAy[0]).isEqualTo(1.5);
        assertThat(buAy[1]).isZero(); // sefer km'si (250) yolcuya yazılmaz
        assertThat(y.adayGorev().yolculuk()).isFalse();
        assertThat(y.adayGorev().baslangic()).isEqualTo(Instant.parse("2026-10-14T07:00:00Z")); // 10:30 - 30 dk hazırlık
    }

    @Test
    void calismaSayilirKapaliysa_buAySaatiYolculukSuz_aralikYineDolu() throws Exception {
        v.atama(e1, m, "YOLCU");
        Tarayici t = yonetici();
        t.put("/api/ayar/yolculuk.calisma_sayilir", "{\"deger\":0,\"neden\":\"deneme\"}").andExpect(status().isOk());

        Yuk y = yuk(s2, AtamaRol.ASIL);

        assertThat(y.buAy(m)[0]).isZero();
        Gorev g = y.digerGorevler(m).getFirst();
        assertThat(g.sureSayilir()).isFalse();
        assertThat(g.sureDakika()).isZero();
        assertThat(g.bitis()).isEqualTo(Instant.parse("2026-10-14T06:30:00Z"));
    }

    @Test
    void adayRoleGoreKurulur_yolcuAdayiYolculukGorevidir() {
        Yuk asil = yuk(e1, AtamaRol.ASIL);
        Yuk yolcu = yuk(e1, AtamaRol.YOLCU);

        assertThat(asil.adayGorev().yolculuk()).isFalse();
        assertThat(asil.adayGorev().baslangic()).isEqualTo(Instant.parse("2026-10-14T04:30:00Z")); // 08:00 - 30
        assertThat(yolcu.adayGorev().yolculuk()).isTrue();
        assertThat(yolcu.adayGorev().baslangic()).isEqualTo(Instant.parse("2026-10-14T05:00:00Z"));
        assertThat(yolcu.adayGorev().bitis()).isEqualTo(Instant.parse("2026-10-14T06:30:00Z"));
    }

    @Test
    void ayarTembelOkunur_yolcuYokkenHicSorulmaz() {
        // tohum satırı geçici silinir (uygulama_ayar temizlikte korunur: satır geri konur)
        var satir = jdbc.queryForMap("SELECT * FROM uygulama_ayar WHERE anahtar = 'yolculuk.calisma_sayilir'");
        jdbc.update("DELETE FROM uygulama_ayar WHERE anahtar = 'yolculuk.calisma_sayilir'");
        try {
            v.atama(s2, m, "ASIL");

            Yuk y = yuk(e1, AtamaRol.ASIL); // yolcu satırı da yolcu adayı da yok: ayar okunmaz (ek sorgu yok)
            assertThat(y.digerGorevler(m)).hasSize(1);
            assertThat(y.buAy(m)[0]).isPositive();

            assertThatThrownBy(() -> yuk(e1, AtamaRol.YOLCU).adayGorev()).isInstanceOf(IllegalStateException.class).hasMessageContaining("tohumlanmamış");
        } finally {
            // sütunlar satırın kendisinden: şemaya sütun eklenince bu geri koyma kırılmaz (eksik sütunlu INSERT patlarsa satır kalıcı kaybolur, sonraki sınıflar zincirleme düşer)
            jdbc.update("INSERT INTO uygulama_ayar (" + String.join(", ", satir.keySet()) + ") VALUES (" + String.join(", ", java.util.Collections.nCopies(satir.size(), "?")) + ")",
                    satir.values().toArray());
        }
    }
}
