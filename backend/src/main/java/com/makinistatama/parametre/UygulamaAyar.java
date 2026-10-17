package com.makinistatama.parametre;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code uygulama_ayar} satırı (tamsayı ayar). Kural kataloğu değildir (kural_parametre ayrıdır, MK-29). */
@Entity
@Table(name = "uygulama_ayar")
public class UygulamaAyar {

    @Id
    private String anahtar;

    private int deger;

    private String birim;

    private int alt;

    private int ust;

    private String aciklama;

    private int varsayilan;

    private String grup;

    private int sira;

    protected UygulamaAyar() {
    }

    void degerYaz(int yeni) {
        this.deger = yeni;
    }

    public String anahtar() {
        return anahtar;
    }

    public int deger() {
        return deger;
    }

    public String birim() {
        return birim;
    }

    public int alt() {
        return alt;
    }

    public int ust() {
        return ust;
    }

    public String aciklama() {
        return aciklama;
    }

    public int varsayilan() {
        return varsayilan;
    }

    public String grup() {
        return grup;
    }

    public int sira() {
        return sira;
    }
}
