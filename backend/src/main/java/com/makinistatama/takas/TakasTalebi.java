package com.makinistatama.takas;

import java.time.Instant;

import com.makinistatama.atama.TakasTarafi;
import com.makinistatama.degisiklik.Neden;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Takas / bırakma talebi (MK-34). Atama, sefer ve makinist kimlikleri düz sayıdır (FK yok); kimlikler talep oluştuktan sonra DEĞİŞMEZ. Özetler talep anındaki {@code GorevOzeti}'dir (bayatlık karşılaştırması). */
@Entity
@Table(name = "takas_talebi")
public class TakasTalebi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TakasTuru tur;

    @Enumerated(EnumType.STRING)
    private TakasDurumu durum;

    private Long kaynakAtamaId;
    private Long kaynakMakinistId;
    private Long kaynakSeferId;
    private String kaynakOzet;

    private Long hedefAtamaId;
    private Long hedefMakinistId;
    private Long hedefSeferId;
    private String hedefOzet;

    @Enumerated(EnumType.STRING)
    private Neden neden;

    private String nedenNotu;
    private Long olusturanKullaniciId;
    private Instant olusturma;
    private Instant karsiKarar;
    private String kararVeren;
    private Instant kararZaman;
    private Instant sonKalkis;

    @Version
    private long versiyon;

    protected TakasTalebi() {
    }

    TakasTalebi(TakasTuru tur, TakasTarafi kaynak, TakasTarafi hedef, Neden neden, String nedenNotu, Long olusturanKullaniciId, Instant olusturma, Instant sonKalkis) {
        this.tur = tur;
        this.durum = tur == TakasTuru.TAKAS ? TakasDurumu.KARSI_ONAY_BEKLIYOR : TakasDurumu.ATAMACI_ONAYI_BEKLIYOR;
        this.kaynakAtamaId = kaynak.atamaId();
        this.kaynakMakinistId = kaynak.makinistId();
        this.kaynakSeferId = kaynak.seferId();
        this.kaynakOzet = kaynak.ozet();
        if (hedef != null) {
            this.hedefAtamaId = hedef.atamaId();
            this.hedefMakinistId = hedef.makinistId();
            this.hedefSeferId = hedef.seferId();
            this.hedefOzet = hedef.ozet();
        }
        this.neden = neden;
        this.nedenNotu = nedenNotu;
        this.olusturanKullaniciId = olusturanKullaniciId;
        this.olusturma = olusturma;
        this.sonKalkis = sonKalkis;
    }

    Long id() {
        return id;
    }

    TakasTuru tur() {
        return tur;
    }

    TakasDurumu durum() {
        return durum;
    }

    Long kaynakAtamaId() {
        return kaynakAtamaId;
    }

    Long kaynakMakinistId() {
        return kaynakMakinistId;
    }

    Long kaynakSeferId() {
        return kaynakSeferId;
    }

    String kaynakOzet() {
        return kaynakOzet;
    }

    Long hedefAtamaId() {
        return hedefAtamaId;
    }

    Long hedefMakinistId() {
        return hedefMakinistId;
    }

    Long hedefSeferId() {
        return hedefSeferId;
    }

    String hedefOzet() {
        return hedefOzet;
    }

    Neden neden() {
        return neden;
    }

    String nedenNotu() {
        return nedenNotu;
    }

    Instant olusturma() {
        return olusturma;
    }

    Instant sonKalkis() {
        return sonKalkis;
    }

    TakasTarafi kaynakTarafi() {
        return new TakasTarafi(kaynakAtamaId, kaynakMakinistId, kaynakSeferId, kaynakOzet);
    }

    TakasTarafi hedefTarafi() {
        return hedefAtamaId == null ? null : new TakasTarafi(hedefAtamaId, hedefMakinistId, hedefSeferId, hedefOzet);
    }

    /** Karşı makinist kabul etti: atamacı onayına geçer. */
    void karsiKabul(Instant simdi) {
        this.karsiKarar = simdi;
        this.durum = TakasDurumu.ATAMACI_ONAYI_BEKLIYOR;
    }

    /** Karşı makinistin reddi ya da açanın iptali (karar verici makinisttir, atamacı alanı dolmaz). */
    void kapat(TakasDurumu yeni, Instant simdi) {
        this.durum = yeni;
        this.karsiKarar = simdi;
    }

    /** REDDEDILDI talepte kararı atamacı mı verdi (karşı makinistin reddinde atamacı alanı boş kalır). */
    boolean atamaciReddetti() {
        return kararVeren != null;
    }

    /** Atamacı kararı (ONAYLANDI ya da REDDEDILDI). */
    void atamaciKarari(TakasDurumu yeni, String kararVeren, Instant simdi) {
        this.durum = yeni;
        this.kararVeren = kararVeren;
        this.kararZaman = simdi;
    }

    /** Yazma yolu bayat / süresi dolmuş talebi kapatır (kimse karar vermedi). */
    void sistemKapat(TakasDurumu yeni) {
        this.durum = yeni;
    }
}
