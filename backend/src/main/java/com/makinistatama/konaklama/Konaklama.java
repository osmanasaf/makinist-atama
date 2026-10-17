package com.makinistatama.konaklama;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Makinistin dış garda kaldığı aralık [giris, cikis) (F2b, MK-43). Düz {@code Long} FK; kim/ne zaman yazdığı denetim kaydındadır. */
@Entity
@Table(name = "konaklama")
public class Konaklama {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long makinistId;

    private Long garId;

    @Enumerated(EnumType.STRING)
    private KonaklamaYerTipi yerTipi;

    private Instant giris;

    private Instant cikis;

    private String notu;

    @Version
    private long versiyon;

    protected Konaklama() {
    }

    Konaklama(Long makinistId, Long garId, KonaklamaYerTipi yerTipi, Instant giris, Instant cikis, String notu) {
        degistir(makinistId, garId, yerTipi, giris, cikis, notu);
    }

    void degistir(Long makinistId, Long garId, KonaklamaYerTipi yerTipi, Instant giris, Instant cikis, String notu) {
        this.makinistId = makinistId;
        this.garId = garId;
        this.yerTipi = yerTipi;
        this.giris = giris;
        this.cikis = cikis;
        this.notu = notu;
    }

    Long id() {
        return id;
    }

    Long makinistId() {
        return makinistId;
    }

    Long garId() {
        return garId;
    }

    KonaklamaYerTipi yerTipi() {
        return yerTipi;
    }

    Instant giris() {
        return giris;
    }

    Instant cikis() {
        return cikis;
    }

    String notu() {
        return notu;
    }

    long versiyon() {
        return versiyon;
    }
}
