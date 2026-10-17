package com.makinistatama.sefer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Sefer. {@code tarih} planlanan kalkışın İstanbul günüdür ve gecikmeyle KAYMAZ (MK-3); güncel saat = planlanan + gecikme (planlanan saklı).
 * Makinist burada değil, {@code atama} tablosundadır (MK-1).
 */
@Entity
@Table(name = "sefer")
public class Sefer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String trenNo;

    private LocalDate tarih;

    private Long kalkisGarId;

    private Long varisGarId;

    private Instant planlananKalkis;

    private Instant planlananVaris;

    private int kalkisGecikmeDk;

    private int varisGecikmeDk;

    private Long aracTipiId;

    private Long hatId;

    private BigDecimal km;

    @Enumerated(EnumType.STRING)
    private TrenTuru trenTuru;

    /** F2c (MK-58/59): parçasız seferde üçü de null. {@code trenGunu} = zincirin 1. parçasının {@code tarih}'i (değişmez tren kimliği). */
    private Integer parcaNo;

    private Integer parcaSayisi;

    private LocalDate trenGunu;

    @Enumerated(EnumType.STRING)
    private SeferDurumu durum = SeferDurumu.PLANLI;

    @Version
    private long versiyon;

    protected Sefer() {
    }

    Sefer(String trenNo, LocalDate tarih) {
        this.trenNo = trenNo;
        this.tarih = tarih;
    }

    /** Yeni sefer satırı (yükleme ve ekran bölmesi ortak): {@code parcaNo} null ise parçasız. */
    static Sefer parca(String trenNo, LocalDate tarih, Long kalkisGarId, Long varisGarId, Instant planlananKalkis, Instant planlananVaris, Long aracTipiId, Long hatId,
            BigDecimal km, TrenTuru trenTuru, Integer parcaNo, Integer parcaSayisi, LocalDate trenGunu) {
        Sefer e = new Sefer(trenNo, tarih);
        e.guncelle(trenNo, tarih, kalkisGarId, varisGarId, planlananKalkis, planlananVaris, aracTipiId, hatId, km, trenTuru);
        if (parcaNo != null) {
            e.parcaYaz(parcaNo, parcaSayisi, trenGunu);
        }
        return e;
    }

    /** Planlanan alanları yazar; {@code tarih} planlanan kalkıştan hesaplanıp çağıran tarafından verilir. */
    void guncelle(String trenNo, LocalDate tarih, Long kalkisGarId, Long varisGarId, Instant planlananKalkis, Instant planlananVaris, Long aracTipiId,
            Long hatId, BigDecimal km, TrenTuru trenTuru) {
        this.trenNo = trenNo;
        this.tarih = tarih;
        this.kalkisGarId = kalkisGarId;
        this.varisGarId = varisGarId;
        this.planlananKalkis = planlananKalkis;
        this.planlananVaris = planlananVaris;
        this.aracTipiId = aracTipiId;
        this.hatId = hatId;
        this.km = km;
        this.trenTuru = trenTuru;
    }

    /** Yalnız yükleme yazar; {@link #guncelle} parça alanlarına dokunmaz (elle güncelleme parçayı silmez). */
    void parcaYaz(int parcaNo, int parcaSayisi, LocalDate trenGunu) {
        this.parcaNo = parcaNo;
        this.parcaSayisi = parcaSayisi;
        this.trenGunu = trenGunu;
    }

    /**
     * Birleştirme (MK-126): parça 1 satırı tüm trenin tek satırına döner. Parça alanları NULL; varış gar/saat son parçanınki, km toplam (biri null ise null), kalkış gecikmesi
     * parça 1'inki, varış gecikmesi son parçanınki. {@link #guncelle} parça alanlarına dokunmadığından ayrı yöntem.
     */
    void birlestirYaz(Long varisGarId, Instant planlananVaris, BigDecimal km, int kalkisGecikmeDk, int varisGecikmeDk) {
        this.varisGarId = varisGarId;
        this.planlananVaris = planlananVaris;
        this.km = km;
        gecikmeYaz(kalkisGecikmeDk, varisGecikmeDk);
        this.parcaNo = null;
        this.parcaSayisi = null;
        this.trenGunu = null;
    }

    void iptalEt() {
        this.durum = SeferDurumu.IPTAL;
    }

    /** Planlanan saatler saklı kalır; gecikme planlanana göre toplamdır. */
    void gecikmeYaz(int kalkisDk, int varisDk) {
        this.kalkisGecikmeDk = kalkisDk;
        this.varisGecikmeDk = varisDk;
    }

    public Instant guncelKalkis() {
        return planlananKalkis.plusSeconds(60L * kalkisGecikmeDk);
    }

    public Instant guncelVaris() {
        return planlananVaris.plusSeconds(60L * varisGecikmeDk);
    }

    public Long id() {
        return id;
    }

    public String trenNo() {
        return trenNo;
    }

    public LocalDate tarih() {
        return tarih;
    }

    public Long kalkisGarId() {
        return kalkisGarId;
    }

    public Long varisGarId() {
        return varisGarId;
    }

    public Instant planlananKalkis() {
        return planlananKalkis;
    }

    public Instant planlananVaris() {
        return planlananVaris;
    }

    public int kalkisGecikmeDk() {
        return kalkisGecikmeDk;
    }

    public int varisGecikmeDk() {
        return varisGecikmeDk;
    }

    public Long aracTipiId() {
        return aracTipiId;
    }

    public Long hatId() {
        return hatId;
    }

    public BigDecimal km() {
        return km;
    }

    /** Boş = belirsiz (hiçbir tren türü kapsamına girmez). */
    public TrenTuru trenTuru() {
        return trenTuru;
    }

    public Integer parcaNo() {
        return parcaNo;
    }

    public Integer parcaSayisi() {
        return parcaSayisi;
    }

    public LocalDate trenGunu() {
        return trenGunu;
    }

    public SeferDurumu durum() {
        return durum;
    }

    public long versiyon() {
        return versiyon;
    }
}
