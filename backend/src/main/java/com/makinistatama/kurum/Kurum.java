package com.makinistatama.kurum;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Kurum adı + logosu (MK-167): tek satır ({@code id = 1}, V34 ekler). Logo {@code bytea}; {@code versiyon} logo ETag'ıdır. */
@Entity
@Table(name = "kurum")
class Kurum {

    @Id
    private Short id;

    private String ad;

    @Column(columnDefinition = "bytea")
    private byte[] logo;

    @Column(name = "logo_tur")
    private String logoTur;

    @Column(name = "logo_boyut")
    private int logoBoyut;

    @Column(name = "sihirbaz_gizli")
    private boolean sihirbazGizli;

    @Version
    private long versiyon;

    private Instant guncelleme;

    protected Kurum() {
    }

    String ad() {
        return ad;
    }

    byte[] logo() {
        return logo;
    }

    String logoTur() {
        return logoTur;
    }

    boolean sihirbazGizli() {
        return sihirbazGizli;
    }

    void adYaz(String ad, Instant simdi) {
        this.ad = ad;
        this.guncelleme = simdi;
    }

    void logoYaz(byte[] logo, String tur, Instant simdi) {
        this.logo = logo;
        this.logoTur = tur;
        this.logoBoyut = logo == null ? 0 : logo.length;
        this.guncelleme = simdi;
    }

    void sihirbazGizle(boolean gizli, Instant simdi) {
        this.sihirbazGizli = gizli;
        this.guncelleme = simdi;
    }
}
