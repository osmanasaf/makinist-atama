package com.makinistatama.atama;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.hibernate.annotations.OptimisticLock;

import com.makinistatama.ortak.ZamanAyari;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Serbest intikal (MK-104): makinistin sefer olarak kayıtlı olmayan bir yolla (servis, otobüs, taksi) bir garından diğerine gidişi [kalkis, varis). Düz {@code Long} FK.
 * Kural motoruna {@link AtamaSatiri#intikaldan} ile F2b'deki yolculukla aynı görev olarak girer. Silme yok: {@link IntikalDurumu#IPTAL}.
 */
@Entity
@Table(name = "intikal")
public class Intikal {

    private static final ZoneId ISTANBUL = ZamanAyari.BOLGE;
    private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long makinistId;

    private Long kalkisGarId;

    private Long varisGarId;

    private Instant kalkis;

    private Instant varis;

    @Enumerated(EnumType.STRING)
    private AracTuru aracTuru;

    private String notu;

    @Enumerated(EnumType.STRING)
    private IntikalDurumu durum = IntikalDurumu.PLANLI;

    @Version
    private long versiyon;

    /** "Gördüm" alanları iyimser kilit sürümünü artırmaz: makinistin onayı, atamacının açık düzeltme ekranını bayatlatmasın. */
    @OptimisticLock(excluded = true)
    private Instant gorduZaman;

    @OptimisticLock(excluded = true)
    private String gorduOzet;

    protected Intikal() {
    }

    Intikal(Long makinistId, Long kalkisGarId, Long varisGarId, Instant kalkis, Instant varis, AracTuru aracTuru, String notu) {
        degistir(makinistId, kalkisGarId, varisGarId, kalkis, varis, aracTuru, notu);
    }

    void degistir(Long makinistId, Long kalkisGarId, Long varisGarId, Instant kalkis, Instant varis, AracTuru aracTuru, String notu) {
        this.makinistId = makinistId;
        this.kalkisGarId = kalkisGarId;
        this.varisGarId = varisGarId;
        this.kalkis = kalkis;
        this.varis = varis;
        this.aracTuru = aracTuru;
        this.notu = notu;
    }

    void iptalEt() {
        this.durum = IntikalDurumu.IPTAL;
    }

    void goruldu(Instant zaman, String ozet) {
        this.gorduZaman = zaman;
        this.gorduOzet = ozet;
    }

    public Long id() {
        return id;
    }

    public Long makinistId() {
        return makinistId;
    }

    public Long kalkisGarId() {
        return kalkisGarId;
    }

    public Long varisGarId() {
        return varisGarId;
    }

    public Instant kalkis() {
        return kalkis;
    }

    public Instant varis() {
        return varis;
    }

    public AracTuru aracTuru() {
        return aracTuru;
    }

    public String notu() {
        return notu;
    }

    public IntikalDurumu durum() {
        return durum;
    }

    public long versiyon() {
        return versiyon;
    }

    Instant gorduZaman() {
        return gorduZaman;
    }

    String gorduOzet() {
        return gorduOzet;
    }

    /** Kural motorundaki sentetik sefer kimliği: negatif, gerçek sefer kimliğiyle (pozitif) çakışmaz (MK-105). */
    public long seferKimligi() {
        return -id;
    }

    /**
     * Makinistin "gördüm" özeti (MK-108, tek yer): içerik değişince (gar, saat, araç) ya da iptalde değişir; "gördü" bu özetle karşılaştırılır.
     * Örn. "İntikal · Eskişehir 15.10 07:00 → Ankara 15.10 08:30 · Servis"; iptalde " · İPTAL" eki. Not özete GİRMEZ (atamacının iç notu).
     */
    public String ozet(Map<Long, String> garAdlari) {
        return "İntikal · " + garAdlari.get(kalkisGarId) + " " + kalkis.atZone(ISTANBUL).format(SAAT) + " → " + garAdlari.get(varisGarId) + " " + varis.atZone(ISTANBUL).format(SAAT)
                + " · " + aracTuru.etiket() + (durum == IntikalDurumu.IPTAL ? " · İPTAL" : "");
    }
}
