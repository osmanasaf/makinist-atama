package com.makinistatama.parametre;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** {@code ozel_kural} satırı (MK-16): kapsam {@code kural.Kapsam} JSON metnidir; silme yok, kapatma = şiddet KAPALI. */
@Entity
@Table(name = "ozel_kural")
public class OzelKuralKaydi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ad;

    @Enumerated(EnumType.STRING)
    private Olcu olcu;

    @Enumerated(EnumType.STRING)
    private Pencere pencere;

    private Integer kayanGun;

    @Enumerated(EnumType.STRING)
    private Karsilastirma karsilastirma;

    private BigDecimal esik;

    private String kapsam;

    @Enumerated(EnumType.STRING)
    private Siddet siddet;

    private LocalDate gecerlilikBas;

    private LocalDate gecerlilikBit;

    private String gerekce;

    private String kaynak;

    @Version
    private long versiyon;

    private String olusturan;

    private Instant olusturma;

    private Instant guncelleme;

    protected OzelKuralKaydi() {
    }

    OzelKuralKaydi(String olusturan, Instant an) {
        this.olusturan = olusturan;
        this.olusturma = an;
        this.guncelleme = an;
    }

    void yaz(String ad, Olcu olcu, Pencere pencere, Integer kayanGun, Karsilastirma karsilastirma, BigDecimal esik, String kapsam, Siddet siddet, LocalDate gecerlilikBas,
            LocalDate gecerlilikBit, String gerekce, String kaynak, Instant an) {
        this.ad = ad;
        this.olcu = olcu;
        this.pencere = pencere;
        this.kayanGun = kayanGun;
        this.karsilastirma = karsilastirma;
        this.esik = esik;
        this.kapsam = kapsam;
        this.siddet = siddet;
        this.gecerlilikBas = gecerlilikBas;
        this.gecerlilikBit = gecerlilikBit;
        this.gerekce = gerekce;
        this.kaynak = kaynak;
        this.guncelleme = an;
    }

    public Long id() {
        return id;
    }

    public String ad() {
        return ad;
    }

    public Olcu olcu() {
        return olcu;
    }

    public Pencere pencere() {
        return pencere;
    }

    public Integer kayanGun() {
        return kayanGun;
    }

    public Karsilastirma karsilastirma() {
        return karsilastirma;
    }

    public BigDecimal esik() {
        return esik;
    }

    public String kapsam() {
        return kapsam;
    }

    public Siddet siddet() {
        return siddet;
    }

    public LocalDate gecerlilikBas() {
        return gecerlilikBas;
    }

    public LocalDate gecerlilikBit() {
        return gecerlilikBit;
    }

    public String gerekce() {
        return gerekce;
    }

    public String kaynak() {
        return kaynak;
    }

    public long versiyon() {
        return versiyon;
    }

    public String olusturan() {
        return olusturan;
    }

    public Instant olusturma() {
        return olusturma;
    }

    public Instant guncelleme() {
        return guncelleme;
    }
}
