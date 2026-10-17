package com.makinistatama.makinist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.Personel;

import jakarta.validation.Valid;

/** İzin talebi ve onayı (FR-11): giriş yapmış herkes (ATAMACI ve YONETICI) talep açar, onaylar, reddeder. */
@RestController
@RequestMapping("/api/izin")
@Personel
public class IzinController {

    private final IzinServisi servis;

    IzinController(IzinServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    public List<IzinKaydiDto> liste(@RequestParam(required = false) IzinDurumu durum) {
        return servis.liste(durum);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IzinKaydiDto talep(@Valid @RequestBody IzinTalepIstegi istek) {
        return servis.talepEt(istek);
    }

    @PostMapping("/{id}/onay")
    public IzinKaydiDto onay(@PathVariable long id) {
        return servis.onayla(id);
    }

    @PostMapping("/{id}/red")
    public IzinKaydiDto red(@PathVariable long id) {
        return servis.reddet(id);
    }
}
