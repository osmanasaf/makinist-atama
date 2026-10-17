package com.makinistatama.atama;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.degisiklik.Neden;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/**
 * "Gördüm" onayı (tebliğ kanıtı, FR-M08) ve elinden alınan görev kalemleri (FR-M09). Makinist kimliği ÇAĞIRANDAN gelir (oturum, {@code benim} paketi); burada hiçbir istek
 * alanından kimlik üretilmez. Başka makinistin ya da ufuk dışındaki atama 404 (varlık sızdırılmaz). Çift basış makinist satırı kilidiyle serileştirilir (MK-32).
 */
@Service
@Transactional(readOnly = true)
public class GordumServisi {

    private final AtamaServisi atamalar;
    private final GorevGordumRepository gordumlar;
    private final AtamaOlayiRepository olaylar;
    private final MakinistServisi makinistler;
    private final SeferServisi seferler;
    private final DegisiklikServisi degisiklik;
    private final Clock saat;
    private final AyarServisi ayarlar;

    GordumServisi(AtamaServisi atamalar, GorevGordumRepository gordumlar, AtamaOlayiRepository olaylar, MakinistServisi makinistler, SeferServisi seferler,
            DegisiklikServisi degisiklik, Clock saat, AyarServisi ayarlar) {
        this.atamalar = atamalar;
        this.gordumlar = gordumlar;
        this.olaylar = olaylar;
        this.makinistler = makinistler;
        this.seferler = seferler;
        this.degisiklik = degisiklik;
        this.saat = saat;
        this.ayarlar = ayarlar;
    }

    /**
     * Makinist atamayı güncel haliyle gördüğünü onaylar. [bas, bit] makinistin görünürlük ufkudur; atama makinistin kendisinin değilse ya da ufuk dışındaysa 404. Güncel özet zaten
     * onaylıysa idempotent (satır ve denetim kaydı eklenmez); özet değişmişse onay yerinde güncellenir ve yeni GORDUM kaydı yazılır.
     */
    @Transactional
    public MakinistGorevDto gordum(long makinistId, long atamaId, LocalDate bas, LocalDate bit, String ekrandakiOzet) {
        makinistler.kilitle(List.of(makinistId));
        MakinistGorevDto g = kendiGorev(makinistId, atamaId, bas, bit);
        if (!g.ozet().equals(ekrandakiOzet)) { // makinist bayat ekrandan basıyor: görmediği hâli onaylamış sayılmaz (05-review Tur 2 W1); güncel görev dönsün, arayüz yeni hâli göstersin
            throw new UygulamaHatasi(HttpStatus.CONFLICT, "OZET_DEGISTI", "Görev siz bakarken değişti. Yeni hâline bakıp yeniden onaylayın.", java.util.Map.of("gorev", g));
        }
        if (g.gordu() == GordumDurumu.GORULDU) {
            return g;
        }
        Instant simdi = saat.instant();
        GorevGordum onay = gordumlar.findByAtamaId(atamaId).orElse(null);
        String eski = g.gordu() == GordumDurumu.DEGISTI ? g.eskiOzet() : null;
        if (onay == null) {
            gordumlar.save(new GorevGordum(atamaId, makinistId, g.seferId(), g.ozet(), simdi));
        } else {
            onay.yenile(makinistId, g.seferId(), g.ozet(), simdi); // başka atamadan/makinistten kalmış ya da özeti değişmiş eski onay
        }
        gordumlar.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.GORDUM, "ATAMA", atamaId).makinist(makinistId).sefer(g.seferId()).degisim(eski, g.ozet()));
        return kendiGorev(makinistId, atamaId, bas, bit);
    }

    private MakinistGorevDto kendiGorev(long makinistId, long atamaId, LocalDate bas, LocalDate bit) {
        return atamalar.makinistGorevleri(makinistId, bas, bit).stream().filter(x -> x.atamaId().equals(atamaId)).findFirst()
                .orElseThrow(() -> UygulamaHatasi.bulunamadi("Görev bulunamadı."));
    }

    /** Makinistin kapatmadığı, son {@code gordum.kalem_gun} günde elinden alınan görev kalemleri (en yeni önce). */
    public List<GorevOlayiDto> kaldirilanlar(long makinistId) {
        Instant bas = saat.instant().minus(ayarlar.tamsayi("gordum.kalem_gun"), ChronoUnit.DAYS);
        List<AtamaOlayi> acik = olaylar.findByMakinistIdAndKapatildiZamanIsNullAndZamanGreaterThanEqualOrderByZamanDescIdDesc(makinistId, bas);
        Map<Long, SeferDto> sefer = seferler.kimliklerle(acik.stream().map(AtamaOlayi::seferId).collect(java.util.stream.Collectors.toSet()));
        return acik.stream().map(o -> {
            SeferDto s = sefer.get(o.seferId());
            return new GorevOlayiDto(o.id(), o.tur(), o.seferId(), s == null ? null : s.trenNo(), s == null ? null : s.tarih(), o.ozet(),
                    o.nedenEtiketi() == null ? null : Neden.valueOf(o.nedenEtiketi()), o.zaman().atZone(ZamanAyari.BOLGE).toOffsetDateTime());
        }).toList();
    }

    /** Kalemi kapatır ("Gördüm"). Başka makinistin kalemi 404; zaten kapalıysa idempotent (ek kayıt yok). */
    @Transactional
    public void olayiKapat(long makinistId, long olayId) {
        makinistler.kilitle(List.of(makinistId));
        AtamaOlayi o = olaylar.findByIdAndMakinistId(olayId, makinistId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kalem bulunamadı."));
        if (o.kapatildiZaman() != null) {
            return;
        }
        o.kapat(saat.instant());
        olaylar.flush();
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.GORDUM, "ATAMA_OLAYI", olayId).makinist(makinistId).sefer(o.seferId()).degisim(o.ozet(), "KAPATILDI"));
    }
}
