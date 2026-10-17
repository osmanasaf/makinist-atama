package com.makinistatama.makinist;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Makinist kartı. TC kimlik, tanı ve telefon alanı bilerek yok (NFR-07). Bröve ve izin ayrı tablolarda, düz {@code Long} FK. */
@Entity
@Table(name = "makinist")
public class Makinist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sicil;

    private String adSoyad;

    private Long bagliGarId;

    @Enumerated(EnumType.STRING)
    private MakinistDurum durum;

    /** F3b (MK-82): ömür boyu sabit; yalnız ilk kayıtta verilir. */
    @Enumerated(EnumType.STRING)
    private PersonelTuru tur = PersonelTuru.MAKINIST;

    private LocalDate ehliyetBitis;

    private LocalDate saglikBitis;

    private LocalDate psikoteknikBitis;

    private LocalDate sonSurus;

    @Version
    private long versiyon;

    protected Makinist() {
    }

    Makinist(String sicil, PersonelTuru tur) {
        this.sicil = sicil;
        this.tur = tur;
    }

    void guncelle(String adSoyad, Long bagliGarId, MakinistDurum durum, LocalDate ehliyetBitis, LocalDate saglikBitis, LocalDate psikoteknikBitis,
            LocalDate sonSurus) {
        this.adSoyad = adSoyad;
        this.bagliGarId = bagliGarId;
        this.durum = durum;
        this.ehliyetBitis = ehliyetBitis;
        this.saglikBitis = saglikBitis;
        this.psikoteknikBitis = psikoteknikBitis;
        this.sonSurus = sonSurus;
    }

    public Long id() {
        return id;
    }

    public String sicil() {
        return sicil;
    }

    public String adSoyad() {
        return adSoyad;
    }

    public Long bagliGarId() {
        return bagliGarId;
    }

    public MakinistDurum durum() {
        return durum;
    }

    public PersonelTuru tur() {
        return tur;
    }

    public LocalDate ehliyetBitis() {
        return ehliyetBitis;
    }

    public LocalDate saglikBitis() {
        return saglikBitis;
    }

    public LocalDate psikoteknikBitis() {
        return psikoteknikBitis;
    }

    public LocalDate sonSurus() {
        return sonSurus;
    }

    public long versiyon() {
        return versiyon;
    }
}
