package com.makinistatama.atama;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Makinistin "gördüm" onayı (tebliğ kanıtı). Düz sayı kimlikler, FK yok (MK-32); atama başına tek satır, özet değişince yerinde güncellenir. */
@Entity
@Table(name = "gorev_gordum")
public class GorevGordum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long atamaId;

    private Long makinistId;

    private Long seferId;

    private String ozet;

    private Instant zaman;

    protected GorevGordum() {
    }

    GorevGordum(Long atamaId, Long makinistId, Long seferId, String ozet, Instant zaman) {
        this.atamaId = atamaId;
        yenile(makinistId, seferId, ozet, zaman);
    }

    void yenile(Long makinistId, Long seferId, String ozet, Instant zaman) {
        this.makinistId = makinistId;
        this.seferId = seferId;
        this.ozet = ozet;
        this.zaman = zaman;
    }

    Long atamaId() {
        return atamaId;
    }

    Long makinistId() {
        return makinistId;
    }

    Long seferId() {
        return seferId;
    }

    String ozet() {
        return ozet;
    }

    Instant zaman() {
        return zaman;
    }

    /** Onay yoksa ya da başka makinist/sefer için kalmışsa YENI; özet aynıysa GORULDU; değişmişse DEGISTI (yeniden onay). */
    static GordumDurumu durumu(GorevGordum onay, Atama a, String guncelOzet) {
        if (onay == null || !onay.kimlikEslesir(a)) {
            return GordumDurumu.YENI;
        }
        return onay.ozet().equals(guncelOzet) ? GordumDurumu.GORULDU : GordumDurumu.DEGISTI;
    }

    /** Onay bu atamaya mı ait: makinist VE sefer eşleşmesi (MK-32 W1: kimlik yeniden kullanımında eski onay yapışmaz). */
    boolean kimlikEslesir(Atama a) {
        return makinistId.equals(a.makinistId()) && seferId.equals(a.seferId());
    }
}
