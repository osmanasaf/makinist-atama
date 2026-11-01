package com.makinistatama.kurum;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Kurum adı + logosu (MK-167). Müşterinin kendi verisidir; ürün hiçbir kurumun markasını taşımaz (OK-44a). Logo kuralları: yalnız PNG/JPEG (tür dosya imzasından çıkar,
 * istemcinin içerik türüne güvenilmez), en çok 256 KB, en çok 1024x1024 piksel (boyut BAŞLIKTAN okunur: piksel verisi çözülmez, genişletme bombası yok). SVG/GIF/WebP kabul edilmez.
 * Her yazma denetim kaydı üretir; ikili veri kayda yazılmaz.
 */
@Service
@Transactional(readOnly = true)
public class KurumServisi {

    static final int EN_FAZLA_BAYT = 256 * 1024;
    static final int EN_FAZLA_PIKSEL = 1024;
    static final int EN_FAZLA_AD = 120;
    private static final short KIMLIK = 1;
    private static final byte[] PNG_IMZASI = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] JPEG_IMZASI = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

    private final KurumRepository depo;
    private final DegisiklikServisi degisiklik;
    private final Clock saat;

    KurumServisi(KurumRepository depo, DegisiklikServisi degisiklik, Clock saat) {
        this.depo = depo;
        this.degisiklik = degisiklik;
        this.saat = saat;
    }

    /** Anonim yüzey ve kabuk: ad + logo var mı + sürüm. Logo baytları okunmaz. */
    public KurumDto.Acik acik() {
        var o = depo.findProjectedById(KIMLIK);
        return new KurumDto.Acik(o.getAd(), o.getLogoBoyut() > 0, o.getVersiyon());
    }

    /** Dolu kurum adı (kırpılmış); girilmemişse boş. */
    public Optional<String> ad() {
        return Optional.ofNullable(depo.findProjectedById(KIMLIK).getAd()).filter(a -> !a.isBlank());
    }

    public boolean sihirbazGizli() {
        return satir().sihirbazGizli();
    }

    public Optional<KurumDto.Logo> logo() {
        Kurum k = satir();
        return k.logo() == null ? Optional.empty() : Optional.of(new KurumDto.Logo(k.logo(), k.logoTur(), depo.findProjectedById(KIMLIK).getVersiyon()));
    }

    @Transactional
    public KurumDto.Acik adYaz(String ham) {
        String ad = ham == null ? "" : ham.strip();
        if (ad.isEmpty()) {
            throw UygulamaHatasi.dogrulama("ad", "Kurum adı boş olamaz.");
        }
        if (ad.length() > EN_FAZLA_AD) {
            throw UygulamaHatasi.dogrulama("ad", "Kurum adı en fazla " + EN_FAZLA_AD + " karakter olabilir.");
        }
        if (ad.chars().anyMatch(Character::isISOControl)) {
            throw UygulamaHatasi.dogrulama("ad", "Kurum adı kontrol karakteri içeremez.");
        }
        Kurum k = satir();
        String eski = k.ad();
        if (!ad.equals(eski)) {
            k.adYaz(ad, saat.instant());
            depo.saveAndFlush(k);
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.KURUM_GUNCELLE, "KURUM", (long) KIMLIK).degisim(eski, ad));
        }
        return acik();
    }

    @Transactional
    public KurumDto.Acik logoYaz(byte[] bayt) {
        String tur = logoDogrula(bayt);
        Kurum k = satir();
        k.logoYaz(bayt, tur, saat.instant());
        depo.saveAndFlush(k);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.KURUM_LOGO, "KURUM", (long) KIMLIK).degisim(null, tur).ayrinti("{\"tur\":\"" + tur + "\",\"boyut\":" + bayt.length + "}"));
        return acik();
    }

    @Transactional
    public KurumDto.Acik logoSil() {
        Kurum k = satir();
        if (k.logo() != null) {
            String eski = k.logoTur();
            k.logoYaz(null, null, saat.instant());
            depo.saveAndFlush(k);
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.KURUM_LOGO, "KURUM", (long) KIMLIK).degisim(eski, null).ayrinti("{\"islem\":\"SIL\"}"));
        }
        return acik();
    }

    /** Kurulum sihirbazı kartını gizler / geri getirir (MK-171). */
    @Transactional
    public void sihirbazGizle(boolean gizli) {
        Kurum k = satir();
        if (k.sihirbazGizli() != gizli) {
            k.sihirbazGizle(gizli, saat.instant());
            depo.saveAndFlush(k);
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.KURUM_GUNCELLE, "KURUM", (long) KIMLIK).degisim("sihirbazGizli=" + !gizli, "sihirbazGizli=" + gizli));
        }
    }

    private Kurum satir() {
        return depo.findById(KIMLIK).orElseThrow(() -> new IllegalStateException("kurum satırı yok (V34)"));
    }

    /** Logo baytlarını doğrular, türü ("PNG" | "JPEG") döndürür. */
    static String logoDogrula(byte[] bayt) {
        if (bayt == null || bayt.length == 0) {
            throw UygulamaHatasi.gecersizDosya("Logo dosyası boş.");
        }
        if (bayt.length > EN_FAZLA_BAYT) {
            throw UygulamaHatasi.gecersizDosya("Logo en fazla 256 KB olabilir.");
        }
        String tur;
        if (basliyor(bayt, PNG_IMZASI)) {
            tur = "PNG";
        } else if (basliyor(bayt, JPEG_IMZASI)) {
            tur = "JPEG";
        } else {
            throw UygulamaHatasi.gecersizDosya("Logo yalnız PNG veya JPEG olabilir (SVG, GIF, WebP kabul edilmez).");
        }
        Dimension boyut = baslikBoyutu(bayt, tur);
        if (boyut.width < 1 || boyut.height < 1 || boyut.width > EN_FAZLA_PIKSEL || boyut.height > EN_FAZLA_PIKSEL) {
            throw UygulamaHatasi.gecersizDosya("Logo en çok " + EN_FAZLA_PIKSEL + " x " + EN_FAZLA_PIKSEL + " piksel olabilir.");
        }
        return tur;
    }

    private static boolean basliyor(byte[] bayt, byte[] imza) {
        if (bayt.length < imza.length) {
            return false;
        }
        for (int i = 0; i < imza.length; i++) {
            if (bayt[i] != imza[i]) {
                return false;
            }
        }
        return true;
    }

    /** Genişlik/yükseklik yalnız dosya başlığından (piksel verisi çözülmez). Okuyucu biçimi imzayla uyuşmalı. */
    private static Dimension baslikBoyutu(byte[] bayt, String tur) {
        try (ImageInputStream akim = new MemoryCacheImageInputStream(new ByteArrayInputStream(bayt))) {
            Iterator<ImageReader> okuyucular = ImageIO.getImageReaders(akim);
            if (!okuyucular.hasNext()) {
                throw UygulamaHatasi.gecersizDosya("Logo görüntüsü okunamadı.");
            }
            ImageReader okuyucu = okuyucular.next();
            try {
                String bicim = okuyucu.getFormatName().toLowerCase(Locale.ROOT);
                boolean uyumlu = tur.equals("PNG") ? bicim.equals("png") : (bicim.equals("jpeg") || bicim.equals("jpg"));
                if (!uyumlu) {
                    throw UygulamaHatasi.gecersizDosya("Logo içeriği dosya türüyle uyuşmuyor.");
                }
                okuyucu.setInput(akim, true, true);
                return new Dimension(okuyucu.getWidth(0), okuyucu.getHeight(0));
            } finally {
                okuyucu.dispose();
            }
        } catch (UygulamaHatasi e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw UygulamaHatasi.gecersizDosya("Logo görüntüsü okunamadı.");
        }
    }
}
