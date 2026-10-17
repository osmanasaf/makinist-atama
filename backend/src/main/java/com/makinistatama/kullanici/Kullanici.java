package com.makinistatama.kullanici;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kullanici")
public class Kullanici {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eposta;

    @Column(nullable = false)
    private String adSoyad;

    @Column(nullable = false)
    private String parolaKarma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private boolean aktif = true;

    @Column(nullable = false)
    private int basarisizGiris;

    private Instant kilitBitis;

    /** Kendi-parola değişimi denemeleri: giriş sayacından ayrıdır (V18); her deneme doğrulamadan önce sayılır, eşikte {@code parolaKilitBitis} konur. */
    @Column(nullable = false)
    private int parolaDeneme;

    private Instant parolaKilitBitis;

    /** Makinist hesabında bağlı makinist kartı (düz sayı, FK yok: MK-26); personel hesabında null. */
    private Long makinistId;

    /** Geçici parolayla açılan/sıfırlanan makinist hesabı kendi parolasını belirleyene dek yalnız kimlik/çıkış/parola uçlarını kullanabilir. */
    @Column(nullable = false)
    private boolean parolaDegistirmeli;

    protected Kullanici() {
    }

    public Kullanici(String eposta, String adSoyad, String parolaKarma, Rol rol) {
        this.eposta = eposta;
        this.adSoyad = adSoyad;
        this.parolaKarma = parolaKarma;
        this.rol = rol;
    }

    /** Makinist hesabı: giriş adı küçük harf sicil, parola geçici (ilk girişte değiştirilmeli). */
    public static Kullanici makinistHesabi(String sicil, String adSoyad, String geciciKarma, long makinistId) {
        Kullanici k = new Kullanici(sicil, adSoyad, geciciKarma, Rol.MAKINIST);
        k.makinistId = makinistId;
        k.parolaDegistirmeli = true;
        return k;
    }

    public Long id() {
        return id;
    }

    public String eposta() {
        return eposta;
    }

    public String adSoyad() {
        return adSoyad;
    }

    public String parolaKarma() {
        return parolaKarma;
    }

    public Rol rol() {
        return rol;
    }

    public boolean aktif() {
        return aktif;
    }

    public Instant kilitBitis() {
        return kilitBitis;
    }

    public Long makinistId() {
        return makinistId;
    }

    public boolean parolaDegistirmeli() {
        return parolaDegistirmeli;
    }

    void guncelle(String adSoyad, Rol rol, boolean aktif) {
        if (this.aktif != aktif) {
            kilidiSifirla(); // aktiflik değişimi sayacı sıfırlar
        }
        this.adSoyad = adSoyad;
        this.rol = rol;
        this.aktif = aktif;
    }

    void parolaDegistir(String yeniKarma) {
        this.parolaKarma = yeniKarma;
        this.parolaDegistirmeli = false;
        kilidiSifirla();
    }

    /** Atamacının verdiği geçici parola: hesap yeni parolayı kendisi belirleyene dek kısıtlıdır. */
    void geciciParolaVer(String yeniKarma) {
        this.parolaKarma = yeniKarma;
        this.parolaDegistirmeli = true;
        kilidiSifirla();
    }

    void kilidiSifirla() {
        this.basarisizGiris = 0;
        this.kilitBitis = null;
        parolaDenemesiniSifirla();
    }

    boolean parolaKilitli(Instant simdi) {
        return parolaKilitBitis != null && parolaKilitBitis.isAfter(simdi);
    }

    /** Bir deneme sayar (süresi dolmuş kilit önce temizlenir); {@code esik}. denemede kilit konur: o deneme sürer, sonraki reddedilir. */
    void parolaDenemesiSay(Instant simdi, int esik, java.time.Duration sure) {
        if (parolaKilitBitis != null && !parolaKilitBitis.isAfter(simdi)) {
            parolaDenemesiniSifirla();
        }
        parolaDeneme++;
        if (parolaDeneme >= esik) {
            parolaKilitBitis = simdi.plus(sure);
        }
    }

    void parolaDenemesiniSifirla() {
        this.parolaDeneme = 0;
        this.parolaKilitBitis = null;
    }
}
