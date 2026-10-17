package com.makinistatama.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.makinist.IzinIstegi;
import com.makinistatama.makinist.IzinTuru;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistIstegi;
import com.makinistatama.makinist.MakinistIstegi.BroveAracTipiIstegi;
import com.makinistatama.makinist.MakinistIstegi.BroveHatIstegi;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.parametre.ParametreGuncellemeIstegi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.sefer.SeferYuklemeServisi;
import com.makinistatama.sefer.YuklemeSonucuDto;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * Demo veri (FR-14, yalnız {@code dev} profili): TAMAMEN UYDURMA. 4 gar, 3 hat, 3 araç tipi, 68 makinist ({@code D001..D068},
 * {@code Demo Makinist NN}; ilk 3 makinistin hesabı açık, geçici parola yalnız yükleme yanıtında), bugünün ayından başlayan 31 günlük sefer (~100/gün), birkaç yaklaşan/dolmuş belge, 15 izin. Makinist sayısı ve bröve dağılımı talebe göre
 * ayarlıdır (haftada ~2.100 saat; Ankara–Sivas ~%61): tipik haftada otomatik atama seferlerin ~%95'ini doldurur, kalanı gerçekçi nedenlerle (izin, belge süresi, hafta
 * tatili, dinlenme, konum) boş kalır. Belirleyici
 * ({@code Random(seed)}). Seferler xlsx bayt dizisi olarak ÜRETİLİP {@link SeferYuklemeServisi} üzerinden yüklenir (tek yükleme yolu).
 * Gerçek kişi adı, sicil, TC kimlik, tanı ya da telefon yoktur.
 */
@Service
@Profile("dev")
public class DemoVeriServisi {

    static final long TOHUM = 20261010L;
    static final int MAKINIST_SAYISI = 68;
    static final int GUN_SAYISI = 31;
    static final int GUNLUK_SEFER = 90;
    static final int DEMO_HESAP_SAYISI = 3;
    static final int KONDUKTOR_SAYISI = 4;
    static final String DEMO_SAAT_UCRETI = "200";
    static final String DEMO_KM_TUTARI = "0.4";
    private static final String DEMO_NEDEN = "DEMO oranı – gerçek değil";

    /** Makinist ekipleri: 0 = Sivas (Elektrikli Dizi; Ankara–Sivas), 1 = hızlı tren (YHT; Eskişehir, Konya), 2 = karma (hepsi). */
    private static final int SIVAS = 0;
    private static final int HIZLI = 1;
    private static final int KARMA = 2;
    /** 10 makinistlik döngü: 4 Sivas, 3 hızlı tren, 3 karma. */
    private static final int[] EKIPLER = { SIVAS, SIVAS, SIVAS, SIVAS, HIZLI, HIZLI, HIZLI, KARMA, KARMA, KARMA };
    /** Ekip -> araç tipi indeksleri (ARAC_TIPLERI) ve hat yetkisi (HATLAR: AE, AS, AK). */
    private static final int[][] EKIP_ARACLARI = { { 1 }, { 0 }, { 0, 1 } };
    /** Ekibin bağlı olabileceği garlar (GARLAR indeksi): Sivas ekibi Ankara/Sivas, hızlı tren Ankara/Eskişehir/Konya, karma Ankara. Makinist kendi hattının uç garında konumlanır. */
    private static final int[][] EKIP_GARLARI = { { 0, 2 }, { 0, 1, 3 }, { 0 } };
    private static final boolean[][] HAT_YETKISI = { { true, true, false }, { true, false, true }, { true, true, true } };
    /**
     * Dizel ekibi (fazla mesai gösterimi): yalnız bunlar Eskişehir–Konya dizel seferlerini (günde 10 sefer, ~45 saat/kişi/hafta) çalıştırabilir; bağlı garları Eskişehir.
     * Varsayılan K-14 KAPALI olduğundan (OK-24) ekip 45 saati aşabilir ve puantajda fazla mesai görünür; demo ayar DEĞİŞTİRMEZ.
     */
    private static final int[] DIZEL_EKIBI = { 5, 6, 16, 25, 35, 45, 55, 65 };

    /**
     * Gerçekçi izin yoğunluğu: dağınık kısa izinler + bir haftada Sivas ekibinden birkaçının aynı anda izne çıkması (o hafta Ankara–Sivas seferlerinin birazı
     * "izin" nedeniyle boş kalır).
     */
    private static final int[][] IZINLER = { { 2, 5, 5, 0 }, { 9, 9, 5, 1 }, { 16, 13, 5, 0 }, { 23, 17, 5, 1 }, { 30, 21, 5, 0 }, { 0, 9, 7, 0 }, { 1, 10, 7, 0 },
            { 10, 9, 7, 0 }, { 11, 12, 5, 1 }, { 4, 11, 6, 0 }, { 14, 16, 7, 0 }, { 20, 3, 4, 0 }, { 34, 14, 7, 0 }, { 40, 6, 5, 0 }, { 45, 19, 7, 0 } };

    private static final String[] GARLAR = { "Ankara", "Eskişehir", "Sivas", "Konya" };
    private static final String[] HATLAR = { "Ankara–Eskişehir", "Ankara–Sivas", "Ankara–Konya", "Eskişehir–Konya" };
    private static final String[] ARAC_TIPLERI = { "YHT", "Elektrikli Dizi", "Dizel Lokomotif" };
    /** Hat -> {başlangıç gar, bitiş gar, ortalama süre dk, km, araç tipi indeksi}. */
    private static final String[] HAT_KODU = { "AE", "AS", "AK", "EK" };
    private static final int[][] HAT_BILGISI = { { 0, 1, 100, 245, 0 }, { 0, 2, 330, 603, 1 }, { 0, 3, 110, 305, 0 }, { 1, 3, 270, 320, 2 } };
    /** Günlük genel sefer şablonu yalnız ilk 3 hattı kullanır (dizel hattı ayrı üretilir). */
    private static final int GENEL_HAT = 3;
    private static final int DIZEL_CIFT = 5;

    private final TanimServisi tanimlar;
    private final KullaniciServisi kullanicilar;
    private final MakinistServisi makinistler;
    private final SeferYuklemeServisi yukleme;
    private final ParametreServisi parametreler;
    private final JdbcTemplate jdbc;
    private final Clock saat;

    DemoVeriServisi(TanimServisi tanimlar, MakinistServisi makinistler, SeferYuklemeServisi yukleme, ParametreServisi parametreler, KullaniciServisi kullanicilar, JdbcTemplate jdbc,
            Clock saat) {
        this.kullanicilar = kullanicilar;
        this.tanimlar = tanimlar;
        this.makinistler = makinistler;
        this.yukleme = yukleme;
        this.parametreler = parametreler;
        this.jdbc = jdbc;
        this.saat = saat;
    }

    /** Demo makinist hesabı: giriş adı = sicil; geçici parola yalnız bu yanıtta (ilk girişte değiştirilmeli). Uydurma veri. */
    public record Hesap(String sicil, String adSoyad, String geciciParola) {
    }

    public record Sonuc(int makinist, int sefer, int izin, List<Hesap> hesaplar) {
    }

    /** {@code yuklu}: demo (ya da başka) veri zaten var; {@code yukle} 409 {@code DEMO_DOLU} verir. */
    public record Durum(boolean yuklu) {
    }

    @Transactional(readOnly = true)
    public Durum durum() {
        return new Durum(dolu());
    }

    private boolean dolu() {
        return !tanimlar.liste(TanimTipi.GAR).isEmpty() || !makinistler.liste(null, null).isEmpty();
    }

    @Transactional
    public Sonuc yukle() {
        if (dolu()) {
            throw UygulamaHatasi.cakisma("DEMO_DOLU", "Veritabanında zaten veri var. Önce 'sıfırla' ile demo tablolarını boşaltın.");
        }
        List<Long> gar = new ArrayList<>();
        for (String ad : GARLAR) {
            gar.add(tanimlar.ekle(TanimTipi.GAR, ad).id());
        }
        List<Long> hat = new ArrayList<>();
        for (String ad : HATLAR) {
            hat.add(tanimlar.ekle(TanimTipi.HAT, ad).id());
        }
        List<Long> arac = new ArrayList<>();
        for (String ad : ARAC_TIPLERI) {
            arac.add(tanimlar.ekle(TanimTipi.ARAC_TIPI, ad).id());
        }

        LocalDate bugun = LocalDate.now(saat);
        Random rnd = new Random(TOHUM);
        List<MakinistDto> kartlar = new ArrayList<>();
        for (int i = 1; i <= MAKINIST_SAYISI; i++) {
            kartlar.add(makinistler.olustur(makinist(i, bugun, rnd, gar, hat, arac)));
        }
        int izin = 0;
        for (int[] i : IZINLER) { // {makinist sırası, bugünden başlangıç günü, süre (gün), 0 = yıllık / 1 = rapor}
            LocalDate bas = bugun.plusDays(i[1]);
            makinistler.izinEkle(kartlar.get(i[0]).id(), new IzinIstegi(bas, bas.plusDays(i[2] - 1L), i[3] == 0 ? IzinTuru.YILLIK : IzinTuru.RAPOR));
            izin++;
        }

        YuklemeSonucuDto sonuc = yukleme.yukle(seferExcel(bugun, rnd), false); // UX-2: yükleme gününden başlar (geçmiş günlerle başlayan boş rapor olmasın)
        demoOranlariYaz();
        List<Hesap> hesaplar = new ArrayList<>();
        for (MakinistDto k : kartlar.subList(0, DEMO_HESAP_SAYISI)) { // D001..D003: demo ekranını makinist olarak denemek için
            String gecici = KullaniciServisi.geciciParolaUret();
            kullanicilar.makinistHesabiAc(k.id(), k.sicil(), k.adSoyad(), gecici);
            hesaplar.add(new Hesap(k.sicil(), k.adSoyad(), gecici));
        }
        return new Sonuc(MAKINIST_SAYISI, sonuc.eklenen(), izin, hesaplar);
    }

    /**
     * F2b: demo veride birkaç yolculuk + konaklama örneği (isteğe bağlı; temel demo yüklemesi DEĞİŞMEZ). Yüklemenin 3. gününde (Ankara -> Eskişehir, yük olmayan) ilk 3 sefere
     * D001..D003 YOLCU olarak yazılır; D004'e Sivas'ta bir gecelik konaklama kaydı eklenir. Doğrudan SQL: bunlar kural kontrollü atama değil, ekranları denemek içindir
     * (masada tüm kontroller yine canlı hesaplanır). Zaten yolculuk varsa 409 {@code DEMO_YOLCULUK_VAR}.
     */
    @Transactional
    public int yolculukOrnekleri() {
        if (jdbc.queryForObject("SELECT count(*) FROM atama WHERE rol = 'YOLCU'", Integer.class) > 0) {
            throw UygulamaHatasi.cakisma("DEMO_YOLCULUK_VAR", "Yolculuk örnekleri zaten var.");
        }
        List<Long> seferler = jdbc.queryForList("""
                SELECT s.id FROM sefer s JOIN tanim k ON k.id = s.kalkis_gar_id JOIN tanim v ON v.id = s.varis_gar_id
                WHERE k.ad = 'Ankara' AND v.ad = 'Eskişehir' AND (s.tren_turu IS NULL OR s.tren_turu = 'YOLCU') AND s.tarih = (SELECT min(tarih) + 2 FROM sefer)
                ORDER BY s.planlanan_kalkis, s.id LIMIT 3""", Long.class);
        List<Long> makinistler = jdbc.queryForList("SELECT id FROM makinist WHERE durum = 'AKTIF' ORDER BY sicil LIMIT 4", Long.class);
        if (seferler.isEmpty() || makinistler.size() < 4) {
            throw UygulamaHatasi.cakisma("DEMO_YOK", "Önce demo veriyi yükleyin.");
        }
        for (int i = 0; i < seferler.size(); i++) {
            jdbc.update("INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk) VALUES (?, ?, 'YOLCU', 'demo', 0)", seferler.get(i), makinistler.get(i));
        }
        Long sivas = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = 'Sivas'", Long.class);
        jdbc.update("INSERT INTO konaklama (makinist_id, gar_id, yer_tipi, giris, cikis, notu) SELECT ?, ?, 'OTEL', (min(tarih) + 2 + TIME '20:00')::timestamp AT TIME ZONE 'Europe/Istanbul', "
                + "(min(tarih) + 3 + TIME '06:00')::timestamp AT TIME ZONE 'Europe/Istanbul', 'DEMO – gerçek değil' FROM sefer", makinistler.get(3), sivas);
        return seferler.size();
    }

    /**
     * F2c: demo veriye parçalı (ara istasyonda makinist değişen) bir tren ekler (isteğe bağlı; temel demo yüklemesi ve sayıları DEĞİŞMEZ, MK-64). Yüklemenin 2. gününde
     * Eskişehir 07:00 -> Ankara 08:45 / 09:00 -> Sivas 14:30 ("DEMO-P1"): iki sefer satırı (1/2 YHT, 2/2 Elektrikli Dizi). Atama masasında iki parça ayrı atanır. Aynı excel yolu
     * kullanılır ({@link SeferYuklemeServisi}); zaten varsa 409 {@code DEMO_PARCALI_VAR}, demo veri yoksa 409 {@code DEMO_YOK}.
     */
    @Transactional
    public int parcaliOrnek() {
        LocalDate ilkGun = jdbc.queryForObject("SELECT min(tarih) FROM sefer", LocalDate.class);
        if (ilkGun == null) {
            throw UygulamaHatasi.cakisma("DEMO_YOK", "Önce demo veriyi yükleyin.");
        }
        YuklemeSonucuDto sonuc = yukleme.yukle(parcaliOrnekExcel(ilkGun.plusDays(1)), false);
        if (sonuc.hataSayisi() > 0) {
            throw UygulamaHatasi.cakisma("DEMO_YOK", "Önce demo veriyi yükleyin (tanımlar eksik).");
        }
        if (sonuc.eklenen() == 0) {
            throw UygulamaHatasi.cakisma("DEMO_PARCALI_VAR", "Parçalı tren örneği zaten var.");
        }
        return sonuc.eklenen();
    }

    /** Serbest intikal örneği (İ-2, MK-111) sonucu: yazılan gar çifti süresi ve intikal sayısı. */
    public record IntikalOrnekSonucu(int sure, int intikal, String sicil) {
    }

    /**
     * İ-2: demo veriye üç gar çifti süresi (Ankara–Eskişehir 90, Ankara–Konya 150, Eskişehir–Konya 120 dk) ve bağlı garı Eskişehir olan ilk makinistin (sicil sırası) yüklemenin 2.
     * gününde Eskişehir -> Ankara 07:00–08:30 SERVİS intikalini ekler (isteğe bağlı; temel demo yüklemesi ve sayıları DEĞİŞMEZ). Doğrudan SQL: kural kontrollü yazma değil, ekranları
     * denemek içindir (masada tüm kontroller canlı hesaplanır). Zaten süre ya da intikal varsa 409 {@code DEMO_INTIKAL_VAR}, demo veri yoksa 409 {@code DEMO_YOK}.
     */
    @Transactional
    public IntikalOrnekSonucu intikalOrnek() {
        LocalDate ilkGun = jdbc.queryForObject("SELECT min(tarih) FROM sefer", LocalDate.class);
        Long ankara = garId("Ankara");
        Long eskisehir = garId("Eskişehir");
        Long konya = garId("Konya");
        List<Map<String, Object>> makinist = ilkGun == null || ankara == null || eskisehir == null || konya == null ? List.of()
                : jdbc.queryForList("SELECT id, sicil FROM makinist WHERE durum = 'AKTIF' AND tur = 'MAKINIST' AND bagli_gar_id = ? ORDER BY sicil LIMIT 1", eskisehir);
        if (makinist.isEmpty()) {
            throw UygulamaHatasi.cakisma("DEMO_YOK", "Önce demo veriyi yükleyin.");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM intikal", Integer.class) > 0 || jdbc.queryForObject("SELECT count(*) FROM gar_intikal_suresi", Integer.class) > 0) {
            throw UygulamaHatasi.cakisma("DEMO_INTIKAL_VAR", "İntikal örneği zaten var.");
        }
        long[][] sureler = { { ankara, eskisehir, 90 }, { ankara, konya, 150 }, { eskisehir, konya, 120 } };
        for (long[] s : sureler) {
            jdbc.update("INSERT INTO gar_intikal_suresi (gar_a_id, gar_b_id, dakika) VALUES (?, ?, ?)", Math.min(s[0], s[1]), Math.max(s[0], s[1]), (int) s[2]);
        }
        jdbc.update("INSERT INTO intikal (makinist_id, kalkis_gar_id, varis_gar_id, kalkis, varis, arac_turu, notu) VALUES (?, ?, ?, "
                + "(?::date + TIME '07:00')::timestamp AT TIME ZONE 'Europe/Istanbul', (?::date + TIME '08:30')::timestamp AT TIME ZONE 'Europe/Istanbul', 'SERVIS', 'DEMO – gerçek değil')",
                makinist.getFirst().get("id"), eskisehir, ankara, java.sql.Date.valueOf(ilkGun.plusDays(1)), java.sql.Date.valueOf(ilkGun.plusDays(1)));
        return new IntikalOrnekSonucu(sureler.length, 1, (String) makinist.getFirst().get("sicil"));
    }

    private Long garId(String ad) {
        return jdbc.queryForList("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = ?", Long.class, ad).stream().findFirst().orElse(null);
    }

    /** Kondüktör örneği (F3b, MK-92) sonucu: kart sayısı, yazılan KONDUKTOR ataması ve K001 demo hesabı (geçici parola yalnız bu yanıtta). */
    public record KonduktorSonucu(int konduktor, int atama, Hesap hesap) {
    }

    /**
     * F3b: demo veriye 4 kondüktör (K001..K004, "Demo Kondüktör NN"; K004'ün belgesi 14 gün içinde biter) ekler, 2.-4. günlerin ilk yolcu trenine K001..K003'ü KONDUKTOR yazar (doğrudan SQL: kural
     * kontrollü atama değil, ekranları denemek içindir), {@code konduktor.yolcu_trenine_gerekli}=1 yapar (masada kalan yolcu trenleri "kondüktör eksik" görünür) ve K001'e demo hesabı açar.
     * İsteğe bağlı: temel demo yüklemesi ve sayıları (68 makinist) DEĞİŞMEZ. Zaten kondüktör varsa 409 {@code DEMO_KONDUKTOR_VAR}, demo veri yoksa 409 {@code DEMO_YOK}.
     */
    @Transactional
    public KonduktorSonucu konduktorOrnegi() {
        LocalDate ilkGun = jdbc.queryForObject("SELECT min(tarih) FROM sefer", LocalDate.class);
        Long ankara = jdbc.queryForList("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = 'Ankara'", Long.class).stream().findFirst().orElse(null);
        if (ilkGun == null || ankara == null) {
            throw UygulamaHatasi.cakisma("DEMO_YOK", "Önce demo veriyi yükleyin.");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM makinist WHERE tur = 'KONDUKTOR'", Integer.class) > 0) {
            throw UygulamaHatasi.cakisma("DEMO_KONDUKTOR_VAR", "Kondüktör örnekleri zaten var.");
        }
        LocalDate bugun = LocalDate.now(saat);
        List<MakinistDto> kartlar = new ArrayList<>();
        for (int i = 1; i <= KONDUKTOR_SAYISI; i++) {
            LocalDate belge = i == KONDUKTOR_SAYISI ? bugun.plusDays(14) : bugun.plusYears(2);
            kartlar.add(makinistler.olustur(new MakinistIstegi(String.format("K%03d", i), String.format("Demo Kondüktör %02d", i), ankara, MakinistDurum.AKTIF, belge, bugun.plusYears(2),
                    bugun.plusYears(2), null, null, null, null, PersonelTuru.KONDUKTOR)));
        }
        int atama = 0;
        for (int gun = 1; gun <= 3; gun++) {
            List<Long> yolcuTreni = jdbc.queryForList("SELECT id FROM sefer WHERE tren_turu = 'YOLCU' AND durum <> 'IPTAL' AND tarih = ? ORDER BY planlanan_kalkis, id LIMIT 1", Long.class, ilkGun.plusDays(gun));
            if (!yolcuTreni.isEmpty()) {
                jdbc.update("INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk) VALUES (?, ?, 'KONDUKTOR', 'demo', 0)", yolcuTreni.getFirst(), kartlar.get(gun - 1).id());
                atama++;
            }
        }
        jdbc.update("UPDATE uygulama_ayar SET deger = 1 WHERE anahtar = 'konduktor.yolcu_trenine_gerekli'");
        MakinistDto k = kartlar.getFirst();
        String gecici = KullaniciServisi.geciciParolaUret();
        kullanicilar.makinistHesabiAc(k.id(), k.sicil(), k.adSoyad(), gecici);
        return new KonduktorSonucu(kartlar.size(), atama, new Hesap(k.sicil(), k.adSoyad(), gecici));
    }

    private static byte[] parcaliOrnekExcel(LocalDate gun) {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            String[][] seferler = { { "Tarih", "Tren No", "Kalkış Gar", "Kalkış Saat", "Varış Gar", "Varış Saat", "Araç Tipi", "Hat", "KM", "Tren Türü" },
                    { String.format("%02d.%02d.%d", gun.getDayOfMonth(), gun.getMonthValue(), gun.getYear()), "DEMO-P1", "Eskişehir", "07:00", "Sivas", "14:30", "YHT",
                            "Ankara–Eskişehir", "848", "Yolcu" } };
            String[][] degisim = { { "Tren No", "Sıra", "Gar", "Varış Saat", "Kalkış Saat", "KM", "Hat", "Araç Tipi" },
                    { "DEMO-P1", "1", "Ankara", "08:45", "09:00", "245", "Ankara–Sivas", "Elektrikli Dizi" } };
            sayfaYaz(kitap.createSheet("Seferler"), seferler);
            sayfaYaz(kitap.createSheet("Değişim Noktaları"), degisim);
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sayfaYaz(Sheet sayfa, String[][] satirlar) {
        for (int r = 0; r < satirlar.length; r++) {
            Row satir = sayfa.createRow(r);
            for (int c = 0; c < satirlar[r].length; c++) {
                satir.createCell(c).setCellValue(satirlar[r][c]);
            }
        }
    }

    /** Demo tablolarını boşaltır; kullanıcı, parametre/şiddet ve değişiklik kaydı KALIR. Özel kurallar da silinir: kapsamları tanım kimliklerine bağlıdır ve tanım kimlikleri sıfırlanır. */
    @Transactional
    public void sifirla() {
        demoOranlariTemizle();
        jdbc.update("UPDATE uygulama_ayar SET deger = 0 WHERE anahtar = 'konduktor.yolcu_trenine_gerekli'"); // kondüktör örneği ayarı açar; kondüktörsüz kalan veride "eksik" rozeti çıkmasın
        jdbc.update("DELETE FROM kullanici WHERE rol = 'MAKINIST'"); // makinist tablosu TRUNCATE edilince asılı hesap kalmasın (kullanici.makinist_id FK değildir, MK-26)
        jdbc.execute("TRUNCATE atama, sefer, izin, brove_hat, brove_arac_tipi, makinist, tanim, ozel_kural, gorev_gordum, atama_olayi, takas_talebi, konaklama, intikal, gar_intikal_suresi RESTART IDENTITY CASCADE"); // gorev_gordum/atama_olayi/takas_talebi FK'siz: CASCADE ulaşmaz, atama kimlikleri sıfırlanınca eski onay yeni atamaya yapışmasın (MK-32 W1)
    }

    /**
     * Puantaj oranları (FR-12) gerçek veri değildir: yalnız demo yüklemesinde parametreye yazılır (ekranlar "DEMO – gerçek değil" der). Saat ücreti ve km tutarı varsayılan
     * olarak boştur; fazla mesai çarpanı (1,5) ve gece oranı (%15) zaten varsayılandır.
     */
    private void demoOranlariYaz() {
        parametreler.degerDegistir("puantaj.saat_ucreti", new ParametreGuncellemeIstegi(DEMO_SAAT_UCRETI, DEMO_NEDEN));
        parametreler.degerDegistir("puantaj.km_birim_tutari", new ParametreGuncellemeIstegi(DEMO_KM_TUTARI, DEMO_NEDEN));
    }

    /** Demo oranları hâlâ demo değerindeyse boşaltır (elle girilmiş gerçek bir oran ezilmez). */
    private void demoOranlariTemizle() {
        var p = parametreler.yukle();
        if (DEMO_SAAT_UCRETI.equals(p.degerler().get("puantaj.saat_ucreti"))) {
            parametreler.degerDegistir("puantaj.saat_ucreti", new ParametreGuncellemeIstegi("", "Demo sıfırlandı"));
        }
        if (DEMO_KM_TUTARI.equals(p.degerler().get("puantaj.km_birim_tutari"))) {
            parametreler.degerDegistir("puantaj.km_birim_tutari", new ParametreGuncellemeIstegi("", "Demo sıfırlandı"));
        }
    }

    private MakinistIstegi makinist(int no, LocalDate bugun, Random rnd, List<Long> gar, List<Long> hat, List<Long> arac) {
        String sicil = String.format("D%03d", no);
        MakinistDurum durum = no == 20 ? MakinistDurum.ASKIDA : no == 21 ? MakinistDurum.PASIF : MakinistDurum.AKTIF;
        LocalDate ehliyet = bugun.plusDays(700 + rnd.nextInt(1500));
        LocalDate saglik = bugun.plusDays(120 + rnd.nextInt(700));
        LocalDate psiko = bugun.plusDays(120 + rnd.nextInt(700));
        if (no == 3) {
            saglik = bugun.plusDays(15); // yaklaşan
        } else if (no == 7) {
            psiko = bugun.minusDays(1); // dolmuş
        } else if (no == 12) {
            ehliyet = bugun.plusDays(25); // yaklaşan
        } else if (no == 22) {
            psiko = bugun.plusDays(13); // ay içinde dolacak: sonrası seferlere atanamaz (belge süresi)
        } else if (no == 31) {
            psiko = bugun.plusDays(8);
        } else if (no == 26) {
            saglik = bugun.plusDays(18);
        }
        // Bröve dağılımı talebe göre (haftada ~2.100 saat: ~%61 Ankara–Sivas/Elektrikli Dizi, ~%39 YHT hatları): her 10 makinistin
        // 4'ü Sivas ekibi, 3'ü hızlı tren (YHT) ekibi, 3'ü her ikisini de çalıştıran karma ekip. Hızlı tren ekibinden 7 kişi ayrıca Dizel Lokomotif kullanır.
        int ekip = EKIPLER[(no - 1) % EKIPLER.length];
        boolean dizel = java.util.Arrays.stream(DIZEL_EKIBI).anyMatch(d -> d == no);
        List<BroveAracTipiIstegi> aracBrove = new ArrayList<>();
        for (int t : dizel ? new int[] { 0, 2 } : EKIP_ARACLARI[ekip]) {
            aracBrove.add(new BroveAracTipiIstegi(arac.get(t), bugun.minusDays(300 + rnd.nextInt(1200))));
        }
        List<BroveHatIstegi> hatBrove = new ArrayList<>();
        for (int h = 0; h < hat.size(); h++) {
            if (h == 0 || (h < GENEL_HAT ? HAT_YETKISI[ekip][h] : dizel)) { // herkeste Ankara–Eskişehir kaydı (araç bröve yoksa yine de çalıştırılamaz)
                LocalDate sinav = bugun.minusDays(200 + rnd.nextInt(1400));
                LocalDate sonSurus = bugun.minusDays(5 + rnd.nextInt(300));
                if (no == 9 && h == 0) {
                    sinav = bugun.minusYears(6); // sınav geçerliliği dolmuş bröve
                }
                if (no == 15 && h == 0) {
                    sonSurus = bugun.minusMonths(20); // 18 aydır sürmemiş
                }
                hatBrove.add(new BroveHatIstegi(hat.get(h), sinav, sonSurus));
            }
        }
        long bagliGar = gar.get(dizel ? 1 : EKIP_GARLARI[ekip][no % EKIP_GARLARI[ekip].length]);
        return new MakinistIstegi(sicil, String.format("Demo Makinist %02d", no), bagliGar, durum, ehliyet, saglik, psiko,
                bugun.minusDays(3 + rnd.nextInt(60)), null, aracBrove, hatBrove, null);
    }

    /** İlk günden itibaren {@link #GUN_SAYISI} gün, günde {@link #GUNLUK_SEFER} sefer içeren sefer şablonu (.xlsx baytları). */
    private byte[] seferExcel(LocalDate ilkGun, Random rnd) {
        try (XSSFWorkbook kitap = new XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            Sheet sayfa = kitap.createSheet("Seferler");
            String[] baslik = { "Tarih", "Tren No", "Kalkış Gar", "Kalkış Saat", "Varış Gar", "Varış Saat", "Araç Tipi", "Hat", "KM", "Tren Türü" };
            Row b = sayfa.createRow(0);
            for (int i = 0; i < baslik.length; i++) {
                b.createCell(i).setCellValue(baslik[i]);
            }
            int r = 1;
            for (int g = 0; g < GUN_SAYISI; g++) {
                LocalDate gun = ilkGun.plusDays(g);
                for (int i = 0; i < GUNLUK_SEFER; i++) {
                    int[] h = HAT_BILGISI[i % GENEL_HAT];
                    boolean donus = (i / GENEL_HAT) % 2 == 1;
                    int kalkisDk = 5 * 60 + (i * 1050 / GUNLUK_SEFER) + rnd.nextInt(10); // 05:00'dan ~22:30'a yayılır
                    int sure = h[2] - 15 + rnd.nextInt(31);
                    LocalTime kalkis = LocalTime.of((kalkisDk / 60) % 24, kalkisDk % 60);
                    LocalTime varis = kalkis.plusMinutes(sure);
                    Row satir = sayfa.createRow(r++);
                    String[] deger = { String.format("%02d.%02d.%d", gun.getDayOfMonth(), gun.getMonthValue(), gun.getYear()),
                            String.format("%s-%03d", HAT_KODU[i % GENEL_HAT], i + 1), GARLAR[donus ? h[1] : h[0]],
                            kalkis.toString(), GARLAR[donus ? h[0] : h[1]], varis.toString(), ARAC_TIPLERI[h[4]], HATLAR[i % GENEL_HAT],
                            Integer.toString(h[3]), i % GENEL_HAT == 1 && (i / GENEL_HAT) % 2 == 0 ? "Yük" : "Yolcu" };
                    for (int c = 0; c < deger.length; c++) {
                        satir.createCell(c).setCellValue(deger[c]);
                    }
                }
                for (int p = 0; p < DIZEL_CIFT; p++) { // dizel ekibi: Eskişehir -> Konya -> Eskişehir gidiş-dönüş, ~9,5 saat
                    int[] h = HAT_BILGISI[GENEL_HAT];
                    int gidis = 5 * 60 + p * 60;
                    int donusKalkis = gidis + h[2] + 45;
                    for (int yon = 0; yon < 2; yon++) {
                        LocalTime kalkis = LocalTime.of((yon == 0 ? gidis : donusKalkis) / 60, (yon == 0 ? gidis : donusKalkis) % 60);
                        LocalTime varis = kalkis.plusMinutes(h[2] - 10 + rnd.nextInt(21));
                        Row satir = sayfa.createRow(r++);
                        String[] deger = { String.format("%02d.%02d.%d", gun.getDayOfMonth(), gun.getMonthValue(), gun.getYear()),
                                String.format("%s-%03d", HAT_KODU[GENEL_HAT], p * 2 + yon + 1), GARLAR[yon == 0 ? h[0] : h[1]], kalkis.toString(),
                                GARLAR[yon == 0 ? h[1] : h[0]], varis.toString(), ARAC_TIPLERI[h[4]], HATLAR[GENEL_HAT], Integer.toString(h[3]), "Yük" };
                        for (int c = 0; c < deger.length; c++) {
                            satir.createCell(c).setCellValue(deger[c]);
                        }
                    }
                }
            }
            kitap.write(cikti);
            return cikti.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
