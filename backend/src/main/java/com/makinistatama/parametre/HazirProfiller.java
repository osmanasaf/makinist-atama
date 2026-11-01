package com.makinistatama.parametre;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Hazır başlangıç profilleri (MK-165, OK-45): classpath'teki {@code profil/hazir/<kod>.json} dosyaları YALNIZ FARKI taşır (öneri + dayanak). Çözülürken taban
 * veritabanındaki {@code varsayilan} sütunlarıdır ({@link ProfilServisi#hazir}); kodda değer tablosu yoktur. Her profil "örnek başlangıç, kurum mevzuatıyla doğrulanmalı"
 * etiketi taşır ve her değişen değerin {@code dayanak}ı projedeki {@code kaynak} metninde geçer ({@code HazirProfillerTest}). Dosya bozuksa uygulama başlarken hata verir.
 */
@Component
class HazirProfiller {

    /** Sıra ekranda gösterilen sıradır. */
    static final List<String> KODLAR = List.of("varsayilan", "ab-2005-47", "4857-siki-haftalik");

    record Oneri(Map<String, String> parametreler, Map<String, String> siddetler, Map<String, Number> ayarlar) {
        Oneri {
            parametreler = parametreler == null ? Map.of() : parametreler;
            siddetler = siddetler == null ? Map.of() : siddetler;
            ayarlar = ayarlar == null ? Map.of() : ayarlar;
        }
    }

    record Profil(String kod, String ad, String aciklama, String etiket, Oneri oneri, Map<String, String> dayanak) {
        Profil {
            oneri = oneri == null ? new Oneri(null, null, null) : oneri;
            dayanak = dayanak == null ? Map.of() : dayanak;
        }
    }

    private final List<Profil> profiller;

    HazirProfiller() {
        this.profiller = KODLAR.stream().map(HazirProfiller::oku).toList();
    }

    List<Profil> hepsi() {
        return profiller;
    }

    Optional<Profil> bul(String kod) {
        return profiller.stream().filter(p -> p.kod().equals(kod)).findFirst();
    }

    private static Profil oku(String kod) {
        try (InputStream akim = HazirProfiller.class.getResourceAsStream("/profil/hazir/" + kod + ".json")) {
            if (akim == null) {
                throw new IllegalStateException("Hazır profil dosyası yok: " + kod);
            }
            Profil p = ProfilJson.coz(akim.readAllBytes(), Profil.class);
            if (!kod.equals(p.kod()) || p.etiket() == null || p.etiket().isBlank()) {
                throw new IllegalStateException("Hazır profil bozuk (kod ya da etiket): " + kod);
            }
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("Hazır profil okunamadı: " + kod, e);
        }
    }
}
