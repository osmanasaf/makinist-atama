package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.param;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.YorgunlukHesabi.Sonuc;

/** F3 / MK-175 / AK-3.7: yorgunluk özeti (üç ham değer) bilinen girdi -> bilinen çıktı; saf, DB'siz. */
class YorgunlukHesabiTest {

    private static final java.time.Instant REF = an("2026-10-14T12:00");

    private static Sonuc hesapla(List<Gorev> g, int gun) {
        return YorgunlukHesabi.hesapla(g, REF, param(), gun);
    }

    @Test
    void bilinenGirdi_toplamGeceNobetiVeEnKisaDinlenme() {
        Gorev a = gorev(1, "2026-10-08T06:00", "2026-10-08T14:00"); // 8 s
        Gorev b = gorev(2, "2026-10-10T22:00", "2026-10-11T05:00"); // 7 s, gece yarısı geçen gece nöbeti
        Gorev c = gorev(3, "2026-10-13T06:00", "2026-10-13T10:00"); // 4 s
        Sonuc s = hesapla(List.of(a, b, c), 7);
        assertThat(s.toplamDk()).isEqualTo(19 * 60);
        assertThat(s.geceNobetSayisi()).isEqualTo(1);
        assertThat(s.enKisaDinlenmeDk()).as("B bitişi 11 Eki 05:00 -> C başlangıcı 13 Eki 06:00 = 49 s; A -> B 56 s").isEqualTo(49L * 60);
    }

    @Test
    void tekNobet_enKisaDinlenmeNull_hicGorevSifir() {
        Sonuc tek = hesapla(List.of(gorev(1, "2026-10-13T06:00", "2026-10-13T10:00")), 7);
        assertThat(tek.enKisaDinlenmeDk()).isNull();
        assertThat(tek.toplamDk()).isEqualTo(240);
        Sonuc bos = hesapla(List.of(), 7);
        assertThat(bos).isEqualTo(new Sonuc(0, 0, null));
    }

    @Test
    void ayniNobetteBirlesenGorevler_tekNobetSayilir_dinlenmeYok() {
        // 06:00-10:00 ve 12:00-16:00: aradaki boşluk 2 s < nobet.ayirma_saat (4 s) -> tek nöbet
        Sonuc s = hesapla(List.of(gorev(1, "2026-10-13T06:00", "2026-10-13T10:00"), gorev(2, "2026-10-13T12:00", "2026-10-13T16:00")), 7);
        assertThat(s.toplamDk()).isEqualTo(480);
        assertThat(s.enKisaDinlenmeDk()).isNull();
    }

    @Test
    void pencereSiniri_oncekiGorevHicSayilmaz_sinirdaKesilenYalnizIcKismiSayilir_sonrakiGorevSayilmaz() {
        Gorev tamDisinda = gorev(1, "2026-10-06T06:00", "2026-10-06T14:00"); // pencere 7 Eki 12:00'da başlıyor
        Gorev sinirdaKesilen = gorev(2, "2026-10-07T06:00", "2026-10-07T14:00"); // yalnız 12:00-14:00 (2 s) pencerede; başlangıcı dışarıda: nöbet sayılmaz
        Gorev ic = gorev(3, "2026-10-09T06:00", "2026-10-09T10:00");
        Gorev referansSonrasi = gorev(4, "2026-10-15T08:00", "2026-10-15T12:00");
        Sonuc s = hesapla(List.of(tamDisinda, sinirdaKesilen, ic, referansSonrasi), 7);
        assertThat(s.toplamDk()).isEqualTo(2 * 60 + 4 * 60);
        assertThat(s.enKisaDinlenmeDk()).as("pencerede başlayan tek nöbet").isNull();
        assertThat(hesapla(List.of(tamDisinda, sinirdaKesilen, ic), 1).toplamDk()).as("1 günlük pencere: yalnız 13 Eki 12:00'dan sonrası").isZero();
    }

    @Test
    void geceNobetiSayisi_gunduzNobetiSayilmaz_yarisindanAzGeceGunduzdur() {
        Gorev gece1 = gorev(1, "2026-10-09T21:00", "2026-10-10T04:00");
        Gorev gunduz = gorev(2, "2026-10-11T08:00", "2026-10-11T16:00");
        Gorev gece2 = gorev(3, "2026-10-12T22:00", "2026-10-13T05:00");
        Gorev kenar = gorev(4, "2026-10-13T16:00", "2026-10-13T23:00"); // 3 s gece (20:00-23:00) / 7 s: oran 0,43 -> gündüz
        assertThat(hesapla(List.of(gece1, gunduz, gece2, kenar), 7).geceNobetSayisi()).isEqualTo(2);
    }

    @Test
    void sayilmayanYolculuk_toplamdanDuserAmaNobetZincirindeKalir() {
        Gorev onceki = gorev(1, "2026-10-11T06:00", "2026-10-11T10:00");
        Gorev yolculuk = Gorev.yolculukOlustur(2, ANKARA, ANKARA, 10, 20, an("2026-10-13T06:00"), an("2026-10-13T06:00"), an("2026-10-13T12:00"), false);
        Gorev surus = gorev(3, "2026-10-13T13:00", "2026-10-13T15:00");
        Sonuc s = hesapla(List.of(onceki, yolculuk, surus), 7);
        assertThat(s.toplamDk()).as("4 s + 2 s; 6 s yolculuk sayılmaz").isEqualTo(360);
        assertThat(s.enKisaDinlenmeDk()).as("nöbet yolculukla 06:00'da başlar: 11 Eki 10:00 -> 13 Eki 06:00 = 44 s").isEqualTo(44L * 60);
        Gorev sayilir = Gorev.yolculukOlustur(2, ANKARA, ANKARA, 10, 20, an("2026-10-13T06:00"), an("2026-10-13T06:00"), an("2026-10-13T12:00"), true);
        assertThat(hesapla(List.of(onceki, sayilir, surus), 7).toplamDk()).isEqualTo(720);
    }

    @Test
    void beklemeSayilirken_nobetIciBoslukDaCalismadir() {
        Parametreler bekleme = param("nobet.bekleme_sayilir", "1");
        List<Gorev> g = List.of(gorev(1, "2026-10-13T06:00", "2026-10-13T08:00"), gorev(2, "2026-10-13T10:00", "2026-10-13T12:00"));
        assertThat(YorgunlukHesabi.hesapla(g, REF, param(), 7).toplamDk()).isEqualTo(240);
        assertThat(YorgunlukHesabi.hesapla(g, REF, bekleme, 7).toplamDk()).isEqualTo(360);
    }
}
