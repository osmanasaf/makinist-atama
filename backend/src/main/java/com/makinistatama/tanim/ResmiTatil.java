package com.makinistatama.tanim;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Resmi tatil (MK-178): tarih + ad; yalnız gösterim, hiçbir hesabı etkilemez. */
@Entity
@Table(name = "resmi_tatil")
public class ResmiTatil {

    @Id
    @Column(name = "tarih")
    private LocalDate tarih;

    private String ad;

    @Version
    private long versiyon;

    protected ResmiTatil() {
    }

    ResmiTatil(LocalDate tarih, String ad) {
        this.tarih = tarih;
        this.ad = ad;
    }

    public LocalDate tarih() {
        return tarih;
    }

    public String ad() {
        return ad;
    }
}
