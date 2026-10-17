package com.makinistatama.benim;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.atama.MakinistGorevDto;
import com.makinistatama.atama.MakinistIntikalDto;
import com.makinistatama.guvenlik.SadeceMakinist;
import com.makinistatama.konaklama.MakinistKonaklamaDto;
import com.makinistatama.makinist.MakinistIzinDto;
import com.makinistatama.rapor.MakinistBelgeDto;
import com.makinistatama.rapor.MakinistOzetDto;
import com.makinistatama.takas.MakinistTakasDto;
import com.makinistatama.takas.TakasAcIstegi;
import com.makinistatama.takas.TakasHedefDto;

import jakarta.validation.Valid;

/**
 * Makinistin kendi uçları (MK-28): beyan yalnız sınıf düzeyinde {@link SadeceMakinist}; hiçbir uç makinist/kullanıcı kimliği almaz (yol, sorgu, gövde): kimlik
 * {@link BenimKimlik#kisi()} ile {@link BenimKapisi}'ndan gelir. Yabancı kaynak kimliği (ileride atama/takas) 404 olur, varlık sızdırılmaz.
 */
@RestController
@RequestMapping("/api/benim")
@SadeceMakinist
public class BenimController {

    private final BenimServisi servis;

    BenimController(BenimServisi servis) {
        this.servis = servis;
    }

    @GetMapping("/gorevler")
    public BenimServisi.GorevlerDto gorevler() {
        return servis.gorevler(BenimKimlik.kisi());
    }

    /** "Gördüm": 200 ve güncel görev; ikinci basış aynı yanıtı verir (idempotent). Yabancı/ufuk dışı atama 404. */
    @PostMapping("/gorevler/{atamaId}/gordum")
    public MakinistGorevDto gordum(@PathVariable long atamaId, @Valid @RequestBody GordumIstegi istek) {
        return servis.gordum(BenimKimlik.kisi(), atamaId, istek.ozet());
    }

    /** Serbest intikal "gördüm" (MK-108): 200 ve güncel intikal; ikinci basış aynı yanıtı verir (idempotent). Yabancı/ufuk dışı intikal 404; bayat özet 409. */
    @PostMapping("/intikal/{id}/gordum")
    public MakinistIntikalDto intikalGordum(@PathVariable long id, @Valid @RequestBody GordumIstegi istek) {
        return servis.intikalGordum(BenimKimlik.kisi(), id, istek.ozet());
    }

    /** Elinden alınan görev kalemini kapatır ("Gördüm"); yabancı kalem 404. */
    @PostMapping("/olaylar/{olayId}/gordum")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void olayiKapat(@PathVariable long olayId) {
        servis.olayiKapat(BenimKimlik.kisi(), olayId);
    }

    // ---- takas / görev bırakma (FR-M11..M13): kimlik oturumdan; yabancı talep / görev 404

    /** Taraf olduğum açık talepler ve son 7 günde kapananlar (durum okuma anında türetilir). */
    @GetMapping("/takas")
    public List<MakinistTakasDto> takaslar() {
        return servis.takaslar(BenimKimlik.kisi());
    }

    /** Takas için seçilebilir başkalarının görevleri (kural motoru çalışmaz, süzme yok; yalnız sicil + sefer özeti). */
    @GetMapping("/takas/hedefler")
    public List<TakasHedefDto> takasHedefler(@RequestParam long atamaId) {
        return servis.takasHedefler(BenimKimlik.kisi(), atamaId);
    }

    /** {@code hedefAtamaId} dolu = karşılıklı takas, boş = görevi bırakma. */
    @PostMapping("/takas")
    @ResponseStatus(HttpStatus.CREATED)
    public MakinistTakasDto takasAc(@Valid @RequestBody TakasAcIstegi istek) {
        return servis.takasAc(BenimKimlik.kisi(), istek);
    }

    @PostMapping("/takas/{id}/kabul")
    public MakinistTakasDto takasKabul(@PathVariable long id) {
        return servis.takasKabul(BenimKimlik.kisi(), id);
    }

    @PostMapping("/takas/{id}/red")
    public MakinistTakasDto takasRed(@PathVariable long id) {
        return servis.takasRed(BenimKimlik.kisi(), id);
    }

    @PostMapping("/takas/{id}/iptal")
    public MakinistTakasDto takasIptal(@PathVariable long id) {
        return servis.takasIptal(BenimKimlik.kisi(), id);
    }

    @GetMapping("/belgeler")
    public MakinistBelgeDto belgeler() {
        return servis.belgeler(BenimKimlik.kisi());
    }

    @GetMapping("/ozet")
    public MakinistOzetDto ozet(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay) {
        return servis.ozet(BenimKimlik.kisi(), ay);
    }

    /** Makinistin kendi konaklaması (görev ufkundaki): nerede kalacağını bilmesi gerekir (OK-10). */
    @GetMapping("/konaklama")
    public List<MakinistKonaklamaDto> konaklama() {
        return servis.konaklamalar(BenimKimlik.kisi());
    }

    @GetMapping("/izinler")
    public List<MakinistIzinDto> izinler() {
        return servis.izinler(BenimKimlik.kisi());
    }

    @PostMapping("/izinler")
    @ResponseStatus(HttpStatus.CREATED)
    public MakinistIzinDto izinTalepEt(@Valid @RequestBody BenimIzinIstegi istek) {
        return servis.izinTalepEt(BenimKimlik.kisi(), istek.baslangic(), istek.bitis(), istek.tur());
    }
}
