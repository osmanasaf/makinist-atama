package com.makinistatama.parametre;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.SadeceYonetici;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Kural profili uçları (MK-163): hepsi yalnız YONETICI. Önizleme ve uygulama gövdeleri ham bayttır (en çok 1 MB okunur, sonra {@link ProfilJson} ile çözülür):
 * dosya yükleme gibi sınırlanır, bozuk içerik 400 {@code GECERSIZ_DOSYA}. Önizleme yazmaz; uygulama tek işlemde tüm-ya-hiç yazar.
 */
@RestController
@RequestMapping("/api/profil")
public class ProfilController {

    private final ProfilServisi servis;

    ProfilController(ProfilServisi servis) {
        this.servis = servis;
    }

    @GetMapping("/disa-aktar")
    @SadeceYonetici
    public ResponseEntity<byte[]> disaAktar() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("makinist-atama-profil.json").build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(ProfilJson.yaz(servis.disaAktar()));
    }

    @GetMapping("/hazir")
    @SadeceYonetici
    public List<ProfilServisi.HazirOzet> hazirListe() {
        return servis.hazirListe();
    }

    @GetMapping("/hazir/{kod}")
    @SadeceYonetici
    public ProfilPaketi hazir(@PathVariable String kod) {
        return servis.hazir(kod);
    }

    @PostMapping("/onizle")
    @SadeceYonetici
    public ProfilServisi.Onizleme onizle(HttpServletRequest istek) throws IOException {
        return servis.onizle(ProfilJson.oku(ProfilJson.govde(istek.getInputStream())));
    }

    @PostMapping("/uygula")
    @SadeceYonetici
    public ProfilServisi.UygulaSonucu uygula(HttpServletRequest istek) throws IOException {
        return servis.uygula(ProfilJson.okuUygula(ProfilJson.govde(istek.getInputStream())));
    }
}
