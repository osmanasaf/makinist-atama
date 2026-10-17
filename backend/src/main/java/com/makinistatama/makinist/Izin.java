package com.makinistatama.makinist;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.makinistatama.ortak.UygulamaHatasi;

/** İzin / rapor aralığı (iki uç dahil, İstanbul günü). K-09 yalnız {@code ONAY} durumunu sayar. */
@Entity
@Table(name = "izin")
public class Izin {

    /** Arayüzle aynı gg.aa.yyyy (ISO ham tarih gösterilmez, UX-12). */
    private static final DateTimeFormatter GUN_BICIMI = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long makinistId;

    private LocalDate baslangic;

    private LocalDate bitis;

    @Enumerated(EnumType.STRING)
    private IzinTuru tur;

    @Enumerated(EnumType.STRING)
    private IzinDurumu durum;

    private String olusturan;

    private String kararVeren;

    /** Talebi açan kullanıcının kimliği (OK-4); eski ve karttan doğrudan eklenen satırlarda boş olabilir. */
    private Long olusturanKullaniciId;

    /** Karar yarışı (W5): aynı talebe iki eşzamanlı karar gelirse ikincisi 409 olur. */
    @Version
    private long versiyon;

    protected Izin() {
    }

    Izin(Long makinistId, LocalDate baslangic, LocalDate bitis, IzinTuru tur, IzinDurumu durum, String olusturan, Long olusturanKullaniciId) {
        this.makinistId = makinistId;
        this.baslangic = baslangic;
        this.bitis = bitis;
        this.tur = tur;
        this.durum = durum;
        this.olusturan = olusturan;
        this.olusturanKullaniciId = olusturanKullaniciId;
    }

    /** Aynı makinistin TALEP/ONAY izniyle (iki uç dahil) çakışan yeni izin açılamaz (BUG-15); RED olanlar sayılmaz. */
    static void cakismaDenetle(IzinRepository izinler, long makinistId, LocalDate baslangic, LocalDate bitis) {
        araligiDenetle(baslangic, bitis);
        if (izinler.cakisanVarMi(makinistId, baslangic, bitis)) {
            throw UygulamaHatasi.cakisma("IZIN_CAKISMASI", "Bu makinistin " + baslangic.format(GUN_BICIMI) + " – " + bitis.format(GUN_BICIMI) + " ile çakışan bekleyen ya da onaylı bir izni var.");
        }
    }

    /** Uç tarihler makul yıl aralığında olmalı: PostgreSQL DATE sınırını aşan tarih 500 değil 400 olsun (05-review WARNING 2). */
    static void araligiDenetle(LocalDate baslangic, LocalDate bitis) {
        if (baslangic.getYear() < 2000 || baslangic.getYear() > 2100) {
            throw UygulamaHatasi.dogrulama("baslangic", "Başlangıç tarihi 2000 ile 2100 arasında olmalı.");
        }
        if (bitis.getYear() < 2000 || bitis.getYear() > 2100) {
            throw UygulamaHatasi.dogrulama("bitis", "Bitiş tarihi 2000 ile 2100 arasında olmalı.");
        }
    }

    /** TALEP -> ONAY/RED; karar veren kullanıcı adı yazılır. */
    void kararVer(IzinDurumu yeni, String kararVeren) {
        this.durum = yeni;
        this.kararVeren = kararVeren;
    }

    public Long id() {
        return id;
    }

    public Long makinistId() {
        return makinistId;
    }

    public LocalDate baslangic() {
        return baslangic;
    }

    public LocalDate bitis() {
        return bitis;
    }

    public IzinTuru tur() {
        return tur;
    }

    public IzinDurumu durum() {
        return durum;
    }

    public String olusturan() {
        return olusturan;
    }

    public Long olusturanKullaniciId() {
        return olusturanKullaniciId;
    }

    public String kararVeren() {
        return kararVeren;
    }
}
