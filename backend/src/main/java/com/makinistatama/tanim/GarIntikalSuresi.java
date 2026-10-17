package com.makinistatama.tanim;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Gar çifti intikal süresi (MK-109): yönsüz (garAId < garBId, servis normalize eder). Tanımlar verisidir, Ayarlar DEĞİL; tohumlanmaz (tanımsız çift = "yolculukla ata"da
 * intikal önerisi yok).
 */
@Entity
@Table(name = "gar_intikal_suresi")
@IdClass(GarIntikalSuresi.Anahtar.class)
public class GarIntikalSuresi {

    /** Birleşik anahtar (garAId < garBId). */
    public record Anahtar(Long garAId, Long garBId) implements Serializable {
    }

    @Id
    @Column(name = "gar_a_id")
    private Long garAId;

    @Id
    @Column(name = "gar_b_id")
    private Long garBId;

    private int dakika;

    @Version
    private long versiyon;

    protected GarIntikalSuresi() {
    }

    GarIntikalSuresi(long a, long b, int dakika) {
        this.garAId = Math.min(a, b);
        this.garBId = Math.max(a, b);
        this.dakika = dakika;
    }

    void dakikaDegistir(int yeni) {
        this.dakika = yeni;
    }

    public Long garAId() {
        return garAId;
    }

    public Long garBId() {
        return garBId;
    }

    public int dakika() {
        return dakika;
    }

    public long versiyon() {
        return versiyon;
    }
}
