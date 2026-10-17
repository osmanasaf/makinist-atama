package com.makinistatama.parametre;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code kural_parametre} satırı (anahtar-değer). Değer metin olarak saklanır; {@code kural.Parametreler} tür güvenli okur. */
@Entity
@Table(name = "kural_parametre")
public class KuralParametre {

    @Id
    private String anahtar;

    private String deger;

    private String tip;

    private BigDecimal altSinir;

    private BigDecimal ustSinir;

    private String birim;

    private String aciklama;

    private String kaynak;

    private String guvenilirlik;

    private String varsayilan;

    private String grup;

    private int sira;

    protected KuralParametre() {
    }

    void degerYaz(String yeni) {
        this.deger = yeni;
    }

    public String anahtar() {
        return anahtar;
    }

    public String deger() {
        return deger;
    }

    public String tip() {
        return tip;
    }

    public BigDecimal altSinir() {
        return altSinir;
    }

    public BigDecimal ustSinir() {
        return ustSinir;
    }

    public String birim() {
        return birim;
    }

    public String aciklama() {
        return aciklama;
    }

    public String kaynak() {
        return kaynak;
    }

    public String guvenilirlik() {
        return guvenilirlik;
    }

    public String varsayilan() {
        return varsayilan;
    }

    public String grup() {
        return grup;
    }

    public int sira() {
        return sira;
    }
}
