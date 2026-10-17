package com.makinistatama.atama;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Sefer x makinist x rol (MK-1). Düz {@code Long} FK; "kaldır" satırı siler (geçmiş değişiklik kaydındadır). Kırmızı durum saklanmaz. */
@Entity
@Table(name = "atama")
public class Atama {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seferId;

    private Long makinistId;

    @Enumerated(EnumType.STRING)
    private AtamaRol rol;

    private String atayan;

    private Instant atamaZamani;

    /** Atama anında seferin kalkış gecikmesi (dk); görev başlangıcı planlanan + bu (OK-6, {@code kural.Gorev.calismaAraligi}). */
    private int kalkisGecikmeDk;

    private String gerekce;

    @Version
    private long versiyon;

    protected Atama() {
    }

    Atama(Long seferId, Long makinistId, AtamaRol rol, String atayan, Instant atamaZamani, String gerekce, int kalkisGecikmeDk) {
        this.seferId = seferId;
        this.makinistId = makinistId;
        this.rol = rol;
        this.atayan = atayan;
        this.atamaZamani = atamaZamani;
        this.gerekce = gerekce;
        this.kalkisGecikmeDk = kalkisGecikmeDk;
    }

    public Long id() {
        return id;
    }

    public Long seferId() {
        return seferId;
    }

    public Long makinistId() {
        return makinistId;
    }

    public AtamaRol rol() {
        return rol;
    }

    public String atayan() {
        return atayan;
    }

    public Instant atamaZamani() {
        return atamaZamani;
    }

    public String gerekce() {
        return gerekce;
    }

    public int kalkisGecikmeDk() {
        return kalkisGecikmeDk;
    }
}
