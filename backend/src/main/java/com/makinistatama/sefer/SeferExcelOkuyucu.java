package com.makinistatama.sefer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.ExcelOkuma;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;

/**
 * Sefer Excel okuyucu (MK-12): stateless, bayt girişli, yalnız POI XSSF + JDK. Başlıklar: Tarih, Tren No, Kalkış Gar, Kalkış Saat, Varış Gar,
 * Varış Saat, Araç Tipi, Hat, [KM], [Tren Türü: Yük / Yolcu / boş]. Gar / hat / araç tipi tanım adıyla (Türkçe-duyarsız, kırpılmış) eşleşir; tanım ÜRETİLMEZ, bilinmeyen satır
 * hatasıdır. Varış saati kalkıştan küçük ya da eşitse ertesi gündür. Saat hücresi hem metin ("08:30") hem Excel zamanı (sayısal) olabilir.
 * POI'nin zip-bombası ve XXE korumaları açık kalır.
 */
public final class SeferExcelOkuyucu {

    public static final long EN_FAZLA_BAYT = ExcelOkuma.EN_FAZLA_BAYT;
    static final int EN_FAZLA_SATIR = 20_000;
    private static final int EN_FAZLA_SEFER_SAAT = 24;

    static final List<String> BASLIKLAR = List.of("Tarih", "Tren No", "Kalkış Gar", "Kalkış Saat", "Varış Gar", "Varış Saat", "Araç Tipi", "Hat", "KM", "Tren Türü");
    private static final DateTimeFormatter SAAT_METIN = DateTimeFormatter.ofPattern("H:mm");

    private SeferExcelOkuyucu() {
    }

    /** Tanım adı (katlanmış) -> kimlik haritaları. */
    public record Tanimlar(Map<String, Long> gar, Map<String, Long> hat, Map<String, Long> aracTipi) {
    }

    /** Geçerli satır. {@code satirNo} Excel'deki 1-tabanlı satır numarasıdır (başlık satırı dahil). */
    public record YuklemeSatiri(int satirNo, String trenNo, LocalDate tarih, long kalkisGarId, long varisGarId, Instant kalkis, Instant varis,
            long aracTipiId, long hatId, BigDecimal km, TrenTuru trenTuru, Integer parcaNo, Integer parcaSayisi, LocalDate trenGunu) {
    }

    /** "Değişim Noktaları" sayfasından geçerli satır (MK-61): hesaba giren {@link ParcaHesabi.Nokta} + hata mesajı için satır/tren. */
    private record NoktaSatiri(String trenNo, int satirNo, ParcaHesabi.Nokta nokta) {
    }

    /** Değişim sayfasının sonucu: tren no -> sıralı noktalar; {@code hataliTren}: geçersiz değişim verisi olan trenler (Seferler satırı da yüklenmez). */
    private record Degisim(Map<String, List<NoktaSatiri>> noktalar, Set<String> hataliTren, List<SatirHatasi> hatalar) {
    }

    /** Hata satırı: hangi sayfanın hangi satırı ({@code sayfa}: "Seferler" / "Değişim Noktaları") ve hangi tren ({@code trenNo}, bilinmiyorsa null; UX-1). */
    public record SatirHatasi(int satirNo, String neden, String sayfa, String trenNo) {
        public SatirHatasi(int satirNo, String neden) {
            this(satirNo, neden, SEFER_SAYFA, null);
        }
    }

    /** {@code engelli}: onayı DURDURAN hata var (Değişim Noktaları'nda Seferler'de karşılığı olmayan tren): öbür hatalar gibi atlanmaz, hiçbir şey yüklenmez. */
    public record Sonuc(List<YuklemeSatiri> gecerli, List<SatirHatasi> hatalar, boolean engelli) {
    }

    static final String SEFER_SAYFA = "Seferler";
    static final String DEGISIM_SAYFA_ADI = "Değişim Noktaları";

    /**
     * Tren no eşleştirme anahtarı (Seferler ↔ Değişim Noktaları): {@link AramaMetni#katla} (yerel ayardan bağımsız Türkçe katlama: i/İ/ı/I tek harf, büyük/küçük duyarsız; tren no
     * ASCII varsayılmaz, gar/hat/araç tipi eşleşmesiyle aynı kural) + tüm boşluklar (NBSP dahil) silinir. {@code toUpperCase(Locale.ROOT)} seçilmedi: ROOT'ta "ı"→"I" ama "İ" ve
     * "i" ayrı kalır (aynı tren "KİZ-1"/"kiz-1" eşleşmez); tr-TR yerelinde ise tersine bozulur. Yalnız EŞLEŞTİRME içindir: sefer kaydına her zaman Seferler sayfasındaki yazım gider.
     */
    static String trenAnahtari(String trenNo) {
        return AramaMetni.katla(trenNo).replaceAll("[\\s\\p{Z}]+", "");
    }

    /** Şablon (.xlsx): Türkçe başlıklar + bir örnek satır. */
    public static byte[] sablon() {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            Sheet sayfa = kitap.createSheet("Seferler");
            Row baslik = sayfa.createRow(0);
            for (int i = 0; i < BASLIKLAR.size(); i++) {
                baslik.createCell(i).setCellValue(BASLIKLAR.get(i));
            }
            Row ornek = sayfa.createRow(1);
            String[] degerler = { "12.10.2026", "YHT-81001", "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir", "245", "Yolcu" };
            for (int i = 0; i < degerler.length; i++) {
                ornek.createCell(i).setCellValue(degerler[i]);
            }
            for (int i = 0; i < BASLIKLAR.size(); i++) {
                sayfa.setColumnWidth(i, 18 * 256);
            }
            // F2c: ara istasyonda makinist değişen trenler için boş "Değişim Noktaları" sayfası (yalnız başlıklar; satır yoksa her şey eskisi gibi)
            Row degisimBaslik = kitap.createSheet("Değişim Noktaları").createRow(0);
            for (int i = 0; i < DEGISIM_BASLIKLAR.size(); i++) {
                degisimBaslik.createCell(i).setCellValue(DEGISIM_BASLIKLAR.get(i));
            }
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parça süresi için ek sınır yok (yalnız sefer süresi CHECK'i: en çok 24 saat). */
    public static Sonuc oku(byte[] girdi, Tanimlar tanimlar) {
        return oku(girdi, tanimlar, EN_FAZLA_SEFER_SAAT);
    }

    /** {@code azamiParcaSaat}: tek bir parçanın en uzun süresi ({@code sefer.parca_azami_saat}); aşan parça saat yazım hatasıdır, tren yüklenmez ve onay engellenir. */
    public static Sonuc oku(byte[] girdi, Tanimlar tanimlar, int azamiParcaSaat) {
        ExcelOkuma.dosyaDenetle(girdi, EN_FAZLA_SATIR);
        try (XSSFWorkbook kitap = new XSSFWorkbook(new ByteArrayInputStream(girdi))) {
            Sheet sayfa = kitap.getSheetAt(0);
            if (DEGISIM_SAYFA.equals(AramaMetni.katla(kitap.getSheetName(0).strip()))) { // UX-3: sayfa sırası ters; "eksik sütun" yanıltıcı olurdu
                throw UygulamaHatasi.gecersizDosya("Sayfa sırası ters: ilk sayfa '" + SEFER_SAYFA + "' olmalı, '" + DEGISIM_SAYFA_ADI + "' ikinci sayfa olmalı. Sayfaların yerini değiştirip yeniden yükleyin.");
            }
            Degisim degisim = degisimOku(degisimSayfasi(kitap), tanimlar);
            return satirlariOku(sayfa, tanimlar, degisim, azamiParcaSaat);
        } catch (UygulamaHatasi e) {
            throw e;
        } catch (Exception e) {
            throw UygulamaHatasi.gecersizDosya("Dosya açılamadı: geçerli bir .xlsx olmalı.");
        }
    }

    private static Sonuc satirlariOku(Sheet sayfa, Tanimlar tanimlar, Degisim degisim, int azamiParcaSaat) {
        Row ilk = sayfa.getRow(sayfa.getFirstRowNum());
        if (ilk == null) {
            throw UygulamaHatasi.gecersizDosya("Dosyada başlık satırı yok.");
        }
        DataFormatter bicim = new DataFormatter(Locale.forLanguageTag("tr-TR"));
        Map<String, Integer> kolon = new HashMap<>();
        for (Cell c : ilk) {
            kolon.putIfAbsent(AramaMetni.katla(bicim.formatCellValue(c).strip()), c.getColumnIndex());
        }
        List<String> eksik = new ArrayList<>();
        for (String b : BASLIKLAR.subList(0, 8)) {
            if (!kolon.containsKey(AramaMetni.katla(b))) {
                eksik.add(b);
            }
        }
        if (!eksik.isEmpty()) {
            throw UygulamaHatasi.gecersizDosya("Başlık satırında eksik sütun: " + String.join(", ", eksik) + ". Şablonu indirip kullanın.");
        }
        int[] k = BASLIKLAR.stream().mapToInt(b -> kolon.getOrDefault(AramaMetni.katla(b), -1)).toArray();

        List<YuklemeSatiri> gecerli = new ArrayList<>();
        List<SatirHatasi> hatalar = new ArrayList<>();
        satirSiniriKontrol(sayfa);
        Set<String> gorulenTren = new HashSet<>(); // trenAnahtari
        Map<String, Set<String>> yazimlar = new LinkedHashMap<>(); // trenAnahtari -> Seferler'deki farklı yazımlar (F2c tur 3)
        Map<String, Integer> farkliYazimSatiri = new HashMap<>(); // trenAnahtari -> ikinci farklı yazımın ilk satırı
        boolean[] engelli = { false };
        for (int i = sayfa.getFirstRowNum() + 1; i <= sayfa.getLastRowNum(); i++) {
            Row satir = sayfa.getRow(i);
            if (satir == null || ExcelOkuma.bos(satir, bicim)) {
                continue;
            }
            int no = i + 1;
            List<String> sorunlar = new ArrayList<>();
            Optional.ofNullable(ExcelOkuma.formulSorunu(satir)).ifPresent(sorunlar::add);
            LocalDate tarih = ExcelOkuma.tarih(satir.getCell(k[0]), sorunlar);
            String trenNo = ExcelOkuma.metin(satir.getCell(k[1]), bicim);
            String anahtar = trenAnahtari(trenNo);
            if (!trenNo.isEmpty()) {
                gorulenTren.add(anahtar);
                Set<String> yz = yazimlar.computeIfAbsent(anahtar, ak -> new java.util.LinkedHashSet<>());
                if (yz.add(trenNo) && yz.size() == 2) {
                    farkliYazimSatiri.put(anahtar, no);
                }
                if (degisim.hataliTren().contains(anahtar)) { // sessizce parçasız yüklenmez (MK-61)
                    hatalar.add(new SatirHatasi(no, "Değişim noktası hatası: bu trenin değişim noktaları geçersiz, sefer yüklenmedi (ayrıntı: Değişim Noktaları satırları)", SEFER_SAYFA,
                            trenNo));
                    continue;
                }
            }
            if (trenNo.isEmpty()) {
                sorunlar.add("Tren no boş");
            } else if (trenNo.length() > 30) {
                sorunlar.add("Tren no çok uzun");
            }
            Long kalkisGar = tanim(tanimlar.gar(), ExcelOkuma.metin(satir.getCell(k[2]), bicim), "Kalkış garı", sorunlar);
            LocalTime kalkisSaat = saat(satir.getCell(k[3]), "Kalkış saati", sorunlar);
            Long varisGar = tanim(tanimlar.gar(), ExcelOkuma.metin(satir.getCell(k[4]), bicim), "Varış garı", sorunlar);
            LocalTime varisSaat = saat(satir.getCell(k[5]), "Varış saati", sorunlar);
            Long aracTipi = tanim(tanimlar.aracTipi(), ExcelOkuma.metin(satir.getCell(k[6]), bicim), "Araç tipi", sorunlar);
            Long hat = tanim(tanimlar.hat(), ExcelOkuma.metin(satir.getCell(k[7]), bicim), "Hat", sorunlar);
            BigDecimal km = k[8] < 0 ? null : km(satir.getCell(k[8]), bicim, sorunlar);
            TrenTuru trenTuru = k[9] < 0 ? null : trenTuru(ExcelOkuma.metin(satir.getCell(k[9]), bicim), sorunlar);

            if (!sorunlar.isEmpty()) {
                hatalar.add(new SatirHatasi(no, String.join("; ", sorunlar), SEFER_SAYFA, trenNo.isEmpty() ? null : trenNo));
                continue;
            }
            Instant kalkis = tarih.atTime(kalkisSaat).atZone(ZamanAyari.BOLGE).toInstant();
            LocalDate varisGunu = varisSaat.isAfter(kalkisSaat) ? tarih : tarih.plusDays(1);
            Instant varis = varisGunu.atTime(varisSaat).atZone(ZamanAyari.BOLGE).toInstant();
            if (Duration.between(kalkis, varis).compareTo(Duration.ofHours(24)) > 0) {
                hatalar.add(new SatirHatasi(no, "Sefer süresi 24 saati aşıyor", SEFER_SAYFA, trenNo));
                continue;
            }
            List<NoktaSatiri> noktalar = degisim.noktalar().get(anahtar);
            if (noktalar == null) {
                gecerli.add(new YuklemeSatiri(no, trenNo, tarih, kalkisGar, varisGar, kalkis, varis, aracTipi, hat, km, trenTuru, null, null, null));
            } else {
                ParcaHesabi.Sonuc sonuc = ParcaHesabi.parcala(tarih, kalkisSaat, varisSaat, kalkisGar, varisGar, aracTipi, hat, km, noktalar.stream().map(NoktaSatiri::nokta).toList(),
                        azamiParcaSaat, null);
                if (sonuc instanceof ParcaHesabi.Hata h) {
                    engelli[0] |= h.engelli();
                    hatalar.add(new SatirHatasi(no, DEGISIM_ONEK + noktalar.get(h.noktaSirasi() - 1).satirNo() + ": " + h.govde(), SEFER_SAYFA, trenNo));
                } else {
                    for (ParcaHesabi.Parca p : ((ParcaHesabi.Parcalar) sonuc).liste()) {
                        gecerli.add(new YuklemeSatiri(no, trenNo, p.tarih(), p.kalkisGarId(), p.varisGarId(), p.kalkis(), p.varis(), p.aracTipiId(), p.hatId(), p.km(), trenTuru,
                                p.parcaNo(), p.parcaSayisi(), p.trenGunu()));
                    }
                }
            }
            if (gecerli.size() > EN_FAZLA_SATIR) { // 20.000 x 6 parça tek transaction'a açılmasın (MK-61)
                throw UygulamaHatasi.gecersizDosya("Parçalar açıldığında en çok " + EN_FAZLA_SATIR + " sefer satırı olabilir.");
            }
        }
        hatalar.addAll(degisim.hatalar());
        // Değişim verisi varken Seferler'de yalnız harf/boşluk farkıyla ayrışan tren no'ları belirsizdir: tek değişim satırı hepsini böler. ENGELLEYİCİ; ana sayfa davranışı (değişim verisi
        // yokken) değişmez.
        if (!degisim.noktalar().isEmpty() || !degisim.hataliTren().isEmpty()) {
            for (var e : yazimlar.entrySet()) {
                if (e.getValue().size() > 1) {
                    String ilkYazim = e.getValue().iterator().next();
                    hatalar.add(new SatirHatasi(farkliYazimSatiri.get(e.getKey()), "Seferler sayfasında yalnız harf farkıyla ayrışan tren no'ları var (" + String.join(", ", e.getValue()) + "): aynı tren farklı yazılmış olabilir; "
                            + "değişim noktaları hepsine uygulanırdı. Yazımı birleştirin.", SEFER_SAYFA, ilkYazim));
                    engelli[0] = true;
                }
            }
        }
        // Karşılığı Seferler'de olmayan nokta satırı ENGELLEYİCİDİR: yazım hatası ya da unutulmuş tren, öbür satırlarla sessizce parçasız yüklenemez
        for (var e : degisim.noktalar().entrySet()) {
            if (!gorulenTren.contains(e.getKey())) {
                NoktaSatiri ilkNokta = e.getValue().getFirst();
                hatalar.add(new SatirHatasi(ilkNokta.satirNo(), DEGISIM_ONEK + ilkNokta.satirNo() + ": '" + ilkNokta.trenNo() + "' tren no'su Seferler sayfasında yok (yazımı kontrol edin)",
                        DEGISIM_SAYFA_ADI, ilkNokta.trenNo()));
                engelli[0] = true;
            }
        }
        return new Sonuc(gecerli, hatalar, engelli[0]);
    }

    private static void satirSiniriKontrol(Sheet sayfa) {
        if (sayfa.getLastRowNum() > sayfa.getFirstRowNum() + EN_FAZLA_SATIR) {
            throw UygulamaHatasi.gecersizDosya("Sayfada en çok " + EN_FAZLA_SATIR + " satır olabilir.");
        }
    }

    // ------------------------------------------------------------------ değişim noktaları (F2c, MK-61)

    private static final String DEGISIM_SAYFA = AramaMetni.katla(DEGISIM_SAYFA_ADI);
    static final List<String> DEGISIM_BASLIKLAR = List.of("Tren No", "Sıra", "Gar", "Varış Saat", "Kalkış Saat", "KM", "Hat", "Araç Tipi");
    private static final String DEGISIM_ONEK = "Değişim Noktaları satır ";
    /** Adı "Değişim Noktaları" olan (Türkçe-duyarsız) ilk sayfa; yoksa null (akış bugünküyle aynı). `Seferler` (ilk sayfa) ada bakmadan okunur. */
    private static Sheet degisimSayfasi(XSSFWorkbook kitap) {
        for (int i = 1; i < kitap.getNumberOfSheets(); i++) {
            if (DEGISIM_SAYFA.equals(AramaMetni.katla(kitap.getSheetName(i).strip()))) {
                return kitap.getSheetAt(i);
            }
        }
        return null;
    }

    /** Sayfayı okur ve tren başına doğrular; hatalı trenler {@code hataliTren}'e girer (satır hatası olarak da bildirilir). */
    private static Degisim degisimOku(Sheet sayfa, Tanimlar tanimlar) {
        Map<String, List<NoktaSatiri>> noktalar = new LinkedHashMap<>();
        Set<String> hataliTren = new HashSet<>();
        List<SatirHatasi> hatalar = new ArrayList<>();
        Row ilk = sayfa == null ? null : sayfa.getRow(sayfa.getFirstRowNum());
        if (ilk == null) {
            return new Degisim(noktalar, hataliTren, hatalar);
        }
        DataFormatter bicim = new DataFormatter(Locale.forLanguageTag("tr-TR"));
        Map<String, Integer> kolon = new HashMap<>();
        for (Cell c : ilk) {
            kolon.putIfAbsent(AramaMetni.katla(bicim.formatCellValue(c).strip()), c.getColumnIndex());
        }
        List<String> eksik = DEGISIM_BASLIKLAR.subList(0, 5).stream().filter(b -> !kolon.containsKey(AramaMetni.katla(b))).toList();
        if (!eksik.isEmpty()) {
            throw UygulamaHatasi.gecersizDosya("Değişim Noktaları sayfasında eksik sütun: " + String.join(", ", eksik) + ". Şablonu indirip kullanın.");
        }
        int[] k = DEGISIM_BASLIKLAR.stream().mapToInt(b -> kolon.getOrDefault(AramaMetni.katla(b), -1)).toArray();
        satirSiniriKontrol(sayfa);

        Map<String, TreeMap<Integer, NoktaSatiri>> tren = new LinkedHashMap<>(); // trenAnahtari -> sıra -> nokta
        Map<String, String> yazim = new HashMap<>(); // trenAnahtari -> sayfadaki ilk yazım (mesajlar için)
        for (int i = sayfa.getFirstRowNum() + 1; i <= sayfa.getLastRowNum(); i++) {
            Row satir = sayfa.getRow(i);
            if (satir == null || ExcelOkuma.bos(satir, bicim)) {
                continue;
            }
            int no = i + 1;
            List<String> sorunlar = new ArrayList<>();
            Optional.ofNullable(ExcelOkuma.formulSorunu(satir)).ifPresent(sorunlar::add);
            String trenNo = ExcelOkuma.metin(satir.getCell(k[0]), bicim);
            if (trenNo.isEmpty()) {
                sorunlar.add("Tren no boş");
            }
            Integer sira = sira(ExcelOkuma.metin(satir.getCell(k[1]), bicim), sorunlar);
            Long gar = tanim(tanimlar.gar(), ExcelOkuma.metin(satir.getCell(k[2]), bicim), "Gar", sorunlar);
            LocalTime varis = saat(satir.getCell(k[3]), "Varış saati", sorunlar);
            LocalTime kalkis = saat(satir.getCell(k[4]), "Kalkış saati", sorunlar);
            if (varis != null && kalkis != null && kalkis.isBefore(varis)) { // yazım hatasını (14:00 yerine 14:10) 24 saatlik bekleme saymamak için
                sorunlar.add("Kalkış saati varış saatinden önce olamaz");
            }
            BigDecimal km = k[5] < 0 ? null : km(satir.getCell(k[5]), bicim, sorunlar);
            Long hat = istegeBagliTanim(tanimlar.hat(), satir, k[6], bicim, "Hat", sorunlar);
            Long arac = istegeBagliTanim(tanimlar.aracTipi(), satir, k[7], bicim, "Araç tipi", sorunlar);
            String anahtar = trenAnahtari(trenNo);
            yazim.putIfAbsent(anahtar, trenNo);
            if (sorunlar.isEmpty() && tren.computeIfAbsent(anahtar, t -> new TreeMap<>()).putIfAbsent(sira, new NoktaSatiri(trenNo, no, new ParcaHesabi.Nokta(sira, gar, varis, kalkis, km, hat, arac))) != null) {
                sorunlar.add("Aynı tren no ve sıra tekrar ediyor");
            }
            if (!sorunlar.isEmpty()) {
                hatalar.add(new SatirHatasi(no, DEGISIM_ONEK + no + ": " + String.join("; ", sorunlar), DEGISIM_SAYFA_ADI, trenNo.isEmpty() ? null : trenNo));
                if (!trenNo.isEmpty()) {
                    hataliTren.add(anahtar);
                }
            }
        }
        for (var e : tren.entrySet()) {
            List<NoktaSatiri> sirali = List.copyOf(e.getValue().values());
            ParcaHesabi.Hata sorun = ParcaHesabi.trenSorunu(sirali.stream().map(NoktaSatiri::nokta).toList());
            if (sorun != null) {
                hatalar.add(new SatirHatasi(sirali.getFirst().satirNo(), DEGISIM_ONEK + sirali.getFirst().satirNo() + ": " + yazim.get(e.getKey()) + " - " + sorun.govde(), DEGISIM_SAYFA_ADI,
                        yazim.get(e.getKey())));
                hataliTren.add(e.getKey());
            } else if (!hataliTren.contains(e.getKey())) {
                noktalar.put(e.getKey(), sirali);
            }
        }
        return new Degisim(noktalar, hataliTren, hatalar);
    }

    private static Integer sira(String metin, List<String> sorunlar) {
        try {
            int s = Integer.parseInt(metin);
            if (s >= 1) {
                return s;
            }
        } catch (NumberFormatException e) {
            // aşağıda raporlanır
        }
        sorunlar.add("Sıra geçersiz (1, 2, ... beklenir): '" + metin + "'");
        return null;
    }

    /** Boş hücre = null (satırınki kullanılır); dolu ama bilinmeyen ad hata. */
    private static Long istegeBagliTanim(Map<String, Long> harita, Row satir, int kolon, DataFormatter bicim, String etiket, List<String> sorunlar) {
        String ad = kolon < 0 ? "" : ExcelOkuma.metin(satir.getCell(kolon), bicim);
        return ad.isEmpty() ? null : tanim(harita, ad, etiket, sorunlar);
    }

    // ------------------------------------------------------------------ hücre okuma

    private static Long tanim(Map<String, Long> harita, String ad, String etiket, List<String> sorunlar) {
        if (ad.isEmpty()) {
            sorunlar.add(etiket + " boş");
            return null;
        }
        Long id = harita.get(AramaMetni.katla(ad));
        if (id == null) {
            sorunlar.add(etiket + " bilinmiyor: '" + ad + "'");
        }
        return id;
    }

    /** Formül hücresinde Excel'in dosyaya yazdığı sonucun türü (formül ÇALIŞTIRILMAZ); formül değilse hücre türü. */
    private static CellType sonucTuru(Cell c) {
        return c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    }

    private static LocalTime saat(Cell c, String etiket, List<String> sorunlar) {
        if (c == null || c.getCellType() == CellType.BLANK) {
            sorunlar.add(etiket + " boş");
            return null;
        }
        if (sonucTuru(c) == CellType.ERROR) {
            return null; // hata sonuçlu formül: satır sorunu zaten ExcelOkuma.formulSorunu ile bildirildi
        }
        try {
            if (sonucTuru(c) == CellType.NUMERIC) {
                double d = c.getNumericCellValue();
                if (d < 0 || d >= 1) {
                    d = d - Math.floor(d); // tarih+saat hücresi: yalnız saat kısmı
                }
                int dakika = (int) Math.round(d * 24 * 60);
                if (dakika >= 24 * 60) {
                    dakika = 0;
                }
                return LocalTime.of(dakika / 60, dakika % 60);
            }
            return LocalTime.parse(c.getStringCellValue().strip(), SAAT_METIN);
        } catch (RuntimeException e) {
            sorunlar.add(etiket + " geçersiz (ss:dd beklenir): '" + ExcelOkuma.ham(c) + "'");
            return null;
        }
    }

    private static BigDecimal km(Cell c, DataFormatter bicim, List<String> sorunlar) {
        if (c == null || c.getCellType() == CellType.BLANK) {
            return null;
        }
        if (sonucTuru(c) == CellType.ERROR) {
            return null; // hata sonuçlu formül: satır sorunu zaten ExcelOkuma.formulSorunu ile bildirildi
        }
        try {
            BigDecimal v = sonucTuru(c) == CellType.NUMERIC ? BigDecimal.valueOf(c.getNumericCellValue())
                    : new BigDecimal(c.getStringCellValue().strip().replace(',', '.'));
            if (v.signum() < 0 || v.compareTo(new BigDecimal("99999")) > 0) {
                sorunlar.add("KM aralık dışı");
                return null;
            }
            return v;
        } catch (RuntimeException e) {
            sorunlar.add("KM sayı değil: '" + bicim.formatCellValue(c) + "'");
            return null;
        }
    }

    /** İsteğe bağlı "Tren Türü": "Yük" / "Yolcu" / boş (belirsiz); başka değer satır hatasıdır. */
    private static TrenTuru trenTuru(String metin, List<String> sorunlar) {
        if (metin.isEmpty()) {
            return null;
        }
        TrenTuru t = TrenTuru.metinden(metin);
        if (t == null) {
            sorunlar.add("Tren türü geçersiz: '" + metin + "' (Yük ya da Yolcu)");
        }
        return t;
    }
}
