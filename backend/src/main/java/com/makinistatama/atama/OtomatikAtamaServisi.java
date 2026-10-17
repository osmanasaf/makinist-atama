package com.makinistatama.atama;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.atama.OtomatikAtama.MakinistGirdisi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Siddet;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.sefer.TrenTuru;

/**
 * Otomatik atama (FR-06): saf çekirdeği ({@link OtomatikAtama}) DB'ye bağlar. {@code onizle} hiçbir şey yazmaz ve kilit almaz. {@code uygula} tek işlemde
 * yazar: istenen türün (F3b: vars. makinist) tüm AKTIF satırları kimlik sırasıyla kilitlenir (MK-7; kilitlenme yok), değerlendirme kilit ALTINDA yeniden yapılır (önizlemeden sonra
 * veri değişmiş olabilir), her atama için ATAMA kaydı + anlık görüntü ve ek olarak tek OTOMATIK_ATAMA çalıştırma kaydı yazılır. Zaten ASIL'ı olan ve
 * iptal seferlere dokunulmaz; ikinci çalıştırma yeni atama üretmez (yolculuk aşaması açıkken de: boş kalanlar bir kez yeniden denenir).
 */
@Service
@Transactional(readOnly = true)
public class OtomatikAtamaServisi {

    private static final int EN_COK_GUN = 62;
    private static final int EN_COK_KAYDIRMA_DK = 1440;
    private static final int EN_COK_ONCELIK = 20_000;
    private static final int EN_COK_AGIRLIK = 100;

    private final AtamaRepository depo;
    private final AtamaServisi atamaServisi;
    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final BaglamYukleyici yukleyici;
    private final DegisiklikServisi degisiklik;
    private final AyarServisi ayarlar;
    private final IyilestirmeImzasi imza;
    /** Yalnız eşdeğerlik testi kapatır: false = eski tam değerlendirme yolu ({@link OtomatikAtama#planla} tembel parametresi). */
    volatile boolean tembel = true;

    OtomatikAtamaServisi(AtamaRepository depo, AtamaServisi atamaServisi, SeferServisi seferler, MakinistServisi makinistler, BaglamYukleyici yukleyici,
            DegisiklikServisi degisiklik, AyarServisi ayarlar, IyilestirmeImzasi imza) {
        this.depo = depo;
        this.atamaServisi = atamaServisi;
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.yukleyici = yukleyici;
        this.degisiklik = degisiklik;
        this.ayarlar = ayarlar;
        this.imza = imza;
    }

    /** İç sonuç: DTO için sefer bilgileri ve seçilen makinistler. */
    private record Plan(OtomatikAtama.Sonuc sonuc, Map<Long, SeferDto> seferler, Map<Long, MakinistDto> kisiler, Yuk yuk, int gecmisSayisi,
            Map<Long, SeferDto> yolculukSeferleri, OtomatikAtamaDto.Iyilestirme iyilestirme) {
    }

    public OtomatikAtamaDto onizle(OtomatikAtamaIstegi istek) {
        dogrula(istek);
        Plan plan = planla(istek);
        return dto(istek, false, plan);
    }

    @Transactional
    public OtomatikAtamaDto uygula(OtomatikAtamaIstegi istek) {
        dogrula(istek);
        // MK-7: tüm AKTIF makinistler id sırasıyla; planlama kilit ALTINDA okur
        // kimlikler varlık yüklemeden okunur; makinist verisi (durum, belge, izin) kilitten SONRA planla() içinde okunur (INFO-5: bayat veri yok)
        makinistler.kilitle(makinistler.kimlikler(MakinistDurum.AKTIF, Set.of(istek.personelTuru())));
        Plan plan = planla(istek);
        int yolculukAdedi = 0;
        boolean konduktor = istek.personelTuru() == PersonelTuru.KONDUKTOR;
        try {
            for (OtomatikAtama.Atandi a : plan.sonuc().atananlar()) {
                MakinistDto m = plan.kisiler().get(a.makinistId());
                if (a.yolculukSeferId() != null) { // F2b: önce YOLCU, sonra ASIL; ikisi de bu işlemde, tek flush
                    atamaServisi.otomatikYaz(a.yolculukSeferId(), plan.yolculukSeferleri().get(a.yolculukSeferId()).kalkisGecikmeDk(), m, AtamaRol.YOLCU, plan.yuk().parametreler());
                    yolculukAdedi++;
                }
                atamaServisi.otomatikYaz(a.seferId(), plan.seferler().get(a.seferId()).kalkisGecikmeDk(), m, konduktor ? AtamaRol.KONDUKTOR : AtamaRol.ASIL, plan.yuk().parametreler());
            }
            atamaServisi.flush();
        } catch (DataIntegrityViolationException e) { // kilit altında bile aynı sefer+rol başkasınca dolmuşsa hiçbir şey yazılmaz
            throw UygulamaHatasi.cakisma("ZATEN_ATANMIS", "Bazı seferler az önce başka biri tarafından dolduruldu. Önizlemeyi yenileyin.");
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.OTOMATIK_ATAMA, "OTOMATIK_ATAMA", null).degisim(null, plan.sonuc().atananlar().size() + " atandı, "
                + plan.sonuc().bosKalanlar().size() + " boş")
                .ayrinti("{\"bas\":\"" + istek.bas() + "\",\"bit\":\"" + istek.bit() + "\",\"atanan\":" + plan.sonuc().atananlar().size() + ",\"bos\":"
                        + plan.sonuc().bosKalanlar().size() + (istek.yolculuk() ? ",\"yolculuk\":" + yolculukAdedi : "") + (istek.iyilestir() ? ",\"iyilestir\":true" : "") + (konduktor ? ",\"tur\":\"KONDUKTOR\"" : "") + "}"));
        return dto(istek, true, plan);
    }

    private void dogrula(OtomatikAtamaIstegi istek) {
        if (istek.personelTuru() == PersonelTuru.KONDUKTOR) { // MK-88: kondüktör v1'de yalnız tek aşamalı doldurma; ölçülmemiş birleşimler açılmaz
            if (istek.yolculuk()) {
                throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "YOLCULUK_YALNIZ_MAKINIST", "Yolculuk aşaması yalnız makinist için kullanılabilir.", null);
            }
            if (istek.iyilestir() || istek.anahtar() != null) {
                throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "IYILESTIRME_YALNIZ_MAKINIST", "İyileştirme yalnız makinist için kullanılabilir.", null);
            }
        }
        if (istek.iyilestir()) {
            if (istek.yolculuk()) { // MK-70: ölçülmemiş birleşim açılmaz
                throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "IYILESTIRME_YOLCULUK", "İyileştirme şimdilik yolculuk aşamasıyla birlikte kullanılamaz.", null);
            }
            IyilestirmeAnahtari a = istek.anahtar();
            if (a == null && istek.uygula()) { // MK-68: kilit altında arama yok; anahtar önizlemeden gelir
                throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "IYILESTIRME_ANAHTARI_GEREKLI", "Önce önizleme yapın: iyileştirmeli uygulama önizlemenin anahtarıyla çalışır.", null);
            }
            if (a != null) {
                if (a.kaydirDk() < 0 || a.kaydirDk() > EN_COK_KAYDIRMA_DK) {
                    throw UygulamaHatasi.dogrulama("anahtar.kaydirDk", "Kaydırma 0 ile " + EN_COK_KAYDIRMA_DK + " dakika arasında olmalı.");
                }
                if (a.oncelik().size() > EN_COK_ONCELIK) {
                    throw UygulamaHatasi.dogrulama("anahtar.oncelik", "Öncelik listesi en çok " + EN_COK_ONCELIK + " sefer içerebilir.");
                }
                if (a.oncelik().values().stream().anyMatch(w -> w == null || w < 1 || w > EN_COK_AGIRLIK)) {
                    throw UygulamaHatasi.dogrulama("anahtar.oncelik", "Öncelik ağırlıkları 1 ile " + EN_COK_AGIRLIK + " arasında olmalı.");
                }
                if (!imza.dogru(istek.bas(), istek.bit(), a)) { // W4: anahtarı yalnız sunucu üretir (HMAC, aralığa bağlı); uydurulmuş/şişirilmiş anahtar kilidi uzatamaz
                    throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "IYILESTIRME_ANAHTARI_GECERSIZ", "İyileştirme anahtarı geçersiz ya da eskimiş (aralık değişti ya da sunucu yeniden başladı). Önizlemeyi yenileyin.", null);
                }
            }
        }
        if (istek.bit().isBefore(istek.bas())) {
            throw UygulamaHatasi.dogrulama("bit", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        if (ChronoUnit.DAYS.between(istek.bas(), istek.bit()) > EN_COK_GUN) {
            throw UygulamaHatasi.dogrulama("bit", "Aralık en çok " + EN_COK_GUN + " gün olabilir.");
        }
    }

    private Plan planla(OtomatikAtamaIstegi istek) {
        LocalDate bas = istek.bas();
        LocalDate bit = istek.bit();
        boolean yolculuk = istek.yolculuk();
        List<SeferDto> aralik = seferler.aralikListesi(bas, bit);
        boolean konduktor = istek.personelTuru() == PersonelTuru.KONDUKTOR; // MK-88: çekirdek rol/tür bilmez; tür burada havuzu, "dolu" rolü ve aday seferleri belirler
        AtamaRol doluRol = konduktor ? AtamaRol.KONDUKTOR : AtamaRol.ASIL;
        Set<Long> doluSefer = aralik.isEmpty() ? Set.of()
                : depo.findBySeferIdIn(aralik.stream().map(SeferDto::id).toList()).stream().filter(a -> a.rol() == doluRol).map(Atama::seferId)
                        .collect(Collectors.toSet());
        List<SeferDto> acik = aralik.stream().filter(s -> s.durum() != SeferDurumu.IPTAL && !doluSefer.contains(s.id()) && (!konduktor || s.trenTuru() == TrenTuru.YOLCU)).toList();
        List<MakinistDto> aktif = makinistler.aktifDetaylar(Set.of(istek.personelTuru()));
        Yuk y = yukleyici.yukleAralik(bas, bit, aktif);
        // UX-11: kalkışı geçmişte kalan (bitmiş ya da başlamış) sefer hedeflenmez ve "boş" sayılmaz; ayrı sayıda bildirilir
        List<SeferDto> doldurulacak = acik.stream().filter(s -> s.guncelKalkis().toInstant().isAfter(y.simdi())).toList();

        List<MakinistGirdisi> girdiler = new ArrayList<>();
        for (MakinistDto m : aktif) {
            List<AtamaSatiri> satirlar = y.satirlar(m.id());
            Map<YearMonth, double[]> aylik = new HashMap<>();
            for (AtamaSatiri s : satirlar) {
                double[] t = aylik.computeIfAbsent(YearMonth.from(s.tarih()), k -> new double[2]);
                t[0] += y.gorev(s).sureDakika() / 60.0;
                t[1] += s.yolculuk() || s.km() == null ? 0 : s.km().doubleValue(); // yolculukta km 0 (MK-40)
            }
            List<Gorev> gorevler = satirlar.stream().map(y::gorev).toList();
            girdiler.add(new MakinistGirdisi(m.id(), m.sicil(), BaglamYukleyici.Yuk.bilgi(m), gorevler, y.hatSon(m.id()), y.genelSon(m.id()), aylik));
        }
        List<SeferGirdisi> sg = y.seferGirdileri(doldurulacak); // W1: bitişik parça kimlikleriyle
        Map<Long, SeferDto> yolculukSeferleri = Map.of();
        List<YolculukOnerisi.YolculukAdayi> yolculukAdaylari = List.of();
        int aktarma = 0;
        int arama = 0;
        if (yolculuk) { // F2b opt-in: ayarlar ve aday seferler yalnız bayrak açıkken okunur (kapalıyken ek sorgu yok)
            aktarma = ayarlar.tamsayi("yolculuk.aktarma_dk");
            arama = ayarlar.tamsayi("yolculuk.arama_saat");
            boolean yukBinilir = ayarlar.tamsayi("yolculuk.yuk_trenine_binilir") == 1;
            // ASIL'ı dolu seferler de dahil (yolcu olarak binilebilir); iptal ve geçmiş kalkışlı hariç
            yolculukSeferleri = seferler.aralikListesi(bas.minusDays(Math.max(1, (arama + 23) / 24)), bit).stream()
                    .filter(s -> s.durum() != SeferDurumu.IPTAL && s.guncelKalkis().toInstant().isAfter(y.simdi()) && (yukBinilir || s.trenTuru() != TrenTuru.YUK))
                    .collect(Collectors.toMap(SeferDto::id, s -> s));
            yolculukAdaylari = yolculukSeferleri.values().stream().map(s -> new YolculukOnerisi.YolculukAdayi(s.id(), s.tarih(), y.yolculukGorevi(s), false)).toList();
        }
        Map<Long, Map<Long, OtomatikAtama.ZincirVaryanti>> zincir = y.zincirVaryantlari(doldurulacak); // OK-17: komşu parçayı taşıyan makinist için aday görev bağlı kurulur
        int pencereGun = BaglamYukleyici.pencereGun(y.parametreler());
        boolean yillik = BaglamYukleyici.yillik(y.parametreler());
        OtomatikAtama.Sonuc sonuc;
        OtomatikAtamaDto.Iyilestirme iyilestirme = null;
        if (!istek.iyilestir()) {
            sonuc = OtomatikAtama.planla(sg, girdiler, y.adlar(), y.parametreler(), y.simdi(), pencereGun, yillik, tembel, yolculukAdaylari, aktarma, arama, Map.of(), 0, zincir);
        } else { // F2d (MK-68): yolculukla birlikte kullanılamaz (dogrula), yani aday listesi boş
            BiFunction<Map<Long, Integer>, Long, OtomatikAtama.Sonuc> tur = (onc, kaydir) -> OtomatikAtama.planla(sg, girdiler, y.adlar(), y.parametreler(), y.simdi(),
                    pencereGun, yillik, tembel, List.of(), 0, 0, onc, kaydir, zincir);
            IyilestirmeAnahtari anahtar = istek.anahtar();
            if (anahtar != null) { // uygula (kilit altında) ya da yeniden oynatma: önizlemenin en iyi turu, TEK tur
                long t0 = System.nanoTime();
                sonuc = tur.apply(anahtar.oncelik(), (long) anahtar.kaydirDk());
                iyilestirme = new OtomatikAtamaDto.Iyilestirme(null, 1, (System.nanoTime() - t0) / 1_000_000, anahtar, null);
            } else { // önizleme: arama (ayarlar yalnız burada okunur)
                Iyilestirme.Bulgu b = Iyilestirme.ara(tur, ayarlar.tamsayi("iyilestir.tur"), 1000L * ayarlar.tamsayi("iyilestir.sure_sn"), System::nanoTime);
                sonuc = b.sonuc();
                iyilestirme = new OtomatikAtamaDto.Iyilestirme(b.tabanBos(), b.tur(), b.sureMs(), imza.imzala(istek.bas(), istek.bit(), (int) b.kaydirDk(), b.oncelik()), b.durma());
            }
        }
        return new Plan(sonuc, doldurulacak.stream().collect(Collectors.toMap(SeferDto::id, s -> s)),
                aktif.stream().collect(Collectors.toMap(MakinistDto::id, m -> m)), y, acik.size() - doldurulacak.size(), yolculukSeferleri, iyilestirme);
    }

    private static OtomatikAtamaDto dto(OtomatikAtamaIstegi istek, boolean uygulandi, Plan plan) {
        List<OtomatikAtamaDto.Atanan> atananlar = plan.sonuc().atananlar().stream().map(a -> {
            SeferDto s = plan.seferler().get(a.seferId());
            MakinistDto m = plan.kisiler().get(a.makinistId());
            SeferDto ys = a.yolculukSeferId() == null ? null : plan.yolculukSeferleri().get(a.yolculukSeferId());
            return new OtomatikAtamaDto.Atanan(s.id(), s.trenNo(), s.tarih(), s.guncelKalkis(), s.guncelVaris(), s.kalkisGar(), s.varisGar(), m.id(),
                    m.sicil(), m.adSoyad(), ys == null ? null : new OtomatikAtamaDto.YolculukSefer(ys.id(), ys.trenNo(), ys.guncelKalkis(), ys.guncelVaris(), ys.kalkisGar(), ys.varisGar()));
        }).toList();
        List<OtomatikAtamaDto.BosSefer> bos = plan.sonuc().bosKalanlar().stream().map(b -> {
            SeferDto s = plan.seferler().get(b.seferId());
            return new OtomatikAtamaDto.BosSefer(s.id(), s.trenNo(), s.tarih(), s.guncelKalkis(), s.guncelVaris(), s.kalkisGar(), s.varisGar(), b.neden(),
                    b.ornekler());
        }).toList();
        return new OtomatikAtamaDto(istek.bas(), istek.bit(), uygulandi, atananlar.size(), bos.size(), plan.gecmisSayisi(), atananlar, bos, plan.iyilestirme());
    }
}
