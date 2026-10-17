package com.makinistatama.tanim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Genel tanım satırı: gar, hat ya da araç tipi; (tip, ad) benzersiz. Silme yok, yeniden adlandırma var. */
@Entity
@Table(name = "tanim")
public class Tanim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TanimTipi tip;

    private String ad;

    protected Tanim() {
    }

    Tanim(TanimTipi tip, String ad) {
        this.tip = tip;
        this.ad = ad;
    }

    void adDegistir(String yeniAd) {
        this.ad = yeniAd;
    }

    public Long id() {
        return id;
    }

    public TanimTipi tip() {
        return tip;
    }

    public String ad() {
        return ad;
    }
}
