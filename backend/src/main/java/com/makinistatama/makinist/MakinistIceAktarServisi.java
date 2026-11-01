package com.makinistatama.makinist;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Makinist toplu içe aktarma (MK-170, OK-45 Q1): stateless iki geçiş. Önizleme ve onay aynı uçtur ({@code onizle} bayrağı); onay dosyayı yeniden doğrular, sunucuda geçici dosya yoktur.
 * Her geçerli YENİ satır {@link MakinistServisi#olustur} ile yazılır (tek doğrulama + yazma yolu; kondüktör bröve yasağı, gar varlığı, sicil benzersizliği orada); veritabanında zaten
 * var olan sicil GÜNCELLENMEZ, atlanır ("ZATEN_VAR", bröveleri de yazılmaz). Hatalı satırlar atlanır, geçerliler yazılır. Hepsi tek işlemde: sicil yarışı (409) tümünü geri alır.
 * Tek {@code MAKINIST_ICE_AKTAR} özet kaydı + satır başına {@code MAKINIST_EKLE}.
 */
@Service
public class MakinistIceAktarServisi {

    private final MakinistServisi makinistler;
    private final TanimServisi tanimlar;
    private final DegisiklikServisi degisiklik;
    private final Clock saat;

    MakinistIceAktarServisi(MakinistServisi makinistler, TanimServisi tanimlar, DegisiklikServisi degisiklik, Clock saat) {
        this.makinistler = makinistler;
        this.tanimlar = tanimlar;
        this.degisiklik = degisiklik;
        this.saat = saat;
    }

    public byte[] sablon() {
        return MakinistExcelOkuyucu.sablon();
    }

    @Transactional
    public MakinistYuklemeSonucuDto yukle(byte[] dosya, boolean onizle) {
        var t = new MakinistExcelOkuyucu.Tanimlar(tanimlar.katlanmisAdlar(TanimTipi.GAR), tanimlar.katlanmisAdlar(TanimTipi.HAT), tanimlar.katlanmisAdlar(TanimTipi.ARAC_TIPI));
        MakinistExcelOkuyucu.Sonuc okunan = MakinistExcelOkuyucu.oku(dosya, t, LocalDate.now(saat));
        Set<String> mevcut = makinistler.tumSiciller();
        Map<Long, String> adlar = tanimlar.tumAdlar();

        List<MakinistYuklemeSonucuDto.Satir> satirlar = new ArrayList<>();
        int eklenen = 0;
        int zatenVar = 0;
        int uyari = 0;
        for (MakinistExcelOkuyucu.Satir s : okunan.gecerli()) {
            boolean var = mevcut.contains(s.istek().sicil());
            if (var) {
                zatenVar++;
            } else {
                eklenen++;
                uyari += s.uyarilar().size();
                if (!onizle) {
                    // ponytail: satır başına ekle (~9 ms/satır: 300 satır ≈ 2,7 sn, 3.000 ≈ 27 sn, 5.000 ≈ 46 sn; tek seferlik kurulum). Tavan: ters vekil 60 sn okuma sınırı; gerekirse toplu (batch) yazım.
                    makinistler.ekle(s.istek());
                }
            }
            MakinistIstegi i = s.istek();
            satirlar.add(new MakinistYuklemeSonucuDto.Satir(s.satirNo(), i.sicil(), i.adSoyad(), i.tur(), adlar.get(i.bagliGarId()), i.durum(), i.broveAracTipleri().size(),
                    i.broveHatlar().size(), var ? "ZATEN_VAR" : "YENI", var ? List.of() : s.uyarilar()));
        }
        if (!onizle && eklenen > 0) { // hiçbir şey yazılmadıysa kayıt da yok (aynı dosyanın ikinci yüklemesi)
            degisiklik.kaydet(Kayit.of(DegisiklikTuru.MAKINIST_ICE_AKTAR, "MAKINIST", null).degisim(null, eklenen + " makinist")
                    .ayrinti("{\"eklenen\":" + eklenen + ",\"zatenVar\":" + zatenVar + ",\"hata\":" + okunan.hatalar().size() + "}"));
        }
        return new MakinistYuklemeSonucuDto(onizle, okunan.toplam(), eklenen, zatenVar, okunan.hatalar().size(), uyari, satirlar,
                okunan.hatalar().stream().map(h -> new MakinistYuklemeSonucuDto.Hata(h.sayfa(), h.satirNo(), h.sicil(), h.neden())).toList(),
                okunan.eksikTanimlar().stream().map(e -> new MakinistYuklemeSonucuDto.EksikTanim(e.tip(), e.ad())).toList());
    }
}
