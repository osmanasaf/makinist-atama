package com.makinistatama.parametre;

import com.makinistatama.kural.Siddet;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code kural_siddet} satırı: kural kodu (K-01..K-21, K-10 hariç) -> ENGEL / UYARI / KAPALI. */
@Entity
@Table(name = "kural_siddet")
public class KuralSiddet {

    @Id
    private String kod;

    @Enumerated(EnumType.STRING)
    private Siddet siddet;

    private String aciklama;

    private String kaynak;

    private String guvenilirlik;

    @Enumerated(EnumType.STRING)
    private Siddet varsayilan;

    private String grup;

    private int sira;

    protected KuralSiddet() {
    }

    void siddetYaz(Siddet yeni) {
        this.siddet = yeni;
    }

    public String kod() {
        return kod;
    }

    public Siddet siddet() {
        return siddet;
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

    public Siddet varsayilan() {
        return varsayilan;
    }

    public String grup() {
        return grup;
    }

    public int sira() {
        return sira;
    }
}
