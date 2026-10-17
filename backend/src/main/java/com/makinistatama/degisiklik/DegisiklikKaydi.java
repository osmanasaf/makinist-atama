package com.makinistatama.degisiklik;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ek-yalnız kayıt: eklenir, hiç güncellenmez/silinmez (DB tetikleyicisi de reddeder). İlişki yok, düz Long'lar. */
@Entity
@Table(name = "degisiklik_kaydi")
public class DegisiklikKaydi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant zaman;

    private Long kullaniciId;

    private String kullaniciAdi;

    @Enumerated(EnumType.STRING)
    private DegisiklikTuru tur;

    private String varlikTipi;

    private Long varlikId;

    private Long makinistId;

    private Long seferId;

    private String eski;

    private String yeni;

    @Enumerated(EnumType.STRING)
    private Neden neden;

    private String nedenNotu;

    private String ayrinti;

    protected DegisiklikKaydi() {
    }

    DegisiklikKaydi(Instant zaman, Long kullaniciId, String kullaniciAdi, DegisiklikServisi.Kayit k) {
        this.zaman = zaman;
        this.kullaniciId = kullaniciId;
        this.kullaniciAdi = kullaniciAdi;
        this.tur = k.tur();
        this.varlikTipi = k.varlikTipi();
        this.varlikId = k.varlikId();
        this.makinistId = k.makinistId();
        this.seferId = k.seferId();
        this.eski = k.eski();
        this.yeni = k.yeni();
        this.neden = k.neden();
        this.nedenNotu = k.nedenNotu();
        this.ayrinti = k.ayrinti();
    }

    public Long id() {
        return id;
    }

    public Instant zaman() {
        return zaman;
    }

    public String kullaniciAdi() {
        return kullaniciAdi;
    }

    public DegisiklikTuru tur() {
        return tur;
    }

    public String varlikTipi() {
        return varlikTipi;
    }

    public Long varlikId() {
        return varlikId;
    }

    public Long makinistId() {
        return makinistId;
    }

    public Long seferId() {
        return seferId;
    }

    public String eski() {
        return eski;
    }

    public String yeni() {
        return yeni;
    }

    public Neden neden() {
        return neden;
    }

    public String nedenNotu() {
        return nedenNotu;
    }

    public String ayrinti() {
        return ayrinti;
    }
}
