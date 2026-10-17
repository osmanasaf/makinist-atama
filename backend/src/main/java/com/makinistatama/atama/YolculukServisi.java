package com.makinistatama.atama;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.atama.YolculukOnerisi.MakinistBaglami;
import com.makinistatama.atama.YolculukOnerisi.YolculukAdayi;
import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Ihlal;
import com.makinistatama.kural.KuralMotoru;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarServisi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferDurumu;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.sefer.TrenTuru;
import com.makinistatama.tanim.GarIntikalSureServisi;

/**
 * Yolculuk öner / "Yolculukla ata" (F2b, MK-41). {@link AtamaServisi}'ne EKLENMEZ (ayrı sorumluluk; boyut eşiği MK-35): bu sınıf ona bağımlıdır, tersi yok. Öneri saf çekirdeğe
 * ({@link YolculukOnerisi}) bağlanır; yazma {@code AtamaServisi.ata}'yı iki kez çağırır (belge, K-20, K-22, ZATEN_ATANMIS, denetim ve anlık görüntünün ikinci kopyası yok).
 */
@Service
@Transactional(readOnly = true)
public class YolculukServisi {

    private static final int EN_COK_ONERI = 3;

    private final AtamaServisi atamalar;
    private final BaglamYukleyici yukleyici;
    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final AyarServisi ayarlar;
    private final IntikalServisi intikaller;
    private final GarIntikalSureServisi sureler;

    YolculukServisi(AtamaServisi atamalar, BaglamYukleyici yukleyici, SeferServisi seferler, MakinistServisi makinistler, AyarServisi ayarlar, IntikalServisi intikaller,
            GarIntikalSureServisi sureler) {
        this.intikaller = intikaller;
        this.sureler = sureler;
        this.atamalar = atamalar;
        this.yukleyici = yukleyici;
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.ayarlar = ayarlar;
    }

    /**
     * {@code makinistId}'nin {@code seferId}'den ÖNCE yolcu olarak gidebileceği en çok 3 sefer (aday listesine gömülmez: aday listesi ölçeği bozulmaz). Yazmaz.
     * Makinist hedef seferde zaten görevliyse ya da iptal sefer ise öneri yoktur.
     */
    public YolculukOneriDto oner(long seferId, long makinistId) {
        SeferDto sefer = seferler.getir(seferId); // 404
        MakinistDto m = makinistler.detaylar(List.of(makinistId)).stream().findFirst().orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        if (m.tur() != PersonelTuru.MAKINIST) { // F3b (V8): yolculuk önerisi v1'de yalnız makinist içindir
            return new YolculukOneriDto(List.of());
        }
        if (sefer.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "İptal edilmiş sefere atama yapılamaz.");
        }
        Yuk y = yukleyici.yukle(sefer, List.of(m), AtamaRol.ASIL);
        if (y.buSeferdekiRol(m.id()) != null) {
            return new YolculukOneriDto(List.of());
        }
        Parametreler p = y.parametreler();
        int aktarma = ayarlar.tamsayi("yolculuk.aktarma_dk");
        int arama = ayarlar.tamsayi("yolculuk.arama_saat");
        boolean yukBinilir = ayarlar.tamsayi("yolculuk.yuk_trenine_binilir") == 1;

        LocalDate bas = sefer.tarih().minusDays(Math.max(1, (arama + 23) / 24));
        Map<Long, SeferDto> aday = seferler.aralikListesi(bas, sefer.tarih()).stream().filter(s -> s.durum() != SeferDurumu.IPTAL && !s.id().equals(sefer.id()))
                .collect(Collectors.toMap(SeferDto::id, Function.identity()));
        List<YolculukAdayi> adaylar = aday.values().stream().map(s -> new YolculukAdayi(s.id(), s.tarih(), y.yolculukGorevi(s), s.trenTuru() == TrenTuru.YUK)).toList();

        List<Gorev> sirali = y.digerGorevler(m.id()); // başlangıca göre sıralı; hedef sefer içinde değil
        MakinistBaglami mb = new MakinistBaglami(BaglamYukleyici.Yuk.bilgi(m), y.hatSon(m.id()), y.genelSon(m.id()), y.adlar());
        List<YolculukOnerisi.Oneri> sonuc = YolculukOnerisi.oner(sirali, mb, y.adayGorev(), sefer.tarih(), adaylar, p, y.simdi(), aktarma, arama, yukBinilir, EN_COK_ONERI, false,
                BaglamYukleyici.pencereGun(p), BaglamYukleyici.yillik(p));

        List<YolculukOneriDto.Oneri> dto = new ArrayList<>();
        for (YolculukOnerisi.Oneri o : sonuc) {
            SeferDto s = aday.get(o.seferId());
            List<IhlalDto> uyarilar = new ArrayList<>();
            for (Ihlal i : o.yolculukIhlalleri()) {
                uyarilar.add(IhlalDto.of(i));
            }
            for (Ihlal i : o.hedefIhlalleri()) {
                if (i.siddet() == Siddet.UYARI && !o.yolculukIhlalleri().contains(i)) {
                    uyarilar.add(IhlalDto.of(i));
                }
            }
            dto.add(new YolculukOneriDto.Oneri(s.id(), s.trenNo(), s.kalkisGar(), s.varisGar(), s.guncelKalkis(), s.guncelVaris(), o.bekleDk(), uyarilar,
                    s.kalkisGar() + " → " + s.varisGar() + " yolcu olarak; " + sureMetni(o.bekleDk()) + " aktarma"));
        }
        return new YolculukOneriDto(dto, intikalOnerisi(sefer, m, y).map(h -> intikalDto(h, aktarma, y.adlar())).orElse(null));
    }

    /**
     * Tek işlem: önce {@code yolculukSeferId}'e YOLCU, sonra {@code seferId}'e ASIL. İkinci {@code ata}, birinci satırı aynı işlemde komşu olarak görür (K-20 kalkar).
     * Herhangi bir ayakta ENGEL / hata → istisna yayılır, işlem geri alınır, hiçbir satır yazılmaz. ASIL zaten doluysa mevcut "değiştir" kuralı (neden zorunlu) aynen işler.
     */
    @Transactional
    public YolculuklaAtamaDto yolculukla(long seferId, YolculukluAtamaIstegi istek) {
        if (istek.yolculukSeferId() == seferId) {
            throw UygulamaHatasi.dogrulama("yolculukSeferId", "Yolculuk seferi, atanacak seferden farklı olmalı.");
        }
        AtamaServisi.AtamaSonucu yolcu = atamalar.ata(istek.yolculukSeferId(), new AtamaIstegi(istek.makinistId(), AtamaRol.YOLCU, istek.gerekce(), null, null));
        AtamaServisi.AtamaSonucu asil = atamalar.ata(seferId, new AtamaIstegi(istek.makinistId(), AtamaRol.ASIL, istek.gerekce(), istek.neden(), istek.nedenNotu()));
        return new YolculuklaAtamaDto(yolcu.atama(), asil.atama());
    }

    /** İntikal önerisinin hesabı (MK-110): gar süre tablosuna göre tek serbest intikal; öneri ve "intikalle ata" AYNI hesabı kullanır (saat istemciden gelmez). */
    private record IntikalHesabi(long kalkisGarId, long varisGarId, Instant kalkis, Instant varis, int sureDk, List<Ihlal> uyarilar) {
    }

    /** Kuralların oturtulmadığı sentetik sefer kimliği: hiçbir gerçek sefer ({@code > 0}) ya da intikal ({@code -id}) kimliğiyle çakışmaz. */
    private static final long ONERI_KIMLIGI = Long.MIN_VALUE;

    /**
     * Koşullar (hepsi): makinistin hedef başlangıcındaki konumu hedef kalkış garından farklı, gar çifti süresi tanımlı, kalkış şimdiden sonra; varış = hedef başlangıcı -
     * {@code yolculuk.aktarma_dk}, kalkış = varış - süre (kaydırma / çok aday YOK [V5]); intikal ve hedef {@code degerlendirYazma} ile ENGEL vermiyor (UYARI öneride gösterilir).
     */
    private java.util.Optional<IntikalHesabi> intikalOnerisi(SeferDto sefer, MakinistDto m, Yuk y) {
        Gorev hedef = y.adayGorev();
        List<Gorev> sirali = y.digerGorevler(m.id());
        long konum = YolculukOnerisi.konumGar(sirali, m.bagliGarId(), hedef.baslangic());
        if (konum == hedef.kalkisGarId()) {
            return java.util.Optional.empty();
        }
        OptionalInt sure = sureler.sure(konum, hedef.kalkisGarId());
        if (sure.isEmpty()) {
            return java.util.Optional.empty();
        }
        Instant varis = hedef.baslangic().minusSeconds(60L * ayarlar.tamsayi("yolculuk.aktarma_dk"));
        Instant kalkis = varis.minusSeconds(60L * sure.getAsInt());
        if (!kalkis.isAfter(y.simdi())) {
            return java.util.Optional.empty();
        }
        Gorev intikal = Gorev.yolculukOlustur(ONERI_KIMLIGI, konum, hedef.kalkisGarId(), 0L, 0L, kalkis, kalkis, varis, yukleyici.yolculukSayilir());
        List<Ihlal> yolculukIhlalleri = KuralMotoru.degerlendirYazma(y.baglam(m, intikal, sirali, y.simdi()));
        if (KuralMotoru.engelVarMi(yolculukIhlalleri)) {
            return java.util.Optional.empty();
        }
        List<Gorev> hedefKomsulari = new ArrayList<>(sirali);
        hedefKomsulari.add(intikal);
        hedefKomsulari.sort(Comparator.comparing(Gorev::baslangic).thenComparingLong(Gorev::seferId));
        List<Ihlal> hedefIhlalleri = KuralMotoru.degerlendirYazma(y.baglam(m, hedef, hedefKomsulari, y.simdi()));
        if (KuralMotoru.engelVarMi(hedefIhlalleri)) {
            return java.util.Optional.empty();
        }
        List<Ihlal> uyarilar = new ArrayList<>(yolculukIhlalleri);
        hedefIhlalleri.stream().filter(i -> i.siddet() == Siddet.UYARI && !yolculukIhlalleri.contains(i)).forEach(uyarilar::add);
        return java.util.Optional.of(new IntikalHesabi(konum, hedef.kalkisGarId(), kalkis, varis, sure.getAsInt(), uyarilar));
    }

    private static YolculukOneriDto.IntikalOneri intikalDto(IntikalHesabi h, int aktarma, Map<Long, String> adlar) {
        String kalkisGar = adlar.get(h.kalkisGarId());
        String varisGar = adlar.get(h.varisGarId());
        return new YolculukOneriDto.IntikalOneri(kalkisGar, varisGar, h.kalkis().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), h.varis().atZone(ZamanAyari.BOLGE).toOffsetDateTime(),
                h.sureDk(), aktarma, h.uyarilar().stream().map(IhlalDto::of).toList(), kalkisGar + " → " + varisGar + " serbest intikal (" + sureMetni(h.sureDk()) + "); " + sureMetni(aktarma) + " aktarma");
    }

    /**
     * Tek işlem (MK-110): önce serbest intikal (süre ve saat SUNUCUDA, öneriyle aynı hesaptan), sonra {@code seferId}'e ASIL. Herhangi biri ENGEL / hata → istisna yayılır, işlem geri
     * alınır, hiçbir satır yazılmaz; hata gövdesi hangi adımın ({@code adim}: INTIKAL | ATAMA) ihlali olduğunu bildirir. Süre tanımsız / konum zaten hedefte / kural engeli ise 422
     * {@code INTIKAL_ONERISI_YOK}.
     */
    @Transactional
    public IntikalliAtamaDto intikalle(long seferId, IntikalliAtamaIstegi istek) {
        SeferDto sefer = seferler.getir(seferId); // 404
        MakinistDto m = makinistler.detaylar(List.of(istek.makinistId())).stream().findFirst().orElseThrow(() -> UygulamaHatasi.bulunamadi("Makinist bulunamadı."));
        if (sefer.durum() == SeferDurumu.IPTAL) {
            throw UygulamaHatasi.cakisma("SEFER_IPTAL", "İptal edilmiş sefere atama yapılamaz.");
        }
        Yuk y = yukleyici.yukle(sefer, List.of(m), AtamaRol.ASIL);
        IntikalHesabi h = intikalOnerisi(sefer, m, y).orElseThrow(() -> new UygulamaHatasi(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "INTIKAL_ONERISI_YOK",
                "Bu makinist için süre tablosuna göre intikal önerilemiyor (gar çifti süresi tanımsız, makinist zaten seferin kalkış garında, saat geçmişte ya da kural engeli var).", null));
        IntikalSonucuDto yazilan = adim("INTIKAL", () -> intikaller.ekle(new IntikalIstegi(m.id(), h.kalkisGarId(), h.varisGarId(), h.kalkis().atZone(ZamanAyari.BOLGE).toOffsetDateTime(),
                h.varis().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), istek.aracTuru(), istek.notu(), istek.gerekce(), null)));
        AtamaServisi.AtamaSonucu asil = adim("ATAMA", () -> atamalar.ata(seferId, new AtamaIstegi(m.id(), AtamaRol.ASIL, istek.gerekce(), istek.neden(), istek.nedenNotu())));
        return new IntikalliAtamaDto(yazilan.intikal(), asil.atama());
    }

    /** Adımın hatasını (ihlaller dahil) {@code adim} alanıyla zenginleştirip aynı durum / kodla yeniden fırlatır. */
    private static <T> T adim(String ad, java.util.function.Supplier<T> is) {
        try {
            return is.get();
        } catch (UygulamaHatasi e) {
            Map<String, Object> ekstra = new HashMap<>(e.ekstra());
            ekstra.put("adim", ad);
            throw new UygulamaHatasi(e.durum(), e.kod(), (ad.equals("INTIKAL") ? "İntikal kaydı yazılamadı: " : "Sefer ataması yapılamadı: ") + e.getMessage(), ekstra);
        }
    }

    private static String sureMetni(long dk) {
        Duration d = Duration.ofMinutes(dk);
        return d.toHours() > 0 ? d.toHours() + " s " + d.toMinutesPart() + " dk" : d.toMinutesPart() + " dk";
    }
}
