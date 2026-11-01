package com.makinistatama.parametre;

import java.io.IOException;

import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ProfilPaketi.UygulaIstegi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Profil paketinin JSON okuma/yazması ve sınırları (MK-162, MK-163). Çerçeveden bağımsız (kendi {@link JsonMapper}'ı): bilinmeyen üst düzey alanlar yok sayılır (ileri uyum),
 * bozuk/boyutu aşan/yanlış biçimli içerik 400 {@code GECERSIZ_DOSYA}, desteklenenden büyük sürüm 400 {@code PROFIL_SURUMU}. Gövde en çok {@link #EN_COK_BAYT} bayt okunur.
 */
final class ProfilJson {

    static final int EN_COK_BAYT = 1_048_576;
    static final int EN_COK_GIRDI = 400;
    static final int EN_COK_OZEL_KURAL = 100;

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private ProfilJson() {
    }

    /** İstek gövdesinden en çok sınır + 1 bayt okur; sınırı aşan gövde 400 (bellekte sınırsız tamponlanmaz). */
    static byte[] govde(java.io.InputStream akim) throws IOException {
        byte[] b = akim.readNBytes(EN_COK_BAYT + 1);
        if (b.length > EN_COK_BAYT) {
            throw UygulamaHatasi.gecersizDosya("Profil dosyası çok büyük (en çok 1 MB).");
        }
        return b;
    }

    static ProfilPaketi oku(byte[] bayt) {
        return kontrol(coz(bayt, ProfilPaketi.class));
    }

    static UygulaIstegi okuUygula(byte[] bayt) {
        UygulaIstegi i = coz(bayt, UygulaIstegi.class);
        if (i.paket() == null) {
            throw UygulamaHatasi.gecersizDosya("Profil paketi eksik.");
        }
        kontrol(i.paket());
        return new UygulaIstegi(i.paket(), i.secilen() == null ? java.util.List.of() : i.secilen(), i.neden());
    }

    static byte[] yaz(ProfilPaketi paket) {
        try {
            return JSON.writeValueAsBytes(paket);
        } catch (JacksonException e) {
            throw new IllegalStateException("Profil yazılamadı", e);
        }
    }

    static <T> T coz(byte[] bayt, Class<T> tur) {
        if (bayt == null || bayt.length == 0) {
            throw UygulamaHatasi.gecersizDosya("Profil dosyası boş.");
        }
        if (bayt.length > EN_COK_BAYT) {
            throw UygulamaHatasi.gecersizDosya("Profil dosyası çok büyük (en çok 1 MB).");
        }
        try {
            T sonuc = JSON.readValue(bayt, tur);
            if (sonuc == null) {
                throw UygulamaHatasi.gecersizDosya("Profil dosyası boş.");
            }
            return sonuc;
        } catch (JacksonException e) { // ayrıntı (kişisel veri/yığın) yanıta sızmaz
            throw UygulamaHatasi.gecersizDosya("Profil dosyası okunamadı: geçerli bir JSON profil dosyası değil.");
        }
    }

    private static ProfilPaketi kontrol(ProfilPaketi p) {
        if (!ProfilPaketi.FORMAT.equals(p.format())) {
            throw UygulamaHatasi.gecersizDosya("Bu dosya bir Makinist Atama profil dosyası değil.");
        }
        if (p.profilSurumu() == null) {
            throw UygulamaHatasi.gecersizDosya("Profil sürümü yazılmamış.");
        }
        if (p.profilSurumu() > ProfilPaketi.SURUM) {
            throw new UygulamaHatasi(org.springframework.http.HttpStatus.BAD_REQUEST, "PROFIL_SURUMU",
                    "Bu profil daha yeni bir sürümle hazırlanmış (sürüm " + p.profilSurumu() + "); bu uygulama sürüm " + ProfilPaketi.SURUM + "'e kadar okur.", null);
        }
        if (p.ozelKurallar().size() > EN_COK_OZEL_KURAL) {
            throw UygulamaHatasi.gecersizDosya("Profilde en çok " + EN_COK_OZEL_KURAL + " özel kural olabilir.");
        }
        int girdi = p.parametreler().size() + p.siddetler().size() + p.ayarlar().size() + p.ozelKurallar().size();
        if (girdi > EN_COK_GIRDI) {
            throw UygulamaHatasi.gecersizDosya("Profilde en çok " + EN_COK_GIRDI + " girdi olabilir.");
        }
        return p;
    }
}
