package com.makinistatama.atama;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Makinistin elinden alınan görev (kaldırıldı / başkasına verildi): ekranda ≥ 7 gün "kalem" olarak durur, makinist "Gördüm" ile kapatır. FK yok; neden NOTU saklanmaz [V9]. */
@Entity
@Table(name = "atama_olayi")
public class AtamaOlayi {

    public enum Tur {
        KALDIRILDI,
        DEGISTI_BASKASINA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long makinistId;

    private Long seferId;

    @Enumerated(EnumType.STRING)
    private Tur tur;

    private String ozet;

    private String nedenEtiketi;

    private Instant zaman;

    private Instant kapatildiZaman;

    protected AtamaOlayi() {
    }

    AtamaOlayi(Long makinistId, Long seferId, Tur tur, String ozet, String nedenEtiketi, Instant zaman) {
        this.makinistId = makinistId;
        this.seferId = seferId;
        this.tur = tur;
        this.ozet = ozet;
        this.nedenEtiketi = nedenEtiketi;
        this.zaman = zaman;
    }

    Long id() {
        return id;
    }

    Long seferId() {
        return seferId;
    }

    Tur tur() {
        return tur;
    }

    String ozet() {
        return ozet;
    }

    String nedenEtiketi() {
        return nedenEtiketi;
    }

    Instant zaman() {
        return zaman;
    }

    Instant kapatildiZaman() {
        return kapatildiZaman;
    }

    void kapat(Instant simdi) {
        this.kapatildiZaman = simdi;
    }
}
