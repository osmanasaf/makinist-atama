package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.degisiklik.Neden;
import com.makinistatama.guvenlik.Personel;

import jakarta.validation.Valid;

/** Giriş yapmış herkes (ATAMACI ve YONETICI) atama yapar; kural denetimi sunucudadır (ENGEL'li atama yazılamaz). */
@RestController
@RequestMapping("/api/atama")
@Personel
public class AtamaController {

    private final AtamaServisi servis;
    private final GunListesiServisi gunListesi;
    private final OtomatikAtamaServisi otomatik;
    private final YolculukServisi yolculuk;
    private final YakinlikServisi yakinlik;

    AtamaController(AtamaServisi servis, GunListesiServisi gunListesi, OtomatikAtamaServisi otomatik, YolculukServisi yolculuk, YakinlikServisi yakinlik) {
        this.servis = servis;
        this.gunListesi = gunListesi;
        this.otomatik = otomatik;
        this.yolculuk = yolculuk;
        this.yakinlik = yakinlik;
    }

    /** "Yerine ata": mevcut makinist hariç en uygun ilk 3 aday gerekçesiyle (atama yine {@code POST /sefer/{id}} ile yapılır). */
    @GetMapping("/sefer/{seferId}/oneri")
    public OneriDto oneri(@PathVariable long seferId) {
        return servis.oner(seferId);
    }

    /** F2b: makinistin bu seferden önce yolcu olarak gidebileceği en çok 3 sefer (isteğe bağlı uç; aday listesine gömülmez). */
    @GetMapping("/sefer/{seferId}/yolculuk-oner")
    public YolculukOneriDto yolculukOner(@PathVariable long seferId, @RequestParam long makinistId) {
        return yolculuk.oner(seferId, makinistId);
    }

    /** F2b: tek işlemde yolculuk (YOLCU) + bu sefere ASIL; herhangi bir ayak reddedilirse hiçbiri yazılmaz. */
    @PostMapping("/sefer/{seferId}/yolculukla")
    public ResponseEntity<YolculuklaAtamaDto> yolculukla(@PathVariable long seferId, @Valid @RequestBody YolculukluAtamaIstegi istek) {
        return ResponseEntity.status(HttpStatus.CREATED).body(yolculuk.yolculukla(seferId, istek));
    }

    /** İ-2 (MK-110): tek işlemde serbest intikal (gar süre tablosundan, saat sunucuda) + bu sefere ASIL; biri reddedilirse hiçbiri yazılmaz (hata gövdesinde {@code adim}). */
    @PostMapping("/sefer/{seferId}/intikalle")
    public ResponseEntity<IntikalliAtamaDto> intikalle(@PathVariable long seferId, @Valid @RequestBody IntikalliAtamaIstegi istek) {
        return ResponseEntity.status(HttpStatus.CREATED).body(yolculuk.intikalle(seferId, istek));
    }

    /** Aralıktaki kırmızı sefer / atama sayısı (canlı hesap). */
    @GetMapping("/kirmizi-sayisi")
    public KirmiziSayisiDto kirmiziSayisi(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit) {
        return gunListesi.kirmiziSayisi(bas, bit);
    }

    /** Atamacı "kim gördü" sayaçları (FR-M10): aralıkta kalkışı geçmemiş atamalar için görülmeyen / hesapsız / uyarı. */
    @GetMapping("/gordum-ozeti")
    public GordumOzetiDto gordumOzeti(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit) {
        return gunListesi.gordumOzeti(bas, bit);
    }

    /** Boşta kalan makinistin, günün makinistsiz seferlerinden ENGEL'siz atanabilecekleri. */
    @GetMapping("/makinist/{makinistId}/uygun-seferler")
    public List<UygunSeferDto> uygunSeferler(@PathVariable long makinistId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gun) {
        return servis.uygunSeferler(makinistId, gun);
    }

    /** Otomatik atama: {@code uygula=false} önizleme (yazmaz), {@code true} aynı sonucu tek işlemde yazar. */
    @PostMapping("/otomatik")
    public OtomatikAtamaDto otomatikAta(@Valid @RequestBody OtomatikAtamaIstegi istek) {
        return istek.uygula() ? otomatik.uygula(istek) : otomatik.onizle(istek);
    }

    /** F3 (MK-174): adaylar için sınıra yakınlık + yorgunluk BİLGİSİ (aday havuzu {@code adaylar} ile aynı; kural motoru çağrılmaz, {@code AdayDto} değişmez). */
    @GetMapping("/sefer/{seferId}/yakinlik")
    public YakinlikDto adayYakinlik(@PathVariable long seferId, @RequestParam(defaultValue = "ASIL") AtamaRol rol) {
        return yakinlik.aday(seferId, rol);
    }

    /** F3 (MK-174): günün atamaları için sınıra yakınlık + yorgunluk BİLGİSİ ({@code GunListesiDto} değişmez). */
    @GetMapping("/yakinlik")
    public YakinlikDto gunYakinlik(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih) {
        return yakinlik.gun(tarih);
    }

    @GetMapping("/sefer/{seferId}/adaylar")
    public List<AdayDto> adaylar(@PathVariable long seferId, @RequestParam(defaultValue = "ASIL") AtamaRol rol) {
        return servis.adaylar(seferId, rol);
    }

    /** 201: yeni atama; 200: o roldeki makinist değiştirildi. */
    @PostMapping("/sefer/{seferId}")
    public ResponseEntity<AtamaDto> ata(@PathVariable long seferId, @Valid @RequestBody AtamaIstegi istek) {
        AtamaServisi.AtamaSonucu s = servis.ata(seferId, istek);
        return ResponseEntity.status(s.yeni() ? HttpStatus.CREATED : HttpStatus.OK).body(s.atama());
    }

    @DeleteMapping("/sefer/{seferId}")
    public ResponseEntity<KaldirSonucuDto> kaldir(@PathVariable long seferId, @RequestParam(defaultValue = "ASIL") AtamaRol rol, @RequestParam(required = false) Long makinistId,
            @RequestParam(required = false) Neden neden, @RequestParam(required = false) String nedenNotu, @RequestParam(required = false) Long atamaId) {
        // makinistId yalnız rol=YOLCU için zorunlu (bir seferde birden çok yolcu olabilir). atamaId (OK-37, Geri al yarışı): verilirse ve güncel atama o kimlik değilse 409. 204; kaldırma başka atamayı ya da aynı makinistin intikalini yeni kırmızı yaptıysa 200 + etkilenenler
        KaldirSonucuDto s = servis.kaldir(seferId, rol, makinistId, atamaId, neden, nedenNotu);
        return s.yeniKirmizi().isEmpty() && s.yeniKirmiziIntikal().isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(s);
    }

    @GetMapping("/gun")
    public GunListesiDto gun(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih) {
        return gunListesi.gunListesi(tarih);
    }

    @GetMapping("/makinist/{makinistId}/izin-etkisi")
    public List<IzinEtkisiDto> izinEtkisi(@PathVariable long makinistId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit) {
        return gunListesi.izinEtkisi(makinistId, bas, bit);
    }
}
