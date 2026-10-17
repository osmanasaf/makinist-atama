package com.makinistatama.makinist;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bröve: makinistin kullanabildiği araç tipi ve sınav tarihi (geçerlilik = sınav + {@code belge.sinav_gecerlilik_ay}). */
@Entity
@Table(name = "brove_arac_tipi")
public class BroveAracTipi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long makinistId;

    private Long aracTipiId;

    private LocalDate sinavTarihi;

    protected BroveAracTipi() {
    }

    BroveAracTipi(Long makinistId, Long aracTipiId, LocalDate sinavTarihi) {
        this.makinistId = makinistId;
        this.aracTipiId = aracTipiId;
        this.sinavTarihi = sinavTarihi;
    }

    public Long makinistId() {
        return makinistId;
    }

    public Long aracTipiId() {
        return aracTipiId;
    }

    public LocalDate sinavTarihi() {
        return sinavTarihi;
    }
}
