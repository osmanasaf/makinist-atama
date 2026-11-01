package com.makinistatama.kural;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.RastgeleKuralVeri.Senaryo;

/**
 * F3 / MK-5 / AK-3.2: yakınlık kural motorunun çıktısını DEĞİŞTİRMEZ (rastgele veri, DB'siz). (a) {@link YakinlikKontrolu} çağrısının öncesi ve sonrası
 * {@code degerlendir/degerlendirYazma} çıktısı birebir aynı; (b) yakınlık ile ihlal aynı kodda birlikte çıkmaz (ihlal varsa işaret yok) ve yakınlığın ölçülen değeri sınırı AŞMAZ;
 * (c) motor ve çıktı tipi dosyaları yakınlığı hiç anmaz. Altın kural özeti ({@code KuralMotoruYazmaEsdegerTest.kuralOzeti_altinDosya}) değişmeden yeşil kalır.
 */
class YakinlikEsdegerTest {

    /** Geniş marjlar: çok sayıda işaret üretir (duyarlılık); dar marjlar: gerçekçi varsayılan. */
    private static final List<YakinlikAyarlari> AYARLAR = List.of(new YakinlikAyarlari(true, 60, 30, 60, 120, 20, 10, 0),
            new YakinlikAyarlari(true, 720, 240, 720, 1440, 270, 120, 3), YakinlikAyarlari.kapali());

    @Test
    void motorCiktisiYakinlikHesabindanOnceVeSonraBirebirAyni() {
        int isaret = 0;
        int degerlendirme = 0;
        for (Senaryo s : KuralMotoruYazmaEsdegerTest.senaryolar()) {
            for (KuralBaglami b : s.baglamlar()) {
                List<Ihlal> once = KuralMotoru.degerlendir(b);
                List<Ihlal> onceYazma = KuralMotoru.degerlendirYazma(b);
                for (YakinlikAyarlari a : AYARLAR) {
                    isaret += YakinlikKontrolu.kontrol(b, a).size();
                }
                assertThat(KuralMotoru.degerlendir(b)).as("senaryo " + s.no()).isEqualTo(once);
                assertThat(KuralMotoru.degerlendirYazma(b)).as("senaryo " + s.no()).isEqualTo(onceYazma);
                degerlendirme++;
            }
        }
        assertThat(degerlendirme).isGreaterThan(1000);
        assertThat(isaret).as("duyarlılık: rastgele veride yakınlık üretiliyor").isGreaterThan(100);
    }

    @Test
    void yakinlikVeIhlalAyniKodda_birlikteCikmaz_olculenSinirdanAsmaz() {
        int kontrol = 0;
        List<String> goruldu = new ArrayList<>();
        for (Senaryo s : KuralMotoruYazmaEsdegerTest.senaryolar()) {
            for (KuralBaglami b : s.baglamlar()) {
                List<String> ihlalKodlari = KuralMotoru.degerlendir(b).stream().map(Ihlal::kod).distinct().toList();
                for (Yakinlik y : YakinlikKontrolu.kontrol(b, AYARLAR.get(1))) {
                    assertThat(ihlalKodlari).as("senaryo " + s.no() + " " + y.kod() + " (" + y.metin() + ")").doesNotContain(y.kod());
                    assertThat(y.kalan()).as(y.metin()).isGreaterThanOrEqualTo(0);
                    assertThat(y.olculen() + y.kalan()).isIn(y.sinir(), y.sinir() + 2 * y.kalan()); // üst sınır: ölçülen+kalan=sınır; alt sınır: ölçülen-kalan=eşik
                    kontrol++;
                    if (!goruldu.contains(y.kod())) {
                        goruldu.add(y.kod());
                    }
                }
            }
        }
        assertThat(kontrol).isGreaterThan(100);
        assertThat(goruldu).as("duyarlılık: birden çok kural için işaret üretiliyor").hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void motorVeCiktiTipleriYakinligiAnmaz() throws Exception {
        for (String ad : List.of("KuralMotoru", "Ihlal", "Siddet", "Onhesap")) {
            String kaynak = Files.readString(Path.of("src", "main", "java", "com", "makinistatama", "kural", ad + ".java"));
            assertThat(kaynak).as(ad + ".java yakınlığı anmaz (MK-172)").doesNotContain("Yakinlik").doesNotContain("Yorgunluk");
        }
    }
}
