package com.makinistatama.konaklama;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;
import com.makinistatama.makinist.PersonelTuru;

import jakarta.validation.Valid;

/** Dış gar konaklaması (F2b, MK-43): giriş yapmış personel (ATAMACI ve YONETICI) yazar ve okur; MAKINIST yalnız {@code /api/benim/konaklama} ile kendi kaydını okur. */
@RestController
@RequestMapping("/api/konaklama")
@Personel
public class KonaklamaController {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final KonaklamaServisi servis;
    private final KonaklamaRaporServisi rapor;

    KonaklamaController(KonaklamaServisi servis, KonaklamaRaporServisi rapor) {
        this.servis = servis;
        this.rapor = rapor;
    }

    @GetMapping
    public List<KonaklamaDto> liste(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit,
            @RequestParam(required = false) Long makinistId) {
        return servis.liste(bas, bit, makinistId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KonaklamaDto ekle(@Valid @RequestBody KonaklamaIstegi istek) {
        return servis.ekle(istek);
    }

    @PutMapping("/{id}")
    public KonaklamaDto guncelle(@PathVariable long id, @Valid @RequestBody KonaklamaIstegi istek) {
        return servis.guncelle(id, istek);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sil(@PathVariable long id) {
        servis.sil(id);
    }

    /** Dış garda iki nöbet arası boşluklardan konaklama önerileri (otomatik yazılmaz). */
    @GetMapping("/oneri")
    public List<KonaklamaOneriDto> oneri(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bas,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bit, @RequestParam(required = false) Long makinistId,
            @RequestParam(required = false) PersonelTuru tur) {
        return servis.oner(bas, bit, makinistId, tur == null ? PersonelTuru.MAKINIST : tur);
    }

    @GetMapping("/rapor")
    public KonaklamaRaporServisi.KonaklamaRaporuDto rapor(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay) {
        return rapor.rapor(ay);
    }

    @GetMapping("/rapor/excel")
    public ResponseEntity<byte[]> excel(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth ay) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("konaklama-raporu-" + ay + ".xlsx").build().toString())
                .body(rapor.excel(rapor.rapor(ay)));
    }
}
