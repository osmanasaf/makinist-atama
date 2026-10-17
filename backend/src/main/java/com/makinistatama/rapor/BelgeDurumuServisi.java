package com.makinistatama.rapor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.AtamaServisi;
import com.makinistatama.kural.BelgeKurallari;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.rapor.BelgeDurumuDto.Belge;
import com.makinistatama.rapor.BelgeDurumuDto.Durum;
import com.makinistatama.rapor.BelgeDurumuDto.Tur;

/**
 * Belge panosu (FR-09, MK-15: makinist -> atama döngüsü olmasın diye {@code rapor} paketinde). Makinist başına belgeler: ehliyet, sağlık, psikoteknik,
 * araç tipi / hat sınav bitişi, hat ve genel sürüş süresi (18 ay) dolumu. Son sürüş = kart değeri ile gerçekleşmiş atamalardan türetilenin büyüğü.
 * Bitiş günü dahil geçerlidir: kalan gün = bitiş - bugün; 0 = bugün son gün (YAKLASIYOR), negatif = DOLDU.
 */
@Service
@Transactional(readOnly = true)
public class BelgeDurumuServisi {

    private final MakinistServisi makinistler;
    private final AtamaServisi atamalar;
    private final ParametreServisi parametreler;
    private final Clock saat;

    BelgeDurumuServisi(MakinistServisi makinistler, AtamaServisi atamalar, ParametreServisi parametreler, Clock saat) {
        this.makinistler = makinistler;
        this.atamalar = atamalar;
        this.parametreler = parametreler;
        this.saat = saat;
    }

    public BelgeDurumuDto bugunku() {
        return rapor(LocalDate.now(saat));
    }

    /** Verilen günü "bugün" sayarak hesaplar (testte sabit gün). */
    public BelgeDurumuDto rapor(LocalDate bugun) {
        Parametreler p = parametreler.yukle();
        int uyariGun = (int) p.tamsayi("belge.uyari_gun");
        int hatAy = (int) p.tamsayi("hat.ara_max_ay");
        int genelAy = (int) p.tamsayi("brove.surus_ara_max_ay");
        Map<Long, Map<Long, LocalDate>> turetilmis = atamalar.turetilmisSonSurus(bugun.plusDays(1).atStartOfDay(saat.getZone()).toInstant());

        List<BelgeDurumuDto.MakinistBelgeleri> sonuc = new ArrayList<>();
        for (MakinistDto m : makinistler.detaylar(null)) {
            sonuc.add(belgeleri(m, turetilmis.getOrDefault(m.id(), Map.of()), bugun, uyariGun, hatAy, genelAy));
        }
        sonuc.sort(Comparator.comparing(BelgeDurumuDto.MakinistBelgeleri::enKotu)
                .thenComparingLong(BelgeDurumuServisi::enKucukKalan).thenComparing(BelgeDurumuDto.MakinistBelgeleri::sicil));
        return new BelgeDurumuDto(bugun, uyariGun, sonuc);
    }

    /** Bir makinistin belge satırları ve en kötü durumu (pano ve makinistin kendi ekranı AYNI sınıflamayı kullanır). */
    private BelgeDurumuDto.MakinistBelgeleri belgeleri(MakinistDto m, Map<Long, LocalDate> hatTuretilmis, LocalDate bugun, int uyariGun, int hatAy, int genelAy) {
        List<Belge> belgeler = new ArrayList<>();
        boolean makinist = m.tur() == PersonelTuru.MAKINIST;
        belgeler.add(belge(Tur.EHLIYET, makinist ? "Ehliyet" : "Kişisel emniyet belgesi", m.ehliyetBitis(), bugun, uyariGun));
        belgeler.add(belge(Tur.SAGLIK, "Sağlık raporu", m.saglikBitis(), bugun, uyariGun));
        belgeler.add(belge(Tur.PSIKOTEKNIK, "Psikoteknik raporu", m.psikoteknikBitis(), bugun, uyariGun));
        if (!makinist) { // F3b (MK-84): kondüktörde bröve ve sürüş ara kuralı yok; pano motorla aynı tür kapısını kullanır, satır üretmez
            return new BelgeDurumuDto.MakinistBelgeleri(m.id(), m.sicil(), m.adSoyad(), m.durum(), m.tur(), enKotu(belgeler), siralaBelgeler(belgeler));
        }
        m.broveAracTipleri().forEach(a -> belgeler.add(belge(Tur.ARAC_TIPI, "Araç tipi sınavı: " + a.aracTipi(), a.gecerlilikBitis(), bugun, uyariGun)));
        // sürüş / sınav yedeği K-07 ve K-08 ile AYNI işlevden (BelgeKurallari): pano ile atama motoru birbirini yalanlamaz
        List<LocalDate> surus = new ArrayList<>(Arrays.asList(m.sonSurus()));
        surus.addAll(hatTuretilmis.values());
        List<LocalDate> sinav = new ArrayList<>();
        m.broveAracTipleri().forEach(a -> sinav.add(a.sinavTarihi()));
        for (MakinistDto.BroveHatDto h : m.broveHatlar()) {
            belgeler.add(belge(Tur.HAT_SINAV, "Hat sınavı: " + h.hat(), h.gecerlilikBitis(), bugun, uyariGun));
            LocalDate hatSon = BelgeKurallari.hatSonSurus(Arrays.asList(h.sonSurus(), hatTuretilmis.get(h.hatId())), h.sinavTarihi());
            surus.add(h.sonSurus());
            sinav.add(h.sinavTarihi());
            belgeler.add(belge(Tur.HAT_SURUS, "Hatta son sürüş (" + hatAy + " ay): " + h.hat(), hatSon.plusMonths(hatAy), bugun, uyariGun));
        }
        LocalDate genelSon = BelgeKurallari.genelSonSurus(surus, sinav);
        belgeler.add(belge(Tur.GENEL_SURUS, "Son sürüş (" + genelAy + " ay)", genelSon == null ? null : genelSon.plusMonths(genelAy), bugun, uyariGun));

        return new BelgeDurumuDto.MakinistBelgeleri(m.id(), m.sicil(), m.adSoyad(), m.durum(), m.tur(), enKotu(belgeler), siralaBelgeler(belgeler));
    }

    private static Durum enKotu(List<Belge> belgeler) {
        return belgeler.stream().map(Belge::durum).min(Comparator.naturalOrder()).orElse(Durum.GECERLI);
    }

    private static List<Belge> siralaBelgeler(List<Belge> belgeler) {
        belgeler.sort(Comparator.comparing(Belge::durum).thenComparing(b -> b.kalanGun() == null ? Long.MIN_VALUE : b.kalanGun()));
        return belgeler;
    }

    /** Makinistin kendi belgeleri (tek makinist süzgeci; başkasının belgesi hesaplanmaz, NFR-M02). Kalan 0 = bugün son gün; tanı/gerekçe alanı yok. */
    public MakinistBelgeDto makinistinBelgeleri(long makinistId, LocalDate bugun) {
        Parametreler p = parametreler.yukle();
        int uyariGun = (int) p.tamsayi("belge.uyari_gun");
        MakinistDto m = makinistler.detaylar(List.of(makinistId)).stream().findFirst().orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        BelgeDurumuDto.MakinistBelgeleri b = belgeleri(m, atamalar.turetilmisSonSurus(bugun.plusDays(1).atStartOfDay(saat.getZone()).toInstant()).getOrDefault(m.id(), Map.of()),
                bugun, uyariGun, (int) p.tamsayi("hat.ara_max_ay"), (int) p.tamsayi("brove.surus_ara_max_ay"));
        return new MakinistBelgeDto(bugun, uyariGun, b.enKotu(), b.belgeler());
    }

    private static long enKucukKalan(BelgeDurumuDto.MakinistBelgeleri m) {
        return m.belgeler().stream().map(Belge::kalanGun).filter(java.util.Objects::nonNull).min(Long::compare).orElse(Long.MAX_VALUE);
    }

    static Belge belge(Tur tur, String ad, LocalDate bitis, LocalDate bugun, int uyariGun) {
        if (bitis == null) {
            return new Belge(tur, ad, null, null, Durum.GIRILMEMIS);
        }
        long kalan = ChronoUnit.DAYS.between(bugun, bitis);
        Durum durum = kalan < 0 ? Durum.DOLDU : kalan <= uyariGun ? Durum.YAKLASIYOR : Durum.GECERLI;
        return new Belge(tur, ad, bitis, kalan, durum);
    }
}
