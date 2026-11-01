package com.makinistatama.makinist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.ExcelOkuma;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Makinist toplu içe aktarma okuyucusu (MK-170): stateless, bayt girişli, yalnız POI XSSF + JDK; güvenlik ön denetimi {@link ExcelOkuma} (sefer okuyucusuyla ortak). Üç sayfa:
 * {@code Makinistler} (zorunlu), {@code Araç Tipi Bröveleri}, {@code Hat Bröveleri} (uzun biçim: sicil + tanım adı + tarih; hücre içi ayraçlı metin yok). Gar / hat / araç tipi tanım adıyla
 * (Türkçe-duyarsız) eşleşir; tanım ÜRETİLMEZ, bilinmeyen ad satır hatasıdır ve {@code eksikTanimlar} listesinde ayrıca döner. Burada yalnız DOSYA doğrulanır; veritabanındaki sicil
 * denetimi ve yazım {@code MakinistIceAktarServisi}'ndedir. Geçmiş bitiş tarihi geçerlidir ama satırda uyarı olur (K-02/03/04 atamayı zaten engeller).
 */
public final class MakinistExcelOkuyucu {

    public static final int EN_FAZLA_MAKINIST_SATIRI = 5_000;
    public static final int EN_FAZLA_BROVE_SATIRI = 20_000;
    static final String MAKINIST_SAYFA = "Makinistler";
    static final String ARAC_SAYFA = "Araç Tipi Bröveleri";
    static final String HAT_SAYFA = "Hat Bröveleri";
    static final String ORNEK_ONEKI = "ORNEK-";
    private static final int EN_FAZLA_SICIL = 20;
    private static final int EN_FAZLA_AD = 120;
    private static final LocalDate EN_ESKI_TARIH = LocalDate.of(2000, 1, 1);
    private static final int EN_FAZLA_ILERI_YIL = 30;
    private static final DateTimeFormatter TARIH_GOSTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    static final List<String> MAKINIST_BASLIKLAR = List.of("Sicil", "Ad Soyad", "Tür", "Bağlı Gar", "Durum", "Ehliyet Bitiş", "Sağlık Bitiş", "Psikoteknik Bitiş", "Son Sürüş");
    static final List<String> ARAC_BASLIKLAR = List.of("Sicil", "Araç Tipi", "Sınav Tarihi");
    static final List<String> HAT_BASLIKLAR = List.of("Sicil", "Hat", "Sınav Tarihi", "Son Sürüş");

    private MakinistExcelOkuyucu() {
    }

    /** Tanım adı (katlanmış) -> kimlik haritaları. */
    public record Tanimlar(Map<String, Long> gar, Map<String, Long> hat, Map<String, Long> aracTipi) {
    }

    /** Geçerli makinist satırı: {@code istek} {@code MakinistServisi.olustur}'a verilir; {@code satirNo} Excel'deki 1-tabanlı satırdır. */
    public record Satir(int satirNo, MakinistIstegi istek, List<String> uyarilar) {
    }

    /** {@code sayfa}: hangi sayfa; {@code sicil}: bilinmiyorsa null. */
    public record SatirHatasi(String sayfa, int satirNo, String sicil, String neden) {
    }

    /** Dosyada geçen ama Tanımlar'da olmayan ad. {@code tip}: GAR | HAT | ARAC_TIPI. */
    public record EksikTanim(String tip, String ad) {
    }

    /** {@code toplam}: Makinistler sayfasındaki dolu satır sayısı. */
    public record Sonuc(int toplam, List<Satir> gecerli, List<SatirHatasi> hatalar, List<EksikTanim> eksikTanimlar) {
    }

    /** Şablon (.xlsx): üç sayfa, Türkçe başlıklar, ORNEK- önekli uydurma örnek satırlar (içe aktarma ORNEK- sicillerini reddeder: silinmeden yüklenemez). */
    public static byte[] sablon() {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            sayfaYaz(kitap.createSheet(MAKINIST_SAYFA), MAKINIST_BASLIKLAR,
                    new String[] { "ORNEK-001", "Örnek Makinist Bir", "Makinist", "Ankara", "Aktif", "31.12.2028", "31.12.2028", "31.12.2028", "" },
                    new String[] { "ORNEK-002", "Örnek Kondüktör Bir", "Kondüktör", "Ankara", "Aktif", "31.12.2028", "31.12.2028", "31.12.2028", "" });
            sayfaYaz(kitap.createSheet(ARAC_SAYFA), ARAC_BASLIKLAR, new String[] { "ORNEK-001", "YHT", "01.03.2026" });
            sayfaYaz(kitap.createSheet(HAT_SAYFA), HAT_BASLIKLAR, new String[] { "ORNEK-001", "Ankara–Eskişehir", "01.03.2026", "01.09.2026" });
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sayfaYaz(Sheet sayfa, List<String> basliklar, String[]... ornekler) {
        Row baslik = sayfa.createRow(0);
        for (int i = 0; i < basliklar.size(); i++) {
            baslik.createCell(i).setCellValue(basliklar.get(i));
            sayfa.setColumnWidth(i, 20 * 256);
        }
        int r = 1;
        for (String[] ornek : ornekler) {
            Row satir = sayfa.createRow(r++);
            for (int i = 0; i < ornek.length; i++) {
                if (!ornek[i].isEmpty()) {
                    satir.createCell(i).setCellValue(ornek[i]);
                }
            }
        }
    }

    /** @param bugun geçmiş bitiş uyarısı ve tarih üst sınırı için (testte sabit saat). */
    public static Sonuc oku(byte[] girdi, Tanimlar tanimlar, LocalDate bugun) {
        ExcelOkuma.dosyaDenetle(girdi, EN_FAZLA_BROVE_SATIRI);
        try (XSSFWorkbook kitap = new XSSFWorkbook(new ByteArrayInputStream(girdi))) {
            Sheet makinistler = sayfa(kitap, MAKINIST_SAYFA);
            if (makinistler == null) {
                throw UygulamaHatasi.gecersizDosya("'" + MAKINIST_SAYFA + "' sayfası yok. Şablonu indirip kullanın.");
            }
            sinir(makinistler, MAKINIST_SAYFA, EN_FAZLA_MAKINIST_SATIRI);
            Okuma b = new Okuma(tanimlar, bugun);
            b.makinistleriOku(makinistler);
            b.broveleriOku(sayfa(kitap, ARAC_SAYFA), true);
            b.broveleriOku(sayfa(kitap, HAT_SAYFA), false);
            return b.sonuc();
        } catch (UygulamaHatasi e) {
            throw e;
        } catch (Exception e) {
            throw UygulamaHatasi.gecersizDosya("Dosya açılamadı: geçerli bir .xlsx olmalı.");
        }
    }

    private static Sheet sayfa(XSSFWorkbook kitap, String ad) {
        String hedef = AramaMetni.katla(ad);
        for (int i = 0; i < kitap.getNumberOfSheets(); i++) {
            if (hedef.equals(AramaMetni.katla(kitap.getSheetName(i).strip()))) {
                return kitap.getSheetAt(i);
            }
        }
        return null;
    }

    private static void sinir(Sheet sayfa, String ad, int azami) {
        if (sayfa.getLastRowNum() > sayfa.getFirstRowNum() + azami) {
            throw UygulamaHatasi.gecersizDosya("'" + ad + "' sayfasında en çok " + azami + " satır olabilir.");
        }
    }

    /** Başlık satırından sütun dizini: katlanmış başlık -> sütun. Zorunlu başlıklardan biri yoksa 400. */
    private static int[] sutunlar(Sheet sayfa, String sayfaAdi, List<String> basliklar, int[] zorunlu, DataFormatter bicim) {
        Row ilk = sayfa.getRow(sayfa.getFirstRowNum());
        if (ilk == null) {
            throw UygulamaHatasi.gecersizDosya("'" + sayfaAdi + "' sayfasında başlık satırı yok.");
        }
        Map<String, Integer> kolon = new HashMap<>();
        for (Cell c : ilk) {
            kolon.putIfAbsent(AramaMetni.katla(bicim.formatCellValue(c).strip()), c.getColumnIndex());
        }
        List<String> eksik = new ArrayList<>();
        for (int z : zorunlu) {
            if (!kolon.containsKey(AramaMetni.katla(basliklar.get(z)))) {
                eksik.add(basliklar.get(z));
            }
        }
        if (!eksik.isEmpty()) {
            throw UygulamaHatasi.gecersizDosya("'" + sayfaAdi + "' sayfasında eksik sütun: " + String.join(", ", eksik) + ". Şablonu indirip kullanın.");
        }
        return basliklar.stream().mapToInt(b -> kolon.getOrDefault(AramaMetni.katla(b), -1)).toArray();
    }

    /** Okuma durumu: geçerli makinistler (sicil sırasıyla dosya sırası), hatalar, eksik tanımlar. */
    private static final class Okuma {
        private final Tanimlar tanimlar;
        private final LocalDate bugun;
        private final DataFormatter bicim = new DataFormatter(Locale.forLanguageTag("tr-TR"));
        private final Map<String, Gecici> makinistler = new LinkedHashMap<>(); // sicil -> geçerli makinist
        private final List<SatirHatasi> hatalar = new ArrayList<>();
        private final Map<String, List<String>> broveSorunlari = new LinkedHashMap<>(); // OK-46: sicil -> "sayfa satır N"
        private final Set<EksikTanimAnahtari> eksik = new TreeSet<>();
        private int toplam;

        Okuma(Tanimlar tanimlar, LocalDate bugun) {
            this.tanimlar = tanimlar;
            this.bugun = bugun;
        }

        private record EksikTanimAnahtari(String tip, String ad) implements Comparable<EksikTanimAnahtari> {
            @Override
            public int compareTo(EksikTanimAnahtari o) {
                int c = tip.compareTo(o.tip);
                return c != 0 ? c : ad.compareTo(o.ad);
            }
        }

        private static final class Gecici {
            final int satirNo;
            final PersonelTuru tur;
            final MakinistIstegi istek;
            final List<String> uyarilar;
            final List<MakinistIstegi.BroveAracTipiIstegi> araclar = new ArrayList<>();
            final List<MakinistIstegi.BroveHatIstegi> hatlar = new ArrayList<>();

            Gecici(int satirNo, PersonelTuru tur, MakinistIstegi istek, List<String> uyarilar) {
                this.satirNo = satirNo;
                this.tur = tur;
                this.istek = istek;
                this.uyarilar = uyarilar;
            }
        }

        void hata(String sayfa, int satirNo, String sicil, String neden) {
            hatalar.add(new SatirHatasi(sayfa, satirNo, sicil == null || sicil.isEmpty() ? null : sicil, neden));
        }

        // ------------------------------------------------------------------ Makinistler

        void makinistleriOku(Sheet sayfa) {
            int[] k = sutunlar(sayfa, MAKINIST_SAYFA, MAKINIST_BASLIKLAR, new int[] { 0, 1, 3, 5, 6, 7 }, bicim); // Tür, Durum, Son Sürüş sütunları isteğe bağlı
            for (int i = sayfa.getFirstRowNum() + 1; i <= sayfa.getLastRowNum(); i++) {
                Row satir = sayfa.getRow(i);
                if (satir == null || ExcelOkuma.bos(satir, bicim)) {
                    continue;
                }
                toplam++;
                int no = i + 1;
                List<String> sorunlar = new ArrayList<>();
Optional.ofNullable(ExcelOkuma.formulSorunu(satir)).ifPresent(sorunlar::add);
                List<String> uyarilar = new ArrayList<>();
                String sicil = ExcelOkuma.metin(satir.getCell(k[0]), bicim).strip().toUpperCase(Locale.ROOT);
                String ad = ExcelOkuma.metin(satir.getCell(k[1]), bicim);
                if (sicil.isEmpty()) {
                    sorunlar.add("Sicil boş");
                } else if (sicil.length() > EN_FAZLA_SICIL) {
                    sorunlar.add("Sicil en fazla " + EN_FAZLA_SICIL + " karakter olabilir");
                } else if (sicil.startsWith(ORNEK_ONEKI)) {
                    sorunlar.add("Şablondaki örnek satır (" + ORNEK_ONEKI + "...) yüklenemez; silin ya da gerçek bilgilerle değiştirin");
                }
                if (ad.isEmpty()) {
                    sorunlar.add("Ad soyad boş");
                } else if (ad.length() > EN_FAZLA_AD) {
                    sorunlar.add("Ad soyad en fazla " + EN_FAZLA_AD + " karakter olabilir");
                }
                PersonelTuru tur = tur(k[2] < 0 ? "" : ExcelOkuma.metin(satir.getCell(k[2]), bicim), sorunlar);
                Long gar = tanim(tanimlar.gar(), "GAR", ExcelOkuma.metin(satir.getCell(k[3]), bicim), "Bağlı gar", sorunlar);
                MakinistDurum durum = durum(k[4] < 0 ? "" : ExcelOkuma.metin(satir.getCell(k[4]), bicim), sorunlar);
                LocalDate ehliyet = belge(satir.getCell(k[5]), "Ehliyet bitişi", true, sorunlar, uyarilar);
                LocalDate saglik = belge(satir.getCell(k[6]), "Sağlık raporu bitişi", true, sorunlar, uyarilar);
                LocalDate psiko = belge(satir.getCell(k[7]), "Psikoteknik bitişi", true, sorunlar, uyarilar);
                LocalDate sonSurus = k[8] < 0 ? null : tarihAraligi(satir.getCell(k[8]), "Son sürüş", false, sorunlar);
                if (sorunlar.isEmpty() && makinistler.containsKey(sicil)) {
                    sorunlar.add("Sicil dosyada tekrar ediyor (ilk satır " + makinistler.get(sicil).satirNo + ")");
                }
                if (!sorunlar.isEmpty()) {
                    hata(MAKINIST_SAYFA, no, sicil, String.join("; ", sorunlar));
                    continue;
                }
                MakinistIstegi istek = new MakinistIstegi(sicil, ad, gar, durum, ehliyet, saglik, psiko, sonSurus, null, List.of(), List.of(), tur);
                makinistler.put(sicil, new Gecici(no, tur, istek, uyarilar));
            }
        }

        private PersonelTuru tur(String metin, List<String> sorunlar) {
            String m = AramaMetni.katla(metin.strip());
            return switch (m) {
                case "", "makinist" -> PersonelTuru.MAKINIST;
                case "konduktor" -> PersonelTuru.KONDUKTOR;
                default -> {
                    sorunlar.add("Tür geçersiz: '" + metin + "' (Makinist ya da Kondüktör)");
                    yield null;
                }
            };
        }

        private MakinistDurum durum(String metin, List<String> sorunlar) {
            String m = AramaMetni.katla(metin.strip());
            return switch (m) {
                case "", "aktif" -> MakinistDurum.AKTIF;
                case "askida" -> MakinistDurum.ASKIDA;
                case "pasif" -> MakinistDurum.PASIF;
                default -> {
                    sorunlar.add("Durum geçersiz: '" + metin + "' (Aktif, Askıda ya da Pasif)");
                    yield null;
                }
            };
        }

        private Long tanim(Map<String, Long> harita, String tip, String ad, String etiket, List<String> sorunlar) {
            if (ad.isEmpty()) {
                sorunlar.add(etiket + " boş");
                return null;
            }
            Long id = harita.get(AramaMetni.katla(ad));
            if (id == null) {
                sorunlar.add(etiket + " bilinmiyor: '" + ad + "'");
                eksik.add(new EksikTanimAnahtari(tip, ad));
            }
            return id;
        }

        /** Zorunlu belge tarihi; geçmişse uyarı (satır geçerli kalır). */
        private LocalDate belge(Cell c, String etiket, boolean zorunlu, List<String> sorunlar, List<String> uyarilar) {
            LocalDate t = tarihAraligi(c, etiket, zorunlu, sorunlar);
            if (t != null && t.isBefore(bugun)) {
                uyarilar.add(etiket + " geçmişte (" + t.format(TARIH_GOSTER) + "): belge süresi dolmuş, atama yapılamaz");
            }
            return t;
        }

        /** Boşsa (zorunlu değilse) null; bozuksa ya da 2000 - bugün+30 yıl dışındaysa hata. */
        private LocalDate tarihAraligi(Cell c, String etiket, boolean zorunlu, List<String> sorunlar) {
            if (!zorunlu && (c == null || ExcelOkuma.metin(c, bicim).isEmpty())) {
                return null;
            }
            List<String> yerel = new ArrayList<>();
            LocalDate t = ExcelOkuma.tarih(c, etiket, yerel);
            if (!yerel.isEmpty()) {
                sorunlar.addAll(yerel);
                return null;
            }
            if (t.isBefore(EN_ESKI_TARIH) || t.isAfter(bugun.plusYears(EN_FAZLA_ILERI_YIL))) {
                sorunlar.add(etiket + " 2000 ile " + bugun.plusYears(EN_FAZLA_ILERI_YIL).getYear() + " arasında olmalı");
                return null;
            }
            return t;
        }

        // ------------------------------------------------------------------ bröveler

        void broveleriOku(Sheet sayfa, boolean aracTipi) {
            if (sayfa == null) {
                return;
            }
            String sayfaAdi = aracTipi ? ARAC_SAYFA : HAT_SAYFA;
            sinir(sayfa, sayfaAdi, EN_FAZLA_BROVE_SATIRI);
            List<String> basliklar = aracTipi ? ARAC_BASLIKLAR : HAT_BASLIKLAR;
            int[] k = sutunlar(sayfa, sayfaAdi, basliklar, new int[] { 0, 1, 2 }, bicim);
            Set<String> gorulen = new java.util.HashSet<>(); // sicil + tanım kimliği
            for (int i = sayfa.getFirstRowNum() + 1; i <= sayfa.getLastRowNum(); i++) {
                Row satir = sayfa.getRow(i);
                if (satir == null || ExcelOkuma.bos(satir, bicim)) {
                    continue;
                }
                int no = i + 1;
                List<String> sorunlar = new ArrayList<>();
Optional.ofNullable(ExcelOkuma.formulSorunu(satir)).ifPresent(sorunlar::add);
                String sicil = ExcelOkuma.metin(satir.getCell(k[0]), bicim).strip().toUpperCase(Locale.ROOT);
                Gecici m = makinistler.get(sicil);
                if (sicil.isEmpty()) {
                    sorunlar.add("Sicil boş");
                } else if (m == null) {
                    sorunlar.add("Sicil '" + sicil + "' bu dosyadaki " + MAKINIST_SAYFA + " sayfasında (geçerli satır olarak) yok");
                } else if (m.tur == PersonelTuru.KONDUKTOR) {
                    sorunlar.add("Kondüktör kartında bröve bilgisi olmaz");
                }
                Long tanimId = aracTipi
                        ? tanim(tanimlar.aracTipi(), "ARAC_TIPI", ExcelOkuma.metin(satir.getCell(k[1]), bicim), "Araç tipi", sorunlar)
                        : tanim(tanimlar.hat(), "HAT", ExcelOkuma.metin(satir.getCell(k[1]), bicim), "Hat", sorunlar);
                LocalDate sinav = tarihAraligi(satir.getCell(k[2]), "Sınav tarihi", true, sorunlar);
                LocalDate sonSurus = aracTipi || k[3] < 0 ? null : tarihAraligi(satir.getCell(k[3]), "Son sürüş", false, sorunlar);
                if (sorunlar.isEmpty() && !gorulen.add(sicil + "/" + tanimId)) {
                    sorunlar.add(aracTipi ? "Aynı araç tipi bu sicil için iki kez girilmiş" : "Aynı hat bu sicil için iki kez girilmiş");
                }
                if (!sorunlar.isEmpty()) {
                    hata(sayfaAdi, no, sicil, String.join("; ", sorunlar));
                    if (m != null) { // OK-46: satırın herhangi bir hücresi hatalıysa makinist hiç eklenmez
                        broveSorunlari.computeIfAbsent(sicil, anahtar -> new ArrayList<>()).add(sayfaAdi + " satır " + no);
                    }
                } else if (aracTipi) {
                    m.araclar.add(new MakinistIstegi.BroveAracTipiIstegi(tanimId, sinav));
                } else {
                    m.hatlar.add(new MakinistIstegi.BroveHatIstegi(tanimId, sinav, sonSurus));
                }
            }
        }

        Sonuc sonuc() {
            List<Satir> gecerli = new ArrayList<>();
            for (Map.Entry<String, Gecici> e : makinistler.entrySet()) {
                Gecici m = e.getValue();
                List<String> broveSorunu = broveSorunlari.get(e.getKey());
                if (broveSorunu != null) {
                    hata(MAKINIST_SAYFA, m.satirNo, e.getKey(), "Makinist eklenmedi: bröve sayfalarında hatalı satır var (" + String.join(", ", broveSorunu) + "); düzeltip dosyayı yeniden yükleyin");
                    continue;
                }
                MakinistIstegi i = m.istek;
                gecerli.add(new Satir(m.satirNo, new MakinistIstegi(i.sicil(), i.adSoyad(), i.bagliGarId(), i.durum(), i.ehliyetBitis(), i.saglikBitis(), i.psikoteknikBitis(),
                        i.sonSurus(), null, List.copyOf(m.araclar), List.copyOf(m.hatlar), i.tur()), m.uyarilar));
            }
            hatalar.sort(java.util.Comparator.comparing((SatirHatasi h) -> !MAKINIST_SAYFA.equals(h.sayfa())).thenComparing(SatirHatasi::sayfa).thenComparingInt(SatirHatasi::satirNo));
            return new Sonuc(toplam, gecerli, hatalar, eksik.stream().map(e -> new EksikTanim(e.tip(), e.ad())).toList());
        }
    }
}
