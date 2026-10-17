package com.makinistatama.kural;

import static com.makinistatama.kural.TestVerisi.ANKARA;
import static com.makinistatama.kural.TestVerisi.ARAC;
import static com.makinistatama.kural.TestVerisi.ESKISEHIR;
import static com.makinistatama.kural.TestVerisi.HAT;
import static com.makinistatama.kural.TestVerisi.an;
import static com.makinistatama.kural.TestVerisi.baglam;
import static com.makinistatama.kural.TestVerisi.gorev;
import static com.makinistatama.kural.TestVerisi.kodlar;
import static com.makinistatama.kural.TestVerisi.param;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.TestVerisi.M;

/** F2b MK-40/FR-02: yolcu olarak gitmek yalnız K-01 (durum) ve K-09 (izin) ister; belge, yetki, hat ara kuralı ve K-21 uygulanmaz; yolculuk son sürüşü temizlemez. */
class YolculukBelgeKuralTest {

    private static Gorev yolculuk(long id, String bas, String bit) {
        return Gorev.yolculukOlustur(id, ANKARA, ESKISEHIR, HAT, ARAC, an(bas), an(bas), an(bit), true);
    }

    @Test
    void yetkisizVeEhliyetiDolmusMakinist_yolcuOlabilir_surusIcinK02K05K06Olurdu() {
        M m = new M();
        m.ehliyet = LocalDate.of(2020, 1, 1);
        m.arac = new HashMap<>();
        m.hat = new HashMap<>();
        Gorev y = yolculuk(1, "2026-10-12T08:00", "2026-10-12T09:30");

        List<Ihlal> yolcu = BelgeKurallari.kontrol(baglam(y, List.of(), m.yap(), param()));
        List<Ihlal> surus = BelgeKurallari.kontrol(baglam(gorev(2, "2026-10-12T08:00", "2026-10-12T09:30"), List.of(), m.yap(), param()));

        assertTrue(yolcu.isEmpty(), yolcu.toString());
        assertTrue(kodlar(surus).containsAll(List.of("K-02", "K-05", "K-06")), surus.toString());
    }

    @Test
    void izinliMakinist_yolcuyaK09_askidaK01Engel() {
        M izinli = new M();
        izinli.izin.add(new MakinistBilgisi.Izin(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), MakinistBilgisi.IzinDurumu.ONAY));
        M askida = new M();
        askida.durum = MakinistBilgisi.Durum.ASKIDA;
        Gorev y = yolculuk(1, "2026-10-12T08:00", "2026-10-12T09:30");

        assertEquals(List.of("K-09"), kodlar(BelgeKurallari.kontrol(baglam(y, List.of(), izinli.yap(), param()))));
        List<Ihlal> a = BelgeKurallari.kontrol(baglam(y, List.of(), askida.yap(), param()));
        assertEquals(List.of("K-01"), kodlar(a));
        assertEquals(Siddet.ENGEL, a.get(0).siddet());
    }

    @Test
    void yolculuk_sonSurusuTemizlemez_K07K08() {
        // son sürüş 1 Mar 2025; 12 Eki 2026'da 19 ay > 18: K-07/K-08. Komşu yolculuk 1 Eki 2026'da bitse de sürüş sayılmaz (FR-02 örneği)
        M m = new M();
        m.sonSurus = LocalDate.of(2025, 3, 1);
        m.hat = new HashMap<>(Map.of(HAT, new MakinistBilgisi.HatBrove(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 3, 1))));
        Gorev aday = gorev(2, "2026-10-12T08:00", "2026-10-12T12:00");

        List<Ihlal> yolculukla = BelgeKurallari.kontrol(baglam(aday, List.of(yolculuk(1, "2026-10-01T08:00", "2026-10-01T09:30")), m.yap(), param()));
        List<Ihlal> surusla = BelgeKurallari.kontrol(baglam(aday, List.of(gorev(1, "2026-10-01T08:00", "2026-10-01T09:30")), m.yap(), param()));

        assertTrue(kodlar(yolculukla).containsAll(List.of("K-07", "K-08")), yolculukla.toString());
        assertFalse(kodlar(surusla).contains("K-07") || kodlar(surusla).contains("K-08"), surusla.toString());
    }

    @Test
    void k21_tekSefer_yolculukta_uygulanmaz() {
        Gorev uzunYolculuk = yolculuk(1, "2026-10-12T06:00", "2026-10-12T17:00"); // 11 s > sefer.max_saat 9
        Gorev uzunSefer = gorev(2, "2026-10-12T06:00", "2026-10-12T17:00");

        Parametreler p = param(Map.of("K-21", Siddet.ENGEL));
        assertFalse(kodlar(KuralMotoru.degerlendir(baglam(uzunYolculuk, List.of(), p))).contains("K-21"));
        assertTrue(kodlar(KuralMotoru.degerlendir(baglam(uzunSefer, List.of(), p))).contains("K-21"));
    }
}
