package com.makinistatama.kurum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.UygulamaHatasi;

/** F1 / MK-167: kurum adı + logo kuralları (tür, imza, boyut, piksel) ve denetim kaydı. Boş kurulumda ad yok, logo yok. */
class KurumServisiTest extends TestVeritabani {

    @Autowired
    KurumServisi servis;

    private void reddedilir(byte[] bayt, String parca) {
        assertThatThrownBy(() -> servis.logoYaz(bayt)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining(parca)
                .satisfies(e -> assertThat(((UygulamaHatasi) e).kod()).isEqualTo("GECERSIZ_DOSYA"));
        assertThat(servis.acik().logoVar()).as("reddedilen logo yazılmaz").isFalse();
    }

    @Test
    void bosKurulum_adVeLogoYok() {
        var a = servis.acik();
        assertThat(a.ad()).isNull();
        assertThat(a.logoVar()).isFalse();
        assertThat(servis.logo()).isEmpty();
        assertThat(servis.ad()).isEmpty();
    }

    @Test
    void png_kabul_ayrintiTurBoyut_surumArtar() {
        long once = servis.acik().logoSurum();
        byte[] png = KurumGorselleri.png();
        var a = servis.logoYaz(png);

        assertThat(a.logoVar()).isTrue();
        assertThat(a.logoSurum()).isGreaterThan(once);
        var logo = servis.logo().orElseThrow();
        assertThat(logo.tur()).isEqualTo("PNG");
        assertThat(logo.bayt()).isEqualTo(png);
        assertThat(jdbc.queryForObject("SELECT ayrinti FROM degisiklik_kaydi WHERE tur = 'KURUM_LOGO'", String.class)).contains("PNG").contains("" + png.length)
                .as("ikili veri denetim kaydına yazılmaz").doesNotContain("iVBOR");
    }

    @Test
    void jpeg_kabul_veTurImzadanCikar() {
        servis.logoYaz(KurumGorselleri.jpeg());
        assertThat(servis.logo().orElseThrow().tur()).isEqualTo("JPEG");
    }

    @Test
    void svgGifWebpBosMetin_reddedilir() {
        reddedilir("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(), "PNG");
        reddedilir(KurumGorselleri.goruntu("gif", 16, 16), "PNG");
        reddedilir("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(), "PNG");
        reddedilir(new byte[0], "boş");
        reddedilir(null, "boş");
        reddedilir("merhaba, bu bir png değil".getBytes(), "PNG");
    }

    @Test
    void uzantiIcerikUyusmazligi_imzaPngAmaGovdeBozuk_reddedilir() {
        byte[] sahte = new byte[200];
        byte[] imza = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
        System.arraycopy(imza, 0, sahte, 0, imza.length);
        reddedilir(sahte, "okunamadı");
        reddedilir(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0 }, "okunamadı");
    }

    @Test
    void asiriPiksel_veBuyukDosya_reddedilir() {
        reddedilir(KurumGorselleri.goruntu("png", 2000, 2000), "1024");
        reddedilir(KurumGorselleri.goruntu("png", 1025, 10), "1024");
        reddedilir(new byte[300 * 1024], "256 KB");
        assertThat(servis.logoYaz(KurumGorselleri.goruntu("png", 1024, 16)).logoVar()).as("sınır dahil").isTrue();
    }

    @Test
    void logoSil_veAdKurallari_veDenetim() {
        servis.logoYaz(KurumGorselleri.png());
        assertThat(servis.logoSil().logoVar()).isFalse();
        assertThat(servis.logo()).isEmpty();

        assertThat(servis.adYaz("  Örnek İşletme  ").ad()).isEqualTo("Örnek İşletme");
        assertThat(servis.ad()).contains("Örnek İşletme");
        assertThatThrownBy(() -> servis.adYaz("   ")).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> servis.adYaz(null)).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> servis.adYaz("x".repeat(121))).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> servis.adYaz("Ad\u0000Gizli")).isInstanceOf(UygulamaHatasi.class);
        assertThat(servis.adYaz("y".repeat(120)).ad()).hasSize(120);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'KURUM_GUNCELLE'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT eski FROM degisiklik_kaydi WHERE tur = 'KURUM_GUNCELLE' ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("Örnek İşletme");
    }

    @Test
    void sihirbazGizle_tercihYazar() {
        assertThat(servis.sihirbazGizli()).isFalse();
        servis.sihirbazGizle(true);
        assertThat(servis.sihirbazGizli()).isTrue();
        servis.sihirbazGizle(false);
        assertThat(servis.sihirbazGizli()).isFalse();
    }
}
