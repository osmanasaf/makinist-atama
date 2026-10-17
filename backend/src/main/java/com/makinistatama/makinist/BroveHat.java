package com.makinistatama.makinist;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bröve: makinistin hat yetkisi; hat sınav tarihi ve o hatta son sürüş (K-06, K-07). */
@Entity
@Table(name = "brove_hat")
public class BroveHat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long makinistId;

    private Long hatId;

    private LocalDate sinavTarihi;

    private LocalDate sonSurus;

    protected BroveHat() {
    }

    BroveHat(Long makinistId, Long hatId, LocalDate sinavTarihi, LocalDate sonSurus) {
        this.makinistId = makinistId;
        this.hatId = hatId;
        this.sinavTarihi = sinavTarihi;
        this.sonSurus = sonSurus;
    }

    public Long makinistId() {
        return makinistId;
    }

    public Long hatId() {
        return hatId;
    }

    public LocalDate sinavTarihi() {
        return sinavTarihi;
    }

    public LocalDate sonSurus() {
        return sonSurus;
    }
}
